import Foundation
import Translation

/// TranslationSession pertence ao ciclo de vida da view que fornece translationTask.
/// O serviço deliberadamente não armazena sessões nem usa APIs posteriores ao iOS 18.
@MainActor
final class AppleTranslationService: TranslationServicing {
    func prepare(using session: TranslationSession, direction: Direction) async throws {
        try Task.checkCancellation()
        try validate(session: session, direction: direction)
        let before = await status(for: direction)
        try Task.checkCancellation()
        switch before {
        case .installed:
            return
        case .unsupported:
            throw unsupported(direction)
        case .supported:
            break
        @unknown default:
            throw ServiceError("Não foi possível confirmar os idiomas de tradução. Atualize os idiomas no iOS e tente novamente.")
        }

        do {
            try await session.prepareTranslation()
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw ServiceError("Falha ao preparar a tradução: \(error.localizedDescription). Conecte-se à internet e permita baixar os idiomas.")
        }
        try Task.checkCancellation()
        // prepareTranslation também retorna quando já existe um download em curso.
        // Seu retorno, isoladamente, não é confirmação de modelos instalados.
        let after = await status(for: direction)
        try Task.checkCancellation()
        switch after {
        case .installed:
            return
        case .supported:
            throw ServiceError("O download dos idiomas ainda não terminou. Mantenha a internet conectada e toque em Preparar idiomas novamente até confirmar a instalação.")
        case .unsupported:
            throw unsupported(direction)
        @unknown default:
            throw ServiceError("Não foi possível confirmar a instalação. Confira os idiomas baixados nos Ajustes do app Traduzir e tente novamente.")
        }
    }

    func translate(_ text: String, using session: TranslationSession, direction: Direction) async throws -> TranslationResult {
        try Task.checkCancellation()
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw ServiceError("Grave ou digite uma frase antes de traduzir.")
        }
        try validate(session: session, direction: direction)
        let availability = await status(for: direction)
        try Task.checkCancellation()
        guard availability == .installed else {
            if availability == .unsupported { throw unsupported(direction) }
            throw ServiceError("Pacotes de tradução ausentes. Use Preparar idiomas com internet e aguarde a confirmação antes do modo avião.")
        }
        do {
            let began = ProcessInfo.processInfo.systemUptime
            let response = try await session.translate(text)
            let durationMS = (ProcessInfo.processInfo.systemUptime - began) * 1_000
            try Task.checkCancellation()
            guard !response.targetText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                throw ServiceError("A tradução retornou vazia. Revise a frase e tente novamente.")
            }
            return TranslationResult(text: response.targetText, translationMS: durationMS)
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as ServiceError {
            throw error
        } catch {
            throw ServiceError("Falha na tradução local: \(error.localizedDescription). Confira os pacotes instalados e tente novamente.")
        }
    }

    private func status(for direction: Direction) async -> LanguageAvailability.Status {
        await LanguageAvailability().status(
            from: Locale.Language(identifier: direction.sourceLanguageIdentifier),
            to: Locale.Language(identifier: direction.targetLanguageIdentifier)
        )
    }

    private func validate(session: TranslationSession, direction: Direction) throws {
        guard session.sourceLanguage?.languageCode?.identifier == direction.sourceLanguageIdentifier,
              session.targetLanguage?.languageCode?.identifier == direction.targetLanguageIdentifier else {
            throw ServiceError("A sessão de tradução não corresponde à direção selecionada. Selecione a direção novamente e repita.")
        }
    }

    private func unsupported(_ direction: Direction) -> ServiceError {
        ServiceError("Tradução local indisponível para \(direction.sourceLocale) → \(direction.targetLocale) neste iOS. Confira os idiomas compatíveis no aparelho.")
    }
}
