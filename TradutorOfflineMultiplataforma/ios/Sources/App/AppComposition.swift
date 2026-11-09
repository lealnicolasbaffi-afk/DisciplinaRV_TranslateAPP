import Foundation
import UIKit
import Darwin

@MainActor
enum AppComposition {
    static func makeConversation() -> ConversationModel {
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return ConversationModel(
            speech: AppleSpeechRecognizer(),
            audio: AppleAudioService(directory: directory.appendingPathComponent("ConversationAudio", isDirectory: true)),
            synthesis: AppleSpeechSynthesizer(), translation: AppleTranslationService(),
            store: JSONTrialStore(directory: directory), device: deviceDescription,
            system: "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)"
        )
    }

    // UIDevice.model alone is generic; keep the actual hardware identifier.
    // Confirm the commercial name in Settings on the physical test phone.
    private static var deviceDescription: String {
        var value = utsname()
        guard uname(&value) == 0 else { return "\(UIDevice.current.model) (modelo físico não confirmado)" }
        let capacity = MemoryLayout.size(ofValue: value.machine)
        let machine = withUnsafePointer(to: &value.machine) { pointer in
            pointer.withMemoryRebound(to: CChar.self, capacity: capacity) { String(cString: $0) }
        }
        #if targetEnvironment(simulator)
        return "Simulador (\(machine)); aparelho físico não validado"
        #else
        return "\(UIDevice.current.model) (hardware: \(machine))"
        #endif
    }
}
