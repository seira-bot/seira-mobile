package com.openmausbot.companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openmausbot.companion.R
import com.openmausbot.companion.audio.LiveCallPhase
import com.openmausbot.companion.audio.MicrophoneAccess
import com.openmausbot.companion.character.SeiraCharacter
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.InkPillButton
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.identityPlate
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween
import kotlinx.coroutines.delay

/** What the call screen shows the person, chosen only from data the call model holds. */
internal enum class CallVoiceStatus { CONNECTING, SPEAKING, LISTENING, MUTED, ENDING, ENDED }

internal object LiveCallScreenRules {
    /** The voice counts as speaking this long after its last words arrived. */
    const val SPEAKING_WINDOW_MS = 1_500L
    const val MUTED_RING_SCALE = 0.94f

    private val LIVE_BARS = listOf(6, 12, 16, 10, 6)
    private val MUTED_BARS = List(5) { 4 }

    fun ringScale(muted: Boolean): Float = if (muted) MUTED_RING_SCALE else 1f

    fun barHeights(muted: Boolean): List<Int> = if (muted) MUTED_BARS else LIVE_BARS

    fun status(model: LiveCallBarModel.Local, speaking: Boolean): CallVoiceStatus = when (model.phase) {
        LiveCallPhase.STARTING -> CallVoiceStatus.CONNECTING
        LiveCallPhase.ENDING -> CallVoiceStatus.ENDING
        LiveCallPhase.ENDED, LiveCallPhase.IDLE -> CallVoiceStatus.ENDED
        LiveCallPhase.LIVE -> when {
            model.muted -> CallVoiceStatus.MUTED
            speaking -> CallVoiceStatus.SPEAKING
            else -> CallVoiceStatus.LISTENING
        }
    }

    /** Whether a person's own words are the newest: the voice is not speaking then. */
    fun voiceIsSpeaking(caption: String, heard: String): Boolean = heard.isEmpty() && caption.isNotEmpty()

    /** True opens the screen, false minimises it, null leaves it as it is. */
    fun expansionFor(previous: LiveCallPhase, current: LiveCallPhase): Boolean? = when {
        current == previous -> null
        current == LiveCallPhase.STARTING -> true
        current == LiveCallPhase.ENDING || current == LiveCallPhase.ENDED || current == LiveCallPhase.IDLE -> false
        else -> null
    }

    fun showsCaptionCard(captionsOn: Boolean, model: LiveCallBarModel.Local): Boolean =
        captionsOn && model.phase == LiveCallPhase.LIVE && (model.heard.isNotEmpty() || model.caption.isNotEmpty())
}

internal val RingScaleKey = SemanticsPropertyKey<Float>("RingScale")
internal var SemanticsPropertyReceiver.ringScale by RingScaleKey

internal class LiveCallScreenActions(
    val onBack: () -> Unit,
    val onMute: (Boolean) -> Unit,
    val onSpeaker: (Boolean) -> Unit,
    val onHangUp: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
)

/** Draws the full call screen over the paired screens while it is expanded; also opens and closes it with the call. */
@Composable
fun LiveCallScreenHost() {
    val environment = LocalCompanion.current
    val callUi = environment.callUi
    val liveCalls = environment.liveCalls
    val local by liveCalls.state.collectAsState()
    val expanded by callUi.expanded.collectAsState()
    val fleet by environment.session.state.collectAsState()
    val captured = remember { mutableStateOf(local.phase) }

    LaunchedEffect(local.phase) {
        LiveCallScreenRules.expansionFor(captured.value, local.phase)?.let { open ->
            if (open) callUi.expand() else callUi.minimize()
        }
        captured.value = local.phase
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, liveCalls, callUi) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && liveCalls.takeBackgroundReturn()) callUi.expand()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (!expanded) return
    val threadId = local.threadId ?: return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val ticking = local.phase == LiveCallPhase.LIVE
    LaunchedEffect(ticking) {
        while (ticking) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val model = LiveCallRules.barModel(local, null, threadId, local.botName, now) as? LiveCallBarModel.Local ?: return
    val bot = local.botId?.let { fleet.bot(it) }
    val speaking = rememberBotSpeaking(model.caption, model.heard)
    BackHandler { callUi.minimize() }
    LiveCallScreen(
        model = model,
        bot = bot,
        botName = bot?.name ?: local.botName,
        speaking = speaking,
        actions = LiveCallScreenActions(
            onBack = callUi::minimize,
            onMute = liveCalls::setMuted,
            onSpeaker = liveCalls::setSpeaker,
            onHangUp = liveCalls::hangUp,
            onRetry = { liveCalls.retry(MicrophoneAccess { environment.mic.ensure(it) }) },
            onClose = {
                liveCalls.dismiss()
                callUi.minimize()
            },
        ),
    )
}

