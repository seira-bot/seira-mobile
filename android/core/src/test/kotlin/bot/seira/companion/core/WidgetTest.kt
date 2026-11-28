package bot.seira.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WidgetTest {
    private fun decode(json: String): Message = CompanionJson.decodeFromString(json)

    private fun node(json: String): JsonObject = CompanionJson.parseToJsonElement(json).jsonObject

    private val cardJson = """
        {"v":1,"rev":2,"root":{"type":"Card","id":"n1","title":"Plan trip","caption":"Pick dates","meta":"now",
         "status":{"text":"Needs you","icon":"info"},
         "confirm":{"label":"Send","action":{"type":"trip.send"}},
         "cancel":{"label":"Skip","action":{"type":"trip.skip"}},
         "children":[
          {"type":"Title","id":"n2","value":"Rome or Paris"},
          {"type":"Text","id":"n3","value":"Two good options"},
          {"type":"Table","id":"n4","children":[
            {"type":"Table.Row","id":"n5","header":true,"children":[
              {"type":"Table.Cell","id":"n6","children":[{"type":"Text","id":"n7","value":"City"}]},
              {"type":"Table.Cell","id":"n8","children":[{"type":"Text","id":"n9","value":"Days"}]}]},
            {"type":"Table.Row","id":"n10","children":[
              {"type":"Table.Cell","id":"n11","children":[{"type":"Text","id":"n12","value":"Rome"}]},
              {"type":"Table.Cell","id":"n13","children":[{"type":"Text","id":"n14","value":"4"}]}]}]},
          {"type":"Chart","id":"n15","xAxis":{"dataKey":"month"},
           "series":[{"type":"bar","dataKey":"cost","label":"Cost"},{"type":"line","dataKey":"rain"}],
           "data":[{"month":"May","cost":120,"rain":3},{"month":"Jun","cost":150,"rain":null}]},
          {"type":"Label","id":"n16","value":"City","fieldName":"city"},
          {"type":"Select","id":"n17","name":"city","required":true,"allowOther":true,
           "options":[{"label":"Rome","value":"rome"},{"label":"Paris","value":"paris"}]},
          {"type":"Checkbox","id":"n18","name":"agree","label":"I agree","required":true},
          {"type":"Input","id":"n19","name":"note","defaultValue":"hello"},
          {"type":"Button","id":"n20","label":"Open map","onClickAction":{"type":"open_url","payload":{"url":"https://example.com/map"}}},
          {"type":"Button","id":"n21","label":"Pair","onClickAction":{"type":"open_phone_pairing"}}]}}
    """.trimIndent()

    private fun cardMessage() = decode(
        """{"id":"m1","role":"bot","kind":"widget","at":1,"text":"Plan trip","serverCard":true,"widget":$cardJson}""",
    )

    private fun root(): WidgetNode = assertNotNull(widgetView(cardMessage().widget)).root

    @Test
    fun decodesAWidgetMessageWithItsTree() {
        val message = cardMessage()
        assertEquals(Message.Kind.WIDGET, message.kind)
        assertEquals(true, message.serverCard)
        val view = assertNotNull(widgetView(message.widget))
        assertEquals(2, view.rev)
        assertNull(view.settled)
        assertEquals("Plan trip", widgetString(view.root, "title"))
    }

    @Test
    fun reEncodesTheKindTheWayTheComputerSpellsIt() {
        val round = decode(CompanionJson.encodeToString(Message.serializer(), cardMessage()))
        assertEquals(Message.Kind.WIDGET, round.kind)
        assertNotNull(round.widget)
    }

    @Test
    fun theTextIsThePreviewAndTheCopy() {
        assertEquals("Plan trip", previewText(cardMessage()))
    }

    @Test
    fun aMissingOrUnknownRootShowsTheText() {
        assertNull(widgetView(null))
        assertNull(widgetView(node("""{"v":1}""")))
        assertNull(widgetView(node("""{"v":1,"root":{"type":"Hologram","id":"n1"}}""")))
    }

    @Test
    fun listsEveryButtonInDocumentOrderWithConfirmAndCancelLast() {
        val actions = widgetActions(root())
        assertEquals(listOf("n20", "n21", "confirm", "cancel"), actions.map { it.ref })
        assertEquals(listOf("Open map", "Pair", "Send", "Skip"), actions.map { it.label })
        assertEquals(listOf(true, true, false, false), actions.map { it.client })
        assertEquals(listOf(null, null, WidgetTone.CONFIRM, WidgetTone.CANCEL), actions.map { it.tone })
        assertTrue(actions[1].hiddenOnPhone)
        assertFalse(actions[0].hiddenOnPhone)
    }

    @Test
    fun repeatedLabelsTakeTheirRowName() {
        val list = node(
            """{"type":"ListView","id":"n1","children":[
              {"type":"ListViewItem","id":"n2","children":[{"type":"Text","id":"n3","value":"Alpha"},
                {"type":"Button","id":"n4","label":"View","onClickAction":{"type":"view"}}]},
              {"type":"ListViewItem","id":"n5","children":[{"type":"Text","id":"n6","value":"Beta"},
                {"type":"Button","id":"n7","label":"View","onClickAction":{"type":"view"}}]}]}""",
        )
        assertEquals(listOf("View: Alpha", "View: Beta"), widgetActions(list).map { it.label })
    }

    @Test
    fun aDisabledButtonIsNotAnAction() {
        val card = node("""{"type":"Card","id":"n1","children":[{"type":"Button","id":"n2","label":"Go","disabled":true,"onClickAction":{"type":"go"}}]}""")
        assertEquals(emptyList(), widgetActions(card))
    }

    @Test
    fun aSubmitButtonTakesItsFormAction() {
        val card = node(
            """{"type":"Card","id":"n1","children":[{"type":"Form","id":"n2","onSubmitAction":{"type":"save"},"children":[
              {"type":"Input","id":"n3","name":"title","required":true},
              {"type":"Button","id":"n4","label":"Save","submit":true}]}]}""",
        )
        val entry = widgetActions(card).single()
        assertEquals("save", entry.action.type)
        assertEquals(WidgetTone.SUBMIT, entry.tone)
        assertEquals(listOf("title"), widgetFields(widgetScope(card, "n4"), card).map { it.name })
    }

    @Test
    fun fieldsTakeTheirLabelOrTheirOwnWordsOrTheirName() {
        val fields = widgetFields(root()).associate { it.name to it.label }
        assertEquals(mapOf("city" to "City", "agree" to "I agree", "note" to "note"), fields)
    }

    @Test
    fun initialValuesComeFromTheDefaults() {
        assertEquals(
            mapOf(
                "city" to WidgetValue.Text(""),
                "agree" to WidgetValue.Flag(false),
                "note" to WidgetValue.Text("hello"),
            ),
            widgetInitialValues(root()),
        )
    }

    @Test
    fun aRequiredFieldThatIsEmptyStopsTheSend() {
        val root = root()
        val plan = planWidgetPress(root, "confirm", widgetInitialValues(root), emptyMap(), settled = false, viewOnly = false)
        assertEquals("city", assertIs<WidgetPressPlan.Missing>(plan).field.name)
    }

    @Test
    fun aCheckboxThatIsOffIsEmptyWhenRequired() {
        val root = root()
        val values = widgetInitialValues(root) + ("city" to WidgetValue.Text("rome"))
        val plan = planWidgetPress(root, "confirm", values, emptyMap(), settled = false, viewOnly = false)
        assertEquals("agree", assertIs<WidgetPressPlan.Missing>(plan).field.name)
    }

    @Test
    fun aFilledScopeSendsEveryFieldValue() {
        val root = root()
        val values = widgetInitialValues(root) + mapOf("city" to WidgetValue.Text("rome"), "agree" to WidgetValue.Flag(true))
        val send = assertIs<WidgetPressPlan.Send>(planWidgetPress(root, "confirm", values, emptyMap(), settled = false, viewOnly = false))
        assertEquals(
            mapOf("city" to WidgetValue.Text("rome"), "agree" to WidgetValue.Flag(true), "note" to WidgetValue.Text("hello")),
            send.values,
        )
        assertNull(send.other)
        assertEquals("confirm", send.entry.ref)
    }

    @Test
    fun anAnswerInTheirOwnWordsFillsARequiredChoice() {
        val root = root()
        val values = widgetInitialValues(root) + ("agree" to WidgetValue.Flag(true))
        val send = assertIs<WidgetPressPlan.Send>(
            planWidgetPress(root, "confirm", values, mapOf("city" to "Lisbon"), settled = false, viewOnly = false),
        )
        assertEquals(WidgetValue.Text(""), send.values?.get("city"))
        assertEquals(mapOf("city" to "Lisbon"), send.other)
    }

    @Test
    fun cancelSkipsTheFieldChecksAndSendsNoValues() {
        val root = root()
        val send = assertIs<WidgetPressPlan.Send>(
            planWidgetPress(root, "cancel", widgetInitialValues(root), emptyMap(), settled = false, viewOnly = false),
        )
        assertNull(send.values)
        assertNull(send.other)
    }

    @Test
    fun aLinkOpensOnThePhoneEvenWhenSettledOrViewOnly() {
        val root = root()
        val plan = planWidgetPress(root, "n20", emptyMap(), emptyMap(), settled = true, viewOnly = true)
        assertEquals("https://example.com/map", widgetActionUrl(assertIs<WidgetPressPlan.OpenOnPhone>(plan).entry))
    }

    @Test
    fun phonePairingDoesNothingHere() {
        val root = root()
        assertEquals(WidgetPressPlan.Ignore, planWidgetPress(root, "n21", emptyMap(), emptyMap(), settled = false, viewOnly = false))
    }

    @Test
    fun aSettledOrViewOnlyCardTakesNoPress() {
        val root = root()
        val values = widgetInitialValues(root)
        assertEquals(WidgetPressPlan.Ignore, planWidgetPress(root, "confirm", values, emptyMap(), settled = true, viewOnly = false))
        assertEquals(WidgetPressPlan.Ignore, planWidgetPress(root, "confirm", values, emptyMap(), settled = false, viewOnly = true))
        assertEquals(WidgetPressPlan.Ignore, planWidgetPress(root, "n99", values, emptyMap(), settled = false, viewOnly = false))
    }

    @Test
    fun aRoomIsViewOnlyAndABotChatIsNot() {
        val bot = Bot(
            id = "b1", threadId = "t1", name = "Echo", title = "", description = "", notifications = true,
            color = "blue", unread = false, modelSelection = ModelSelection("codex", "gpt-5.5"), createdAt = 1.0,
        )
        val room = Room(
            id = "g1", threadId = "gt1", name = "War Room", memberIds = listOf("b1"),
            defaultResponder = GroupResponder("mentions"), bulletin = "", unread = false, createdAt = 1.0,
        )
        assertFalse(widgetViewOnly(Chat.BotChat(bot)))
        assertTrue(widgetViewOnly(Chat.RoomChat(room)))
    }

    @Test
    fun theSamePressKeepsItsKeyAndAChangedAnswerDoesNot() {
        val root = root()
        val values = widgetInitialValues(root) + mapOf("city" to WidgetValue.Text("rome"), "agree" to WidgetValue.Flag(true))
        fun key(city: String) = assertIs<WidgetPressPlan.Send>(
            planWidgetPress(root, "confirm", values + ("city" to WidgetValue.Text(city)), emptyMap(), settled = false, viewOnly = false),
        ).key
        assertEquals(key("rome"), key("rome"))
        assertNotEquals(key("rome"), key("paris"))
    }

    @Test
    fun aRetryReusesTheSendIdUntilItIsCleared() {
        var count = 0
        val ids = WidgetSendIds { "send-id-${++count}" }
        val first = ids.idFor("a")
        assertEquals(first, ids.idFor("a"))
        assertNotEquals(first, ids.idFor("b"))
        ids.clear()
        val fresh = ids.idFor("b")
        assertNotEquals("send-id-2", fresh)
        assertEquals(3, count)
    }

    @Test
    fun aPressBodyCarriesTheThreadTheSendIdAndTheAction() {
        val press = WidgetPress(
            messageId = "m1", ref = "confirm", rev = 2,
            values = mapOf("city" to WidgetValue.Text("rome"), "agree" to WidgetValue.Flag(true)),
            other = mapOf("pet" to "owl"), sendId = "widget-send-12345678",
        )
        assertEquals(
            node(
                """{"threadId":"t1","sendId":"widget-send-12345678","widgetAction":{"messageId":"m1","ref":"confirm","rev":2,
                   "values":{"city":"rome","agree":true},"other":{"pet":"owl"}}}""",
            ),
            press.body("t1"),
        )
        val bare = WidgetPress("m1", "n20", 0, sendId = "widget-send-12345678").body("t1")
        assertEquals(setOf("messageId", "ref", "rev"), bare["widgetAction"]!!.jsonObject.keys)
    }

    @Test
    fun readsASettledCard() {
        val message = decode(
            """{"id":"m1","role":"bot","kind":"widget","at":1,"text":"Plan trip","widget":{"v":1,"rev":3,
               "root":{"type":"Card","id":"n1","children":[]},
               "settled":{"ref":"confirm","label":"Send","tone":"confirm","at":1700000000000,"sendId":"s1",
               "values":{"city":"rome","agree":true},"other":{"pet":"owl"}}}}""",
        )
        val settled = assertNotNull(assertNotNull(widgetView(message.widget)).settled)
        assertEquals("Send", settled.label)
        assertEquals(WidgetTone.CONFIRM, settled.tone)
        assertEquals(WidgetValue.Flag(true), settled.values["agree"])
        assertEquals(mapOf("pet" to "owl"), settled.other)
    }

    @Test
    fun anUnknownNodeCutsTheDrawnPartAndEverythingAfterIt() {
        val card = node(
            """{"type":"Card","id":"n1","title":"T","confirm":{"label":"OK","action":{"type":"ok"}},"children":[
              {"type":"Text","id":"n2","value":"before"},
              {"type":"Col","id":"n3","children":[
                {"type":"Text","id":"n4","value":"inside"},
                {"type":"Hologram","id":"n5"},
                {"type":"Text","id":"n6","value":"after inside"}]},
              {"type":"Text","id":"n7","value":"after"}]}""",
        )
        val cut = widgetCutAtUnknown(card)
        assertTrue(cut.cut)
        assertEquals(listOf("n2", "n3"), widgetChildren(cut.root).map { widgetString(it, "id") })
        assertEquals(listOf("n4"), widgetChildren(widgetChildren(cut.root)[1]).map { widgetString(it, "id") })
        assertNotNull(cut.root["confirm"])
    }

    @Test
    fun aKnownTreeIsNotCut() {
        val cut = widgetCutAtUnknown(root())
        assertFalse(cut.cut)
        assertEquals(root(), cut.root)
    }

    @Test
    fun theFallbackTextIsCutAtTheLimit() {
        assertNull(widgetFallbackText(null))
        assertNull(widgetFallbackText("   "))
        assertEquals("hello", widgetFallbackText(" hello "))
        val long = widgetFallbackText("a".repeat(2_000))!!
        assertEquals(WidgetLimits.FALLBACK, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun aTableKeepsItsHeaderAndRows() {
        val table = widgetTable(widgetChildren(root())[2])
        assertEquals(listOf("City", "Days"), table.headers)
        assertEquals(listOf(listOf("Rome", "4")), table.rows)
    }

    @Test
    fun aChartReadsItsSeriesAndLeavesGapsForMissingNumbers() {
        val chart = assertNotNull(widgetChart(widgetChildren(root())[3]))
        assertEquals(listOf("May", "Jun"), chart.categories)
        assertEquals(listOf("Cost", "rain"), chart.series.map { it.label })
        assertEquals(listOf(120.0, 150.0), chart.series[0].values)
        assertEquals(listOf(3.0, null), chart.series[1].values)
        assertTrue(chart.drawable)
    }

    @Test
    fun aChartWithNoNumbersFallsBackToItsData() {
        val chart = assertNotNull(
            widgetChart(
                node(
                    """{"type":"Chart","id":"n1","xAxis":{"dataKey":"k"},"series":[{"type":"bar","dataKey":"v"}],
                       "data":[{"k":"a","v":"x"}]}""",
                ),
            ),
        )
        assertFalse(chart.drawable)
        assertEquals(listOf("k", "v"), chart.asTable().headers)
        assertNull(widgetChart(node("""{"type":"Chart","id":"n1","series":[],"data":[]}""")))
    }

    @Test
    fun aChartIsHeldToTheComputersLimits() {
        val rows = (1..150).joinToString(",") { """{"k":"r$it","a":$it}""" }
        val series = (1..6).joinToString(",") { """{"type":"line","dataKey":"a","label":"s$it"}""" }
        val chart = assertNotNull(
            widgetChart(node("""{"type":"Chart","id":"n1","xAxis":{"dataKey":"k"},"series":[$series],"data":[$rows]}""")),
        )
        assertEquals(WidgetLimits.CHART_ROWS, chart.categories.size)
        assertEquals(WidgetLimits.CHART_SERIES, chart.series.size)
    }

    @Test
    fun aThreadTargetNeedsOneWellFormedOwner() {
        fun target(payload: String) = widgetThreadTarget(
            widgetActions(node("""{"type":"Card","id":"n1","confirm":{"label":"Go","action":{"type":"open_thread","payload":$payload}}}""")).single(),
        )
        assertEquals(WidgetThreadTarget("b1", null, "t1"), target("""{"botId":"b1","threadId":"t1"}"""))
        assertEquals(WidgetThreadTarget(null, "g1", "t1"), target("""{"groupId":"g1","threadId":"t1"}"""))
        assertNull(target("""{"botId":"b1","groupId":"g1","threadId":"t1"}"""))
        assertNull(target("""{"botId":"../x","threadId":"t1"}"""))
        assertNull(target("""{"botId":"b1"}"""))
    }

    @Test
    fun onlyAWebAddressOpensAsALink() {
        fun url(value: String) = widgetActionUrl(
            widgetActions(node("""{"type":"Card","id":"n1","confirm":{"label":"Go","action":{"type":"open_url","payload":{"url":"$value"}}}}""")).single(),
        )
        assertEquals("https://a.example/x", url("https://a.example/x"))
        assertNull(url("javascript:alert(1)"))
        assertNull(url("file:///etc/passwd"))
    }

    @Test
    fun aNamedListOffersItsRowsAsChoices() {
        val list = node(
            """{"type":"ListView","id":"n1","name":"pick","allowOther":true,"children":[
              {"type":"ListViewItem","id":"n2","value":"a","children":[{"type":"Text","id":"n3","value":"Alpha"}]},
              {"type":"ListViewItem","id":"n4","value":"b","children":[{"type":"Text","id":"n5","value":"Beta"}]}]}""",
        )
        assertEquals(listOf(WidgetOption("Alpha", "a"), WidgetOption("Beta", "b")), widgetFieldOptions(list))
        assertTrue(widgetTakesOther(list))
        assertEquals("pick", widgetFields(list).single().name)
    }

    @Test
    fun valuesEncodeAsPlainJson() {
        assertEquals("rome", WidgetValue.Text("rome").toJson().jsonPrimitive.content)
        assertEquals("true", WidgetValue.Flag(true).toJson().jsonPrimitive.content)
    }

    @Test
    fun onlyASecureAddressIsAnImageSource() {
        fun src(value: String) = widgetImageUrl(node("""{"type":"Image","id":"n1","src":"$value"}"""))
        assertEquals("https://a.example/p.png", src("https://a.example/p.png"))
        assertEquals("HTTPS://A.example/p.png", src("HTTPS://A.example/p.png"))
        assertNull(src("http://a.example/p.png"))
        assertNull(src("https://"))
        assertNull(src("data:image/png;base64,AAAA"))
        assertNull(widgetImageUrl(node("""{"type":"Image","id":"n1"}""")))
    }

    @Test
    fun daysComeBackInTheOrderTheyAreDrawn() {
        assertEquals(listOf("mon", "wed", "sun"), widgetDays("sun,mon,wed"))
        assertEquals(emptyList(), widgetDays(""))
    }

    @Test
    fun badNumbersAreHeldInRange() {
        val n = node("""{"a":1e999,"b":-5,"c":1e9,"d":"NaN","e":2.7}""")
        assertNull(widgetNumber(n, "a"))
        assertNull(widgetNumber(n, "d"))
        assertEquals(1, widgetInt(n, "b", 1, 50))
        assertEquals(50, widgetInt(n, "c", 1, 50))
        assertEquals(0, widgetInt(n, "b", 0, 40))
        assertEquals(2, widgetInt(n, "e", 0, 40))
        assertNull(widgetInt(n, "missing", 0, 40))
        assertEquals(0f, widgetFraction(n, "a"))
        assertEquals(1f, widgetFraction(n, "c"))
        assertEquals(0f, widgetFraction(n, "b"))
    }

    @Test
    fun aVeryDeepTreeIsCutAtTheDepthLimit() {
        var json = """{"type":"Text","id":"leaf","value":"deep"}"""
        repeat(1000) { json = """{"type":"Col","children":[$json]}""" }
        val view = assertNotNull(widgetView(node("""{"v":1,"rev":1,"root":{"type":"Card","children":[$json]}}""")))
        assertTrue(view.truncated)
        var depth = 0
        var current: WidgetNode? = view.root
        while (current != null) {
            depth++
            current = widgetChildren(current).firstOrNull()
        }
        assertTrue(depth <= WidgetLimits.DEPTH + 1)
    }

    @Test
    fun aHugeRevIsHeldInRange() {
        val view = assertNotNull(widgetView(node("""{"v":1,"rev":1e999,"root":{"type":"Card"}}""")))
        assertEquals(0, view.rev)
    }

    @Test
    fun aChartWithEqualValuesNanPointsOrNoSeriesDoesNotBreak() {
        val equal = widgetChart(
            node("""{"type":"Chart","xAxis":{"dataKey":"x"},"series":[{"type":"bar","dataKey":"y"}],"data":[{"x":"a","y":5},{"x":"b","y":5}]}"""),
        )
        assertNotNull(equal)
        val bad = widgetChart(
            node("""{"type":"Chart","xAxis":{"dataKey":"x"},"series":[{"type":"line","dataKey":"y"}],"data":[{"x":1e999,"y":1e999},{"x":"b","y":"NaN"},{"x":"c","y":null}]}"""),
        )
        assertNotNull(bad)
        assertTrue(bad.series.all { s -> s.values.all { it == null || it.isFinite() } })
        assertNull(widgetChart(node("""{"type":"Chart","xAxis":{"dataKey":"x"},"series":[],"data":[{"x":"a"}]}""")))
    }

    @Test
    fun aTableKeepsOnlyTheFirstSixtyRows() {
        val rows = (1..200).joinToString(",") { """{"type":"Table.Row","children":[{"type":"Table.Cell","children":[{"type":"Text","value":"r$it"}]}]}""" }
        val table = widgetTable(node("""{"type":"Table","children":[$rows]}"""))
        assertTrue(table.rows.size <= WidgetLimits.TABLE_ROWS)
    }
}
