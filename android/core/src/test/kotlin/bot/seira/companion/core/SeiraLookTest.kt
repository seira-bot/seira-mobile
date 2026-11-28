package bot.seira.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeiraLookTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CompanionClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connection = requireNotNull(Connection.parse(server.url("/").toString()))
        client = CompanionClient(connection, "paired-token")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun look(
        hair: String = "seira",
        color: String = "red",
        faceInk: String? = null,
        expression: String? = null,
        accessories: List<String> = emptyList(),
    ) = SeiraLook.of(hair, color, faceInk, expression, accessories)

    @Test
    fun hairCatalogHasSeventeenStyles() {
        assertEquals(17, SeiraLookCatalog.hairIds.size)
        assertEquals("seira", SeiraLookCatalog.hairIds.first())
        assertEquals("none", SeiraLookCatalog.hairIds.last())
    }

    @Test
    fun unknownAndRetiredHairReadAsSeira() {
        assertEquals("seira", SeiraLookCatalog.hair(null))
        assertEquals("seira", SeiraLookCatalog.hair("nonsense"))
        assertEquals("seira", SeiraLookCatalog.hair("cursor"))
        assertEquals("seira", SeiraLookCatalog.hair("pumpkin"))
        assertEquals("twin-tails", SeiraLookCatalog.hair("twin-tails"))
        SeiraLookCatalog.hairIds.forEach { assertEquals(it, SeiraLookCatalog.hair(it)) }
    }

    @Test
    fun accessoriesDropUnknownKeepFirstPerSlotAndCapAtThree() {
        assertEquals(listOf("glasses"), SeiraLookCatalog.accessories(listOf("monocle", "glasses")))
        assertEquals(emptyList(), SeiraLookCatalog.accessories(null))
        assertEquals(
            listOf("sunglasses", "bowtie"),
            SeiraLookCatalog.accessories(listOf("sunglasses", "glasses", "bowtie")),
        )
        assertEquals(
            listOf("glasses", "earring", "ribbon"),
            SeiraLookCatalog.accessories(listOf("glasses", "earring", "ribbon", "cap", "scarf")),
        )
    }

    @Test
    fun everyAccessoryHasASlot() {
        assertEquals(12, SeiraLookCatalog.accessoryIds.size)
        SeiraLookCatalog.accessoryIds.forEach { assertNotNull(SeiraLookCatalog.slot(it), it) }
        assertEquals(SeiraLookSlot.EARS, SeiraLookCatalog.slot("headset-mic"))
        assertNull(SeiraLookCatalog.slot("monocle"))
    }

    @Test
    fun toggleTurnsOnAndOff() {
        assertEquals(listOf("glasses"), SeiraLookCatalog.toggle("glasses", emptyList()))
        assertEquals(listOf("bowtie"), SeiraLookCatalog.toggle("glasses", listOf("glasses", "bowtie")))
    }

    @Test
    fun toggleReplacesTheExtraInATakenSlot() {
        assertEquals(
            listOf("earring", "scarf", "sunglasses"),
            SeiraLookCatalog.toggle("sunglasses", listOf("earring", "glasses", "scarf")),
        )
        assertEquals(
            listOf("glasses", "earring", "tiara"),
            SeiraLookCatalog.toggle("tiara", listOf("glasses", "earring", "ribbon")),
        )
    }

    @Test
    fun toggleDropsTheOldestExtraForAFourthSlot() {
        assertEquals(
            listOf("earring", "ribbon", "cap"),
            SeiraLookCatalog.toggle("cap", listOf("glasses", "earring", "ribbon")),
        )
    }

    @Test
    fun toggleLeavesListAloneForUnknownId() {
        assertEquals(listOf("glasses"), SeiraLookCatalog.toggle("monocle", listOf("glasses")))
    }

    @Test
    fun colorCatalog() {
        assertEquals(10, SeiraLookCatalog.starterColors.size)
        assertFalse("silver" in SeiraLookCatalog.starterColors)
        assertEquals(12, SeiraLookCatalog.colorHex.size)
        SeiraLookCatalog.starterColors.forEach { assertNotNull(SeiraLookCatalog.colorHex[it], it) }
        assertEquals("silver", SeiraLookCatalog.color("silver"))
        assertEquals("green", SeiraLookCatalog.color("magenta"))
        assertEquals("green", SeiraLookCatalog.color(null))
    }

    @Test
    fun lookNormalizesEveryField() {
        val value = SeiraLook.of("ghost", "magenta", "grey", "dancing", listOf("monocle", "glasses"))
        assertEquals("seira", value.hair)
        assertEquals("green", value.color)
        assertNull(value.faceInk)
        assertNull(value.expression)
        assertEquals(listOf("glasses"), value.accessories)
        assertEquals(
            SeiraLook("bun", "teal", "black", "proud", listOf("tiara")),
            SeiraLook.of("bun", "teal", "black", "proud", listOf("tiara")),
        )
    }

    @Test
    fun seiraLookIsSeiraHairWithNoExtras() {
        assertEquals("seira", SeiraLook.SEIRA.hair)
        assertEquals("red", SeiraLook.SEIRA.color)
        assertNull(SeiraLook.SEIRA.faceInk)
        assertNull(SeiraLook.SEIRA.expression)
        assertTrue(SeiraLook.SEIRA.accessories.isEmpty())
    }

    private val botJson = """
        {"id":"look-bot","threadId":"look-thread","name":"Dina Pratama","title":"Analyst",
         "description":"Reads reports.","notifications":true,"color":"blue","unread":false,
         "modelSelection":{"instanceId":"local","model":"default"},"createdAt":1786742441013,
         "mascotBody":"bob","mascotFaceColor":"black","mascotExpression":"happy",
         "mascotAccessories":["glasses","ribbon"]}
    """.trimIndent()

    @Test
    fun lookReadsABotWithTheNewFields() {
        val bot = CompanionJson.decodeFromString<Bot>(botJson)
        assertEquals("black", bot.mascotFaceColor)
        assertEquals(listOf("glasses", "ribbon"), bot.mascotAccessories)
        assertEquals(
            SeiraLook("bob", "blue", "black", "happy", listOf("glasses", "ribbon")),
            SeiraLook.from(bot),
        )
    }

    @Test
    fun lookReadsABotWithoutTheNewFields() {
        val json = """
            {"id":"old-bot","threadId":"old-thread","name":"Dina Pratama","title":"","description":"",
             "notifications":true,"color":"pink","unread":false,
             "modelSelection":{"instanceId":"local","model":"default"},"createdAt":1786742441013,
             "mascotBody":"cursor"}
        """.trimIndent()
        val bot = CompanionJson.decodeFromString<Bot>(json)
        assertNull(bot.mascotFaceColor)
        assertNull(bot.mascotAccessories)
        val value = SeiraLook.from(bot)
        assertEquals("seira", value.hair)
        assertEquals("pink", value.color)
        assertNull(value.faceInk)
        assertTrue(value.accessories.isEmpty())
    }

    private val castJson = """
        [
          {"id":"mira","name":"Mira","mascotBody":"long-straight","mascotAccessories":["hairpin"],"color":"orange"},
          {"id":"lumi","name":"Lumi","mascotBody":"bob","mascotAccessories":["headset-mic"],"color":"cyan"},
          {"id":"aldo","name":"Aldo","mascotBody":"slick-back","color":"chrome"}
        ]
    """.trimIndent()

    @Test
    fun castDecodesAndDefaultsMissingAccessories() {
        val cast = SeiraCast.decode(castJson)
        assertEquals(listOf("mira", "lumi", "aldo"), cast.map { it.id })
        assertEquals(emptyList(), cast[2].mascotAccessories)
        assertEquals(SeiraLook.of("bob", "cyan", accessories = listOf("headset-mic")), cast[1].look)
        assertNull(cast[1].look.faceInk)
        assertNull(cast[1].look.expression)
    }

    @Test
    fun surpriseNeverReturnsTheCurrentLook() {
        val cast = SeiraCast.decode(castJson)
        val current = cast[0].look
        val random = Random(42)
        repeat(50) { assertNotEquals(current, SeiraCast.surprise(cast, current, random)) }
    }

    @Test
    fun surpriseFallsBackWhenNoOtherLookExists() {
        val cast = SeiraCast.decode(castJson)
        val random = Random(7)
        assertEquals(cast[0].look, SeiraCast.surprise(listOf(cast[0]), cast[0].look, random))
        assertEquals(SeiraLook.SEIRA, SeiraCast.surprise(emptyList(), SeiraLook.SEIRA, random))
    }

    @Test
    fun patchWithOneChangedFieldHasOneKey() {
        val body = BotLookPatch.between(look("bob", "blue"), look("bob", "teal")).toJson()
        assertEquals(setOf("color"), body.keys)
        assertEquals("teal", body.getValue("color").jsonPrimitive.content)
    }

    @Test
    fun patchSendsHairAsMascotBody() {
        val body = BotLookPatch.between(SeiraLook.SEIRA, look("buzz")).toJson()
        assertEquals(setOf("mascotBody"), body.keys)
        assertEquals("buzz", body.getValue("mascotBody").jsonPrimitive.content)
    }

    @Test
    fun clearedFaceInkAndExpressionGoOutAsNull() {
        val cleared = BotLookPatch.between(
            look(faceInk = "black", expression = "happy"),
            look(),
        ).toJson()
        assertEquals(setOf("mascotFaceColor", "mascotExpression"), cleared.keys)
        assertEquals(JsonNull, cleared.getValue("mascotFaceColor"))
        assertEquals(JsonNull, cleared.getValue("mascotExpression"))
        val set = BotLookPatch.between(look(), look(expression = "happy")).toJson()
        assertEquals("happy", set.getValue("mascotExpression").jsonPrimitive.content)
    }

    @Test
    fun accessoriesGoOutAsTheWholeList() {
        val body = BotLookPatch.between(
            look(accessories = listOf("glasses", "bowtie")),
            look(accessories = listOf("glasses", "cap")),
        ).toJson()
        assertEquals(setOf("mascotAccessories"), body.keys)
        assertEquals(listOf("glasses", "cap"), body.getValue("mascotAccessories").jsonArray.map { it.jsonPrimitive.content })
        val removed = BotLookPatch.between(look(accessories = listOf("glasses")), look()).toJson()
        assertEquals(0, removed.getValue("mascotAccessories").jsonArray.size)
    }

    @Test
    fun patchWithNoChangeIsEmpty() {
        val value = look("bun", "pink", "white", "idle", listOf("cap"))
        val patch = BotLookPatch.between(value, value)
        assertTrue(patch.isEmpty)
        assertEquals(JsonObject(emptyMap()), patch.toJson())
        assertFalse(BotLookPatch.between(value, SeiraLook.SEIRA).isEmpty)
    }

    @Test
    fun updateLookPatchesTheBotRouteWithOnlyChangedKeys() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("""{"bot":$botJson}"""),
        )
        val old = look("bob", "blue", accessories = listOf("glasses"))
        val new = look("bob", "blue", faceInk = "black", accessories = listOf("glasses"))

        val bot = client.updateLook("look-bot", BotLookPatch.between(old, new))

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/bots/look-bot", request.path)
        assertEquals("Bearer paired-token", request.getHeader("Authorization"))
        val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("mascotFaceColor"), body.keys)
        assertEquals("black", body.getValue("mascotFaceColor").jsonPrimitive.content)
        assertEquals("look-bot", bot.id)
        assertEquals("black", bot.mascotFaceColor)
        assertEquals(listOf("glasses", "ribbon"), bot.mascotAccessories)
    }
}
