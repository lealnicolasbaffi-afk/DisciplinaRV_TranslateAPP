import XCTest
import Translation
@testable import TradutorOffline

/// These tests use only fakes. They do not certify any Apple model or offline voice.
@MainActor
final class ConversationModelTests: XCTestCase {
    func testASRStartsAutomaticTranslationWithImmutableInput() async {
        let fixture = Fixture()
        await fixture.model.startRecording()
        fixture.clock.now = 10
        fixture.model.stopRecording()
        await settle { fixture.speech.pending != nil }
        fixture.clock.now = 10.25
        fixture.speech.finish("Bom dia.")
        await settle { fixture.model.translationRequest != nil }
        guard let request = fixture.model.translationRequest,
              case .translate(let text) = request.kind else { return XCTFail("Tradução automática ausente") }
        XCTAssertEqual(text, "Bom dia.")
        XCTAssertEqual(request.direction, .portugueseToEnglish)
        XCTAssertEqual(fixture.model.state, .translating)
        fixture.model.cancelOperation()
        XCTAssertEqual(fixture.model.trials.count, 1)
        XCTAssertEqual(fixture.model.latestTrial?.rawInput, "Bom dia.")
        XCTAssertEqual(fixture.model.latestTrial?.transcriptionMS, 250)
        XCTAssertNil(fixture.model.latestTrial?.textMS)
    }

    func testLateASRDoesNotOverwriteNewTypedRoundOrItsMetrics() async {
        let fixture = Fixture()
        await fixture.model.startRecording()
        fixture.model.stopRecording()
        await settle { fixture.speech.pending != nil }
        fixture.model.cancelOperation()
        fixture.model.newTypedRound()
        fixture.model.updateInput("Texto novo")
        fixture.speech.finish("Resultado antigo")
        await Task.yield()
        await Task.yield()
        XCTAssertEqual(fixture.model.input, "Texto novo")
        XCTAssertEqual(fixture.model.output, "")
        XCTAssertNil(fixture.model.translationRequest)
        fixture.model.requestTranslation()
        fixture.model.cancelOperation()
        XCTAssertEqual(fixture.model.trials.count, 2)
        let typed = fixture.model.latestTrial
        XCTAssertEqual(typed?.origin, .typedText)
        XCTAssertEqual(typed?.rawInput, "Texto novo")
        XCTAssertEqual(typed?.edited, false)
        XCTAssertNil(typed?.transcriptionMS)
        XCTAssertNil(typed?.textMS)
        XCTAssertNil(typed?.audioStartMS)
    }

    func testDelayedRecordingCallbackCannotStartAnotherRound() async {
        let fixture = Fixture()
        await fixture.model.startRecording()
        let obsoleteCallback = fixture.audio.onRecordingFinished
        fixture.model.cancelOperation()
        fixture.model.newTypedRound()
        fixture.model.updateInput("Outra frase")
        obsoleteCallback?(.success(fixture.audio.url))
        await Task.yield()
        XCTAssertEqual(fixture.model.input, "Outra frase")
        XCTAssertEqual(fixture.model.state, .ready)
        XCTAssertNil(fixture.speech.pending)
        XCTAssertEqual(fixture.model.trials.count, 1)
    }

    func testPermissionWaitPreventsDoubleStartAndCancelledPromptCannotRecord() async {
        let fixture = Fixture()
        fixture.audio.delayPermission = true
        let first = Task { await fixture.model.startRecording() }
        await settle { fixture.audio.permission != nil }
        await fixture.model.startRecording()
        XCTAssertEqual(fixture.audio.permissionRequests, 1)
        fixture.model.cancelOperation()
        fixture.audio.permission?.resume()
        fixture.audio.permission = nil
        await first.value
        XCTAssertEqual(fixture.audio.recordingStarts, 0)
        XCTAssertFalse(fixture.model.isRecording)
        XCTAssertEqual(fixture.model.trials.count, 1)
    }

    func testOriginalReplayCancellationPreservesPreviousAttempt() async {
        let fixture = Fixture()
        await fixture.model.importAudio(fixture.audio.url)
        await settle { fixture.speech.pending != nil }
        fixture.speech.finish("Olá")
        await settle { fixture.model.translationRequest != nil }
        fixture.model.cancelOperation()
        let previous = fixture.model.trials
        fixture.model.replayOriginal()
        XCTAssertEqual(fixture.model.state, .reproducing)
        fixture.model.cancelOperation()
        XCTAssertEqual(fixture.model.trials, previous)
    }

