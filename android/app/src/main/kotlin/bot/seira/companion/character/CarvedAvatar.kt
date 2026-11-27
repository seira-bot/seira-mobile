package bot.seira.companion.character

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import bot.seira.companion.core.SeiraLook
import bot.seira.companion.ui.CharacterMood
import bot.seira.companion.ui.normalize
import bot.seira.companion.ui.theme.rememberReducedMotion
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

/**
 * The carved Seira avatar of a [look]. It stays still unless [animated] or [sway] is true and the system has animations on.
 * When [animated] is true and [mood] is idle, the mood of `look.expression` is used.
 */
@Composable
internal fun CarvedAvatar(
    look: SeiraLook,
    size: Dp,
    mood: CharacterMood = CharacterMood.IDLE,
    animated: Boolean = false,
    sway: Boolean = false,
    eyeColor: Color? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val shown = if (animated && mood == CharacterMood.IDLE) CharacterMood.normalize(look.expression) ?: mood else mood
    CarvedAvatar(
        hair = look.hair,
        color = look.color,
        accessories = look.accessories,
        size = size,
        mood = shown,
        animated = animated,
        sway = sway,
        eyeColor = eyeColor,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/** The carved Seira avatar. [eyeColor] replaces the eye colour of the body when set. It stays still unless [animated] or [sway] is true and the system has animations on. */
@Composable
internal fun CarvedAvatar(
    hair: String?,
    color: String,
    accessories: List<String>,
    size: Dp,
    mood: CharacterMood = CharacterMood.IDLE,
    animated: Boolean = false,
    sway: Boolean = false,
    eyeColor: Color? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val assets = LocalContext.current.assets
    val reduced = rememberReducedMotion()
    val live = animated && !reduced
    val swaying = sway && !reduced
    val look = remember(hair, accessories) { CarvedLook.of(hair, accessories) }
    val animator = remember { CarvedAnimator(mood) }
    val pose = remember { mutableStateOf(animator.pose) }
    val seconds = remember { mutableFloatStateOf(0f) }
    val restPose = moodPose(mood)

    if (live) {
        LaunchedEffect(mood) {
            animator.mood = mood
            if (motionIsOff()) return@LaunchedEffect
            while (true) {
                val now = withFrameNanos { it / 1_000_000 }
                val next = animator.step(now)
                pose.value = animator.pose
                if (next - now > IDLE_SLEEP_MS) delay(next - now - IDLE_SLEEP_MS)
            }
        }
    }
    if (swaying) {
        LaunchedEffect(Unit) {
            if (motionIsOff()) return@LaunchedEffect
            var start = Long.MIN_VALUE
            while (true) {
                withFrameNanos { now ->
                    if (start == Long.MIN_VALUE) start = now
                    seconds.floatValue = (now - start) / 1_000_000_000f
                }
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
        val angle = SeiraCharacterMotion.swayDegrees(seconds.floatValue, sway, reduced)
        drawIntoCanvas { canvas ->
            CarvedAvatarRenderer.draw(
                canvas.nativeCanvas, assets, look, color, if (live) pose.value else restPose, this.size.minDimension, angle,
                eyeColor?.toArgb(),
            )
        }
    }
}

/** Whether the frame loops must stay off because the composition runs with a motion scale of 0. */
internal suspend fun motionIsOff(): Boolean =
    (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) == 0f

private const val IDLE_SLEEP_MS = 40L
