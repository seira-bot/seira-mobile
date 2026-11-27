package com.openmausbot.companion.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType

internal object ChatMembers {
    /** The room's bots, in the room's own order. A member that left the fleet is skipped. */
    fun of(room: Room, bots: List<Bot>): List<Bot> =
        room.memberIds.mapNotNull { id -> bots.firstOrNull { it.id == id } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMembersSheet(members: List<Bot>, onDismiss: () -> Unit) {
    val colors = Seira.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("chat-members-sheet"),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.chat2_menu_members),
                style = SeiraType.title2,
                color = colors.ink,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
            for (bot in members) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IdentityAvatar(bot = bot, size = 40.dp, animated = false)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bot.name, style = SeiraType.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (bot.title.isNotBlank()) {
                            Text(bot.title, style = SeiraType.caption, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
