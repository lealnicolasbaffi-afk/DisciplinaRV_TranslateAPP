package br.edu.tradutoroffline.features.camera

import java.util.Locale
import kotlin.math.*

internal data class Vertex(val x: Float, val y: Float) {
    fun distance(other: Vertex) = hypot(x - other.x, y - other.y)
    fun blend(other: Vertex, amount: Float) = Vertex(x + (other.x - x) * amount, y + (other.y - y) * amount)
}

/** Clockwise OCR corners, starting at the text's top-left (not screen top-left). */
internal data class TextQuad(val corners: List<Vertex>) {
    val width get() = (corners[0].distance(corners[1]) + corners[3].distance(corners[2])) / 2
    val height get() = (corners[0].distance(corners[3]) + corners[1].distance(corners[2])) / 2
    val left get() = corners.minOf { it.x }
    val top get() = corners.minOf { it.y }
    val right get() = corners.maxOf { it.x }
    val bottom get() = corners.maxOf { it.y }
    val center get() = Vertex(corners.sumOf { it.x.toDouble() }.toFloat() / 4, corners.sumOf { it.y.toDouble() }.toFloat() / 4)
    fun valid(): Boolean {
        if (corners.size != 4 || corners.any { !it.x.isFinite() || !it.y.isFinite() }) return false
        val cross = corners.indices.map { i ->
            val a = corners[i]; val b = corners[(i + 1) % 4]; val c = corners[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return cross.all { it > 1f } && width >= 12 && height >= 8
    }
    fun overlap(other: TextQuad): Float {
        val intersection = (min(right, other.right) - max(left, other.left)).coerceAtLeast(0f) *
            (min(bottom, other.bottom) - max(top, other.top)).coerceAtLeast(0f)
        val area = (right - left) * (bottom - top) + (other.right - other.left) * (other.bottom - other.top) - intersection
        return if (area > 0) intersection / area else 0f
    }
    fun smooth(next: TextQuad): TextQuad {
        val motion = corners.indices.sumOf { corners[it].distance(next.corners[it]).toDouble() }.toFloat() / 4
        val scale = hypot(width, height).coerceAtLeast(1f)
        // Dampen subpixel/tiny OCR jitter, but follow real camera motion promptly.
        val alpha = when {
            motion < 0.006f * scale -> 0.18f
            motion < 0.025f * scale -> 0.40f
            motion < 0.10f * scale -> 0.72f
            else -> 1f
        }
        return TextQuad(corners.indices.map { corners[it].blend(next.corners[it], alpha) })
    }
}

internal data class TextObservation(val text: String, val quad: TextQuad, val confidence: Float = 1f)
internal data class TrackedText(val id: Long, val text: String, val quad: TextQuad, val opacity: Float, val confirmed: Boolean)

/** Temporal OCR association; this is image-space tracking, not a persistent 3D world anchor. */
internal class TextRegionTracker {
    private data class Track(
        val id: Long, var quad: TextQuad, var lastSeen: Long,
        var candidate: String, var key: String, var votes: Int = 1,
        var candidateSince: Long, var accepted: String? = null, var acceptedKey: String? = null,
        var showAccepted: Boolean = false
    )
    private val tracks = mutableListOf<Track>()
    private var nextID = 0L
    private var lastUpdate = Long.MIN_VALUE

    fun clear() { tracks.clear(); lastUpdate = Long.MIN_VALUE }

    fun update(observations: List<TextObservation>, now: Long): List<TrackedText> {
        if (now < lastUpdate) clear()
        lastUpdate = now
        tracks.removeAll { now - it.lastSeen > 1200 }
        val unused = tracks.toMutableList()
        var matched = false
        for (observation in observations.take(12)) {
            val value = observation.text.trim().replace(Regex("\\s+"), " ")
            if (value.length !in 2..1500 || !observation.quad.valid() || observation.confidence < 0.45f ||
                value.none { it.isLetter() }) continue
            val key = key(value)
            val best = unused.mapNotNull { track ->
                val overlap = track.quad.overlap(observation.quad)
                val distance = track.quad.center.distance(observation.quad.center) /
                    hypot(track.quad.width, track.quad.height).coerceAtLeast(1f)
                if (overlap <= 0.22f && distance >= 0.65f) return@mapNotNull null
                val similarity = similarity(track.key, key)
                val sizeRatio = observation.quad.width / track.quad.width
                if (sizeRatio !in 0.45f..2.2f ||
                    !(overlap > 0.22f || similarity > 0.82f && distance < 0.65f)) null
                else track to (overlap * 2 + similarity - distance * 0.4f)
            }.maxByOrNull { it.second }?.first
            if (best == null) {
                val track = Track(++nextID, observation.quad, now, value, key, candidateSince = now)
                tracks += track
            } else {
                matched = true
                unused.remove(best)
                best.quad = best.quad.smooth(observation.quad)
                best.lastSeen = now
                if (key == best.key) best.votes++ else {
                    best.candidate = value; best.key = key; best.votes = 1; best.candidateSince = now
                }
                // A radically different text in the same place must never inherit its translation.
                best.showAccepted = best.acceptedKey?.let { similarity(it, key) >= 0.78f } ?: false
                val requiredVotes = if (best.accepted == null) 2 else 3
                if (best.votes >= requiredVotes && now - best.candidateSince >= 100) {
                    best.accepted = best.candidate; best.acceptedKey = best.key; best.showAccepted = true
                }
            }
        }
        // A large scene change should not leave unrelated translations floating on the new image.
        if (observations.isNotEmpty() && !matched) {
            unused.forEach { it.showAccepted = false }
        }
        while (tracks.size > 48) tracks.removeAt(0)
        return snapshot(now)
    }

    /** A frozen bitmap has no inter-frame uncertainty, so no temporal voting is needed. */
    fun stillImage(observations: List<TextObservation>, now: Long): List<TrackedText> {
        clear()
        update(observations, now)
        tracks.forEach { it.accepted = it.candidate; it.acceptedKey = it.key; it.showAccepted = true }
        return snapshot(now)
    }

    fun snapshot(now: Long): List<TrackedText> = tracks.mapNotNull { track ->
        val age = now - track.lastSeen
        if (age !in 0..HOLD_MS) null else {
            val opacity = if (age <= 65) 1f else (1f - (age - 65).toFloat() / (HOLD_MS - 65)).coerceIn(0f, 1f)
            TrackedText(track.id, track.accepted ?: track.candidate, track.quad, opacity,
                track.accepted != null && track.showAccepted)
        }
    }

    companion object {
        const val HOLD_MS = 450L
        fun key(text: String) = text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        private fun similarity(a: String, b: String): Float {
            if (a == b) return 1f
            // Bound matching cost even for large paragraphs.
            val x = a.take(160); val y = b.take(160)
            if (x.isEmpty() || y.isEmpty()) return 0f
            var previous = IntArray(y.length + 1) { it }
            for (i in x.indices) {
                val next = IntArray(y.length + 1); next[0] = i + 1
                for (j in y.indices) next[j + 1] = minOf(next[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (x[i] == y[j]) 0 else 1)
                previous = next
            }
            return 1f - previous[y.length].toFloat() / max(x.length, y.length)
        }
    }
}
