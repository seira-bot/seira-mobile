package bot.seira.companion.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceEngineRuleTest {
    private val desktop = Connection(name = "Mac", host = "192.0.2.1", port = 8799)
    private val server = desktop.copy(serverEnvironmentId = "env", serverScopes = listOf("admin", "client"))

    @Test
    fun aDesktopPairingCannotChangeTheVoiceEngine() {
        assertFalse(desktop.canChangeVoiceEngine)
    }

    @Test
    fun anAdminServerPairingCanChangeTheVoiceEngine() {
        assertTrue(server.canChangeVoiceEngine)
    }

    @Test
    fun aServerPairingWithoutAdminCannotChangeTheVoiceEngine() {
        assertFalse(server.copy(serverScopes = listOf("client")).canChangeVoiceEngine)
        assertFalse(server.copy(serverScopes = null).canChangeVoiceEngine)
    }
}
