package com.openmausbot.companion.ui

import androidx.compose.ui.res.stringResource
import android.view.KeyCharacterMap
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.windowInsetsPadding
import com.openmausbot.companion.ui.theme.ControlCircleButton
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.seiraShadow
import com.openmausbot.companion.ui.theme.seiraTween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.MentionChoice
import com.openmausbot.companion.core.PendingMessageAttachment
import com.openmausbot.companion.core.QueuedSend

/**
 * The names an @ can tag, above the composer while one is being typed — iOS
 * `MentionPicker`. Tapping writes the exact name, so the harness's routing
 * always matches and the keyboard's corrections never touch it.
 */
@Composable
private fun MentionPicker(choices: List<MentionChoice>, bots: List<Bot>, onPick: (MentionChoice) -> Unit) {
    val label = stringResource(R.string.mobile_mention_someone)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Four rows, then it scrolls: a big room stays reachable without
            // the list climbing over the conversation.
            .heightIn(max = MENTION_ROW_HEIGHT * 4)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .semantics { contentDescription = label },
    ) {
        choices.forEach { choice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MENTION_ROW_HEIGHT)
                    .clickable(role = Role.Button) { onPick(choice) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val bot = bots.firstOrNull { it.id == choice.id }
                if (bot != null) {
                    BotAvatar(bot = bot, size = 26.dp, animated = false)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = secondaryTint,
                        modifier = Modifier
                            .size(26.dp)
                            .background(secondaryTint.copy(alpha = 0.15f), CircleShape)
                            .padding(5.dp),
                    )
                }
                Text(text = "@" + choice.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                if (choice.isEveryone) {
                    Text(text = stringResource(R.string.mobile_mention_everyone), fontSize = 13.sp, color = secondaryTint, maxLines = 1)
                }
            }
        }
    }
}

private val MENTION_ROW_HEIGHT = 44.dp

/**
 * What a working bot is saying, at Hidden: one grey line above the composer,
 * replaced by each new message, instead of a bubble per message. Port of the iOS
 * `LiveStatusLine`.
 */
@Composable
private fun LiveStatusLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("live-status-line")
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No spinner of its own: the typing bubble in the transcript says the
        // bot is working, and this line says what at.
        Text(
            text = text.replace('\n', ' '),
            fontSize = 13.sp,
            color = secondaryTint,
            maxLines = 1,
            // The newest words are the ones worth reading, as on iOS.
            overflow = TextOverflow.StartEllipsis,
        )
    }
}

/** A quiet progress line above the field — `ProgressView` plus a caption on iOS. */
@Composable
private fun ComposerStatusLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = secondaryTint, maxLines = 1)
    }
}

/** The + turns a quarter-eighth turn into an ×, while the sheet is open. */
private const val PLUS_TURN_DEGREES = 45f
private const val PLUS_ROTATE_MILLIS = 240
private val COMPOSER_RADIUS = 28.dp
private val COMPOSER_PADDING = 6.dp
/** How far the pill floats above the navigation bar or the keyboard. */
private val COMPOSER_LIFT = 28.dp

