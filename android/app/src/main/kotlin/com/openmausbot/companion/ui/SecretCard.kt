package com.openmausbot.companion.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openmausbot.companion.R
import com.openmausbot.companion.core.APIError
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.PhoneSecretCrypto
import com.openmausbot.companion.core.PhoneSecretException
import com.openmausbot.companion.core.PreparedPhoneCredential
import com.openmausbot.companion.core.SecretRequestCardData
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.dialsProtectedRoute
import com.openmausbot.companion.core.pairedForSecrets
import com.openmausbot.companion.core.safeHelpUrl
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal const val CREDENTIAL_FIELD_TAG = "secret-card-field"
internal const val CREDENTIAL_SAVE_TAG = "secret-card-save"

/**
 * Security invariant: the typed value lives only in [value] (plain `remember`, never saved
 * state) and is cleared before any coroutine starts. Only the sealed envelope is kept.
 */
private class SecretEntry(prepared: PreparedPhoneCredential?) {
    var value by mutableStateOf("")
    var fieldGeneration by mutableIntStateOf(0)
    var prepared by mutableStateOf(prepared)
    var submitting by mutableStateOf(false)
    var submitted by mutableStateOf(false)
    var error by mutableStateOf<SecretCardError?>(null)
    var job: Job? = null
    var attempt = 0

    /** An empty field keeps its node, so a password manager that is still filling it can finish. */
    fun clearPlaintext() {
        if (value.isEmpty()) return
        value = ""
        fieldGeneration++
    }

    /** Stops entry and any send, and keeps a sealed envelope so a retry resends the same bytes. */
    fun suspendEntry() {
        clearPlaintext()
        attempt++
        job?.cancel()
        job = null
        submitting = false
        error = null
    }
}

private sealed interface SecretCardError {
    data class Copy(@StringRes val id: Int) : SecretCardError
    data class Words(val text: String) : SecretCardError
}

