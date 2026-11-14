import SwiftUI

/// Native symbols stay centered independently of text metrics, with 52 pt touch targets.
enum AppStyle {
    static let accent = Color(red: 0.06, green: 0.42, blue: 0.36)
    static let background = Color(red: 0.95, green: 0.96, blue: 0.96)
    static let pale = Color(red: 0.88, green: 0.94, blue: 0.92)
    static let night = Color(red: 0.06, green: 0.11, blue: 0.11)
}

struct ActionSymbol: View {
    let name: String
    let label: String
    var primary = false
    var dark = false
    var size: CGFloat = 52
    let action: () -> Void
    @Environment(\.isEnabled) private var enabled

    var body: some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: 22, weight: .medium))
                .frame(width: size, height: size, alignment: .center)
                .foregroundStyle((primary || dark ? Color.white : AppStyle.accent).opacity(enabled ? 1 : 0.38))
                .background(primary ? AppStyle.accent : (dark ? Color.white.opacity(0.10) : AppStyle.pale),
                            in: RoundedRectangle(cornerRadius: primary ? size / 2 : 16))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .help(label)
    }
}

extension View {
    func translationCard() -> some View {
        padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(.white, in: RoundedRectangle(cornerRadius: 22))
    }
}