/** The pill: + on the left, the field, dictate, and one ink circle for Send or the live call. */
@Composable
internal fun ChatComposer(
    modifier: Modifier = Modifier,
    name: String,
    draft: String,
    accessory: ComposerAccessory,
    commands: List<SlashCommand>,
    plusOpen: Boolean,
    dictationListening: Boolean,
    dictationLocked: Boolean,
    dictationError: String?,
    onTogglePlus: () -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleDictation: () -> Unit,
    onSend: () -> Unit,
    onToggleHud: () -> Unit,
    onCloseHud: () -> Unit,
    onSelectCommand: (SlashCommand) -> Unit,
    chips: List<PredictiveChip>,
    onSelectChip: (PredictiveChip) -> Unit,
    attachments: List<PendingMessageAttachment>,
    sending: Boolean,
    preparing: Boolean,
    busy: Boolean,
    engineCanSteer: Boolean,
    /** The bot waiting on the one open question Send would answer. */
    questionAsker: String?,
    queuedSends: List<QueuedSend>,
    steering: Boolean,
    onSteer: (() -> Unit)?,
    onCancelQueued: (QueuedSend) -> Unit,
    onEditQueued: (QueuedSend) -> Unit,
    openingFileName: String?,
    attachmentError: String?,
    onRemoveAttachment: (PendingMessageAttachment) -> Unit,
    onDismissError: () -> Unit,
    mentions: List<MentionChoice>,
    mentionBots: List<Bot>,
    onSelectMention: (MentionChoice) -> Unit,
    stoppable: Boolean,
    onStop: () -> Unit,
    liveStatus: String? = null,
    callAction: ChatCallAction = ChatCallAction.NONE,
    onCall: () -> Unit = {},
) {
    val colors = Seira.colors
    val canSend = AttachmentImportRules.canSend(draft, attachments.size, preparing, sending)
    var fieldFocused by remember { mutableStateOf(false) }
    // The field keeps its own caret. A draft changed from outside it — a
    // picked @tag, dictation, a queued message handed back — puts the caret
    // at the end, where those words were written; the string overload would
    // leave it wherever it was, in the middle of the new name.
    var field by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    val fieldValue = if (field.text == draft) field else TextFieldValue(draft, TextRange(draft.length))
    val inFlight = preparing || sending
    // Held as the state rather than unwrapped with `by`: read inside the layer
    // block, the turn is a new frame, not a new composition of the composer.
    val turn = animateFloatAsState(
        targetValue = if (plusOpen) PLUS_TURN_DEGREES else 0f,
        animationSpec = seiraTween(PLUS_ROTATE_MILLIS),
        label = stringResource(R.string.mobile_plus_6a8437dd),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(chatBottomInsets())
            .padding(start = 12.dp, end = 12.dp, bottom = COMPOSER_LIFT),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (fieldFocused && mentions.isNotEmpty()) {
            MentionPicker(choices = mentions, bots = mentionBots, onPick = onSelectMention)
        }
        liveStatus?.trim()?.takeIf { it.isNotEmpty() }?.let { LiveStatusLine(it) }
        if (dictationError != null) {
            Text(
                text = dictationError,
                fontSize = 13.sp,
                color = Color(0xFFFF9800),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        // What is in flight, in the order iOS stacks them: the send or the
        // import, then a file on its way, then whatever went wrong.
        if (inFlight) {
            ComposerStatusLine(stringResource(if (preparing) R.string.mobile_preparing_attachments_343475f3 else R.string.mobile_sending_cf765512))
        }
        if (openingFileName != null) {
            ComposerStatusLine(stringResource(R.string.mobile_chat_opening_file, openingFileName))
        }
        if (attachmentError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFF9800).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .semantics(mergeDescendants = true) { },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(18.dp),
                )
                Text(text = localizedMobileCopy(attachmentError), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.mobile_dismiss_70afe9ef),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onDismissError)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        // Everything the computer is holding, stacked straight above the chat
        // bar in the order it was sent.
        queuedSends.forEach { queued ->
            QueuedSendRow(
                send = queued,
                onSteer = onSteer,
                steering = steering,
                onEdit = { onEditQueued(queued) },
                onCancel = { onCancelQueued(queued) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { attachment ->
                    PendingAttachmentChip(
                        attachment = attachment,
                        enabled = !inFlight,
                        onRemove = { onRemoveAttachment(attachment) },
                    )
                }
            }
        }
        // One or the other, never both: the HUD is what the composer is doing
        // right now, and a row of send-immediately chips under it would be a
        // second thing to tap by accident.
        when (accessory) {
            ComposerAccessory.HUD -> CommandSkillHud(
                commands = commands,
                draft = draft,
                onSelect = onSelectCommand,
                onClose = onCloseHud,
            )

            ComposerAccessory.CHIPS -> PredictiveChipsRow(chips = chips, onSelect = onSelectChip)

            ComposerAccessory.NONE -> Unit
        }

        val showSlash = draft.isEmpty() || accessory == ComposerAccessory.HUD
        val hint = if (!busy && questionAsker == null && !sending && !dictationListening) {
            stringResource(R.string.chat_composer_hint, name)
        } else {
            localizedMobileCopy(ComposerPromise.placeholder(
                name = name,
                busy = busy,
                engineCanSteer = engineCanSteer,
                sending = sending,
                listening = dictationListening,
                questionAsker = questionAsker,
            ))
        }
        val pillShape = RoundedCornerShape(COMPOSER_RADIUS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .seiraShadow(pillShape)
                .background(colors.composer, pillShape)
                .padding(COMPOSER_PADDING),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ControlCircleButton(
                icon = SeiraIcons.Plus,
                contentDescription = stringResource(if (plusOpen) R.string.mobile_a11y_close else R.string.mobile_more_4bab2d8f),
                onClick = onTogglePlus,
                size = 40.dp,
                modifier = Modifier.graphicsLayer { rotationZ = turn.value },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .padding(start = 10.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = hint,
                        style = SeiraType.body,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { next ->
                        field = next
                        if (next.text != draft) onDraftChange(next.text)
                    },
                    // Partials rebuild from a frozen base; prevent competing
                    // edits without dimming the text.
                    readOnly = dictationLocked,
                    maxLines = 5,
                    textStyle = SeiraType.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    // The software keyboard's Return breaks the line, like
                    // Messages; the button on the bar is the one send.
                    // A hardware Return still sends and Shift+Return breaks
                    // the line. Some soft keyboards deliver their Return as a
                    // key event too, so the rule also asks where it came from.
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            val sends = ComposerReturn.sends(
                                isReturnKey = event.key == Key.Enter || event.key == Key.NumPadEnter,
                                keyDown = event.type == KeyEventType.KeyDown,
                                shift = event.isShiftPressed,
                                fromSoftwareKeyboard =
                                    event.nativeKeyEvent.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD,
                            )
                            if (sends) onSend()
                            sends
                        },
                )
            }

            // The way into the HUD that does not start with typing. It steps
            // aside once there is a draft: four 48 dp targets left a 360 dp
            // phone a field about 60 dp wide.
            if (showSlash) {
                TouchTarget(
                    onClick = onToggleHud,
                    contentDescription = stringResource(R.string.mobile_slash_commands_efce77da),
                    modifier = Modifier.localizedSemantics(stateDescription = {
                        stringResource(
                            if (accessory == ComposerAccessory.HUD) R.string.mobile_a11y_expanded
                            else R.string.mobile_a11y_collapsed,
                        )
                    }),
                ) {
                    Text(
                        text = "/",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (accessory == ComposerAccessory.HUD) colors.ink else colors.ink3,
                    )
                }
            }

            // Stop sits in the bar while the turn runs, in the mic's slot. A
            // dictation already running keeps its mic so it can be stopped.
            if (stoppable) {
                TouchTarget(onClick = onStop, contentDescription = stringResource(R.string.mobile_action_stop_turn)) {
                    Box(
                        modifier = Modifier.size(32.dp).background(colors.control, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(colors.ink, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }

            if (!stoppable || dictationListening) TouchTarget(
                onClick = onToggleDictation,
                contentDescription = stringResource(
                    if (dictationListening) R.string.chat_composer_dictate_stop else R.string.chat_composer_dictate,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (dictationListening) colors.danger.copy(alpha = 0.18f) else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SeiraIcons.Mic,
                        contentDescription = null,
                        tint = if (dictationListening) colors.danger else colors.ink2,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // One ink circle with two jobs: Send once there is something to send,
            // otherwise the live call, when this chat can have one.
            val talks = !canSend && callAction != ChatCallAction.NONE
            TouchTarget(
                onClick = if (talks) onCall else onSend,
                enabled = canSend || talks,
                contentDescription = when {
                    talks -> stringResource(R.string.chat_composer_talk, name)
                    questionAsker != null -> stringResource(R.string.mobile_submit_answer_bf80bc31)
                    else -> stringResource(R.string.mobile_send_9bc2575c)
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.primary.copy(alpha = if (canSend || talks) 1f else 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (talks) SeiraIcons.AudioLines else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
