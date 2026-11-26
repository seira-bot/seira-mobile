package com.openmausbot.companion.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.MIN_TOUCH_TARGET
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween

internal val CardShape = RoundedCornerShape(18.dp)

/** A rounded card on the surface colour with a one dp border. */
@Composable
internal fun ProfileCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = Seira.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.s1, CardShape)
            .border(BorderStroke(1.dp, colors.hairline), CardShape),
        content = content,
    )
}

/** A heading above a card, and an optional note below it. */
@Composable
internal fun ProfileGroup(
    title: String?,
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Seira.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = SeiraType.headline,
                color = colors.ink2,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { heading() },
            )
        }
        ProfileCard(content = content)
        if (note != null) {
            Text(text = note, style = SeiraType.callout, color = colors.ink3, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
internal fun CardDivider() {
    HorizontalDivider(color = Seira.colors.hairline, modifier = Modifier.padding(start = 16.dp))
}

/** A line of a card: title, optional subtitle and a trailing slot. Tappable when [onClick] is set. */
@Composable
internal fun ProfileRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = Seira.colors.ink,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Seira.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SeiraType.bodyMedium,
                color = if (enabled) titleColor else colors.ink3,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = SeiraType.callout, color = colors.ink3)
            }
        }
        trailing?.invoke()
    }
}

/** The page's tabs: large labels in a row that scrolls when the screen is narrow, the chosen one underlined. */
@Composable
internal fun ProfileTabBar(
    tabs: List<Pair<ProfileTab, String>>,
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { (tab, label) ->
                val isSelected = tab == selected
                val ink by animateColorAsState(
                    if (isSelected) colors.ink else colors.ink3,
                    seiraTween(SeiraMotion.FAST, reduced),
                    label = "profileTabInk",
                )
                val underline by animateColorAsState(
                    if (isSelected) colors.accent else Color.Transparent,
                    seiraTween(SeiraMotion.FAST, reduced),
                    label = "profileTabUnderline",
                )
                Column(
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        color = ink,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(underline, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.hairline)
    }
}

@Composable
internal fun ProfileLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Seira.colors.ink3)
    }
}

/** A plain-words notice, with an action when there is one to offer. */
@Composable
internal fun ProfileNotice(text: String, modifier: Modifier = Modifier, retry: (() -> Unit)? = null) {
    val colors = Seira.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = text, style = SeiraType.body, color = colors.ink3, textAlign = TextAlign.Center)
        if (retry != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clickable(role = Role.Button, onClick = retry)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.mobile_try_again_042c862e),
                    style = SeiraType.bodyMedium,
                    color = colors.accentText,
                )
            }
        }
    }
}
