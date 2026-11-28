package bot.seira.companion.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.R
import bot.seira.companion.core.Chat
import bot.seira.companion.core.Message
import bot.seira.companion.core.ThreadRef
import bot.seira.companion.core.WidgetActionEntry
import bot.seira.companion.core.WidgetChartModel
import bot.seira.companion.core.WidgetField
import bot.seira.companion.core.WidgetNode
import bot.seira.companion.core.WidgetPress
import bot.seira.companion.core.WidgetPressPlan
import bot.seira.companion.core.WidgetPressResult
import bot.seira.companion.core.WidgetSendIds
import bot.seira.companion.core.WidgetSettled
import bot.seira.companion.core.WidgetTone
import bot.seira.companion.core.WidgetValue
import bot.seira.companion.core.WidgetView
import bot.seira.companion.core.planWidgetPress
import bot.seira.companion.core.widgetViewOnly
import bot.seira.companion.core.widgetActionUrl
import bot.seira.companion.core.widgetActions
import bot.seira.companion.core.widgetChart
import bot.seira.companion.core.widgetChildren
import bot.seira.companion.core.widgetCutAtUnknown
import bot.seira.companion.core.widgetDays
import bot.seira.companion.core.widgetFallbackText
import bot.seira.companion.core.widgetFieldOptions
import bot.seira.companion.core.widgetFields
import bot.seira.companion.core.widgetFraction
import bot.seira.companion.core.widgetImageUrl
import bot.seira.companion.core.widgetInt
import bot.seira.companion.core.widgetInitialValues
import bot.seira.companion.core.widgetString
import bot.seira.companion.core.widgetTable
import bot.seira.companion.core.widgetTakesOther
import bot.seira.companion.core.widgetThreadTarget
import bot.seira.companion.core.widgetType
import bot.seira.companion.core.widgetView
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle as DayStyle
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val SENT_MS = 1_600L
private const val IMAGE_BYTES = 5 * 1024 * 1024
private const val IMAGE_EDGE = 1_080
private val CARD_WIDTHS = mapOf("sm" to 360, "md" to 440, "lg" to 600, "full" to 600)
private val NARROW_ROW = 360.dp

private class WidgetForm(
    val values: Map<String, WidgetValue>,
    val other: Map<String, String>,
    val locked: Boolean,
    val invalidField: String?,
    val problem: String?,
    val pending: String?,
    val sent: String?,
    val actions: Map<String, WidgetActionEntry>,
    val labelled: Set<String>,
    val setValue: (String, WidgetValue) -> Unit,
    val chooseOther: (String) -> Unit,
    val setOther: (String, String) -> Unit,
    val press: (String) -> Unit,
    val imagesAllowed: Boolean,
    val allowImages: () -> Unit,
)

private val LocalWidgetForm = compositionLocalOf<WidgetForm?> { null }

private class WidgetProblem(val text: String, val field: String?)

/** Joins a widget message to the session, the system browser and the thread navigation. */
@Composable
internal fun WidgetMessageRow(
    chat: Chat,
    message: Message,
    openLink: ((String, Message) -> Unit)?,
    openThread: ((ThreadRef) -> Unit)?,
) {
    val session = LocalCompanion.current.session
    val uriHandler = LocalUriHandler.current
    WidgetCardView(
        message = message,
        viewOnly = widgetViewOnly(chat),
        // The press finishes even when the row scrolls away; the card then hears the result from the computer.
        press = { press -> withContext(NonCancellable) { session.pressWidget(chat, press) } },
        openUrl = { url -> openLink?.invoke(url, message) ?: runCatching { uriHandler.openUri(url) } },
        openThread = openThread,
    )
}

/**
 * A card a bot drew: the phone's counterpart of the desktop `WidgetMessage`.
 * [press] sends a button press; [openUrl] and [openThread] run on the phone.
 * With [viewOnly] (a group chat) the card takes no press, but links and
 * thread jumps still work.
 */