    func testSaveFailureKeepsRecordInMemoryAndDisablesStaleExport() async {
        let fixture = Fixture()
        XCTAssertNotNil(fixture.model.exportURL)
        fixture.store.rejectSave = true
        await fixture.model.startRecording()
        fixture.model.cancelOperation()
        XCTAssertEqual(fixture.model.trials.count, 1)
        XCTAssertNotNil(fixture.model.persistenceWarning)
        XCTAssertNil(fixture.model.exportURL)
        fixture.store.rejectSave = false
        fixture.model.notes = "PT01, repetição 1; cancelamento intencional"
        fixture.model.transcriptionQuality = .incorrect
        fixture.model.saveAssessment()
        XCTAssertEqual(fixture.store.saved.last?.notes, fixture.model.notes)
        XCTAssertEqual(fixture.store.saved.last?.transcriptionQuality, .incorrect)
        XCTAssertNotNil(fixture.model.exportURL)
        XCTAssertNil(fixture.model.persistenceWarning)
    }

    func testUnreadableHistoryIsNotOverwritten() {
        let store = MemoryStore()
        store.rejectLoad = true
        let fixture = Fixture(store: store)
        XCTAssertEqual(store.saveCalls, 0)
        XCTAssertNil(fixture.model.exportURL)
        XCTAssertNotNil(fixture.model.persistenceWarning)
    }

    func testPreparationCancellationDoesNotCreateSpeechAttempt() {
        let fixture = Fixture()
        fixture.model.updateInput("Bom dia")
        fixture.model.prepareLanguages()
        XCTAssertEqual(fixture.model.state, .preparing)
        fixture.model.cancelOperation()
        XCTAssertTrue(fixture.model.trials.isEmpty)
        XCTAssertEqual(fixture.model.input, "Bom dia")
        fixture.model.setDirection(.englishToPortuguese)
        XCTAssertEqual(fixture.model.input, "")
        XCTAssertEqual(fixture.model.output, "")
    }

    func testAutomaticVoiceMeasuresFirstStartAndReplayPreservesIt() async throws {
        let fixture = Fixture()
        try await translateMicrophone(fixture)
        XCTAssertEqual(fixture.model.latestTrial?.textMS, 500)
        XCTAssertEqual(fixture.model.latestTrial?.translationMS, 25)
        fixture.clock.now = 11
        fixture.synthesis.onStart?()
        XCTAssertEqual(fixture.model.latestTrial?.audioStartMS, 1_000)
        fixture.synthesis.onFinish?(.success(()))
        let completed = fixture.model.trials
        fixture.clock.now = 20
        fixture.model.replayTranslation()
        fixture.synthesis.onStart?()
        fixture.model.cancelOperation()
        XCTAssertEqual(fixture.model.trials, completed)
    }

    func testCorrectionRetainsRawInputAndIncludesReviewTimeInNewAttempt() async throws {
        let fixture = Fixture()
        try await translateMicrophone(fixture)
        fixture.clock.now = 11
        fixture.synthesis.onStart?()
        fixture.synthesis.onFinish?(.success(()))
        fixture.model.updateInput("Bom dia revisado")
        fixture.clock.now = 20
        fixture.model.requestTranslation()
        let request = try XCTUnwrap(fixture.model.translationRequest)
        await fixture.model.executeTranslation(request: request, prepare: {}, translate: { text in
            XCTAssertEqual(text, "Bom dia revisado")
            return TranslationResult(text: "Revised greeting", translationMS: 30)
        })
        let corrected = try XCTUnwrap(fixture.model.latestTrial)
        XCTAssertEqual(fixture.model.trials.count, 2)
        XCTAssertEqual(corrected.rawInput, "Bom dia")
        XCTAssertEqual(corrected.input, "Bom dia revisado")
        XCTAssertEqual(corrected.origin, .microphone)
        XCTAssertTrue(corrected.edited)
        XCTAssertEqual(corrected.transcriptionMS, 250)
        XCTAssertEqual(corrected.textMS, 10_000)
        fixture.synthesis.onFinish?(.success(()))
    }

    func testOldVoiceStartCannotChangeNewTypedRound() async throws {
        let fixture = Fixture()
        try await translateMicrophone(fixture)
        let oldStart = fixture.synthesis.onStart
        fixture.model.cancelOperation()
        fixture.model.newTypedRound()
        fixture.model.updateInput("Texto independente")
        oldStart?()
        XCTAssertEqual(fixture.model.input, "Texto independente")
        XCTAssertEqual(fixture.model.state, .ready)
        XCTAssertNil(fixture.model.latestTrial?.audioStartMS)
    }

