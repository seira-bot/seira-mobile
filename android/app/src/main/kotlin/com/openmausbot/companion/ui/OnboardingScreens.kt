package com.openmausbot.companion.ui

import com.openmausbot.companion.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.ui.theme.InkPillButton
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMark
import com.openmausbot.companion.ui.theme.SeiraType

/**
 * The three first-run surfaces — the behavioural port of
 * `ios/App/OnboardingViews.swift`.
 *
 * What is ported is the **timing and the routing**: the app says what it is
 * before it asks for anything, offers a way to defer that leads somewhere
 * useful, and explains notifications only once a pairing has made approvals and
 * finished work real. Painting uses the Seira tokens only.
 */

/** The sentences these screens are made of, named so a test can look them up. */
object OnboardingCopy {
    const val WELCOME_TITLE = "Take your bots with you"
    const val WELCOME_SUBTITLE =
        "Open chats, approve actions, and send new work from your phone."
    const val WELCOME_CONNECT = "Connect my computer"
    const val NOT_NOW = "Not now"

    const val UNPAIRED_HOME_TITLE = "Connect when you're ready"
    const val UNPAIRED_HOME_BODY =
        "Pair this phone with Seira to see your chats and respond to your bots."
    const val UNPAIRED_HOME_HINT =
        "On your computer, open Seira → Settings → Phone."
    const val UNPAIRED_HOME_CONNECT = "Connect computer"

    const val NOTIFICATIONS_TITLE = "Stay in the loop"

    /**
     * The limit is stated here rather than softened, and it is the same sentence
     * Settings shows, from [SettingsPolicy.NOTIFICATIONS_FOOTER]: someone
     * deciding whether to grant this should be told what it can and cannot do
     * before the system sheet appears, not after.
     */
    const val NOTIFICATIONS_BODY = SettingsPolicy.NOTIFICATIONS_FOOTER
    const val NOTIFICATIONS_APPROVALS = "Approvals that are waiting for you"
    const val NOTIFICATIONS_FINISHED = "Finished work and important updates"
    const val NOTIFICATIONS_ENABLE = "Enable notifications"
}

/**
 * First launch. Says what the app is, then offers exactly two answers — and the
 * second one is real: "Not now" leads to [UnpairedHomeScreen], not back here.
 *
 * Nothing on this screen asks the system for anything. That is the whole point
 * of it existing.
 */
@Composable
fun WelcomeScreen(onConnect: () -> Unit, onSkip: () -> Unit) {
    OnboardingLayout(
        actions = {
            OnboardingPrimary(stringResource(R.string.mobile_onboarding_welcome_connect), onConnect)
            OnboardingSecondary(stringResource(R.string.mobile_onboarding_not_now), onSkip)
        },
    ) {
        OnboardingHero(null)
        Text(
            text = stringResource(R.string.mobile_onboarding_welcome_title),
            style = SeiraType.title1,
            color = Seira.colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.mobile_onboarding_welcome_subtitle),
            style = SeiraType.body,
            color = Seira.colors.ink2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Benefit(
            icon = SeiraIcons.PenSquare,
            title = stringResource(R.string.mobile_your_chats_in_your_pocket_91291d76),
            detail = stringResource(R.string.mobile_onboarding_conversations),
        )
        Benefit(
            icon = SeiraIcons.Check,
            title = stringResource(R.string.mobile_respond_when_a_bot_needs_you_96973325),
            detail = stringResource(R.string.mobile_onboarding_approvals),
        )
        Benefit(
            icon = SeiraIcons.Shield,
            title = stringResource(R.string.mobile_private_by_design_3aaa9fd1),
            detail = stringResource(R.string.mobile_onboarding_trusted_computer),
        )
    }
}

/**
 * Where "Not now" leads. A home that is useful without a pairing: it says how to
 * get one, offers the way in, and — the part iOS puts in a toolbar and this puts
 * in a header — reaches Settings, so notifications can be recovered later
 * without first entering a pairing flow the person just declined.
 */
@Composable
fun UnpairedHomeScreen(onConnect: () -> Unit, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Seira.colors.app)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mobile_openmausbot_befe710d),
                style = SeiraType.title3,
                color = Seira.colors.ink,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            ChromeButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.mobile_settings_c7f73bb5),
                onClick = onOpenSettings,
            )
        }
        OnboardingLayout(
            actions = {
                OnboardingPrimary(stringResource(R.string.mobile_onboarding_connect_computer), onConnect)
            },
        ) {
            Icon3d(R.drawable.icon3d_not_connected, 120.dp, "not_connected")
            Text(
                text = stringResource(R.string.mobile_onboarding_unpaired_title),
                style = SeiraType.title2,
                color = Seira.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.mobile_onboarding_unpaired_body),
                style = SeiraType.body,
                color = Seira.colors.ink2,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.mobile_onboarding_unpaired_hint),
                style = SeiraType.callout,
                color = Seira.colors.ink3,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The only screen in this app that asks for the notification permission, and it
 * is reached only after a first pairing has committed.
 *
 * Both buttons end the step: [onEnable] fires the system prompt through the one
 * object allowed to fire it, and "Not now" declines without one. Either way the
 * step is answered once — see the marker's lifecycle in
 * [com.openmausbot.companion.core.NotificationOnboardingPolicy].
 */
@Composable
fun NotificationOnboardingScreen(
    enabling: Boolean,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingLayout(
        actions = {
            if (enabling) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Seira.colors.ink2,
                    )
                }
            } else {
                OnboardingPrimary(stringResource(R.string.mobile_onboarding_enable_notifications), onEnable)
            }
            OnboardingSecondary(stringResource(R.string.mobile_onboarding_not_now), onSkip, enabled = !enabling)
        },
    ) {
        OnboardingHero(SeiraIcons.Bell)
        Text(
            text = stringResource(R.string.mobile_onboarding_notifications_title),
            style = SeiraType.title1,
            color = Seira.colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.mobile_settings_notifications_footer),
            style = SeiraType.body,
            color = Seira.colors.ink2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Benefit(
            icon = SeiraIcons.Check,
            title = stringResource(R.string.mobile_onboarding_notifications_approvals),
            detail = null,
        )
        Benefit(
            icon = SeiraIcons.PenSquare,
            title = stringResource(R.string.mobile_onboarding_notifications_finished),
            detail = null,
        )
    }
}

/** Scrolling body, actions pinned to the bottom — the same frame for all three. */
@Composable
private fun OnboardingLayout(
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Seira.colors.app)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actions()
        }
    }
}

@Composable
private fun OnboardingPrimary(text: String, onClick: () -> Unit) {
    InkPillButton(
        text = text,
        onClick = onClick,
        height = 52.dp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OnboardingSecondary(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SeiraType.bodyMedium,
            color = if (enabled) Seira.colors.ink2 else Seira.colors.ink3,
        )
    }
}

/** The Seira mark, or one glyph, on a tinted disc. */
@Composable
private fun OnboardingHero(icon: ImageVector?) {
    Box(
        modifier = Modifier.size(104.dp).background(Seira.colors.tint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null) {
            SeiraMark(size = 52.dp)
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Seira.colors.ink,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, detail: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Seira.colors.ink,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = SeiraType.headline, color = Seira.colors.ink)
            detail?.let {
                Text(text = it, style = SeiraType.callout, color = Seira.colors.ink3)
            }
        }
    }
}
