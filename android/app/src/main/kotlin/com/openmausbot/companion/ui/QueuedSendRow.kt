package com.openmausbot.companion.ui

import com.openmausbot.companion.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.core.QueuedSend
import com.openmausbot.companion.ui.theme.Seira

/**
 * A message the computer is holding, sitting just above the chat bar.
 *
 * A mid-turn send to a bot that cannot take words into a running turn does
 * not reach the transcript: the harness holds it. The row keeps it in view,
 * next to the field it was typed in. Steer asks the computer to move the held
 * words into the running turn without stopping it; the pencil takes them back
 * into the chat bar, and the bin drops them.
 */
@Composable
fun QueuedSendRow(
    send: QueuedSend,
    /** Move these words into the running turn. Null hides the button. */
    onSteer: (() -> Unit)?,
    /** A Steer is in flight; the button shows progress until the computer answers. */
    steering: Boolean,
    /** Pull these words back into the chat bar to change them before they send. */
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Seira.colors.s2)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = send.text,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSteer != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    .clickable(enabled = !steering, role = Role.Button, onClick = onSteer)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (steering) {
                    CircularProgressIndicator(
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    text = if (steering) stringResource(R.string.mobile_steering_eabc6c5d) else stringResource(R.string.mobile_steer_02f4a3c6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.mobile_queued_edit),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onEdit)
                .size(48.dp)
                .padding(16.dp),
        )
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.mobile_delete_this_queued_message_4390e043),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onCancel)
                .size(48.dp)
                .padding(16.dp),
        )
    }
}

/** What the composer promises about the message being typed. */
object ComposerPromise {
    /**
     * The name is in the header a thumb's width above; spending the field's
     * whole width repeating it truncates the part that says what pressing
     * send will actually do.
     */
    fun placeholder(
        name: String,
        busy: Boolean,
        engineCanSteer: Boolean,
        sending: Boolean,
        listening: Boolean,
        /** The bot waiting on the one open question a typed line answers. */
        questionAsker: String? = null,
    ): String = when {
        sending -> "Sending…"
        listening -> "Listening…"
        questionAsker != null -> "Answer $questionAsker…"
        !busy -> "Ask $name"
        engineCanSteer -> "Sends into this turn"
        else -> "Sends after this turn"
    }
}
