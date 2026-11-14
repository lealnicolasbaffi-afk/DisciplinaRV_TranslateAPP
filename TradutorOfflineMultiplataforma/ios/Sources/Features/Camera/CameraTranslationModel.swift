import AVFoundation
import Combine
import Translation
import UIKit

struct CameraTranslationRequest: Identifiable {
    enum Kind { case prepare, texts([String]) }
    let id = UUID()
    let direction: Direction
    let kind: Kind
}

@MainActor
final class CameraTranslationModel: ObservableObject {
    @Published private(set) var image: CGImage?
    @Published private(set) var regions: [CameraRegion] = []
    @Published private(set) var translations: [String: String] = [:]
    @Published private(set) var frozen = false
    @Published private(set) var direction: Direction = .englishToPortuguese
    @Published private(set) var ready = false
    @Published private(set) var permissionDenied = false
    @Published private(set) var status = "Aponte para um texto bem iluminado."
    @Published private(set) var request: CameraTranslationRequest?
    private let translator = AppleTranslationService()
    private let tracker = CameraRegionTracker()
    private var cacheOrder: [String] = []
    private var active = false
    private var runID = UUID()
    private var generation = UUID()
    private var expiry: Task<Void, Never>?
    private var requestWatchdog: Task<Void, Never>?
    private var foregroundIdleTimerDisabled: Bool?
    private lazy var capture = CameraCapture(onFrame: { [weak self] id, image, observations in
        guard let self, self.active, self.runID == id, !self.frozen else { return }
        if observations == nil { self.image = image }
        if let observations {
            self.regions = self.tracker.update(observations, now: ProcessInfo.processInfo.systemUptime)
            self.scheduleTranslation()
        }
    }, onError: { [weak self] id, message in
        guard let self, self.active, self.runID == id, !self.frozen else { return }
        self.status = message
    })

