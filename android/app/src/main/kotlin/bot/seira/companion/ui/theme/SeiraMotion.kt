package bot.seira.companion.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

object SeiraMotion {
    val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    const val FAST = 120
    const val BASE = 200
    const val SHEET = 280
    const val SLOW = 300
}

internal fun isMotionReduced(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** True while the system animator duration scale is 0, the "remove animations" setting. */
@Composable
fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    val state = remember {
        mutableStateOf(
            isMotionReduced(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)),
        )
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = isMotionReduced(
                    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
                )
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        observer.onChange(false)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state.value
}

fun <T> seiraTween(durationMillis: Int, reduced: Boolean): FiniteAnimationSpec<T> =
    if (reduced) snap() else tween(durationMillis = durationMillis, easing = SeiraMotion.Ease)

@Composable
fun <T> seiraTween(durationMillis: Int): FiniteAnimationSpec<T> =
    seiraTween(durationMillis, rememberReducedMotion())
