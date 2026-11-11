import AVFoundation
import ImageIO
import UIKit
import Vision

struct CameraText: Identifiable {
    let text: String
    /// Vision normalized coordinates: origin at bottom left.
    let box: CGRect
    var id: String { text + "-\(box.origin.x)-\(box.origin.y)" }
}

/// Capture and OCR share a serial background queue; frames are never saved.
final class CameraCapture: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    let session = AVCaptureSession()
    var onText: (@MainActor ([CameraText], Int) -> Void)?
    var onError: (@MainActor (String, Int) -> Void)?
    private let queue = DispatchQueue(label: "tradutor.camera.capture", qos: .userInitiated)
    private var configured = false
    private var captureToken = 0
    private var lastFrame = CFAbsoluteTimeGetCurrent()

    func start(token: Int) {
        queue.async { [weak self] in
            guard let self = self else { return }
            self.captureToken = token
            do {
                if !self.configured { try self.configure() }
                if !self.session.isRunning { self.session.startRunning() }
            } catch {
                DispatchQueue.main.async { self.onError?("Câmera indisponível: \(error.localizedDescription)", token) }
            }
        }
    }

    func stop() {
        queue.async { [weak self] in
            guard let self = self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    private func configure() throws {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            throw ServiceError("Nenhuma câmera traseira disponível neste aparelho.")
        }
        let input = try AVCaptureDeviceInput(device: device)
        let output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: queue)
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .high
        guard session.canAddInput(input), session.canAddOutput(output) else {
            throw ServiceError("Não foi possível configurar a captura de vídeo.")
        }
        session.addInput(input)
        session.addOutput(output)
        // The app is portrait-only. Video output rotates its buffers to match.
        if let connection = output.connection(with: .video), connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
        configured = true
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = CFAbsoluteTimeGetCurrent()
        guard now - lastFrame >= 0.5, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastFrame = now
        let token = captureToken
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["pt-BR", "en-US"]
        request.usesLanguageCorrection = true
        do {
            try VNImageRequestHandler(cvPixelBuffer: buffer, orientation: .up, options: [:]).perform([request])
            let rows = (request.results ?? []).compactMap { observation -> CameraText? in
                guard let candidate = observation.topCandidates(1).first else { return nil }
                let text = candidate.string.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty, text.count <= 1_500 else { return nil }
                return CameraText(text: text, box: observation.boundingBox)
            }.prefix(12)
            let snapshot = Array(rows)
            DispatchQueue.main.async { [weak self] in self?.onText?(snapshot, token) }
        } catch {
            DispatchQueue.main.async { [weak self] in self?.onError?("Falha no reconhecimento. Melhore a iluminação e reabra a câmera.", token) }
        }
    }
}
