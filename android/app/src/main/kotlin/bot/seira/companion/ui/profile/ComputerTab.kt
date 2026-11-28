package bot.seira.companion.ui.profile

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.Bot
import bot.seira.companion.core.ComputerControlAction
import bot.seira.companion.ui.ComputerFrame
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.theme.InkPillButton
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType
import bot.seira.companion.ui.theme.SegmentedControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun ComputerTab(bot: Bot, onOpenBrowser: (String) -> Unit) {
    var view by rememberSaveable(bot.id) { mutableStateOf(ComputerView.SCREEN.name) }
    val views = ComputerView.entries
    val selected = views.firstOrNull { it.name == view } ?: ComputerView.SCREEN

    SegmentedControl(
        options = listOf(
            stringResource(R.string.profile_computer_screen) to null,
            stringResource(R.string.profile_computer_browser) to null,
            stringResource(R.string.profile_computer_data) to null,
        ),
        selectedIndex = views.indexOf(selected),
        onSelect = { view = views[it].name },
    )

    when (selected) {
        ComputerView.SCREEN -> ScreenView(bot)
        ComputerView.BROWSER -> BrowserView(bot, onOpenBrowser)
        ComputerView.DATA -> LibraryList(bot)
    }
}

@Composable
private fun ScreenView(bot: Bot) {
    val session = LocalCompanion.current.session
    val colors = Seira.colors
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val takenText = stringResource(R.string.profile_control_taken)
    val failedText = stringResource(R.string.profile_control_failed)
    var lease by rememberSaveable(bot.id) { mutableStateOf<String?>(null) }
    var changing by remember(bot.id) { mutableStateOf(false) }
    val heldLease by rememberUpdatedState(lease)
    val current by rememberUpdatedState(bot)
    val detached = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    LaunchedEffect(bot.id) {
        val saved = lease ?: return@LaunchedEffect
        val state = session.controlComputer(bot, ComputerControlAction.CHECK, saved, bot.threadId)
        if (state?.owned != true) lease = null
    }

    DisposableEffect(bot.id) {
        onDispose {
            val held = heldLease ?: return@onDispose
            if (activity?.isChangingConfigurations == true) return@onDispose
            detached.launch {
                session.controlComputer(current, ComputerControlAction.RELEASE, held, current.threadId)
            }
        }
    }

    ProfileCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        ) {
            ComputerFrame(bot = bot, modifier = Modifier.fillMaxSize())
        }
        if (ProfileComputerRules.mayTakeControl(bot)) {
            CardDivider()
            val holding = lease != null
            ProfileRow(
                title = stringResource(if (holding) R.string.profile_release_control else R.string.profile_take_control),
                subtitle = stringResource(
                    if (holding) R.string.profile_release_control_hint else R.string.profile_take_control_hint,
                ),
                enabled = !changing,
                modifier = Modifier.testTag("profile-control"),
                onClick = {
                    scope.launch {
                        changing = true
                        try {
                            val saved = lease
                            if (saved != null) {
                                val state = session.controlComputer(bot, ComputerControlAction.RELEASE, saved, bot.threadId)
                                if (state != null) lease = null
                            } else {
                                val id = "phone-" + UUID.randomUUID()
                                val state = session.controlComputer(bot, ComputerControlAction.TAKE, id, bot.threadId)
                                when {
                                    state == null -> Unit
                                    state.held && state.owned == false -> session.actionError = takenText
                                    state.held -> lease = id
                                    else -> session.actionError = failedText
                                }
                            }
                        } finally {
                            changing = false
                        }
                    }
                },
                trailing = {
                    Icon(
                        SeiraIcons.Monitor,
                        contentDescription = null,
                        tint = colors.ink2,
                        modifier = Modifier.size(22.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun BrowserView(bot: Bot, onOpenBrowser: (String) -> Unit) {
    val colors = Seira.colors
    ProfileCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_browser_hint),
                style = SeiraType.body,
                color = colors.ink2,
            )
            InkPillButton(
                text = stringResource(R.string.profile_browser_open),
                onClick = { onOpenBrowser(bot.id) },
                modifier = Modifier.fillMaxWidth().testTag("profile-open-browser"),
            )
        }
    }
}
