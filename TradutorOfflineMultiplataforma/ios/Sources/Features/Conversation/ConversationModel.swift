import Foundation
import Combine
import Translation

@MainActor
final class ConversationModel: ObservableObject {
    @Published private(set) var direction: Direction = .portugueseToEnglish
    @Published private(set) var state: ConversationState = .ready
    @Published private(set) var status = "Prepare os idiomas antes do modo avião."
    @Published private(set) var input = ""
    @Published private(set) var output = ""
    @Published private(set) var trials: [Trial] = []
    @Published private(set) var translationRequest: TranslationRequest?
    @Published private(set) var exportURL: URL?
    @Published private(set) var persistenceWarning: String?
    @Published var airplaneModeConfirmed = false
    @Published var transcriptionQuality: Quality = .notEvaluated
    @Published var translationQuality: Quality = .notEvaluated
    @Published var notes = ""

    private let speech: SpeechRecognizing
    private let audio: AudioServicing
    private let synthesis: SpeechSynthesizing
    private let translation: TranslationServicing
    private let store: TrialStoring
    private let device: String
    private let system: String
    private let clock: () -> TimeInterval
    private var round: RoundContext?
    private var audioURL: URL?
    private var operationID = UUID()
    private var flowTask: Task<Void, Never>?
    private var watchdog: Task<Void, Never>?
    private var currentTrialID: UUID?
    private var assessmentTrialID: UUID?
    private var storageReadable = true
    private var recordsOperationFailure = false

    var isRecording: Bool { state == .recording }
    var canBegin: Bool { !state.isBusy }
    var canReplayOriginal: Bool { canBegin && audioURL != nil }
    var canTranslate: Bool { canBegin && !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var canSpeak: Bool { canBegin && !output.isEmpty }
    var latestTrial: Trial? { trials.last }

    init(speech: SpeechRecognizing, audio: AudioServicing,
         synthesis: SpeechSynthesizing, translation: TranslationServicing,
         store: TrialStoring, device: String, system: String,
         clock: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }) {
        self.speech = speech; self.audio = audio; self.synthesis = synthesis
        self.translation = translation; self.store = store
        self.device = device; self.system = system; self.clock = clock
        do {
            trials = try store.load()
            // Materialize a valid empty export without adding a trial.
            try store.save(trials)
            exportURL = store.exportURL
            if let last = trials.last { selectAssessment(last) }
        } catch {
            storageReadable = false
            persistenceWarning = "Medições não carregadas. Preserve/restaure medicoes.json antes de reabrir o app: \(error.localizedDescription)"
        }
        audio.onInterruption = { [weak self] in
            guard let self, self.state.isBusy else { return }
            self.fail("Áudio interrompido ou app enviado ao fundo. Volte e tente novamente.", record: self.recordsOperationFailure)
        }
    }

    func setDirection(_ value: Direction) {
        guard canBegin, value != direction else { return }
        resetRound()
        direction = value
        status = "Direção alterada. Prepare e teste este sentido."
    }

    func updateInput(_ value: String) {
        guard canBegin else { return }
        input = value
        // Corrections retain the recognized rawInput and original reference.
        // Unrelated typed input starts with the explicit new-round action.
        if round == nil { round = RoundContext(direction: direction, origin: .typedText) }
        output = ""
    }

    func newTypedRound() {
        guard canBegin else { return }
        resetRound()
        status = "Nova rodada de texto digitado • tempos da fala não serão medidos."
    }

    func startRecording() async {
        guard canBegin else { return }
        resetRound()
        round = RoundContext(direction: direction, origin: .microphone)
        recordsOperationFailure = true
        let token = operationID
        state = .preparing; status = "Verificando permissões e reconhecimento local…"
        do {
            try await audio.requestRecordPermission()
            guard isCurrent(token), !Task.isCancelled else { return }
            try await speech.requestPermission()
            guard isCurrent(token), !Task.isCancelled else { return }
            try speech.validateAvailability(locale: direction.sourceLocale)
            audio.onRecordingFinished = { [weak self] result in
                guard let self, self.isCurrent(token), self.state == .recording else { return }
                switch result {
                case .success(let url):
                    self.round?.referenceTime = self.clock()
                    self.audioURL = url
                    self.beginTranscription(url, token: token)
                case .failure(let error): self.fail(error.localizedDescription, record: true)
                }
            }
            audioURL = try audio.startRecording()
            state = .recording; status = "Gravando • máximo de 20 segundos"
        } catch {
            guard isCurrent(token) else { return }
            fail(error.localizedDescription, record: true)
        }
    }

    func stopRecording() {
        guard state == .recording else { return }
        round?.referenceTime = clock()
        do {
            let url = try audio.stopRecording()
            audioURL = url
            beginTranscription(url, token: operationID)
        } catch { fail(error.localizedDescription, record: true) }
    }