    private func translateMicrophone(_ fixture: Fixture) async throws {
        await fixture.model.startRecording()
        fixture.clock.now = 10
        fixture.model.stopRecording()
        await settle { fixture.speech.pending != nil }
        fixture.clock.now = 10.25
        fixture.speech.finish("Bom dia")
        await settle { fixture.model.translationRequest != nil }
        let request = try XCTUnwrap(fixture.model.translationRequest)
        fixture.clock.now = 10.5
        await fixture.model.executeTranslation(request: request, prepare: {}, translate: { _ in
            TranslationResult(text: "Good morning", translationMS: 25)
        })
        XCTAssertEqual(fixture.model.state, .reproducing)
    }

    private func settle(_ condition: () -> Bool) async {
        for _ in 0..<100 {
            if condition() { return }
            await Task.yield()
        }
        XCTAssertTrue(condition(), "A operação falsa não avançou após ceder ao executor")
    }
}

@MainActor
private final class TestClock { var now: TimeInterval = 0 }

@MainActor
private final class Fixture {
    let speech: FakeSpeech
    let audio: FakeAudio
    let synthesis: FakeSynthesis
    let store: MemoryStore
    let clock: TestClock
    let model: ConversationModel

    init(store providedStore: MemoryStore? = nil) {
        let speech = FakeSpeech()
        let audio = FakeAudio()
        let synthesis = FakeSynthesis()
        let clock = TestClock()
        let store = providedStore ?? MemoryStore()
        self.speech = speech; self.audio = audio; self.synthesis = synthesis
        self.clock = clock
        self.store = store
        model = ConversationModel(speech: speech, audio: audio, synthesis: synthesis,
            translation: FakeTranslation(), store: store, device: "Aparelho falso de teste",
            system: "Sistema falso de teste", clock: { clock.now })
    }
}

@MainActor
private final class MemoryStore: TrialStoring {
    var saved: [Trial] = []
    var rejectLoad = false
    var rejectSave = false
    var saveCalls = 0
    let exportURL = URL(fileURLWithPath: "/fake/medicoes.json")
    func load() throws -> [Trial] {
        if rejectLoad { throw ServiceError("arquivo inválido de teste") }
        return saved
    }
    func save(_ trials: [Trial]) throws {
        saveCalls += 1
        if rejectSave { throw ServiceError("falha de escrita de teste") }
        saved = trials
    }
}

@MainActor
private final class FakeSpeech: SpeechRecognizing {
    var pending: CheckedContinuation<String, Error>?
    func requestPermission() async throws {}
    func validateAvailability(locale: String) throws {}
    func transcribe(url: URL, locale: String) async throws -> String {
        try await withCheckedThrowingContinuation { pending = $0 }
    }
    // Deliberately ignores cancellation to simulate a stale framework callback.
    func cancel() {}
    func finish(_ text: String) {
        let value = pending; pending = nil; value?.resume(returning: text)
    }
}

@MainActor
private final class FakeAudio: AudioServicing {
    var onRecordingFinished: ((Result<URL, Error>) -> Void)?
    var onPlaybackFinished: ((Result<Void, Error>) -> Void)?
    var onInterruption: (() -> Void)?
    let url = URL(fileURLWithPath: "/fake/audio.caf")
    var delayPermission = false
    var permission: CheckedContinuation<Void, Never>?
    var permissionRequests = 0
    var recordingStarts = 0
    func requestRecordPermission() async throws {
        permissionRequests += 1
        if delayPermission { await withCheckedContinuation { permission = $0 } }
    }
    func startRecording() throws -> URL { recordingStarts += 1; return url }
    func stopRecording() throws -> URL { url }
    func importAudio(_ url: URL) throws -> URL { url }
    func play(_ url: URL) throws {}
    func cancel() {}
}

@MainActor
private final class FakeSynthesis: SpeechSynthesizing {
    var onStart: (() -> Void)?
    var onFinish: ((Result<Void, Error>) -> Void)?
    func speak(text: String, locale: String, onStart: @escaping () -> Void,
               onFinish: @escaping (Result<Void, Error>) -> Void) throws {
        self.onStart = onStart; self.onFinish = onFinish
    }
    func cancel() {}
}

@MainActor
private final class FakeTranslation: TranslationServicing {
    func prepare(using session: TranslationSession, direction: Direction) async throws {}
    func translate(_ text: String, using session: TranslationSession,
                   direction: Direction) async throws -> TranslationResult {
        TranslationResult(text: "Tradução falsa de teste", translationMS: 1)
    }
}
