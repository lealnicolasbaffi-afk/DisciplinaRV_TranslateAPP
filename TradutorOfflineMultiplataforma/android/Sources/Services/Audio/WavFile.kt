package br.edu.tradutoroffline.services.audio

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** The only audio format sent to Vosk: signed PCM16, mono, 16 kHz, at most 20 s. */
object WavFile {
    const val SAMPLE_RATE = 16_000
    const val MAX_SECONDS = 20
    const val MAX_DATA_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS
    const val MAX_FILE_BYTES = 1_048_576L
    private const val FORMAT_MESSAGE =
        "Use um WAV PCM de 16 bits, mono, 16.000 Hz, com duração entre 1 amostra e 20 segundos."

    data class Info(val dataOffset: Long, val dataBytes: Long) {
        val durationMS: Long get() = dataBytes * 1_000 / (SAMPLE_RATE * 2)
    }

    /** Parse RIFF chunks instead of assuming that all WAV headers have 44 bytes. */
    fun inspect(file: File): Info {
        try {
            RandomAccessFile(file, "r").use { wav ->
                val length = wav.length()
                requireWav(length in 44..MAX_FILE_BYTES)
                requireWav(wav.readFourCC() == "RIFF")
                requireWav(wav.readLE32() + 8 == length)
                requireWav(wav.readFourCC() == "WAVE")
                var hasFormat = false
                var info: Info? = null
                var chunks = 0
                while (wav.filePointer < length) {
                    requireWav(++chunks <= 128 && length - wav.filePointer >= 8)
                    val name = wav.readFourCC()
                    val size = wav.readLE32()
                    val offset = wav.filePointer
                    val end = offset + size
                    val paddedEnd = end + (size and 1)
                    requireWav(end >= offset && paddedEnd <= length)
                    when (name) {
                        "fmt " -> {
                            requireWav(!hasFormat && size >= 16)
                            requireWav(wav.readLE16() == 1) // PCM, never a compressed codec.
                            requireWav(wav.readLE16() == 1)
                            requireWav(wav.readLE32() == SAMPLE_RATE.toLong())
                            requireWav(wav.readLE32() == (SAMPLE_RATE * 2).toLong())
                            requireWav(wav.readLE16() == 2)
                            requireWav(wav.readLE16() == 16)
                            hasFormat = true
                        }
                        "data" -> {
                            requireWav(info == null && size in 2..MAX_DATA_BYTES.toLong() && size % 2 == 0L)
                            info = Info(offset, size)
                        }
                    }
                    wav.seek(paddedEnd)
                }
                requireWav(hasFormat && info != null)
                return info!!
            }
        } catch (exception: IOException) {
            throw IOException("Arquivo de áudio inválido ou incompleto. $FORMAT_MESSAGE", exception)
        }
    }

    /** Reserve/finalize the canonical 44-byte header of an app recording. */
    fun writeHeader(wav: RandomAccessFile, dataBytes: Int) {
        require(dataBytes in 0..MAX_DATA_BYTES && dataBytes % 2 == 0)
        wav.seek(0)
        wav.writeBytes("RIFF")
        wav.writeLE32(36 + dataBytes)
        wav.writeBytes("WAVEfmt ")
        wav.writeLE32(16)
        wav.writeLE16(1)
        wav.writeLE16(1)
        wav.writeLE32(SAMPLE_RATE)
        wav.writeLE32(SAMPLE_RATE * 2)
        wav.writeLE16(2)
        wav.writeLE16(16)
        wav.writeBytes("data")
        wav.writeLE32(dataBytes)
    }

    private fun requireWav(condition: Boolean) {
        if (!condition) throw IOException(FORMAT_MESSAGE)
    }

    private fun RandomAccessFile.readFourCC(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readLE16(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun RandomAccessFile.readLE32(): Long =
        readUnsignedByte().toLong() or (readUnsignedByte().toLong() shl 8) or
            (readUnsignedByte().toLong() shl 16) or (readUnsignedByte().toLong() shl 24)

    private fun RandomAccessFile.writeLE16(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun RandomAccessFile.writeLE32(value: Int) {
        writeLE16(value and 0xffff)
        writeLE16(value ushr 16)
    }
}
