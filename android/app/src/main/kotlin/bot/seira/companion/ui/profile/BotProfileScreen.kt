package bot.seira.companion.ui.profile

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.Bot
import bot.seira.companion.core.ChatTarget
import bot.seira.companion.core.ReplyStyle
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.MIN_TOUCH_TARGET
import bot.seira.companion.ui.home.PageTopBar
import bot.seira.companion.ui.theme.IdentityAvatar
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraMotion
import bot.seira.companion.ui.theme.SeiraType
import bot.seira.companion.ui.theme.rememberReducedMotion
import bot.seira.companion.ui.theme.seiraTween

/**
 * A bot's profile page: who it is, then four tabs for what it did, how it is
 * set up, what it keeps and its computer.
 *
 * [onOpenChat] opens a conversation above this page, [onOpenBrowser] the
 * driven browser, and [onDeleted] leaves for the Chats tab once the bot is gone.
 */
@Composable
internal fun BotProfileScreen(
    botId: String,
    tab: String,
    onBack: () -> Unit,
    onOpenChat: (ChatTarget) -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onDeleted: () -> Unit = onBack,
) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val bot = state.bot(botId)
    var deleting by remember(botId) { mutableStateOf(false) }
    var selected by rememberSaveable(botId, tab) { mutableStateOf(ProfileTab.fromKey(tab).key) }
    val reduced = rememberReducedMotion()
    val colors = Seira.colors
    // Kept here, not in the Settings tab, so a half-typed change survives a visit to another tab.
    var draft by rememberSaveable(botId, stateSaver = ProfileDraftSaver) {
        mutableStateOf(bot?.let(ProfileDraft::of) ?: ProfileDraft("", "", "", "", null))
    }
    var baseline by rememberSaveable(botId, stateSaver = ProfileDraftSaver) {
        mutableStateOf(bot?.let(ProfileDraft::of) ?: ProfileDraft("", "", "", "", null))
    }

    if (bot == null) {
        if (!deleting) LaunchedEffect(botId) { onBack() }
        Box(modifier = Modifier.fillMaxSize().background(colors.app))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.app)
            .imePadding()
            .testTag("bot-profile-screen"),
    ) {
        PageTopBar(title = stringResource(R.string.shell_bot_profile_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            ProfileHero(bot = bot, onMessage = { onOpenChat(ChatTarget.Bot(bot.id, bot.threadId)) })
            ProfileTabBar(
                tabs = listOf(
                    ProfileTab.ACTIVITY to stringResource(R.string.profile_tab_activity),
                    ProfileTab.SETTINGS to stringResource(R.string.mobile_settings_c7f73bb5),
                    ProfileTab.LIBRARY to stringResource(R.string.profile_tab_library),
                    ProfileTab.COMPUTER to stringResource(R.string.mobile_computer_924645b3),
                ),
                selected = ProfileTab.fromKey(selected),
                onSelect = { selected = it.key },
                modifier = Modifier.testTag("bot-profile-tabs"),
            )
            Crossfade(
                targetState = ProfileTab.fromKey(selected),
                animationSpec = seiraTween(SeiraMotion.BASE, reduced),
                label = "profileTab",
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (current) {
                        ProfileTab.ACTIVITY -> ActivityTab(bot = bot, onOpenChat = onOpenChat)
                        ProfileTab.SETTINGS -> SettingsTab(
                            bot = bot,
                            draft = draft,
                            baseline = baseline,
                            onDraft = { draft = it },
                            onSaved = { saved -> draft = saved; baseline = saved },
                            onDeleting = { deleting = it },
                            onDeleted = onDeleted,
                        )
                        ProfileTab.LIBRARY -> LibraryTab(bot = bot)
                        ProfileTab.COMPUTER -> ComputerTab(bot = bot, onOpenBrowser = onOpenBrowser)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(bot: Bot, onMessage: () -> Unit) {
    val colors = Seira.colors
    val status = stringResource(if (bot.busy == true) R.string.chat_status_working else R.string.mobile_idle_cc1ebdd0)
    val line = if (bot.title.isBlank()) status else stringResource(R.string.profile_status_line, bot.title, status)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            IdentityAvatar(bot = bot, size = 72.dp, contentDescription = bot.name)
        }
        Text(
            text = bot.name,
            style = SeiraType.title1,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = line,
            style = SeiraType.body,
            color = colors.ink3,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp, bottom = 12.dp)
                .heightIn(min = MIN_TOUCH_TARGET)
                .clip(CircleShape)
                .background(colors.s1, CircleShape)
                .border(1.dp, colors.line2, CircleShape)
                .clickable(role = Role.Button, onClick = onMessage)
                .padding(horizontal = 28.dp)
                .testTag("bot-profile-message"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.mobile_message_68f4145f),
                style = SeiraType.headline,
                color = colors.ink,
            )
        }
    }
}

private val ProfileDraftSaver = listSaver<ProfileDraft, String>(
    save = { listOf(it.name, it.title, it.description, it.soul, it.replyStyle?.wire.orEmpty()) },
    restore = {
        ProfileDraft(
            name = it[0],
            title = it[1],
            description = it[2],
            soul = it[3],
            replyStyle = ReplyStyle.entries.firstOrNull { style -> style.wire == it[4] },
        )
    },
)
