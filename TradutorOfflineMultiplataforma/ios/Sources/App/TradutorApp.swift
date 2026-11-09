import SwiftUI

@main
@MainActor
struct TradutorApp: App {
    @StateObject private var model = AppComposition.makeConversation()

    var body: some Scene {
        WindowGroup { ConversationView(model: model) }
    }
}
