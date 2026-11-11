import AVFoundation
import Foundation
import Translation

@MainActor
final class CameraTranslationModel: ObservableObject {
    struct Request: Identifiable {
        enum Kind { case prepare, translate(String) }
        let id = UUID()
        let direction: Direction
        let kind: Kind
    }

    let capture = CameraCapture()
    @Published private(set) var direction: Direction = .englishToPortuguese
    @Published private(set) var rows: [CameraText] = []
    @Published private(set) var translations: [String: String] = [:]
    @Published private(set) var status = "Autorize a câmera e prepare a tradução."
    @Published private(set) var request: Request?
    @Published private(set) var ready = false
    private let translator = AppleTranslationService()
    private var active = false
    private var generation = 0
    private var captureGeneration = 0
    private var cacheOrder: [String] = []

    init() {
        capture.onText = { [weak self] rows, token in
            guard let self = self, self.active, token == self.captureGeneration else { return }
            self.rows = rows
            self.requestNext()
        }
        capture.onError = { [weak self] message, token in
            guard let self = self, self.active, token == self.captureGeneration else { return }
            self.rows = []
            self.status = message
        }
    }

    func start() async {
        guard !active else { return }
        active = true
        generation += 1
        captureGeneration += 1
        let token = captureGeneration
        let allowed: Bool
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: allowed = true
        case .notDetermined: allowed = await AVCaptureDevice.requestAccess(for: .video)
        default: allowed = false
        }
        guard active, token == captureGeneration else { return }
        guard allowed else {
            status = "Câmera não autorizada. Abra Ajustes → Tradutor Offline → Câmera."
            return
        }
        status = ready ? "Aponte para um texto bem iluminado." : "Toque em Preparar tradução; use internet se os idiomas ainda não estiverem instalados."
        capture.start(token: token)
    }

    func stop() {
        active = false
        generation += 1
        captureGeneration += 1
        request = nil
        rows = []
        capture.stop()
    }

    func switchDirection() {
        generation += 1
        request = nil
        direction = direction == .englishToPortuguese ? .portugueseToEnglish : .englishToPortuguese
        rows = []
        translations = [:]
        cacheOrder = []
        ready = false
        status = "Toque em Preparar tradução para confirmar os idiomas nesta direção."
    }

    func prepare() {
        guard active, request == nil else { return }
        status = "Verificando e preparando os idiomas…"
        request = Request(direction: direction, kind: .prepare)
    }

    private func requestNext() {
        guard active, ready, request == nil,
              let text = rows.first(where: { translations[$0.text] == nil })?.text else { return }
        request = Request(direction: direction, kind: .translate(text))
    }

    /// The session is scoped to the SwiftUI translation task and never retained.
    func perform(_ pending: Request, using session: TranslationSession) async {
        let token = generation
        guard active, request?.id == pending.id else { return }
        do {
            switch pending.kind {
            case .prepare:
                try await translator.prepare(using: session, direction: pending.direction)
                guard active, token == generation, request?.id == pending.id else { return }
                ready = true
                status = "Tradução preparada. Aponte para um texto bem iluminado."
            case .translate(let text):
                let result = try await translator.translate(text, using: session, direction: pending.direction)
                guard active, token == generation, request?.id == pending.id else { return }
                // Never attach an old result to a different current observation.
                if rows.contains(where: { $0.text == text }) {
                    translations[text] = result.text
                    cacheOrder.append(text)
                    if cacheOrder.count > 128 { translations.removeValue(forKey: cacheOrder.removeFirst()) }
                }
            }
            request = nil
            requestNext()
        } catch is CancellationError {
            if active, token == generation, request?.id == pending.id { request = nil }
        } catch {
            guard active, token == generation, request?.id == pending.id else { return }
            ready = false
            request = nil
            status = error.localizedDescription
        }
    }
}
