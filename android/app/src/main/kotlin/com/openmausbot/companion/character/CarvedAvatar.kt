package com.openmausbot.companion.character

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.withFrameNanos
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/** The carved Seira avatar. It stays still unless [animated] is true and the system has animations on. */
@Composable
internal fun CarvedAvatar(
    hair: String?,
    color: String,
    accessories: List<String>,
    size: Dp,
    mood: CharacterMood = CharacterMood.IDLE,
    animated: Boolean = false,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val assets = LocalContext.current.assets
    val live = animated && !rememberReducedMotion()
    val look = remember(hair, accessories) { CarvedLook.of(hair, accessories) }
    val animator = remember { CarvedAnimator(mood) }
    val pose = remember { mutableStateOf(animator.pose) }
    val restPose = moodPose(mood)

    if (live) {
        LaunchedEffect(mood) {
            animator.mood = mood
            while (true) {
                val now = withFrameNanos { it / 1_000_000 }
                val next = animator.step(now)
                pose.value = animator.pose
                if (next - now > IDLE_SLEEP_MS) delay(next - now - IDLE_SLEEP_MS)
            }
        }
    }

    val semantic = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Canvas(modifier.size(size).then(semantic)) {
        drawIntoCanvas { canvas ->
            CarvedAvatarRenderer.draw(
                canvas.nativeCanvas, assets, look, color, if (live) pose.value else restPose, this.size.minDimension,
            )
        }
    }
}

private const val IDLE_SLEEP_MS = 40L
