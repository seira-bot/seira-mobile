package com.openmausbot.companion.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fixtures are hand-built: the server does not fill `suggestedReplies` yet (seira#527). */
class SuggestedRepliesTest {
    private fun fixture(name: String): JsonObject = CompanionJson.parseToJsonElement(fixtureText(name)).jsonObject

    private fun line(name: String): Message = CompanionJson.decodeFromJsonElement(fixture("suggested-replies-line").getValue(name))

    private fun user(id: String = "u") = Message(id = id, role = Message.Role.USER, kind = Message.Kind.TEXT, at = 2.0, text = "hi")

    private fun reply(title: String, prompt: String) = SuggestedReply(title, prompt)

    private fun entry(title: String, prompt: String): JsonElement = buildJsonObject { put("title", title); put("prompt", prompt) }

    @Test
    fun `the cleaner gives the same list as the reference for every case`() {
        val cases = fixture("suggested-replies-cases").getValue("cases").jsonArray
        assertTrue(cases.size > 100, "the corpus is read")
        for (case in cases) {
            val name = case.jsonObject.getValue("name").jsonPrimitive.content
            val expected = case.jsonObject.getValue("expected").jsonArray.map {
                SuggestedReply(it.jsonObject.getValue("title").jsonPrimitive.content, it.jsonObject.getValue("prompt").jsonPrimitive.content)
            }
            assertEquals(expected, SuggestedReplies.clean(case.jsonObject.getValue("input")), name)
        }
    }

    @Test
    fun `a line with chips decodes and reads as its chips`() {
        val chips = SuggestedReplies.forTranscript(listOf(line("withChips")))
        assertEquals(listOf("Next step", "Explain", "Is it safe?"), chips.map { it.title })
        assertEquals("Please do the next step", chips.first().prompt)
    }

    @Test
    fun `a line without the field, or with a field that is not a list, gives no chips and still decodes`() {
        for (name in listOf("absent", "notAList", "notAListText")) {
            val message = line(name)
            assertEquals("Done.", message.text, name)
            assertEquals(emptyList(), SuggestedReplies.forTranscript(listOf(message)), name)
        }
        assertNull(line("absent").suggestedReplies)
    }

    @Test
    fun `a null field gives no chips`() {
        val message = CompanionJson.decodeFromString<Message>("""{"id":"n","at":1,"role":"bot","kind":"text","text":"x","suggestedReplies":null}""")
        assertEquals(emptyList(), SuggestedReplies.forTranscript(listOf(message)))
    }

    @Test
    fun `bad entries drop out of a mixed list and the good one stays`() {
        assertEquals(listOf(reply("Fine", "Show me the diff")), SuggestedReplies.forTranscript(listOf(line("mixed"))))
    }

    @Test
    fun `chips show only after the last bot line, never after a line of the person, and never from another conversation`() {
        val chips = line("withChips")
        assertEquals(3, SuggestedReplies.forTranscript(listOf(user(), chips)).size)
        assertEquals(emptyList(), SuggestedReplies.forTranscript(listOf(chips, user())))
        assertEquals(emptyList(), SuggestedReplies.forTranscript(emptyList()))
        assertEquals(emptyList(), SuggestedReplies.forTranscript(listOf(chips, line("absent"))))
    }

    @Test
    fun `a tap sends only when the title is the whole prompt, otherwise it fills the composer`() {
        assertEquals(ChipTap.SEND, SuggestedReplies.tap(reply("Is it safe?", "Is it safe?")))
        assertEquals(ChipTap.FILL_COMPOSER, SuggestedReplies.tap(reply("Explain", "Explain what you changed")))
        assertEquals(ChipTap.FILL_COMPOSER, SuggestedReplies.tap(reply("Safe", "safe")))
        val long = "x".repeat(SuggestedReplies.TITLE_MAX + 1)
        assertEquals(ChipTap.FILL_COMPOSER, SuggestedReplies.tap(reply(long, long)))
    }

    @Test
    fun `no chip taps into anything but the composer text or a plain send`() {
        val cleaned = SuggestedReplies.clean(
            buildJsonArray {
                add(entry("Open", "Open https://example.com/pay"))
                add(entry("Command", "/approve all"))
                add(entry("Shell", "rm -rf /"))
                add(entry("Payload", """{"action":"approve"}"""))
                add(entry("Mail", "Write to mailto:a@b.c"))
            },
        )
        assertEquals(emptyList(), cleaned)
    }

    @Test
    fun `the server chips come first and the saved chips stay after them`() {
        val saved = listOf(QuickReply(id = "mine", title = "Mine", prompt = "My own ask", icon = "send"))
        val row = SuggestedReplies.composerChips(listOf(reply("Explain", "Explain what you changed")), saved)
        assertEquals(listOf("Explain", "Mine"), row.map { it.title })
        assertEquals(listOf(ChipTap.FILL_COMPOSER, ChipTap.SEND), row.map { it.tap })
        assertEquals("send", row.last().icon)
    }

    @Test
    fun `without server chips the row is the saved list as before`() {
        val row = SuggestedReplies.composerChips(emptyList(), QuickReply.DEFAULTS)
        assertEquals(QuickReply.DEFAULTS.map { it.title }, row.map { it.title })
        assertTrue(row.all { it.tap == ChipTap.SEND })
    }

    @Test
    fun `a saved chip with the prompt of a server chip is left out`() {
        val saved = listOf(QuickReply(id = "next", title = "What's next?", prompt = "What should we do next?", icon = "next"))
        val row = SuggestedReplies.composerChips(listOf(reply("Next?", "what should we do next?")), saved)
        assertEquals(listOf("Next?"), row.map { it.title })
    }

    @Test
    fun `a field that is not an object list reads as no chips`() {
        for (value in listOf<JsonElement>(JsonNull, JsonPrimitive("x"), JsonPrimitive(7), JsonObject(emptyMap()), JsonArray(emptyList()))) {
            assertEquals(emptyList(), SuggestedReplies.clean(value), value.toString())
        }
        assertEquals(emptyList(), SuggestedReplies.clean(null))
    }
}
