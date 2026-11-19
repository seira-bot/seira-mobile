import SwiftUI

public struct SkillExecutionReceiptView: View {
    public let skillName: String
    public let status: String // "running", "success", "error"
    public let durationMs: Int
    public let parameters: String
    public let output: String
    /// Output that is someone's words — a teammate's report — rather than a
    /// tool's log: shown in full, in the body font, and selectable.
    public let outputIsProse: Bool
    
    @State private var isExpanded: Bool = false
    
    public init(
        skillName: String,
        status: String = "success",
        durationMs: Int = 0,
        parameters: String = "",
        output: String = "",
        outputIsProse: Bool = false
    ) {
        self.skillName = skillName
        self.status = status
        self.durationMs = durationMs
        self.parameters = parameters
        self.output = output
        self.outputIsProse = outputIsProse
    }
    
    public var body: some View {
        let hasDetails = !parameters.isEmpty || !output.isEmpty

        VStack(alignment: .leading, spacing: 6) {
            Button {
                guard hasDetails else { return }
                withSeiraAnimation(SeiraMotion.base) { isExpanded.toggle() }
                Haptics.selection()
            } label: {
                summaryRow(hasDetails: hasDetails)
            }
            .buttonStyle(.plain)
            .disabled(!hasDetails)

            if isExpanded && hasDetails {
                details
                    .transition(.opacity)
            }
        }
    }

    private func summaryRow(hasDetails: Bool) -> some View {
        HStack(spacing: 8) {
            Text(skillName)
                .font(.seira(13, .medium, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk)
                .lineLimit(1)
            statusLabel
            if durationMs > 0 {
                Text("\(durationMs)ms")
                    .font(.seira(12, .regular, relativeTo: .caption))
                    .foregroundStyle(Color.seiraInk3)
            }
            Spacer(minLength: 0)
            if hasDetails {
                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color.seiraInk3)
            }
        }
        .frame(minHeight: 28)
        .contentShape(Rectangle())
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !parameters.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Input")
                        .font(.seira(12, .medium, relativeTo: .caption))
                        .foregroundStyle(Color.seiraInk3)
                    Text(parameters)
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(Color.seiraInk)
                }
            }
            if !output.isEmpty { outputBlock }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .seiraCard(radius: 14)
    }

    private var outputBlock: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(outputIsProse ? "Report" : "Output")
                .font(.seira(12, .medium, relativeTo: .caption))
                .foregroundStyle(Color.seiraInk3)
            if outputIsProse {
                Text(verbatim: output)
                    .font(.seira(15))
                    .lineSpacing(4)
                    .foregroundStyle(Color.seiraInk)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Text(output)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Color.seiraInk)
                    .lineLimit(6)
            }
        }
    }

    private var statusColor: Color {
        switch status {
        case "success": return Color.seiraSuccess
        case "running": return Color.seiraWarning
        default: return Color.seiraDangerText
        }
    }

    private var statusText: String {
        switch status {
        case "success": return "Done"
        case "running": return "Running"
        default: return "Failed"
        }
    }

    private var statusLabel: some View {
        HStack(spacing: 5) {
            StatusDot(color: statusColor, size: 6)
            Text(statusText)
                .font(.seira(12, .regular, relativeTo: .caption))
                .foregroundStyle(Color.seiraInk3)
        }
    }
}
