package bot.seira.companion.ui

import bot.seira.companion.R

import androidx.compose.ui.res.stringResource

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import bot.seira.companion.ui.home.PageTopBar
import bot.seira.companion.ui.settings.Chevron
import bot.seira.companion.ui.settings.ConfirmSheet
import bot.seira.companion.ui.settings.GroupCard
import bot.seira.companion.ui.settings.GroupTitle
import bot.seira.companion.ui.settings.PageActionPill
import bot.seira.companion.ui.settings.PageSearchField
import bot.seira.companion.ui.settings.RowDivider
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import bot.seira.companion.core.ConnectedAppsRules
import bot.seira.companion.core.ConnectorAccount
import bot.seira.companion.core.ConnectorCard
import bot.seira.companion.core.ConnectorCatalog
import bot.seira.companion.core.ConnectorStatus
import bot.seira.companion.core.ConnectorStatuses
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The paired-phone inventory for apps connected on the computer.
 *
 * Authorization deliberately leaves the app for the system browser. The
 * desktop owns provider credentials and revocation, and the paired phone only
 * starts an authorization flow and reports the inventory it was given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAppsScreen(onBack: () -> Unit) {
    val environment = LocalCompanion.current
    val session = environment.session
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var catalog by remember { mutableStateOf<ConnectorCatalog?>(null) }
    var inventory by remember { mutableStateOf(ConnectedAppsInventory()) }
    // Everything typed, and the dialog holding it, outlive a rotation: this
    // Activity is recreated on one, and the catalog behind them is re-fetched.
    var query by rememberSaveable { mutableStateOf("") }
    val visibleCards by remember {
        derivedStateOf { ConnectedAppsPolicy.filterCards(catalog?.cards.orEmpty(), query) }
    }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var aliasTarget by rememberSaveable(stateSaver = AliasTargetSaver) {
        mutableStateOf<AliasTarget?>(null)
    }
    var alias by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<RemoveTarget?>(null) }
    var removeError by remember { mutableStateOf<String?>(null) }
    val removeFailed = stringResource(R.string.settings2_apps_remove_failed)

    suspend fun refresh(showProgress: Boolean = false) {
        if (showProgress) refreshing = true
        try {
            session.loadAllConnectorStatuses()?.let { response ->
                inventory = ConnectedAppsPolicy.accept(inventory, response)
            }
        } finally {
            if (showProgress) refreshing = false
        }
    }

    fun authorize(slug: String, accountAlias: String?) {
        scope.launch {
            val url = session.authorizeConnector(slug, accountAlias) ?: return@launch
            try {
                // The core client accepts only a well-formed HTTPS URL. ACTION_VIEW
                // keeps provider cookies, redirects and account choice in the
                // browser instead of placing them in our process.
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toASCIIString())))
            } catch (_: ActivityNotFoundException) {
                session.actionError = "This phone has no browser available to open the authorization page."
            } catch (_: SecurityException) {
                session.actionError = "The authorization page could not be opened. Check your browser restrictions and try again."
            }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            coroutineScope {
                val loadedCatalog = async { catalog = session.loadConnectorCatalog() }
                val loadedInventory = async { refresh() }
                loadedCatalog.await()
                loadedInventory.await()
            }
        } finally {
            loading = false
        }
    }

    // Authorization finishes in the browser, so the answer arrives while this
    // screen is away — iOS re-reads on `scenePhase == .active`
    // (`ios/App/ConnectedAppsView.swift:90-92`), without the full-screen
    // spinner its `load` uses. The first resume is this screen's own entry,
    // which the effect above already loaded, so it is skipped rather than
    // asking twice.
    var entered by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (entered) scope.launch { refresh() } else entered = true
        onPauseOrDispose {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Seira.colors.app),
    ) {
        PageTopBar(
            title = stringResource(R.string.mobile_connected_apps_8ab72a8e),
            onBack = onBack,
            trailing = {
                PageActionPill(
                    text = if (refreshing) stringResource(R.string.mobile_refreshing_96141178) else stringResource(R.string.mobile_refresh_56e3badc),
                    enabled = !refreshing,
                    onClick = { scope.launch { refresh(showProgress = true) } },
                )
            },
        )

        // The same gesture the roster uses for the same question (RosterScreen.kt:167),
        // and iOS's `.refreshable` (ConnectedAppsView.swift:89).
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refresh(showProgress = true) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "search", contentType = "search") {
                    PageSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.mobile_search_apps_ca3ce8f3),
                    )
                }

                removeError?.let {
                    item(key = "remove-error", contentType = "notice") {
                        Text(it, style = SeiraType.callout, color = Seira.colors.danger)
                    }
                }

                if (inventory.credentialStoreUnreadable) {
                    item(key = "credential-warning", contentType = "notice") {
                        CredentialStoreWarning(hasLastKnownInventory = inventory.statuses != null)
                    }
                }

                if (ConnectedAppsPolicy.showSetupNotice(catalog, inventory.credentialStoreUnreadable)) {
                    item(key = "setup-notice", contentType = "notice") {
                        GroupCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(stringResource(R.string.mobile_connected_apps_need_setup_2cdb536a), style = SeiraType.bodyMedium, color = Seira.colors.ink)
                                Text(
                                    stringResource(R.string.mobile_configure_composio_on_your_compute_4281e8b0),
                                    color = secondaryTint,
                                    style = SeiraType.callout,
                                )
                            }
                        }
                    }
                }

                items(visibleCards, key = { it.slug }, contentType = { "card" }) { card ->
                    ConnectorCardView(
                        card = card,
                        status = inventory.statuses?.get(card.slug),
                        hasAuthoritativeInventory = inventory.statuses != null,
                        configured = catalog?.configured == true,
                        onConnect = { authorize(card.slug, ConnectedAppsRules.firstAccountAlias) },
                        onAddAccount = {
                            alias = ""
                            aliasTarget = AliasTarget(slug = card.slug, label = card.label)
                        },
                        onRemoveAccount = { account -> removing = RemoveTarget(card.slug, card.label, account.id, account.alias.orEmpty()) },
                    )
                }

                if (!loading && catalog?.cards.isNullOrEmpty()) {
                    item(key = "empty", contentType = "notice") {
                        Text(stringResource(R.string.mobile_no_connected_apps_are_available_fr_0529e71d), color = secondaryTint)
                    }
                } else if (!loading && visibleCards.isEmpty()) {
                    item(key = "no-match", contentType = "notice") {
                        Text(stringResource(R.string.settings2_apps_no_match), color = secondaryTint)
                    }
                }

                if (loading) {
                    item(key = "loading", contentType = "notice") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
            }
        }
    }

    removing?.let { target ->
        val accountName = ConnectedAppsPolicy.accountAlias(target.alias) ?: stringResource(R.string.mobile_primary_account_32f2184e)
        ConfirmSheet(
            title = stringResource(R.string.settings2_apps_remove_title, accountName, target.label),
            note = stringResource(R.string.settings2_apps_remove_note),
            confirmLabel = stringResource(R.string.mobile_remove_e963907d),
            onConfirm = {
                removing = null
                scope.launch {
                    removeError = null
                    if (session.deleteConnectorAccount(target.slug, target.accountId)) {
                        refresh()
                    } else {
                        session.actionError = null
                        removeError = removeFailed
                    }
                }
            },
            onDismiss = { removing = null },
            tag = "connected-apps-remove-sheet",
        )
    }

    aliasTarget?.let { target ->
        val normalized = ConnectedAppsRules.additionalAccountAlias(alias)
        AlertDialog(
            onDismissRequest = { aliasTarget = null },
            title = { Text(stringResource(R.string.mobile_account_alias_8cf27ca0)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text(stringResource(R.string.mobile_work_personal_client_7d6a18a4)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.mobile_an_alias_makes_the_account_explici_725873b3, target.label),
                        color = secondaryTint,
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = normalized != null,
                    onClick = {
                        aliasTarget = null
                        authorize(target.slug, normalized)
                    },
                ) { Text(stringResource(R.string.mobile_continue_2e026239)) }
            },
            dismissButton = { TextButton(onClick = { aliasTarget = null }) { Text(stringResource(R.string.mobile_cancel_77dfd213)) } },
        )
    }
}

