package br.edu.tradutoroffline.services.speech

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.AtomicFile
import br.edu.tradutoroffline.domain.SpeechRecognitionService
import br.edu.tradutoroffline.domain.Direction
import br.edu.tradutoroffline.services.audio.WavFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

/** Vosk consumes only private files. This adapter never makes a network request. */
class LocalSpeech(context: Context) : SpeechRecognitionService {
    private val cache = context.applicationContext.cacheDir
    private val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val directory = File(context.applicationContext.filesDir, "vosk")
    private val mutex = Mutex()
    @Volatile private var closed = false
    @Volatile private var prepared = emptySet<String>()
    @Volatile private var installedBytes = emptyMap<String, Long>()
    // Cached labels avoid disk I/O in the UI; native validity is checked by import/prepare.
    @Volatile private var installed = ModelArchive.specifications.mapNotNull { spec ->
        runCatching { activeModel(spec)?.let { spec.language } }.getOrNull()
    }.toSet()

    override fun modelStatus(): String = ModelArchive.specifications.joinToString(" · ") { spec ->
        val language = if (spec.language == "pt") "PT" else "EN"
        val status = when (spec.language) {
            in prepared -> "$language: preparado localmente"
            in installed -> "$language: importado; toque em Preparar idiomas"
            else -> "$language: importe ${spec.directory}.zip"
        }
        if (spec.language in installed) {
            val size = installedBytes[spec.language]?.let { "$it bytes (arquivos extraídos)" }
                ?: "tamanho indisponível até preparar"
            "$status, $size"
        } else status
    }

    override suspend fun downloadMissingModels(progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        for (spec in ModelArchive.specifications) {
            checkOpen()
            currentCoroutineContext().ensureActive()
            if (mutex.withLock { activeModel(spec) != null }) continue
            check(connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                "Ative o Wi-Fi para baixar os modelos Vosk ou importe os ZIPs oficiais já salvos no aparelho."
            }
            val archive = File.createTempFile("vosk-download-", ".zip", cache)
            try {
                val coroutine = currentCoroutineContext()
                var lastPercent = -1L
                ModelDownload.download(spec, archive, { bytes, size ->
                    val percent = size?.takeIf { it > 0 }?.let { bytes * 100 / it }
                    val bucket = percent ?: (bytes / (1_024 * 1_024))
                    if (bucket != lastPercent) {
                        lastPercent = bucket
                        progress("Baixando modelo ${spec.language.uppercase()}: ${percent?.let { "$it%" } ?: "${bytes / (1_024 * 1_024)} MiB"}…")
                    }
                }, { coroutine.ensureActive(); checkOpen() })
                progress("Validando e instalando modelo ${spec.language.uppercase()}…")
                archive.inputStream().use { importModel(spec.language, it) }
            } finally {
                archive.delete()
            }
        }
    }

    /** An existing model stays active until the new version passes native validation. */
    override suspend fun importModel(language: String, input: InputStream): Unit = input.use { source ->
        withContext(Dispatchers.IO) {
            mutex.withLock {
                checkOpen()
                val spec = ModelArchive.spec(language)
                val languageDirectory = File(directory, language)
                check(languageDirectory.isDirectory || languageDirectory.mkdirs()) {
                    "Não foi possível criar a pasta dos modelos. Verifique o espaço livre."
                }
                val version = UUID.randomUUID().toString()
                val staging = File(languageDirectory, ".import-$version")
                val destination = File(languageDirectory, version)
                check(staging.mkdir()) { "Não foi possível preparar a importação. Verifique o espaço livre." }
                var activated = false
                try {
                    val coroutine = currentCoroutineContext()
                    val extracted = ModelArchive.extract(source, staging, spec) {
                        coroutine.ensureActive()
                        checkOpen()
                    }
                    loadModel(extracted).use { /* Validate model data, not just the ZIP names. */ }
                    coroutine.ensureActive()
                    checkOpen()
                    check(staging.renameTo(destination)) { "Não foi possível salvar o modelo. Verifique o espaço livre." }
                    val pointer = pointer(spec)
                    var stream: FileOutputStream? = null
                    try {
                        stream = pointer.startWrite()
                        stream.write(version.toByteArray(Charsets.US_ASCII))
                        pointer.finishWrite(stream)
                    } catch (exception: Exception) {
                        pointer.failWrite(stream)
                        throw IOException("Não foi possível ativar o modelo. A versão anterior foi preservada.", exception)
                    }
                    activated = true
                    installed = installed + language
                    prepared = prepared + language
                    cacheSize(language, File(destination, spec.directory))
                    // No recognizer can hold an old path while this mutex is held.
                    languageDirectory.listFiles()?.filter { it != destination }?.forEach { it.deleteRecursively() }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: IOException) {
                    throw IOException("Falha ao importar o modelo ${spec.language.uppercase()}: ${exception.message}. Baixe novamente e importe ${spec.directory}.zip oficial, sem modificar.", exception)
                } finally {
                    staging.deleteRecursively()
                    if (!activated) destination.deleteRecursively()
                }
            }
        }
        Unit
    }

