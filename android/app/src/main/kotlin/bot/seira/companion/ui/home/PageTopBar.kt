package bot.seira.companion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.ui.TouchTarget
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType

private val ROUND_BUTTON = 46.dp

/**
 * The bar of a page opened from home: a round back button, the title in the
 * middle and, optionally, one action on the right.
 */
@Composable
internal fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = Seira.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
            .testTag("page-top-bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TouchTarget(
            onClick = onBack,
            contentDescription = stringResource(R.string.mobile_back_b52b36b7),
            modifier = Modifier.testTag("page-back"),
        ) {
            Box(
                modifier = Modifier
                    .size(ROUND_BUTTON)
                    .background(colors.s1, CircleShape)
                    .border(1.dp, colors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SeiraIcons.ChevronLeft, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            text = title,
            style = SeiraType.title3,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            trailing()
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}
