package bot.seira.companion.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.CompanionEndpointKind
import bot.seira.companion.core.Connection
import bot.seira.companion.core.Profile
import bot.seira.companion.notifications.NotificationMapping
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraSwitch
import bot.seira.companion.ui.theme.SeiraType

/** What the profile card and the privacy section need from the computer. */
internal data class AccountInfo(
    val profile: Profile? = null,
    val connectedApps: Int? = null,
)

internal fun interface AccountInfoSource {
    suspend fun load(): AccountInfo
}

internal enum class NotificationChannelRow(
    val channelId: String,
    val title: Int,
    val body: Int,
    val icon: ImageVector,
) {
    APPROVAL(
        NotificationMapping.CHANNEL_BLOCKING,
        R.string.settings_notif_approval_title,
        R.string.settings_notif_approval_body,
        SeiraIcons.Check,
    ),
    DONE(
        NotificationMapping.CHANNEL_DONE,
        R.string.settings_notif_done_title,
        R.string.settings_notif_done_body,
        SeiraIcons.Bell,
    ),
    ROUTINE(
        NotificationMapping.CHANNEL_ROUTINE_FAILED,
        R.string.settings_notif_routine_title,
        R.string.settings_notif_routine_body,
        SeiraIcons.Warning,
    ),
}

internal object SettingsDesign {
    /** A channel the system has not created yet can still alert, so it counts as on. */
    fun channelIsOn(appNotificationsEnabled: Boolean, importance: Int?): Boolean =
        appNotificationsEnabled && importance != android.app.NotificationManager.IMPORTANCE_NONE

    fun kindLabel(kind: CompanionEndpointKind?): Int? = when (kind) {
        CompanionEndpointKind.LAN, CompanionEndpointKind.BONJOUR -> R.string.pair_kind_same_wifi
        CompanionEndpointKind.TAILNET -> R.string.pair_kind_tailscale
        CompanionEndpointKind.HOSTED -> R.string.pair_kind_internet
        null -> null
    }
}

/** A 56 dp row with an optional icon, a title, an optional second line and a trailing slot. */
@Composable
internal fun DesignRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleDot: androidx.compose.ui.graphics.Color? = null,
    icon: ImageVector? = null,
    titleColor: androidx.compose.ui.graphics.Color = Seira.colors.ink,
    minHeight: androidx.compose.ui.unit.Dp = 56.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Seira.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = SeiraType.bodyMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = if (enabled) titleColor else colors.ink3,
            )
            if (subtitle != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (subtitleDot != null) Box(Modifier.size(6.dp).background(subtitleDot, CircleShape))
                    Text(subtitle, style = SeiraType.callout, color = colors.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        trailing?.invoke()
    }
}

/** A row that is one switch: the whole row toggles, and the switch only draws state. */
@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val switch: @Composable () -> Unit = {
        Box(Modifier.clearAndSetSemantics {}) {
            SeiraSwitch(checked = checked, onCheckedChange = null, contentDescription = title, interactionSource = source)
        }
    }
    Box(
        modifier = Modifier.toggleable(
            value = checked,
            interactionSource = source,
            indication = LocalIndication.current,
            role = Role.Switch,
            onValueChange = { onToggle() },
        ),
    ) {
        if (icon == null) {
            DesignRow(title = title, subtitle = subtitle, minHeight = 64.dp, trailing = switch)
        } else {
            LiveIconRow(title = title, subtitle = subtitle, icon = icon, on = checked, trailing = switch)
        }
    }
}

@Composable
private fun LiveIconRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    on: Boolean,
    trailing: @Composable () -> Unit,
) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveRowIcon(icon = icon, on = on)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SeiraType.bodyMedium.copy(fontSize = 16.sp), color = colors.ink)
            if (subtitle != null) {
                Text(subtitle, style = SeiraType.callout, color = colors.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** Two sheets, one in front of the other. The theme set has no copy icon. */
internal val CopyIcon: ImageVector = ImageVector.Builder(
    name = "Copy",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    for (path in listOf("M10 9h9a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1Z", "M15 9V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h4")) {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(path),
            stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 1.85f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
    }
}.build()