    /** Load sequentially and release, keeping only one language model resident at a time. */
    override suspend fun prepare() = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkOpen()
            val missing = ModelArchive.specifications.filter { activeModel(it) == null }
            if (missing.isNotEmpty()) {
                throw IOException("Reconhecimento local indisponível. Importe ${missing.joinToString(" e ") { it.directory + ".zip" }} e toque em Preparar idiomas.")
            }
            for (spec in ModelArchive.specifications) {
                currentCoroutineContext().ensureActive()
                checkOpen()
                val modelDirectory = activeModel(spec) ?: throw IOException("Modelo ausente. Importe ${spec.directory}.zip novamente.")
                ModelArchive.validate(modelDirectory)
                loadModel(modelDirectory).use { }
                currentCoroutineContext().ensureActive()
                checkOpen()
                prepared = prepared + spec.language
                cacheSize(spec.language, modelDirectory)
            }
        }
    }

    override suspend fun transcribe(file: File, direction: Direction): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkOpen()
            val spec = ModelArchive.spec(if (direction == Direction.PT_EN) "pt" else "en")
            val modelDirectory = activeModel(spec)
                ?: throw IOException("Reconhecimento local indisponível. Importe ${spec.directory}.zip e prepare os idiomas.")
            val info = WavFile.inspect(file)
            ModelArchive.validate(modelDirectory)
            val parts = mutableListOf<String>()
            loadModel(modelDirectory).use { model ->
                currentCoroutineContext().ensureActive()
                Recognizer(model, WavFile.SAMPLE_RATE.toFloat()).use { recognizer ->
                    RandomAccessFile(file, "r").use { audio ->
                        audio.seek(info.dataOffset)
                        var remaining = info.dataBytes
                        val buffer = ByteArray(8_000)
                        while (remaining > 0) {
                            currentCoroutineContext().ensureActive()
                            checkOpen()
                            val count = minOf(remaining, buffer.size.toLong()).toInt()
                            audio.readFully(buffer, 0, count)
                            if (recognizer.acceptWaveForm(buffer, count)) {
                                val text = JSONObject(recognizer.result).optString("text").trim()
                                if (text.isNotEmpty()) parts += text
                            }
                            remaining -= count
                        }
                        currentCoroutineContext().ensureActive()
                        checkOpen()
                        val finalText = JSONObject(recognizer.finalResult).optString("text").trim()
                        if (finalText.isNotEmpty()) parts += finalText
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            checkOpen()
            parts.joinToString(" ").ifBlank {
                throw IOException("Nenhuma fala foi reconhecida. Fale mais perto do microfone, confirme a direção ou importe um WAV de reserva.")
            }
        }
    }

    /** In-flight native calls release at the next PCM block; no idle native model is retained. */
    override fun close() {
        closed = true
        prepared = emptySet()
    }

    private fun checkOpen() {
        check(!closed) { "O reconhecimento local foi encerrado. Abra a tela novamente." }
    }

    private fun pointer(spec: ModelArchive.Spec) = AtomicFile(File(directory, "active-${spec.language}"))

    private fun cacheSize(language: String, modelDirectory: File) {
        val bytes = measuredBytes(modelDirectory)
        installedBytes = if (bytes == null) installedBytes - language else installedBytes + (language to bytes)
    }

    // Called only from import/prepare on Dispatchers.IO; status never walks the filesystem.
    private fun measuredBytes(modelDirectory: File): Long? = runCatching {
        modelDirectory.walkTopDown().onFail { _, exception -> throw exception }
            .filter { it.isFile }.sumOf { it.length() }
    }.getOrNull()

    private fun activeModel(spec: ModelArchive.Spec): File? {
        val version = try {
            pointer(spec).openRead().use { input ->
                val bytes = ByteArray(37)
                val count = input.read(bytes)
                if (count != 36 || input.read() != -1) return null
                String(bytes, 0, count, Charsets.US_ASCII)
            }
        } catch (_: IOException) {
            return null
        }
        if (!version.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) return null
        return File(File(File(directory, spec.language), version), spec.directory).takeIf { it.isDirectory }
    }

    private fun loadModel(file: File): Model = try {
        Model(file.absolutePath)
    } catch (exception: LinkageError) {
        throw IOException("Vosk não está disponível para a arquitetura deste aparelho. Instale um APK compatível e tente novamente.", exception)
    } catch (exception: IOException) {
        throw IOException("Não foi possível abrir o modelo local. Importe o ZIP oficial novamente e confira o espaço livre.", exception)
    }
}
