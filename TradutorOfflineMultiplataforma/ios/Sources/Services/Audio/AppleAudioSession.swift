import AVFoundation
import Foundation

/// Os adaptadores de gravação, reprodução e voz compartilham a sessão do app.
/// Uma conclusão antiga só pode liberar a sessão que o próprio adaptador possui.
@MainActor
enum AppleAudioSession {
    private static var owners: Set<UUID> = []

    static var hasActiveOwner: Bool { !owners.isEmpty }

    static func activateRecording(owner: UUID) throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try session.setActive(true)
        owners.insert(owner)
    }

    static func activatePlayback(owner: UUID) throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .spokenAudio)
        try session.setActive(true)
        owners.insert(owner)
    }

    static func release(owner: UUID) {
        guard owners.remove(owner) != nil, owners.isEmpty else { return }
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }
}