@Composable
internal fun SecretCard(
    chat: Chat,
    message: Message,
    secret: SecretRequestCardData,
    haptics: Haptics,
    openLink: ((String, Message) -> Unit)?,
) {
    val session = LocalCompanion.current.session
    val connection by session.connection.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = Seira.colors
    val uriHandler = LocalUriHandler.current
    val tint = Color(BotPalette.argb(message.from?.color ?: chat.color))
    val label = secret.label.visible() ?: stringResource(R.string.secret_card_default_label)
    val placeholder = secret.placeholder.visible() ?: label
    val requester = message.from?.name ?: chat.name
    val target = secret.target.visible()
    val requestKey = secret.requestKey.visible()
    val pairedForSecrets = secret.isPending && target != null && requestKey != null && connection?.pairedForSecrets == true
    val protectedRoute = connection?.dialsProtectedRoute == true
    val computerOnly = target in PhoneSecretCrypto.COMPUTER_ONLY_TARGETS
    val canEnterOnPhone = pairedForSecrets && protectedRoute && !computerOnly
    val botId = when (chat) {
        is Chat.BotChat -> chat.bot.id
        is Chat.RoomChat -> message.from?.botId
    }

    val entry = remember(connection?.id, botId, chat.threadId, message.id, secret.target, secret.requestKey) {
        SecretEntry(session.preparedCredential(chat, message))
    }
    DisposableEffect(entry) { onDispose { entry.suspendEntry() } }
    LaunchedEffect(entry) { session.credentialResetGeneration.drop(1).collect { entry.suspendEntry() } }
    LaunchedEffect(entry) { session.status.collect { if (it !is Session.Status.Live) entry.suspendEntry() } }
    LaunchedEffect(entry, secret.isPending) { if (!secret.isPending) entry.clearPlaintext() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, entry) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) entry.suspendEntry() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun send(prepared: PreparedPhoneCredential) {
        entry.job?.cancel()
        val attempt = ++entry.attempt
        entry.error = null
        entry.submitting = true
        entry.job = scope.launch {
            try {
                session.provideCredential(prepared)
                if (entry.attempt == attempt) {
                    entry.submitted = true
                    haptics.play(HapticCue.SUCCESS)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (entry.attempt == attempt) entry.error = secretCardError(error)
            } finally {
                if (entry.attempt == attempt) {
                    entry.job = null
                    entry.submitting = false
                }
            }
        }
    }

    val canSubmit = canEnterOnPhone && !entry.submitting && !entry.submitted &&
        (entry.prepared != null || entry.value.isNotBlank())

    fun submit() {
        if (!canSubmit) return
        entry.error = null
        entry.prepared?.let {
            send(it)
            return
        }
        val prepared = try {
            session.prepareCredential(entry.value, chat, message)
        } catch (error: Exception) {
            entry.clearPlaintext()
            entry.error = secretCardError(error)
            return
        }
        entry.clearPlaintext()
        entry.prepared = prepared
        send(prepared)
    }

    fun discardPrepared() {
        entry.suspendEntry()
        entry.prepared?.let(session::discardPreparedCredential)
        entry.prepared = null
        entry.submitted = false
    }

    val status = stringResource(
        when {
            secret.provided == true -> if (secret.resumed == true) R.string.secret_card_saved_resumed else R.string.secret_card_saved
            secret.dismissed == true -> R.string.secret_card_dismissed
            secret.superseded == true -> R.string.secret_card_superseded
            secret.stopped == true -> R.string.secret_card_stopped
            entry.submitted -> R.string.secret_card_a11y_sent
            computerOnly -> R.string.secret_card_computer_only
            canEnterOnPhone -> R.string.secret_card_a11y_enter
            !pairedForSecrets -> R.string.secret_card_a11y_pair_again
            else -> R.string.secret_card_a11y_secure
        },
    )
    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.tint, cardShape)
            .border(1.dp, if (secret.isPending) tint.copy(alpha = 0.65f) else colors.hairline, cardShape)
            .semantics(mergeDescendants = !canEnterOnPhone) { contentDescription = "$label. $status" }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(tint.copy(alpha = 0.13f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = SeiraType.headline, color = colors.ink)
                Text(stringResource(R.string.secret_card_requested_by, requester), style = SeiraType.caption, color = colors.ink2)
            }
        }

        secret.description.visible()?.let { Text(it, fontSize = 14.sp, color = colors.ink2) }

        when {
            secret.provided == true -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusLine(
                    Icons.Filled.CheckCircle,
                    stringResource(if (secret.resumed == true) R.string.secret_card_saved_resumed else R.string.secret_card_saved),
                    colors.success,
                )
                val prepared = entry.prepared
                if (secret.resumed != true && prepared != null) {
                    TextButton(onClick = { send(prepared) }, enabled = !entry.submitting && protectedRoute) {
                        if (entry.submitting) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            stringResource(if (entry.submitting) R.string.secret_card_resuming else R.string.secret_card_resume),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 7.dp),
                        )
                    }
                }
            }
            secret.dismissed == true -> StatusLine(Icons.Filled.Close, stringResource(R.string.secret_card_dismissed), colors.ink2)
            secret.superseded == true -> StatusLine(Icons.Filled.Info, stringResource(R.string.secret_card_superseded), colors.ink2)
            secret.stopped == true -> StatusLine(Icons.Filled.Close, stringResource(R.string.secret_card_stopped), colors.ink2)
            entry.submitted -> StatusLine(Icons.Filled.CheckCircle, stringResource(R.string.secret_card_submitted), colors.success)
            computerOnly -> StatusLine(Icons.Filled.Info, stringResource(R.string.secret_card_computer_only), colors.ink2)
            canEnterOnPhone -> PhoneEntry(
                entry = entry,
                label = label,
                placeholder = placeholder,
                tint = tint,
                canSubmit = canSubmit,
                onSubmit = ::submit,
                onDiscard = ::discardPrepared,
            )
            !pairedForSecrets -> Notice(
                tint,
                stringResource(R.string.secret_card_pair_again_title),
                stringResource(R.string.secret_card_pair_again_body),
            )
            else -> Notice(
                tint,
                stringResource(R.string.secret_card_secure_title),
                stringResource(R.string.secret_card_secure_body),
            )
        }

        entry.error?.let { StatusLine(Icons.Filled.Warning, it.text(), colors.danger) }
        secret.error.visible()?.let { StatusLine(Icons.Filled.Warning, it, colors.warningText) }

        secret.safeHelpUrl?.let { url ->
            TextButton(onClick = { openLink?.invoke(url, message) ?: uriHandler.openUri(url) }) {
                Text(stringResource(R.string.secret_card_help_link), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PhoneEntry(
    entry: SecretEntry,
    label: String,
    placeholder: String,
    tint: Color,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = Seira.colors
    SecureWindowWhileShown()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.secret_card_entry_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint)
        }
        val prepared = entry.prepared
        if (prepared == null) {
            key(entry.fieldGeneration) {
                OutlinedTextField(
                    value = entry.value,
                    onValueChange = { entry.value = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    enabled = !entry.submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CREDENTIAL_FIELD_TAG)
                        .semantics {
                            contentType = ContentType.Password
                            contentDescription = label
                        },
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(colors.control, RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(if (entry.submitting) R.string.secret_card_prepared_saving else R.string.secret_card_prepared_ready),
                    fontSize = 13.sp,
                    color = colors.ink2,
                )
            }
        }
        SaveButton(
            text = stringResource(
                when {
                    entry.submitting -> R.string.secret_card_saving
                    prepared == null -> R.string.secret_card_save
                    else -> R.string.secret_card_retry
                },
            ),
            enabled = canSubmit,
            busy = entry.submitting,
            onClick = onSubmit,
        )
        if (prepared != null && !entry.submitting && entry.error != null) {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.secret_card_enter_different), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text(stringResource(R.string.secret_card_entry_hint), fontSize = 13.sp, color = colors.ink2)
    }
}

@Composable
private fun SaveButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val colors = Seira.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .testTag(CREDENTIAL_SAVE_TAG)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(colors.primary.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), color = colors.onPrimary, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
            }
            Text(text, style = SeiraType.headline, color = colors.onPrimary)
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun Notice(tint: Color, title: String, body: String) {
    val colors = Seira.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint)
        }
        Text(body, fontSize = 13.sp, color = colors.ink2)
    }
}

