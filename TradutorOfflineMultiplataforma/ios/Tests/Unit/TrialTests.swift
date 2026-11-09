import Foundation
import XCTest
@testable import TradutorCore

final class TrialTests: XCTestCase {
    private let date = Date(timeIntervalSince1970: 1_700_000_000.125)

    private func trial() -> Trial {
        Trial(date: date, device: "dispositivo de teste unitário", system: "teste",
              direction: .portugueseToEnglish, input: "Olá", output: "Hello",
              rawInput: "Olá", origin: .microphone)
    }

    func testAllSchemaKeysArePresentWithExplicitNullsAndISOTimestamp() throws {
        let record = trial()
        let data = try JSONEncoder().encode(record)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(object.keys), Set([
            "id", "date", "platform", "device", "system", "direction", "input", "output",
            "rawInput", "edited", "origin", "airplaneModeConfirmed", "failure", "notes",
            "transcriptionMS", "textMS", "audioStartMS", "translationMS",
            "transcriptionQuality", "translationQuality"
        ]))
        for key in ["failure", "transcriptionMS", "textMS", "audioStartMS", "translationMS"] {
            XCTAssertTrue(object[key] is NSNull, key)
        }
        XCTAssertEqual(object["date"] as? String, "2023-11-14T22:13:20.125Z")
        XCTAssertEqual(object["platform"] as? String, "ios")
        XCTAssertEqual(try JSONDecoder().decode(Trial.self, from: data), record)
    }

    func testISOTimeZoneAndWholeSecondsAreAccepted() throws {
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(trial())) as? [String: Any])
        object["date"] = "2023-11-14T19:13:20-03:00"
        let decoded = try JSONDecoder().decode(Trial.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertEqual(decoded.date.timeIntervalSince1970, 1_700_000_000, accuracy: 0.001)
    }

    func testMalformedModernRecordsDoNotMasqueradeAsLegacy() throws {
        let valid = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(trial())) as? [String: Any])
        var missingNullable = valid
        missingNullable.removeValue(forKey: "failure")
        var extraKey = valid
        extraKey["invented"] = true
        var missingTimezone = valid
        missingTimezone["date"] = "2023-11-14T22:13:20"
        var negativeDuration = valid
        negativeDuration["translationMS"] = -1
        var invalidPlatform = valid
        invalidPlatform["platform"] = "unknown"
        for object in [missingNullable, extraKey, missingTimezone, negativeDuration, invalidPlatform] {
            let data = try JSONSerialization.data(withJSONObject: object)
            XCTAssertThrowsError(try JSONDecoder().decode(Trial.self, from: data))
        }
    }

    func testInvalidMetricsCannotBeExported() {
        for value in [-1.0, Double.infinity, Double.nan] {
            var record = trial()
            record.translationMS = value
            XCTAssertThrowsError(try JSONEncoder().encode(record))
        }
    }

    func testRoundPreservesRawInputAndMonotonicOriginAcrossManualReview() {
        let round = RoundContext(date: date, direction: .englishToPortuguese, origin: .importedAudio,
                                 rawInput: "I need water", referenceTime: 100, transcriptionMS: 200)
        let record = round.makeTrial(input: "I need some water", output: "Preciso de água",
                                     airplaneModeConfirmed: true, device: "teste", system: "teste",
                                     now: 103.5, translationMS: 25)
        XCTAssertEqual(record.date, date)
        XCTAssertEqual(record.direction, .englishToPortuguese)
        XCTAssertEqual(record.origin, .importedAudio)
        XCTAssertEqual(record.rawInput, "I need water")
        XCTAssertEqual(record.input, "I need some water")
        XCTAssertTrue(record.edited)
        XCTAssertTrue(record.airplaneModeConfirmed)
        XCTAssertEqual(record.transcriptionMS, 200)
        XCTAssertEqual(record.textMS, 3_500)
        XCTAssertEqual(record.translationMS, 25)
        XCTAssertNil(record.audioStartMS)
        // A revised translation is a distinct attempt, with the original round metadata.
        let repeated = round.makeTrial(input: "I need water", output: "Preciso de água",
                                       airplaneModeConfirmed: false, device: "teste", system: "teste",
                                       now: 105)
        XCTAssertNotEqual(repeated.id, record.id)
        XCTAssertEqual(repeated.date, record.date)
        XCTAssertEqual(repeated.textMS, 5_000)
        XCTAssertFalse(repeated.edited)
    }

    func testTypedInputCannotInheritSpeechMetricsOrEditedFlag() {
        let round = RoundContext(date: date, direction: .portugueseToEnglish, origin: .typedText,
                                 rawInput: "stale", referenceTime: 100, transcriptionMS: 300)
        let record = round.makeTrial(input: "Texto digitado", output: "Typed text",
                                     airplaneModeConfirmed: false, device: "teste", system: "teste",
                                     now: 110, translationMS: 45)
        XCTAssertEqual(record.rawInput, "Texto digitado")
        XCTAssertFalse(record.edited)
        XCTAssertNil(record.transcriptionMS)
        XCTAssertNil(record.textMS)
        XCTAssertNil(record.audioStartMS)
        XCTAssertEqual(record.translationMS, 45)
    }

    func testNewRoundStartsWithoutStaleMetadataOrTimes() {
        let previous = RoundContext(direction: .portugueseToEnglish, origin: .microphone,
                                    rawInput: "fala anterior", referenceTime: 100, transcriptionMS: 500)
        let next = RoundContext(direction: .englishToPortuguese, origin: .importedAudio)
        XCTAssertNotEqual(previous.id, next.id)
        XCTAssertEqual(next.rawInput, "")
        XCTAssertNil(next.referenceTime)
        XCTAssertNil(next.transcriptionMS)
        let record = next.makeTrial(input: "", output: "", airplaneModeConfirmed: false,
                                   device: "teste", system: "teste", now: 150, failure: "Falha de ASR")
        XCTAssertEqual(record.origin, .importedAudio)
        XCTAssertEqual(record.direction, .englishToPortuguese)
        XCTAssertNil(record.transcriptionMS)
        XCTAssertNil(record.textMS)
        XCTAssertNil(record.translationMS)
        XCTAssertEqual(record.failure, "Falha de ASR")
    }

    func testClockAnomalyAndMissingMeasurementStayNullInsteadOfZero() {
        let round = RoundContext(direction: .portugueseToEnglish, origin: .microphone,
                                 referenceTime: 100, transcriptionMS: -1)
        XCTAssertNil(round.elapsedMS(at: 99))
        XCTAssertNil(round.elapsedMS(at: .infinity))
        let failure = round.makeTrial(input: "", output: "", airplaneModeConfirmed: false,
                                      device: "teste", system: "teste", now: 103,
                                      translationMS: .nan, failure: "timeout")
        XCTAssertNil(failure.transcriptionMS)
        XCTAssertNil(failure.textMS)
        XCTAssertNil(failure.translationMS)
    }

    func testStateAndLanguageContracts() {
        XCTAssertFalse(ConversationState.ready.isBusy)
        XCTAssertFalse(ConversationState.failed("erro").isBusy)
        for state in [ConversationState.preparing, .recording, .transcribing, .translating, .reproducing] {
            XCTAssertTrue(state.isBusy)
        }
        XCTAssertEqual(Direction.portugueseToEnglish.sourceLocale, "pt-BR")
        XCTAssertEqual(Direction.portugueseToEnglish.targetLanguageIdentifier, "en")
        XCTAssertEqual(Direction.englishToPortuguese.targetLocale, "pt-BR")
        XCTAssertEqual(Direction.englishToPortuguese.sourceLanguageIdentifier, "en")
    }
}
