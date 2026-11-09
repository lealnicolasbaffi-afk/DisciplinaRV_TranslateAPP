import Foundation
import Translation

/// Falhas exibíveis ao usuário; cada mensagem indica como repetir a operação.
struct ServiceError: LocalizedError, Equatable {
    let message: String

    init(_ message: String) { self.message = message }

    var errorDescription: String? { message }
}

@MainActor
protocol SpeechRecognizing: AnyObject {
    func requestPermission() async throws
    func validateAvailability(locale: String) throws
    func transcribe(url: URL, locale: String) async throws -> String
    func cancel()
}

@MainActor
protocol AudioServicing: AnyObject {
    /// Só o término natural dispara este callback. Parada manual retorna a URL.
    var onRecordingFinished: ((Result<URL, Error>) -> Void)? { get set }
    var onPlaybackFinished: ((Result<Void, Error>) -> Void)? { get set }
    var onInterruption: (() -> Void)? { get set }

    func requestRecordPermission() async throws
    func startRecording() throws -> URL
    func stopRecording() throws -> URL
    func importAudio(_ url: URL) throws -> URL
    func play(_ url: URL) throws
    /// Cancela sem notificar callbacks de conclusão da operação cancelada.
    func cancel()
}

@MainActor
protocol SpeechSynthesizing: AnyObject {
    func speak(
        text: String,
        locale: String,
        onStart: @escaping () -> Void,
        onFinish: @escaping (Result<Void, Error>) -> Void
    ) throws
    /// Cancela sem chamar onFinish da fala descartada.
    func cancel()
}

@MainActor
protocol TranslationServicing: AnyObject {
    /// Recebe a sessão temporária de translationTask; implementações não a retêm.
    func prepare(using session: TranslationSession, direction: Direction) async throws
    func translate(_ text: String, using session: TranslationSession, direction: Direction) async throws -> TranslationResult
}

struct TranslationResult {
    let text: String
    /// Apenas session.translate; exclui checagem de pacotes e preparação.
    let translationMS: Double
}
