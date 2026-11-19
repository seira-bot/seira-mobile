package com.openmausbot.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.character.CarvedAvatar
import com.openmausbot.companion.core.SeiraLookCatalog
import com.openmausbot.companion.core.SeiraLookSlot
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType

private val AVATAR_IN_TILE = 76.dp

@Composable
internal fun ChangeLookPanel(state: ChangeLookState) {
    when (state.category) {
        ChangeLookCategory.CHARACTERS -> CharactersPanel(state)
        ChangeLookCategory.HAIR -> HairPanel(state)
        ChangeLookCategory.COLOUR -> ColourPanel(state)
        ChangeLookCategory.FACE -> FacePanel(state)
        ChangeLookCategory.EXTRAS -> ExtrasPanel(state)
    }
}

@Composable
private fun CharactersPanel(state: ChangeLookState) {
    val haptics = rememberHaptics()
    ChangeLookGrid(state.cast, columns = 3, spacing = 12.dp) { member ->
        ChangeLookTile(
            title = member.name,
            selected = member.look == state.look,
            onClick = {
                haptics.play(HapticCue.SELECT)
                state.pick(member)
            },
        ) {
            CarvedAvatar(look = member.look, size = AVATAR_IN_TILE)
        }
    }
}

@Composable
private fun HairPanel(state: ChangeLookState) {
    val haptics = rememberHaptics()
    ChangeLookGrid(SeiraLookCatalog.hairIds, columns = 3, spacing = 12.dp) { id ->
        ChangeLookTile(
            title = changeLookHairLabel(id),
            selected = state.look.hair == id,
            onClick = {
                haptics.play(HapticCue.SELECT)
                state.setHair(id)
            },
        ) {
            CarvedAvatar(look = state.look.copy(hair = id), size = AVATAR_IN_TILE)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourPanel(state: ChangeLookState) {
    val colors = Seira.colors
    val haptics = rememberHaptics()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SeiraLookCatalog.starterColors.forEach { name ->
            val selected = state.look.color == name
            val fill = SeiraLookCatalog.colorHex[name]?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color.Gray
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        haptics.play(HapticCue.SELECT)
                        state.setColor(name)
                    }
                    .border(BorderStroke(2.dp, if (selected) colors.ink else Color.Transparent), CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val label = changeLookColorLabel(name)
                Box(
                    Modifier
                        .size(44.dp)
                        .background(fill, CircleShape)
                        .semantics { contentDescription = label },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacePanel(state: ChangeLookState) {
    val haptics = rememberHaptics()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChangeLookState.faceExpressions.forEach { id ->
            ChangeLookChip(
                title = changeLookExpressionLabel(id),
                selected = state.isExpressionSelected(id),
                onClick = {
                    haptics.play(HapticCue.SELECT)
                    state.setExpression(id)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtrasPanel(state: ChangeLookState) {
    val colors = Seira.colors
    val haptics = rememberHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        val count = state.look.accessories.size
        val spoken = stringResource(R.string.change_look_extras_count_a11y, count)
        Text(
            text = stringResource(R.string.change_look_extras_count, count),
            style = SeiraType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = colors.ink2,
            modifier = Modifier.semantics { contentDescription = spoken },
        )
        SeiraLookSlot.entries.forEach { slot ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = changeLookSlotLabel(slot),
                    style = SeiraType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = colors.ink2,
                    modifier = Modifier.semantics { heading() },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SeiraLookCatalog.accessoryIds.filter { SeiraLookCatalog.slot(it) == slot }.forEach { id ->
                        val on = id in state.look.accessories
                        ChangeLookTile(
                            title = changeLookExtraLabel(id),
                            selected = on,
                            onClick = {
                                haptics.play(HapticCue.SELECT)
                                state.toggleExtra(id)
                            },
                            modifier = Modifier.width(80.dp),
                            height = 80.dp,
                            radius = 18.dp,
                            captionSize = 12.sp,
                        ) {
                            ChangeLookSticker(id, Modifier.padding(14.dp))
                            if (on) ChangeLookCheckBadge()
                        }
                    }
                }
            }
        }
    }
}
