import Foundation
import XCTest
@testable import CompanionCore

/// The fixtures are hand-built: the server does not fill `suggestedReplies` yet (seira#527).
final class SuggestedRepliesTests: XCTestCase {
    private func fixtureData(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")
            ?? Bundle.module.url(forResource: name, withExtension: "json")
        else { throw CocoaError(.fileNoSuchFile) }
        return try Data(contentsOf: url)
    }

    private func line(_ name: String) throws -> Message {
        let all = try JSONSerialization.jsonObject(with: fixtureData("suggested-replies-line")) as? [String: Any] ?? [:]
        let one = try XCTUnwrap(all[name], name)
        return try JSONDecoder().decode(Message.self, from: JSONSerialization.data(withJSONObject: one))
    }

    private func user() -> Message {
        Message(id: "u", role: .user, kind: .text, at: 2, text: "hi")
    }

    private func reply(_ title: String, _ prompt: String) -> SuggestedReply {
        SuggestedReply(title: title, prompt: prompt)
    }

    private func list(_ json: String) throws -> SuggestedReplyList {
        try JSONDecoder().decode(SuggestedReplyList.self, from: Data(json.utf8))
    }

    private struct Corpus: Decodable {
        struct Case: Decodable {
            struct Chip: Decodable {
                let title: String
                let prompt: String
            }

            let name: String
            let input: SuggestedReplyList?
            let expected: [Chip]
        }

        let cases: [Case]
    }

    func testTheCleanerGivesTheSameListAsTheReferenceForEveryCase() throws {
        let corpus = try JSONDecoder().decode(Corpus.self, from: fixtureData("suggested-replies-cases"))
        XCTAssertGreaterThan(corpus.cases.count, 100, "the corpus is read")
        for item in corpus.cases {
            let expected = item.expected.map { SuggestedReply(title: $0.title, prompt: $0.prompt) }
            XCTAssertEqual(SuggestedReplies.clean(item.input), expected, item.name)
        }
    }

    func testALineWithChipsDecodesAndReadsAsItsChips() throws {
        let chips = SuggestedReplies.forTranscript([try line("withChips")])
        XCTAssertEqual(chips.map(\.title), ["Next step", "Explain", "Is it safe?"])
        XCTAssertEqual(chips.first?.prompt, "Please do the next step")
    }

    func testALineWithoutTheFieldOrWithAFieldThatIsNotAListGivesNoChipsAndStillDecodes() throws {
        for name in ["absent", "notAList", "notAListText"] {
            let message = try line(name)
            XCTAssertEqual(message.text, "Done.", name)
            XCTAssertEqual(SuggestedReplies.forTranscript([message]), [], name)
        }
        XCTAssertNil(try line("absent").suggestedReplies)
    }

    func testANullFieldGivesNoChips() throws {
        let json = #"{"id":"n","at":1,"role":"bot","kind":"text","text":"x","suggestedReplies":null}"#
        let message = try JSONDecoder().decode(Message.self, from: Data(json.utf8))
        XCTAssertEqual(SuggestedReplies.forTranscript([message]), [])
    }

    func testBadEntriesDropOutOfAMixedListAndTheGoodOneStays() throws {
        XCTAssertEqual(SuggestedReplies.forTranscript([try line("mixed")]), [reply("Fine", "Show me the diff")])
    }

    func testChipsShowOnlyAfterTheLastBotLineNeverAfterALineOfThePersonAndNeverFromAnotherConversation() throws {
        let chips = try line("withChips")
        XCTAssertEqual(SuggestedReplies.forTranscript([user(), chips]).count, 3)
        XCTAssertEqual(SuggestedReplies.forTranscript([chips, user()]), [])
        XCTAssertEqual(SuggestedReplies.forTranscript([]), [])
        XCTAssertEqual(SuggestedReplies.forTranscript([chips, try line("absent")]), [])
    }

