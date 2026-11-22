package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType

/** What the Generate and Upload tabs show and do. */
internal data class AvatarPanels(
    val showsPicture: Boolean = false,
    val hasPicture: Boolean = false,
    val busy: Boolean = false,
    val note: String? = null,
    val generationKnown: Boolean = true,
    val generationReady: Boolean = true,
    val canGenerate: Boolean = false,
    val prompt: String = "",
    val onPrompt: (String) -> Unit = {},
    val onGenerate: () -> Unit = {},
    val onChoose: () -> Unit = {},
    val onUsePicture: () -> Unit = {},
    val onRemovePicture: () -> Unit = {},
)

@Composable
internal fun avatarTabTitle(tab: AvatarTab): String = stringResource(
    when (tab) {
        AvatarTab.SEIRA -> R.string.avatar_tab_seira
        AvatarTab.GENERATE -> R.string.avatar_tab_generate
        AvatarTab.UPLOAD -> R.string.avatar_tab_upload
    },
)

private fun avatarTabTag(tab: AvatarTab): String = "avatar-tab-" + tab.name.lowercase()

/** The three-way switch under the preview. */
@Composable
internal fun AvatarTabs(tab: AvatarTab, onTab: (AvatarTab) -> Unit) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.control, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AvatarTab.entries.forEach { entry ->
            val selected = entry == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else Color.Transparent, CircleShape)
                    .selectable(selected = selected, role = Role.Tab) { onTab(entry) }
                    .testTag(avatarTabTag(entry)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = avatarTabTitle(entry),
                    style = SeiraType.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = if (selected) colors.onPrimary else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun GeneratePanel(panels: AvatarPanels) {
    val colors = Seira.colors
    val setup = stringResource(R.string.avatar_generate_setup)
    val needsSetup = panels.generationKnown && !panels.generationReady
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = panels.prompt,
            onValueChange = panels.onPrompt,
            label = { Text(stringResource(R.string.avatar_generate_prompt)) },
            enabled = !panels.busy,
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("avatar-prompt"),
        )
        if (needsSetup) {
            Text(
                text = setup,
                style = SeiraType.callout,
                color = colors.ink2,
                modifier = Modifier.fillMaxWidth().testTag("avatar-generate-setup"),
            )
        }
        AvatarPill(
            text = stringResource(R.string.avatar_generate_action),
            tag = "avatar-generate",
            primary = true,
            enabled = panels.canGenerate,
            reason = if (needsSetup) setup else null,
            onClick = panels.onGenerate,
        )
    }
}

@Composable
internal fun UploadPanel(panels: AvatarPanels, connected: Boolean) {
    val colors = Seira.colors
    val enabled = connected && !panels.busy
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvatarPill(
            text = stringResource(R.string.avatar_upload_choose),
            tag = "avatar-choose",
            primary = true,
            enabled = enabled,
            onClick = panels.onChoose,
        )
        Text(
            text = stringResource(R.string.avatar_upload_hint),
            style = SeiraType.callout,
            color = colors.ink2,
            modifier = Modifier.fillMaxWidth().testTag("avatar-upload-hint"),
        )
        if (panels.hasPicture && !panels.showsPicture) {
            AvatarPill(
                text = stringResource(R.string.avatar_upload_use),
                tag = "avatar-use",
                enabled = enabled,
                onClick = panels.onUsePicture,
            )
        }
        if (panels.hasPicture) {
            AvatarPill(
                text = stringResource(R.string.avatar_upload_remove),
                tag = "avatar-remove",
                destructive = true,
                enabled = enabled,
                onClick = panels.onRemovePicture,
            )
        }
    }
}

@Composable
private fun AvatarPill(
    text: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    primary: Boolean = false,
    destructive: Boolean = false,
    reason: String? = null,
) {
    val colors = Seira.colors
    val fill = if (primary) colors.primary else colors.control
    val ink = when {
        primary -> colors.onPrimary
        destructive -> colors.danger
        else -> colors.ink
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(fill.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag)
            .semantics { if (!enabled && reason != null) stateDescription = reason },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SeiraType.headline.copy(fontSize = 16.sp),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
