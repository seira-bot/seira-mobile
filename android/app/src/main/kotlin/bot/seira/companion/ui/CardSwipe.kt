package bot.seira.companion.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.R
import bot.seira.companion.core.CardPresentation
import bot.seira.companion.core.OptionCard
import bot.seira.companion.core.presentation
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraType
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.launch

/** The two sides of a swipe on an approval card. A swipe asks; it never answers. */
internal enum class SwipeSide { ALLOW, DENY }

/**
 * The rules of the swipe on an approval card (seira-bot/seira#602).
 *
 * Security invariant: a swipe never sends an answer. A drag past the threshold
 * only asks for the confirm row; the answer goes out from "Confirm", through the
 * same path as the buttons, so a stray thumb cannot grant a permission.
 */
internal object CardSwipe {
    /** Part of the card width a release must pass to count as a decision. */
    const val THRESHOLD_FRACTION = 0.35f

    /** A release at this speed counts as a decision, once the drag is past [MIN_FLING_TRAVEL_DP]. */
    const val FLING_SPEED_DP_PER_SECOND = 1_000f

    const val MIN_FLING_TRAVEL_DP = 32f

    /** A drag that starts this close to the window edge is left to the system back gesture. */
    const val EDGE_GUARD_DP = 24f

    private val ALLOW_LABELS = setOf("allow", "allow once", "approve", "yes")
    private val DENY_LABELS = setOf("deny", "no", "reject", "decline")

    /**
     * Only a card that grants one request can be swiped: a permission, a
     * "send on your behalf" or a teammate ask. Proposals, skill reviews,
     * questions and team memory keep their buttons, because they need a full read.
     */
    fun isSwipeable(card: OptionCard): Boolean =
        card.isPending &&
            card.presentation != CardPresentation.STANDARD &&
            card.skillRequest == null &&
            card.questionRequest == null &&
            card.quotaSwitch == null &&
            choiceFor(card, SwipeSide.ALLOW) != null &&
            choiceFor(card, SwipeSide.DENY) != null

    /**
     * The card's own option for a side, never a string made up here. Narrower
     * than the button set on purpose: "Always allow" and the like are not a
     * swipe.
     */
    fun choiceFor(card: OptionCard, side: SwipeSide): String? {
        val labels = if (side == SwipeSide.ALLOW) ALLOW_LABELS else DENY_LABELS
        return card.options.firstOrNull { it.trim().lowercase() in labels }
    }

    /** What a release means: the side that was decided, or null for a spring back. */
    fun verdict(travelPx: Float, widthPx: Float, velocityPxPerSecond: Float, density: Float): SwipeSide? {
        if (widthPx <= 0f || travelPx == 0f) return null
        val side = if (travelPx > 0f) SwipeSide.ALLOW else SwipeSide.DENY
        if (abs(travelPx) >= widthPx * THRESHOLD_FRACTION) return side
        val flungTheSameWay = velocityPxPerSecond * travelPx > 0f &&
            abs(velocityPxPerSecond) >= FLING_SPEED_DP_PER_SECOND * density
        if (flungTheSameWay && abs(travelPx) >= MIN_FLING_TRAVEL_DP * density) return side
        return null
    }

    /** How strong the overlay is: 0 at rest, 1 at the threshold. */
    fun progress(travelPx: Float, widthPx: Float): Float =
        if (widthPx <= 0f) 0f else min(1f, abs(travelPx) / (widthPx * THRESHOLD_FRACTION))

    /** Whether a drag that began at [startXInWindowPx] belongs to the system back gesture. */
    fun startsInSystemEdge(startXInWindowPx: Float, windowWidthPx: Float, leftInsetPx: Float, rightInsetPx: Float, guardPx: Float): Boolean =
        startXInWindowPx < maxOf(leftInsetPx, guardPx) || startXInWindowPx > windowWidthPx - maxOf(rightInsetPx, guardPx)
}

internal const val CARD_SWIPE_FRAME_TAG = "card-swipe-frame"

/**
 * Wraps one approval card so it follows a horizontal drag, turns a little, and
 * shows what a release would ask for. Vertical drags go to the chat scroll;
 * a drag that starts in the system gesture edge is ignored. A release past the
 * threshold calls [onDecision] and the card returns to rest; the caller shows
 * the confirm step.
 */
