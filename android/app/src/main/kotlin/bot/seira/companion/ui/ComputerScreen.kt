package bot.seira.companion.ui

import bot.seira.companion.R

import androidx.compose.ui.res.stringResource

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.core.Bot

/**
 * A bot's computer, live — the port of `ios/App/ComputerView.swift`.
 *
 * Watch-only: no clicking, no typing, no zoom. The frame is fit to the bounds and
 * lands as a letterbox, because the desktop is wider than the phone.
 *
 * Frames are expensive — hundreds of kilobytes of base64 each — so `Session` only
 * asks for them while this screen is on. [ScreenWatch] holds exactly one watcher
 * for the composable's lifetime.
 */
@Composable
fun ComputerScreen(botId: String, onBack: () -> Unit, onOpenBrowser: (String) -> Unit = {}) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()

    val bot = remember(state, botId) { state.bot(botId) }
    if (bot == null) {
        LaunchedEffect(botId) { onBack() }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ComputerFrame(bot = bot, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.mobile_back_b52b36b7),
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(onClick = onBack)
                    .padding(6.dp),
            )
            Text(
                text = bot.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = localizedMobileCopy(ComputerPolicy.statusLabel(bot)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (bot.busy == true) {
                    Color(BotPalette.argb("green"))
                } else {
                    Color.White.copy(alpha = 0.6f)
                },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedButton(
                onClick = { onOpenBrowser(botId) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(localizedMobileCopy("Open browser")) }
        }
    }
}

@Composable
internal fun ComputerFrame(bot: Bot, modifier: Modifier = Modifier) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val botId = bot.id

    DisposableEffect(botId) {
        val watch = ScreenWatch(
            start = session::watchScreen,
            stop = session::stopWatchingScreen,
        )
        watch.watch(botId)
        onDispose { watch.release() }
    }

    val frame = state.screens[botId]
    val image: ImageBitmap? = remember(frame) {
        frame?.data
            ?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
            ?.asImageBitmap()
    }

    Box(modifier = modifier) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.mobile_bot_name_s_computer_7637def1, bot.name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Waiting(
                headline = ComputerPolicy.waitingHeadline(bot),
                explanation = ComputerPolicy.IDLE_EXPLANATION.takeIf {
                    ComputerPolicy.explainsIdle(bot)
                },
            )
        }
    }
}

@Composable
private fun Waiting(headline: String, explanation: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = Color.White,
        )
        Text(
            text = localizedMobileCopy(headline),
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        explanation?.let {
            Text(
                text = localizedMobileCopy(it),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

// English source; shown through localizedMobileCopy (MOCA-291).
