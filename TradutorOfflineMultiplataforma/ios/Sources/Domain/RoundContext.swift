import Foundation

/// Created anew for recording, import, or typed input. Its ID also guards async callbacks.
/// `referenceTime` and `now` must come from the same monotonic clock (systemUptime).
public struct RoundContext: Equatable {
    public let id: UUID
    public let date: Date
    public let direction: Direction
    public let origin: InputOrigin
    public var rawInput: String
    public var referenceTime: TimeInterval?
    public var transcriptionMS: Double?

    public init(id: UUID = UUID(), date: Date = Date(), direction: Direction,
                origin: InputOrigin, rawInput: String = "",
                referenceTime: TimeInterval? = nil, transcriptionMS: Double? = nil) {
        self.id = id
        self.date = date
        self.direction = direction
        self.origin = origin
        self.rawInput = rawInput
        self.referenceTime = referenceTime
        self.transcriptionMS = transcriptionMS
    }

    public func elapsedMS(at now: TimeInterval) -> Double? {
        guard origin != .typedText, let referenceTime,
              referenceTime.isFinite, now.isFinite, now >= referenceTime else { return nil }
        return Self.validMS((now - referenceTime) * 1_000)
    }

    public func makeTrial(input: String, output: String, airplaneModeConfirmed: Bool,
                          device: String, system: String, now: TimeInterval,
                          translationMS: Double? = nil, failure: String? = nil) -> Trial {
        let typed = origin == .typedText
        let hasSpeechReference = !typed && referenceTime?.isFinite == true
        return Trial(date: date, device: device, system: system, direction: direction,
                     input: input, output: output, rawInput: typed ? input : rawInput,
                     edited: !typed && input != rawInput, origin: origin,
                     airplaneModeConfirmed: airplaneModeConfirmed, failure: failure,
                     transcriptionMS: hasSpeechReference ? Self.validMS(transcriptionMS) : nil,
                     textMS: failure == nil ? elapsedMS(at: now) : nil,
                     translationMS: Self.validMS(translationMS))
    }

    private static func validMS(_ value: Double?) -> Double? {
        guard let value, value.isFinite, value >= 0 else { return nil }
        return value
    }
}