@Composable
internal fun SwipeableCardFrame(
    enabled: Boolean,
    shape: Shape,
    onThreshold: () -> Unit,
    onDecision: (SwipeSide) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val layoutDirection = LocalLayoutDirection.current
    val gestureInsets = WindowInsets.systemGestures
    val scope = rememberCoroutineScope()
    var travel by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var leftInWindow by remember { mutableFloatStateOf(0f) }
    val latestOnThreshold = rememberUpdatedState(onThreshold)
    val latestOnDecision = rememberUpdatedState(onDecision)
    var pastThreshold by remember { mutableStateOf(false) }
    val allowLabel = stringResource(R.string.mobile_card_swipe_allow)
    val denyLabel = stringResource(R.string.mobile_card_swipe_deny)

    val swipeModifier = if (enabled) {
        Modifier
            .testTag(CARD_SWIPE_FRAME_TAG)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(allowLabel) { latestOnDecision.value(SwipeSide.ALLOW); true },
                    CustomAccessibilityAction(denyLabel) { latestOnDecision.value(SwipeSide.DENY); true },
                )
            }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var ignored = false
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        tracker.resetTracking()
                        pastThreshold = false
                        ignored = CardSwipe.startsInSystemEdge(
                            startXInWindowPx = leftInWindow + start.x,
                            windowWidthPx = view.width.toFloat().takeIf { it > 0f } ?: Float.MAX_VALUE,
                            leftInsetPx = gestureInsets.getLeft(density, layoutDirection).toFloat(),
                            rightInsetPx = gestureInsets.getRight(density, layoutDirection).toFloat(),
                            guardPx = CardSwipe.EDGE_GUARD_DP * density.density,
                        )
                    },
                    onHorizontalDrag = { change, amount ->
                        if (ignored) return@detectHorizontalDragGestures
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        travel += amount
                        val past = abs(travel) >= widthPx * CardSwipe.THRESHOLD_FRACTION
                        if (past && !pastThreshold) latestOnThreshold.value()
                        pastThreshold = past
                    },
                    onDragEnd = {
                        val decided = if (ignored) null else CardSwipe.verdict(
                            travelPx = travel,
                            widthPx = widthPx,
                            velocityPxPerSecond = tracker.calculateVelocity().x,
                            density = density.density,
                        )
                        pastThreshold = false
                        scope.launch { springBack(travel) { travel = it } }
                        decided?.let { latestOnDecision.value(it) }
                    },
                    onDragCancel = {
                        pastThreshold = false
                        scope.launch { springBack(travel) { travel = it } }
                    },
                )
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .onGloballyPositioned {
                widthPx = it.size.width.toFloat()
                leftInWindow = it.positionInWindow().x
            }
            .then(swipeModifier),
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = travel
                rotationZ = if (widthPx > 0f) travel / widthPx * MAX_TILT_DEGREES else 0f
            },
        ) {
            content()
            SwipeOverlay(travel, widthPx, shape)
        }
    }
}

private const val MAX_TILT_DEGREES = 6f

private suspend fun springBack(from: Float, set: (Float) -> Unit) {
    animate(from, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { value, _ -> set(value) }
}

@Composable
private fun BoxScope.SwipeOverlay(travelPx: Float, widthPx: Float, shape: Shape) {
    val progress = CardSwipe.progress(travelPx, widthPx)
    if (progress <= 0f) return
    val allowing = travelPx > 0f
    val colors = Seira.colors
    val tint = if (allowing) colors.success else colors.ink2
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape)
            .background(tint.copy(alpha = 0.2f * progress)),
    ) {
        Row(
            modifier = Modifier
                .align(if (allowing) Alignment.TopStart else Alignment.TopEnd)
                .padding(14.dp)
                .graphicsLayer { alpha = progress },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (allowing) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(if (allowing) R.string.mobile_card_swipe_allow else R.string.mobile_card_swipe_deny),
                fontSize = 14.sp,
                color = tint,
            )
        }
    }
}

/** The step between a swipe and the answer: nothing is sent until Confirm. */
@Composable
internal fun SwipeConfirmRow(side: SwipeSide, enabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (side == SwipeSide.ALLOW) colors.successSoft else colors.control, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(CARD_SWIPE_CONFIRM_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(if (side == SwipeSide.ALLOW) R.string.mobile_card_confirm_allow else R.string.mobile_card_confirm_deny),
            style = SeiraType.headline,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel, enabled = enabled) { Text(stringResource(R.string.mobile_card_cancel_swipe)) }
        TextButton(onClick = onConfirm, enabled = enabled, modifier = Modifier.testTag(CARD_SWIPE_CONFIRM_BUTTON_TAG)) {
            Text(stringResource(R.string.mobile_card_confirm))
        }
    }
}

internal const val CARD_SWIPE_CONFIRM_TAG = "card-swipe-confirm"
internal const val CARD_SWIPE_CONFIRM_BUTTON_TAG = "card-swipe-confirm-button"
