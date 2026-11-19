package com.openmausbot.companion.character

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CROSS_FADE_MS = 180
private val CrossFadeEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** The animated Seira character of a [look]. The face ink comes from `look.faceInk`. */
@Composable
internal fun SeiraCharacter(
    look: SeiraLook,
    size: Dp,
    mood: CharacterMood = CharacterMood.IDLE,
    sway: Boolean = false,
    talkLevel: () -> Float = { 0f },
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    SeiraCharacter(
        hair = look.hair,
        color = look.color,
        accessories = look.accessories,
        faceInk = look.faceInk,
        size = size,
        mood = mood,
        sway = sway,
        talkLevel = talkLevel,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/**
 * The animated Seira character. A change of hair, colour, accessories, face ink or mood cross-fades in 180 ms,
 * or at once when the system removes animations. [talkLevel] is read while drawing, so it never recomposes.
 */
@Composable
internal fun SeiraCharacter(
    hair: String?,
    color: String,
    accessories: List<String>,
    faceInk: String?,
    size: Dp,
    mood: CharacterMood = CharacterMood.IDLE,
    sway: Boolean = false,
    talkLevel: () -> Float = { 0f },
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val assets = remember(context) { SeiraAssets.forContext(context) }
    val renderer = remember(assets) { SeiraCharacterRenderer(assets) }
    val outgoing = remember(assets) { SeiraCharacterRenderer(assets) }
    val target = FrameLook.of(assets, hair, color, accessories, faceInk, mood)

    var shown by remember { mutableStateOf<FrameLook?>(null) }
    var previous by remember { mutableStateOf<FrameLook?>(null) }
    val fade = remember { Animatable(1f) }
    val clock = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(target, reduced) {
        if (target == shown) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            assets.preload(target.hair, SeiraRecolor.tintFor(target.hair, target.color), target.accessories)
        }
        if (reduced || shown == null) {
            previous = null
            shown = target
            fade.snapTo(1f)
        } else {
            previous = shown
            shown = target
            fade.snapTo(0f)
            fade.animateTo(1f, tween(CROSS_FADE_MS, easing = CrossFadeEasing))
            previous = null
        }
    }

    LaunchedEffect(reduced) {
        if (reduced) {
            clock.floatValue = 0f
            return@LaunchedEffect
        }
        var start = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                if (start == Long.MIN_VALUE) start = now
                clock.floatValue = (now - start) / 1_000_000_000f
            }
        }
    }

    val semantic = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Spacer(
        modifier
            .size(size)
            .then(semantic)
            .drawBehind {
                val now = shown ?: return@drawBehind
                val w = this.size.width.roundToInt()
                val h = this.size.height.roundToInt()
                val seconds = clock.floatValue
                val swayAngle = SeiraCharacterMotion.swayDegrees(seconds, sway, reduced)
                val blink = SeiraCharacterMotion.blinkAmount(seconds, reduced)
                val talk = talkLevel()
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val before = previous
                    val k = fade.value
                    if (before != null && k < 1f) {
                        outgoing.draw(native, before, w, h, 0f, swayAngle, blink, talk)
                        val layer = native.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (k * 255f).roundToInt())
                        renderer.draw(native, now, w, h, 0f, swayAngle, blink, talk)
                        native.restoreToCount(layer)
                    } else {
                        renderer.draw(native, now, w, h, 0f, swayAngle, blink, talk)
                    }
                }
            },
    )
}
