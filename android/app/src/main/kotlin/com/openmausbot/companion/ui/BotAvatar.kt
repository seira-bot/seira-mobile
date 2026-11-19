package com.openmausbot.companion.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.avatar.AvatarImageRules
import com.openmausbot.companion.core.AvatarCrop
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.character.CarvedAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.identityPlate

/**
 * An agent's identity image, or its carved Seira avatar.
 *
 * The image is an app-owned attachment on the paired computer, fetched with this
 * phone's bearer token, so it never goes through a URL-loading composable that
 * could not attach one. Every way that can fail lands on the carved avatar,
 * because an agent's identity must never become an empty placeholder.
 */
@Composable
internal fun BotAvatar(
    bot: Bot,
    size: Dp,
    state: CharacterMood = CharacterMood.IDLE,
    animated: Boolean = true,
    sway: Boolean = false,
    /** Left null where the name is already beside the face. Set it where the face stands alone. */
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val crop = bot.avatarCrop ?: AvatarCrop.MASCOT
    val path = bot.avatarUrl
    val store = LocalCompanion.current.avatars

    val identity = remember(path, crop) { BotAvatarRules.identity(path, crop) }
    var image by remember(identity) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(identity) { mutableStateOf(false) }

    LaunchedEffect(identity) {
        if (crop == AvatarCrop.MASCOT || path == null) return@LaunchedEffect
        val decoded = store.bitmapFor(bot)
        if (decoded == null) {
            failed = true
            return@LaunchedEffect
        }
        image = decoded
    }

    val named = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }

    val painted = image
    if (painted != null && AvatarImageRules.usesImage(crop, path, failed)) {
        val bitmap = remember(painted) { painted.asImageBitmap() }
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = named.size(size).clip(BotAvatarRules.shape(crop, size)),
        )
    } else {
        CarvedAvatar(
            look = SeiraLook.from(bot),
            size = size,
            mood = state,
            animated = animated,
            sway = sway,
            modifier = named,
        )
    }
}

/** A bot's own avatar, or for a room the first two member bots stacked. */
@Composable
internal fun ChatAvatar(
    chat: Chat,
    size: Dp,
    state: CharacterMood = CharacterMood.IDLE,
    animated: Boolean = true,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    when (chat) {
        is Chat.BotChat -> BotAvatar(
            bot = chat.bot,
            size = size,
            state = state,
            animated = animated,
            contentDescription = contentDescription,
            modifier = modifier,
        )
        is Chat.RoomChat -> RoomAvatar(room = chat.room, size = size, modifier = modifier)
    }
}

/** The member bots of [room] as a stacked pair. A room with no member bots shows the Seira avatar. */
@Composable
internal fun RoomAvatar(room: Room, size: Dp, modifier: Modifier = Modifier) {
    val state by LocalCompanion.current.session.state.collectAsState()
    val members = remember(state, room.memberIds) { RosterLayout.memberBots(state, room) }
    Box(modifier = modifier) {
        RoomFaces(members = members, size = size)
    }
}

/** The Seira avatar on its identity plate. */
@Composable
internal fun SeiraRoomAvatar(size: Dp, modifier: Modifier = Modifier) {
    val plate = identityPlate(SeiraLook.SEIRA.color, Seira.colors.isDark)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(plate, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CarvedAvatar(look = SeiraLook.SEIRA, size = size)
    }
}

/** The look a chat wears: a bot's own, and the Seira look for a room. */
internal fun Chat.look(): SeiraLook = when (this) {
    is Chat.BotChat -> SeiraLook.from(bot)
    is Chat.RoomChat -> SeiraLook.SEIRA
}

internal object BotAvatarRules {
    /**
     * What `BotAvatarView` keys `.task(id:)` on: the attachment and the crop
     * together, joined the same way, so changing either restarts the fetch and
     * clears whatever the previous identity had painted.
     */
    fun identity(avatarUrl: String?, crop: AvatarCrop): String =
        "${avatarUrl.orEmpty()}|${crop.name.lowercase()}"

    /** The mask each crop wears — `BotAvatarView.mask`. */
    fun shape(crop: AvatarCrop, size: Dp): Shape = when (crop) {
        AvatarCrop.CIRCLE -> CircleShape
        AvatarCrop.ROUNDED -> RoundedCornerShape(
            AvatarImageRules.roundedCornerRadius(size.value).dp,
        )
        AvatarCrop.SQUARE, AvatarCrop.MASCOT -> RectangleShape
    }
}
