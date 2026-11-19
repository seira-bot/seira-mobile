import SwiftUI
import WidgetKit

struct TalkEntry: TimelineEntry {
    let date: Date
}

struct TalkProvider: TimelineProvider {
    func placeholder(in context: Context) -> TalkEntry { TalkEntry(date: Date()) }

    func getSnapshot(in context: Context, completion: @escaping (TalkEntry) -> Void) {
        completion(TalkEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TalkEntry>) -> Void) {
        completion(Timeline(entries: [TalkEntry(date: Date())], policy: .never))
    }
}

struct TalkWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TalkWidget", provider: TalkProvider()) { _ in
            TalkWidgetView()
        }
        .configurationDisplayName("Talk to Seira")
        .description("Start a Live call with your Chief of Staff.")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryRectangular])
    }
}

private struct TalkWidgetView: View {
    @Environment(\.widgetFamily) private var family

    var body: some View {
        content
            .widgetURL(URL(string: "seira://talk"))
    }

    @ViewBuilder
    private var content: some View {
        switch family {
        case .accessoryCircular:
            Image(systemName: "waveform")
                .font(.system(size: 22, weight: .semibold))
                .widgetAccessoryBackground()
        case .accessoryRectangular:
            HStack(spacing: 8) {
                Image(systemName: "waveform")
                    .font(.system(size: 18, weight: .semibold))
                Text("Talk to Seira")
                    .font(.system(size: 15, weight: .semibold))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .widgetAccessoryBackground()
        default:
            small
        }
    }

    private var small: some View {
        VStack(spacing: 10) {
            Image(systemName: "waveform")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.white)
                .frame(width: 48, height: 48)
                .background(Circle().fill(Color.black))
            Text("Talk to Seira")
                .font(.system(size: 14, weight: .semibold))
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .widgetContainerBackground()
    }
}