internal const val CONNECTOR_CARD_TAG = "connector-card"

/**
 * What the alias dialog is about: the connector it will authorize and the name
 * it says out loud. Kept apart from [ConnectorCard] because it has to survive a
 * rotation on its own — the catalog it came from is being fetched again at that
 * moment, and a dialog holding typed text cannot wait for the network.
 */
private data class AliasTarget(val slug: String, val label: String)

private data class RemoveTarget(val slug: String, val label: String, val accountId: String, val alias: String)

private val AliasTargetSaver = listSaver<AliasTarget?, String>(
    save = { target -> target?.let { listOf(it.slug, it.label) }.orEmpty() },
    restore = { saved -> saved.takeIf { it.size == 2 }?.let { AliasTarget(it[0], it[1]) } },
)

@Composable
private fun CredentialStoreWarning(hasLastKnownInventory: Boolean) {
    GroupCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.mobile_accounts_could_not_be_re_checked_6727be8f), style = SeiraType.bodyMedium, color = Seira.colors.danger)
            Text(
                if (hasLastKnownInventory) {
                    stringResource(R.string.mobile_showing_what_was_connected_last_ti_62107fa4)
                } else {
                    stringResource(R.string.mobile_your_computer_could_not_open_its_c_fa640d75)
                },
                color = secondaryTint,
                style = SeiraType.callout,
            )
        }
    }
}

