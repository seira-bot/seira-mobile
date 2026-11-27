package com.openmausbot.companion.ui.search

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.GroupResponder
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchRulesTest {
    private val bots = listOf(
        bot("nova", title = "Researcher", description = "Reads the news"),
        bot("atlas", title = "Planner", description = ""),
        bot("secret").copy(hidden = true),
    )
    private val rooms = listOf(room("Weekly standup"), room("Launch"))

    @Test
    fun `bots match on name, title and description without case`() {
        assertEquals(listOf("nova"), SearchRules.bots(bots, "NOV").map(Bot::id))
        assertEquals(listOf("atlas"), SearchRules.bots(bots, "planner").map(Bot::id))
        assertEquals(listOf("nova"), SearchRules.bots(bots, "the news").map(Bot::id))
    }

    @Test
    fun `hidden bots and blank queries match nothing`() {
        assertTrue(SearchRules.bots(bots, "secret").isEmpty())
        assertTrue(SearchRules.bots(bots, "  ").isEmpty())
        assertTrue(SearchRules.groups(rooms, "").isEmpty())
    }

    @Test
    fun `groups match on name`() {
        assertEquals(listOf("Weekly standup"), SearchRules.groups(rooms, "stand").map(Room::name))
    }

    @Test
    fun `a chip lets through only its own kind`() {
        val results = SearchResults(bots = bots.take(1), groups = rooms.take(1))
        assertEquals(1, SearchRules.visible(results, SearchFilter.BOTS).bots.size)
        assertTrue(SearchRules.visible(results, SearchFilter.BOTS).groups.isEmpty())
        assertTrue(SearchRules.visible(results, SearchFilter.GROUPS).bots.isEmpty())
        assertTrue(SearchRules.visible(results, SearchFilter.MESSAGES).isEmpty())
        assertEquals(results, SearchRules.visible(results, SearchFilter.ALL))
    }

    @Test
    fun `an empty field shows the prompt`() {
        assertEquals(SearchEmpty.PROMPT, SearchRules.empty("", SearchResults(), searching = false, failed = false))
        assertEquals(SearchEmpty.PROMPT, SearchRules.empty("a", SearchResults(), searching = false, failed = false))
    }

    @Test
    fun `a finished search with nothing shows no results`() {
        assertEquals(SearchEmpty.NONE, SearchRules.empty("zz", SearchResults(), searching = false, failed = false))
        assertNull(SearchRules.empty("zz", SearchResults(), searching = true, failed = false))
    }

    @Test
    fun `a failed search shows the error line`() {
        assertEquals(SearchEmpty.FAILED, SearchRules.empty("zz", SearchResults(), searching = false, failed = true))
    }

    @Test
    fun `results hide the empty line`() {
        assertNull(SearchRules.empty("no", SearchResults(bots = bots.take(1)), searching = false, failed = false))
    }

    @Test
    fun `the search counts as failed only when the shared error changed`() {
        assertTrue(SearchRules.failed(null, "boom", emptyList()))
        assertTrue(SearchRules.failed("old", "new", emptyList()))
        assertFalse(SearchRules.failed("same", "same", emptyList()))
        assertFalse(SearchRules.failed(null, null, emptyList()))
    }

    @Test
    fun `the message search starts at two characters`() {
        assertFalse(SearchRules.needsRemote("a"))
        assertFalse(SearchRules.needsRemote(" a "))
        assertTrue(SearchRules.needsRemote("ab"))
    }

    private fun bot(id: String, title: String = "", description: String = "") = Bot(
        id = id,
        threadId = "thread-$id",
        name = id,
        title = title,
        description = description,
        notifications = true,
        color = "blue",
        unread = false,
        modelSelection = ModelSelection("instance", "model"),
        createdAt = 1.0,
    )

    private fun room(name: String) = Room(
        id = name,
        threadId = "thread-$name",
        name = name,
        memberIds = emptyList(),
        defaultResponder = GroupResponder("auto"),
        bulletin = "",
        unread = false,
        createdAt = 0.0,
    )
}
