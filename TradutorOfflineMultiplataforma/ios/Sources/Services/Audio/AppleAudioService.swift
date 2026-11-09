import AVFoundation
import Foundation

@MainActor
final class AppleAudioService: NSObject, AudioServicing, AVAudioRecorderDelegate, AVAudioPlayerDelegate {
    static let maximumDuration: TimeInterval = 20

    var onRecordingFinished: ((Result<URL, Error>) -> Void)?
    var onPlaybackFinished: ((Result<Void, Error>) -> Void)?
    var onInterruption: (() -> Void)?

    private let directory: URL
    private let sessionOwner = UUID()
    private var recorder: AVAudioRecorder?
    private var player: AVAudioPlayer?
    private var observers: [NSObjectProtocol] = []
    private let allowedExtensions: Set<String> = ["caf", "wav", "m4a", "mp3", "aif", "aiff", "aac"]

    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("ConversationAudio", isDirectory: true)
        super.init()
        observeInterruptions()
    }

    deinit {
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
        let owner = sessionOwner
        Task { @MainActor in AppleAudioSession.release(owner: owner) }
    }

    func requestRecordPermission() async throws {
        try Task.checkCancellation()
        let granted = await AVAudioApplication.requestRecordPermission()
        try Task.checkCancellation()
        guard granted else {
            throw ServiceError("Permita o microfone em Ajustes → Privacidade e Segurança → Microfone e tente gravar novamente.")
        }
    }

    func startRecording() throws -> URL {
        guard AVAudioApplication.shared.recordPermission == .granted else {
            throw ServiceError("Permita o microfone antes de iniciar a gravação.")
        }
        cancel()
        try createDirectory()
        let url = directory.appendingPathComponent("fala-\(UUID().uuidString).caf")
        do {
            try AppleAudioSession.activateRecording(owner: sessionOwner)
            let recording = try AVAudioRecorder(url: url, settings: [
                AVFormatIDKey: kAudioFormatLinearPCM,
                AVSampleRateKey: 16_000,
                AVNumberOfChannelsKey: 1,
                AVLinearPCMBitDepthKey: 16,
                AVLinearPCMIsFloatKey: false,
                AVLinearPCMIsBigEndianKey: false
            ])
            recording.delegate = self
            recorder = recording
            guard recording.record(forDuration: Self.maximumDuration) else {
                throw ServiceError("A gravação não iniciou. Confira o microfone e tente novamente.")
            }
            return url
        } catch {
            cancel()
            try? FileManager.default.removeItem(at: url)
            if let serviceError = error as? ServiceError { throw serviceError }
            throw ServiceError("Não foi possível gravar: \(error.localizedDescription). Confira o microfone e tente novamente.")
        }
    }

    func stopRecording() throws -> URL {
        guard let recording = recorder else {
            throw ServiceError("Não há gravação em andamento. Grave uma frase antes de parar.")
        }
        // Desanexar antes de stop: seu delegate não pode iniciar uma segunda transcrição.
        recorder = nil
        recording.delegate = nil
        recording.stop()
        AppleAudioSession.release(owner: sessionOwner)
        do {
            try validateAudio(recording.url)
            return recording.url
        } catch {
            throw ServiceError("A gravação ficou inválida: \(error.localizedDescription)")
        }
    }

    func importAudio(_ url: URL) throws -> URL {
        guard recorder == nil, player == nil else {
            throw ServiceError("Pare a gravação ou a reprodução antes de importar o áudio.")
        }
        guard url.isFileURL else {
            throw ServiceError("Escolha um arquivo local de áudio em No Meu iPhone. Links remotos não são aceitos.")
        }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            let values = try url.resourceValues(forKeys: [
                .isRegularFileKey, .isUbiquitousItemKey, .ubiquitousItemDownloadingStatusKey, .fileSizeKey
            ])
            guard values.isRegularFile == true, FileManager.default.isReadableFile(atPath: url.path) else {
                throw ServiceError("O áudio não está disponível localmente. Baixe-o antes e escolha uma cópia em No Meu iPhone.")
            }
            // Não iniciar downloads do iCloud para uma importação offline.
            if values.isUbiquitousItem == true {
                let status = values.ubiquitousItemDownloadingStatus
                guard status == .current || status == .downloaded else {
                    throw ServiceError("O áudio do iCloud ainda não está baixado. Salve uma cópia em No Meu iPhone antes de importar.")
                }
            }
            guard let size = values.fileSize, size > 0, size <= 20 * 1_024 * 1_024 else {
                throw ServiceError("Escolha um áudio não vazio de até 20 MB e 20 segundos.")
            }
            try validateAudio(url)
            try createDirectory()
            // Sempre criar destino distinto; nunca excluir o arquivo selecionado.
            let destination = directory.appendingPathComponent("reserva-\(UUID().uuidString).\(url.pathExtension.lowercased())")
            try FileManager.default.copyItem(at: url, to: destination)
            do {
                try validateAudio(destination)
                return destination
            } catch {
                try? FileManager.default.removeItem(at: destination)
                throw error
            }
        } catch let error as ServiceError {
            throw error
        } catch {
            throw ServiceError("Falha ao importar áudio: \(error.localizedDescription). Use WAV, CAF, M4A, MP3, AIFF ou AAC local de até 20 segundos.")
        }
    }

    func play(_ url: URL) throws {
        guard recorder == nil else {
            throw ServiceError("Pare a gravação antes de ouvir o áudio original.")
        }
        cancelPlayback()
        do {
            try validateAudio(url)
            try AppleAudioSession.activatePlayback(owner: sessionOwner)
            let playback = try AVAudioPlayer(contentsOf: url)
            playback.delegate = self
            player = playback
            guard playback.prepareToPlay(), playback.play() else {
                throw ServiceError("A reprodução não iniciou. Confira a saída de áudio e tente novamente.")
            }
        } catch {
            cancelPlayback()
            if let serviceError = error as? ServiceError { throw serviceError }
            throw ServiceError("Falha ao reproduzir o original: \(error.localizedDescription). Grave ou importe o áudio novamente.")
        }
    }

    func cancel() {
        let recording = recorder
        recorder = nil
        recording?.delegate = nil
        recording?.stop()
        cancelPlayback()
        AppleAudioSession.release(owner: sessionOwner)
    }

    nonisolated func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        Task { @MainActor [weak self, recorder] in
            guard let self, let current = self.recorder, current === recorder else { return }
            self.recorder = nil
            current.delegate = nil
            AppleAudioSession.release(owner: self.sessionOwner)
            guard flag else {
                self.onRecordingFinished?(.failure(ServiceError("A gravação foi interrompida. Grave novamente em até 20 segundos.")))
                return
            }
            do {
                try self.validateAudio(current.url)
                self.onRecordingFinished?(.success(current.url))
            } catch {
                self.onRecordingFinished?(.failure(error))
            }
        }
    }

    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        let message = error?.localizedDescription ?? "erro de codificação"
        Task { @MainActor [weak self, recorder] in
            guard let self, let current = self.recorder, current === recorder else { return }
            self.recorder = nil
            current.delegate = nil
            current.stop()
            AppleAudioSession.release(owner: self.sessionOwner)
            self.onRecordingFinished?(.failure(ServiceError("Falha ao salvar o áudio: \(message). Confira o espaço livre e grave novamente.")))
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self, player] in
            guard let self, let current = self.player, current === player else { return }
            self.player = nil
            current.delegate = nil
            AppleAudioSession.release(owner: self.sessionOwner)
            self.onPlaybackFinished?(flag ? .success(()) : .failure(ServiceError("A reprodução do original foi interrompida. Tente ouvir novamente.")))
        }
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        let message = error?.localizedDescription ?? "formato inválido"
        Task { @MainActor [weak self, player] in
            guard let self, let current = self.player, current === player else { return }
            self.cancelPlayback()
            self.onPlaybackFinished?(.failure(ServiceError("Não foi possível decodificar o original: \(message). Importe outro áudio local.")))
        }
    }

    private func validateAudio(_ url: URL) throws {
        guard url.isFileURL, allowedExtensions.contains(url.pathExtension.lowercased()),
              FileManager.default.isReadableFile(atPath: url.path) else {
            throw ServiceError("Formato inválido. Use WAV, CAF, M4A, MP3, AIFF ou AAC baixado no iPhone.")
        }
        do {
            let file = try AVAudioFile(forReading: url)
            let format = file.processingFormat
            let duration = Double(file.length) / format.sampleRate
            guard format.sampleRate.isFinite, format.sampleRate > 0,
                  format.channelCount > 0, format.channelCount <= 2,
                  duration.isFinite, duration > 0, duration <= Self.maximumDuration else {
                throw ServiceError("O áudio precisa ter de 1 a 2 canais, conteúdo não vazio e duração de até 20 segundos.")
            }
            // Abrir o contêiner não basta: verificar a decodificação também
            // impede aceitar um arquivo truncado apenas pelo cabeçalho.
            guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 4_096) else {
                throw ServiceError("Não foi possível validar o formato do áudio. Exporte uma cópia WAV ou M4A.")
            }
            while file.framePosition < file.length {
                let remaining = min(AVAudioFramePosition(buffer.frameCapacity), file.length - file.framePosition)
                try file.read(into: buffer, frameCount: AVAudioFrameCount(remaining))
                guard buffer.frameLength > 0 else {
                    throw ServiceError("O arquivo de áudio está incompleto. Importe uma cópia local íntegra.")
                }
            }
        } catch let error as ServiceError {
            throw error
        } catch {
            throw ServiceError("O arquivo não pôde ser lido como áudio: \(error.localizedDescription). Exporte uma cópia WAV ou M4A de até 20 segundos.")
        }
    }

    private func createDirectory() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    private func cancelPlayback() {
        let playback = player
        player = nil
        playback?.delegate = nil
        playback?.stop()
        AppleAudioSession.release(owner: sessionOwner)
    }

    private func observeInterruptions() {
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
            let type = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.uintValue
            guard type == AVAudioSession.InterruptionType.began.rawValue else { return }
            Task { @MainActor in self?.handleInterruption() }
        })
        observers.append(center.addObserver(forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.handleInterruption() }
        })
        observers.append(center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
            let reason = (notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.uintValue
            guard reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue else { return }
            Task { @MainActor in self?.handleInterruption() }
        })
    }

    private func handleInterruption() {
        // A voz usa a mesma sessão mesmo quando recorder/player estão vazios.
        guard recorder != nil || player != nil || AppleAudioSession.hasActiveOwner else { return }
        cancel()
        onInterruption?()
    }
}
