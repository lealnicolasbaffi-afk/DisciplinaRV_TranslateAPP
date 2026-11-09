import Foundation

@MainActor
public protocol TrialStoring {
    var exportURL: URL { get }
    func load() throws -> [Trial]
    func save(_ trials: [Trial]) throws
}

/// Never treats an unreadable/invalid existing file as an empty history.
@MainActor
public final class JSONTrialStore: TrialStoring {
    public let exportURL: URL
    public let backupURL: URL
    public let legacyBackupURL: URL
    private let directory: URL
    private let fileManager: FileManager

    public init(directory: URL, fileManager: FileManager = .default) {
        self.directory = directory
        self.fileManager = fileManager
        exportURL = directory.appendingPathComponent("medicoes.json")
        backupURL = directory.appendingPathComponent("medicoes.backup.json")
        legacyBackupURL = directory.appendingPathComponent("medicoes.legacy.backup.json")
    }

    public func load() throws -> [Trial] {
        guard fileManager.fileExists(atPath: exportURL.path) else { return [] }
        let data = try Data(contentsOf: exportURL)
        let result = try Self.decode(data)
        if result.wasLegacy {
            let encoded = try Self.encode(result.trials)
            try preserveLegacy(data)
            try encoded.write(to: exportURL, options: .atomic)
        }
        return result.trials
    }

    public func save(_ trials: [Trial]) throws {
        // Finish validation before changing any files.
        let encoded = try Self.encode(trials)
        if fileManager.fileExists(atPath: exportURL.path) {
            let existing = try Data(contentsOf: exportURL)
            let old = try Self.decode(existing)
            if old.wasLegacy { try preserveLegacy(existing) }
            try existing.write(to: backupURL, options: .atomic)
        } else {
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        try encoded.write(to: exportURL, options: .atomic)
    }

    private func preserveLegacy(_ data: Data) throws {
        if fileManager.fileExists(atPath: legacyBackupURL.path) {
            if try Data(contentsOf: legacyBackupURL) == data { return }
            // Preserve a previous migration's backup too.
            let unique = directory.appendingPathComponent("medicoes.legacy.\(UUID().uuidString).backup.json")
            try data.write(to: unique, options: .atomic)
        } else {
            try data.write(to: legacyBackupURL, options: .atomic)
        }
    }

    private static func encode(_ trials: [Trial]) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(trials)
    }

    private static func decode(_ data: Data) throws -> (trials: [Trial], wasLegacy: Bool) {
        let object = try JSONSerialization.jsonObject(with: data)
        guard let rows = object as? [[String: Any]] else {
            throw StoreError.invalidRoot
        }
        let decoder = JSONDecoder()
        var migrated = false
        let trials = try rows.map { row -> Trial in
            let rowData = try JSONSerialization.data(withJSONObject: row)
            // Only the specific original format is eligible for migration.
            if row["platform"] == nil, row["rawInput"] == nil, row["date"] is NSNumber {
                let legacy = try decoder.decode(LegacyTrial.self, from: rowData)
                migrated = true
                return legacy.migrated()
            }
            return try decoder.decode(Trial.self, from: rowData)
        }
        // Also validate migrated values before exposing them or rewriting the file.
        _ = try encode(trials)
        return (trials, migrated)
    }
}

private enum StoreError: LocalizedError {
    case invalidRoot
    var errorDescription: String? { "O arquivo de medições precisa conter um array JSON válido." }
}

/// Foundation's original default Date coding uses seconds since 2001-01-01 UTC.
private struct LegacyTrial: Decodable {
    let id: UUID
    let date: Date
    let device: String
    let system: String
    let direction: Direction
    let input: String
    let output: String
    let transcriptionMS: Double?
    let textMS: Double?
    let audioStartMS: Double?
    let translationMS: Double?
    let edited: Bool
    let origin: InputOrigin
    let airplaneModeConfirmed: Bool
    let transcriptionQuality: Quality
    let translationQuality: Quality
    let notes: String
    let failure: String?

    func migrated() -> Trial {
        let migrationNote = "Migração do formato anterior: rawInput indisponível; copiado de input."
        return Trial(id: id, date: date, device: device, system: system, direction: direction,
                     input: input, output: output, rawInput: input,
                     edited: origin == .typedText ? false : edited, origin: origin,
                     airplaneModeConfirmed: airplaneModeConfirmed, failure: failure,
                     notes: notes.isEmpty ? migrationNote : notes + "\n" + migrationNote,
                     transcriptionMS: origin == .typedText ? nil : transcriptionMS,
                     textMS: origin == .typedText ? nil : textMS,
                     audioStartMS: origin == .typedText ? nil : audioStartMS,
                     translationMS: translationMS, transcriptionQuality: transcriptionQuality,
                     translationQuality: translationQuality)
    }
}