@Composable
private fun SecretCardError.text(): String = when (this) {
    is SecretCardError.Copy -> stringResource(id)
    is SecretCardError.Words -> text
}

private fun secretCardError(error: Throwable): SecretCardError = when (error) {
    is PhoneSecretException -> SecretCardError.Copy(
        when (error.reason) {
            PhoneSecretException.Reason.UNAVAILABLE,
            PhoneSecretException.Reason.INVALID_PUBLIC_KEY,
            -> R.string.secret_card_pair_again_title
            PhoneSecretException.Reason.INSECURE_TRANSPORT -> R.string.secret_card_secure_title
            PhoneSecretException.Reason.COMPUTER_ONLY -> R.string.secret_card_computer_only
            PhoneSecretException.Reason.INVALID_CREDENTIAL -> R.string.secret_card_value_length
            PhoneSecretException.Reason.INVALID_REQUEST,
            PhoneSecretException.Reason.ENCRYPTION_FAILED,
            -> R.string.secret_card_not_saved
        },
    )
    is APIError.Status -> error.serverMessage.visible()?.let(SecretCardError::Words)
        ?: SecretCardError.Copy(R.string.secret_card_not_saved)
    else -> SecretCardError.Copy(R.string.secret_card_not_saved)
}

private fun String?.visible(): String? = this?.trim()?.takeIf(String::isNotEmpty)

/** Keeps the window out of screenshots and the recent-apps preview while an entry field shows. */
@Composable
private fun SecureWindowWhileShown() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        window?.let(SecureWindows::acquire)
        onDispose { window?.let(SecureWindows::release) }
    }
}

/** Security invariant: FLAG_SECURE stays on while any entry field shows, and a flag the window already had stays. */
private object SecureWindows {
    private class Hold(var count: Int, val preset: Boolean)

    private val holds = WeakHashMap<Window, Hold>()

    fun acquire(window: Window) {
        val hold = holds.getOrPut(window) {
            Hold(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        if (hold.count++ == 0 && !hold.preset) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        val hold = holds[window] ?: return
        if (--hold.count > 0) return
        holds.remove(window)
        if (!hold.preset) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
