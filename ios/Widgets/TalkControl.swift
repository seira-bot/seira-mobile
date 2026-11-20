import AppIntents
import SwiftUI
import WidgetKit

@available(iOS 18.0, *)
struct TalkControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "bot.seira.app.talk-control") {
            ControlWidgetButton(action: OpenURLIntent(URL(string: "seira://talk")!)) {
                Label("Talk to Seira", systemImage: "waveform")
            }
        }
        .displayName("Talk to Seira")
    }
}