@Composable
internal fun WidgetCardView(
    message: Message,
    viewOnly: Boolean,
    press: suspend (WidgetPress) -> WidgetPressResult,
    openUrl: (String) -> Unit,
    openThread: ((ThreadRef) -> Unit)?,
) {
    val view = remember(message.widget) { widgetView(message.widget) }
    if (view == null) {
        widgetFallbackText(message.text)?.let {
            Text(it, fontSize = 15.sp, color = Seira.colors.ink, modifier = Modifier.padding(start = 4.dp))
        }
        return
    }
    // Replacing a widget renumbers its nodes, so a new revision starts the card afresh.
    key(message.id, view.rev) {
        WidgetCard(message, view, viewOnly, press, openUrl, openThread)
    }
}

@Composable
private fun WidgetCard(
    message: Message,
    view: WidgetView,
    viewOnly: Boolean,
    press: suspend (WidgetPress) -> WidgetPressResult,
    openUrl: (String) -> Unit,
    openThread: ((ThreadRef) -> Unit)?,
) {
    val root = view.root
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val colors = Seira.colors
    val ids = remember { WidgetSendIds() }
    var edits by remember { mutableStateOf<Map<String, WidgetValue>>(emptyMap()) }
    var otherEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pending by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<WidgetProblem?>(null) }
    var settledHere by remember { mutableStateOf<WidgetSettled?>(null) }
    var rowSpoke by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var imagesAllowed by remember { mutableStateOf(false) }
    LaunchedEffect(view.settled != null) { if (view.settled != null) rowSpoke = true }
    val settled = view.settled ?: if (rowSpoke) null else settledHere

    val actions = remember(root) { widgetActions(root).associateBy { it.ref } }
    val labelled = remember(root) { labelledFields(root) }
    val values = remember(root, edits, settled) { widgetInitialValues(root) + edits + settled?.values.orEmpty() }
    val other = if (settled != null) settled.other else otherEdits

    fun pressRef(ref: String) {
        if (pending != null) return
        when (val plan = planWidgetPress(root, ref, values, other, settled != null, viewOnly)) {
            WidgetPressPlan.Ignore -> Unit
            is WidgetPressPlan.OpenOnPhone -> {
                widgetActionUrl(plan.entry)?.let(openUrl)
                val target = widgetThreadTarget(plan.entry)
                val botId = target?.botId
                if (target != null && botId != null) openThread?.invoke(ThreadRef(botId, target.threadId, ""))
            }
            is WidgetPressPlan.Missing ->
                problem = WidgetProblem(resources.getString(R.string.widget_required, plan.field.label), plan.field.name)
            is WidgetPressPlan.Send -> {
                val request = WidgetPress(message.id, ref, view.rev, plan.values, plan.other, ids.idFor(plan.key))
                rowSpoke = false
                settledHere = null
                problem = null
                sent = null
                pending = ref
                scope.launch {
                    val result = press(request)
                    pending = null
                    when (result) {
                        WidgetPressResult.Sent -> {
                            ids.clear()
                            val tone = plan.entry.tone
                            if (tone != null) {
                                settledHere = WidgetSettled(
                                    ref = ref,
                                    label = plan.entry.label,
                                    tone = tone,
                                    at = System.currentTimeMillis().toDouble(),
                                    sendId = request.sendId,
                                    values = plan.values.orEmpty(),
                                    other = plan.other.orEmpty(),
                                )
                            } else {
                                sent = ref
                                delay(SENT_MS)
                                if (sent == ref) sent = null
                            }
                        }
                        is WidgetPressResult.Refused -> {
                            ids.clear()
                            problem = WidgetProblem(result.message ?: resources.getString(R.string.widget_send_failed), null)
                        }
                        WidgetPressResult.Failed ->
                            problem = WidgetProblem(resources.getString(R.string.widget_send_failed), null)
                    }
                }
            }
        }
    }

    val form = WidgetForm(
        values = values,
        other = other,
        locked = settled != null || pending != null || viewOnly,
        invalidField = problem?.field,
        problem = problem?.text,
        pending = pending,
        sent = sent,
        actions = actions,
        labelled = labelled,
        setValue = { name, value ->
            edits = edits + (name to value)
            otherEdits = otherEdits - name
            if (problem?.field == name) problem = null
        },
        chooseOther = { name ->
            edits = edits + (name to WidgetValue.Text(""))
            otherEdits = otherEdits + (name to (otherEdits[name] ?: ""))
            if (problem?.field == name) problem = null
        },
        setOther = { name, text ->
            otherEdits = otherEdits + (name to text)
            if (problem?.field == name) problem = null
        },
        press = ::pressRef,
        imagesAllowed = imagesAllowed,
        allowImages = { imagesAllowed = true },
    )

    val card: @Composable () -> Unit = {
        CompositionLocalProvider(LocalWidgetForm provides form, LocalWidgetUrlOpener provides openUrl) {
            WidgetSurface(message, view, form, settled == null, ::pressRef)
        }
    }

    if (settled == null) {
        card()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (settled.tone == WidgetTone.CANCEL) SeiraIcons.X else SeiraIcons.Check,
                contentDescription = null,
                tint = secondaryTint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(R.string.widget_settled_line, settled.label, settledTime(settled.at)),
                fontSize = 14.sp,
                color = secondaryTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = { details = !details }) {
                Text(
                    if (details) stringResource(R.string.widget_hide_details) else stringResource(R.string.mobile_card_details),
                    fontSize = 13.sp,
                    color = colors.ink2,
                )
            }
        }
        if (details) card()
    }
}

