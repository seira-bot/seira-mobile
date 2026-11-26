package com.openmausbot.companion.ui.home.inbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.seiraTween

/**
 * A large page title. The selected one carries the accent underline. With no
 * [onClick] it is a plain heading.
 */
@Composable
internal fun TitleSegment(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tag: String = "title-segment",
) {
    val colors = Seira.colors
    val ink by animateColorAsState(
        if (selected) colors.ink else colors.ink3,
        seiraTween(SeiraMotion.FAST),
        label = "title-ink",
    )
    val bar by animateColorAsState(
        if (selected) colors.accent else Color.Transparent,
        seiraTween(SeiraMotion.BASE),
        label = "title-bar",
    )
    val base = modifier
        .width(IntrinsicSize.Max)
        .heightIn(min = 48.dp)
        .testTag(tag)
    val interactive = if (onClick != null) {
        base.selectable(selected = selected, role = Role.Tab, onClick = onClick)
    } else {
        base.semantics { heading() }
    }
    Column(modifier = interactive, verticalArrangement = Arrangement.Bottom) {
        Text(
            text = label,
            style = SeiraType.title2.copy(fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(bar))
    }
}
