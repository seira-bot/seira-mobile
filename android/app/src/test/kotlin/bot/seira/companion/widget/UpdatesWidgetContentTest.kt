package bot.seira.companion.widget

import android.app.Application
import android.graphics.Bitmap
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import bot.seira.companion.widget.WidgetFixture.NOW
import bot.seira.companion.widget.WidgetFixture.row
import bot.seira.companion.widget.WidgetFixture.working
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
    fun `the small widget shows the top request with one review button that opens its chat`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.SMALL)
        val ask = row()
        provideComposable { UpdatesWidgetContent(view(listOf(working(), ask)), face) }

        onNode(hasText("Scout")).assertExists()
        onNode(hasText("git push")).assertExists()
        onNode(hasText("Review")).assertExists().assertHasStartActivityClickAction(openIntent(context, ask))
        onNode(hasText("Approve")).assertDoesNotExist()
        onNode(hasText("Allow")).assertDoesNotExist()
    }

    @Test
    fun `the small widget shows no review button for a row that needs nothing`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(UpdatesWidget.SMALL)
        provideComposable { UpdatesWidgetContent(view(listOf(working())), face) }

        onNode(hasText("Review")).assertDoesNotExist()
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
