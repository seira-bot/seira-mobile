package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import java.util.Calendar

/** Which greeting the hour of the day picks, and the first name it is said to. */
internal object HomeGreeting {
    enum class Period { MORNING, AFTERNOON, EVENING }

    fun period(hourOfDay: Int): Period = when (hourOfDay) {
        in 5..11 -> Period.MORNING
        in 12..17 -> Period.AFTERNOON
        else -> Period.EVENING
    }

    fun firstName(fullName: String?): String? =
        fullName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }

    /** One or two capitals for the profile circle; empty when there is no name. */
    fun initials(fullName: String?): String =
        fullName.orEmpty().trim().split(' ').filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.take(1).uppercase() }
}

/** The hour of day the greeting reads; tests replace it to pin a period. */
internal val LocalHomeHour = staticCompositionLocalOf<() -> Int> {
    { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
}

@Composable
internal fun homeGreetingText(hour: Int, fullName: String?): String {
    val base = stringResource(
        when (HomeGreeting.period(hour)) {
            HomeGreeting.Period.MORNING -> R.string.home_greeting_morning
            HomeGreeting.Period.AFTERNOON -> R.string.home_greeting_afternoon
            HomeGreeting.Period.EVENING -> R.string.home_greeting_evening
        },
    )
    val first = HomeGreeting.firstName(fullName) ?: return base
    return stringResource(R.string.home_greeting_with_name, base, first)
}

/** The greeting, the computer this list comes from, and the profile circle that opens Settings. */
@Composable
internal fun HomeHeader(
    greeting: String,
    initials: String,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    computerName: String? = null,
    onComputer: () -> Unit = {},
) {
    val colors = Seira.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("roster-header")
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp)
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = SeiraType.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            if (computerName != null) {
                ComputerChip(name = computerName, onClick = onComputer)
            }
        }
        TouchTarget(
            onClick = onProfile,
            contentDescription = stringResource(R.string.mobile_settings_c7f73bb5),
            modifier = Modifier.testTag("home-profile"),
        ) {
            Box(
                modifier = Modifier
                    .size(PROFILE_CIRCLE)
                    .background(colors.tint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (initials.isEmpty()) {
                    Icon(SeiraIcons.User, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = initials,
                        style = SeiraType.callout.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em),
                        color = colors.ink,
                    )
                }
            }
        }
    }
}

/**
 * The Chats title and the menu that keeps the secondary actions one tap away:
 * updates, the routine calendar and Settings.
 */
@Composable
internal fun ChatsHeading(
    onUpdates: () -> Unit,
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-chats-heading")
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_chats),
            style = SeiraType.headline,
            color = colors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Box {
            TouchTarget(
                onClick = { menuOpen = true },
                contentDescription = stringResource(R.string.home_more_options),
                modifier = Modifier.testTag("home-more"),
            ) {
                Icon(EllipsisIcon, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mobile_open_updates_2c80d633)) },
                    leadingIcon = { Icon(SeiraIcons.Bell, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onUpdates()
                    },
                    modifier = Modifier.testTag("open-updates"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mobile_calendar_open)) },
                    leadingIcon = { Icon(SeiraIcons.CalendarClock, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onCalendar()
                    },
                    modifier = Modifier.testTag("open-calendar"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mobile_settings_c7f73bb5)) },
                    leadingIcon = { Icon(SeiraIcons.User, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onSettings()
                    },
                )
            }
        }
    }
}

@Composable
private fun ComputerChip(name: String, onClick: () -> Unit) {
    val colors = Seira.colors
    val label = stringResource(R.string.home_switch_computer, name)
    Row(
        modifier = Modifier
            .testTag("home-computer")
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SeiraIcons.Laptop, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(16.dp))
        Text(
            text = name,
            style = SeiraType.callout,
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(SeiraIcons.ChevronDown, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(14.dp))
    }
}

private val PROFILE_CIRCLE = 40.dp

/** Three dots in the theme's stroke weight; the theme has no ellipsis of its own. */
private val EllipsisIcon: ImageVector = ImageVector.Builder(
    name = "Ellipsis",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = addPathNodes("M5 12h.01 M12 12h.01 M19 12h.01"),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 3f,
        strokeLineCap = StrokeCap.Round,
    )
}.build()
