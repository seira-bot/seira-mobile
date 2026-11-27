package bot.seira.companion

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.w3c.dom.Document
import org.w3c.dom.Element

/** The manifest entries behind notification buttons, the Quick Settings tile and the Talk shortcut. */
class NativeSurfacesManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test
    fun `only this app can reach the notification action receiver`() {
        val receiver = component("receiver", ".notifications.NotificationActionReceiver")

        assertEquals("false", receiver.getAttributeNS(android, "exported"))
        assertEquals(0, receiver.getElementsByTagName("intent-filter").length)
    }

    @Test
    fun `the tile service is bound only by the system`() {
        val service = component("service", ".tile.TalkTileService")

        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", service.getAttributeNS(android, "permission"))
        val action = (service.getElementsByTagName("action").item(0) as Element).getAttributeNS(android, "name")
        assertEquals("android.service.quicksettings.action.QS_TILE", action)
    }

    @Test
    fun `the launcher reads the static shortcuts and the Talk shortcut sends the talk action`() {
        val activity = component("activity", ".MainActivity")
        val meta = activity.getElementsByTagName("meta-data").item(0) as Element
        assertEquals("android.app.shortcuts", meta.getAttributeNS(android, "name"))
        assertEquals("@xml/shortcuts", meta.getAttributeNS(android, "resource"))

        val shortcuts = parse(resource("src/main/res/xml/shortcuts.xml"))
        val intent = shortcuts.getElementsByTagName("intent").item(0) as Element
        assertEquals("bot.seira.companion.action.TALK", intent.getAttributeNS(android, "action"))
        assertEquals("bot.seira.companion.MainActivity", intent.getAttributeNS(android, "targetClass"))
    }

    @Test
    fun `the preview build points its Talk shortcut at its own package`() {
        val intent = parse(resource("src/preview/res/xml/shortcuts.xml")).getElementsByTagName("intent").item(0) as Element
        assertEquals("bot.seira.companion.preview", intent.getAttributeNS(android, "targetPackage"))
    }

    private fun component(tag: String, name: String): Element {
        val nodes = parse(resource("src/main/AndroidManifest.xml")).documentElement.getElementsByTagName(tag)
        val found = (0 until nodes.length).map { nodes.item(it) as Element }
            .firstOrNull { it.getAttributeNS(android, "name") == name }
        return assertNotNull(found, "$tag $name is not declared")
    }

    private fun parse(file: File): Document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private fun resource(path: String): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            for (prefix in listOf("", "app/")) {
                val file = File(directory, prefix + path)
                if (file.isFile) return file
            }
            directory = directory.parentFile
        }
        error("could not find $path from ${File(".").absolutePath}")
    }
}