@Composable
private fun ConnectorCardView(
    card: ConnectorCard,
    status: ConnectorStatus?,
    hasAuthoritativeInventory: Boolean,
    configured: Boolean,
    onConnect: () -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (ConnectorAccount) -> Unit,
) {
    val colors = Seira.colors
    val accounts = status?.accounts.orEmpty()
    val pending = status?.pending == true

    Column(modifier = Modifier.fillMaxWidth().testTag(CONNECTOR_CARD_TAG)) {
        GroupTitle(card.label)
        GroupCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(card.blurb, style = SeiraType.callout, color = colors.ink2, modifier = Modifier.weight(1f))
                if (pending) Text(stringResource(R.string.mobile_connecting_fd3e7969), color = secondaryTint, style = SeiraType.callout)
            }
            RowDivider()
            when {
                card.noAuth -> DesignRow(title = stringResource(R.string.mobile_connected_c2f9b7b4))
                !hasAuthoritativeInventory -> DesignRow(title = stringResource(R.string.mobile_connection_unknown_973b0484))
                ConnectedAppsPolicy.offersConnect(card, status) -> DesignRow(
                    title = stringResource(R.string.mobile_connect_card_label_c9e612b4, card.label),
                    enabled = configured,
                    onClick = onConnect,
                    trailing = { Chevron() },
                )
                accounts.isEmpty() -> {
                    DesignRow(
                        title = if (pending) stringResource(R.string.mobile_connecting_fd3e7969) else stringResource(R.string.mobile_connected_c2f9b7b4),
                        subtitle = stringResource(R.string.mobile_account_details_are_unavailable_fr_d9d62172),
                    )
                }
                else -> {
                    accounts.forEachIndexed { index, account ->
                        if (index > 0) RowDivider()
                        ConnectorAccountRow(account, appLabel = card.label, onRemove = { onRemoveAccount(account) })
                    }
                    RowDivider()
                    // Kept and disabled at the limit, so the limit is legible
                    // (`ios/App/ConnectedAppsView.swift:159-163`); hiding the
                    // row leaves the ceiling to be guessed at.
                    DesignRow(
                        title = stringResource(R.string.mobile_add_another_account_5dcc7913),
                        icon = SeiraIcons.Plus,
                        enabled = ConnectedAppsRules.canAddAnotherAccount(accounts),
                        onClick = onAddAccount,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectorAccountRow(account: ConnectorAccount, appLabel: String, onRemove: () -> Unit) {
    val colors = Seira.colors
    val name = ConnectedAppsPolicy.accountAlias(account.alias) ?: stringResource(R.string.mobile_primary_account_32f2184e)
    DesignRow(
        title = name,
        subtitle = if (account.isActive) null else localizedMobileCopy(ConnectedAppsPolicy.statusLabel(account.status)),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (account.isActive) stringResource(R.string.mobile_active_a733b809) else stringResource(R.string.mobile_pending_96f608c1),
                    color = if (account.isActive) colors.success else secondaryTint,
                    style = SeiraType.callout,
                )
                TouchTarget(
                    onClick = onRemove,
                    contentDescription = stringResource(R.string.settings2_apps_remove_content, name, appLabel),
                ) {
                    Icon(SeiraIcons.X, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                }
            }
        },
    )
}

/** Pure state transition so an unavailable store can never erase a known inventory. */
data class ConnectedAppsInventory(
    val statuses: Map<String, ConnectorStatus>? = null,
    val credentialStoreUnreadable: Boolean = false,
)

object ConnectedAppsPolicy {
    /** The name a person gave an account; null for the primary account, which has none. */
    fun accountAlias(alias: String?): String? =
        alias?.trim { it.isWhitespace() }?.takeIf { it.isNotEmpty() }

    fun accept(previous: ConnectedAppsInventory, response: ConnectorStatuses): ConnectedAppsInventory =
        if (response.isAuthoritative) {
            ConnectedAppsInventory(statuses = response.services)
        } else {
            previous.copy(credentialStoreUnreadable = true)
        }

    /**
     * The provider's own wire word, made readable —
     * `status.replacingOccurrences(of: "_", with: " ").capitalized` in
     * `ios/App/ConnectedAppsView.swift:144`. Case is fixed, not the phone's
     * locale: these are ASCII protocol words, not prose.
     */
    fun statusLabel(status: String): String =
        status.replace('_', ' ').split(' ').joinToString(" ") { word ->
            word.lowercase().replaceFirstChar(Char::uppercaseChar)
        }

    /** A toolkit that needs no sign-in has nothing to authorize, so it never offers Connect. */
    fun offersConnect(card: ConnectorCard, status: ConnectorStatus?): Boolean =
        !card.noAuth && status?.accounts.orEmpty().isEmpty() && status?.connected != true && status?.pending != true

    fun showSetupNotice(catalog: ConnectorCatalog?, credentialStoreUnreadable: Boolean): Boolean =
        catalog?.configured == false && !credentialStoreUnreadable

    fun filterCards(cards: List<ConnectorCard>, query: String): List<ConnectorCard> {
        val needle = query.trim()
        if (needle.isEmpty()) return cards
        return cards.filter { card ->
            card.label.contains(needle, ignoreCase = true) || card.slug.contains(needle, ignoreCase = true)
        }
    }
}
