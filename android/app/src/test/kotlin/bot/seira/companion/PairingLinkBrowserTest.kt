package bot.seira.companion

import android.content.Context
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairingLinkBrowserTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    // Robolectric resolves the plain view intent and ignores the browser selector,
    // so a seira.run URL would match this app's own link filter and not fail.
    @Test
    fun `a link says so when no browser can open it`() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        openInBrowser(context, "https://example.com/some/path")
        assertEquals(context.getString(R.string.mobile_link_no_browser), ShadowToast.getTextOfLatestToast())
        assertEquals("No browser on this phone can open this link.", ShadowToast.getTextOfLatestToast())
    }
}