private fun settledTime(at: Double): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at.toLong()))

private fun labelledFields(node: WidgetNode, into: MutableSet<String> = mutableSetOf()): Set<String> {
    if (widgetType(node) == "Label") widgetString(node, "fieldName")?.let(into::add)
    widgetChildren(node).forEach { labelledFields(it, into) }
    return into
}

@Composable
private fun WidgetSurface(message: Message, view: WidgetView, form: WidgetForm, live: Boolean, press: (String) -> Unit) {
    val root = view.root
    val colors = Seira.colors
    val shape = RoundedCornerShape(18.dp)
    val cut = remember(root) { widgetCutAtUnknown(root) }
    val status = (root["status"] as? JsonObject)
    val statusText = status?.let { widgetString(it, "text") }
    val statusIcon = status?.let { widgetString(it, "icon") }
    val title = widgetString(root, "title")
    val caption = widgetString(root, "caption")
    val meta = widgetString(root, "meta")
    val headed = listOf(statusText, title, caption, meta).any { !it.isNullOrEmpty() }
    val width = CARD_WIDTHS[widgetString(root, "size") ?: "lg"] ?: 600

    Column(modifier = Modifier.widthIn(max = width.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(secondaryTint.copy(alpha = 0.10f), shape)
                .border(1.dp, secondaryTint.copy(alpha = 0.18f), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (headed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!statusText.isNullOrEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                statusIcon?.let(::widgetIcon)?.let {
                                    Icon(it, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
                                }
                                Text(statusText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText)
                            }
                        }
                        if (!title.isNullOrEmpty()) Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                        if (!caption.isNullOrEmpty()) Text(caption, fontSize = 13.sp, color = secondaryTint)
                    }
                    if (!meta.isNullOrEmpty()) Text(meta, fontSize = 12.sp, color = colors.ink3, maxLines = 1)
                }
            }
            WidgetChildren(cut.root, Modifier.fillMaxWidth())
            if (cut.cut || view.truncated) {
                widgetFallbackText(message.text)?.let { Text(it, fontSize = 14.sp, color = secondaryTint) }
            }
            form.problem?.takeIf { form.invalidField == null }?.let {
                Text(it, fontSize = 13.sp, color = colors.danger)
            }
        }
        if (live) {
            val foot = listOf("confirm", "cancel").mapNotNull { form.actions[it]?.takeIf { entry -> !entry.hiddenOnPhone } }
            if (foot.isNotEmpty() || form.pending != null || form.sent != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    foot.forEach { entry ->
                        WidgetButtonView(entry.ref, entry.label, if (entry.ref == "confirm") "primary" else "secondary", false)
                    }
                    if (form.pending != null) Text(stringResource(R.string.widget_sending), fontSize = 13.sp, color = secondaryTint)
                    else if (form.sent != null) Text(stringResource(R.string.widget_sent), fontSize = 13.sp, color = secondaryTint)
                }
            }
        }
    }
}

