package com.openmausbot.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.theme.GlassCircleButton
import com.openmausbot.companion.ui.theme.InkPillButton
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMark
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraShadow
import com.openmausbot.companion.ui.theme.seiraTween

/** What the torch button says and shows. It exists only on a phone that has a flash unit. */
internal data class TorchControl(val labelRes: Int, val on: Boolean)

internal enum class PairPhase { POINT, FOUND, CONNECTING, CONNECTED }

internal object PairDesign {
    fun torch(hasFlashUnit: Boolean, on: Boolean): TorchControl? =
        if (hasFlashUnit) {
            TorchControl(if (on) R.string.pair_flash_off else R.string.pair_flash_on, on)
        } else {
            null
        }

    fun phase(connected: Boolean, pairing: Boolean, hasComputer: Boolean): PairPhase = when {
        connected -> PairPhase.CONNECTED
        pairing -> PairPhase.CONNECTING
        hasComputer -> PairPhase.FOUND
        else -> PairPhase.POINT
    }

    fun statusRes(phase: PairPhase): Int = when (phase) {
        PairPhase.POINT -> R.string.pair_status_point
        PairPhase.FOUND -> R.string.pair_status_found
        PairPhase.CONNECTING -> R.string.pair_status_connecting
        PairPhase.CONNECTED -> R.string.pair_connected_title
    }
}

private val ViewfinderSize = 270.dp
private val ViewfinderInk = Color(0xFF0C0C0E)
private val ViewfinderGlow = Color(0xFF2A2A30)
private val ViewfinderSuccess = Color(0xFF3CCF8E)

@Composable
internal fun PairFrame(
    phase: PairPhase,
    statusText: String,
    statusIsError: Boolean,
    torch: TorchControl?,
    onTorch: () -> Unit,
    onBack: () -> Unit,
    backEnabled: Boolean,
    viewfinder: (@Composable BoxScope.() -> Unit)?,
    card: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = Seira.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.app)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            GlassCircleButton(
                icon = SeiraIcons.ChevronLeft,
                contentDescription = stringResource(R.string.mobile_back_b52b36b7),
                onClick = onBack,
                enabled = backEnabled,
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SeiraMark(36.dp)
            Text(
                stringResource(R.string.pair_title),
                style = SeiraType.title2.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                stringResource(R.string.pair_body),
                style = SeiraType.body.copy(fontSize = 15.sp),
                color = colors.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        PairViewfinder(phase = phase, torch = torch, onTorch = onTorch, content = viewfinder)
        Spacer(Modifier.height(16.dp))
        PairStatusLine(statusText, statusIsError)
        Spacer(Modifier.height(20.dp))
        PairCard(card)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = below,
        )
    }
}

@Composable
internal fun PairViewfinder(
    phase: PairPhase,
    torch: TorchControl?,
    onTorch: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val connected = phase == PairPhase.CONNECTED
    val frameScale by animateFloatAsState(
        if (connected) 0.9f else 1f,
        seiraTween(SeiraMotion.SLOW, reduced),
    )
    val frameColor by animateColorAsState(
        if (connected) ViewfinderSuccess else Color.White,
        seiraTween(SeiraMotion.BASE, reduced),
    )
    val badge by animateFloatAsState(
        if (connected) 1f else 0f,
        seiraTween(SeiraMotion.SLOW, reduced),
    )
    val label = stringResource(R.string.pair_viewfinder)
    Box(
        modifier = modifier
            .size(ViewfinderSize)
            .clip(RoundedCornerShape(34.dp))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(0f to ViewfinderGlow, 0.72f to ViewfinderInk),
                        center = Offset(size.width * 0.5f, size.height * 0.3f),
                        radius = size.width * 0.9f,
                    ),
                )
            }
            .semantics { contentDescription = label },
    ) {
        content?.invoke(this)
        Canvas(Modifier.matchParentSize().padding(18.dp)) {
            val length = 40.dp.toPx()
            val radius = 18.dp.toPx()
            val width = 3.dp.toPx()
            val half = width / 2f
            val corner = Path().apply {
                moveTo(half, length)
                lineTo(half, radius)
                arcTo(Rect(half, half, half + 2f * radius, half + 2f * radius), 180f, 90f, false)
                lineTo(length, half)
            }
            scale(frameScale, center) {
                for (index in 0 until 4) {
                    rotate(index * 90f, center) {
                        drawPath(corner, frameColor, style = Stroke(width = width, cap = StrokeCap.Round))
                    }
                }
            }
        }
        if (badge > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .graphicsLayer {
                        alpha = badge
                        scaleX = 0.6f + 0.4f * badge
                        scaleY = 0.6f + 0.4f * badge
                    }
                    .background(ViewfinderSuccess, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SeiraIcons.Check, contentDescription = null, tint = ViewfinderInk, modifier = Modifier.size(34.dp))
            }
        }
        if (torch != null) {
            val label = stringResource(torch.labelRes)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .toggleable(value = torch.on, role = Role.Switch, onValueChange = { onTorch() })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (torch.on) Color(0xFFF2F2F3) else Color.White.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        SeiraIcons.Flashlight,
                        contentDescription = null,
                        tint = if (torch.on) Color(0xFF111113) else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.PairViewfinderMessage(title: String, body: String) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = SeiraType.headline, color = Color.White, textAlign = TextAlign.Center)
        Text(body, style = SeiraType.callout, color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PairStatusLine(text: String, isError: Boolean) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(if (isError) colors.danger else colors.success, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = SeiraType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = if (isError) colors.danger else colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
internal fun PairCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .seiraShadow(shape)
            .background(Seira.colors.sheet, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun PairComputerRow(name: String, detail: String?, connected: Boolean = false) {
    val colors = Seira.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(if (connected) colors.successSoft else colors.tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (connected) SeiraIcons.Check else SeiraIcons.Laptop,
                contentDescription = null,
                tint = if (connected) colors.success else colors.ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = SeiraType.title3, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = SeiraType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = colors.ink2)
            }
        }
    }
}

@Composable
internal fun PairPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    InkPillButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        height = 52.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}

@Composable
internal fun PairTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = SeiraType.bodyMedium.copy(fontSize = 15.sp),
            color = if (enabled) Seira.colors.ink2 else Seira.colors.ink3,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun PairFoundCard(
    name: String,
    detail: String?,
    connecting: Boolean,
    onConnect: () -> Unit,
    onEnterCode: () -> Unit,
) {
    Column {
        PairComputerRow(name, detail)
        PairPrimaryButton(
            text = stringResource(if (connecting) R.string.pair_status_connecting else R.string.mobile_connect_b65463cb),
            onClick = onConnect,
            enabled = !connecting,
        )
        PairTextButton(stringResource(R.string.pair_enter_code), onEnterCode)
    }
}

@Composable
internal fun PairConnectedCard(
    onDone: () -> Unit,
    onPairAnother: (() -> Unit)?,
) {
    Column {
        PairComputerRow(
            name = stringResource(R.string.pair_connected_title),
            detail = stringResource(R.string.pair_connected_body),
            connected = true,
        )
        PairPrimaryButton(stringResource(R.string.pair_done), onDone)
        if (onPairAnother != null) {
            PairTextButton(stringResource(R.string.settings_pair_another), onPairAnother)
        }
    }
}

@Composable
internal fun PairSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = SeiraType.callout.copy(fontWeight = FontWeight.Medium),
            color = Seira.colors.ink3,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Seira.colors.tint, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
