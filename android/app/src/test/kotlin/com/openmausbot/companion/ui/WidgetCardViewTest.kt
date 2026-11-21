package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.ThreadRef
import com.openmausbot.companion.core.WidgetPress
import com.openmausbot.companion.core.WidgetPressResult
import com.openmausbot.companion.core.WidgetValue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.decodeFromString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class WidgetCardViewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tree = """
        {"v":1,"rev":1,"root":{"type":"Card","id":"n1","title":"Plan trip","caption":"Pick a name",
         "confirm":{"label":"Send","action":{"type":"trip.send"}},
         "cancel":{"label":"Skip","action":{"type":"trip.skip"}},
         "children":[
          {"type":"Text","id":"n2","value":"Two good options"},
          {"type":"Label","id":"n3","value":"Name","fieldName":"who"},
          {"type":"Input","id":"n4","name":"who","required":true,"placeholder":"Your name"},
          {"type":"Button","id":"n5","label":"Open map","onClickAction":{"type":"open_url","payload":{"url":"https://example.com/map"}}},
          {"type":"Button","id":"n6","label":"Open chat","onClickAction":{"type":"open_thread","payload":{"botId":"b2","threadId":"t9"}}}]}}
    """.trimIndent()

    private fun message(widget: String = tree, text: String = "Plan trip fallback") = CompanionJson.decodeFromString<Message>(
        """{"id":"m1","role":"bot","kind":"widget","at":1,"text":"$text","widget":$widget}""",
    )

    private class Taps {
        val presses = mutableListOf<WidgetPress>()
        val urls = mutableListOf<String>()
        val threads = mutableListOf<ThreadRef>()
        var result: WidgetPressResult = WidgetPressResult.Sent
    }

    private fun show(message: Message, taps: Taps, viewOnly: Boolean = false) {
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    WidgetCardView(
                        message = message,
                        viewOnly = viewOnly,
                        press = { press -> taps.presses += press; taps.result },
                        openUrl = { taps.urls += it },
                        openThread = { taps.threads += it },
                    )
                }
            }
        }
    }

    @Test
    fun drawsTheHeaderTheBodyAndTheButtons() {
        show(message(), Taps())
        compose.onNodeWithText("Plan trip").assertIsDisplayed()
        compose.onNodeWithText("Pick a name").assertIsDisplayed()
        compose.onNodeWithText("Two good options").assertIsDisplayed()
        compose.onNodeWithText("Name").assertIsDisplayed()
        compose.onNodeWithText("Send").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun anEmptyRequiredFieldShowsItsErrorAndSendsNothing() {
        val taps = Taps()
        show(message(), taps)
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Fill in Name first.").assertIsDisplayed()
        assertEquals(0, taps.presses.size)
    }

    @Test
    fun aFilledFormSendsTheValuesAndFoldsToOneLine() {
        val taps = Taps()
        show(message(), taps)
        compose.onNodeWithText("Your name").performTextInput("Ada")
        compose.onNodeWithText("Send").performClick()
        compose.waitForIdle()
        val press = taps.presses.single()
        assertEquals("confirm", press.ref)
        assertEquals(1, press.rev)
        assertEquals(mapOf("who" to WidgetValue.Text("Ada")), press.values)
        compose.onNodeWithText("Details").assertIsDisplayed()
        compose.onNodeWithText("Plan trip").assertDoesNotExist()
    }

    @Test
    fun aFailedPressShowsTheErrorAndTheRetryKeepsTheSendId() {
        val taps = Taps().apply { result = WidgetPressResult.Failed }
        show(message(), taps)
        compose.onNodeWithText("Your name").performTextInput("Ada")
        compose.onNodeWithText("Send").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Could not send. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Send").performClick()
        compose.waitForIdle()
        assertEquals(2, taps.presses.size)
        assertEquals(taps.presses[0].sendId, taps.presses[1].sendId)
    }

    @Test
    fun aRefusalShowsTheComputersWords() {
        val taps = Taps().apply { result = WidgetPressResult.Refused("This was already answered.") }
        show(message(), taps)
        compose.onNodeWithText("Your name").performTextInput("Ada")
        compose.onNodeWithText("Send").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("This was already answered.").assertIsDisplayed()
    }

    @Test
    fun aViewOnlyCardTakesNoPressButStillOpensLinksAndThreads() {
        val taps = Taps()
        show(message(), taps, viewOnly = true)
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.onNodeWithText("Open map").assertIsEnabled().performClick()
        compose.onNodeWithText("Open chat").assertIsEnabled().performClick()
        assertEquals(emptyList(), taps.presses)
        assertEquals(listOf("https://example.com/map"), taps.urls)
        assertEquals(listOf(ThreadRef("b2", "t9", "")), taps.threads)
    }

    @Test
    fun aClientActionNeverGoesToTheComputer() {
        val taps = Taps()
        show(message(), taps)
        compose.onNodeWithText("Open map").performClick()
        assertEquals(0, taps.presses.size)
        assertEquals(1, taps.urls.size)
    }

    @Test
    fun aSettledCardOpensItsDetails() {
        val settled = tree.replace(
            """"rev":1,""",
            """"rev":1,"settled":{"ref":"confirm","label":"Send","tone":"confirm","at":1700000000000,"values":{"who":"Ada"}},""",
        )
        show(message(settled), Taps())
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithText("Hide details").assertIsDisplayed()
        compose.onNodeWithText("Ada").assertIsDisplayed()
    }

    @Test
    fun anUnknownRootShowsOnlyTheText() {
        show(message("""{"v":1,"root":{"type":"Hologram","id":"n1"}}""", text = "Only the words"), Taps())
        compose.onNodeWithText("Only the words").assertIsDisplayed()
    }

    @Test
    fun anUnknownNodeKeepsWhatCameBeforeAndShowsTheTextBelow() {
        val widget = """{"v":1,"root":{"type":"Card","id":"n1","children":[
            {"type":"Text","id":"n2","value":"Drawn part"},{"type":"Hologram","id":"n3"},{"type":"Text","id":"n4","value":"Lost part"}]}}"""
        show(message(widget, text = "Readable fallback"), Taps())
        compose.onNodeWithText("Drawn part").assertIsDisplayed()
        compose.onNodeWithText("Readable fallback").assertIsDisplayed()
        compose.onNodeWithText("Lost part").assertDoesNotExist()
    }

    @Test
    fun aMessageWithoutATreeShowsTheText() {
        val bare = CompanionJson.decodeFromString<Message>("""{"id":"m1","role":"bot","kind":"widget","at":1,"text":"Just words"}""")
        assertNull(bare.widget)
        show(bare, Taps())
        compose.onNodeWithText("Just words").assertIsDisplayed()
    }

    @Test
    fun anImageIsOnlyOfferedForASecureAddress() {
        val widget = """{"v":1,"root":{"type":"Card","id":"n1","children":[
            {"type":"Image","id":"n2","src":"https://example.com/a.png","alt":"A"},
            {"type":"Image","id":"n3","src":"http://example.com/b.png","alt":"B"}]}}"""
        show(message(widget), Taps())
        compose.onNodeWithText("Show image").assertIsDisplayed()
    }

    @Test
    fun aTableAndAChartDrawFromTheirData() {
        val widget = """{"v":1,"root":{"type":"Card","id":"n1","children":[
            {"type":"Table","id":"n2","children":[
              {"type":"Table.Row","id":"n3","header":true,"children":[{"type":"Table.Cell","id":"n4","children":[{"type":"Text","id":"n5","value":"City"}]}]},
              {"type":"Table.Row","id":"n6","children":[{"type":"Table.Cell","id":"n7","children":[{"type":"Text","id":"n8","value":"Rome"}]}]}]},
            {"type":"Chart","id":"n9","xAxis":{"dataKey":"m"},"series":[{"type":"line","dataKey":"v","label":"Visits"},{"type":"bar","dataKey":"w","label":"Sales"}],
             "data":[{"m":"May","v":1,"w":2},{"m":"Jun","v":3,"w":1}]}]}}"""
        show(message(widget), Taps())
        compose.onNodeWithText("Rome").assertIsDisplayed()
        compose.onNodeWithText("Visits").assertIsDisplayed()
        compose.onNodeWithText("Sales").assertIsDisplayed()
        compose.onNodeWithText("May").assertIsDisplayed()
    }

    private fun cardWith(children: String) =
        """{"v":1,"rev":1,"root":{"type":"Card","id":"n1","title":"Safe card","children":[$children]}}"""

    @Test
    fun oddMaxLinesAndLimitDoNotCrash() {
        val texts = listOf("0", "-5", "1000000000", "\"NaN\"", "1e999").mapIndexed { i, v ->
            """{"type":"Text","id":"t$i","value":"line $i","maxLines":$v}"""
        }
        val list = """{"type":"ListView","id":"l1","limit":-1,"children":[{"type":"ListViewItem","id":"i1","children":[{"type":"Text","id":"i2","value":"item"}]}]}"""
        show(message(cardWith((texts + list).joinToString(","))), Taps())
        compose.onNodeWithText("Safe card").assertIsDisplayed()
        compose.onNodeWithText("line 0").assertIsDisplayed()
    }

    @Test
    fun oddProgressValuesDoNotCrash() {
        val bars = listOf("\"NaN\"", "1e999", "5", "-3").mapIndexed { i, v ->
            """{"type":"Progress","id":"p$i","value":$v,"max":0}"""
        }
        show(message(cardWith(bars.joinToString(","))), Taps())
        compose.onNodeWithText("Safe card").assertIsDisplayed()
    }

    @Test
    fun oddChartsDoNotCrash() {
        val equal = """{"type":"Chart","id":"c1","xAxis":{"dataKey":"x"},"series":[{"type":"bar","dataKey":"y"},{"type":"area","dataKey":"y"}],"data":[{"x":"a","y":5},{"x":"b","y":5}]}"""
        val empty = """{"type":"Chart","id":"c2","xAxis":{"dataKey":"x"},"series":[],"data":[]}"""
        val nan = """{"type":"Chart","id":"c3","xAxis":{"dataKey":"x"},"series":[{"type":"line","dataKey":"y"}],"data":[{"x":"a","y":1e999},{"x":"b","y":"NaN"},{"x":"c","y":null}]}"""
        show(message(cardWith("$equal,$empty,$nan")), Taps())
        compose.onNodeWithText("Safe card").assertIsDisplayed()
    }

    @Test
    fun aThousandLevelsDeepTreeDoesNotCrash() {
        var json = """{"type":"Text","id":"leaf","value":"deep"}"""
        repeat(1000) { json = """{"type":"Col","children":[$json]}""" }
        show(message(cardWith(json)), Taps())
        compose.onNodeWithText("Safe card").assertIsDisplayed()
    }
}