@Composable
private fun WidgetChildren(node: WidgetNode, modifier: Modifier = Modifier) {
    val items = widgetChildren(node)
    val limit = widgetInt(node, "limit", 0, 40)
    val shown = if (widgetType(node) == "ListView" && limit != null) items.take(limit) else items
    if (widgetType(node) == "ListView") {
        WidgetListView(node, shown, modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { WidgetNodeView(it) }
    }
}

@Composable
private fun WidgetListView(list: WidgetNode, rows: List<WidgetNode>, modifier: Modifier) {
    val form = LocalWidgetForm.current ?: return
    val name = widgetString(list, "name")
    val choosing = name != null
    val colors = Seira.colors
    val current = (form.values[name] as? WidgetValue.Text)?.value.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            val value = widgetString(row, "value").orEmpty()
            val entry = form.actions[widgetString(row, "id")]
            val selected = choosing && current == value && value.isNotEmpty() && !form.other.containsKey(name)
            val canTap = when {
                choosing -> !form.locked
                entry != null -> entry.client || !form.locked
                else -> false
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canTap, role = if (choosing) Role.RadioButton else Role.Button) {
                        if (choosing) form.setValue(name!!, WidgetValue.Text(value)) else entry?.let { form.press(it.ref) }
                    }
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (choosing) RadioButton(selected = selected, onClick = null, enabled = !form.locked)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    widgetChildren(row).forEach { WidgetNodeView(it) }
                }
                if (!choosing && entry != null) {
                    Icon(SeiraIcons.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
                }
            }
            HorizontalDivider(color = colors.hairline)
        }
        if (choosing && widgetTakesOther(list)) OtherChoice(name!!, selectedOther = form.other.containsKey(name))
    }
}

@Composable
private fun WidgetNodeView(node: WidgetNode) {
    val form = LocalWidgetForm.current ?: return
    val colors = Seira.colors
    when (val type = widgetType(node)) {
        "Box", "Col", "Form", "ListViewItem", "Transition", "Table.Row", "Table.Cell" -> {
            if (type == "Box" && widgetString(node, "direction") == "row") WidgetRow(node) else WidgetChildren(node)
        }
        "Row" -> WidgetRow(node)
        "Spacer" -> Spacer(Modifier.height((widgetInt(node, "minSize", 4, 48) ?: 8).dp))
        "Divider" -> HorizontalDivider(color = colors.hairline)
        "Text" -> WidgetText(node, 14.sp, FontWeight.Normal)
        "Title" -> WidgetText(node, titleSize(widgetString(node, "size")), FontWeight.SemiBold)
        "Caption" -> WidgetText(node, 12.sp, FontWeight.Normal, defaultColor = secondaryTint)
        "Label" -> Text(
            widgetString(node, "value").orEmpty(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = secondaryTint,
        )
        "Markdown" -> MarkdownText(widgetString(node, "value").orEmpty(), openLink = LocalWidgetUrlOpener.current)
        "Image" -> WidgetImage(node, form)
        "Icon" -> widgetString(node, "name")?.let(::widgetIcon)?.let {
            Icon(it, contentDescription = null, tint = toneColor(widgetString(node, "color"), colors.ink2), modifier = Modifier.size(iconSize(widgetString(node, "size"))))
        }
        "Chart" -> WidgetChartView(node)
        "Badge" -> WidgetBadge(node)
        "Progress" -> WidgetProgress(node)
        "Button" -> {
            val entry = form.actions[widgetString(node, "id")]
            if (entry?.hiddenOnPhone != true) {
                WidgetButtonView(
                    ref = entry?.ref.orEmpty(),
                    label = widgetString(node, "label").orEmpty(),
                    look = widgetString(node, "style") ?: "secondary",
                    disabled = entry == null,
                )
            }
        }
        "Table" -> {
            val table = remember(node) { widgetTable(node) }
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                DataGrid(table.headers, table.rows)
            }
        }
        "Select" -> WidgetSelect(node)
        "RadioGroup" -> WidgetRadioGroup(node)
        "Checkbox" -> WidgetCheckbox(node)
        "Input", "Textarea" -> WidgetTextField(node, multiline = type == "Textarea")
        "DatePicker", "TimePicker", "DayPicker" -> WidgetReadOnlyField(node)
    }
}

