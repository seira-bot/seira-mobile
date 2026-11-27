package com.openmausbot.companion.ui.chat.composer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ApprovalMode
import com.openmausbot.companion.ui.PredictiveChip
import com.openmausbot.companion.ui.chat.ComposerTool
import com.openmausbot.companion.ui.quickReplyGlyph
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.seiraTween

internal const val MODE_CHIP_TAG = "composer-mode-chip"
internal const val GOAL_CHIP_TAG = "composer-goal-chip"

internal fun composerToolTag(tool: ComposerTool): String = "composer-tool.${tool.name.lowercase()}"

private val TOUCH = 48.dp
private val PILL_HEIGHT = 36.dp

/** One line above the field: the approval mode, a running goal, then the quick chips. */
@Composable
internal fun ComposerChipRow(
    showMode: Boolean,
    mode: ApprovalMode?,
    onMode: () -> Unit,
    goal: String?,
    chips: List<PredictiveChip>,
    onSelectChip: (PredictiveChip) -> Unit,
    modifier: Modifier = Modifier,
    tools: List<ComposerTool> = emptyList(),
    contextPercent: Int? = null,
    onTool: (ComposerTool) -> Unit = {},
) {
    if (!showMode && goal == null && chips.isEmpty() && tools.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (tools.isNotEmpty()) Modifier.animateContentSize(seiraTween(SeiraMotion.BASE)) else Modifier)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMode) {
            val name = stringResource(ModeChip.nameRes(mode))
            val label = stringResource(R.string.composer_mode_chip_a11y, name)
            Pill(
                text = name,
                onClick = onMode,
                description = label,
                tag = MODE_CHIP_TAG,
                trailingChevron = true,
            )
        }
        if (goal != null) {
            Pill(
                text = stringResource(R.string.composer_goal_chip, goal),
                onClick = null,
                description = stringResource(R.string.composer_goal_chip, goal),
                tag = GOAL_CHIP_TAG,
                soft = true,
            )
        }
        tools.forEach { tool ->
            val text = when (tool) {
                ComposerTool.TEACH -> stringResource(R.string.chat3_tool_teach)
                ComposerTool.GOAL -> stringResource(R.string.chat3_tool_goal)
                ComposerTool.CONTEXT -> stringResource(R.string.chat3_tool_context, contextPercent ?: 0)
            }
            val description = when (tool) {
                ComposerTool.TEACH -> stringResource(R.string.chat3_tool_teach_a11y)
                ComposerTool.GOAL -> stringResource(R.string.chat3_tool_goal_a11y)
                ComposerTool.CONTEXT -> stringResource(R.string.chat3_tool_context_a11y, contextPercent ?: 0)
            }
            Pill(
                text = text,
                onClick = { onTool(tool) },
                description = description,
                tag = composerToolTag(tool),
            )
        }
        chips.forEach { chip ->
            Pill(
                text = chip.title,
                onClick = { onSelectChip(chip) },
                description = chip.title,
                tag = "composer-chip",
                glyph = chip.icon?.let(::quickReplyGlyph),
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    onClick: (() -> Unit)?,
    description: String,
    tag: String,
    glyph: String? = null,
    trailingChevron: Boolean = false,
    soft: Boolean = false,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(50)
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Box(
        modifier = Modifier
            .heightIn(min = TOUCH)
            .then(clickable)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = PILL_HEIGHT)
                .background(if (soft) colors.accentSoft else colors.s1, shape)
                .border(BorderStroke(1.dp, colors.hairline), shape)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) Text(glyph, fontSize = 13.sp, color = colors.ink3)
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (soft) colors.accentText else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingChevron) {
                Icon(
                    imageVector = SeiraIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.ink3,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
