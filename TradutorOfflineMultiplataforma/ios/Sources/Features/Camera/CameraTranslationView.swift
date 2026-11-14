import SwiftUI
import Translation
import UIKit

@MainActor
struct CameraTranslationView: View {
    @StateObject private var model = CameraTranslationModel()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 16) {
                ActionSymbol(name: "arrow.left", label: "Voltar para conversa", dark: true) { dismiss() }
                Text("Traduzir").font(.title2.weight(.semibold)).foregroundStyle(.white)
                Spacer()
            }.padding(.horizontal, 16)
            Button { model.swapDirection() } label: {
                HStack(spacing: 20) {
                    Text(model.direction == .englishToPortuguese ? "Inglês" : "Português")
                    Image(systemName: "arrow.left.arrow.right")
                    Text(model.direction == .englishToPortuguese ? "Português" : "Inglês")
                }
                .font(.headline).foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))
            }
            .accessibilityLabel("\(model.direction.rawValue). Inverter idiomas")
            .padding(.horizontal, 16)
            CameraSurface(image: model.image, regions: model.regions, translations: model.translations,
                          frozen: model.frozen, focus: model.focus, zoom: model.zoom)
                .frame(maxWidth: .infinity, maxHeight: .infinity).clipped()
            Text(model.status).font(.subheadline).foregroundStyle(.white.opacity(0.8))
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(minHeight: 46, alignment: .topLeading)
                .padding(.horizontal, 16)
            HStack(spacing: 16) {
                if model.permissionDenied {
                    ActionSymbol(name: "gearshape", label: "Abrir Ajustes para permitir câmera", dark: true) {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                } else {
                    ActionSymbol(name: model.ready ? "checkmark" : "arrow.down.to.line",
                                 label: model.ready ? "Idiomas disponíveis offline" : "Baixar idiomas com internet", dark: true) { model.prepare() }
                        .disabled(model.ready || model.request != nil)
                }
                ActionSymbol(name: model.frozen ? "play.fill" : "pause.fill",
                             label: model.frozen ? "Retomar câmera ao vivo" : "Congelar imagem para ler",
                             primary: true, size: 64) { model.toggleFreeze() }
                    .disabled(model.image == nil)
                Color.clear.frame(width: 52, height: 1).accessibilityHidden(true)
            }.padding(.bottom, 12)
        }
        .padding(.top, 8)
        .background(AppStyle.night)
        .preferredColorScheme(.dark)
        .background {
            if let request = model.request { CameraTranslationHost(model: model, request: request).id(request.id) }
        }
        .task { await model.start() }
        .onDisappear { model.stop() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await model.start() } }
            else if phase == .background { model.stop() }
        }
    }
}

@MainActor
private struct CameraTranslationHost: View {
    let model: CameraTranslationModel
    let request: CameraTranslationRequest
    var body: some View {
        Color.clear.frame(width: 1, height: 1).accessibilityHidden(true)
            .translationTask(.init(source: Locale.Language(identifier: request.direction.sourceLanguageIdentifier),
                                   target: Locale.Language(identifier: request.direction.targetLanguageIdentifier))) { session in
                await model.perform(using: session, request: request)
            }
    }
}
