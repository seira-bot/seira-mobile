package bot.seira.companion.ui

import bot.seira.companion.core.AvatarCrop
import bot.seira.companion.core.Bot
import bot.seira.companion.core.ModelSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarSheetRulesTest {
    private val plain = Bot(
        id = "b1", threadId = "t1", name = "Dina", title = "", description = "",
        notifications = true, color = "red", unread = false,
        modelSelection = ModelSelection("i", "m"), createdAt = 0.0,
    )

    private fun withPicture(crop: AvatarCrop?) = plain.copy(avatarUrl = "/api/attachments/a.png", avatarCrop = crop)

    @Test
    fun `a bot with no picture opens on the Seira tab`() {
        assertEquals(AvatarTab.SEIRA, AvatarSheetRules.defaultTab(plain))
    }

    @Test
    fun `a bot that shows a picture opens on the Upload tab`() {
        assertEquals(AvatarTab.UPLOAD, AvatarSheetRules.defaultTab(withPicture(AvatarCrop.CIRCLE)))
        assertEquals(AvatarTab.UPLOAD, AvatarSheetRules.defaultTab(withPicture(AvatarCrop.ROUNDED)))
        assertEquals(AvatarTab.UPLOAD, AvatarSheetRules.defaultTab(withPicture(AvatarCrop.SQUARE)))
    }

    @Test
    fun `a bot with a stored picture that shows the mascot opens on the Seira tab`() {
        assertEquals(AvatarTab.SEIRA, AvatarSheetRules.defaultTab(withPicture(AvatarCrop.MASCOT)))
        assertEquals(AvatarTab.SEIRA, AvatarSheetRules.defaultTab(withPicture(null)))
    }

    @Test
    fun `a crop with no stored picture shows no picture`() {
        assertFalse(AvatarSheetRules.showsPicture(plain.copy(avatarCrop = AvatarCrop.CIRCLE)))
    }

    @Test
    fun `use this picture is offered only while a stored picture is hidden`() {
        assertFalse(AvatarSheetRules.canUsePicture(plain))
        assertTrue(AvatarSheetRules.canUsePicture(withPicture(AvatarCrop.MASCOT)))
        assertFalse(AvatarSheetRules.canUsePicture(withPicture(AvatarCrop.CIRCLE)))
    }

    @Test
    fun `a generated picture that comes back as the mascot needs a circle`() {
        assertTrue(AvatarSheetRules.needsCircle(withPicture(null)))
        assertTrue(AvatarSheetRules.needsCircle(withPicture(AvatarCrop.MASCOT)))
        assertFalse(AvatarSheetRules.needsCircle(withPicture(AvatarCrop.CIRCLE)))
        assertFalse(AvatarSheetRules.needsCircle(withPicture(AvatarCrop.SQUARE)))
    }

    @Test
    fun `a stored tab name comes back and an unknown one does not`() {
        assertEquals(AvatarTab.GENERATE, AvatarTab.restore("GENERATE"))
        assertNull(AvatarTab.restore("SHAPE"))
        assertNull(AvatarTab.restore(null))
    }
}
