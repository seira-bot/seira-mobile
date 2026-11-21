package com.openmausbot.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.AiUsageResult
import com.openmausbot.companion.core.AiUsageRules
import com.openmausbot.companion.core.ConversationUsageLine
import com.openmausbot.companion.core.EngineLimit
import com.openmausbot.companion.core.EngineLimitsReport
import com.openmausbot.companion.core.EngineUsage
import com.openmausbot.companion.core.LimitWindowKind
import com.openmausbot.companion.core.TaskUsage
import com.openmausbot.companion.core.UsageRow
import com.openmausbot.companion.core.UsageTone
import com.openmausbot.companion.core.limitLine
import com.openmausbot.companion.core.limitResetTime
import com.openmausbot.companion.core.usageRows
import com.openmausbot.companion.core.usageTone
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraCard
import com.openmausbot.companion.ui.theme.SeiraType
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiUsageScreen(onBack: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<EngineLimitsReport?>(null) }
    var failed by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }

    suspend fun load(refresh: Boolean) {
        checking = true
        when (val result = session.aiUsage(refresh)) {
            is AiUsageResult.Report -> {
                report = result.report
                failed = false
            }
            AiUsageResult.NotAvailable -> onBack()
            AiUsageResult.Failed -> failed = true
        }
        checking = false
    }

    LaunchedEffect(Unit) { load(refresh = false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderBackButton(onBack)
            Text(
                stringResource(R.string.ai_usage_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { load(refresh = true) } }, enabled = !checking) {
                Text(stringResource(if (checking) R.string.ai_usage_checking else R.string.ai_usage_check_again))
            }
        }
        HorizontalDivider()
        PullToRefreshBox(
            isRefreshing = checking && report != null,
            onRefresh = { scope.launch { load(refresh = true) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val current = report
                val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
                val cards = remember(current) { current?.let { EngineUsage.reporting(it.engines) }.orEmpty() }
                cards.forEach { AiUsageCardView(it, locale) }
                when {
                    failed -> Text(stringResource(R.string.ai_usage_load_failed), color = Seira.colors.ink2)
                    current == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                    cards.isEmpty() -> Text(stringResource(R.string.ai_usage_empty), color = Seira.colors.ink2)
                }
            }
        }
    }
}

@Composable
internal fun AiUsageCardView(
    limit: EngineLimit,
    locale: Locale,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = Seira.colors
    val tone = usageTone(limit)
    val ringColor = when (tone) {
        UsageTone.FINE -> colors.accent
        UsageTone.NEAR -> colors.warning
        UsageTone.OUT -> colors.danger
    }
    val name = limit.name.ifBlank { limit.engine.replaceFirstChar { it.uppercase() } }
    val percent = if (limit.blocked) 100 else (limit.usedPercent ?: 0.0).roundToInt().coerceIn(0, 100)
    val paused = limit.blocked || limit.held == true
    SeiraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val ringDescription = stringResource(R.string.ai_usage_ring_description, name, percent)
                UsageRing(
                    percent = percent,
                    color = ringColor,
                    modifier = Modifier.semantics { contentDescription = ringDescription },
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = SeiraType.headline, color = colors.ink)
                    if (paused) {
                        val until = limitLine(limit, now).resetsAt
                        val chip = until?.let {
                            stringResource(R.string.ai_usage_paused_until, limitResetTime(it, now, locale, zone))
                        } ?: stringResource(R.string.ai_usage_paused)
                        Text(
                            chip,
                            style = SeiraType.caption,
                            color = colors.warningText,
                            modifier = Modifier
                                .background(colors.warningSoft, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    } else if (tone != UsageTone.FINE) {
                        Text(stringResource(R.string.ai_usage_near_limit), style = SeiraType.caption, color = colors.warningText)
                    }
                }
            }
            usageRows(limit).forEach { UsageRowView(it, now, zone, locale) }
        }
    }
}

@Composable
private fun UsageRowView(row: UsageRow, now: Instant, zone: ZoneId, locale: Locale) {
    val label = stringResource(
        when (EngineUsage.windowKind(row.window)) {
            LimitWindowKind.FIVE_HOUR -> R.string.ai_usage_window_five_hour
            LimitWindowKind.WEEKLY -> R.string.ai_usage_window_weekly
            LimitWindowKind.OTHER -> R.string.ai_usage_window_other
        },
    )
    val reset = row.resetsAt?.takeIf { it > now }
    val detail = reset?.let {
        stringResource(R.string.ai_usage_window_row, row.left, limitResetTime(it, now, locale, zone))
    } ?: stringResource(R.string.ai_usage_window_row_no_reset, row.left)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = SeiraType.body, color = Seira.colors.ink)
        Text(detail, style = SeiraType.callout, color = if (row.tone == UsageTone.FINE) Seira.colors.ink2 else Seira.colors.warningText)
    }
}

@Composable
private fun UsageRing(percent: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val track = Seira.colors.hairline
    Canvas(modifier = modifier.size(44.dp)) {
        val width = 6.dp.toPx()
        val inset = width / 2
        val arc = Size(size.width - width, size.height - width)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
        drawArc(color, -90f, 360f * percent / 100f, false, Offset(inset, inset), arc, style = Stroke(width, cap = StrokeCap.Round))
    }
}

@Composable
internal fun ConversationUsageSection(usage: TaskUsage?) {
    val line = AiUsageRules.conversationUsage(usage) ?: return
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    FormSection(header = null) {
        Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(conversationUsageText(line, locale), fontSize = 15.sp)
            val contextTokens = line.contextTokens
            val contextWindow = line.contextWindow
            if (contextTokens != null && contextWindow != null) {
                Text(
                    stringResource(
                        R.string.ai_usage_conversation_context,
                        AiUsageRules.formatCount(contextTokens, locale),
                        AiUsageRules.formatCount(contextWindow, locale),
                    ),
                    fontSize = 13.sp,
                    color = secondaryTint,
                )
            }
        }
    }
}

@Composable
private fun conversationUsageText(line: ConversationUsageLine, locale: Locale): String {
    val count = AiUsageRules.formatCount(line.tokens, locale)
    val base = if (line.turns == 1) {
        stringResource(R.string.ai_usage_conversation_tokens_one_turn, count)
    } else {
        stringResource(R.string.ai_usage_conversation_tokens, count, line.turns)
    }
    val cost = line.costUsd ?: return base
    return stringResource(R.string.ai_usage_conversation_cost, base, AiUsageRules.formatCost(cost, locale))
}
