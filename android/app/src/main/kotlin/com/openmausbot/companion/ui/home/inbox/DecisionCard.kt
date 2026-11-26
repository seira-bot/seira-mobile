package com.openmausbot.companion.ui.home.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.ApprovalAnswers
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.ChatAvatar
import com.openmausbot.companion.ui.ChatUpdate
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.TactileAction
import com.openmausbot.companion.ui.play
import com.openmausbot.companion.ui.rememberHaptics
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType
import kotlinx.coroutines.launch

/**
 * One decision that waits for the person. An answer taken here goes through the
 * same session call as the card in the chat, so it settles the card everywhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DecisionCard(
    update: ChatUpdate,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var answering by remember(update.id) { mutableStateOf(false) }

    val card = update.card
    val answer = remember(card) { InboxDecisions.answer(card) }
    val detail = remember(update) { InboxDecisions.detail(update) }
    val kindLabel = when (InboxDecisions.kind(card)) {
        DecisionKind.APPROVAL -> stringResource(R.string.inbox_kind_approval)
        DecisionKind.QUESTION -> stringResource(R.string.inbox_kind_question)
        DecisionKind.WAITING -> stringResource(R.string.mobile_needs_you_0d9d0df1)
    }
    val kindLine = if (InboxDecisions.namesBot(update)) {
        kindLabel
    } else {
        stringResource(R.string.inbox_kind_with_bot, kindLabel, update.chat.name)
    }
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.s1, shape)
            .border(1.dp, colors.hairline, shape)
            .padding(16.dp)
            .testTag("decision-${update.id}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatAvatar(chat = update.chat, size = 38.dp, state = CharacterMood.IDLE, animated = false)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = InboxDecisions.title(update),
                    style = SeiraType.headline,
                    color = colors.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = kindLine, style = SeiraType.caption, color = colors.warning)
            }
        }

        detail?.let {
            Text(
                text = it,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = colors.ink2,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (answer) {
            is DecisionAnswer.Options -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                answer.labels.forEach { option ->
                    DecisionButton(
                        label = option,
                        filled = option == answer.primary,
                        enabled = !answering && card != null,
                        onClick = {
                            if (card == null) return@DecisionButton
                            haptics.play(TactileAction.CHOOSE_APPROVAL)
                            answering = true
                            scope.launch {
                                ApprovalAnswers.choose(session, update.chat, card, option)
                                answering = false
                            }
                        },
                    )
                }
            }
            DecisionAnswer.InChat -> DecisionButton(
                label = stringResource(R.string.inbox_answer_in_chat),
                filled = true,
                enabled = true,
                onClick = onOpenChat,
            )
            DecisionAnswer.ReviewSkill -> Text(
                text = stringResource(R.string.mobile_open_the_chat_to_review_skill_md_6225775b),
                style = SeiraType.caption,
                color = colors.ink3,
            )
            DecisionAnswer.None -> Unit
        }

        val openLabel = stringResource(R.string.inbox_open_chat_with, update.chat.name)
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenChat)
                .semantics { contentDescription = openLabel },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.inbox_open_in_chat),
                fontSize = 13.sp,
                color = colors.ink3,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun DecisionButton(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = Seira.colors
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(if (filled) colors.primary else Color.Transparent, CircleShape)
            .then(if (filled) Modifier else Modifier.border(1.dp, colors.line2, CircleShape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = if (filled) colors.onPrimary else colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