@Composable
internal fun rememberBotSpeaking(caption: String, heard: String): Boolean {
    var speaking by remember { mutableStateOf(false) }
    LaunchedEffect(caption, heard) {
        if (!LiveCallScreenRules.voiceIsSpeaking(caption, heard)) {
            speaking = false
            return@LaunchedEffect
        }
        speaking = true
        delay(LiveCallScreenRules.SPEAKING_WINDOW_MS)
        speaking = false
    }
    return speaking
}

@Composable
internal fun LiveCallScreen(
    model: LiveCallBarModel.Local,
    bot: Bot?,
    botName: String,
    speaking: Boolean,
    actions: LiveCallScreenActions,
    modifier: Modifier = Modifier,
    animateAvatar: Boolean = true,
) {
    val colors = Seira.colors
    val call = colors.call
    var captionsOn by rememberSaveable { mutableStateOf(true) }
    val over = model.phase == LiveCallPhase.ENDED || model.phase == LiveCallPhase.ENDING
    Box(modifier = modifier.fillMaxSize().background(call.app)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val available = maxHeight
            val ring = minOf(300.dp, available * 0.36f)
            Column(modifier = Modifier.fillMaxSize()) {
                TopRow(model, actions.onBack)
                Spacer(Modifier.height(minOf(54.dp, available * 0.05f)))
                Hero(model, bot, botName, speaking, ring, animateAvatar)
            }
            if (!over) {
                CaptionCard(
                    visible = LiveCallScreenRules.showsCaptionCard(captionsOn, model),
                    model = model,
                    bot = bot,
                    botName = botName,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 176.dp, start = 16.dp, end = 16.dp),
                )
                Controls(
                    model = model,
                    captionsOn = captionsOn,
                    onToggleCaptions = { captionsOn = !captionsOn },
                    actions = actions,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
                )
            } else {
                EndedActions(
                    showRetry = model.phase == LiveCallPhase.ENDED && model.canRetry,
                    showClose = model.phase == LiveCallPhase.ENDED,
                    actions = actions,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
                )
            }
        }
    }
}

