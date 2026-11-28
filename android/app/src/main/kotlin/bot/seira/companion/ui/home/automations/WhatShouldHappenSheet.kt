package bot.seira.companion.ui.home.automations

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.Bot
import bot.seira.companion.ui.HapticCue
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.rememberHaptics
import bot.seira.companion.ui.theme.IdentityAvatar
import bot.seira.companion.ui.theme.InkPillButton
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraMotion
import bot.seira.companion.ui.theme.SeiraType
import bot.seira.companion.ui.theme.rememberReducedMotion
import bot.seira.companion.ui.theme.seiraTween

/**
 * "What should happen?": a sentence and a bot, then the routine form opens
 * with both filled in. [onSetUp] gets the sentence (empty for "Set it up
 * yourself") and the chosen bot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatShouldHappenSheet(
    onSetUp: (prompt: String, botId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val haptics = rememberHaptics()
    val reduced = rememberReducedMotion()
    val bots = remember(state.bots) { state.bots.filter { it.hidden != true } }

    var prompt by rememberSaveable { mutableStateOf("") }
    var chosenId by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    val chosen = bots.firstOrNull { it.id == chosenId } ?: AutomationsRules.defaultAssistant(bots)
    val canSetUp = AutomationsRules.canSetUp(prompt) && chosen != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
    ) {
        AnimatedContent(
            targetState = picking,
            transitionSpec = {
                fadeIn(seiraTween(SeiraMotion.BASE, reduced)) togetherWith fadeOut(seiraTween(SeiraMotion.FAST, reduced))
            },
            label = "what-should-happen",
        ) { choosingBot ->
            if (choosingBot) {
                BotChoice(
                    bots = bots,
                    selectedId = chosen?.id,
                    onPick = {
                        haptics.play(HapticCue.SELECT)
                        chosenId = it.id
                        picking = false
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp)
                        .testTag("what-should-happen"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.auto2_sheet_title),
                        style = SeiraType.title3,
                        color = colors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text(stringResource(R.string.auto2_describe_label)) },
                        placeholder = { Text(stringResource(R.string.auto2_describe_placeholder)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().testTag("what-should-happen-prompt"),
                    )
                    if (chosen != null) {
                        val rowDescription = stringResource(R.string.auto2_who_row, chosen.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Button) {
                                    haptics.play(HapticCue.SELECT)
                                    picking = true
                                }
                                .semantics(mergeDescendants = true) { contentDescription = rowDescription }
                                .testTag("what-should-happen-bot"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IdentityAvatar(bot = chosen, size = 36.dp, animated = false)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(chosen.name, style = SeiraType.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.auto2_who_sub), style = SeiraType.callout, color = colors.ink3)
                            }
                            Icon(SeiraIcons.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(20.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(enabled = chosen != null, role = Role.Button) {
                                haptics.play(HapticCue.SELECT)
                                chosen?.let { onSetUp("", it.id) }
                            }
                            .testTag("what-should-happen-yourself"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(SeiraIcons.Sliders, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(22.dp))
                        Text(
                            text = stringResource(R.string.auto2_set_up_yourself),
                            style = SeiraType.bodyMedium,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    InkPillButton(
                        text = stringResource(R.string.auto2_set_up),
                        enabled = canSetUp,
                        onClick = {
                            haptics.play(HapticCue.SELECT)
                            chosen?.let { onSetUp(prompt.trim(), it.id) }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("what-should-happen-go"),
                    )
                    if (!canSetUp) {
                        Text(
                            text = stringResource(R.string.auto2_set_up_needs_text),
                            style = SeiraType.callout,
                            color = colors.ink3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** The bot list: name and role, a check on the current one. */
@Composable
private fun BotChoice(bots: List<Bot>, selectedId: String?, onPick: (Bot) -> Unit) {
    val colors = Seira.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .testTag("who-does-it"),
    ) {
        Text(
            text = stringResource(R.string.auto2_who_title),
            style = SeiraType.title3,
            color = colors.ink,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        bots.forEach { bot ->
            val isSelected = bot.id == selectedId
            val description = if (isSelected) stringResource(R.string.auto2_who_selected, bot.name) else bot.name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { onPick(bot) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = description
                        selected = isSelected
                    }
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IdentityAvatar(bot = bot, size = 36.dp, animated = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(bot.name, style = SeiraType.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (bot.title.isNotBlank()) {
                        Text(bot.title, style = SeiraType.callout, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (isSelected) {
                    Icon(SeiraIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
