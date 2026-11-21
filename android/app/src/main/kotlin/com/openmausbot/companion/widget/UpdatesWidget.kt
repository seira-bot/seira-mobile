package com.openmausbot.companion.widget

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.openmausbot.companion.R
import com.openmausbot.companion.notifications.NotificationIntents
import java.util.Date

/** The Updates widget: 2x2 answers the top ask, 4x2 and 4x4 list the updates. */
class UpdatesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetSnapshotStore.forContext(context)
        val snapshot = store.read()
        val faces = WidgetFaces(context, store)
        val bitmaps = snapshot?.rows.orEmpty().associate { it.lookKey to faces.load(it) }
        provideContent {
            UpdatesWidgetContent(
                view = WidgetPresentation.view(snapshot, System.currentTimeMillis()),
                face = { row -> bitmaps[row.lookKey] ?: WidgetFaces.dot(row.color) },
            )
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val TALL = DpSize(250.dp, 250.dp)
    }
}

@Composable
internal fun UpdatesWidgetContent(view: WidgetView, face: (WidgetSnapshot.Row) -> Bitmap) {
    val context = LocalContext.current
    val size = LocalSize.current
    GlanceTheme {
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(12.dp),
        ) {
            when (view) {
                WidgetView.Unpaired -> Notice(context.getString(R.string.widget_open_to_pair))
                is WidgetView.Quiet -> Column {
                    Notice(context.getString(R.string.widget_all_quiet))
                    view.asOf?.let { AsOf(context, it) }
                }
                is WidgetView.Active -> when {
                    size.width < WIDE_FROM -> Small(context, view, face)
                    size.height < TALL_FROM -> Digest(context, view, face, tall = false)
                    else -> Digest(context, view, face, tall = true)
                }
            }
        }
    }
}

private val WIDE_FROM = 200.dp
private val TALL_FROM = 200.dp

@Composable
private fun Notice(text: String) {
    Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = body(GlanceTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun AsOf(context: Context, writtenAt: Long) {
    val time = DateFormat.getTimeFormat(context).format(Date(writtenAt))
    Text(context.getString(R.string.widget_as_of, time), style = caption())
}

@Composable
private fun Small(context: Context, view: WidgetView.Active, face: (WidgetSnapshot.Row) -> Bitmap) {
    val row = WidgetPresentation.top(view.rows) ?: return
    Column(modifier = GlanceModifier.fillMaxSize().clickable(openChat(context, row))) {
        RowHead(row, face(row), nameSize = 14)
        Spacer(GlanceModifier.height(4.dp))
        Text(row.line, style = caption(), maxLines = 3)
        Spacer(GlanceModifier.defaultWeight())
        if (row.kind == WidgetSnapshot.Kind.NEEDS_YOU && row.canAnswer) {
            if (view.answersOn) {
                AnswerButton(context, row)
            } else {
                Text(context.getString(R.string.widget_open_to_answer), style = caption())
            }
        }
        view.asOf?.let { AsOf(context, it) }
    }
}

@Composable
private fun AnswerButton(context: Context, row: WidgetSnapshot.Row) {
    val choice = row.plainAllow ?: return
    val requestId = row.requestId ?: return
    Box(
        modifier = GlanceModifier.fillMaxWidth()
            .height(32.dp)
            .background(GlanceTheme.colors.primary)
            .cornerRadius(16.dp)
            .clickable(
                actionRunCallback<WidgetAnswerAction>(
                    actionParametersOf(
                        WidgetAnswerAction.ThreadKey to row.threadId,
                        WidgetAnswerAction.BotKey to row.botId,
                        WidgetAnswerAction.RequestKey to requestId,
                        WidgetAnswerAction.ChoiceKey to choice,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            context.getString(R.string.notification_action_approve),
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun Digest(context: Context, view: WidgetView.Active, face: (WidgetSnapshot.Row) -> Bitmap, tall: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.widget_updates_label),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(context.getString(R.string.widget_active_count, view.rows.size), style = caption())
        }
        view.asOf?.let { AsOf(context, it) }
        Spacer(GlanceModifier.height(6.dp))
        if (tall) {
            for ((kind, rows) in WidgetPresentation.sections(view.rows)) {
                Text(sectionTitle(context, kind).uppercase(), style = caption())
                for (row in rows) DigestRow(context, row, face(row))
            }
        } else {
            for (row in view.rows.take(WidgetPresentation.FLAT_ROWS)) DigestRow(context, row, face(row))
        }
    }
}

@Composable
private fun DigestRow(context: Context, row: WidgetSnapshot.Row, face: Bitmap) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(openChat(context, row))) {
        RowHead(row, face, nameSize = 13)
        Text(row.line, style = caption(), maxLines = 1)
    }
}

@Composable
private fun RowHead(row: WidgetSnapshot.Row, face: Bitmap, nameSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(face), contentDescription = null, modifier = GlanceModifier.size(24.dp))
        Spacer(GlanceModifier.width(8.dp))
        Text(
            row.name,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = nameSize.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

private fun openChat(context: Context, row: WidgetSnapshot.Row) = actionStartActivity(openIntent(context, row))

internal fun openIntent(context: Context, row: WidgetSnapshot.Row) =
    NotificationIntents.openIntent(context, row.botId, row.threadId, row.kind.wire)

internal val WidgetSnapshot.Kind.wire: String
    get() = when (this) {
        WidgetSnapshot.Kind.NEEDS_YOU -> "needsYou"
        WidgetSnapshot.Kind.WORKING -> "working"
        WidgetSnapshot.Kind.TO_REVIEW -> "toReview"
    }

private fun sectionTitle(context: Context, kind: WidgetSnapshot.Kind): String = context.getString(
    when (kind) {
        WidgetSnapshot.Kind.NEEDS_YOU -> R.string.widget_section_needs_you
        WidgetSnapshot.Kind.WORKING -> R.string.widget_section_working
        WidgetSnapshot.Kind.TO_REVIEW -> R.string.widget_section_to_review
    },
)

@Composable
private fun body(color: androidx.glance.unit.ColorProvider) = TextStyle(color = color, fontSize = 13.sp)

@Composable
private fun caption() = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)
