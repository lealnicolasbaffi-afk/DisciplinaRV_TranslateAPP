import Foundation

/// Each UUID creates a view-scoped session with immutable request input.
struct TranslationRequest: Identifiable {
    enum Kind { case prepare, translate(String) }
    let id: UUID
    let direction: Direction
    let kind: Kind
}
