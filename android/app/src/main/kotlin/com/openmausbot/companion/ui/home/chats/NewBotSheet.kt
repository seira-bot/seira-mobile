package com.openmausbot.companion.ui.home.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType

/**
 * The form behind "New bot". The bot exists only after Create: closing the
 * sheet before then creates nothing. [onCreate] receives the typed name, the
 * job and a key that stays the same while both stay the same, so a second tap
 * after a failure cannot make a second bot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewBotSheet(
    creating: Boolean,
    failed: Boolean,
    onCreate: (name: String, job: String, retryKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    var name by rememberSaveable { mutableStateOf("") }
    var job by rememberSaveable { mutableStateOf("") }
    val retryKey = remember(name.trim(), job.trim()) { NewBotRules.newRetryKey() }
    val canCreate = NewBotRules.canCreate(name, creating)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("new-bot-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.mobile_new_bot_66d3c052),
                style = SeiraType.title2,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            SheetTextField(
                label = stringResource(R.string.newbot_name_label),
                placeholder = stringResource(R.string.newbot_name_placeholder),
                value = name,
                onValueChange = { name = it },
                tag = "new-bot-name",
                imeAction = ImeAction.Next,
            )
            SheetTextField(
                label = stringResource(R.string.newbot_job_label),
                placeholder = stringResource(R.string.newbot_job_placeholder),
                value = job,
                onValueChange = { job = it },
                tag = "new-bot-job",
                imeAction = ImeAction.Done,
            )
            Text(
                text = stringResource(R.string.newbot_note),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.ink3,
            )
            if (failed) {
                Text(
                    text = stringResource(R.string.newbot_failed),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.warningText,
                    modifier = Modifier.testTag("new-bot-error"),
                )
            }
            SheetPrimaryButton(
                label = stringResource(R.string.newbot_create),
                enabled = canCreate,
                busy = creating,
                busyLabel = stringResource(R.string.newbot_creating),
                tag = "new-bot-create",
                onClick = { onCreate(name, job, retryKey) },
            )
            if (name.isBlank() && !creating) {
                Text(
                    text = stringResource(R.string.newbot_name_needed),
                    fontSize = 13.sp,
                    color = colors.ink3,
                    modifier = Modifier.testTag("new-bot-reason"),
                )
            }
        }
    }
}

/** A labelled one-line field in the sheet's own colours. */
@Composable
internal fun SheetTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(colors.s1, shape)
                .border(1.dp, colors.hairline, shape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(text = placeholder, fontSize = 16.sp, color = colors.ink3, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = imeAction,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(tag)
                    .semantics { contentDescription = "$label, $placeholder" },
            )
        }
    }
}

/** The full-width action at the foot of a form. A disabled one keeps its label. */
@Composable
internal fun SheetPrimaryButton(
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    busyLabel: String = label,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(tag)
            .clip(shape)
            .background(if (enabled) colors.primary else colors.s2, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                if (busy) stateDescription = busyLabel
            }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.onPrimary,
            )
        }
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.onPrimary else colors.ink3,
        )
    }
}
