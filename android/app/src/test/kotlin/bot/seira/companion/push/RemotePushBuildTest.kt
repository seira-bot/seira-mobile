package bot.seira.companion.push

import bot.seira.companion.BuildConfig
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Element

/** What a build with and without `google-services.json` does, and the manifest entries for push. */
class RemotePushBuildTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test
    fun `AC-10 remote push is on exactly when google-services json is present`() {
        assertEquals(File("google-services.json").isFile, BuildConfig.REMOTE_PUSH)
    }

    @Test
    fun `AC-10 the repository ignores google-services json`() {
        val ignore = File("../../.gitignore").readLines().map(String::trim)
        assertTrue("google-services.json" in ignore, ".gitignore must list google-services.json")
    }

    @Test
    fun `the messaging service is private and Firebase starts only on request`() {
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement
        val services = manifest.getElementsByTagName("service")
        val service = (0 until services.length).map { services.item(it) as Element }
            .firstOrNull { it.getAttributeNS(android, "name") == ".push.PushMessagingService" }
        assertNotNull(service)
        assertEquals("false", service.getAttributeNS(android, "exported"))
        val action = (service.getElementsByTagName("action").item(0) as Element).getAttributeNS(android, "name")
        assertEquals("com.google.firebase.MESSAGING_EVENT", action)

        val metas = manifest.getElementsByTagName("meta-data")
        val autoInit = (0 until metas.length).map { metas.item(it) as Element }
            .first { it.getAttributeNS(android, "name") == "firebase_messaging_auto_init_enabled" }
        assertEquals("false", autoInit.getAttributeNS(android, "value"))
    }
}
