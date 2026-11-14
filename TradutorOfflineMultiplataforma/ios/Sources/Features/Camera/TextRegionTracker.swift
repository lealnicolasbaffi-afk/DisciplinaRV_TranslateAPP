import Foundation
import CoreGraphics

/// Coordinates are normalized to the upright image, with a top-left origin.
struct CameraQuad {
    var points: [CGPoint] // top-left, top-right, bottom-right, bottom-left
    var width: CGFloat { (distance(points[0], points[1]) + distance(points[3], points[2])) / 2 }
    var height: CGFloat { (distance(points[0], points[3]) + distance(points[1], points[2])) / 2 }
    var rect: CGRect {
        let xs = points.map(\.x), ys = points.map(\.y)
        return CGRect(x: xs.min() ?? 0, y: ys.min() ?? 0,
                      width: (xs.max() ?? 0) - (xs.min() ?? 0), height: (ys.max() ?? 0) - (ys.min() ?? 0))
    }
    var valid: Bool {
        guard points.count == 4, points.allSatisfy({ $0.x.isFinite && $0.y.isFinite }) else { return false }
        let crosses = (0..<4).map { i -> CGFloat in
            let a = points[i], b = points[(i + 1) % 4], c = points[(i + 2) % 4]
            return (b.x-a.x)*(c.y-b.y) - (b.y-a.y)*(c.x-b.x)
        }
        return crosses.allSatisfy { $0 > 0 } && width >= 0.005 && height >= 0.003
    }
    func smoothed(toward next: CameraQuad) -> CameraQuad {
        let motion = zip(points, next.points).map { distance($0.0, $0.1) }.reduce(0, +) / 4
        let scale = max(hypot(width, height), 0.001)
        let alpha: CGFloat = motion < scale * 0.006 ? 0.18 : motion < scale * 0.025 ? 0.4 : motion < scale * 0.1 ? 0.72 : 1
        return CameraQuad(points: zip(points, next.points).map { a, b in
            CGPoint(x: a.x + (b.x-a.x)*alpha, y: a.y + (b.y-a.y)*alpha)
        })
    }
}
private func distance(_ a: CGPoint, _ b: CGPoint) -> CGFloat { hypot(a.x-b.x, a.y-b.y) }

struct CameraObservation {
    let text: String
    let quad: CameraQuad
    let confidence: Float
}
struct CameraRegion: Identifiable {
    let id: Int
    let text: String
    let quad: CameraQuad
    let confirmed: Bool
    let opacity: CGFloat
}

final class CameraRegionTracker {
    private struct Track {
        let id: Int
        var quad: CameraQuad
        var seen: TimeInterval
        var candidate: String
        var candidateSince: TimeInterval
        var votes = 1
        var accepted: String?
        var showAccepted = false
    }
    private var tracks: [Track] = []
    private var nextID = 0
    func clear() { tracks.removeAll() }
    static func key(_ text: String) -> String {
        text.lowercased().split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }
    private func similarity(_ a: String, _ b: String) -> CGFloat {
        let x = Array(Self.key(a).prefix(160)), y = Array(Self.key(b).prefix(160))
        guard !x.isEmpty, !y.isEmpty else { return 0 }
        var previous = Array(0...y.count)
        for (i, letter) in x.enumerated() {
            var next = [i + 1] + Array(repeating: 0, count: y.count)
            for j in y.indices { next[j+1] = min(next[j]+1, previous[j+1]+1, previous[j] + (letter == y[j] ? 0 : 1)) }
            previous = next
        }
        return 1 - CGFloat(previous[y.count]) / CGFloat(max(x.count, y.count))
    }
    @discardableResult
    func update(_ observations: [CameraObservation], now: TimeInterval, still: Bool = false) -> [CameraRegion] {
        if still { clear() }
        tracks.removeAll { now - $0.seen > 1.2 }
        var unused = Set(tracks.indices)
        var matched = false
        for observation in observations.prefix(12) {
            let text = observation.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard (2...1500).contains(text.count), text.contains(where: { $0.isLetter }),
                  observation.confidence >= 0.45, observation.quad.valid else { continue }
            let candidates = unused.compactMap { index -> (Int, CGFloat)? in
                let old = tracks[index].quad, new = observation.quad
                let intersection = old.rect.intersection(new.rect)
                let area = intersection.isNull ? 0 : intersection.width * intersection.height
                let union = old.rect.width*old.rect.height + new.rect.width*new.rect.height - area
                let overlap = union > 0 ? area / union : 0
                let delta = hypot(old.rect.midX-new.rect.midX, old.rect.midY-new.rect.midY) / max(hypot(old.width, old.height), 0.001)
                let similarity = self.similarity(tracks[index].candidate, text)
                guard (0.45...2.2).contains(new.width / old.width),
                      overlap > 0.22 || (similarity > 0.82 && delta < 0.65) else { return nil }
                return (index, overlap*2 + similarity - delta*0.4)
            }
            if let index = candidates.max(by: { $0.1 < $1.1 })?.0 {
                matched = true; unused.remove(index)
                tracks[index].quad = tracks[index].quad.smoothed(toward: observation.quad)
                tracks[index].seen = now
                if Self.key(tracks[index].candidate) == Self.key(text) { tracks[index].votes += 1 }
                else { tracks[index].candidate = text; tracks[index].votes = 1; tracks[index].candidateSince = now }
                tracks[index].showAccepted = tracks[index].accepted.map { similarity($0, text) >= 0.78 } ?? false
                let votes = tracks[index].accepted == nil ? 2 : 3
                if tracks[index].votes >= votes && now - tracks[index].candidateSince >= 0.1 {
                    tracks[index].accepted = text; tracks[index].showAccepted = true
                }
            } else {
                nextID += 1
                tracks.append(Track(id: nextID, quad: observation.quad, seen: now, candidate: text, candidateSince: now))
            }
        }
        if !observations.isEmpty && !matched { for i in unused { tracks[i].showAccepted = false } }
        if still { for i in tracks.indices { tracks[i].accepted = tracks[i].candidate; tracks[i].showAccepted = true } }
        if tracks.count > 48 { tracks.removeFirst(tracks.count - 48) }
        return snapshot(now: now)
    }
    func snapshot(now: TimeInterval) -> [CameraRegion] {
        tracks.compactMap { track in
            let age = now - track.seen
            guard age >= 0, age <= 0.45 else { return nil }
            return CameraRegion(id: track.id, text: track.accepted ?? track.candidate, quad: track.quad,
                                confirmed: track.accepted != nil && track.showAccepted,
                                opacity: age <= 0.065 ? 1 : max(0, 1 - (age-0.065)/0.385))
        }
    }
}
