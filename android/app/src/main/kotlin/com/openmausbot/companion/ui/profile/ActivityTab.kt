package com.openmausbot.companion.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ActivityRow
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ChatTarget
import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.RoutineRules
import com.openmausbot.companion.ui.TaskSheet
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TODAY_SHOWN = 8

@Composable
internal fun ActivityTab(bot: Bot, onOpenChat: (ChatTarget) -> Unit) {
    val session = LocalCompanion.current.session
    val colors = Seira.colors
    var rows by remember(bot.id) { mutableStateOf<List<ActivityRow>?>(null) }
    var failed by remember(bot.id) { mutableStateOf(false) }
    var attempt by remember(bot.id) { mutableStateOf(0) }
    var routines by remember(bot.id) { mutableStateOf<List<Routine>?>(null) }
    var showingThreads by remember(bot.id) { mutableStateOf(false) }
    var showAll by remember(bot.id) { mutableStateOf(false) }

    LaunchedEffect(bot.id, attempt) {
        failed = false
        val loaded = session.botActivity(bot, limit = 100)
        rows = loaded
        failed = loaded == null
    }
    LaunchedEffect(bot.id) {
        routines = session.loadRoutines().routines.filter { it.botId == bot.id }
    }

    ProfileGroup(title = stringResource(R.string.profile_activity_now)) {
        ProfileRow(
            title = if (bot.busy == true) {
                bot.activity?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_status_working)
            } else {
                stringResource(R.string.mobile_idle_cc1ebdd0)
            },
        )
    }

    val today = rows?.let { ProfileActivityRules.today(it, LocalDate.now()) }
    ProfileGroup(title = stringResource(R.string.profile_activity_today)) {
        when {
            failed -> ProfileNotice(stringResource(R.string.profile_activity_failed), retry = { attempt++ })
            today == null -> ProfileLoading()
            today.isEmpty() -> ProfileNotice(stringResource(R.string.profile_activity_today_empty))
            else -> {
                val shown = if (showAll) today else today.take(TODAY_SHOWN)
                shown.forEachIndexed { index, row ->
                    if (index > 0) CardDivider()
                    ActivityLine(row)
                }
                if (today.size > TODAY_SHOWN && !showAll) {
                    CardDivider()
                    ProfileRow(
                        title = stringResource(R.string.profile_activity_more),
                        titleColor = colors.accentText,
                        onClick = { showAll = true },
                    )
                }
            }
        }
    }

    ProfileGroup(title = null) {
        ProfileRow(
            title = stringResource(R.string.mobile_threads_bb12e8aa),
            subtitle = stringResource(R.string.profile_threads_subtitle),
            onClick = { showingThreads = true },
            trailing = {
                Icon(
                    SeiraIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.ink3,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }

    ProfileGroup(title = stringResource(R.string.mobile_routines_a445e5ad)) {
        val list = routines
        when {
            list == null -> ProfileLoading()
            list.isEmpty() -> ProfileNotice(stringResource(R.string.profile_routines_empty))
            else -> list.forEachIndexed { index, routine ->
                if (index > 0) CardDivider()
                ProfileRow(
                    title = routine.name,
                    subtitle = RoutineRules.scheduleSummary(routine.schedule),
                )
            }
        }
    }

    if (showingThreads) {
        TaskSheet(
            chat = Chat.BotChat(bot),
            onDismiss = { showingThreads = false },
            onSelectTask = {
                showingThreads = false
                onOpenChat(it)
            },
        )
    }
}

@Composable
private fun ActivityLine(row: ActivityRow) {
    val colors = Seira.colors
    val time = ProfileActivityRules.instantOf(row)?.let {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(it.atZone(ZoneId.systemDefault()))
    }.orEmpty()
    val (outcome, tint) = when (row.outcome) {
        "ran" -> stringResource(R.string.profile_outcome_ran) to colors.success
        "allowed" -> stringResource(R.string.profile_outcome_allowed) to colors.success
        "failed" -> stringResource(R.string.profile_outcome_failed) to colors.danger
        "denied" -> stringResource(R.string.profile_outcome_denied) to colors.danger
        "running" -> stringResource(R.string.profile_outcome_running) to colors.ink3
        "waiting" -> stringResource(R.string.profile_outcome_waiting) to colors.warning
        else -> "" to colors.ink3
    }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(row.app?.takeIf { it.isNotBlank() }, row.label).joinToString(" · "),
                style = SeiraType.bodyMedium,
                color = colors.ink,
            )
            row.summary?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = SeiraType.callout, color = colors.ink3)
            }
            Text(text = time, style = SeiraType.caption, color = colors.ink3)
        }
        if (outcome.isNotEmpty()) {
            Text(text = outcome, style = SeiraType.callout, color = tint)
        }
    }
}
