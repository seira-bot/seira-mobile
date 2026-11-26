import XCTest
@testable import CompanionCore

final class ProfileTeamLogicTests: XCTestCase {
    private func makeBot(_ id: String, name: String = "Ada", section: String? = nil, chief: Bool? = nil) -> Bot {
        var bot = Bot(
            id: id, threadId: "\(id)-thread", name: name, title: "Helper",
            description: "Does things", notifications: true, color: "blue", unread: false,
            modelSelection: ModelSelection(instanceId: "i", model: "m"), createdAt: 1
        )
        bot.section = section
        bot.chiefOfStaff = chief
        return bot
    }

    private func decodeBot(_ extra: String) throws -> Bot {
        let json = """
        {
          "id":"b1","threadId":"t1","name":"Dina","title":"","description":"",
          "notifications":true,"color":"pink","unread":false,
          "modelSelection":{"instanceId":"local","model":"default"},"createdAt":1\(extra)
        }
        """
        return try JSONDecoder().decode(Bot.self, from: Data(json.utf8))
    }

    func testABotDecodesWithAndWithoutInstructions() throws {
        let plain = try decodeBot("")
        XCTAssertNil(plain.soul)
        XCTAssertNil(plain.replyStyle)
        let full = try decodeBot(#","soul":"Be brief.","replyStyle":"conversational""#)
        XCTAssertEqual(full.soul, "Be brief.")
        XCTAssertEqual(full.replyStyle, "conversational")
    }

    func testTheDraftStartsFromTheComputersValues() throws {
        let bot = try decodeBot(#","soul":"Be brief.","replyStyle":"conversational""#)
        let draft = ProfileDraft.of(bot)
        XCTAssertEqual(draft.soul, "Be brief.")
        XCTAssertEqual(draft.replyStyle, .conversational)
        XCTAssertFalse(ProfileDraftRules.isDirty(draft, baseline: ProfileDraft.of(bot)))
    }

    func testAnUnknownReplyStyleStaysOutOfTheDraft() throws {
        let bot = try decodeBot(#","replyStyle":"poetic""#)
        XCTAssertNil(ProfileDraft.of(bot).replyStyle)
    }

    func testSaveSendsOnlyChangedFields() {
        let baseline = ProfileDraft(name: "Ada", title: "Helper", description: "Does things")
        var draft = baseline
        draft.title = "  Lead  "
        let patch = ProfileDraftRules.patch(draft, baseline: baseline)
        XCTAssertNil(patch.name)
        XCTAssertEqual(patch.title, "Lead")
        XCTAssertNil(patch.description)
        XCTAssertNil(patch.soul)
        XCTAssertNil(patch.replyStyle)
    }

    func testInstructionsNeverTypedAreNeverSent() {
        let baseline = ProfileDraft(name: "Ada", title: "Helper", description: "")
        var draft = baseline
        draft.name = "Ada B"
        XCTAssertNil(ProfileDraftRules.patch(draft, baseline: baseline).soul)
    }

    func testTypedInstructionsAndReplyStyleAreSent() {
        let baseline = ProfileDraft(name: "Ada", title: "Helper", description: "")
        var draft = baseline
        draft.soul = "Be kind."
        draft.replyStyle = .conversational
        let patch = ProfileDraftRules.patch(draft, baseline: baseline)
        XCTAssertEqual(patch.soul, "Be kind.")
        XCTAssertEqual(patch.replyStyle, .conversational)
    }

    func testSaveNeedsANameAndAChange() {
        let baseline = ProfileDraft(name: "Ada", title: "Helper", description: "")
        XCTAssertFalse(ProfileDraftRules.canSave(baseline, baseline: baseline, busy: false))
        var blank = baseline
        blank.name = "  "
        XCTAssertFalse(ProfileDraftRules.canSave(blank, baseline: baseline, busy: false))
        var changed = baseline
        changed.title = "Lead"
        XCTAssertTrue(ProfileDraftRules.canSave(changed, baseline: baseline, busy: false))
        XCTAssertFalse(ProfileDraftRules.canSave(changed, baseline: baseline, busy: true))
    }

    func testTabKeysFallBackToActivity() {
        XCTAssertEqual(ProfileTab.from(key: "library"), .library)
        XCTAssertEqual(ProfileTab.from(key: "nope"), .activity)
        XCTAssertEqual(ProfileTab.from(key: nil), .activity)
    }

    func testTeamLayoutPutsSeiraFirstThenSectionsThenTheRest() {
        var state = CompanionState()
        state.bots = [
            makeBot("chief", name: "Seira", chief: true),
            makeBot("a", name: "Ada", section: "Sales"),
            makeBot("b", name: "Ben"),
        ]
        let layout = TeamMapRules.layout(state)
        XCTAssertEqual(layout.chief?.id, "chief")
        XCTAssertEqual(layout.groups.map(\.name), ["Sales", nil])
        XCTAssertEqual(layout.groups[0].bots.map(\.id), ["a"])
        XCTAssertEqual(layout.groups[1].bots.map(\.id), ["b"])
    }

    func testTeamLayoutOfNoBotsIsEmpty() {
        XCTAssertTrue(TeamMapRules.layout(CompanionState()).isEmpty)
    }

    func testTeamStatusReadsRunningBeforeQueued() throws {
        let json = """
        {"collaborations":[],
         "running":[{"sourceBotId":"x","targetBotId":"a","threadId":"t"}],
         "queued":[{"sourceBotId":"x","targetBotId":"a","threadId":"t"},{"sourceBotId":"x","targetBotId":"b","threadId":"t"}]}
        """
        let map = try JSONDecoder().decode(TeamMap.self, from: Data(json.utf8))
        XCTAssertEqual(TeamMapRules.status(map, botId: "a"), .working)
        XCTAssertEqual(TeamMapRules.status(map, botId: "b"), .waiting)
        XCTAssertNil(TeamMapRules.status(map, botId: "c"))
        XCTAssertNil(TeamMapRules.status(nil, botId: "a"))
    }

    func testANoteSplitsIntoNameAndDetail() throws {
        let parts = try XCTUnwrap(TeamMemoryRules.split("  Ship on Friday \n\n Ask Ben first "))
        XCTAssertEqual(parts.name, "Ship on Friday")
        XCTAssertEqual(parts.detail, "Ask Ben first")
        XCTAssertNil(TeamMemoryRules.split("  \n "))
    }

    func testALongFirstLineSpillsIntoTheDetail() throws {
        let parts = try XCTUnwrap(TeamMemoryRules.split(String(repeating: "a", count: 130)))
        XCTAssertEqual(parts.name.count, TeamMemoryRules.nameLimit)
        XCTAssertEqual(parts.detail.count, 10)
    }

    func testAnEntryChangedWhenItsRevisionMovedOrItIsGone() throws {
        func entry(_ id: String, _ revision: String) throws -> TeamMemoryEntry {
            let json = """
            {"id":"\(id)","kind":"term","name":"n","detail":"","aliases":[],"status":"accepted",
             "source":{"botId":"b","botName":"Ada","threadId":"t","at":1},"updatedAt":1,"revision":"\(revision)"}
            """
            return try JSONDecoder().decode(TeamMemoryEntry.self, from: Data(json.utf8))
        }
        let mine = try entry("e1", "r1")
        XCTAssertFalse(TeamMemoryRules.changedSince(mine, latest: [try entry("e1", "r1")]))
        XCTAssertTrue(TeamMemoryRules.changedSince(mine, latest: [try entry("e1", "r2")]))
        XCTAssertTrue(TeamMemoryRules.changedSince(mine, latest: []))
    }
}
