import AVFoundation
import CoreImage
import Vision
import UIKit

/// Session, device configuration and Vision work are serialized away from the UI.
/// Portrait buffers feed both display and OCR, avoiding independent preview transforms.
final class CameraCapture: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "br.edu.tradutor.camera", qos: .userInitiated)
    private let context = CIContext()
    private let visionQueue = DispatchQueue(label: "br.edu.tradutor.camera.vision", qos: .userInitiated)
    private var recognizing = false
    private var frozen = false
    private var previewPending = false
    private var analysisID = 0
    private var device: AVCaptureDevice?
    private var configured = false
    private var running = false
    private var runID = UUID()
    private var lastFrame: TimeInterval = 0
    private var lastOCR: TimeInterval = 0
    private var zoomAtStart: CGFloat = 1
    private let onFrame: @MainActor (UUID, CGImage, [CameraObservation]?) -> Void
    private let onError: @MainActor (UUID, String) -> Void

    init(onFrame: @escaping @MainActor (UUID, CGImage, [CameraObservation]?) -> Void,
         onError: @escaping @MainActor (UUID, String) -> Void) {
        self.onFrame = onFrame; self.onError = onError
        super.init()
    }
    func start(id: UUID) {
        queue.async { [self] in
            runID = id
            do {
                if !configured { try configure() }
                running = true; frozen = false; previewPending = false; analysisID += 1; lastFrame = 0; lastOCR = 0
                if !session.isRunning { session.startRunning() }
                if !session.isRunning { throw ServiceError("Não foi possível iniciar a câmera. Reabra esta tela.") }
            } catch {
                running = false
                Task { @MainActor in self.onError(id, error.localizedDescription) }
            }
        }
    }
    func stop() {
        queue.async { [self] in
            running = false
            if session.isRunning { session.stopRunning() }
        }
    }
    private func configure() throws {
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .hd1280x720
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            throw ServiceError("Câmera traseira indisponível neste aparelho.")
        }
        let input = try AVCaptureDeviceInput(device: camera)
        guard session.canAddInput(input) else { throw ServiceError("Entrada da câmera indisponível.") }
        session.addInput(input)
        let output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        output.setSampleBufferDelegate(self, queue: queue)
        guard session.canAddOutput(output) else {
            session.removeInput(input)
            throw ServiceError("Saída da câmera indisponível.")
        }
        session.addOutput(output)
        guard let connection = output.connection(with: .video), connection.isVideoRotationAngleSupported(90) else {
            session.removeOutput(output); session.removeInput(input)
            throw ServiceError("A câmera não suporta a orientação vertical solicitada.")
        }
        connection.videoRotationAngle = 90
        device = camera; configured = true
    }
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        guard running, !frozen, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let now = ProcessInfo.processInfo.systemUptime
        guard !previewPending, now - lastFrame >= 0.05 else { return }
        lastFrame = now
        let image = CIImage(cvPixelBuffer: buffer)
        guard let cgImage = context.createCGImage(image, from: image.extent) else { return }
        let id = runID
        // Display is independent of OCR cadence, but capture discards queued old frames.
        previewPending = true
        Task { @MainActor in
            self.onFrame(id, cgImage, nil)
            self.queue.async { if self.runID == id { self.previewPending = false } }
        }
        guard !recognizing, now - lastOCR >= 0.15 else { return }
        lastOCR = now; recognizing = true
        let analysis = analysisID
        visionQueue.async { [self] in
            let result = Result { try Self.recognize(cgImage) }
            queue.async { [self] in
                recognizing = false
                guard running, !frozen, runID == id, analysisID == analysis else { return }
                switch result {
                case .success(let observations): Task { @MainActor in self.onFrame(id, cgImage, observations) }
                case .failure(let error): Task { @MainActor in self.onError(id, "Falha na leitura: \(error.localizedDescription)") }
                }
            }
        }
    }
    func setFrozen(_ value: Bool) {
        queue.async { self.frozen = value; self.analysisID += 1; self.lastOCR = 0 }
    }
    func recognizeStill(_ image: CGImage, completion: @escaping @MainActor (Result<[CameraObservation], Error>) -> Void) {
        visionQueue.async {
            let result = Result { try Self.recognize(image) }
            Task { @MainActor in completion(result) }
        }
    }
    private static func recognize(_ image: CGImage) throws -> [CameraObservation] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["en-US", "pt-BR"]
        request.usesLanguageCorrection = true
        request.minimumTextHeight = 0.008
        try VNImageRequestHandler(cgImage: image, orientation: .up, options: [:]).perform([request])
        return (request.results ?? []).prefix(12).compactMap { observation in
            guard let candidate = observation.topCandidates(1).first else { return nil }
            func point(_ p: CGPoint) -> CGPoint { CGPoint(x: p.x, y: 1-p.y) }
            let quad = CameraQuad(points: [point(observation.topLeft), point(observation.topRight),
                                           point(observation.bottomRight), point(observation.bottomLeft)])
            return CameraObservation(text: candidate.string, quad: quad, confidence: candidate.confidence)
        }
    }
    func focus(at normalized: CGPoint) {
        queue.async { [self] in
            guard let device, running else { return }
            do {
                try device.lockForConfiguration(); defer { device.unlockForConfiguration() }
                // AVFoundation device points remain in the sensor's landscape coordinate system.
                let sensorPoint = CGPoint(x: normalized.y, y: 1-normalized.x)
                if device.isFocusPointOfInterestSupported && device.isFocusModeSupported(.autoFocus) {
                    device.focusPointOfInterest = sensorPoint; device.focusMode = .autoFocus
                }
                if device.isExposurePointOfInterestSupported && device.isExposureModeSupported(.continuousAutoExposure) {
                    device.exposurePointOfInterest = sensorPoint; device.exposureMode = .continuousAutoExposure
                }
            } catch { /* Focus is best effort; capture remains usable. */ }
        }
    }
    func zoom(scale: CGFloat, began: Bool) {
        queue.async { [self] in
            guard let device, running else { return }
            do {
                try device.lockForConfiguration(); defer { device.unlockForConfiguration() }
                if began { zoomAtStart = device.videoZoomFactor }
                device.videoZoomFactor = min(max(zoomAtStart * scale, 1), min(device.activeFormat.videoMaxZoomFactor, 6))
            } catch { /* Zoom is best effort. */ }
        }
    }
}
