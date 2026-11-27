package com.openmausbot.companion.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.openmausbot.companion.ui.theme.LocalThemeReveal
import com.openmausbot.companion.ui.theme.ThemeMode
import com.openmausbot.companion.ui.theme.ThemeRevealState
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeEclipsePickerTest {
    @get:Rule val compose = createComposeRule()

    private val chosen = mutableListOf<ThemeMode>()

    private fun show(initial: ThemeMode) {
        chosen.clear()
        compose.setContent {
            var mode by remember { mutableStateOf(initial) }
            CompositionLocalProvider(LocalThemeReveal provides ThemeRevealState()) {
                ThemeEclipsePicker(selected = mode, onSelect = { chosen += it; mode = it })
            }
        }
    }

    @Test
    fun `the picker is a group of three radio buttons and one is selected`() {
        show(ThemeMode.AUTO)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertCountEquals(3)
        compose.onNodeWithTag(themeTileTag(ThemeMode.AUTO)).assertIsSelected()
        compose.onNodeWithTag(themeTileTag(ThemeMode.LIGHT)).assertIsNotSelected()
        compose.onNodeWithTag(themeTileTag(ThemeMode.DARK)).assertIsNotSelected()
    }

    @Test
    fun `a tap on Dark stores Dark`() {
        show(ThemeMode.LIGHT)
        compose.onNodeWithTag(themeTileTag(ThemeMode.DARK)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(ThemeMode.DARK), chosen)
    }

    @Test
    fun `a tap on the selected tile changes nothing`() {
        show(ThemeMode.LIGHT)
        compose.onNodeWithTag(themeTileTag(ThemeMode.LIGHT)).performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), chosen)
    }
}