@Composable
private fun TopRow(model: LiveCallBarModel.Local, onBack: () -> Unit) {
    val colors = Seira.colors
    val back = stringResource(R.string.call_screen_back_to_chat)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = back, onClick = onBack)
                .semantics { contentDescription = back },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(44.dp).background(colors.tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(SeiraIcons.ChevronDown, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (model.phase == LiveCallPhase.LIVE) {
                Box(Modifier.size(6.dp).background(colors.success, CircleShape))
            }
            Text(stringResource(R.string.call_screen_live_call), style = SeiraType.callout, color = colors.ink2)
            model.clock?.let {
                Text(
                    it,
                    style = SeiraType.callout.copy(fontFeatureSettings = "tnum"),
                    color = colors.ink3,
                    modifier = Modifier.testTag("CallClock"),
                )
            }
        }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun Hero(model: LiveCallBarModel.Local, bot: Bot?, botName: String, speaking: Boolean, ring: Dp, animateAvatar: Boolean) {
    val colors = Seira.colors
    val identity = Color(identityArgb(bot))
    val muted = model.muted && model.phase == LiveCallPhase.LIVE
    val reduced = rememberReducedMotion()
    val target = LiveCallScreenRules.ringScale(muted)
    val outer by animateFloatAsState(target, seiraTween(SeiraMotion.SLOW, reduced))
    val middle by animateFloatAsState(target, seiraTween(260, reduced))
    val (a3, a2, a1) = if (colors.isDark) Triple(0.12f, 0.22f, 0.10f) else Triple(0.10f, 0.18f, 0.07f)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(ring)
                .testTag("CallRings")
                .semantics { ringScale = target },
            contentAlignment = Alignment.Center,
        ) {
            val inset30 = ring * 0.1f
            val inset60 = ring * 0.2f
            Box(
                Modifier.size(ring).graphicsLayer { scaleX = outer; scaleY = outer }
                    .border(BorderStroke(1.dp, identity.copy(alpha = a3)), CircleShape),
            )
            Box(
                Modifier.size(ring - inset30 * 2).graphicsLayer { scaleX = middle; scaleY = middle }
                    .border(BorderStroke(1.dp, identity.copy(alpha = a2)), CircleShape),
            )
            Box(Modifier.size(ring - inset60 * 2).background(identity.copy(alpha = a1), CircleShape))
            val talk = rememberUpdatedState(if (speaking) SPEAKING_TALK_LEVEL else 0f)
            SeiraCharacter(
                look = bot?.let(SeiraLook::from) ?: SeiraLook.SEIRA,
                size = minOf(156.dp, ring * 0.7f),
                sway = animateAvatar,
                talkLevel = { talk.value },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(botName, style = SeiraType.title2, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(6.dp))
        StatusLine(model, botName, speaking, muted)
    }
}

private const val SPEAKING_TALK_LEVEL = 0.6f

private fun identityArgb(bot: Bot?): Int =
    if (bot == null) BotPalette.FALLBACK else BotPalette.argb(bot.color)

@Composable
private fun StatusLine(model: LiveCallBarModel.Local, botName: String, speaking: Boolean, muted: Boolean) {
    val colors = Seira.colors
    val status = LiveCallScreenRules.status(model, speaking)
    val text = when (status) {
        CallVoiceStatus.SPEAKING -> stringResource(R.string.call_screen_speaking, botName)
        CallVoiceStatus.LISTENING -> stringResource(R.string.call_screen_listening)
        CallVoiceStatus.MUTED -> stringResource(R.string.call_screen_muted)
        CallVoiceStatus.CONNECTING, CallVoiceStatus.ENDING -> localizedMobileCopy(model.title)
        CallVoiceStatus.ENDED -> localizedMobileCopy(model.title)
    }
    val isError = status == CallVoiceStatus.ENDED
    Row(
        modifier = Modifier.padding(horizontal = 24.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == CallVoiceStatus.SPEAKING || status == CallVoiceStatus.LISTENING || status == CallVoiceStatus.MUTED) {
            VoiceBars(moving = status == CallVoiceStatus.SPEAKING, muted = muted)
        }
        Text(
            text = text,
            style = SeiraType.body.copy(lineHeight = 20.sp),
            color = if (isError) colors.call.danger else colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("CallStatus"),
        )
    }
}

@Composable
private fun VoiceBars(moving: Boolean, muted: Boolean) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val heights = LiveCallScreenRules.barHeights(muted)
    val swing = if (moving && !reduced) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        ).value
    } else {
        0f
    }
    Row(
        modifier = Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { index, base ->
            val wave = if (index % 2 == 0) swing else 1f - swing
            val target = if (muted) base.dp else (base + (16 - base) * wave * 0.5f).dp
            val height by animateDpAsState(target, seiraTween(SeiraMotion.BASE, reduced))
            Box(Modifier.width(3.dp).height(height).background(colors.ink2, CircleShape))
        }
    }
}