private val LocalWidgetUrlOpener = compositionLocalOf<((String) -> Unit)?> { null }

@Composable
private fun WidgetRow(node: WidgetNode) {
    val children = widgetChildren(node)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < NARROW_ROW) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { children.forEach { WidgetNodeView(it) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                children.forEach { Box(Modifier.weight(1f, fill = false)) { WidgetNodeView(it) } }
            }
        }
    }
}

private fun titleSize(size: String?) = when (size) {
    "sm" -> 14.sp
    "md" -> 16.sp
    "lg" -> 18.sp
    "xl" -> 20.sp
    "2xl" -> 22.sp
    "3xl" -> 24.sp
    "4xl" -> 28.sp
    "5xl" -> 32.sp
    else -> 16.sp
}

private fun iconSize(size: String?): Dp = when (size) {
    "xs" -> 12.dp
    "sm" -> 14.dp
    "lg" -> 20.dp
    "xl" -> 24.dp
    "2xl" -> 28.dp
    "3xl" -> 32.dp
    else -> 16.dp
}

@Composable
private fun toneColor(name: String?, fallback: Color): Color {
    val colors = Seira.colors
    return when (name) {
        "secondary" -> colors.ink2
        "tertiary" -> colors.ink3
        "success" -> colors.success
        "warning" -> colors.warningText
        "danger" -> colors.danger
        "primary", "prose", "emphasis" -> colors.ink
        else -> fallback
    }
}

