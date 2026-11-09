package br.edu.tradutoroffline.services.audio

import br.edu.tradutoroffline.domain.AudioService
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger

class LocalAudio(context: Context) : AudioService {
    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, "audio")
    private val captureMutex = Mutex()
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var closed = false
    @Volatile private var captureStop: CompletableDeferred<Unit>? = null
    /** Logical end of capture, before WAV finalization; same monotonic clock as controller. */
    @Volatile override var lastCaptureStoppedMS: Long? = null
        private set
    // MediaPlayer and focus are always accessed on the main looper.
    private var player: MediaPlayer? = null
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null

    /** The supplied stop signal ends normally; coroutine cancellation discards partial audio. */
    override suspend fun capture(file: File, stopSignal: CompletableDeferred<Unit>): File = withContext(Dispatchers.IO) {
        check(!closed) { "O serviço de áudio foi encerrado. Abra a tela novamente." }
        check(captureMutex.tryLock()) { "Já existe uma gravação em andamento." }
        lastCaptureStoppedMS = null
        captureStop = stopSignal
        val token = generation.incrementAndGet()
        main.post { if (token == generation.get()) releasePlayer() }
        var recorder: AudioRecord? = null
        var staging: File? = null
        try {
            cleanOldCache()
            if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Permita o acesso ao microfone nas configurações do aplicativo e tente novamente.")
            }
            require(!file.exists()) { "O arquivo de gravação já existe. Inicie uma nova rodada." }
            val parent = file.absoluteFile.parentFile ?: throw IOException("Pasta de gravação indisponível.")
            check(parent.isDirectory || parent.mkdirs()) { "Não foi possível criar a pasta de gravação. Verifique o espaço livre." }
            staging = File.createTempFile("capture-", ".part", parent)
            val minBuffer = AudioRecord.getMinBufferSize(
                WavFile.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minBuffer > 0) { "O aparelho não oferece captura PCM mono a 16.000 Hz. Importe um WAV de reserva." }
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, WavFile.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, 6_400),
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "Não foi possível abrir o microfone. Feche outros gravadores e tente novamente."
            }
            if (closed) stopSignal.complete(Unit)
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "O microfone não iniciou a gravação." }
            val started = SystemClock.elapsedRealtimeNanos()
            var dataBytes = 0
            val samples = ShortArray(1_600)
            val pcm = ByteArray(samples.size * 2)
            RandomAccessFile(staging, "rw").use { wav ->
                WavFile.writeHeader(wav, 0)
                while (!stopSignal.isCompleted && !closed && dataBytes < WavFile.MAX_DATA_BYTES &&
                    SystemClock.elapsedRealtimeNanos() - started < WavFile.MAX_SECONDS * 1_000_000_000L
                ) {
                    currentCoroutineContext().ensureActive()
                    if (Build.VERSION.SDK_INT >= 29 && recorder.activeRecordingConfiguration?.isClientSilenced == true) {
                        throw IOException("O sistema silenciou o microfone. Confira a privacidade do microfone, feche outros gravadores e tente novamente.")
                    }
                    val count = recorder.read(
                        samples, 0, minOf(samples.size, (WavFile.MAX_DATA_BYTES - dataBytes) / 2),
                        AudioRecord.READ_NON_BLOCKING,
                    )
                    when {
                        count < 0 -> throw IOException("A captura foi interrompida pelo sistema (código $count). Grave novamente ou importe um WAV.")
                        count == 0 -> delay(10)
                        else -> {
                            for (index in 0 until count) {
                                val sample = samples[index].toInt()
                                pcm[index * 2] = sample.toByte()
                                pcm[index * 2 + 1] = (sample ushr 8).toByte()
                            }
                            wav.write(pcm, 0, count * 2)
                            dataBytes += count * 2
                        }
                    }
                }
                lastCaptureStoppedMS = SystemClock.elapsedRealtime()
                runCatching { recorder.stop() }
                currentCoroutineContext().ensureActive()
                check(dataBytes > 0) { "A gravação ficou vazia. Toque em Gravar e fale antes de parar." }
                WavFile.writeHeader(wav, dataBytes)
                wav.fd.sync()
            }
            WavFile.inspect(staging)
            check(staging.renameTo(file)) { "Não foi possível salvar a gravação. Verifique o espaço livre." }
            file
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            staging?.delete()
            captureStop = null
            captureMutex.unlock()
        }
    }

    /** Input is closed here; imports are bounded before format parsing. No codec fallback. */
    override suspend fun importWav(input: InputStream): File {
        var committed: File? = null
        var delivered = false
        try {
            val result = input.use { source ->
                withContext(Dispatchers.IO) {
                    check(!closed) { "O serviço de áudio foi encerrado. Abra a tela novamente." }
                    cleanOldCache()
                    check(directory.isDirectory || directory.mkdirs()) {
                        "Não foi possível criar a pasta de áudio. Verifique o espaço livre."
                    }
                    val staging = File.createTempFile("reserve-", ".part", directory)
                    val output = File(directory, "reserve-${java.util.UUID.randomUUID()}.wav")
                    try {
                        staging.outputStream().use { target ->
                            val buffer = ByteArray(16_384)
                            var total = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                check(!closed) { "A importação foi encerrada. Abra a tela novamente." }
                                val count = source.read(buffer)
                                if (count == -1) break
                                if (count == 0) continue
                                total += count
                                check(total <= WavFile.MAX_FILE_BYTES) {
                                    "Áudio muito grande. Use WAV PCM16 mono a 16.000 Hz, até 20 segundos (arquivo até 1 MiB)."
                                }
                                target.write(buffer, 0, count)
                            }
                        }
                        WavFile.inspect(staging)
                        currentCoroutineContext().ensureActive()
                        check(staging.renameTo(output)) {
                            "Não foi possível salvar o áudio importado. Verifique o espaço livre."
                        }
                        committed = output
                        output
                    } finally {
                        staging.delete()
                    }
                }
            }
            delivered = true
            return result
        } finally {
            // Also handles prompt cancellation while withContext dispatches its result.
            if (!delivered) committed?.delete()
        }
    }

    override fun play(file: File, onDone: () -> Unit, onError: (String) -> Unit) {
        val token = generation.incrementAndGet()
        main.post {
            if (token != generation.get()) return@post
            releasePlayer()
            if (closed) {
                onError("O serviço de áudio foi encerrado. Abra a tela novamente.")
                return@post
            }
            if (captureMutex.isLocked) {
                onError("Pare a gravação antes de ouvir o original.")
                return@post
            }
            try {
                WavFile.inspect(file)
                val focus = AudioManager.OnAudioFocusChangeListener { change ->
                    if (change < 0 && token == generation.get()) {
                        generation.incrementAndGet()
                        releasePlayer()
                        onError("A reprodução foi interrompida por outro áudio. Toque em Ouvir original novamente.")
                    }
                }
                focusListener = focus
                @Suppress("DEPRECATION")
                val granted = audioManager.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                check(granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "O áudio está ocupado. Tente ouvir o original novamente." }
                val media = MediaPlayer()
                player = media
                media.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                media.setDataSource(file.absolutePath)
                media.setOnPreparedListener {
                    if (token == generation.get() && !closed && player === media) {
                        runCatching { media.start() }.onFailure {
                            releasePlayer()
                            onError("A reprodução não iniciou. Toque em Ouvir original novamente.")
                        }
                    }
                }
                media.setOnCompletionListener {
                    if (token == generation.get() && player === media) {
                        releasePlayer()
                        onDone()
                    }
                }
                media.setOnErrorListener { _, what, extra ->
                    if (token == generation.get() && player === media) {
                        releasePlayer()
                        onError("Não foi possível ouvir o original ($what/$extra). Importe outro WAV ou grave novamente.")
                    }
                    true
                }
                media.prepareAsync()
            } catch (exception: Exception) {
                releasePlayer()
                if (token == generation.get()) onError(exception.message ?: "Não foi possível ouvir o original.")
            }
        }
    }

    override fun stop() {
        val token = generation.incrementAndGet()
        captureStop?.complete(Unit)
        main.post { if (token == generation.get()) releasePlayer() }
    }

    override fun close() {
        closed = true
        stop()
    }

    /** Remove abandoned temporary/cache audio after one day, only from this app's known names. */
    private fun cleanOldCache() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1_000L
        directory.listFiles()?.filter {
            it.isFile && it.lastModified() < cutoff &&
                ((it.name.startsWith("reserve-") && it.extension in setOf("wav", "part")) ||
                    (it.name.startsWith("capture-") && it.extension == "part"))
        }?.forEach { it.delete() }
        appContext.cacheDir.listFiles()?.filter {
            it.isFile && it.lastModified() < cutoff &&
                ((it.name.startsWith("original-") && it.extension == "wav") ||
                    (it.name.startsWith("capture-") && it.extension == "part"))
        }?.forEach { it.delete() }
    }

    @Suppress("DEPRECATION")
    private fun releasePlayer() {
        player?.let { media ->
            media.setOnPreparedListener(null)
            media.setOnCompletionListener(null)
            media.setOnErrorListener(null)
            runCatching { media.release() }
        }
        player = null
        focusListener?.let { audioManager.abandonAudioFocus(it) }
        focusListener = null
    }
}
