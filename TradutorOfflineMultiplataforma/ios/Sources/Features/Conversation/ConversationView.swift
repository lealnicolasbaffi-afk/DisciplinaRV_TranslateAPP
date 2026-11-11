import SwiftUI
import Translation
import UniformTypeIdentifiers

@MainActor
struct ConversationView: View {
    @ObservedObject var model: ConversationModel
    @State private var importing = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            Form {
                Section("Conversa por turnos • iPhone • PT ↔ EN") {
                    NavigationLink("Traduzir com a câmera") { CameraTranslationView() }
                        .disabled(!model.canBegin)
                    Toggle("Inglês → Português", isOn: Binding(
                        get: { model.direction == .englishToPortuguese },
                        set: { model.setDirection($0 ? .englishToPortuguese : .portugueseToEnglish) }
                    )).disabled(!model.canBegin)
                    Text(model.status).accessibilityIdentifier("status")
                    if model.state.isBusy { Button("Cancelar operação") { model.cancelOperation() } }
                    Button("Preparar idiomas (com internet)") { model.prepareLanguages() }
                        .disabled(!model.canBegin)
                    Toggle("Confirmo modo avião e Wi-Fi desligado", isOn: $model.airplaneModeConfirmed)
                        .disabled(!model.canBegin)
                    if let warning = model.persistenceWarning {
                        Text(warning).foregroundStyle(.red).accessibilityIdentifier("persistenceWarning")
                    }
                }
                Section("1. Falar") {
                    Button(model.isRecording ? "Parar e traduzir" : "Gravar fala") {
                        if model.isRecording { model.stopRecording() }
                        else { Task { await model.startRecording() } }
                    }.disabled(!model.canBegin && !model.isRecording)
                    Button("Ouvir gravação") { model.replayOriginal() }
                        .disabled(!model.canReplayOriginal)
                    Button("Importar áudio de reserva") { importing = true }
                        .disabled(!model.canBegin)
                }
                Section("2. Transcrição • pode editar") {
                    TextEditor(text: Binding(get: { model.input }, set: { model.updateInput($0) }))
                        .frame(minHeight: 90).disabled(!model.canBegin)
                        .accessibilityLabel("Transcrição editável")
                    Button("Traduzir texto revisado") { model.requestTranslation() }
                        .disabled(!model.canTranslate)
                    Button("Nova rodada (texto digitado)") { model.newTypedRound() }
                        .disabled(!model.canBegin)
                    Text("Corrigir mantém a origem e o instante da fala. Use Nova rodada para digitar outra frase.")
                        .font(.caption)
                }
                Section("3. Tradução") {
                    Text(model.output.isEmpty ? "A tradução aparecerá aqui." : model.output)
                        .textSelection(.enabled)
                    Button("Reproduzir tradução") { model.replayTranslation() }.disabled(!model.canSpeak)
                    Text("Tradução automática: revise antes de usar.").font(.caption)
                }
                measurements
            }
            .navigationTitle("Tradutor Offline")
            .background {
                if let request = model.translationRequest {
                    TranslationTaskHost(model: model, request: request).id(request.id)
                }
            }
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

    private var measurements: some View {
        Section("Medições • \(model.trials.count) tentativas") {
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
