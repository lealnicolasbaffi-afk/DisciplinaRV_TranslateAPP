package br.edu.tradutoroffline.services.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Synthetic format fixtures only; these do not represent microphone/device tests. */
class WavFileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun acceptsStandardOneSecondPcm() {
        val info = inspect(wav("fmt " to format(), "data" to ByteArray(32_000)))
        assertEquals(44, info.dataOffset)
        assertEquals(32_000, info.dataBytes)
        assertEquals(1_000, info.durationMS)
    }

    @Test fun skipsPaddedMetadataRatherThanAssuming44ByteHeader() {
        val info = inspect(wav("JUNK" to byteArrayOf(1, 2, 3), "fmt " to format(), "data" to ByteArray(32_000)))
        assertEquals(56, info.dataOffset)
        assertEquals(1_000, info.durationMS)
    }

    @Test fun acceptsDataBeforeFormatAndMetadataAfterData() {
        val info = inspect(wav("data" to ByteArray(32_000), "LIST" to byteArrayOf(1, 2), "fmt " to format()))
        assertEquals(20, info.dataOffset)
        assertEquals(1_000, info.durationMS)
    }

    @Test fun accepts20SecondsButRejectsOneSampleBeyondLimit() {
        assertEquals(20_000, inspect(wav("fmt " to format(), "data" to ByteArray(640_000))).durationMS)
        assertThrows(IOException::class.java) { inspect(wav("fmt " to format(), "data" to ByteArray(640_002))) }
    }

    @Test fun rejectsStereoFloatDifferentRateAndBitDepthWithoutConversion() {
        for (invalid in listOf(format(channels = 2), format(codec = 3), format(rate = 44_100), format(bits = 24))) {
            assertThrows(IOException::class.java) { inspect(wav("fmt " to invalid, "data" to ByteArray(32_000))) }
        }
    }

    @Test fun rejectsInconsistentByteRateAndBlockAlignment() {
        val rate = format().apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(8, 99) }
        val alignment = format().apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putShort(12, 4) }
        for (invalid in listOf(rate, alignment)) {
            assertThrows(IOException::class.java) { inspect(wav("fmt " to invalid, "data" to ByteArray(32_000))) }
        }
    }

    @Test fun rejectsEmptyOddAndDuplicatedDataChunks() {
        for (invalid in listOf(
            wav("fmt " to format(), "data" to byteArrayOf()),
            wav("fmt " to format(), "data" to byteArrayOf(1, 2, 3)),
            wav("fmt " to format(), "data" to ByteArray(32), "data" to ByteArray(32)),
        )) assertThrows(IOException::class.java) { inspect(invalid) }
    }

    @Test fun rejectsTruncatedFileAndForgedRiffLength() {
        val valid = wav("fmt " to format(), "data" to ByteArray(32_000))
        assertThrows(IOException::class.java) { inspect(valid.copyOf(valid.size - 2)) }
        val forged = valid.copyOf().apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(4, Int.MAX_VALUE) }
        assertThrows(IOException::class.java) { inspect(forged) }
    }

    @Test fun rejectsMissingFormatOrDataAndNonRiffFiles() {
        for (invalid in listOf(
            wav("JUNK" to ByteArray(32), "data" to ByteArray(32)),
            wav("fmt " to format(), "LIST" to ByteArray(32)),
            ByteArray(80),
        )) assertThrows(IOException::class.java) { inspect(invalid) }
    }

    @Test fun recordedHeaderHasExpectedPcmRateAndSizes() {
        val file = temporary.newFile()
        RandomAccessFile(file, "rw").use { target ->
            WavFile.writeHeader(target, 32_000)
            target.write(ByteArray(32_000))
        }
        val header = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(32_036, header.getInt(4))
        assertEquals(16_000, header.getInt(24))
        assertEquals(32_000, header.getInt(28))
        assertEquals(32_000, header.getInt(40))
        assertEquals(1_000, WavFile.inspect(file).durationMS)
    }

    private fun inspect(bytes: ByteArray): WavFile.Info =
        temporary.newFile().also { it.writeBytes(bytes) }.let(WavFile::inspect)

    private fun format(codec: Int = 1, channels: Int = 1, rate: Int = 16_000, bits: Int = 16): ByteArray =
        ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(codec.toShort()); putShort(channels.toShort()); putInt(rate)
            putInt(rate * channels * bits / 8); putShort((channels * bits / 8).toShort()); putShort(bits.toShort())
        }.array()

    private fun wav(vararg chunks: Pair<String, ByteArray>): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray())
            for ((id, data) in chunks) {
                write(id.toByteArray())
                write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array())
                write(data)
                if (data.size % 2 != 0) write(0)
            }
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array())
            write(body)
        }.toByteArray()
    }
}
