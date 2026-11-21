package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType

internal object ComputerSwitchRules {
    fun ordered(computers: List<Connection>, activeId: String?): List<Connection> =
        computers.filter { it.id == activeId } + computers.filter { it.id != activeId }

    /** The address that tells apart computers with one name; a relay-only `.invalid` name is no address. */
    fun distinguishingAddress(computer: Connection, computers: List<Connection>): String? =
        computer.displayAddress.takeIf {
            computers.count { it.name == computer.name } > 1 && !computer.host.endsWith(".invalid", ignoreCase = true)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComputerSwitchSheet(
    computers: List<Connection>,
    activeId: String?,
    status: Session.Status,
    onPick: (Connection) -> Unit,
    onPairAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("computer-switch-sheet"),
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.home_computers_title),
                    style = SeiraType.title2,
                    color = colors.ink,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            items(ComputerSwitchRules.ordered(computers, activeId), key = { "computer-${it.id}" }) { computer ->
                val active = computer.id == activeId
                val state = if (active) computerStatusPhrase(status) else stringResource(R.string.settings_state_saved)
                ComputerRow(
                    icon = SeiraIcons.Laptop,
                    title = computer.name,
                    subtitle = listOfNotNull(state, ComputerSwitchRules.distinguishingAddress(computer, computers))
                        .joinToString(" · "),
                    tag = "computer-switch.${computer.id}",
                    active = active,
                    onClick = { if (active) onDismiss() else onPick(computer) },
                )
            }
            item(key = "pair-another") {
                ComputerRow(
                    icon = SeiraIcons.Plus,
                    title = stringResource(R.string.settings_pair_another),
                    subtitle = null,
                    tag = "computer-switch-pair",
                    active = false,
                    onClick = onPairAnother,
                )
            }
        }
    }
}

@Composable
private fun ComputerRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tag: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { selected = active }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(colors.control, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SeiraType.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = SeiraType.caption, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active) {
            Icon(SeiraIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
    }
}
