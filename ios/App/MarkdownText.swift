// Bot replies, rendered.
//
// `Markdown.blocks` does the splitting; this draws each block and hands the
// inline run to Foundation, which knows emphasis, code spans, strikethrough
// and links. SwiftUI makes the links tappable on its own, which is most of
// why this is worth doing at all — a reply full of sources was previously a
// wall of bracketed URLs.
//
// Only bot messages get this. The desktop makes the same split: what you
// typed is shown as you typed it, because markdown you did not intend is
// worse than markdown you did.
import SwiftUI
import UIKit
import CompanionCore

private struct OptionalIdentifier: ViewModifier {
    let identifier: String?

    func body(content: Content) -> some View {
        if let identifier {
            content.accessibilityIdentifier(identifier)
        } else {
            content
        }
    }
}

struct MarkdownText: View {
    private final class CachedInline: NSObject {
        let text: AttributedString
        init(_ text: AttributedString) { self.text = text }
    }
    private static let inlineCache: NSCache<NSString, CachedInline> = {
        let cache = NSCache<NSString, CachedInline>()
        cache.countLimit = 256
        cache.totalCostLimit = 524_288
        return cache
    }()
    /// The live reply's last block is different text on almost every update.
    /// It gets its own small cache so a redraw for some other reason is still
    /// a lookup, while its one-use prefixes never push settled messages out
    /// of `inlineCache`.
    private static let liveInlineCache: NSCache<NSString, CachedInline> = {
        let cache = NSCache<NSString, CachedInline>()
        cache.countLimit = 64
        cache.totalCostLimit = 131_072
        return cache
    }()

    private final class CachedWidths: NSObject {
        let widths: [CGFloat]
        init(_ widths: [CGFloat]) { self.widths = widths }
    }
    /// A table's column widths, measured once per table rather than once per
    /// cell per render. Bold Text changes what the system font measures, so
    /// it is part of the key.
    private final class TableKey: NSObject {
        let table: MarkdownTable
        let boldText: Bool
        let sizeCategory: String
        init(_ table: MarkdownTable, boldText: Bool, sizeCategory: String) {
            self.table = table
            self.boldText = boldText
            self.sizeCategory = sizeCategory
        }
        override var hash: Int {
            var hasher = Hasher()
            hasher.combine(table)
            hasher.combine(boldText)
            hasher.combine(sizeCategory)
            return hasher.finalize()
        }
        override func isEqual(_ object: Any?) -> Bool {
            guard let other = object as? TableKey else { return false }
            return boldText == other.boldText && sizeCategory == other.sizeCategory && table == other.table
        }
    }
    private static let widthCache: NSCache<TableKey, CachedWidths> = {
        let cache = NSCache<TableKey, CachedWidths>()
        cache.countLimit = 64
        return cache
    }()
    /// Same split as the inline caches: a table that is still streaming is a
    /// new value on almost every update.
    private static let liveWidthCache: NSCache<TableKey, CachedWidths> = {
        let cache = NSCache<TableKey, CachedWidths>()
        cache.countLimit = 4
        return cache
    }()

    let source: String
    /// Draws a caret after the last block. The streaming bubble sets this so
    /// the live reply and the settled one are the same view with the same
    /// layout — a caret bolted on outside would put it on its own line the
    /// moment the reply ends in a list item.
    var caret: Bool = false
    /// Identifier for the first table's horizontal scroll view. Settled
    /// bubbles pass `message-<id>-scroll`. Streaming and file preview pass nil.
    var scrollIdentifier: String? = nil
    var openLink: ((URL) -> OpenURLAction.Result)?
    private var caretVisible = true
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let caretImage: Image = {
        let size = CGSize(width: 8, height: 17)
        let drawn = UIGraphicsImageRenderer(size: size).image { _ in
            UIColor.black.setFill()
            UIBezierPath(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 2).fill()
        }
        return Image(uiImage: drawn.withRenderingMode(.alwaysTemplate)).renderingMode(.template)
    }()

