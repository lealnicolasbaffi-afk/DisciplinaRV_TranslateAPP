package br.edu.tradutoroffline.services.speech

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class ModelDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = "modelo de teste, não é um modelo de fala".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun keepsOnlyCompleteVerifiedDownloads() {
        val file = temporary.newFile()
        var received = 0L
        ModelDownload.copyVerified(ByteArrayInputStream(bytes), file, hash, bytes.size.toLong(), progress = { value, _ -> received = value })
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), received)
    }

    @Test fun rejectsAlteredAndTruncatedDownloadsAndDeletesThem() {
        for ((expectedHash, length) in listOf("0".repeat(64) to bytes.size.toLong(), hash to bytes.size + 1L)) {
            val file = temporary.newFile()
            assertThrows(IOException::class.java) { ModelDownload.copyVerified(ByteArrayInputStream(bytes), file, expectedHash, length) }
            assertFalse(file.exists())
        }
    }

    @Test fun enforcesSizeLimitEvenWithoutContentLength() {
        val file = temporary.newFile()
        assertThrows(IOException::class.java) { ModelDownload.copyVerified(ByteArrayInputStream(bytes), file, hash, null, maxBytes = 8) }
        assertFalse(file.exists())
    }

    @Test fun cancellationDeletesThePartialFile() {
        val file = temporary.newFile()
        var checks = 0
        assertThrows(CancellationException::class.java) {
            ModelDownload.copyVerified(ByteArrayInputStream(bytes), file, hash, null, checkCancellation = {
                if (++checks == 2) throw CancellationException("cancelado")
            })
        }
        assertFalse(file.exists())
    }
}
