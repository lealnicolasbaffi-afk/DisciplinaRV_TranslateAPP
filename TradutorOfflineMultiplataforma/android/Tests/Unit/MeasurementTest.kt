package br.edu.tradutoroffline.tests

import br.edu.tradutoroffline.domain.Direction
import br.edu.tradutoroffline.domain.Measurement
import br.edu.tradutoroffline.domain.Origin
import br.edu.tradutoroffline.domain.Quality
import br.edu.tradutoroffline.domain.TurnTiming
import br.edu.tradutoroffline.persistence.MeasurementStore
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every value below is a synthetic fixture, never a physical Android measurement. */
class MeasurementTest {
    private val requiredKeys = setOf(
        "id", "date", "platform", "device", "system", "direction", "input", "output",
        "rawInput", "edited", "origin", "airplaneModeConfirmed", "failure", "notes",
        "transcriptionMS", "textMS", "audioStartMS", "translationMS",
        "transcriptionQuality", "translationQuality",
    )

    private fun synthetic() = Measurement(
        id = "00000000-0000-4000-8000-000000000001",
        date = "2000-01-01T00:00:00Z",
        device = "FIXTURE SINTÉTICA — SEM APARELHO",
        system = "FIXTURE SINTÉTICA — SEM SISTEMA EXECUTADO",
        direction = Direction.PT_EN.label,
        origin = Origin.TEXT.label,
        input = "Texto sintético, com acentuação e \"aspas\".\nOutra linha.",
        rawInput = "Texto sintético, com acentuação e \"aspas\".\nOutra linha.",
        notes = "TESTE UNITÁRIO SINTÉTICO; NÃO É RESULTADO FÍSICO.",
    )

    @Test fun `null measurements remain explicit nulls with every schema key`() {
        val row = synthetic()
        val encoded = MeasurementStore.encode(row)
        assertEquals(requiredKeys, encoded.keys().asSequence().toSet())
        for (key in listOf("failure", "transcriptionMS", "textMS", "audioStartMS", "translationMS")) {
            assertTrue("Chave obrigatória ausente: $key", encoded.has(key))
            assertTrue("Ausência de medida deve permanecer null: $key", encoded.isNull(key))
        }
        assertFalse(encoded.getBoolean("airplaneModeConfirmed"))
        assertFalse(encoded.getBoolean("edited"))
        assertEquals(row, MeasurementStore.decode(encoded))
    }

    @Test fun `export array round trips populated synthetic values and failures`() {
        val first = synthetic()
        val second = synthetic().copy(
            id = "00000000-0000-4000-8000-000000000002",
            direction = Direction.EN_PT.label,
            origin = Origin.FILE.label,
            input = "Synthetic corrected input",
            rawInput = "Synthetic original input",
            output = "Saída sintética",
            edited = true,
            failure = "ERRO SINTÉTICO DE TESTE",
            transcriptionMS = 100.0,
            textMS = 500.0,
            audioStartMS = 700.0,
            translationMS = 40.0,
            transcriptionQuality = Quality.UNDERSTANDABLE.label,
            translationQuality = Quality.INCORRECT.label,
        )
        val array = JSONArray(MeasurementStore.encodeAll(listOf(first, second)))
        assertEquals(2, array.length())
        val restored = (0 until array.length()).map { index ->
            val encoded = array.getJSONObject(index)
            assertEquals(requiredKeys, encoded.keys().asSequence().toSet())
            MeasurementStore.decode(encoded)
        }
        assertEquals(listOf(first, second), restored)
        assertEquals(0, JSONArray(MeasurementStore.encodeAll(emptyList())).length())
    }

    @Test fun `correction preserves original identity and origin and clears obsolete translation`() {
        val original = synthetic().copy(
            origin = Origin.MICROPHONE.label,
            output = "Tradução sintética anterior",
            failure = "Falha sintética anterior",
            transcriptionMS = 100.0,
            textMS = 500.0,
            audioStartMS = 700.0,
            translationMS = 40.0,
            transcriptionQuality = Quality.UNDERSTANDABLE.label,
            translationQuality = Quality.CORRECT.label,
        )
        val corrected = original.correcting("Correção sintética da transcrição")
        assertEquals(original.id, corrected.id)
        assertEquals(original.date, corrected.date)
        assertEquals(original.direction, corrected.direction)
        assertEquals(original.rawInput, corrected.rawInput)
        assertEquals(original.origin, corrected.origin)
        assertEquals(original.transcriptionMS, corrected.transcriptionMS)
        assertEquals(original.transcriptionQuality, corrected.transcriptionQuality)
        assertEquals(original.notes, corrected.notes)
        assertTrue(corrected.edited)
        assertEquals("", corrected.output)
        assertNull(corrected.failure)
        assertNull(corrected.textMS)
        assertNull(corrected.audioStartMS)
        assertNull(corrected.translationMS)
        assertEquals(Quality.UNRATED.label, corrected.translationQuality)
        assertEquals(corrected, MeasurementStore.decode(MeasurementStore.encode(corrected)))

        val reverted = corrected.correcting(original.rawInput)
        assertFalse(reverted.edited)
        assertEquals(original.rawInput, reverted.rawInput)
        assertEquals(original.origin, reverted.origin)
    }

    @Test fun `typed turns never acquire speech latencies without a reference`() {
        val timing = TurnTiming()
        assertNull(timing.elapsed(0L))
        assertNull(timing.elapsed(10_000L))
    }

    @Test fun `monotonic timing keeps elapsed values nonnegative and resets to null`() {
        val timing = TurnTiming(referenceMS = 10_000L)
        assertEquals(250.0, requireNotNull(timing.elapsed(10_250L)), 0.0)
        assertEquals(0.0, requireNotNull(timing.elapsed(10_000L)), 0.0)
        assertEquals(0.0, requireNotNull(timing.elapsed(9_000L)), 0.0)
        timing.referenceMS = null
        assertNull(timing.elapsed(20_000L))
    }
}
