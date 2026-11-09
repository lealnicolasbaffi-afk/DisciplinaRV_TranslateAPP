import Foundation

/// The complete v1 shared measurement record. Nullable fields are encoded explicitly.
public struct Trial: Codable, Identifiable, Equatable {
    public var id: UUID
    public var date: Date
    public var platform: String
    public var device: String
    public var system: String
    public var direction: Direction
    public var input: String
    public var output: String
    public var rawInput: String
    public var edited: Bool
    public var origin: InputOrigin
    public var airplaneModeConfirmed: Bool
    public var failure: String?
    public var notes: String
    public var transcriptionMS: Double?
    public var textMS: Double?
    public var audioStartMS: Double?
    public var translationMS: Double?
    public var transcriptionQuality: Quality
    public var translationQuality: Quality

    public init(id: UUID = UUID(), date: Date = Date(), platform: String = "ios",
                device: String, system: String, direction: Direction,
                input: String, output: String, rawInput: String, edited: Bool = false,
                origin: InputOrigin, airplaneModeConfirmed: Bool = false,
                failure: String? = nil, notes: String = "",
                transcriptionMS: Double? = nil, textMS: Double? = nil,
                audioStartMS: Double? = nil, translationMS: Double? = nil,
                transcriptionQuality: Quality = .notEvaluated,
                translationQuality: Quality = .notEvaluated) {
        self.id = id
        self.date = date
        self.platform = platform
        self.device = device
        self.system = system
        self.direction = direction
        self.input = input
        self.output = output
        self.rawInput = rawInput
        self.edited = edited
        self.origin = origin
        self.airplaneModeConfirmed = airplaneModeConfirmed
        self.failure = failure
        self.notes = notes
        self.transcriptionMS = transcriptionMS
        self.textMS = textMS
        self.audioStartMS = audioStartMS
        self.translationMS = translationMS
        self.transcriptionQuality = transcriptionQuality
        self.translationQuality = translationQuality
    }

    private enum CodingKeys: String, CodingKey, CaseIterable {
        case id, date, platform, device, system, direction, input, output, rawInput
        case edited, origin, airplaneModeConfirmed, failure, notes
        case transcriptionMS, textMS, audioStartMS, translationMS
        case transcriptionQuality, translationQuality
    }

    public init(from decoder: Decoder) throws {
        let allKeys = try decoder.container(keyedBy: MeasurementKey.self)
        let expected = Set(CodingKeys.allCases.map(\.rawValue))
        let actual = Set(allKeys.allKeys.map(\.stringValue))
        guard actual == expected else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath,
                debugDescription: "Medição v1 com chaves ausentes ou desconhecidas."))
        }
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        let timestamp = try c.decode(String.self, forKey: .date)
        guard let parsed = MeasurementDate.parse(timestamp) else {
            throw DecodingError.dataCorruptedError(forKey: .date, in: c,
                debugDescription: "A data precisa ser ISO 8601 com timezone.")
        }
        date = parsed
        platform = try c.decode(String.self, forKey: .platform)
        device = try c.decode(String.self, forKey: .device)
        system = try c.decode(String.self, forKey: .system)
        direction = try c.decode(Direction.self, forKey: .direction)
        input = try c.decode(String.self, forKey: .input)
        output = try c.decode(String.self, forKey: .output)
        rawInput = try c.decode(String.self, forKey: .rawInput)
        edited = try c.decode(Bool.self, forKey: .edited)
        origin = try c.decode(InputOrigin.self, forKey: .origin)
        airplaneModeConfirmed = try c.decode(Bool.self, forKey: .airplaneModeConfirmed)
        failure = try c.decodeIfPresent(String.self, forKey: .failure)
        notes = try c.decode(String.self, forKey: .notes)
        transcriptionMS = try c.decodeIfPresent(Double.self, forKey: .transcriptionMS)
        textMS = try c.decodeIfPresent(Double.self, forKey: .textMS)
        audioStartMS = try c.decodeIfPresent(Double.self, forKey: .audioStartMS)
        translationMS = try c.decodeIfPresent(Double.self, forKey: .translationMS)
        transcriptionQuality = try c.decode(Quality.self, forKey: .transcriptionQuality)
        translationQuality = try c.decode(Quality.self, forKey: .translationQuality)
        guard hasValidValues else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath,
                debugDescription: "Plataforma, data ou duração de medição inválida."))
        }
    }

    public func encode(to encoder: Encoder) throws {
        guard hasValidValues else {
            throw EncodingError.invalidValue(self, .init(codingPath: encoder.codingPath,
                debugDescription: "Plataforma, data ou duração de medição inválida."))
        }
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(MeasurementDate.string(date), forKey: .date)
        try c.encode(platform, forKey: .platform)
        try c.encode(device, forKey: .device)
        try c.encode(system, forKey: .system)
        try c.encode(direction, forKey: .direction)
        try c.encode(input, forKey: .input)
        try c.encode(output, forKey: .output)
        try c.encode(rawInput, forKey: .rawInput)
        try c.encode(edited, forKey: .edited)
        try c.encode(origin, forKey: .origin)
        try c.encode(airplaneModeConfirmed, forKey: .airplaneModeConfirmed)
        try c.encode(failure, forKey: .failure)
        try c.encode(notes, forKey: .notes)
        try c.encode(transcriptionMS, forKey: .transcriptionMS)
        try c.encode(textMS, forKey: .textMS)
        try c.encode(audioStartMS, forKey: .audioStartMS)
        try c.encode(translationMS, forKey: .translationMS)
        try c.encode(transcriptionQuality, forKey: .transcriptionQuality)
        try c.encode(translationQuality, forKey: .translationQuality)
    }

    private var hasValidValues: Bool {
        ["ios", "android"].contains(platform) && date.timeIntervalSinceReferenceDate.isFinite &&
        [transcriptionMS, textMS, audioStartMS, translationMS].allSatisfy {
            guard let value = $0 else { return true }
            return value.isFinite && value >= 0
        }
    }
}

private struct MeasurementKey: CodingKey {
    var stringValue: String
    var intValue: Int? { nil }
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}

private enum MeasurementDate {
    static func string(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: date)
    }

    static func parse(_ value: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
