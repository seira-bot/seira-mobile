package com.openmausbot.companion.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.AiUsageRules
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotTask
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.seiraTween
import java.util.Locale
import kotlin.math.roundToInt

internal enum class ComposerTool { TEACH, GOAL, CONTEXT }

/** How full the conversation's context is. Built only when the computer reported both numbers. */
internal data class ContextMeter(val percent: Int, val tokens: Long, val window: Long)

internal enum class CompactPhase { IDLE, WORKING, DONE, FAILED }

internal object ChatAdvanced {
    private const val TITLE_LIMIT = 80
    private const val DEFAULT_FILE = "conversation"
    private val UNSAFE_FILE_CHARS = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun currentTask(bot: Bot): BotTask? = bot.tasks?.firstOrNull { it.threadId == bot.threadId }

    fun contextMeter(task: BotTask?): ContextMeter? {
        val line = AiUsageRules.conversationUsage(task?.usage) ?: return null
        val tokens = line.contextTokens ?: return null
        val window = line.contextWindow ?: return null
        val percent = (tokens * 100.0 / window).roundToInt().coerceIn(0, 100)
        return ContextMeter(percent, tokens, window)
    }

    /**
     * The tool chips above the field. Off, the row is empty, so the composer reads as it did before
     * Advanced mode. [learnAllowed] is the learn gate: Teach stays hidden while it is false.
     */
    fun tools(
        chat: Chat,
        advanced: Boolean,
        learnAllowed: Boolean,
        goalRunning: Boolean,
        context: ContextMeter?,
    ): List<ComposerTool> {
        if (!advanced) return emptyList()
        val isDm = (chat as? Chat.RoomChat)?.room?.dm == true
        return buildList {
            if (learnAllowed) add(ComposerTool.TEACH)
            if (!goalRunning && !isDm) add(ComposerTool.GOAL)
            if (chat is Chat.BotChat && context != null) add(ComposerTool.CONTEXT)
        }
    }

    /** A chip writes its command at the start of the field and keeps what was typed. */
    fun insertion(draft: String, command: String): String =
        if (draft.startsWith(command)) draft else command + draft

    /** A short count for the context line: 850, 1.5k, 12k, 1.2M. */
    fun compactCount(count: Long): String = when {
        count < 1_000 -> count.toString()
        count < 1_000_000 -> scaled(count / 1_000.0, "k")
        else -> scaled(count / 1_000_000.0, "M")
    }

    private fun scaled(value: Double, suffix: String): String =
        if (value >= 10) "${value.roundToInt()}$suffix" else String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0") + suffix

    /** The file a downloaded transcript gets: the thread title as one safe word run, then `.md`. */
    fun markdownFileName(title: String?): String {
        val words = UNSAFE_FILE_CHARS.replace(title.orEmpty().trim(), "-").trim('-').take(TITLE_LIMIT)
        return (words.ifEmpty { DEFAULT_FILE }) + ".md"
    }

    /** The title a rename sends: trimmed and cut to what the computer keeps. */
    fun renameTitle(raw: String): String = raw.trim().take(TITLE_LIMIT)

    fun canSaveRename(raw: String): Boolean = raw.trim().isNotEmpty()

    const val RENAME_LIMIT = TITLE_LIMIT
}

internal const val RENAME_DIALOG_TAG = "chat-rename-dialog"
internal const val RENAME_FIELD_TAG = "chat-rename-field"
internal const val CONTEXT_SHEET_TAG = "chat-context-sheet"
internal const val CONTEXT_COMPACT_TAG = "chat-context-compact"
internal const val SESSION_ALLOW_TAG = "approval-allow-session"

@Composable
internal fun RenameThreadDialog(
    title: String,
    saving: Boolean,
    failed: Boolean,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onCancel() },
        modifier = Modifier.testTag(RENAME_DIALOG_TAG),
        title = { Text(stringResource(R.string.chat3_rename_heading)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failed) {
                    Text(stringResource(R.string.chat3_rename_failed), color = Seira.colors.danger, style = SeiraType.caption)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { onTitleChange(it.take(ChatAdvanced.RENAME_LIMIT)) },
                    label = { Text(stringResource(R.string.chat3_rename_label)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag(RENAME_FIELD_TAG),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !saving && ChatAdvanced.canSaveRename(title)) {
                Text(stringResource(R.string.chat3_rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.chat3_rename_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextSheet(
    meter: ContextMeter,
    phase: CompactPhase,
    canCompact: Boolean,
    onCompact: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    val fill = animateFloatAsState(
        targetValue = meter.percent / 100f,
        animationSpec = seiraTween(SeiraMotion.SHEET),
        label = "context-fill",
    )
    val percentLabel = stringResource(R.string.chat3_context_bar_a11y, meter.percent)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag(CONTEXT_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.chat3_context_title), style = SeiraType.headline, color = colors.ink)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 10.dp)
                    .background(colors.control, CircleShape)
                    .semantics(mergeDescendants = true) { contentDescription = percentLabel },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fill.value.coerceIn(0f, 1f))
                        .heightIn(min = 10.dp)
                        .fillMaxHeight()
                        .background(colors.primary, CircleShape),
                )
            }
            Text(
                stringResource(
                    R.string.chat3_context_usage,
                    ChatAdvanced.compactCount(meter.tokens),
                    ChatAdvanced.compactCount(meter.window),
                ),
                style = SeiraType.caption,
                color = colors.ink3,
            )
            val enabled = canCompact && phase != CompactPhase.WORKING
            ChatSheetRow(
                icon = ChatIcons.Layers,
                title = stringResource(R.string.chat3_context_compact),
                subtitle = when (phase) {
                    CompactPhase.WORKING -> stringResource(R.string.chat3_context_compacting)
                    CompactPhase.DONE -> stringResource(R.string.chat3_context_compacted)
                    CompactPhase.FAILED -> stringResource(R.string.chat3_context_failed)
                    CompactPhase.IDLE -> stringResource(
                        if (canCompact) R.string.chat3_context_compact_sub else R.string.chat3_context_wait,
                    )
                },
                tag = CONTEXT_COMPACT_TAG,
                enabled = enabled,
                onClick = onCompact,
            )
        }
    }
}
