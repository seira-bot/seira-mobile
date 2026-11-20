import SwiftUI

/// The person, not a bot: the roster header and the settings row. A letter
/// rather than a character, because the character means "this is a bot".
struct ProfileAvatar: View {
    let name: String
    var size: CGFloat = 34

    var body: some View {
        Circle()
            .fill(SeiraPalette.color("green"))
            .frame(width: size, height: size)
            .overlay {
                Text(initial)
                    .font(.system(size: size * 0.45, weight: .semibold))
                    .foregroundStyle(.white)
            }
    }

    private var initial: String {
        String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased()
    }
}