@Composable
private fun CaptionCard(visible: Boolean, model: LiveCallBarModel.Local, bot: Bot?, botName: String, modifier: Modifier) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val shift = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(seiraTween(SeiraMotion.BASE, reduced)) +
            slideInVertically(seiraTween<IntOffset>(240, reduced)) { shift },
        exit = fadeOut(seiraTween(SeiraMotion.BASE, reduced)) +
            slideOutVertically(seiraTween<IntOffset>(240, reduced)) { shift },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.tint, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .testTag("CallCaptionCard"),
        ) {
            if (model.heard.isNotEmpty()) {
                Text(stringResource(R.string.call_screen_you), style = SeiraType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = colors.ink3)
                Text(model.heard, style = SeiraType.body, color = colors.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).testTag("CallHeard"))
            }
            if (model.caption.isNotEmpty()) {
                Text(
                    botName,
                    style = SeiraType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = botNameColor(bot),
                    modifier = Modifier.padding(top = if (model.heard.isNotEmpty()) 12.dp else 0.dp),
                )
                Text(model.caption, style = SeiraType.body, color = colors.ink, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).testTag("CallCaption"))
            }
        }
    }
}

@Composable
private fun botNameColor(bot: Bot?): Color {
    val colors = Seira.colors
    return when (bot?.name?.lowercase()) {
        "seira" -> colors.seiraName
        "lumi" -> colors.lumiName
        else -> colors.ink2
    }
}

@Composable
private fun Controls(
    model: LiveCallBarModel.Local,
    captionsOn: Boolean,
    onToggleCaptions: () -> Unit,
    actions: LiveCallScreenActions,
    modifier: Modifier,
) {
    val muteLabel = stringResource(if (model.muted) R.string.call_screen_unmute else R.string.call_screen_mute)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        CallControl(
            icon = if (model.muted) SeiraIcons.MicOff else SeiraIcons.Mic,
            label = muteLabel,
            selected = model.muted,
            onClick = { actions.onMute(!model.muted) },
        )
        CallControl(
            icon = SeiraIcons.Volume2,
            label = stringResource(R.string.call_screen_speaker),
            selected = model.speaker,
            onClick = { actions.onSpeaker(!model.speaker) },
        )
        CallControl(
            icon = SeiraIcons.Captions,
            label = stringResource(R.string.call_screen_captions),
            selected = captionsOn,
            onClick = onToggleCaptions,
        )
        CallControl(
            icon = SeiraIcons.PhoneOff,
            label = stringResource(R.string.call_screen_end),
            description = stringResource(R.string.call_screen_end_call),
            danger = true,
            onClick = actions.onHangUp,
        )
    }
}

@Composable
private fun CallControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    description: String? = null,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val fill by animateColorAsState(
        when {
            danger -> colors.call.danger
            selected -> colors.primary
            else -> colors.call.control
        },
        seiraTween(SeiraMotion.FAST, reduced),
    )
    val ink by animateColorAsState(
        when {
            danger -> Color.White
            selected -> colors.onPrimary
            else -> colors.ink
        },
        seiraTween(SeiraMotion.FAST, reduced),
    )
    val semanticsModifier = if (description != null) {
        Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick { onClick(); true }
        }
    } else {
        Modifier.semantics { this.selected = selected }
    }
    Column(
        modifier = modifier
            .width(64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(semanticsModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(64.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        }
        Text(label, style = SeiraType.caption, color = colors.ink3, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true))
    }
}

@Composable
private fun EndedActions(showRetry: Boolean, showClose: Boolean, actions: LiveCallScreenActions, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showRetry) InkPillButton(text = stringResource(R.string.call_screen_try_again), onClick = actions.onRetry)
        if (showClose) {
            val close = stringResource(R.string.call_screen_close)
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Seira.colors.call.control, CircleShape)
                    .clickable(role = Role.Button, onClick = actions.onClose)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(close, style = SeiraType.headline, color = Seira.colors.ink)
            }
        }
    }
}
