package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openmausbot.companion.R
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairDesignTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `no flash unit means no torch button`() {
        assertNull(PairDesign.torch(hasFlashUnit = false, on = false))
        assertNull(PairDesign.torch(hasFlashUnit = false, on = true))
    }

    @Test
    fun `the torch label names the action it will take`() {
        assertEquals(TorchControl(R.string.pair_flash_on, false), PairDesign.torch(true, false))
        assertEquals(TorchControl(R.string.pair_flash_off, true), PairDesign.torch(true, true))
    }

    @Test
    fun `the phase follows the pairing and wins in order`() {
        assertEquals(PairPhase.POINT, PairDesign.phase(connected = false, pairing = false, hasComputer = false))
        assertEquals(PairPhase.FOUND, PairDesign.phase(false, false, true))
        assertEquals(PairPhase.CONNECTING, PairDesign.phase(false, true, true))
        assertEquals(PairPhase.CONNECTED, PairDesign.phase(true, true, true))
    }

    @Test
    fun `each phase has its own status line`() {
        val lines = PairPhase.entries.map { PairDesign.statusRes(it) }
        assertEquals(lines.size, lines.toSet().size)
    }

    @Test
    fun `the torch button toggles and carries its label`() {
        var toggled = 0
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                PairFrame(
                    phase = PairPhase.POINT,
                    statusText = "Point your camera at the code",
                    statusIsError = false,
                    torch = PairDesign.torch(hasFlashUnit = true, on = false),
                    onTorch = { toggled++ },
                    onBack = {},
                    backEnabled = true,
                    viewfinder = {},
                    card = {},
                )
            }
        }
        compose.onNodeWithText("Point your camera at the code").assertExists()
        compose.onNode(hasContentDescription("Turn on flashlight")).performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun `without a flash unit the frame draws no torch`() {
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                PairFrame(
                    phase = PairPhase.POINT,
                    statusText = "Point your camera at the code",
                    statusIsError = false,
                    torch = null,
                    onTorch = {},
                    onBack = {},
                    backEnabled = true,
                    viewfinder = {},
                    card = {},
                )
            }
        }
        assertTrue(
            compose.onAllNodes(hasContentDescription("Turn on flashlight")).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `the connected card offers done`() {
        var done = 0
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                PairFrame(
                    phase = PairPhase.CONNECTED,
                    statusText = "Paired with Dina's MacBook",
                    statusIsError = false,
                    torch = null,
                    onTorch = {},
                    onBack = {},
                    backEnabled = true,
                    viewfinder = {},
                    card = { PairConnectedCard(onDone = { done++ }, onPairAnother = null) },
                )
            }
        }
        compose.onNodeWithText("You're connected").assertExists()
    }
}
