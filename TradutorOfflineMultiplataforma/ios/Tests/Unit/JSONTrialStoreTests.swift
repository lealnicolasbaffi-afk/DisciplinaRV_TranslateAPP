import Foundation
import XCTest
@testable import TradutorCore

final class JSONTrialStoreTests: XCTestCase {
    @MainActor
    private func withStore(_ body: (JSONTrialStore, URL) throws -> Void) throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        try body(JSONTrialStore(directory: directory), directory)
    }

    private func record(_ input: String = "Olá") -> Trial {
        Trial(date: Date(timeIntervalSince1970: 1_700_000_000), device: "teste unitário",
              system: "teste", direction: .portugueseToEnglish,
              input: input, output: "Hello", rawInput: input, origin: .microphone)
    }

    private func legacyData(origin: InputOrigin = .microphone) throws -> Data {
        let object: [[String: Any]] = [[
            "id": "00000000-0000-0000-0000-000000000001", "date": 0.0,
            "device": "iPhone", "system": "18.0", "direction": "pt-BR → en-US",
            "input": "Olá revisado", "output": "Hello", "edited": true,
            "origin": origin.rawValue, "airplaneModeConfirmed": true,
            "transcriptionQuality": "compreensível com erros", "translationQuality": "correta",
            "notes": "PT01", "transcriptionMS": 500, "textMS": 700, "audioStartMS": 900
        ]]
        // The original JSONEncoder omitted nulls (including translationMS and failure).
        return try JSONSerialization.data(withJSONObject: object)
    }

    @MainActor
    func testEmptyStoreRoundTripAndBackupOfPreviousValidHistory() async throws {
        try withStore { store, _ in
            XCTAssertEqual(try store.load(), [])
            XCTAssertFalse(FileManager.default.fileExists(atPath: store.exportURL.path))
            let first = record()
            try store.save([first])
            let firstBytes = try Data(contentsOf: store.exportURL)
            let second = record("Segunda tentativa")
            try store.save([first, second])
            XCTAssertEqual(try store.load(), [first, second])
            XCTAssertEqual(try Data(contentsOf: store.backupURL), firstBytes)
        }
    }

    @MainActor
    func testLegacyMigrationPreservesBytesDateEvaluationAndExplainsMissingOriginal() async throws {
        try withStore { store, _ in
            let legacy = try legacyData()
            try legacy.write(to: store.exportURL)
            let migrated = try XCTUnwrap(try store.load().first)
            XCTAssertEqual(migrated.id.uuidString, "00000000-0000-0000-0000-000000000001")
            XCTAssertEqual(migrated.date, Date(timeIntervalSinceReferenceDate: 0))
            XCTAssertEqual(migrated.platform, "ios")
            XCTAssertEqual(migrated.rawInput, migrated.input)
            XCTAssertTrue(migrated.edited)
            XCTAssertEqual(migrated.transcriptionQuality, .understandableWithErrors)
            XCTAssertEqual(migrated.translationQuality, .correct)
            XCTAssertTrue(migrated.notes.hasPrefix("PT01\n"))
            XCTAssertTrue(migrated.notes.contains("rawInput indisponível"))
            XCTAssertEqual(try Data(contentsOf: store.legacyBackupURL), legacy)
            XCTAssertEqual(try JSONDecoder().decode([Trial].self, from: Data(contentsOf: store.exportURL)), [migrated])
            // A second load is stable and never appends another migration note.
            XCTAssertEqual(try store.load(), [migrated])
        }
    }

    @MainActor
    func testTypedLegacyMigrationRemovesInapplicableSpeechTimes() async throws {
        try withStore { store, _ in
            try legacyData(origin: .typedText).write(to: store.exportURL)
            let migrated = try XCTUnwrap(try store.load().first)
            XCTAssertNil(migrated.transcriptionMS)
            XCTAssertNil(migrated.textMS)
            XCTAssertNil(migrated.audioStartMS)
            XCTAssertFalse(migrated.edited)
        }
    }

    @MainActor
    func testCorruptionIsReportedAndNeverOverwrittenByLoadOrSave() async throws {
        try withStore { store, _ in
            for corrupt in [Data("[{broken".utf8), Data("{}".utf8), Data("[{\"date\":0}]".utf8)] {
                try corrupt.write(to: store.exportURL, options: .atomic)
                XCTAssertThrowsError(try store.load())
                XCTAssertThrowsError(try store.save([record()]))
                XCTAssertEqual(try Data(contentsOf: store.exportURL), corrupt)
                XCTAssertFalse(FileManager.default.fileExists(atPath: store.backupURL.path))
                XCTAssertFalse(FileManager.default.fileExists(atPath: store.legacyBackupURL.path))
            }
        }
    }

    @MainActor
    func testInvalidNewValuesCannotReplaceValidHistory() async throws {
        try withStore { store, _ in
            try store.save([record()])
            let previous = try Data(contentsOf: store.exportURL)
            var invalid = record()
            invalid.audioStartMS = -10
            XCTAssertThrowsError(try store.save([invalid]))
            XCTAssertEqual(try Data(contentsOf: store.exportURL), previous)
        }
    }

    @MainActor
    func testSaveDoesNotSilentlyRepairIncompleteModernRecords() async throws {
        try withStore { store, _ in
            let encoded = try JSONEncoder().encode([record()])
            var rows = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [[String: Any]])
            rows[0].removeValue(forKey: "rawInput")
            let invalid = try JSONSerialization.data(withJSONObject: rows)
            try invalid.write(to: store.exportURL)
            XCTAssertThrowsError(try store.load())
            XCTAssertThrowsError(try store.save([]))
            XCTAssertEqual(try Data(contentsOf: store.exportURL), invalid)
        }
    }
}
