package br.edu.tradutoroffline.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.edu.tradutoroffline.domain.Direction
import br.edu.tradutoroffline.services.audio.WavFile
import br.edu.tradutoroffline.services.speech.LocalSpeech
import br.edu.tradutoroffline.services.speech.ModelArchive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Real native libraries and real models; isolated test files never enter user measurements. */
@RunWith(AndroidJUnit4::class)
class VoskNativeTest {
    @Test fun importsBothOfficialModelsAndRejectsSilenceInBothDirections() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue("Baixe os ZIPs em work/models e reconstrua o APK de testes.",
            ModelArchive.specifications.all { "${it.directory}.zip" in assets.list("").orEmpty() })
        val context = isolatedContext("vosk")
        val speech = LocalSpeech(context)
        try {
            for (spec in ModelArchive.specifications) assets.open("${spec.directory}.zip").use { speech.importModel(spec.language, it) }
            speech.prepare()
            assertTrue(speech.modelStatus().contains("PT: preparado localmente"))
            assertTrue(speech.modelStatus().contains("EN: preparado localmente"))
            val silence = File(context.filesDir, "silence.wav")
            RandomAccessFile(silence, "rw").use {
                WavFile.writeHeader(it, 32_000)
                it.write(ByteArray(32_000))
            }
            for (direction in Direction.entries) {
                try {
                    speech.transcribe(silence, direction)
                    fail("Silêncio não deve produzir uma transcrição válida.")
                } catch (expected: IOException) {
                    assertTrue(expected.message.orEmpty().contains("Nenhuma fala"))
                }
            }
        } finally {
            speech.close()
            context.filesDir.deleteRecursively()
        }
    }
}
