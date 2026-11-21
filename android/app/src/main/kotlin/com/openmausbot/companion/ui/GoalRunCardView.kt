package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.GoalRunCard
import com.openmausbot.companion.core.GoalRunTone
import com.openmausbot.companion.core.GoalRuns
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.TurnLine
import kotlinx.coroutines.launch

internal fun goalStatusLabelRes(status: String): Int? = when (status) {
    "working" -> R.string.chat_goal_status_working
    "completed" -> R.string.chat_goal_status_completed
    "needs-input" -> R.string.chat_goal_status_needs_input
    "blocked" -> R.string.chat_goal_status_blocked
    "limit-reached" -> R.string.chat_goal_status_limit_reached
    "paused" -> R.string.chat_goal_status_paused
    "stopped" -> R.string.chat_goal_status_stopped
    "failed" -> R.string.chat_goal_status_failed
    else -> null
}

@Composable
internal fun toneTint(tone: GoalRunTone): Color = when (tone) {
    GoalRunTone.ACTIVE -> MaterialTheme.colorScheme.primary
    GoalRunTone.SUCCESS -> Color(BotPalette.argb("green"))
    GoalRunTone.ATTENTION -> Color(BotPalette.argb("orange"))
    GoalRunTone.ERROR -> MaterialTheme.colorScheme.error
    GoalRunTone.NEUTRAL -> secondaryTint
}

/**
 * A group goal run as a card: where it got to, the goal, the coordinator and
 * the turn it is on. Stop shows while the run works and stops the turn that is
 * running in this chat. The caller shows `text` instead when [card] is missing.
 */
@Composable
internal fun GoalRunCardView(chat: Chat, message: Message, card: GoalRunCard) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val tone = GoalRuns.tone(card.status)
    val tint = toneTint(tone)
    val label = goalStatusLabelRes(card.status)?.let { stringResource(it) } ?: card.status
    val goal = GoalRuns.compact(card.goal, GoalRuns.GOAL_LIMIT).ifEmpty { stringResource(R.string.chat_goal_group_goal) }
    val detail = GoalRuns.compact(card.detail, GoalRuns.DETAIL_LIMIT)
    val turns = when (val line = GoalRuns.turnLine(card)) {
        is TurnLine.Working -> stringResource(R.string.chat_goal_turn_of, line.turn, line.max)
        is TurnLine.Finished ->
            if (line.count == 1) {
                stringResource(R.string.chat_goal_turns_one)
            } else {
                stringResource(R.string.chat_goal_turns_many, line.count)
            }
    }
    val footer = if (chat is Chat.BotChat) {
        stringResource(R.string.chat_goal_solo, card.coordinatorName, turns)
    } else {
        stringResource(R.string.chat_goal_coordinating, card.coordinatorName, turns)
    }
    val description = stringResource(R.string.chat_goal_run_label, label) + ". " + goal
    var stopping by remember(message.id, card.status) { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .background(secondaryTint.copy(alpha = 0.10f), shape)
            .border(1.dp, tint.copy(alpha = 0.30f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(secondaryTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            GoalStatusIcon(card.status, tone, tint)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(goal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tint)
            if (detail.isNotEmpty()) {
                Text(detail, fontSize = 13.sp, color = secondaryTint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(footer, fontSize = 12.sp, color = secondaryTint)
        }
        if (GoalRuns.showsStop(card)) {
            OutlinedButton(
                enabled = !stopping,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                onClick = {
                    haptics.play(TactileAction.CHOOSE_APPROVAL)
                    stopping = true
                    scope.launch {
                        session.interrupt(chat)
                        stopping = false
                    }
                },
            ) { Text(stringResource(R.string.chat_goal_stop), fontSize = 13.sp) }
        }
    }
}

@Composable
private fun GoalStatusIcon(status: String, tone: GoalRunTone, tint: Color) {
    when {
        tone == GoalRunTone.ACTIVE ->
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
        status == "completed" -> Icon(Icons.Filled.CheckCircle, null, tint = tint, modifier = Modifier.size(18.dp))
        status == "needs-input" -> Icon(Icons.Filled.Info, null, tint = tint, modifier = Modifier.size(18.dp))
        status == "blocked" || status == "limit-reached" || status == "paused" ->
            Icon(Icons.Filled.Warning, null, tint = tint, modifier = Modifier.size(18.dp))
        status == "stopped" -> Box(Modifier.size(10.dp).background(tint, RoundedCornerShape(2.dp)))
        status == "failed" -> Icon(Icons.Filled.Close, null, tint = tint, modifier = Modifier.size(18.dp))
        else -> Icon(Icons.Filled.Info, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}
