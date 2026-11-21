package com.openmausbot.companion.widget

import android.app.Application
import android.graphics.Bitmap
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.openmausbot.companion.widget.WidgetFixture.NOW
import com.openmausbot.companion.widget.WidgetFixture.row
import com.openmausbot.companion.widget.WidgetFixture.working
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdatesWidgetContentTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application
    private val minute = 60_000L
    private val face = { _: WidgetSnapshot.Row -> Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }

    private fun view(rows: List<WidgetSnapshot.Row>, age: Long = 0) =
        WidgetPresentation.view(WidgetFixture.snapshot(rows), NOW + age) as WidgetView.Active

    @Test
    fun `an unpaired phone shows the pair line and no rows`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.TALL)
        provideComposable { UpdatesWidgetContent(WidgetView.Unpaired, face) }

        onNode(hasText("Open Seira to pair")).assertExists()
        onNode(hasText("Scout")).assertDoesNotExist()
        onNode(hasText("Updates")).assertDoesNotExist()
    }

    @Test
    fun `stale content says how old it is`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.WIDE)
        val stale = view(listOf(row()), age = 16 * minute)
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(NOW))
        provideComposable { UpdatesWidgetContent(stale, face) }

        onNode(hasText("As of $time")).assertExists()
    }

    @Test
    fun `fresh content shows no age line`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.WIDE)
        provideComposable { UpdatesWidgetContent(view(listOf(row())), face) }

        onNode(hasText("1 active")).assertExists()
        onAllNodes(hasText("As of")).assertCountEquals(0)
    }

    @Test
    fun `the small widget offers one approve button for a plain permission ask`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.SMALL)
        provideComposable { UpdatesWidgetContent(view(listOf(row())), face) }

        val parameters = actionParametersOf(
            WidgetAnswerAction.ThreadKey to "thread-1",
            WidgetAnswerAction.BotKey to "bot-1",
            WidgetAnswerAction.RequestKey to "request-1",
            WidgetAnswerAction.ChoiceKey to "Allow",
        )
        onNode(hasRunCallbackClickAction<WidgetAnswerAction>(parameters)).assertExists()
        onNode(hasText("Approve")).assertExists()
    }

    @Test
    fun `a request that may be cut shows no approve button and a tap opens the chat`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.SMALL)
        val cut = row(fullyShown = false)
        provideComposable { UpdatesWidgetContent(view(listOf(cut)), face) }

        onNode(hasText("Approve")).assertDoesNotExist()
        onNode(hasRunCallbackClickAction<WidgetAnswerAction>()).assertDoesNotExist()
        onNode(hasStartActivityClickAction(openIntent(context, cut))).assertExists()
    }

    @Test
    fun `a large font scale hides the approve button`() = runGlanceAppWidgetUnitTest {
        val scaled = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply { fontScale = 1.3f },
        )
        setContext(scaled)
        setAppWidgetSize(UpdatesWidget.SMALL)
        val long = row(line = "x".repeat(30))
        provideComposable { UpdatesWidgetContent(view(listOf(long)), face) }

        onNode(hasText("Approve")).assertDoesNotExist()
        onNode(hasStartActivityClickAction(openIntent(context, long))).assertExists()
    }

    @Test
    fun `the approve button disappears after ten minutes`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.SMALL)
        provideComposable { UpdatesWidgetContent(view(listOf(row()), age = 11 * minute), face) }

        onNode(hasText("Approve")).assertDoesNotExist()
        onNode(hasText("Open Seira to answer")).assertExists()
    }

    @Test
    fun `an ask that is not a plain permission card only opens the chat`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.SMALL)
        val skill = row(plainAllow = null, options = emptyList(), isPermission = false)
        provideComposable { UpdatesWidgetContent(view(listOf(skill)), face) }

        onNode(hasText("Approve")).assertDoesNotExist()
        onNode(hasRunCallbackClickAction<WidgetAnswerAction>()).assertDoesNotExist()
        onNode(hasStartActivityClickAction(openIntent(context, skill))).assertExists()
    }

    @Test
    fun `a row tap opens that chat`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.TALL)
        val rows = listOf(row(), working())
        provideComposable { UpdatesWidgetContent(view(rows), face) }

        for (row in rows) {
            onNode(hasStartActivityClickAction(openIntent(context, row))).assertExists()
        }
        onNode(hasText("NEEDS YOU")).assertExists()
        onNode(hasText("WORKING")).assertExists()
    }

    @Test
    fun `the wide widget lists the updates flat`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.WIDE)
        provideComposable { UpdatesWidgetContent(view(listOf(row(), working())), face) }

        onNode(hasText("2 active")).assertExists()
        onNode(hasText("NEEDS YOU")).assertDoesNotExist()
    }

    @Test
    fun `a quiet widget says all quiet`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.SMALL)
        provideComposable { UpdatesWidgetContent(WidgetView.Quiet(null), face) }

        onNode(hasText("All quiet")).assertExists()
    }
}
