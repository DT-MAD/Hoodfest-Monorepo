//
//  Components.swift
//  PitStop-iOS
//
//  The shared pieces every screen is built from.
//

import SwiftUI

// MARK: - Buttons

/// The primary action on a screen. Deliberately enormous: this is a shared
/// tablet, used at arm's length, often by a child, often in a hurry.
struct PitStopButton: View {
    enum Kind {
        case primary, secondary, quiet

        var background: Color {
            switch self {
            case .primary: Theme.racingYellow
            case .secondary: Theme.slateHigh
            case .quiet: .clear
            }
        }

        var foreground: Color {
            switch self {
            case .primary: Theme.asphalt
            case .secondary, .quiet: Theme.ink
            }
        }
    }

    let title: String
    var systemImage: String?
    var kind: Kind = .primary
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(Theme.buttonLabel())
                }
                Text(title)
                    .font(Theme.buttonLabel())
            }
            .frame(maxWidth: .infinity, minHeight: Theme.tapTargetHeight)
            .foregroundStyle(kind.foreground)
            .background(kind.background, in: RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                    .strokeBorder(kind == .quiet ? Theme.hairline : .clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .contentShape(Rectangle())
    }
}

// MARK: - Headers

/// The title block at the top of a game or result screen.
struct ScreenHeader: View {
    let title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(Theme.screenTitle())
                .foregroundStyle(Theme.ink)

            if let subtitle {
                Text(subtitle)
                    .font(Theme.body())
                    .foregroundStyle(Theme.inkMuted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// The checkered flag mark used in the app's chrome.
struct CheckeredFlag: View {
    var size: CGFloat = 44
    var squares: Int = 4

    var body: some View {
        let side = size / CGFloat(squares)

        return ZStack(alignment: .topLeading) {
            Theme.ink
            ForEach(0..<squares, id: \.self) { row in
                ForEach(0..<squares, id: \.self) { column in
                    if (row + column).isMultiple(of: 2) {
                        Rectangle()
                            .fill(Theme.asphalt)
                            .frame(width: side, height: side)
                            .offset(x: CGFloat(column) * side, y: CGFloat(row) * side)
                    }
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .accessibilityHidden(true)
    }
}

// MARK: - Score display

/// The large number on a result screen.
struct ScoreDisplay: View {
    let value: String
    let caption: String
    var tint: Color = Theme.racingYellow

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(Theme.scoreDisplay())
                .foregroundStyle(tint)
                .minimumScaleFactor(0.5)
                .lineLimit(1)

            Text(caption.uppercased())
                .font(Theme.caption())
                .tracking(1.4)
                .foregroundStyle(Theme.inkMuted)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(caption): \(value)")
    }
}

// MARK: - Status

/// A labelled state pill. State is never carried by color alone — every pill
/// has an icon and a word as well.
struct StatusPill: View {
    let text: String
    let systemImage: String
    var tint: Color = Theme.inkMuted

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage)
            Text(text)
        }
        .font(Theme.caption())
        .foregroundStyle(tint)
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(Theme.slateHigh, in: Capsule())
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Layout

/// A full-screen touch surface that reports touch-DOWN, not touch-up.
///
/// This matters more than it looks. `onTapGesture` fires when the finger
/// lifts, which would add the player's lift time to every reaction score. A
/// zero-distance drag gesture fires the instant the finger lands.
struct TouchDownArea<Content: View>: View {
    let onTouchDown: () -> Void
    @ViewBuilder var content: Content

    @State private var isPressed = false

    var body: some View {
        content
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard !isPressed else { return }
                        isPressed = true
                        onTouchDown()
                    }
                    .onEnded { _ in isPressed = false }
            )
    }
}
