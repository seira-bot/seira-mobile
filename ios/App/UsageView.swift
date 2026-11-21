import CompanionCore
import SwiftUI

/// How much of each AI plan is left, as the computer reports it. Only plans
/// that report usage appear; API keys and local models report none.
struct UsageView: View {
    @EnvironmentObject private var session: Session
    @State private var report: EngineLimitsReport?
    @State private var phase: Phase
    @State private var checking = false

    private enum Phase: Equatable {
        case loading
        case loaded
        case unavailable
        case failed
    }

    init(initial: EngineLimitsReport? = nil) {
        _report = State(initialValue: initial)
        _phase = State(initialValue: initial == nil ? .loading : .loaded)
    }

    private var reporting: [EngineLimit] {
        EngineUsage.reporting(report?.engines ?? [])
    }

    var body: some View {
        List {
            switch phase {
            case .loading:
                Section { HStack { Spacer(); ProgressView(); Spacer() } }
            case .unavailable:
                Section {
                    EmptyStateView(
                        String(localized: "Usage is not available"),
                        systemImage: "chart.bar",
                        description: Text("Your computer does not share plan usage with this phone.")
                    )
                }
            case .failed:
                failedSection
                loadedSections
            case .loaded:
                loadedSections
            }
        }
        .navigationTitle("AI usage")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Check now", systemImage: "arrow.clockwise") {
                    Task { await load(refresh: true) }
                }
                .disabled(checking || phase == .unavailable)
            }
        }
        .task { await load(refresh: false) }
    }

    @ViewBuilder private var failedSection: some View {
        Section {
            Label("Usage could not be read.", systemImage: "exclamationmark.triangle")
                .foregroundStyle(Color.seiraWarningText)
            Button("Try again") { Task { await load(refresh: false) } }
        }
    }

    @ViewBuilder private var loadedSections: some View {
        if report != nil, reporting.isEmpty {
            Section {
                EmptyStateView(
                    String(localized: "No usage to show"),
                    systemImage: "chart.bar",
                    description: Text("Plans such as Claude and Codex report usage here. API keys and local models do not.")
                )
            }
        }
        ForEach(reporting) { limit in
            engineSection(limit)
        }
        if let checked = EngineUsage.date(fromMilliseconds: report?.checkedAt), checked.timeIntervalSince1970 > 0 {
            Section {
                Text("Checked \(limitResetTime(checked))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
            }
        }
    }

    private func engineSection(_ limit: EngineLimit) -> some View {
        Section {
            ForEach(Array(usageRows(limit).enumerated()), id: \.offset) { _, row in
                usageRow(row)
            }
        } header: {
            Text(verbatim: limit.name)
        }
    }

    private func usageRow(_ row: UsageRow) -> some View {
        let color = Self.color(for: row.tone)
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Self.windowTitle(row.window)
                Spacer()
                if row.tone == .out {
                    Text("At limit").foregroundStyle(color)
                } else {
                    Text("\(row.left)% left").foregroundStyle(color)
                }
            }
            ProgressView(value: Double(row.left), total: 100)
                .tint(color)
            if let reset = row.resetsAt {
                Text("Resets \(limitResetTime(reset))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }

    private static func windowTitle(_ window: String) -> Text {
        switch window {
        case "5-hour": return Text("5-hour limit")
        case "weekly": return Text("Weekly limit")
        default: return Text("Usage")
        }
    }

    static func color(for tone: UsageTone) -> Color {
        switch tone {
        case .fine: return Color.seiraAccent
        case .near: return Color.seiraWarningText
        case .out: return Color.seiraDangerText
        }
    }

    private func load(refresh: Bool) async {
        guard !checking else { return }
        checking = true
        defer { checking = false }
        switch await session.loadEngineLimits(refresh: refresh) {
        case let .report(loaded):
            report = loaded
            phase = .loaded
        case .unavailable:
            phase = .unavailable
        case .failed:
            phase = .failed
        }
    }
}