    init(
        source: String,
        caret: Bool = false,
        scrollIdentifier: String? = nil,
        openLink: ((URL) -> OpenURLAction.Result)? = nil
    ) {
        self.source = source
        self.caret = caret
        self.scrollIdentifier = scrollIdentifier
        self.openLink = openLink
    }

    var body: some View {
        if caret && !reduceMotion {
            TimelineView(.periodic(from: .now, by: 0.5)) { context in
                blinking(at: context.date)
            }
        } else {
            blocksView
        }
    }

    private func blinking(at date: Date) -> some View {
        var shown = self
        shown.caretVisible = Int(date.timeIntervalSinceReferenceDate / 0.5) % 2 == 0
        return shown.blocksView
    }

    @ViewBuilder private var blocksView: some View {
        // The caret marks the live bubble, whose text grows every batch: its
        // settled blocks are parsed once and only the open tail again.
        let blocks = Markdown.blocks(source, streaming: caret)
        VStack(alignment: .leading, spacing: 8) {
            let firstTable = blocks.firstIndex { if case .table = $0 { return true }; return false }
            ForEach(Array(blocks.enumerated()), id: \.offset) { item in
                view(
                    for: item.element,
                    tail: caret && item.offset == blocks.count - 1,
                    scrollIdentifier: item.offset == firstTable ? scrollIdentifier : nil
                )
            }
        }
        .environment(\.openURL, OpenURLAction { url in
            openLink?(url) ?? .systemAction(url)
        })
    }

    @ViewBuilder
    private func view(for block: MarkdownBlock, tail: Bool, scrollIdentifier: String?) -> some View {
        switch block {
        case let .paragraph(text):
            inline(text, tail: tail)
                .font(.seira(15))
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)

        case let .heading(level, text):
            // Three sizes, not six. A chat bubble is not a document, and an
            // h4 that looks exactly like body text is a heading that failed.
            inline(text, tail: tail)
                .font(.seira(level <= 1 ? 19 : level == 2 ? 17 : 15, .semibold, relativeTo: .headline))
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 2)

        case let .bullet(indent, text):
            marker("•", indent: indent, text: text, tail: tail)

        case let .ordered(indent, number, text):
            marker("\(number).", indent: indent, text: text, tail: tail)

        case let .task(indent, number, checked, text):
            taskRow(indent: indent, number: number, checked: checked, text: text, tail: tail)

        case let .table(table):
            tableView(table, tail: tail, scrollIdentifier: scrollIdentifier)

        case let .quote(text):
            HStack(alignment: .top, spacing: 8) {
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(Color.seiraChatHairline)
                    .frame(width: 3)
                inline(text, tail: tail)
                    .font(.seira(15))
                    .lineSpacing(4)
                    .foregroundStyle(Color.seiraInk2)
            }
            .fixedSize(horizontal: false, vertical: true)

