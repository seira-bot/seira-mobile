package com.openmausbot.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.character.SeiraAssets
import com.openmausbot.companion.character.motionIsOff
import com.openmausbot.companion.core.SeiraLookSlot
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.rememberReducedMotion

@Composable
internal fun changeLookCategoryTitle(category: ChangeLookCategory): String = stringResource(
    when (category) {
        ChangeLookCategory.HAIR -> R.string.change_look_cat_hair
        ChangeLookCategory.COLOUR -> R.string.change_look_cat_colour
        ChangeLookCategory.FACE -> R.string.change_look_cat_face
        ChangeLookCategory.EXTRAS -> R.string.change_look_cat_extras
    },
)

@Composable
internal fun changeLookHairLabel(id: String): String = when (id) {
    "seira" -> stringResource(R.string.change_look_hair_seira)
    "seira-long" -> stringResource(R.string.change_look_hair_seira_long)
    "bob" -> stringResource(R.string.change_look_hair_bob)
    "twin-tails" -> stringResource(R.string.change_look_hair_twin_tails)
    "ponytail" -> stringResource(R.string.change_look_hair_ponytail)
    "long-straight" -> stringResource(R.string.change_look_hair_long_straight)
    "bun" -> stringResource(R.string.change_look_hair_bun)
    "long-wavy" -> stringResource(R.string.change_look_hair_long_wavy)
    "short-neat" -> stringResource(R.string.change_look_hair_short_neat)
    "center-part" -> stringResource(R.string.change_look_hair_center_part)
    "spiky" -> stringResource(R.string.change_look_hair_spiky)
    "slick-back" -> stringResource(R.string.change_look_hair_slick_back)
    "long-tied" -> stringResource(R.string.change_look_hair_long_tied)
    "long-loose" -> stringResource(R.string.change_look_hair_long_loose)
    "buzz" -> stringResource(R.string.change_look_hair_buzz)
    "messy" -> stringResource(R.string.change_look_hair_messy)
    "none" -> stringResource(R.string.change_look_hair_none)
    else -> id
}

@Composable
internal fun changeLookColorLabel(name: String): String = when (name) {
    "green" -> stringResource(R.string.change_look_color_green)
    "blue" -> stringResource(R.string.change_look_color_blue)
    "red" -> stringResource(R.string.change_look_color_red)
    "orange" -> stringResource(R.string.change_look_color_orange)
    "purple" -> stringResource(R.string.change_look_color_purple)
    "cyan" -> stringResource(R.string.change_look_color_cyan)
    "pink" -> stringResource(R.string.change_look_color_pink)
    "yellow" -> stringResource(R.string.change_look_color_yellow)
    "teal" -> stringResource(R.string.change_look_color_teal)
    "coral" -> stringResource(R.string.change_look_color_coral)
    else -> name
}

@Composable
internal fun changeLookExpressionLabel(id: String): String = when (id) {
    "idle" -> stringResource(R.string.change_look_expr_idle)
    "happy" -> stringResource(R.string.change_look_expr_happy)
    "surprised" -> stringResource(R.string.change_look_expr_surprised)
    "drowsy" -> stringResource(R.string.change_look_expr_drowsy)
    "thinking" -> stringResource(R.string.change_look_expr_thinking)
    else -> id
}

@Composable
internal fun changeLookExtraLabel(id: String): String = when (id) {
    "glasses" -> stringResource(R.string.change_look_extra_glasses)
    "sunglasses" -> stringResource(R.string.change_look_extra_sunglasses)
    "earring" -> stringResource(R.string.change_look_extra_earring)
    "headphones" -> stringResource(R.string.change_look_extra_headphones)
    "headset-mic" -> stringResource(R.string.change_look_extra_headset_mic)
    "ribbon" -> stringResource(R.string.change_look_extra_ribbon)
    "hairpin" -> stringResource(R.string.change_look_extra_hairpin)
    "tiara" -> stringResource(R.string.change_look_extra_tiara)
    "cap" -> stringResource(R.string.change_look_extra_cap)
    "antenna" -> stringResource(R.string.change_look_extra_antenna)
    "scarf" -> stringResource(R.string.change_look_extra_scarf)
    "bowtie" -> stringResource(R.string.change_look_extra_bowtie)
    else -> id
}

@Composable
internal fun changeLookSlotLabel(slot: SeiraLookSlot): String = stringResource(
    when (slot) {
        SeiraLookSlot.FACE -> R.string.change_look_slot_face
        SeiraLookSlot.EARS -> R.string.change_look_slot_ears
        SeiraLookSlot.HAIR -> R.string.change_look_slot_hair
        SeiraLookSlot.TOP -> R.string.change_look_slot_top
        SeiraLookSlot.NECK -> R.string.change_look_slot_neck
    },
)

/** A tinted tile with its name under it. The whole column is one button that says its name and whether it is picked. */
@Composable
internal fun ChangeLookTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 104.dp,
    radius: Dp = 22.dp,
    captionSize: TextUnit = 13.sp,
    art: @Composable BoxScope.() -> Unit,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = title },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(changeLookTileColor(), shape)
                .border(BorderStroke(2.dp, if (selected) colors.ink else Color.Transparent), shape),
            contentAlignment = Alignment.Center,
            content = art,
        )
        Text(
            text = title,
            style = SeiraType.callout.copy(fontSize = captionSize),
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** In dark the tint is as dark as the sheet, so tiles take the control colour. */
@Composable
internal fun changeLookTileColor(): Color = Seira.colors.let { if (it.isDark) it.control else it.tint }

@Composable
internal fun ChangeLookChip(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else colors.control, CircleShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = SeiraType.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = if (selected) colors.onPrimary else colors.ink,
                maxLines = 1,
            )
        }
    }
}

/** The picture of one extra on its own, as the desktop sticker sheet draws it. */
@Composable
internal fun ChangeLookSticker(id: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(id) {
        val assets = SeiraAssets.forContext(context)
        assets.accessory(id)?.let { assets.accessoryImage(it) }?.bitmap?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}

@Composable
internal fun BoxScope.ChangeLookCheckBadge() {
    val colors = Seira.colors
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(20.dp)
            .background(colors.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SeiraIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(12.dp))
    }
}

/** Rows of [columns] equal cells; the last row is padded so its cells stay the same width. */
@Composable
internal fun <T> ChangeLookGrid(
    items: List<T>,
    columns: Int,
    spacing: Dp,
    modifier: Modifier = Modifier,
    cell: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { Box(Modifier.weight(1f)) { cell(it) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A small arc that turns while a save runs. With motion off it stays still. */
@Composable
internal fun ChangeLookSpinner(color: Color, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val angle = remember { mutableFloatStateOf(0f) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            if (motionIsOff()) return@LaunchedEffect
            var start = Long.MIN_VALUE
            while (true) {
                withFrameNanos { now ->
                    if (start == Long.MIN_VALUE) start = now
                    angle.floatValue = ((now - start) / 1_000_000L % SPIN_MILLIS) * 360f / SPIN_MILLIS
                }
            }
        }
    }
    Canvas(modifier.size(22.dp)) {
        val stroke = 2.5.dp.toPx()
        drawArc(
            color = color,
            startAngle = angle.floatValue - 90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private const val SPIN_MILLIS = 900L