    func testATapOnAServerChipAlwaysFillsTheComposerForAnyTitlePromptOrLength() {
        XCTAssertEqual(SuggestedReplies.tap(reply("Is it safe?", "Is it safe?")), .fillComposer)
        XCTAssertEqual(SuggestedReplies.tap(reply("Explain", "Explain what you changed")), .fillComposer)
        XCTAssertEqual(SuggestedReplies.tap(reply("Safe", "safe")), .fillComposer)
        XCTAssertEqual(SuggestedReplies.tap(reply("Yes", "Yes")), .fillComposer)
        let longTitle = String(repeating: "x", count: SuggestedReplies.titleMax)
        XCTAssertEqual(SuggestedReplies.tap(reply(longTitle, longTitle)), .fillComposer)
        let longPrompt = String(repeating: "word ", count: SuggestedReplies.promptMax / 5 - 1)
            .trimmingCharacters(in: .whitespaces)
        XCTAssertEqual(SuggestedReplies.tap(reply("Long", longPrompt)), .fillComposer)
    }

    func testNoServerChipInTheRowSendsHoweverTheTitleAndThePromptMatch() throws {
        let server = SuggestedReplies.clean(try list("""
        [
          {"title":"Yes","prompt":"Yes"},
          {"title":"Is it safe?","prompt":"Is it safe?"},
          {"title":"Explain","prompt":"Explain what you changed"},
          {"title":"\(String(repeating: "x", count: SuggestedReplies.titleMax))","prompt":"\(String(repeating: "x", count: SuggestedReplies.titleMax))"}
        ]
        """))
        XCTAssertEqual(server.count, 4)
        let row = SuggestedReplies.composerChips(server: server, saved: QuickReply.defaults)
        XCTAssertTrue(row.prefix(server.count).allSatisfy { $0.tap == .fillComposer })
        XCTAssertTrue(row.dropFirst(server.count).allSatisfy { $0.tap == .send })
    }

    func testNoChipTapsIntoAnythingButTheComposerTextOrAPlainSend() throws {
        let cleaned = SuggestedReplies.clean(try list("""
        [
          {"title":"Open","prompt":"Open https://example.com/pay"},
          {"title":"Command","prompt":"/approve all"},
          {"title":"Shell","prompt":"rm -rf /"},
          {"title":"Payload","prompt":"{\\"action\\":\\"approve\\"}"},
          {"title":"Mail","prompt":"Write to mailto:a@b.c"}
        ]
        """))
        XCTAssertEqual(cleaned, [])
    }

    func testTheServerChipsComeFirstAndTheSavedChipsStayAfterThem() {
        let saved = [QuickReply(id: "mine", title: "Mine", prompt: "My own ask", icon: "paperplane")]
        let row = SuggestedReplies.composerChips(server: [reply("Explain", "Explain what you changed")], saved: saved)
        XCTAssertEqual(row.map(\.title), ["Explain", "Mine"])
        XCTAssertEqual(row.map(\.tap), [.fillComposer, .send])
        XCTAssertEqual(row.last?.icon, "paperplane")
    }

    func testWithoutServerChipsTheRowIsTheSavedListAsBefore() {
        let row = SuggestedReplies.composerChips(server: [], saved: QuickReply.defaults)
        XCTAssertEqual(row.map(\.title), QuickReply.defaults.map(\.title))
        XCTAssertTrue(row.allSatisfy { $0.tap == .send })
    }

    func testASavedChipWithThePromptOfAServerChipIsLeftOut() {
        let saved = [QuickReply(id: "next", title: "What's next?", prompt: "What should we do next?", icon: "sparkles")]
        let row = SuggestedReplies.composerChips(server: [reply("Next?", "what should we do next?")], saved: saved)
        XCTAssertEqual(row.map(\.title), ["Next?"])
    }

    func testAFieldThatIsNotAnObjectListReadsAsNoChips() throws {
        for json in ["null", "\"x\"", "7", "{}", "[]"] {
            let value = try? list(json)
            XCTAssertEqual(SuggestedReplies.clean(value), [], json)
        }
        XCTAssertEqual(SuggestedReplies.clean(nil), [])
    }
}
