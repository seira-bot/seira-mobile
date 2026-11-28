package bot.seira.companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import bot.seira.companion.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Offline compositions exercise Android's actual script-qualified resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChineseLocalizationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "zh-rCN")
    fun simplifiedChineseDensityAndFormattedCopy() {
        showCopy()
        compose.onNodeWithText("列表密度").assertIsDisplayed()
        compose.onNodeWithText("紧凑").assertIsDisplayed()
        compose.onNodeWithText("显示全部 27 行").assertIsDisplayed()
        compose.onNodeWithText("Seira").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "zh-rTW")
    fun traditionalChineseDensityAndFormattedCopy() {
        showCopy()
        compose.onNodeWithText("列表密度").assertIsDisplayed()
        compose.onNodeWithText("緊湊").assertIsDisplayed()
        compose.onNodeWithText("顯示全部 27 行").assertIsDisplayed()
        compose.onNodeWithText("Seira").assertIsDisplayed()
    }

    private fun showCopy() = compose.setContent {
        Column {
            Text(localizedMobileCopy("List density"))
            Text(localizedMobileCopy("Compact"))
            Text(pluralStringResource(R.plurals.mobile_diff_show_all_lines, 27, 27))
            Text(stringResource(R.string.app_name))
        }
    }
}
