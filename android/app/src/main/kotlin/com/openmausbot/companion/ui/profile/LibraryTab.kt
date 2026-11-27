package com.openmausbot.companion.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.DataPage
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import kotlinx.coroutines.launch

@Composable
internal fun LibraryTab(bot: Bot) {
    LibraryList(bot)
}

/** The pages and files a bot keeps. The Library tab and the Data view of the Computer tab both show it. */
@Composable
internal fun LibraryList(bot: Bot) {
    val session = LocalCompanion.current.session
    var items by remember(bot.id) { mutableStateOf<List<LibraryItem>?>(null) }
    var failed by remember(bot.id) { mutableStateOf(false) }
    var attempt by remember(bot.id) { mutableStateOf(0) }
    var open by remember(bot.id) { mutableStateOf<LibraryItem?>(null) }

    LaunchedEffect(bot.id, attempt) {
        failed = false
        val data = session.botData(bot)
        items = data?.let(ProfileLibraryRules::items)
        failed = data == null
    }

    ProfileGroup(title = stringResource(R.string.profile_library_pages)) {
        val list = items
        when {
            failed -> ProfileNotice(stringResource(R.string.profile_library_failed), retry = { attempt++ })
            list == null -> ProfileLoading()
            list.isEmpty() -> ProfileNotice(stringResource(R.string.profile_library_empty))
            else -> list.forEachIndexed { index, item ->
                if (index > 0) CardDivider()
                ProfileRow(
                    title = item.title,
                    subtitle = item.count?.let {
                        val n = it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        pluralStringResource(R.plurals.profile_library_count, n, n)
                    },
                    onClick = { open = item },
                    trailing = {
                        Icon(
                            SeiraIcons.ChevronRight,
                            contentDescription = null,
                            tint = Seira.colors.ink3,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
    }

    open?.let { LibraryViewerSheet(bot = bot, item = it, onDismiss = { open = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryViewerSheet(bot: Bot, item: LibraryItem, onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    val colors = Seira.colors
    var page by remember(item.id) { mutableStateOf<DataPage?>(null) }
    var rows by remember(item.id) { mutableStateOf<List<List<String>>>(emptyList()) }
    var total by remember(item.id) { mutableStateOf(0L) }
    var failed by remember(item.id) { mutableStateOf(false) }
    var loading by remember(item.id) { mutableStateOf(item.text == null) }

    suspend fun load(offset: Int) {
        loading = true
        failed = false
        val loaded = session.botDataPage(bot, ProfileLibraryRules.request(item, offset))
        if (loaded == null) {
            failed = true
        } else {
            if (offset == 0) page = loaded
            rows = rows + loaded.rows.map { row -> row.map(ProfileLibraryRules::cell) }
            total = loaded.rowCount
        }
        loading = false
    }

    LaunchedEffect(item.id) { if (item.text == null) load(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = item.title, style = SeiraType.title2, color = colors.ink)
            if (item.text != null) {
                Text(text = item.text, style = SeiraType.body, color = colors.ink)
            } else {
                val columns = page?.columns.orEmpty()
                rows.forEach { row ->
                    ProfileCard {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEachIndexed { index, value ->
                                val name = columns.getOrNull(index)?.name.orEmpty()
                                Text(
                                    text = if (name.isEmpty()) value else "$name: $value",
                                    style = SeiraType.callout,
                                    color = colors.ink,
                                )
                            }
                        }
                    }
                }
                when {
                    failed -> ProfileNotice(stringResource(R.string.profile_library_failed), retry = {
                        scope.launch { load(rows.size) }
                    })
                    loading -> ProfileLoading()
                    rows.isEmpty() -> ProfileNotice(stringResource(R.string.profile_library_empty))
                    ProfileLibraryRules.hasMore(rows.size, total) -> ProfileRow(
                        title = stringResource(R.string.profile_library_more),
                        titleColor = colors.accentText,
                        onClick = { scope.launch { load(rows.size) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
