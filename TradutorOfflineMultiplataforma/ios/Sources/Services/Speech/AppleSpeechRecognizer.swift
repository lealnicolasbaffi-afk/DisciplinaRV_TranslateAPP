import Foundation
import Speech

@MainActor
final class AppleSpeechRecognizer: SpeechRecognizing {
    private var recognizer: SFSpeechRecognizer?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var continuation: CheckedContinuation<String, Error>?
    private var generation = UUID()

    func requestPermission() async throws {
        try Task.checkCancellation()
        let status = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status)
            }
        }
        try Task.checkCancellation()
        guard status == .authorized else {
            throw ServiceError("Permita Reconhecimento de Fala em Ajustes → Privacidade e Segurança. Se houver restrição do aparelho, remova-a antes de repetir.")
        }
    }

    func validateAvailability(locale: String) throws {
        _ = try availableRecognizer(locale: locale)
    }

    func transcribe(url: URL, locale: String) async throws -> String {
        cancel()
        try Task.checkCancellation()
        guard SFSpeechRecognizer.authorizationStatus() == .authorized else {
            throw ServiceError("Permita Reconhecimento de Fala antes de transcrever o áudio.")
        }
        guard url.isFileURL, FileManager.default.isReadableFile(atPath: url.path) else {
            throw ServiceError("Áudio local indisponível. Grave novamente ou importe um arquivo baixado no iPhone.")
        }
        let localRecognizer = try availableRecognizer(locale: locale)
        let token = UUID()
        generation = token

        return try await withTaskCancellationHandler {
            try Task.checkCancellation()
            return try await withCheckedThrowingContinuation { continuation in
                // A tarefa pode ser cancelada antes de instalar a continuação.
                guard !Task.isCancelled, generation == token else {
                    continuation.resume(throwing: CancellationError())
                    return
                }
                self.continuation = continuation
                self.recognizer = localRecognizer
                let request = SFSpeechURLRecognitionRequest(url: url)
                // Nunca remover esta exigência para contornar um erro local.
                request.requiresOnDeviceRecognition = true
                request.shouldReportPartialResults = false
                recognitionTask = localRecognizer.recognitionTask(with: request) { [weak self] result, error in
                    // Transferir valores, sem reter o resultado do framework no actor.
                    let finalText = result?.isFinal == true ? result?.bestTranscription.formattedString : nil
                    let message = error?.localizedDescription
                    Task { @MainActor in
                        guard let self, self.generation == token else { return }
                        if let finalText {
                            let text = finalText.trimmingCharacters(in: .whitespacesAndNewlines)
                            if text.isEmpty {
                                self.finish(token: token, result: .failure(ServiceError("Nenhuma fala foi reconhecida. Confira o áudio e grave uma frase curta novamente.")))
                            } else {
                                self.finish(token: token, result: .success(text))
                            }
                        } else if let message {
                            self.finish(token: token, result: .failure(ServiceError("Falha no reconhecimento local: \(message). Confira a disponibilidade de \(locale) no iPhone e tente novamente.")))
                        }
                    }
                }
            }
        } onCancel: { [weak self] in
            Task { @MainActor in
                self?.cancel(generation: token)
            }
        }
    }

    func cancel() {
        let token = generation
        finish(token: token, result: .failure(CancellationError()))
    }

    private func cancel(generation token: UUID) {
        guard generation == token else { return }
        finish(token: token, result: .failure(CancellationError()))
    }

    private func availableRecognizer(locale: String) throws -> SFSpeechRecognizer {
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: locale)),
              recognizer.supportsOnDeviceRecognition else {
            throw ServiceError("Reconhecimento local indisponível para \(locale). Prepare esse idioma no iPhone e teste novamente; o app não usa reconhecimento remoto.")
        }
        guard recognizer.isAvailable else {
            throw ServiceError("Reconhecimento de \(locale) indisponível no momento. Confira os recursos locais de fala e repita no aparelho.")
        }
        return recognizer
    }

    private func finish(token: UUID, result: Result<String, Error>) {
        guard generation == token else { return }
        // Invalidar antes de cancelar: o framework pode emitir outro callback.
        generation = UUID()
        let pending = continuation
        continuation = nil
        let task = recognitionTask
        recognitionTask = nil
        recognizer = nil
        task?.cancel()
        pending?.resume(with: result)
    }
}
