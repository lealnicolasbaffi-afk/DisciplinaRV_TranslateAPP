package br.edu.tradutoroffline.features.camera

import org.junit.Assert.*
import org.junit.Test

class TextRegionTrackerTest {
    private fun quad(x: Float = 0f, y: Float = 0f) = TextQuad(listOf(
        Vertex(x, y), Vertex(x + 200, y), Vertex(x + 200, y + 50), Vertex(x, y + 50)))
    private fun observation(text: String = "The station", x: Float = 0f) = TextObservation(text, quad(x))

    @Test fun translationWaitsForRepeatedRecognition() {
        val tracker = TextRegionTracker()
        assertFalse(tracker.update(listOf(observation()), 0).single().confirmed)
        assertTrue(tracker.update(listOf(observation()), 120).single().confirmed)
    }

    @Test fun tinyOcrJitterKeepsIdentityAndDampensMovement() {
        val tracker = TextRegionTracker()
        val first = tracker.update(listOf(observation()), 0).single()
        val next = tracker.update(listOf(observation(x = 1f)), 120).single()
        assertEquals(first.id, next.id)
        assertTrue(next.quad.left in 0.01f..0.5f)
    }

    @Test fun realCameraMotionFollowsSurfaceWithoutLongSmoothingLag() {
        val tracker = TextRegionTracker()
        tracker.update(listOf(observation()), 0)
        val next = tracker.update(listOf(observation(x = 50f)), 120).single()
        assertEquals(50f, next.quad.left, 0.01f)
        assertTrue(next.confirmed)
    }

    @Test fun missingFrameDoesNotImmediatelyLoseTranslationButExpires() {
        val tracker = TextRegionTracker()
        tracker.update(listOf(observation()), 0)
        tracker.update(listOf(observation()), 120)
        assertTrue(tracker.update(emptyList(), 180).single().confirmed)
        assertTrue(tracker.snapshot(120 + TextRegionTracker.HOLD_MS + 1).isEmpty())
    }

    @Test fun newTextAtSameLocationCannotInheritOldTranslation() {
        val tracker = TextRegionTracker()
        tracker.update(listOf(observation("EXIT")), 0)
        tracker.update(listOf(observation("EXIT")), 120)
        assertFalse(tracker.update(listOf(observation("ENTRY")), 180).single().confirmed)
        tracker.update(listOf(observation("ENTRY")), 240)
        val accepted = tracker.update(listOf(observation("ENTRY")), 310).single()
        assertTrue(accepted.confirmed)
        assertEquals("ENTRY", accepted.text)
    }

    @Test fun isolatedSmallOcrMistakeDoesNotReplaceStableText() {
        val tracker = TextRegionTracker()
        tracker.update(listOf(observation()), 0)
        tracker.update(listOf(observation()), 120)
        val noisy = tracker.update(listOf(observation("The statlon")), 180).single()
        assertTrue(noisy.confirmed)
        assertEquals("The station", noisy.text)
        assertEquals("The station", tracker.update(listOf(observation()), 240).single().text)
    }

    @Test fun duplicateWordsInSeparateLocationsRemainSeparateTracks() {
        val tracker = TextRegionTracker()
        val list = listOf(observation("OPEN", 0f), observation("OPEN", 400f))
        tracker.update(list, 0)
        val confirmed = tracker.update(list.reversed(), 120)
        assertEquals(2, confirmed.map { it.id }.distinct().size)
        assertTrue(confirmed.all { it.confirmed })
    }

    @Test fun invalidOrLowConfidenceRegionsAreRejected() {
        val tracker = TextRegionTracker()
        val bowTie = TextQuad(listOf(Vertex(0f, 0f), Vertex(50f, 50f), Vertex(50f, 0f), Vertex(0f, 50f)))
        assertTrue(tracker.update(listOf(TextObservation("bad", bowTie),
            TextObservation("weak", quad(), 0.2f), observation("123")), 0).isEmpty())
    }

    @Test fun perspectiveCornersRetainSlope() {
        val tracker = TextRegionTracker()
        val tilted = TextQuad(listOf(Vertex(10f, 20f), Vertex(220f, 40f), Vertex(200f, 100f), Vertex(0f, 80f)))
        assertTrue(tilted.valid())
        val seen = tracker.update(listOf(TextObservation("Surface", tilted)), 0).single()
        assertEquals(tilted, seen.quad)
    }

    @Test fun resettingDirectionRequiresFreshConsensus() {
        val tracker = TextRegionTracker()
        tracker.update(listOf(observation()), 0)
        tracker.update(listOf(observation()), 120)
        tracker.clear()
        assertFalse(tracker.update(listOf(observation()), 180).single().confirmed)
    }
    @Test fun slowRecognitionKeepsIdentityAcrossLongFrameIntervals() {
        val tracker = TextRegionTracker()
        val first = tracker.update(listOf(observation()), 0).single()
        val second = tracker.update(listOf(observation()), 600).single()
        assertEquals(first.id, second.id)
        assertTrue(second.confirmed)
    }

    @Test fun frozenImageCanBeTranslatedImmediatelyWithoutFakeFrames() {
        val tracker = TextRegionTracker()
        val still = tracker.stillImage(listOf(observation()), 1000).single()
        assertTrue(still.confirmed)
        assertEquals("The station", still.text)
    }

}
