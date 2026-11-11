import AVFoundation
import SwiftUI
import Translation
import UIKit

@MainActor
struct CameraTranslationView: View {
    @StateObject private var model = CameraTranslationModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Button(model.direction.rawValue) { model.switchDirection() }
                    .accessibilityLabel("Inverter direção: \(model.direction.rawValue)")
                Spacer()
                Button("Preparar tradução") { model.prepare() }
                    .disabled(model.request != nil)
            }
            .padding(.horizontal)
            Text(model.status).font(.caption).frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
            CameraPreview(session: model.capture.session, rows: model.rows, translations: model.translations)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityLabel("Prévia da câmera com texto traduzido")
        }
        .navigationTitle("Traduzir com a câmera")
        .navigationBarTitleDisplayMode(.inline)
        .background {
            if let pending = model.request {
                CameraTranslationTask(model: model, request: pending).id(pending.id)
            }
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
private struct CameraTranslationTask: View {
    let model: CameraTranslationModel
    let request: CameraTranslationModel.Request

    var body: some View {
        Color.clear.frame(width: 1, height: 1).accessibilityHidden(true)
            .translationTask(.init(
                source: Locale.Language(identifier: request.direction.sourceLanguageIdentifier),
                target: Locale.Language(identifier: request.direction.targetLanguageIdentifier)
            )) { session in
                await model.perform(request, using: session)
            }
    }
}

@MainActor
private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    let rows: [CameraText]
    let translations: [String: String]

    func makeUIView(context: Context) -> CameraPreviewSurface {
        let view = CameraPreviewSurface()
        view.preview.session = session
        return view
    }

    func updateUIView(_ view: CameraPreviewSurface, context: Context) {
        view.overlay.rows = rows
        view.overlay.translations = translations
        view.overlay.setNeedsDisplay()
    }
}

private final class CameraPreviewSurface: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    let overlay = CameraOverlay()

    override init(frame: CGRect) {
        super.init(frame: frame)
        preview.videoGravity = .resizeAspectFill
        overlay.preview = preview
        overlay.backgroundColor = .clear
        overlay.isUserInteractionEnabled = false
        addSubview(overlay)
    }
    required init?(coder: NSCoder) { return nil }
    override func layoutSubviews() {
        super.layoutSubviews()
        overlay.frame = bounds
        if let connection = preview.connection, connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
        overlay.setNeedsDisplay()
    }
}

private final class CameraOverlay: UIView {
    weak var preview: AVCaptureVideoPreviewLayer?
    var rows: [CameraText] = []
    var translations: [String: String] = [:]

    override func draw(_ rect: CGRect) {
        guard bounds.width > 20, bounds.height > 20,
              let preview = preview, let context = UIGraphicsGetCurrentContext() else { return }
        for row in rows {
            let box = row.box
            let metadata = CGRect(x: box.minX, y: 1 - box.maxY, width: box.width, height: box.height)
            let frame = preview.layerRectConverted(fromMetadataOutputRect: metadata).intersection(bounds)
            guard !frame.isNull, frame.width > 1, frame.height > 1 else { continue }
            context.setStrokeColor(UIColor.systemMint.cgColor)
            context.setLineWidth(2)
            context.stroke(frame)
            guard let translation = translations[row.text] else { continue }
            let labelWidth = min(max(frame.width, 140), max(bounds.width - 12, 1))
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.preferredFont(forTextStyle: .caption1),
                .foregroundColor: UIColor.white
            ]
            let size = (translation as NSString).boundingRect(
                with: CGSize(width: labelWidth - 8, height: bounds.height),
                options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attributes, context: nil
            ).size
            let height = min(ceil(size.height) + 8, bounds.height)
            let left = max(0, min(frame.minX, bounds.width - labelWidth))
            let top = max(0, min(frame.minY, bounds.height - height))
            let background = CGRect(x: left, y: top, width: labelWidth, height: height)
            UIColor.black.withAlphaComponent(0.9).setFill()
            UIBezierPath(roundedRect: background, cornerRadius: 4).fill()
            (translation as NSString).draw(with: background.insetBy(dx: 4, dy: 4),
                                            options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine], attributes: attributes, context: nil)
        }
    }
}
