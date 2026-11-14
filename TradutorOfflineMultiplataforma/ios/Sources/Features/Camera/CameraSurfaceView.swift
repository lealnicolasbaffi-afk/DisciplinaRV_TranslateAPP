import SwiftUI
import UIKit

struct CameraSurface: UIViewRepresentable {
    let image: CGImage?
    let regions: [CameraRegion]
    let translations: [String: String]
    let frozen: Bool
    let focus: (CGPoint) -> Void
    let zoom: (CGFloat, Bool) -> Void

    func makeUIView(context: Context) -> CameraSurfaceView { CameraSurfaceView() }
    func updateUIView(_ view: CameraSurfaceView, context: Context) {
        view.image = image; view.regions = regions; view.translations = translations
        view.frozen = frozen; view.focus = focus; view.zoom = zoom
        view.setNeedsLayout()
    }
    static func dismantleUIView(_ view: CameraSurfaceView, coordinator: ()) { view.image = nil }
}

/// The image and projected labels share one aspect-fill transform in upright image coordinates.
final class CameraSurfaceView: UIView {
    var image: CGImage?
    var regions: [CameraRegion] = []
    var translations: [String: String] = [:]
    var frozen = false
    var focus: ((CGPoint) -> Void)?
    var zoom: ((CGFloat, Bool) -> Void)?
    private let imageLayer = CALayer()
    private let overlay = CALayer()
    private var labels: [String: CGImage] = [:]
    private var labelOrder: [String] = []