    func start() async {
        guard !active else { return }
        active = true; runID = UUID(); generation = UUID()
        let token = runID
        foregroundIdleTimerDisabled = UIApplication.shared.isIdleTimerDisabled
        UIApplication.shared.isIdleTimerDisabled = true
        let allowed: Bool
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: allowed = true
        case .notDetermined: allowed = await AVCaptureDevice.requestAccess(for: .video)
        default: allowed = false
        }
        guard active, runID == token else { return }
        permissionDenied = !allowed
        guard allowed else {
            status = "Autorize a câmera em Ajustes → Tradutor Offline."
            return
        }
        capture.start(id: token)
        expiry = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(50))
                guard !Task.isCancelled, let self, self.active else { return }
                if !self.frozen { self.regions = self.tracker.snapshot(now: ProcessInfo.processInfo.systemUptime) }
            }
        }
        await checkReadiness()
    }
    func stop() {
        active = false; runID = UUID(); generation = UUID(); requestWatchdog?.cancel(); request = nil
        expiry?.cancel(); expiry = nil
        capture.stop(); tracker.clear(); regions = []; image = nil; frozen = false
        translations = [:]; cacheOrder = []
        if let previous = foregroundIdleTimerDisabled { UIApplication.shared.isIdleTimerDisabled = previous }
        foregroundIdleTimerDisabled = nil
    }
    func checkReadiness() async {
        let token = generation
        let available = await LanguageAvailability().status(
            from: Locale.Language(identifier: direction.sourceLanguageIdentifier),
            to: Locale.Language(identifier: direction.targetLanguageIdentifier))
        guard active, generation == token else { return }
        ready = available == .installed
        status = ready ? (frozen ? "Imagem congelada · tradução sobre a captura." : "Ao vivo · toque para focar; use dois dedos para ampliar.") : "Baixe os idiomas antes de usar sem internet."
        scheduleTranslation()
    }
    func swapDirection() {
        direction = direction == .englishToPortuguese ? .portugueseToEnglish : .englishToPortuguese
        generation = UUID(); requestWatchdog?.cancel(); request = nil; translations = [:]; cacheOrder = []
        tracker.clear(); regions = []; ready = false
        if frozen { recognizeFrozen() }
        Task { await checkReadiness() }
    }
    func prepare() {
        guard active, request == nil else { return }
        status = "Preparando idiomas · permita o download solicitado pelo iOS."
        beginRequest(.prepare)
    }
    func toggleFreeze() {
        guard image != nil else { status = "Aguarde a câmera iniciar."; return }
        generation = UUID(); requestWatchdog?.cancel(); request = nil; tracker.clear(); regions = []
        frozen.toggle()
        capture.setFrozen(frozen)
        if frozen { recognizeFrozen() }
        else { status = "Ao vivo · toque para focar; use dois dedos para ampliar." }
        if !ready { Task { await checkReadiness() } }
    }
    private func recognizeFrozen() {
        guard let image else { return }
        let token = generation
        status = "Lendo imagem congelada…"
        capture.recognizeStill(image) { [weak self] result in
            guard let self, self.active, self.frozen, self.generation == token else { return }
            switch result {
            case .success(let observations):
                self.regions = self.tracker.update(observations, now: ProcessInfo.processInfo.systemUptime, still: true)
                self.status = self.regions.isEmpty ? "Nenhum texto legível. Retome e ajuste foco ou iluminação." : "Imagem congelada · tradução sobre a captura."
                self.scheduleTranslation()
            case .failure(let error): self.status = "Falha ao ler captura: \(error.localizedDescription)"
            }
        }
    }
    func focus(at point: CGPoint) { if !frozen { capture.focus(at: point) } }
    func zoom(scale: CGFloat, began: Bool) { if !frozen { capture.zoom(scale: scale, began: began) } }
    private func scheduleTranslation() {
        guard active, ready, request == nil else { return }
        var seen = Set<String>()
        let pending = regions.filter(\.confirmed).compactMap { region -> String? in
            let key = CameraRegionTracker.key(region.text)
            guard translations[key] == nil, seen.insert(key).inserted else { return nil }
            return region.text
        }
        guard !pending.isEmpty else { return }
        beginRequest(.texts(pending))
    }
    private func beginRequest(_ kind: CameraTranslationRequest.Kind) {
        requestWatchdog?.cancel()
        let operation = CameraTranslationRequest(direction: direction, kind: kind)
        request = operation
        let seconds: Int
        switch kind { case .prepare: seconds = 120; case .texts: seconds = 45 }
        requestWatchdog = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(seconds)) } catch { return }
            guard let self, self.active, self.request?.id == operation.id else { return }
            self.generation = UUID(); self.request = nil; self.ready = false
            self.status = "A tradução excedeu o tempo. Use Baixar idiomas para tentar novamente."
        }
    }
    func perform(using session: TranslationSession, request operation: CameraTranslationRequest) async {
        let token = generation
        func current() -> Bool { active && generation == token && request?.id == operation.id }
        guard current() else { return }
        do {
            switch operation.kind {
            case .prepare:
                try await translator.prepare(using: session, direction: operation.direction)
                guard current() else { return }
                ready = true; status = "Idiomas prontos. Confira o funcionamento em modo avião."
            case .texts(let texts):
                for text in texts {
                    guard current() else { return }
                    let key = CameraRegionTracker.key(text)
                    guard regions.contains(where: { $0.confirmed && CameraRegionTracker.key($0.text) == key }) else { continue }
                    let result = try await translator.translate(text, using: session, direction: operation.direction)
                    guard current() else { return }
                    translations[key] = result.text
                    cacheOrder.removeAll { $0 == key }; cacheOrder.append(key)
                    while cacheOrder.count > 128 { translations.removeValue(forKey: cacheOrder.removeFirst()) }
                }
            }
        } catch is CancellationError {
            guard current() else { return }
        } catch {
            guard current() else { return }
            ready = false; status = "\(error.localizedDescription) Use Baixar idiomas para tentar novamente."
        }
        guard current() else { return }
        requestWatchdog?.cancel(); requestWatchdog = nil
        self.request = nil
        // A fresh view-scoped session handles any regions that appeared during translation.
        Task { [weak self] in
            await Task.yield()
            self?.scheduleTranslation()
        }
    }
}