        case let .code(language, text):
            VStack(alignment: .leading, spacing: 4) {
                if let language, !language.isEmpty {
                    Text(language)
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundStyle(Color.seiraInk3)
                }
                // Horizontal scroll rather than wrapping: wrapped code is
                // harder to read than code you have to push sideways, and
                // indentation is most of what a snippet is saying.
                ScrollView(.horizontal, showsIndicators: false) {
                    (Text(text) + caretText(tail))
                        .font(.system(size: 14, design: .monospaced))
                        .textSelection(.enabled)
                }
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color.seiraTint)
            )

        case .rule:
            Color.seiraChatHairline.frame(height: 1).padding(.vertical, 2)
        }
    }

    private func taskRow(indent: Int, number: Int?, checked: Bool, text: String, tail: Bool) -> some View {
        let state = String(localized: checked ? "completed" : "not completed")
        let attributed = attributedInline(text, live: tail)
        let words = String(attributed.characters)
        let label = words.isEmpty ? state : "\(state), \(words)"
        return HStack(alignment: .firstTextBaseline, spacing: 6) {
            if let number {
                Text("\(number).")
                    .font(.seira(15))
                    .foregroundStyle(Color.seiraInk2)
                    .frame(minWidth: 16, alignment: .trailing)
            }
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Image(systemName: checked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 15))
                    .foregroundStyle(Color.seiraInk2)
                inline(attributed, tail: tail).font(.seira(15)).lineSpacing(4)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
        }
        .padding(.leading, CGFloat(indent) * 14)
        .fixedSize(horizontal: false, vertical: true)
    }

    /// `tail` is set on the live reply's last block: the caret goes after its
    /// last cell, and nothing measured from it is kept with settled tables.
    private func tableView(_ table: MarkdownTable, tail: Bool, scrollIdentifier: String?) -> some View {
        let widths = columnWidths(table, live: tail)
        return ScrollView(.horizontal, showsIndicators: false) {
            Grid(alignment: .leading, horizontalSpacing: 0, verticalSpacing: 0) {
                headerRow(table, widths: widths, tail: tail, scrollIdentifier: scrollIdentifier)
                ForEach(Array(table.rows.enumerated()), id: \.offset) { rowIndex, row in
                    Color.seiraChatHairline.frame(height: 1).gridCellColumns(max(table.headers.count, 1))
                    bodyRow(
                        row, rowIndex: rowIndex, table: table, widths: widths,
                        tail: tail, scrollIdentifier: scrollIdentifier
                    )
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(Color.seiraChatHairline, lineWidth: 1)
        )
        .background(alignment: .topLeading) {
            if let scrollIdentifier {
                Color.white.opacity(0.001)
                    .frame(width: 12, height: 12)
                    .accessibilityIdentifier(scrollIdentifier)
            }
        }
    }

    private func headerRow(
        _ table: MarkdownTable, widths: [CGFloat], tail: Bool, scrollIdentifier: String?
    ) -> some View {
        GridRow {
            ForEach(Array(table.headers.enumerated()), id: \.offset) { index, header in
                cell(
                    header,
                    width: widths[index],
                    alignment: table.alignments[index],
                    isHeader: true,
                    tail: tail && table.rows.isEmpty && index == table.headers.count - 1,
                    live: tail,
                    identifier: scrollIdentifier.map { "\($0)-cell-0-\(index)" }
                )
            }
        }
    }

    private func bodyRow(
        _ row: [String], rowIndex: Int, table: MarkdownTable, widths: [CGFloat],
        tail: Bool, scrollIdentifier: String?
    ) -> some View {
        GridRow {
            ForEach(Array(row.enumerated()), id: \.offset) { index, value in
                let isLast = rowIndex == table.rows.count - 1 && index == row.count - 1
                cell(
                    value,
                    width: widths[index],
                    alignment: table.alignments[index],
                    isHeader: false,
                    tail: tail && isLast,
                    live: tail,
                    identifier: scrollIdentifier.map { "\($0)-cell-\(rowIndex + 1)-\(index)" }
                )
            }
        }
    }

    private func cell(
        _ text: String,
        width: CGFloat,
        alignment: MarkdownTableAlignment,
        isHeader: Bool,
        tail: Bool,
        live: Bool,
        identifier: String?
    ) -> some View {
        // One inline render serves both the drawn words and the spoken ones.
        let attributed = attributedInline(text, live: live)
        let font: Font = isHeader
            ? .seira(12, .medium, relativeTo: .caption)
            : .seira(13, .regular, relativeTo: .footnote)
        return Color.clear
            .frame(width: tail ? width + caretWidth : width, height: 22)
            .overlay(alignment: frameAlignment(alignment)) {
                inline(attributed, tail: tail)
                    .font(font)
                    .monospacedDigit()
                    .foregroundStyle(isHeader ? Color.seiraInk3 : Color.seiraInk)
                    .lineLimit(1)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(String(attributed.characters))
            .modifier(OptionalIdentifier(identifier: identifier))
    }

    private func frameAlignment(_ alignment: MarkdownTableAlignment) -> Alignment {
        switch alignment {
        case .leading: .leading
        case .trailing: .trailing
        case .center: .center
        }
    }

    private func columnWidths(_ table: MarkdownTable, live: Bool) -> [CGFloat] {
        let key = TableKey(
            table, boldText: UIAccessibility.isBoldTextEnabled,
            sizeCategory: UIApplication.shared.preferredContentSizeCategory.rawValue
        )
        let cache = live ? Self.liveWidthCache : Self.widthCache
        if let cached = cache.object(forKey: key) { return cached.widths }
        let widths = measuredColumnWidths(table, live: live)
        cache.setObject(CachedWidths(widths), forKey: key)
        return widths
    }

    /// Column width is the widest single-line cell, measured on the words
    /// that are actually drawn. A code span is also measured in monospace,
    /// which is wider than the proportional font.
    private func measuredColumnWidths(_ table: MarkdownTable, live: Bool) -> [CGFloat] {
        let headerFont = ChatFont.ui(12, medium: true, style: .caption1)
        let bodyFont = ChatFont.ui(13, style: .footnote)
        return table.headers.indices.map { index in
            var widest = textWidth(table.headers[index], font: headerFont, live: live)
            for row in table.rows where index < row.count {
                widest = max(widest, textWidth(row[index], font: bodyFont, live: live))
            }
            return max(widest + 10, 24)
        }
    }

    private var caretWidth: CGFloat {
        textWidth("\u{2007}", font: ChatFont.ui(13, style: .footnote), live: false) + 8
    }

    private func textWidth(_ text: String, font: UIFont, live: Bool) -> CGFloat {
        let plain = String(attributedInline(text, live: live).characters)
        var width = ceil((plain as NSString).size(withAttributes: [.font: font]).width)
        if text.contains("`") {
            let mono = UIFont.monospacedSystemFont(ofSize: font.pointSize, weight: .regular)
            width = max(width, ceil((plain as NSString).size(withAttributes: [.font: mono]).width))
        }
        return width
    }

    private func marker(_ symbol: String, indent: Int, text: String, tail: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(symbol)
                .font(.seira(15))
                .foregroundStyle(Color.seiraInk2)
                .frame(minWidth: 16, alignment: .trailing)
            inline(text, tail: tail).font(.seira(15)).lineSpacing(4)
        }
        .padding(.leading, CGFloat(indent) * 14)
        .fixedSize(horizontal: false, vertical: true)
    }

    /// Inline markdown via Foundation. `.inlineOnlyPreservingWhitespace`
    /// because the blocks are already split — asking for `.full` here would
    /// have it re-interpret list markers this has already consumed.
    ///
    /// Falling back to the raw string on a parse failure is the point: a
    /// half-typed link mid-stream should show as the characters the model has
    /// sent so far, not vanish until it closes the bracket.
    ///
    /// `tail` is only ever set on the live reply's last block, which is also
    /// the one block whose text is still changing.
    private func inline(_ text: String, tail: Bool = false) -> Text {
        inline(attributedInline(text, live: tail), tail: tail)
    }

    private func inline(_ attributed: AttributedString, tail: Bool) -> Text {
        Text(attributed) + caretText(tail)
    }

    /// The words VoiceOver should hear are this with the markers gone:
    /// `String(attributed.characters)`. The visible text still goes through
    /// Foundation so emphasis stays styled.
    private func attributedInline(_ text: String, live: Bool) -> AttributedString {
        let key = text as NSString
        if let cached = Self.inlineCache.object(forKey: key) { return cached.text }
        if live, let cached = Self.liveInlineCache.object(forKey: key) { return cached.text }
        let attributed = (try? AttributedString(
            markdown: text,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        )) ?? AttributedString(text)
        let bytes = text.utf8.count
        if bytes <= 8_192 {
            let cache = live ? Self.liveInlineCache : Self.inlineCache
            cache.setObject(CachedInline(attributed), forKey: key, cost: bytes * 4)
        }
        return attributed
    }

    /// A figure space then the caret, so it sits off the last glyph
    /// rather than touching it. Empty when not streaming — an empty `Text`
    /// concatenated in costs nothing and keeps the callers branch-free.
    private func caretText(_ tail: Bool) -> Text {
        guard tail else { return Text("") }
        let ink = caretVisible ? Color.seiraInk.opacity(0.7) : Color.clear
        return Text("\u{2007}") + Text(Self.caretImage).baselineOffset(-3).foregroundColor(ink)
    }
}
