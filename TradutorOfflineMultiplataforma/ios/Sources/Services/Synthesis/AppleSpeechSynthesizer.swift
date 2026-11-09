import AVFoundation
import Foundation

@MainActor
final class AppleSpeechSynthesizer: NSObject, SpeechSynthesizing, AVSpeechSynthesizerDelegate {
    private struct PendingSpeech {
        let utterance: AVSpeechUtterance
        let onStart: () -> Void
        let onFinish: (Result<Void, Error>) -> Void
        var started = false
    }

    private var synthesizer = AVSpeechSynthesizer()
    private let sessionOwner = UUID()
    private var pending: PendingSpeech?
    private var interruptionObserver: NSObjectProtocol?
    private var resetObserver: NSObjectProtocol?

    override init() {
        super.init()
        synthesizer.delegate = self
        synthesizer.usesApplicationAudioSession = true
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            let type = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.uintValue
            guard type == AVAudioSession.InterruptionType.began.rawValue else { return }
            Task { @MainActor in self?.interrupt() }
        }
        resetObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.mediaServicesWereResetNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.resetAfterMediaServicesRestart() }
        }
    }

    deinit {
        if let interruptionObserver { NotificationCenter.default.removeObserver(interruptionObserver) }
        if let resetObserver { NotificationCenter.default.removeObserver(resetObserver) }
        let owner = sessionOwner
        Task { @MainActor in AppleAudioSession.release(owner: owner) }
    }

    func speak(
        text: String,
        locale: String,
        onStart: @escaping () -> Void,
        onFinish: @escaping (Result<Void, Error>) -> Void
    ) throws {
        cancel()
        let input = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { throw ServiceError("Traduza uma frase antes de reproduzir a voz.") }
        let expectedLanguage = canonical(locale)
        let preferred = AVSpeechSynthesisVoice(language: locale)
        let exactVoice: AVSpeechSynthesisVoice?
        if let preferred, canonical(preferred.language) == expectedLanguage {
            exactVoice = preferred
        } else {
            exactVoice = AVSpeechSynthesisVoice.speechVoices().first {
                canonical($0.language) == expectedLanguage
            }
        }
        guard let voice = exactVoice else {
            throw ServiceError("Voz \(locale) indisponível. Baixe essa voz em Ajustes → Acessibilidade → Conteúdo Falado → Vozes e teste em modo avião.")
        }
        // O idioma exato evita fallback silencioso para outra variante.
        // Enumerar a voz não prova seus recursos offline: validar no iPhone em
        // modo avião, inclusive após encerrar e abrir novamente o aplicativo.
        do {
            try AppleAudioSession.activatePlayback(owner: sessionOwner)
        } catch {
            throw ServiceError("Não foi possível ativar a saída de voz: \(error.localizedDescription). Confira a saída de áudio e tente novamente.")
        }
        let utterance = AVSpeechUtterance(string: input)
        utterance.voice = voice
        pending = PendingSpeech(utterance: utterance, onStart: onStart, onFinish: onFinish)
        synthesizer.speak(utterance)
        // O coordenador aplica limites de início e conclusão. O framework não
        // oferece callback de erro para todas as falhas de disponibilização da voz.
    }

    func cancel() {
        // Invalidar antes de stopSpeaking: didCancel pode chegar após outra fala.
        pending = nil
        synthesizer.stopSpeaking(at: .immediate)
        AppleAudioSession.release(owner: sessionOwner)
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self, synthesizer, utterance] in
            guard let self, self.synthesizer === synthesizer,
                  var current = self.pending, current.utterance === utterance,
                  !current.started else { return }
            current.started = true
            self.pending = current
            current.onStart()
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self, synthesizer, utterance] in
            guard let self, self.synthesizer === synthesizer,
                  let current = self.pending, current.utterance === utterance else { return }
            let result: Result<Void, Error> = current.started
                ? .success(())
                : .failure(ServiceError("A voz terminou sem confirmar o início. Confira o volume e a voz baixada e teste novamente em modo avião."))
            self.finish(utteranceID: ObjectIdentifier(utterance), result: result)
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self, synthesizer, utterance] in
            guard let self, self.synthesizer === synthesizer else { return }
            self.finish(utteranceID: ObjectIdentifier(utterance), result: .failure(ServiceError("A reprodução da tradução foi interrompida. Confira a saída de áudio e reproduza novamente.")))
        }
    }

    private func finish(utteranceID: ObjectIdentifier, result: Result<Void, Error>) {
        guard let current = pending, ObjectIdentifier(current.utterance) == utteranceID else { return }
        pending = nil
        AppleAudioSession.release(owner: sessionOwner)
        current.onFinish(result)
    }

    private func interrupt() {
        guard let current = pending else { return }
        pending = nil
        synthesizer.stopSpeaking(at: .immediate)
        AppleAudioSession.release(owner: sessionOwner)
        current.onFinish(.failure(ServiceError("A voz foi interrompida pelo sistema. Aguarde o fim da interrupção e reproduza novamente.")))
    }

    private func resetAfterMediaServicesRestart() {
        let current = pending
        pending = nil
        synthesizer.delegate = nil
        synthesizer.stopSpeaking(at: .immediate)
        AppleAudioSession.release(owner: sessionOwner)
        synthesizer = AVSpeechSynthesizer()
        synthesizer.delegate = self
        synthesizer.usesApplicationAudioSession = true
        current?.onFinish(.failure(ServiceError("Os serviços de áudio do iOS foram reiniciados. Reproduza a tradução novamente.")))
    }

    private func canonical(_ identifier: String) -> String {
        identifier.replacingOccurrences(of: "_", with: "-").lowercased()
    }
}
