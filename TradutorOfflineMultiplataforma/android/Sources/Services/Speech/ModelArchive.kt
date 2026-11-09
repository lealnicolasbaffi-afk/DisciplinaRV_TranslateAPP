package br.edu.tradutoroffline.services.speech

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Bounded ZIP extraction; no native/Android dependency so validation can run on the JVM. */
object ModelArchive {
    data class Spec(val language: String, val directory: String) {
        val url: String get() = "https://alphacephei.com/vosk/models/$directory.zip"
    }

    val specifications = listOf(
        Spec("pt", "vosk-model-small-pt-0.3"),
        Spec("en", "vosk-model-small-en-us-0.15"),
    )
    const val MAX_COMPRESSED_BYTES = 100L * 1_024 * 1_024
    const val MAX_EXPANDED_BYTES = 256L * 1_024 * 1_024
    private const val MAX_ENTRY_BYTES = 128L * 1_024 * 1_024
    private const val MAX_ENTRIES = 1_024

    fun spec(language: String): Spec = specifications.firstOrNull { it.language == language }
        ?: throw IllegalArgumentException("Idioma inválido. Escolha português (pt) ou inglês (en).")

    /** Destination must be new and private; caller removes it if this function fails. */
    fun extract(input: InputStream, destination: File, spec: Spec, checkCancellation: () -> Unit = {}): File {
        check(destination.isDirectory && destination.listFiles()?.isEmpty() == true) {
            "A pasta temporária do modelo não está vazia. Tente importar novamente."
        }
        val rootPath = destination.canonicalPath + File.separator
        val seen = HashSet<String>()
        var expanded = 0L
        var entries = 0
        ZipInputStream(LimitedInputStream(input, MAX_COMPRESSED_BYTES)).use { zip ->
            val buffer = ByteArray(32_768)
            while (true) {
                checkCancellation()
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IOException("ZIP contém arquivos demais. Importe somente o modelo pequeno oficial.")
                val name = entry.name
                val segments = name.trimEnd('/').split('/')
                if (name.length !in 1..256 || name.startsWith('/') || '\\' in name || ':' in name ||
                    segments.any { it.isEmpty() || it == "." || it == ".." || it.any(Char::isISOControl) } ||
                    segments.first() != spec.directory || !seen.add(name.trimEnd('/'))
                ) throw IOException("ZIP com caminho inválido, duplicado ou de outro idioma. Importe ${spec.directory}.zip sem modificar.")
                val target = File(destination, name)
                if (!target.canonicalPath.startsWith(rootPath)) throw IOException("Caminho inseguro no ZIP do modelo.")
                if (entry.isDirectory) {
                    if (!target.isDirectory && !target.mkdirs()) throw IOException("Não foi possível extrair o modelo. Verifique o espaço livre.")
                    if (zip.read() != -1) throw IOException("Pasta com conteúdo inválido no ZIP do modelo.")
                } else {
                    if (entry.size > MAX_ENTRY_BYTES) throw IOException("Arquivo do modelo grande demais. Use o ZIP pequeno oficial.")
                    val parent = target.parentFile ?: throw IOException("Caminho inválido no ZIP do modelo.")
                    if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Não foi possível criar a pasta do modelo. Verifique o espaço livre.")
                    var fileBytes = 0L
                    target.outputStream().use { output ->
                        while (true) {
                            checkCancellation()
                            val count = zip.read(buffer)
                            if (count == -1) break
                            if (count == 0) continue
                            fileBytes += count
                            expanded += count
                            if (fileBytes > MAX_ENTRY_BYTES || expanded > MAX_EXPANDED_BYTES) {
                                throw IOException("ZIP excede o limite de extração de 256 MiB. Use o modelo pequeno oficial.")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                zip.closeEntry()
            }
        }
        if (entries == 0) throw IOException("O arquivo não contém um modelo ZIP. Importe ${spec.directory}.zip.")
        return File(destination, spec.directory).also(::validate)
    }

    fun validate(directory: File) {
        fun exists(path: String) = File(directory, path).let { it.isFile && it.length() > 0 }
        // Vosk itself supports both layouts. PT 0.3 is the legacy flat layout;
        // EN-US 0.15 is v2. Requiring only v2 would reject the official PT ZIP.
        val modern = exists("am/final.mdl") && exists("conf/model.conf")
        val required = if (modern) listOf("am/final.mdl", "conf/model.conf", "conf/mfcc.conf")
            else listOf("final.mdl", "mfcc.conf")
        val missing = required.firstOrNull { !exists(it) }
        if (missing != null) throw IOException("Modelo incompleto: falta $missing. Importe novamente o ZIP pequeno oficial.")
        val graph = if (modern) "graph/" else ""
        if (!exists("${graph}HCLG.fst") &&
            !(exists("${graph}HCLr.fst") && exists("${graph}Gr.fst") && exists("${graph}disambig_tid.int"))
        ) throw IOException("Modelo incompleto: falta o grafo de reconhecimento. Importe novamente o ZIP oficial.")
        if (File(directory, "ivector").exists()) {
            val vectorFiles = listOf("final.ie", "final.dubm", "final.mat", "global_cmvn.stats", "splice.conf")
            if (vectorFiles.any { !exists("ivector/$it") } || !File(directory, "ivector/online_cmvn.conf").isFile) {
                throw IOException("Modelo incompleto: faltam arquivos ivector. Importe novamente o ZIP oficial.")
            }
        }
    }

    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var total = 0L

        override fun read(): Int = `in`.read().also { if (it >= 0) account(1) }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            `in`.read(bytes, offset, length).also { if (it > 0) account(it) }

        private fun account(count: Int) {
            total += count
            if (total > limit) throw IOException("ZIP maior que 100 MiB. Use o modelo pequeno oficial.")
        }
    }
}