@Composable
private fun WidgetText(node: WidgetNode, base: androidx.compose.ui.unit.TextUnit, baseWeight: FontWeight, defaultColor: Color = Seira.colors.ink) {
    val size = when (widgetString(node, "size")) {
        "xs" -> 11.sp
        "sm" -> if (base == 12.sp) 12.sp else 13.sp
        "md" -> 14.sp
        "lg" -> 17.sp
        "xl" -> 19.sp
        else -> base
    }
    val weight = when (widgetString(node, "weight")) {
        "medium" -> FontWeight.Medium
        "semibold" -> FontWeight.SemiBold
        "bold" -> FontWeight.Bold
        "normal" -> FontWeight.Normal
        else -> baseWeight
    }
    val lines = widgetInt(node, "maxLines", 1, 50) ?: if (node["truncate"] == JsonPrimitive(true)) 1 else Int.MAX_VALUE
    Text(
        widgetString(node, "value").orEmpty(),
        style = TextStyle(
            fontSize = size,
            fontWeight = weight,
            fontStyle = if (node["italic"] == JsonPrimitive(true)) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (node["lineThrough"] == JsonPrimitive(true)) TextDecoration.LineThrough else null,
            textAlign = when (widgetString(node, "textAlign")) {
                "center" -> TextAlign.Center
                "end" -> TextAlign.End
                else -> TextAlign.Start
            },
        ),
        color = toneColor(widgetString(node, "color"), defaultColor),
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun WidgetBadge(node: WidgetNode) {
    val colors = Seira.colors
    val tone = when (widgetString(node, "color")) {
        "success" -> colors.success
        "danger" -> colors.danger
        "warning" -> colors.warningText
        "info", "discovery" -> colors.accentText
        else -> colors.ink2
    }
    val solid = widgetString(node, "variant") == "solid"
    Text(
        widgetString(node, "label").orEmpty(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (solid) colors.onPrimary else tone,
        modifier = Modifier
            .background(if (solid) tone else tone.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun WidgetProgress(node: WidgetNode) {
    val value = widgetFraction(node, "value")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(widgetString(node, "label").orEmpty(), fontSize = 13.sp, color = secondaryTint)
            Text("${(value * 100).roundToInt()}%", fontSize = 13.sp, color = secondaryTint)
        }
        LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WidgetButtonView(ref: String, label: String, look: String, disabled: Boolean) {
    val form = LocalWidgetForm.current ?: return
    val entry = form.actions[ref]
    val busy = form.pending == ref && ref.isNotEmpty()
    val enabled = !disabled && entry != null && (entry.client || !form.locked)
    val colors = Seira.colors
    val onClick = { form.press(ref) }
    val content: @Composable () -> Unit = {
        if (busy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
        else if (form.sent == ref && ref.isNotEmpty()) Icon(SeiraIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
    when (look) {
        "primary" -> Button(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
        "danger" -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = Color.White),
        ) { content() }
        "quiet" -> TextButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
        else -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
    }
}

// MARK: - Fields

@Composable
private fun FieldFrame(node: WidgetNode, content: @Composable () -> Unit) {
    val form = LocalWidgetForm.current ?: return
    val name = widgetString(node, "name").orEmpty()
    val label = widgetString(node, "label")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!label.isNullOrEmpty() && name !in form.labelled && widgetType(node) != "Checkbox") {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = secondaryTint)
        }
        content()
        if (form.invalidField == name) {
            form.problem?.let { Text(it, fontSize = 12.sp, color = Seira.colors.danger) }
        }
    }
}

@Composable
private fun WidgetTextField(node: WidgetNode, multiline: Boolean) {
    val form = LocalWidgetForm.current ?: return
    val name = widgetString(node, "name") ?: return
    val value = (form.values[name] as? WidgetValue.Text)?.value.orEmpty()
    val enabled = !form.locked && node["disabled"] != JsonPrimitive(true)
    val keyboard = when (widgetString(node, "inputType")) {
        "number" -> KeyboardType.Number
        "email" -> KeyboardType.Email
        "tel" -> KeyboardType.Phone
        "url" -> KeyboardType.Uri
        else -> KeyboardType.Text
    }
    FieldFrame(node) {
        OutlinedTextField(
            value = value,
            onValueChange = { form.setValue(name, WidgetValue.Text(it)) },
            enabled = enabled,
            isError = form.invalidField == name,
            placeholder = widgetString(node, "placeholder")?.let { hint -> @Composable { Text(hint) } },
            singleLine = !multiline,
            minLines = if (multiline) widgetInt(node, "rows", 1, 12) ?: 3 else 1,
            maxLines = if (multiline) max(widgetInt(node, "rows", 1, 12) ?: 3, widgetInt(node, "maxRows", 1, 20) ?: 8) else 1,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WidgetCheckbox(node: WidgetNode) {
    val form = LocalWidgetForm.current ?: return
    val name = widgetString(node, "name") ?: return
    val on = (form.values[name] as? WidgetValue.Flag)?.on == true
    val enabled = !form.locked && node["disabled"] != JsonPrimitive(true)
    FieldFrame(node) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .clickable(enabled = enabled, role = Role.Checkbox) { form.setValue(name, WidgetValue.Flag(!on)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = on, onCheckedChange = null, enabled = enabled)
            Text(widgetString(node, "label") ?: name, fontSize = 14.sp, color = Seira.colors.ink)
        }
    }
}

@Composable
private fun WidgetRadioGroup(node: WidgetNode) {
    val form = LocalWidgetForm.current ?: return
    val name = widgetString(node, "name") ?: return
    val current = (form.values[name] as? WidgetValue.Text)?.value.orEmpty()
    val enabled = !form.locked && node["disabled"] != JsonPrimitive(true)
    FieldFrame(node) {
        Column {
            widgetFieldOptions(node).forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .clickable(enabled = enabled, role = Role.RadioButton) { form.setValue(name, WidgetValue.Text(option.value)) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = current == option.value && !form.other.containsKey(name), onClick = null, enabled = enabled)
                    Text(option.label, fontSize = 14.sp, color = Seira.colors.ink)
                }
            }
            if (widgetTakesOther(node) && enabled) OtherChoice(name, selectedOther = form.other.containsKey(name))
            else if (form.other.containsKey(name)) OtherChoice(name, selectedOther = true)
        }
    }
}

@Composable
private fun WidgetSelect(node: WidgetNode) {
    val form = LocalWidgetForm.current ?: return
    val name = widgetString(node, "name") ?: return
    val options = remember(node) { widgetFieldOptions(node) }
    val current = (form.values[name] as? WidgetValue.Text)?.value.orEmpty()
    val enabled = !form.locked && node["disabled"] != JsonPrimitive(true)
    var open by remember { mutableStateOf(false) }
    val shown = when {
        form.other.containsKey(name) -> stringResource(R.string.widget_other)
        else -> options.firstOrNull { it.value == current }?.label
    } ?: widgetString(node, "placeholder") ?: stringResource(R.string.widget_choose)
    FieldFrame(node) {
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            ) {
                Text(shown, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            open = false
                            form.setValue(name, WidgetValue.Text(option.value))
                        },
                    )
                }
                if (widgetTakesOther(node)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.widget_other)) },
                        onClick = {
                            open = false
                            form.chooseOther(name)
                        },
                    )
                }
            }
        }
        if (form.other.containsKey(name)) OtherChoice(name, selectedOther = true, showRow = false)
    }
}

