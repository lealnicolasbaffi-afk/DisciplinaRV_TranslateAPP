package br.edu.tradutoroffline.services.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Tiny structural fixtures, never actual recognition models or physical ASR results. */
class ModelArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun acceptsOfficialPortugueseLegacyLayout() {
        val spec = ModelArchive.spec("pt")
        val output = ModelArchive.extract(zip(legacy(spec.directory)), temporary.newFolder(), spec)
        assertTrue(output.resolve("final.mdl").isFile)
        assertFalse(output.resolve("am").exists())
    }

    @Test fun acceptsOfficialEnglishV2LayoutAndEmptyOptionalConfiguration() {
        val spec = ModelArchive.spec("en")
        val files = modern(spec.directory) + listOf(
            "${spec.directory}/ivector/final.ie", "${spec.directory}/ivector/final.dubm",
            "${spec.directory}/ivector/final.mat", "${spec.directory}/ivector/global_cmvn.stats",
            "${spec.directory}/ivector/splice.conf", "${spec.directory}/ivector/online_cmvn.conf",
        )
        val output = ModelArchive.extract(zip(files, empty = files.last()), temporary.newFolder(), spec)
        assertTrue(output.resolve("am/final.mdl").isFile)
        assertEquals(0L, output.resolve("ivector/online_cmvn.conf").length())
    }

    @Test fun rejectsMissingAcousticModelGraphAndPartialIvector() {
        val spec = ModelArchive.spec("en")
        for (files in listOf(
            modern(spec.directory).filterNot { it.endsWith("final.mdl") },
            modern(spec.directory).filterNot { it.endsWith("Gr.fst") },
            modern(spec.directory) + "${spec.directory}/ivector/final.ie",
        )) assertThrows(IOException::class.java) { ModelArchive.extract(zip(files), temporary.newFolder(), spec) }
    }

    @Test fun rejectsPathsThatCouldEscapeExtractionDirectory() {
        val spec = ModelArchive.spec("pt")
        for (name in listOf(
            "${spec.directory}/../../outside", "/${spec.directory}/absolute", "${spec.directory}/../outside",
            "${spec.directory}\\outside", "C:/${spec.directory}/outside", "${spec.directory}//empty-segment",
        )) {
            val parent = temporary.newFolder()
            val destination = parent.resolve("extract").apply { mkdir() }
            assertThrows(IOException::class.java) { ModelArchive.extract(zip(listOf(name)), destination, spec) }
            assertFalse(parent.resolve("outside").exists())
        }
    }

    @Test fun rejectsOtherLanguageAndEmptyOrNonZipInput() {
        val spec = ModelArchive.spec("pt")
        for (input in listOf(
            zip(modern(ModelArchive.spec("en").directory)), zip(emptyList()), ByteArrayInputStream(byteArrayOf(1, 2, 3)),
        )) assertThrows(IOException::class.java) { ModelArchive.extract(input, temporary.newFolder(), spec) }
    }

    @Test fun rejectsDeclaredOversizedEntryBeforeAllocatingOrWritingIt() {
        val spec = ModelArchive.spec("pt")
        val name = "${spec.directory}/huge.bin".toByteArray()
        val size = 129 * 1_024 * 1_024
        // Deliberately forged local ZIP header: refusal must happen before the nonexistent body is read.
        val bytes = ByteBuffer.allocate(30 + name.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x04034b50); putShort(20); putShort(0); putShort(0); putShort(0); putShort(0)
            putInt(0); putInt(size); putInt(size); putShort(name.size.toShort()); putShort(0); put(name)
        }.array()
        val destination = temporary.newFolder()
        val failure = assertThrows(IOException::class.java) {
            ModelArchive.extract(ByteArrayInputStream(bytes), destination, spec)
        }
        assertTrue(failure.message!!.contains("grande demais"))
        assertFalse(destination.resolve(spec.directory).exists())
    }

    @Test fun rejectsTooManyEntries() {
        val spec = ModelArchive.spec("pt")
        val files = (0..1_024).map { "${spec.directory}/file-$it" }
        val failure = assertThrows(IOException::class.java) { ModelArchive.extract(zip(files), temporary.newFolder(), spec) }
        assertTrue(failure.message!!.contains("arquivos demais"))
    }

    @Test fun checksCancellationDuringExtraction() {
        val spec = ModelArchive.spec("pt")
        var checks = 0
        val failure = assertThrows(IOException::class.java) {
            ModelArchive.extract(zip(legacy(spec.directory)), temporary.newFolder(), spec) {
                if (++checks == 3) throw IOException("cancelled-test")
            }
        }
        assertEquals("cancelled-test", failure.message)
    }

    private fun legacy(root: String) = listOf("$root/final.mdl", "$root/mfcc.conf", "$root/HCLr.fst", "$root/Gr.fst", "$root/disambig_tid.int")

    private fun modern(root: String) = listOf(
        "$root/am/final.mdl", "$root/conf/model.conf", "$root/conf/mfcc.conf", "$root/graph/HCLr.fst",
        "$root/graph/Gr.fst", "$root/graph/disambig_tid.int",
    )

    private fun zip(names: List<String>, empty: String? = null): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for (name in names) {
                zip.putNextEntry(ZipEntry(name))
                if (name != empty) zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
