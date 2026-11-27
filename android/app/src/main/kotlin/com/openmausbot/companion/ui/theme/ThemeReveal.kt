package com.openmausbot.companion.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlin.math.max

/** Holds the picture of the old theme and grows a circle that uncovers the new one. */
@Stable
class ThemeRevealState {
    private var layer: GraphicsLayer? = null
    private var hostOrigin: Offset = Offset.Zero
    private var hostSize: IntSize = IntSize.Zero
    private var overlay by mutableStateOf<ImageBitmap?>(null)
    private var radius by mutableFloatStateOf(0f)
    private var center by mutableStateOf(Offset.Zero)

    internal fun attach(layer: GraphicsLayer) {
        this.layer = layer
    }

    internal fun place(origin: Offset, size: IntSize) {
        hostOrigin = origin
        hostSize = size
    }

    /**
     * Runs [apply] and uncovers its result with a circle that grows from
     * [origin], given in window coordinates. With reduced motion, or without a
     * host, only [apply] runs.
     */
    suspend fun reveal(origin: Offset, reducedMotion: Boolean, apply: () -> Unit) {
        val layer = layer
        if (reducedMotion || layer == null || hostSize.width == 0 || hostSize.height == 0) {
            apply()
            return
        }
        val old = try {
            layer.toImageBitmap()
        } catch (_: Exception) {
            apply()
            return
        }
        val local = origin - hostOrigin
        val reach = farthestCorner(local, hostSize)
        center = local
        radius = 0f
        overlay = old
        try {
            apply()
            withFrameNanos { }
            Animatable(0f).animateTo(reach, tween(REVEAL_MILLIS, easing = SeiraMotion.Ease)) { radius = value }
        } finally {
            overlay = null
        }
    }

    internal fun DrawScope.drawOverlay() {
        val bitmap = overlay ?: return
        val circle = Path().apply { addOval(Rect(center, radius)) }
        clipPath(circle, ClipOp.Difference) {
            drawImage(bitmap)
        }
    }

    companion object {
        const val REVEAL_MILLIS = 560

        internal fun farthestCorner(from: Offset, size: IntSize): Float {
            val dx = max(from.x, size.width - from.x)
            val dy = max(from.y, size.height - from.y)
            return hypot(dx, dy)
        }
    }
}

val LocalThemeReveal = compositionLocalOf { ThemeRevealState() }

/** Draws through a layer, so the old theme can be kept while a circle uncovers the new one. */
@Composable
fun Modifier.themeRevealSurface(state: ThemeRevealState): Modifier {
    val layer = rememberGraphicsLayer()
    state.attach(layer)
    return this
        .onGloballyPositioned { state.place(it.positionInWindow(), it.size) }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
            with(state) { drawOverlay() }
        }
}
