package com.openmausbot.companion.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.CardPresentation
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.presentation
import com.openmausbot.companion.core.stacksOptions
import com.openmausbot.companion.ui.ApprovalChoices
import com.openmausbot.companion.ui.MIN_TOUCH_TARGET
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType

internal enum class ApprovalLook { FILLED, OUTLINED }

internal enum class ApprovalLabel { ALLOW_ONCE, DENY, OWN }

/** One button on a pending card. [option] is the card's own answer, sent unchanged. */
internal data class ApprovalButtonSpec(val option: String, val label: ApprovalLabel, val look: ApprovalLook)

internal object ApprovalButtons {
    private const val ALWAYS_ALLOW = "always allow"

    /** True for a card that asks leave to act, as against one that asks a question. */
    fun isApproval(card: OptionCard): Boolean = card.presentation != CardPresentation.STANDARD

    /**
     * True when the text button stands in for the card's own "Always allow":
     * the card shares a row, offers the button, and keeps another way to say yes.
     * The button shows exactly when this is true, so the choice is never shown twice.
     */
    fun movesAlways(card: OptionCard, chat: Chat): Boolean =
        !card.stacksOptions &&
            ApprovalChoices.showsAlwaysAllow(card, chat) &&
            card.options.any { OptionCard.isApproval(it) && !isAlways(it) }

    /**
     * The extra "Allow for this session" button: Advanced mode, a pending permission card that says
     * the computer honors it, and no skill review, which needs its own hash on the answer.
     */
    fun offersSessionAllow(card: OptionCard, advancedMode: Boolean): Boolean =
        card.offersSessionAllow(advancedMode) && !card.stacksOptions && card.skillRequest == null

    fun layout(card: OptionCard, chat: Chat): List<ApprovalButtonSpec> {
        val movesAlways = movesAlways(card, chat)
        val options = card.options.filterNot { movesAlways && isAlways(it) }
        if (card.stacksOptions) {
            return options.map { ApprovalButtonSpec(it, ApprovalLabel.OWN, ApprovalLook.OUTLINED) }
        }
        var filledGiven = false
        return options.map { option ->
            val refusal = card.refuses(option)
            val trimmed = option.trim()
            val label = when {
                trimmed.equals("allow", ignoreCase = true) || trimmed.equals("allow once", ignoreCase = true) ->
                    ApprovalLabel.ALLOW_ONCE
                trimmed.equals("deny", ignoreCase = true) -> ApprovalLabel.DENY
                else -> ApprovalLabel.OWN
            }
            val look = if (!refusal && !filledGiven) {
                filledGiven = true
                ApprovalLook.FILLED
            } else {
                ApprovalLook.OUTLINED
            }
            ApprovalButtonSpec(option, label, look)
        }
    }

    private fun isAlways(option: String): Boolean = option.trim().equals(ALWAYS_ALLOW, ignoreCase = true)
}

@Composable
internal fun ApprovalButton(
    spec: ApprovalButtonSpec,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val text = when (spec.label) {
        ApprovalLabel.ALLOW_ONCE -> stringResource(R.string.mobile_allow_once_c551e6cf)
        ApprovalLabel.DENY -> stringResource(R.string.mobile_deny_53577bb5)
        ApprovalLabel.OWN -> spec.option
    }
    val filled = spec.look == ApprovalLook.FILLED
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .then(
                    if (filled) {
                        Modifier.background(colors.primary.copy(alpha = alpha), CircleShape)
                    } else {
                        Modifier.border(1.dp, colors.line2.copy(alpha = alpha), CircleShape)
                    },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = SeiraType.headline,
                color = (if (filled) colors.onPrimary else colors.ink).copy(alpha = alpha),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun AlwaysAllowButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = MIN_TOUCH_TARGET)) {
        Text(stringResource(R.string.mobile_always_allow_41325daa), fontSize = 14.sp)
    }
}

/** Allow once, Deny and Always allow on one line. */
@Composable
internal fun ApprovalButtonRow(
    buttons: List<ApprovalButtonSpec>,
    showsAlways: Boolean,
    enabledFor: (ApprovalButtonSpec) -> Boolean,
    alwaysEnabled: Boolean,
    onChoose: (ApprovalButtonSpec) -> Unit,
    onAlways: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { spec ->
            ApprovalButton(
                spec = spec,
                enabled = enabledFor(spec),
                onClick = { onChoose(spec) },
                modifier = Modifier.weight(1f),
            )
        }
        if (showsAlways) AlwaysAllowButton(enabled = alwaysEnabled, onClick = onAlways)
    }
}
