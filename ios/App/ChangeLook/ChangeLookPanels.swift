import CompanionCore
import SwiftUI

struct ChangeLookPanel: View {
    @ObservedObject var model: ChangeLookModel

    var body: some View {
        switch model.category {
        case .hair: ChangeLookHairPanel(model: model)
        case .colour: ChangeLookColourPanel(model: model)
        case .face: ChangeLookFacePanel(model: model)
        case .extras: ChangeLookExtrasPanel(model: model)
        }
    }
}

struct ChangeLookHairPanel: View {
    @ObservedObject var model: ChangeLookModel

    var body: some View {
        LazyVGrid(columns: ChangeLookMetrics.threeColumns, spacing: 14) {
            ForEach(SeiraLookCatalog.hairIds, id: \.self) { id in
                ChangeLookTile(
                    title: Text(ChangeLookLabels.hair(id)),
                    selected: model.look.hair == id,
                    action: { model.setHair(id) }
                ) {
                    SeiraAvatarHead(look: tileLook(id), size: 76, mood: .idle)
                }
            }
        }
    }

    private func tileLook(_ id: String) -> SeiraLook {
        var look = model.look
        look.hair = id
        return look
    }
}

struct ChangeLookColourPanel: View {
    @ObservedObject var model: ChangeLookModel

    private let columns = [GridItem(.adaptive(minimum: 52), spacing: 8)]

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
            ForEach(SeiraLookCatalog.starterColors, id: \.self) { name in
                swatch(name)
            }
        }
    }

    private func swatch(_ name: String) -> some View {
        let selected = model.look.color == name
        let hex = SeiraLookCatalog.colorHex[name]
        let ring: Color = selected ? .seiraInk : .clear
        return Button { model.setColor(name) } label: {
            Circle()
                .fill(SeiraIdentity.color(hex: hex))
                .frame(width: 44, height: 44)
                .overlay(Circle().inset(by: -4).stroke(ring, lineWidth: 2))
                .frame(width: 52, height: 52)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(ChangeLookLabels.color(name)))
        .accessibilityAddTraits(selected ? .isSelected : [])
        .feedback(.selection, trigger: selected)
    }
}

struct ChangeLookFacePanel: View {
    @ObservedObject var model: ChangeLookModel

    var body: some View {
        LazyVGrid(columns: ChangeLookMetrics.threeColumns, spacing: 14) {
            ForEach(SeiraLookCatalog.faceExpressions, id: \.self) { id in
                ChangeLookTile(
                    title: Text(ChangeLookLabels.expression(id)),
                    selected: model.isExpressionSelected(id),
                    action: { model.setExpression(id) }
                ) {
                    SeiraAvatarHead(look: tileLook(id), size: 76, mood: CharacterMood.normalize(id) ?? .idle)
                }
            }
        }
    }

    private func tileLook(_ id: String) -> SeiraLook {
        var look = model.look
        look.expression = id
        return look
    }
}

struct ChangeLookExtrasPanel: View {
    @ObservedObject var model: ChangeLookModel
    private let columns = [GridItem(.adaptive(minimum: 80), spacing: 12)]

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            counter
            ForEach(SeiraLookSlot.allCases, id: \.self) { slot in
                section(slot)
            }
        }
    }

    private var counter: some View {
        let count = model.look.accessories.count
        return Text("\(count) of 3")
            .font(.seira(13, .medium, relativeTo: .subheadline))
            .foregroundStyle(Color.seiraInk2)
            .accessibilityLabel(Text("\(count) extras on"))
    }

    private func ids(in slot: SeiraLookSlot) -> [String] {
        SeiraLookCatalog.accessoryIds.filter { SeiraLookCatalog.slot(of: $0) == slot }
    }

    private func section(_ slot: SeiraLookSlot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(ChangeLookLabels.slot(slot))
                .font(.seira(13, .medium, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk2)
                .accessibilityAddTraits(.isHeader)
            LazyVGrid(columns: columns, alignment: .leading, spacing: 12) {
                ForEach(ids(in: slot), id: \.self) { id in
                    extra(id)
                }
            }
        }
    }

    private func extra(_ id: String) -> some View {
        let on = model.look.accessories.contains(id)
        return ChangeLookTile(
            title: Text(ChangeLookLabels.accessory(id)),
            selected: on,
            height: 80,
            radius: 18,
            captionSize: 12,
            action: { model.toggleExtra(id) }
        ) {
            ZStack(alignment: .topTrailing) {
                sticker(id)
                if on { badge }
            }
        }
    }

    @ViewBuilder private func sticker(_ id: String) -> some View {
        if let image = SeiraKit.image("accessories/\(id).webp", maxPixel: 160) {
            Image(decorative: image, scale: 2)
                .resizable()
                .scaledToFit()
                .padding(14)
        }
    }

    private var badge: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(Color.seiraOnInverse)
            .frame(width: 20, height: 20)
            .background(Color.seiraInverse, in: Circle())
            .padding(6)
    }
}
