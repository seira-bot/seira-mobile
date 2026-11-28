package bot.seira.companion.ui.profile

import android.content.ClipData
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.Bot
import bot.seira.companion.core.BotDepartments
import bot.seira.companion.ui.HapticCue
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.rememberHaptics
import bot.seira.companion.ui.theme.InkPillButton
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long an answer stays under the group. */
private const val ANSWER_MILLIS = 2500L

/**
 * "Bot actions": small things to do with one bot, shown in Advanced mode only.
 * Each row changes the screen after the computer answers, never before.
 */
@Composable
internal fun BotActionsGroup(bot: Bot) {
    val environment = LocalCompanion.current
    val advanced by environment.chatPreferences.advancedMode.collectAsState()
    if (!advanced) return

    val session = environment.session
    val state by session.state.collectAsState()
    val colors = Seira.colors
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val clipboard = LocalClipboard.current
    var busy by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    var answerTick by remember { mutableStateOf(0) }
    var moving by rememberSaveable { mutableStateOf(false) }

    val failed = stringResource(R.string.auto2_failed)
    val pinnedDone = stringResource(R.string.auto2_done_pinned)
    val unpinnedDone = stringResource(R.string.auto2_done_unpinned)
    val unreadDone = stringResource(R.string.auto2_done_unread)
    val allReadDone = stringResource(R.string.auto2_done_all_read)
    val movedDone = stringResource(R.string.auto2_done_moved)
    val copiedDone = stringResource(R.string.auto2_done_copied)

    LaunchedEffect(answerTick) {
        if (answer == null) return@LaunchedEffect
        delay(ANSWER_MILLIS)
        answer = null
    }

    fun say(text: String) {
        answer = text
        answerTick++
    }

    fun change(done: String, call: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = try {
                call()
            } finally {
                busy = false
            }
            if (ok) {
                haptics.play(HapticCue.SUCCESS)
                say(done)
            } else {
                say(failed)
            }
        }
    }

    val pinned = BotActionRules.isPinned(bot)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileGroup(title = stringResource(R.string.auto2_actions_group)) {
            val actions = BotActionRules.visible(bot)
            actions.forEachIndexed { index, action ->
                if (index > 0) CardDivider()
                val title = when (action) {
                    BotAction.PIN -> stringResource(if (pinned) R.string.auto2_unpin else R.string.auto2_pin)
                    BotAction.MARK_UNREAD -> stringResource(R.string.auto2_mark_unread)
                    BotAction.MARK_ALL_READ -> stringResource(R.string.auto2_mark_all_read)
                    BotAction.MOVE -> stringResource(R.string.auto2_move_department)
                    BotAction.COPY_ID -> stringResource(R.string.auto2_copy_id)
                }
                ProfileRow(
                    title = title,
                    enabled = !busy,
                    modifier = Modifier.testTag("bot-action-${action.name}"),
                    onClick = {
                        when (action) {
                            BotAction.PIN -> change(if (pinned) unpinnedDone else pinnedDone) {
                                session.setBotFlags(bot.id, pinned = !pinned)
                            }
                            BotAction.MARK_UNREAD -> change(unreadDone) { session.setBotFlags(bot.id, unread = true) }
                            BotAction.MARK_ALL_READ -> change(allReadDone) { session.markBotAllRead(bot.id) }
                            BotAction.MOVE -> moving = true
                            BotAction.COPY_ID -> scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Conversation ID", bot.threadId)))
                                haptics.play(HapticCue.SELECT)
                                say(copiedDone)
                            }
                        }
                    },
                )
            }
        }
        Text(
            text = answer.orEmpty(),
            style = SeiraType.callout,
            color = if (answer == failed) colors.danger else colors.ink2,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .heightIn(min = 20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("bot-action-answer"),
        )
    }

    if (moving) {
        DepartmentSheet(
            bot = bot,
            departments = BotDepartments.names(state.bots, state.declaredSections),
            onPick = { name ->
                moving = false
                change(movedDone) { session.moveToDepartment(bot.id, name) }
            },
            onDismiss = { moving = false },
        )
    }
}

/** General, the departments the phone knows, and a way to start a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepartmentSheet(
    bot: Bot,
    departments: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    val current = BotDepartments.current(bot)
    var naming by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val clean = BotDepartments.cleanName(draft)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("department-sheet"),
        ) {
            Text(
                text = stringResource(R.string.auto2_department_title),
                style = SeiraType.title3,
                color = colors.ink,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            val choices = listOf("" to stringResource(R.string.auto2_department_general)) + departments.map { it to it }
            choices.forEach { (name, label) ->
                val isCurrent = BotActionRules.sameDepartment(current, name)
                val description = if (isCurrent) stringResource(R.string.auto2_department_current, label) else label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { onPick(name) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = description
                            selected = isCurrent
                        }
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = SeiraType.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
                    if (isCurrent) {
                        Icon(SeiraIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (!naming) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { naming = true }
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SeiraIcons.Plus, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.auto2_department_new), style = SeiraType.bodyMedium, color = colors.ink)
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= BotDepartments.MAX_NAME_LENGTH) draft = it },
                        label = { Text(stringResource(R.string.auto2_department_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("department-name"),
                    )
                    InkPillButton(
                        text = stringResource(R.string.auto2_department_move),
                        enabled = clean != null,
                        onClick = { clean?.let(onPick) },
                        modifier = Modifier.fillMaxWidth().testTag("department-move"),
                    )
                }
            }
        }
    }
}
