import SwiftUI
import Translation
import UIKit
import UniformTypeIdentifiers

@MainActor
struct ConversationView: View {
    @ObservedObject var model: ConversationModel
    @State private var importing = false
    @State private var showingSettings = false
    @State private var showingMeasurements = false
    @State private var showingCamera = false
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    HStack(spacing: 8) {
                        Text("Tradutor Offline").font(.title2.weight(.semibold))
                            .frame(maxWidth: .infinity, alignment: .leading)
                        ActionSymbol(name: "camera", label: "Traduzir com a câmera") { showingCamera = true }
                            .disabled(!model.canBegin)
                        ActionSymbol(name: "slider.horizontal.3", label: "Configurar idiomas e vozes") { showingSettings = true }
                    }
                    Text("Voz, texto e câmera").font(.subheadline).foregroundStyle(.secondary)
                    Button {
                        model.setDirection(model.direction == .englishToPortuguese ? .portugueseToEnglish : .englishToPortuguese)
                    } label: {
                        HStack {
                            Text(model.direction == .englishToPortuguese ? "Inglês" : "Português")
                            Spacer(); Image(systemName: "arrow.left.arrow.right"); Spacer()
                            Text(model.direction == .englishToPortuguese ? "Português" : "Inglês")
                        }.font(.headline).translationCard()
                    }
                    .buttonStyle(.plain).disabled(!model.canBegin)
                    .accessibilityLabel("\(model.direction.rawValue). Inverter idiomas")
                    Text(model.status).font(.subheadline).foregroundStyle(.secondary)
                        .accessibilityIdentifier("status")
                    if let warning = model.persistenceWarning {
                        Text(warning).foregroundStyle(.red).accessibilityIdentifier("persistenceWarning")
                    }
                    if model.state.isBusy && !model.isRecording {
                        Button("Cancelar operação") { model.cancelOperation() }
                    }
                    Text("Sua mensagem").font(.title3.weight(.semibold))
                    ZStack(alignment: .topLeading) {
                        TextEditor(text: Binding(get: { model.input }, set: { model.updateInput($0) }))
                            .font(.body).scrollContentBackground(.hidden)
                            .frame(minHeight: 100).disabled(!model.canBegin)
                            .accessibilityLabel("Texto original editável")
                        if model.input.isEmpty {
                            Text("Digite ou toque no microfone").foregroundStyle(.secondary)
                                .padding(.top, 8).padding(.leading, 5).allowsHitTesting(false)
                                .accessibilityHidden(true)
                        }
                    }.translationCard()
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { inputActions; translateButton }
                        VStack(alignment: .leading, spacing: 12) {
                            HStack(spacing: 8) { inputActions }
                            translateButton
                        }
                    }
                    Text("Tradução").font(.title3.weight(.semibold))
                    Text(model.output.isEmpty ? "A tradução aparecerá aqui." : model.output)
                        .font(.title3).textSelection(.enabled)
                        .frame(minHeight: 90, alignment: .topLeading).translationCard()
                    HStack(spacing: 8) {
                        ActionSymbol(name: "speaker.wave.2", label: "Ouvir tradução") { model.replayTranslation() }
                            .disabled(!model.canSpeak)
                        ActionSymbol(name: "plus", label: "Nova rodada de texto") { model.newTypedRound() }
                            .disabled(!model.canBegin)
                    }
                    Button { showingMeasurements = true } label: {
                        Label("Avaliação · \(model.trials.count)", systemImage: "chart.bar")
                            .frame(maxWidth: .infinity, minHeight: 48)
                    }.buttonStyle(.bordered)
                }.padding(20)
            }
            .background(AppStyle.background)
            .preferredColorScheme(.light)
            .tint(AppStyle.accent)
            .toolbar(.hidden, for: .navigationBar)
            .background {
                if let request = model.translationRequest {
                    TranslationTaskHost(model: model, request: request).id(request.id)
                }
            }
            .sheet(isPresented: $showingSettings) { settings }
            .sheet(isPresented: $showingMeasurements) {
                NavigationStack {
                    Form { measurements }
                        .navigationTitle("Avaliação")
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fechar") { showingMeasurements = false } } }
                }.tint(AppStyle.accent)
            }
            .fullScreenCover(isPresented: $showingCamera) { CameraTranslationView() }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.audio]) { result in
                switch result {
                case .success(let url): Task { await model.importAudio(url) }
                case .failure(let error):
                    model.reportImportFailure("Falha ao escolher áudio: \(error.localizedDescription). Salve um arquivo de até 20 segundos localmente e tente novamente.")
                }
            }
            .onChange(of: scenePhase) { _, phase in
                if phase == .background { model.didEnterBackground() }
            }
        }
    }

    @ViewBuilder private var inputActions: some View {
        ActionSymbol(name: model.isRecording ? "stop.fill" : "mic",
                     label: model.isRecording ? "Parar e traduzir" : "Gravar fala, até 20 segundos", primary: true) {
            if model.isRecording { model.stopRecording() }
            else { Task { await model.startRecording() } }
        }.disabled(!model.canBegin && !model.isRecording)
        ActionSymbol(name: "folder", label: "Importar áudio") { importing = true }.disabled(!model.canBegin)
        ActionSymbol(name: "speaker.wave.2", label: "Ouvir original") { model.replayOriginal() }.disabled(!model.canReplayOriginal)
    }
    private var translateButton: some View {
        Button("Traduzir") { model.requestTranslation() }
            .font(.headline).frame(minWidth: 90, minHeight: 52)
            .buttonStyle(.borderedProminent).disabled(!model.canTranslate)
    }
    private var settings: some View {
        NavigationStack {
            Form {
                Section("Idiomas e vozes") {
                    Text(model.direction.rawValue)
                    Button("Preparar idiomas (com internet)") { model.prepareLanguages() }.disabled(!model.canBegin)
                    Text(model.status)
                    if model.state.isBusy { Button("Cancelar operação") { model.cancelOperation() } }
                    Text("Prepare o par de tradução antes do modo avião. Reconhecimento de fala e vozes dependem dos recursos disponíveis no iOS; confirme ambos os sentidos sem rede no aparelho.").font(.footnote)
                    Button("Abrir Ajustes do aplicativo") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                }
                Section("Como usar") {
                    Text("O microfone grava por até 20 segundos. Parar inicia transcrição, tradução e voz. Editar mantém a origem da rodada; use + para digitar uma nova frase.")
                    Text("Na câmera, toque para focar, use dois dedos para ampliar e pause para ler uma captura. A tradução acompanha regiões 2D, sem âncora 3D persistente.")
                }
            }
            .navigationTitle("Idiomas e vozes")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fechar") { showingSettings = false } } }
        }.tint(AppStyle.accent)
    }

    private var measurements: some View {
        Section("Medições • \(model.trials.count) tentativas") {
            Toggle("Confirmo modo avião e Wi-Fi desligado", isOn: $model.airplaneModeConfirmed).disabled(!model.canBegin)
            if let last = model.latestTrial {
                Text("Origem: \(last.origin.rawValue) • \(last.edited ? "corrigida" : "sem correção")")
                Text("Texto: \(milliseconds(last.textMS)) • Início da voz: \(milliseconds(last.audioStartMS))")
                Text("Transcrição: \(milliseconds(last.transcriptionMS)) • Tradução: \(milliseconds(last.translationMS))")
                Text("Desde Parar; inclui revisão manual. Arquivos usam fim da importação como referência.")
                    .font(.caption)
                Text("Original reconhecido: \(last.rawInput.isEmpty ? "—" : last.rawInput)")
                    .textSelection(.enabled)
                if let failure = last.failure { Text("Falha: \(failure)").foregroundStyle(.red) }
                Picker("Transcrição", selection: $model.transcriptionQuality) {
                    ForEach(Quality.allCases) { quality in Text(quality.rawValue).tag(quality) }
                }.disabled(!model.canBegin)
                Picker("Tradução", selection: $model.translationQuality) {
                    ForEach(Quality.allCases) { quality in Text(quality.rawValue).tag(quality) }
                }.disabled(!model.canBegin)
                TextField("ID da frase, ruído, falhas, observações", text: $model.notes, axis: .vertical)
                    .disabled(!model.canBegin)
                Button("Salvar avaliação") { model.saveAssessment() }.disabled(!model.canBegin)
            }
            if let url = model.exportURL {
                ShareLink("Exportar medições JSON", item: url).disabled(!model.canBegin)
            }
        }
    }

    private func milliseconds(_ value: Double?) -> String {
        value.map { String(format: "%.0f ms", $0) } ?? "—"
    }
}

/// Keeping the host identity tied to one request prevents session reuse across
/// directions/attempts. TranslationSession is never stored in the model.
@MainActor
private struct TranslationTaskHost: View {
    let model: ConversationModel
    let request: TranslationRequest

    var body: some View {
        Color.clear.frame(width: 1, height: 1).accessibilityHidden(true)
            .translationTask(.init(
                source: Locale.Language(identifier: request.direction.sourceLanguageIdentifier),
                target: Locale.Language(identifier: request.direction.targetLanguageIdentifier)
            )) { session in
                await model.performTranslation(using: session, request: request)
            }
    }
}