    func importAudio(_ url: URL) async {
        guard canBegin else { return }
        resetRound()
        round = RoundContext(direction: direction, origin: .importedAudio)
        recordsOperationFailure = true
        let token = operationID
        state = .preparing; status = "Verificando permissão e arquivo local…"
        do {
            try await speech.requestPermission()
            guard isCurrent(token), !Task.isCancelled else { return }
            try speech.validateAvailability(locale: direction.sourceLocale)
            let local = try audio.importAudio(url)
            round?.referenceTime = clock()
            audioURL = local
            beginTranscription(local, token: token)
        } catch {
            guard isCurrent(token) else { return }
            fail(error.localizedDescription, record: true)
        }
    }

    func reportImportFailure(_ message: String) {
        guard canBegin else { return }
        // File-picker errors aren't a speech attempt.
        state = .failed(message); status = message
    }

    private func beginTranscription(_ url: URL, token: UUID) {
        state = .transcribing; status = "Transcrevendo no aparelho…"
        armWatchdog(seconds: 30, token: token, message: "Transcrição excedeu 30 segundos. Confira o recurso local e tente novamente.")
        let locale = direction.sourceLocale
        flowTask = Task { [weak self] in
            guard let self else { return }
            do {
                let text = try await self.speech.transcribe(url: url, locale: locale)
                guard self.isCurrent(token), !Task.isCancelled else { return }
                guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                    self.fail("Nenhuma fala reconhecida. Grave uma frase audível e tente novamente.", record: true)
                    return
                }
                self.watchdog?.cancel()
                self.input = text
                self.round?.rawInput = text
                let elapsed = self.round?.elapsedMS(at: self.clock())
                self.round?.transcriptionMS = elapsed
                self.state = .ready
                self.requestTranslation()
            } catch {
                guard self.isCurrent(token), !Task.isCancelled else { return }
                self.fail(error.localizedDescription, record: true)
            }
        }
    }

    func prepareLanguages() {
        guard canBegin else { return }
        invalidateOperation()
        recordsOperationFailure = false
        let request = TranslationRequest(id: operationID, direction: direction, kind: .prepare)
        state = .preparing; status = "Preparando os modelos • aceite o download do sistema…"
        translationRequest = request
        armWatchdog(seconds: 180, token: request.id,
                    message: "Preparação excedeu 3 minutos. Verifique a conexão e o download; toque em Preparar idiomas novamente.", record: false)
    }

    func requestTranslation() {
        guard canTranslate else { return }
        if round == nil { round = RoundContext(direction: direction, origin: .typedText) }
        if round?.origin == .typedText { round?.rawInput = input }
        // A correction is a new attempt retaining the original round reference.
        currentTrialID = nil
        recordsOperationFailure = true
        output = ""
        invalidateOperation()
        let request = TranslationRequest(id: operationID, direction: direction, kind: .translate(input))
        translationRequest = request
        state = .translating; status = "Traduzindo no aparelho…"
        armWatchdog(seconds: 30, token: request.id,
                    message: "Tradução excedeu 30 segundos. Confira os pacotes em Preparar idiomas e tente novamente.")
    }

    /// Only called within the lifetime of the SwiftUI translationTask host.
    func performTranslation(using session: TranslationSession, request: TranslationRequest) async {
        await executeTranslation(request: request, prepare: {
            try await self.translation.prepare(using: session, direction: request.direction)
        }, translate: { text in
            try await self.translation.translate(text, using: session, direction: request.direction)
        })
    }

    /// Native session ownership stays at the boundary. The coordinator consumes
    /// operations, allowing its full metrics/state flow to run with test doubles.
    func executeTranslation(request: TranslationRequest,
                            prepare: () async throws -> Void,
                            translate: (String) async throws -> TranslationResult) async {
        guard isCurrent(request.id) else { return }
        do {
            switch request.kind {
            case .prepare:
                try await prepare()
                guard isCurrent(request.id), !Task.isCancelled else { return }
                watchdog?.cancel(); translationRequest = nil
                state = .ready
                status = "Pacote de tradução instalado. Valide reconhecimento e voz em modo avião."
            case .translate(let text):
                let result = try await translate(text)
                guard isCurrent(request.id), !Task.isCancelled else { return }
                let now = clock()
                watchdog?.cancel()
                output = result.text
                guard let round else { return }
                let trial = round.makeTrial(input: text, output: result.text,
                    airplaneModeConfirmed: airplaneModeConfirmed, device: device, system: system,
                    now: now, translationMS: result.translationMS, failure: nil)
                trials.append(trial); currentTrialID = trial.id
                selectAssessment(trial); persist()
                translationRequest = nil
                state = .ready
                speak(measuredTrialID: trial.id)
            }
        } catch {
            guard isCurrent(request.id), !Task.isCancelled else { return }
            let shouldRecord: Bool
            switch request.kind { case .prepare: shouldRecord = false; case .translate: shouldRecord = true }
            fail(error.localizedDescription, record: shouldRecord)
        }
    }

    func replayTranslation() {
        guard canSpeak else { return }
        speak(measuredTrialID: nil)
    }

    private func speak(measuredTrialID: UUID?) {
        guard canSpeak else { return }
        invalidateOperation()
        let recordFailure = measuredTrialID != nil
        recordsOperationFailure = recordFailure
        let token = operationID
        state = .reproducing; status = "Preparando voz local…"
        armWatchdog(seconds: 15, token: token, message: "A voz não confirmou início em 15 segundos. Verifique a voz instalada e teste em modo avião.", record: recordFailure)
        do {
            try synthesis.speak(text: output, locale: direction.targetLocale, onStart: { [weak self] in
                guard let self, self.isCurrent(token) else { return }
                self.armWatchdog(seconds: 120, token: token,
                    message: "A reprodução da voz excedeu 2 minutos. Confira a saída de áudio e tente novamente.", record: recordFailure)
                if let id = measuredTrialID, let index = self.trials.firstIndex(where: { $0.id == id }),
                   self.trials[index].audioStartMS == nil, let reference = self.round?.referenceTime,
                   self.round?.origin != .typedText {
                    self.trials[index].audioStartMS = max(0, (self.clock() - reference) * 1_000)
                    self.persist()
                }
                self.status = "Reproduzindo tradução • avalie o resultado abaixo."
            }, onFinish: { [weak self] result in
                guard let self, self.isCurrent(token) else { return }
                self.watchdog?.cancel()
                switch result {
                case .success:
                    self.recordsOperationFailure = false
                    self.state = .ready; self.status = "Pronto • revise e salve a avaliação."
                case .failure(let error): self.fail(error.localizedDescription, record: recordFailure)
                }
            })
        } catch { fail(error.localizedDescription, record: recordFailure) }
    }

    func replayOriginal() {
        guard canReplayOriginal, let url = audioURL else { return }
        invalidateOperation()
        recordsOperationFailure = false
        let token = operationID
        state = .reproducing; status = "Reproduzindo gravação original…"
        audio.onPlaybackFinished = { [weak self] result in
            guard let self, self.isCurrent(token) else { return }
            self.watchdog?.cancel()
            switch result {
            case .success: self.state = .ready; self.status = "Gravação reproduzida."
            case .failure(let error): self.fail(error.localizedDescription, record: false)
            }
        }
        armWatchdog(seconds: 30, token: token, message: "Reprodução do original excedeu 30 segundos.", record: false)
        do { try audio.play(url) } catch { fail(error.localizedDescription, record: false) }
    }

    func cancelOperation() {
        guard state.isBusy else { return }
        fail("Operação cancelada. Tente novamente ou inicie uma nova rodada.", record: recordsOperationFailure)
    }

    func didEnterBackground() {
        // Permission prompts/download sheets make the app inactive; cancel only
        // when it actually enters the background.
        guard state.isBusy else { return }
        cancelOperation()
    }

    func saveAssessment() {
        guard canBegin, let id = assessmentTrialID,
              let index = trials.firstIndex(where: { $0.id == id }) else { return }
        trials[index].transcriptionQuality = transcriptionQuality
        trials[index].translationQuality = translationQuality
        trials[index].notes = notes
        if persist() { status = "Avaliação salva no aparelho." }
    }

    private func selectAssessment(_ trial: Trial) {
        assessmentTrialID = trial.id
        transcriptionQuality = trial.transcriptionQuality
        translationQuality = trial.translationQuality
        notes = trial.notes
    }

    private func resetRound() {
        invalidateOperation()
        round = nil; audioURL = nil; currentTrialID = nil
        recordsOperationFailure = false
        input = ""; output = ""
        state = .ready
    }

    private func invalidateOperation() {
        operationID = UUID()
        watchdog?.cancel(); watchdog = nil
        flowTask?.cancel(); flowTask = nil
        translationRequest = nil
        speech.cancel(); synthesis.cancel(); audio.cancel()
    }

    private func isCurrent(_ token: UUID) -> Bool { operationID == token }

    private func armWatchdog(seconds: Double, token: UUID, message: String, record: Bool = true) {
        watchdog?.cancel()
        watchdog = Task { [weak self] in
            do { try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000)) }
            catch { return }
            guard let self, self.isCurrent(token), self.state.isBusy else { return }
            self.fail(message, record: record)
        }
    }

    private func fail(_ message: String, record: Bool) {
        if record, let round {
            if let id = currentTrialID, let index = trials.firstIndex(where: { $0.id == id }) {
                trials[index].failure = message
            } else {
                let trial = round.makeTrial(input: input, output: output,
                    airplaneModeConfirmed: airplaneModeConfirmed, device: device, system: system,
                    now: clock(), translationMS: nil, failure: message)
                trials.append(trial); currentTrialID = trial.id
                selectAssessment(trial)
            }
            persist()
        }
        invalidateOperation()
        recordsOperationFailure = false
        state = .failed(message); status = message
    }

    @discardableResult
    private func persist() -> Bool {
        guard storageReadable else { exportURL = nil; return false }
        do {
            try store.save(trials)
            exportURL = store.exportURL; persistenceWarning = nil
            return true
        } catch {
            exportURL = nil
            persistenceWarning = "Falha ao salvar medições. Os dados atuais estão só na memória; exportação suspensa: \(error.localizedDescription)"
            return false
        }
    }
}