@Composable
private fun OtherChoice(name: String, selectedOther: Boolean, showRow: Boolean = true) {
    val form = LocalWidgetForm.current ?: return
    Column {
        if (showRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clickable(enabled = !form.locked, role = Role.RadioButton) { form.chooseOther(name) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = selectedOther, onClick = null, enabled = !form.locked)
                Text(stringResource(R.string.widget_other), fontSize = 14.sp, color = Seira.colors.ink)
            }
        }
        if (selectedOther) {
            OutlinedTextField(
                value = form.other[name].orEmpty(),
                onValueChange = { form.setOther(name, it) },
                enabled = !form.locked,
                isError = form.invalidField == name,
                placeholder = { Text(stringResource(R.string.widget_other_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A date, a time or a set of days, shown as it is. The phone does not edit these yet. */
@Composable
private fun WidgetReadOnlyField(node: WidgetNode) {
    val form = LocalWidgetForm.current ?: return
    val name = widgetString(node, "name") ?: return
    val raw = (form.values[name] as? WidgetValue.Text)?.value.orEmpty()
    val shown = when {
        raw.isEmpty() -> stringResource(R.string.widget_not_set)
        widgetType(node) == "DayPicker" -> widgetDays(raw).joinToString(", ") {
            DayOfWeek.valueOf(
                when (it) {
                    "mon" -> "MONDAY"
                    "tue" -> "TUESDAY"
                    "wed" -> "WEDNESDAY"
                    "thu" -> "THURSDAY"
                    "fri" -> "FRIDAY"
                    "sat" -> "SATURDAY"
                    else -> "SUNDAY"
                },
            ).getDisplayName(DayStyle.SHORT, Locale.getDefault())
        }
        else -> raw
    }
    FieldFrame(node) {
        Text(shown, fontSize = 14.sp, color = Seira.colors.ink)
    }
}

// MARK: - Images

@Composable
private fun WidgetImage(node: WidgetNode, form: WidgetForm) {
    val url = widgetImageUrl(node) ?: return
    val alt = widgetString(node, "alt").orEmpty()
    if (!form.imagesAllowed) {
        OutlinedButton(onClick = form.allowImages, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Icon(SeiraIcons.Image, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.widget_show_image), fontSize = 14.sp)
        }
        return
    }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { bitmap = fetchSecureImage(url) }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = alt.ifEmpty { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(10.dp)),
        )
    }
}

/** Fetches a secure image once. A redirect, a large body or a bad picture gives null. */
private suspend fun fetchSecureImage(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            if (connection.responseCode != 200) return@runCatching null
            val bytes = connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > IMAGE_BYTES) return@runCatching null
                }
                out.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > IMAGE_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

// MARK: - Chart

@Composable
private fun WidgetChartView(node: WidgetNode) {
    val chart = remember(node) { widgetChart(node) }
    if (chart == null) return
    if (!chart.drawable) {
        val table = remember(chart) { chart.asTable() }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { DataGrid(table.headers, table.rows) }
        return
    }
    val colors = Seira.colors
    val palette = listOf(colors.accent, colors.success, colors.warning, colors.danger)
    val summary = stringResource(R.string.widget_chart, chart.series.joinToString(", ") { it.label })
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (chart.series.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chart.series.forEachIndexed { index, series ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(palette[index % palette.size], CircleShape))
                        Text(series.label, fontSize = 12.sp, color = secondaryTint)
                    }
                }
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = summary },
        ) { drawChart(chart, palette, colors.hairline) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(chart.categories.first(), fontSize = 11.sp, color = colors.ink3, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (chart.categories.size > 1) {
                Text(chart.categories.last(), fontSize = 11.sp, color = colors.ink3, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChart(chart: WidgetChartModel, palette: List<Color>, rule: Color) {
    val rows = chart.categories.size
    val slot = size.width / rows
    val groups = chart.series.withIndex().filter { it.value.type == "bar" }.groupBy { it.value.stack ?: "own-${it.index}" }.values.toList()
    var top = 0.0
    var bottom = 0.0
    for (row in 0 until rows) {
        for (group in groups) {
            val sum = group.sumOf { it.value.values.getOrNull(row) ?: 0.0 }
            top = max(top, sum)
            bottom = min(bottom, sum)
        }
        chart.series.filter { it.type != "bar" }.forEach { line ->
            line.values.getOrNull(row)?.let {
                top = max(top, it)
                bottom = min(bottom, it)
            }
        }
    }
    if (top == bottom) top = bottom + 1.0
    fun y(value: Double): Float = (size.height - ((value - bottom) / (top - bottom) * size.height)).toFloat()
    drawLine(rule, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1f)

    val barWidth = slot * 0.7f / max(groups.size, 1)
    groups.forEachIndexed { groupIndex, group ->
        for (row in 0 until rows) {
            var base = 0.0
            group.forEach { (index, series) ->
                val value = series.values.getOrNull(row) ?: return@forEach
                val from = y(base)
                val to = y(base + value)
                drawRect(
                    color = palette[index % palette.size],
                    topLeft = Offset(row * slot + slot * 0.15f + groupIndex * barWidth, min(from, to)),
                    size = Size(barWidth * 0.9f, kotlin.math.abs(from - to).coerceAtLeast(1f)),
                )
                base += value
            }
        }
    }
    chart.series.withIndex().filter { it.value.type != "bar" }.forEach { (index, series) ->
        val color = palette[index % palette.size]
        val points = series.values.mapIndexedNotNull { row, value -> value?.let { Offset(row * slot + slot / 2, y(it)) } }
        if (points.isEmpty()) return@forEach
        val line = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        if (series.type == "area" && points.size > 1) {
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, y(0.0))
                lineTo(points.first().x, y(0.0))
                close()
            }
            drawPath(area, color.copy(alpha = 0.18f))
        }
        drawPath(line, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
        points.forEach { drawCircle(color, radius = 4f, center = it) }
    }
}

// MARK: - Icons

private fun widgetIcon(name: String): ImageVector? = when (name) {
    "info", "circle-question", "lifesaver" -> Icons.Filled.Info
    "check" -> SeiraIcons.Check
    "check-circle", "check-circle-filled" -> Icons.Filled.CheckCircle
    "warning", "bug" -> Icons.Filled.Warning
    "bell" -> SeiraIcons.Bell
    "calendar", "clock", "book-clock" -> SeiraIcons.CalendarClock
    "lock", "keys" -> Icons.Filled.Lock
    "mail" -> Icons.Filled.Email
    "phone" -> Icons.Filled.Call
    "mobile" -> SeiraIcons.Smartphone
    "desktop" -> SeiraIcons.Monitor
    "play" -> Icons.Filled.PlayArrow
    "plus" -> Icons.Filled.Add
    "reload" -> Icons.Filled.Refresh
    "search" -> SeiraIcons.Search
    "star", "star-filled" -> Icons.Filled.Star
    "user", "profile", "profile-card", "agent", "name" -> Icons.Filled.Person
    "close" -> SeiraIcons.X
    "write", "write-alt", "write-alt2", "notebook-pencil" -> Icons.Filled.Edit
    "settings-slider" -> Icons.Filled.Settings
    "map-pin", "maps", "compass" -> Icons.Filled.LocationOn
    "download", "upload" -> SeiraIcons.Download
    "document", "file", "page-blank", "folder", "square-text", "notebook" -> SeiraIcons.File
    "images", "square-image" -> SeiraIcons.Image
    "chevron-right" -> SeiraIcons.ChevronRight
    "chevron-left" -> SeiraIcons.ChevronLeft
    "arrow-right", "external-link", "link" -> Icons.AutoMirrored.Filled.ArrowForward
    else -> null
}
