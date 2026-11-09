import Foundation

public enum Direction: String, Codable, CaseIterable, Identifiable {
    case portugueseToEnglish = "pt-BR → en-US"
    case englishToPortuguese = "en-US → pt-BR"

    public var id: String { rawValue }
    public var sourceLocale: String { self == .portugueseToEnglish ? "pt-BR" : "en-US" }
    public var targetLocale: String { self == .portugueseToEnglish ? "en-US" : "pt-BR" }
    public var sourceLanguageIdentifier: String { self == .portugueseToEnglish ? "pt" : "en" }
    public var targetLanguageIdentifier: String { self == .portugueseToEnglish ? "en" : "pt" }
}

public enum InputOrigin: String, Codable {
    case microphone = "microfone"
    case importedAudio = "arquivo pré-gravado"
    case typedText = "texto digitado"
}

public enum Quality: String, Codable, CaseIterable, Identifiable {
    case notEvaluated = "não avaliada"
    case correct = "correta"
    case understandableWithErrors = "compreensível com erros"
    case incorrect = "incorreta"

    public var id: String { rawValue }
}

public enum ConversationState: Equatable {
    case ready
    case preparing
    case recording
    case transcribing
    case translating
    case reproducing
    case failed(String)

    public var isBusy: Bool {
        switch self {
        case .ready, .failed: return false
        case .preparing, .recording, .transcribing, .translating, .reproducing: return true
        }
    }
}
