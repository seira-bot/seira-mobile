package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.ui.theme.AvatarRing
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType

/**
 * The Chief of Staff, large: face, role, what it is doing, and the Talk pill.
 * The whole card opens the chief's chat. Talk is left out while a call is
 * already running, rather than drawn dead.
 */
@Composable
internal fun HomeHero(
    bot: Bot,
    role: String,
    status: String,
    busy: Boolean,
    waiting: Boolean,
    face: MausState,
    canTalk: Boolean,
    onOpen: () -> Unit,
    onTalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(24.dp)
    val talkLabel = stringResource(R.string.home_talk_to, bot.name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("home-hero")
            .clip(shape)
            .background(colors.tint, shape)
            .padding(start = 11.dp, top = 9.dp, end = 14.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .testTag("home-hero-open")
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onOpen)
                .heightIn(min = 56.dp)
                .clearAndSetSemantics { contentDescription = "${bot.name}, $role, $status" },
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The working ring is drawn outside the face, so the face gets room around it.
            Box(modifier = Modifier.padding(5.dp)) {
                IdentityAvatar(
                    bot = bot,
                    size = 56.dp,
                    ring = if (busy) AvatarRing.Working else AvatarRing.None,
                    state = face,
                    animated = false,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = bot.name,
                        style = SeiraType.title3,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = role,
                        style = SeiraType.caption,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                when {
                                    waiting -> colors.warning
                                    busy -> colors.success
                                    else -> colors.ink3
                                },
                                CircleShape,
                            ),
                    )
                    Text(
                        text = status,
                        style = SeiraType.callout,
                        color = colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (canTalk) {
            Box(
                modifier = Modifier
                    .testTag("home-talk")
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primary, CircleShape)
                    .clickable(role = Role.Button, onClick = onTalk)
                    .semantics { contentDescription = talkLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SeiraIcons.AudioLines, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
