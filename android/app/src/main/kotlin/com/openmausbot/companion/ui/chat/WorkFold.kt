package com.openmausbot.companion.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.TranscriptRow
import com.openmausbot.companion.core.isStatusNotice
import com.openmausbot.companion.core.transcriptRows
import com.openmausbot.companion.ui.MIN_TOUCH_TARGET
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween

internal sealed interface WorkDuration {
    data object Instant : WorkDuration
    data class Seconds(val seconds: Int) : WorkDuration
    data class Minutes(val minutes: Int, val seconds: Int) : WorkDuration
}

internal object WorkFold {
    fun duration(elapsedMillis: Double): WorkDuration {
        if (!elapsedMillis.isFinite() || elapsedMillis < 1_000) return WorkDuration.Instant
        val total = (elapsedMillis / 1_000).toInt()
        return if (total < 60) WorkDuration.Seconds(total) else WorkDuration.Minutes(total / 60, total % 60)
    }

    fun runElapsed(items: List<Message>): Double {
        if (items.size < 2) return 0.0
        return (items.last().at - items.first().at).coerceAtLeast(0.0)
    }

    fun rows(messages: List<Message>, detail: ActivityDetail): List<TranscriptRow> {
        val rows = transcriptRows(messages, detail)
        return if (detail == ActivityDetail.FULL) foldSteps(rows) else rows
    }

    /** Full lists every step once opened, so it starts open; Reduced starts as one line. */
    fun startsOpen(detail: ActivityDetail): Boolean = detail == ActivityDetail.FULL

    /**
     * Full detail keeps every step, but two or more finished steps in a row
     * sit behind one fold. A failure and a status notice stay on their own.
     */
    fun foldSteps(rows: List<TranscriptRow>): List<TranscriptRow> = buildList {
        val run = mutableListOf<Message>()
        fun flush() {
            when (run.size) {
                0 -> Unit
                1 -> add(TranscriptRow.Single(run.single()))
                else -> add(TranscriptRow.ActivityRun(run.toList()))
            }
            run.clear()
        }
        for (row in rows) {
            val message = (row as? TranscriptRow.Single)?.message
            if (message != null && foldable(message)) {
                run += message
            } else {
                flush()
                add(row)
            }
        }
        flush()
    }

    private fun foldable(message: Message): Boolean =
        message.kind == Message.Kind.ACTIVITY && message.tool?.ok != false && !isStatusNotice(message)
}

@Composable
internal fun workLabel(duration: WorkDuration, running: Boolean, steps: Int): String = when {
    running -> stringResource(R.string.mobile_running_steps, steps)
    duration is WorkDuration.Seconds -> stringResource(
        R.string.chat2_work_done,
        stringResource(R.string.chat2_work_seconds, duration.seconds),
    )
    duration is WorkDuration.Minutes -> stringResource(
        R.string.chat2_work_done,
        stringResource(R.string.chat2_work_minutes, duration.minutes, duration.seconds),
    )
    else -> stringResource(R.string.chat2_work_instant)
}

/** The fold line: "Worked for 21s · Show". */
@Composable
internal fun WorkFoldPill(
    label: String,
    running: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    chevronTag: String = WORK_FOLD_CHEVRON_TAG,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = seiraTween(SeiraMotion.BASE, reduced),
        label = chevronTag,
    )
    val action = stringResource(if (expanded) R.string.mobile_hide_34d8b60f else R.string.mobile_show_d97d1ee3)
    val description = stringResource(
        if (expanded) R.string.mobile_a11y_summary_expanded else R.string.mobile_a11y_summary_collapsed,
        label,
    )
    Box(
        modifier = modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .background(colors.s1, CircleShape)
                .border(1.dp, colors.hairline, CircleShape)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp, color = colors.ink2)
            } else {
                Icon(SeiraIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
            }
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink2)
            Text("·", fontSize = 13.sp, color = colors.ink3)
            Text(action, fontSize = 13.sp, color = colors.ink3)
            Icon(
                SeiraIcons.ChevronDown,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = turn }
                    .testTag(chevronTag),
            )
        }
    }
}

internal const val WORK_FOLD_CHEVRON_TAG = "work-fold-chevron"
