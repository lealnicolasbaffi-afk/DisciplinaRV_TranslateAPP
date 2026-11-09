package br.edu.tradutoroffline.services.speech

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Network access is explicit during setup; recognition never invokes this downloader. */
object ModelDownload {
    fun download(spec: ModelArchive.Spec, destination: File, progress: (Long, Long?) -> Unit, checkCancellation: () -> Unit) {
        val connection = URL(spec.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        try {
            checkCancellation()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("O servidor do modelo respondeu ${connection.responseCode}. Tente novamente com Wi-Fi ou importe o ZIP oficial.")
            }
            val size = connection.contentLengthLong.takeIf { it >= 0 }
            connection.inputStream.use { input ->
                copyVerified(input, destination, spec.sha256, size, progress = progress, checkCancellation = checkCancellation)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** A failed, interrupted, truncated or altered download never reaches the model importer. */
    internal fun copyVerified(
        input: InputStream, destination: File, expectedHash: String, expectedBytes: Long?,
        maxBytes: Long = ModelArchive.MAX_COMPRESSED_BYTES,
        progress: (Long, Long?) -> Unit = { _, _ -> }, checkCancellation: () -> Unit = {},
    ) {
        var complete = false
        try {
            if (expectedBytes != null && expectedBytes > maxBytes) throw IOException("Download do modelo excede o limite permitido.")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            destination.outputStream().use { output ->
                val buffer = ByteArray(32_768)
                while (true) {
                    checkCancellation()
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (count == 0) continue
                    total += count
                    if (total > maxBytes) throw IOException("Download do modelo excede o limite permitido.")
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    progress(total, expectedBytes)
                }
                output.fd.sync()
            }
            checkCancellation()
            if (expectedBytes != null && total != expectedBytes) throw IOException("Download incompleto. Tente novamente com Wi-Fi.")
            val actualHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (actualHash != expectedHash) throw IOException("O modelo não passou na verificação SHA-256. Baixe novamente o ZIP oficial.")
            complete = true
        } finally {
            if (!complete) destination.delete()
        }
    }
}
