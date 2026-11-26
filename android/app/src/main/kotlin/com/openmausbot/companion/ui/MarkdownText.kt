package com.openmausbot.companion.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.core.Markdown
import com.openmausbot.companion.core.MarkdownBlock

/**
 * Bot replies, rendered — the port of `ios/App/MarkdownText.swift`.
 *
 * `Markdown.blocks` (`:core`) does the splitting; this draws each block and hands
 * the inline run to [InlineMarkdown], which knows emphasis, code spans,
 * strikethrough and links. Links are tappable, which is most of why this is worth
 * doing at all — a reply full of sources was otherwise a wall of bracketed URLs.
 *
 * Only bot messages get this. The desktop makes the same split: what you typed is
 * shown as you typed it, because markdown you did not intend is worse than
 * markdown you did.
 */
@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier,
    /**
     * Draws a caret after the last block. The streaming bubble sets this so the
     * live reply and the settled one are the same view with the same layout — a
     * caret bolted on outside would sit on its own line the moment the reply ends
     * in a list item.
     */
    caret: Boolean = false,
    /**
     * Where a tapped link goes. Null hands every link to the system; a chat
     * supplies this so a bot's desktop path is fetched through the computer
     * instead of handed to a browser that cannot reach it.
     */
    openLink: ((String) -> Unit)? = null,
) {
    val segments = remember(source) { MarkdownTables.split(source) }
    CompositionLocalProvider(LocalLinkOpener provides openLink) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEachIndexed { segmentIndex, segment ->
                val lastSegment = segmentIndex == segments.lastIndex
                when (segment) {
                    is MarkdownSegment.Table -> {
                        MarkdownTableView(segment, Modifier.padding(vertical = 6.dp))
                        // The caret still needs a line to sit on after a closing table.
                        if (caret && lastSegment) MarkdownBlockView(MarkdownBlock.Paragraph(""), tail = true)
                    }
                    is MarkdownSegment.Prose -> {
                        val blocks = remember(segment.source) { Markdown.blocks(segment.source) }
                        blocks.forEachIndexed { index, block ->
                            MarkdownBlockView(block, tail = caret && lastSegment && index == blocks.lastIndex)
                        }
                    }
                }
            }
        }
    }
}

/** The opener [MarkdownText] was given, reachable from every block's inline run. */
private val LocalLinkOpener = compositionLocalOf<((String) -> Unit)?> { null }

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, tail: Boolean) {
    val muted = secondaryTint
    when (block) {
        is MarkdownBlock.Paragraph ->
            Text(inline(block.text, tail, muted), style = SeiraType.body, color = Seira.colors.ink, inlineContent = caretContent())

        is MarkdownBlock.Heading ->
            // Three sizes, not six. A chat bubble is not a document, and an h4
            // that looks exactly like body text is a heading that failed.
            Text(
                text = inline(block.text, tail, muted),
                fontSize = when {
                    block.level <= 1 -> 20.sp
                    block.level == 2 -> 17.sp
                    else -> 15.sp
                },
                lineHeight = 24.sp,
                color = Seira.colors.ink,
                fontWeight = FontWeight.SemiBold,
                inlineContent = caretContent(),
                modifier = Modifier.padding(top = 2.dp),
            )

        is MarkdownBlock.Bullet -> MarkerRow("•", block.indent, block.text, tail, muted)

        is MarkdownBlock.Ordered -> MarkerRow("${block.number}.", block.indent, block.text, tail, muted)

        is MarkdownBlock.Quote ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(muted.copy(alpha = 0.4f), RoundedCornerShape(1.5.dp)),
                )
                Text(inline(block.text, tail, muted), style = SeiraType.body, color = muted, inlineContent = caretContent())
            }

        is MarkdownBlock.Code ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(muted.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                block.language?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = muted,
                    )
                }
                // Horizontal scroll rather than wrapping: wrapped code is harder
                // to read than code you have to push sideways, and indentation is
                // most of what a snippet is saying.
                // No selection here: the bubble's long press opens the message
                // menu, and "Select text" is where part of a reply is selected.
                Text(
                    text = buildAnnotatedString {
                        append(block.text)
                        appendCaret(tail)
                    },
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Seira.colors.ink,
                    inlineContent = caretContent(),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }

        MarkdownBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 2.dp))
    }
}

@Composable
private fun MarkerRow(symbol: String, indent: Int, text: String, tail: Boolean, muted: Color) {
    Row(
        modifier = Modifier.padding(start = (indent * 14).dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = symbol,
            style = SeiraType.body,
            color = muted,
            modifier = Modifier.widthIn(min = 16.dp),
        )
        Text(inline(text, tail, muted), style = SeiraType.body, color = Seira.colors.ink, inlineContent = caretContent())
    }
}

@Composable
private fun inline(text: String, tail: Boolean, muted: Color): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val opener = LocalLinkOpener.current
    return buildAnnotatedString {
        for (span in InlineMarkdown.parse(text)) {
            val style = SpanStyle(
                fontWeight = if (InlineStyle.BOLD in span.styles) FontWeight.Bold else null,
                fontStyle = if (InlineStyle.ITALIC in span.styles) FontStyle.Italic else null,
                fontFamily = if (InlineStyle.CODE in span.styles) FontFamily.Monospace else null,
                textDecoration = if (InlineStyle.STRIKE in span.styles) TextDecoration.LineThrough else null,
            )
            // An empty destination is a legal link (`[x]()`), but there is
            // nothing to open — render the label and leave it inert rather than
            // handing the UriHandler a blank URI.
            val url = span.link?.takeIf { it.isNotBlank() }
            if (url == null) {
                withStyle(style) { append(span.text) }
            } else {
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(
                            style = style.copy(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        // With an opener the UriHandler is never consulted.
                        linkInteractionListener = opener?.let { open -> LinkInteractionListener { open(url) } },
                    ),
                ) { append(span.text) }
            }
        }
        appendCaret(tail)
    }
}

/** The id the caret's inline content is registered under. */
private const val CARET_ID = "stream-caret"

/** The tag a test finds the caret by. */
internal const val STREAM_CARET_TAG = "stream-caret"
private const val CARET_BLINK = "caret"
private const val CARET_ALPHA = "caret-alpha"

/**
 * A space, then the caret as inline content, so it sits on the last line of the
 * prose off the last glyph instead of on a line of its own.
 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendCaret(tail: Boolean) {
    if (!tail) return
    append(" ")
    appendInlineContent(CARET_ID, "|")
}

/**
 * The caret: 8 by 17 in ink, blinking once a second. Where the system removes
 * animations it holds still, a little faint.
 */
@Composable
private fun caretContent(): Map<String, InlineTextContent> {
    val color = Seira.colors.ink
    val reduced = rememberReducedMotion()
    return remember(color, reduced) {
        mapOf(
            CARET_ID to InlineTextContent(
                Placeholder(8.sp, 17.sp, PlaceholderVerticalAlign.TextCenter),
            ) {
                val alpha = if (reduced) {
                    0.7f
                } else {
                    val blink = rememberInfiniteTransition(label = CARET_BLINK)
                    blink.animateFloat(
                        initialValue = 0.7f,
                        targetValue = 0.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 500, easing = SeiraMotion.Ease),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = CARET_ALPHA,
                    ).value
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(STREAM_CARET_TAG)
                        .graphicsLayer { this.alpha = alpha }
                        .background(color),
                )
            },
        )
    }
}
