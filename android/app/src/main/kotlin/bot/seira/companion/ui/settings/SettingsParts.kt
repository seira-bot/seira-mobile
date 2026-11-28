package bot.seira.companion.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.R
import bot.seira.companion.ui.MIN_TOUCH_TARGET
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraMotion
import bot.seira.companion.ui.theme.SeiraType
import bot.seira.companion.ui.theme.rememberReducedMotion
import bot.seira.companion.ui.theme.seiraTween

private val CARD_SHAPE = RoundedCornerShape(18.dp)

@Composable
internal fun GroupTitle(text: String) {
    Text(
        text = text,
        style = SeiraType.callout.copy(fontWeight = FontWeight.Medium),
        color = Seira.colors.ink3,
        modifier = Modifier
            .padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun GroupNote(text: String) {
    Text(
        text = text,
        style = SeiraType.callout,
        color = Seira.colors.ink3,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}

@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = Seira.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(colors.s1, CARD_SHAPE)
            .border(1.dp, colors.hairline, CARD_SHAPE),
        content = content,
    )
}

@Composable
internal fun RowDivider(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(Seira.colors.hairline),
    )
}

@Composable
internal fun Chevron() {
    Icon(SeiraIcons.ChevronRight, contentDescription = null, tint = Seira.colors.ink3, modifier = Modifier.size(18.dp))
}

@Composable
internal fun ValueWithChevron(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = SeiraType.body, color = Seira.colors.ink2)
        Chevron()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipChoiceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = SeiraType.callout, color = colors.ink3)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val fill by animateColorAsState(
                    if (selected) colors.primary else Color.Transparent,
                    seiraTween(SeiraMotion.BASE, reduced),
                    label = "chipFill",
                )
                val ink by animateColorAsState(
                    if (selected) colors.onPrimary else colors.ink,
                    seiraTween(SeiraMotion.BASE, reduced),
                    label = "chipInk",
                )
                Box(
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .clip(CircleShape)
                        .background(fill, CircleShape)
                        .border(1.dp, if (selected) Color.Transparent else colors.hairline, CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = SeiraType.body.copy(fontSize = 16.sp), color = ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun PageActionPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = Seira.colors
    Box(
        modifier = modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(CircleShape)
            .background(colors.s1, CircleShape)
            .border(1.dp, colors.hairline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = SeiraType.bodyMedium.copy(fontSize = 15.sp),
            color = if (enabled) colors.ink else colors.ink3,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PageSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = SeiraType.body.copy(color = colors.ink, fontSize = 16.sp),
        cursorBrush = SolidColor(colors.ink),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = placeholder },
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(colors.s1, shape)
                    .border(1.dp, colors.hairline, shape)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SeiraIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = SeiraType.body.copy(fontSize = 16.sp), color = colors.ink3)
                    field()
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    title: String,
    onDismiss: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Seira.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag(tag),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(
                text = title,
                style = SeiraType.title2,
                color = colors.ink,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
            )
            content()
        }
    }
}

@Composable
internal fun SheetButton(text: String, onClick: () -> Unit, danger: Boolean = false, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(if (danger) colors.danger else colors.primary, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = SeiraType.headline.copy(fontSize = 16.sp),
            color = if (danger) Color.White else colors.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

@Composable
internal fun SheetTextButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SeiraType.bodyMedium.copy(fontSize = 15.sp), color = Seira.colors.ink2, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ConfirmSheet(
    title: String,
    note: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    tag: String,
) {
    SettingsSheet(title = title, onDismiss = onDismiss, tag = tag) {
        Text(note, style = SeiraType.body, color = Seira.colors.ink2, modifier = Modifier.padding(bottom = 20.dp))
        SheetButton(text = confirmLabel, onClick = onConfirm, danger = true)
        SheetTextButton(text = stringResource(R.string.mobile_cancel_77dfd213), onClick = onDismiss)
    }
}

@Composable
internal fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    caption: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
    tag: String,
) {
    val colors = Seira.colors
    SettingsSheet(title = title, onDismiss = onDismiss, tag = tag) {
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                onChoose(option)
                                onDismiss()
                            },
                        )
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label(option), style = SeiraType.bodyMedium.copy(fontSize = 16.sp), color = colors.ink)
                        Text(caption(option), style = SeiraType.callout, color = colors.ink2)
                    }
                    if (isSelected) {
                        Icon(SeiraIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        SheetTextButton(text = stringResource(R.string.mobile_cancel_77dfd213), onClick = onDismiss)
    }
}