    init() {
        super.init(frame: .zero)
        backgroundColor = .black; clipsToBounds = true
        layer.addSublayer(imageLayer); layer.addSublayer(overlay)
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapped(_:))))
        addGestureRecognizer(UIPinchGestureRecognizer(target: self, action: #selector(pinched(_:))))
        isAccessibilityElement = true
        accessibilityLabel = "Imagem da câmera"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) não suportado") }
    private func imageRect(_ image: CGImage) -> CGRect {
        let scale = max(bounds.width / CGFloat(image.width), bounds.height / CGFloat(image.height))
        let size = CGSize(width: CGFloat(image.width)*scale, height: CGFloat(image.height)*scale)
        return CGRect(x: (bounds.width-size.width)/2, y: (bounds.height-size.height)/2, width: size.width, height: size.height)
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin(); CATransaction.setDisableActions(true)
        defer { CATransaction.commit() }
        overlay.frame = bounds; overlay.sublayers?.forEach { $0.removeFromSuperlayer() }
        guard let image else { imageLayer.contents = nil; return }
        let rect = imageRect(image)
        imageLayer.frame = rect; imageLayer.contents = image
        imageLayer.contentsGravity = .resize
        var spoken: [String] = []
        for region in regions where region.quad.valid && region.opacity > 0 {
            let points = region.quad.points.map { CGPoint(x: rect.minX + $0.x*rect.width, y: rect.minY + $0.y*rect.height) }
            let text = region.confirmed ? translations[CameraRegionTracker.key(region.text)] : nil
            if let text {
                let width = max(24, min(1600, (region.quad.width*rect.width / 12).rounded()*12))
                let height = max(16, min(1200, (region.quad.height*rect.height / 8).rounded()*8))
                let size = CGSize(width: width, height: height)
                guard let transform = projection(size: size, points: points), let bitmap = label(text, size: size) else { continue }
                let panel = CALayer()
                panel.anchorPoint = .zero; panel.position = .zero
                panel.bounds = CGRect(origin: .zero, size: size)
                panel.contents = bitmap; panel.transform = transform
                panel.opacity = Float(region.opacity)
                overlay.addSublayer(panel); spoken.append(text)
            } else {
                let outline = CAShapeLayer(), path = UIBezierPath()
                path.move(to: points[0]); points.dropFirst().forEach { path.addLine(to: $0) }; path.close()
                outline.path = path.cgPath; outline.fillColor = UIColor.clear.cgColor
                outline.strokeColor = UIColor.systemMint.withAlphaComponent(0.6).cgColor
                outline.lineWidth = 1; outline.opacity = Float(region.opacity)
                overlay.addSublayer(outline)
            }
        }
        accessibilityValue = spoken.isEmpty ? (frozen ? "Imagem congelada" : "Ao vivo") : spoken.joined(separator: ". ")
    }
    private func label(_ text: String, size: CGSize) -> CGImage? {
        let key = "\(Int(size.width)):\(Int(size.height)):\(text)"
        if let cached = labels[key] {
            labelOrder.removeAll { $0 == key }; labelOrder.append(key)
            return cached
        }
        let padding = min(5, max(1, size.height*0.06))
        let content = CGSize(width: max(1, size.width-padding*2), height: max(1, size.height-padding*2))
        let label = UILabel()
        label.text = text; label.numberOfLines = 0; label.textColor = UIColor(red: 0.09, green: 0.11, blue: 0.12, alpha: 1)
        label.lineBreakMode = .byWordWrapping
        var low: CGFloat = 4, high = min(24, max(4, size.height*0.8))
        for _ in 0..<8 {
            let mid = (low+high)/2; label.font = .systemFont(ofSize: mid)
            if label.sizeThatFits(CGSize(width: content.width, height: .greatestFiniteMagnitude)).height <= content.height { low = mid } else { high = mid }
        }
        label.font = .systemFont(ofSize: low)
        let measured = label.sizeThatFits(CGSize(width: content.width, height: .greatestFiniteMagnitude))
        let height = min(content.height, measured.height)
        label.frame = CGRect(x: padding, y: max(padding, (size.height-height)/2), width: content.width, height: height)
        label.lineBreakMode = .byTruncatingTail
        label.setNeedsLayout(); label.layoutIfNeeded()
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let bitmap = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            UIColor(red: 0.973, green: 0.969, blue: 0.937, alpha: 1).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            ctx.cgContext.saveGState(); ctx.cgContext.translateBy(x: label.frame.minX, y: label.frame.minY)
            label.layer.render(in: ctx.cgContext); ctx.cgContext.restoreGState()
        }.cgImage
        if let bitmap {
            labels[key] = bitmap; labelOrder.append(key)
            while labelOrder.count > 64 { labels.removeValue(forKey: labelOrder.removeFirst()) }
        }
        return bitmap
    }
    /// Solve a four-point planar homography; no 3D anchor or optical flow is implied.
    private func projection(size: CGSize, points: [CGPoint]) -> CATransform3D? {
        let source = [CGPoint.zero, CGPoint(x: size.width, y: 0), CGPoint(x: size.width, y: size.height), CGPoint(x: 0, y: size.height)]
        var matrix = Array(repeating: Array(repeating: CGFloat(0), count: 9), count: 8)
        for i in 0..<4 {
            let x = source[i].x, y = source[i].y, u = points[i].x, v = points[i].y
            matrix[i*2] = [x,y,1,0,0,0,-u*x,-u*y,u]
            matrix[i*2+1] = [0,0,0,x,y,1,-v*x,-v*y,v]
        }
        for col in 0..<8 {
            let pivot = (col..<8).max { abs(matrix[$0][col]) < abs(matrix[$1][col]) }!
            guard abs(matrix[pivot][col]) > 0.000001 else { return nil }
            matrix.swapAt(pivot, col)
            let divisor = matrix[col][col]
            for j in col...8 { matrix[col][j] /= divisor }
            for row in 0..<8 where row != col {
                let factor = matrix[row][col]
                for j in col...8 { matrix[row][j] -= factor*matrix[col][j] }
            }
        }
        let h = matrix.map { $0[8] }
        guard h.allSatisfy(\.isFinite) else { return nil }
        var transform = CATransform3DIdentity
        transform.m11 = h[0]; transform.m21 = h[1]; transform.m41 = h[2]
        transform.m12 = h[3]; transform.m22 = h[4]; transform.m42 = h[5]
        transform.m14 = h[6]; transform.m24 = h[7]
        return transform
    }
    @objc private func tapped(_ gesture: UITapGestureRecognizer) {
        guard !frozen, let image else { return }
        let rect = imageRect(image), point = gesture.location(in: self)
        focus?(CGPoint(x: min(1, max(0, (point.x-rect.minX)/rect.width)),
                       y: min(1, max(0, (point.y-rect.minY)/rect.height))))
    }
    @objc private func pinched(_ gesture: UIPinchGestureRecognizer) {
        guard !frozen else { return }
        zoom?(gesture.scale, gesture.state == .began)
    }
}
