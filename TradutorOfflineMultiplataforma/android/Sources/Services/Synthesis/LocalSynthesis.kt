package br.edu.tradutoroffline.services.synthesis

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import br.edu.tradutoroffline.domain.SynthesisService
import br.edu.tradutoroffline.domain.Direction
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Uses only installed voices advertised as offline by the selected Android TTS engine.
 * Voice metadata is supplied by that engine, so real offline behavior requires an airplane-mode test.
 * https://developer.android.com/reference/android/speech/tts/Voice#isNetworkConnectionRequired()
 */
class LocalSynthesis(context: Context) : SynthesisService {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    // Engine creation is deferred to prepare(), so a missing engine never throws from construction.
    private var engine: TextToSpeech? = null
    private var initialization: CompletableDeferred<TextToSpeech>? = null
    private var preparedVoices = emptyMap<String, String>()
    private var pending: Pending? = null
    private var focus: Pair<String, () -> Unit>? = null
    private var closed = false

    private data class Pending(
        val id: String,
        val continuation: CancellableContinuation<Unit>,
        val onStart: () -> Unit,
        var started: Boolean = false,
    )

    override suspend fun prepare(): String = withContext(Dispatchers.Main.immediate) {
        checkOpen()
        check(pending == null) { "Aguarde a reprodução terminar antes de preparar as vozes." }
        preparedVoices = emptyMap()
        val tts = awaitEngine()
        val voices = listOf("pt-BR", "en-US").associateWith { tag ->
            val voice = findLocalVoice(tts, tag)
            selectAndVerify(tts, voice, tag)
            voice.name
        }
        preparedVoices = voices
        "Vozes locais declaradas pelo motor: pt-BR (${voices.getValue("pt-BR")}); en-US (${voices.getValue("en-US")}). Valide em modo avião."
    }

    override suspend fun speak(text: String, direction: Direction, onStart: () -> Unit) = withContext(Dispatchers.Main.immediate) {
        checkOpen()
        require(text.isNotBlank()) { "Não há tradução para reproduzir." }
        require(text.length <= TextToSpeech.getMaxSpeechInputLength()) { "O texto excede o limite de reprodução do Android. Divida em frases menores." }
        check(pending == null) { "Já há uma reprodução em andamento. Pare e tente novamente." }
        val tag = if (direction == Direction.PT_EN) "en-US" else "pt-BR"
        val voiceName = preparedVoices[tag]
            ?: throw IllegalStateException("Vozes não preparadas. Use Preparar idiomas e instale as vozes offline nas configurações de texto para fala do Android.")
        val tts = engine ?: throw IllegalStateException("O motor de voz está indisponível. Use Preparar idiomas novamente.")
        // Re-query before every utterance: a prepared voice may have been removed outside the app.
        val voice = tts.voices.orEmpty().firstOrNull { it.name == voiceName && isLocalVoice(it, tag) }
            ?: throw missingVoice(tag)
        selectAndVerify(tts, voice, tag)
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine<Unit> { continuation ->
            pending = Pending(id, continuation, onStart)
            continuation.invokeOnCancellation {
                runOnMain {
                    if (pending?.id == id) {
                        pending = null
                        engine?.stop()
                        releaseFocus(id)
                    }
                }
            }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            try {
                acquireFocus(id)
                if (!continuation.isActive) {
                    stop()
                    return@suspendCancellableCoroutine
                }
                val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
                if (queued != TextToSpeech.SUCCESS) {
                    finish(id, IllegalStateException("O motor recusou a reprodução. Verifique a voz offline nas configurações do Android."))
                }
            } catch (error: Exception) {
                finish(id, IllegalStateException("Falha ao iniciar a voz local: ${error.message.orEmpty()}", error))
            }
        }
    }

    override fun stop() = runOnMain {
        val old = pending
        pending = null
        engine?.stop()
        releaseFocus()
        old?.continuation?.cancel(CancellationException("Reprodução interrompida."))
    }

    override fun close() = runOnMain {
        if (!closed) {
            closed = true
            stop()
            initialization?.cancel(CancellationException("Serviço de voz encerrado."))
            initialization = null
            engine?.shutdown()
            engine = null
            preparedVoices = emptyMap()
        }
    }

    private suspend fun awaitEngine(): TextToSpeech {
        initialization?.let { return awaitInitialization(it) }
        val ready = CompletableDeferred<TextToSpeech>()
        initialization = ready
        try {
            engine = TextToSpeech(appContext) { status ->
                // An engine may call init synchronously or from another thread. Posting also ensures
                // the constructor result is assigned before we read engine.
                main.post {
                    val tts = engine
                    if (closed || ready.isCompleted || initialization !== ready) return@post
                    if (status != TextToSpeech.SUCCESS || tts == null) {
                        tts?.shutdown()
                        engine = null
                        initialization = null
                        ready.completeExceptionally(IllegalStateException("Motor de voz indisponível. Instale/ative um motor TTS e as vozes offline pt-BR e en-US nas configurações do Android."))
                    } else {
                        try {
                            check(tts.setOnUtteranceProgressListener(listener) == TextToSpeech.SUCCESS) { "O motor não aceita acompanhamento da reprodução." }
                            check(tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()) == TextToSpeech.SUCCESS) { "Não foi possível configurar a saída da voz." }
                            ready.complete(tts)
                        } catch (error: Exception) {
                            tts.shutdown()
                            engine = null
                            initialization = null
                            ready.completeExceptionally(IllegalStateException("Não foi possível preparar o motor de voz: ${error.message.orEmpty()}", error))
                        }
                    }
                }
            }
        } catch (error: Exception) {
            initialization = null
            ready.completeExceptionally(IllegalStateException("Não foi possível iniciar o motor TTS. Verifique as configurações de texto para fala do Android.", error))
        }
        return awaitInitialization(ready)
    }

    private suspend fun awaitInitialization(ready: CompletableDeferred<TextToSpeech>): TextToSpeech = try {
        ready.await()
    } catch (cancelled: CancellationException) {
        // Do not leave a timed-out initialization permanently cached: retry must create a new engine.
        if (initialization === ready && !ready.isCompleted) {
            initialization = null
            ready.cancel(cancelled)
            engine?.shutdown()
            engine = null
        }
        throw cancelled
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = runOnMain {
            val active = pending?.takeIf { it.id == utteranceId && it.continuation.isActive } ?: return@runOnMain
            if (!active.started) {
                active.started = true
                try {
                    // API callback approximates audio availability; it is not an acoustic measurement.
                    active.onStart()
                } catch (error: Exception) {
                    finish(active.id, error)
                }
            }
        }

        override fun onDone(utteranceId: String?) = runOnMain { finish(utteranceId, null) }

        @Deprecated("Required callback for older TTS engines")
        override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR_SYNTHESIS)

        override fun onError(utteranceId: String?, errorCode: Int) = runOnMain {
            finish(utteranceId, IllegalStateException("Falha na voz local (código $errorCode). Verifique as vozes offline instaladas e tente novamente; nenhum fallback de rede foi solicitado."))
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) = runOnMain {
            finish(utteranceId, IllegalStateException("A reprodução foi interrompida pelo motor de voz. Tente novamente."))
        }
    }

    private fun finish(id: String?, error: Throwable?) {
        val active = pending?.takeIf { it.id == id } ?: return
        pending = null
        releaseFocus(active.id)
        if (error != null) engine?.stop()
        if (!active.continuation.isActive) return
        if (error == null) active.continuation.resume(Unit) else active.continuation.resumeWithException(error)
    }

    private fun findLocalVoice(tts: TextToSpeech, tag: String): Voice = tts.voices.orEmpty()
        .filter { isLocalVoice(it, tag) }
        .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.latency }.thenBy { it.name })
        .firstOrNull() ?: throw missingVoice(tag)

    private fun isLocalVoice(voice: Voice, tag: String): Boolean {
        val locale = Locale.forLanguageTag(tag)
        return voice.locale.language.equals(locale.language, ignoreCase = true) &&
            voice.locale.country.equals(locale.country, ignoreCase = true) &&
            !voice.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
    }

    private fun selectAndVerify(tts: TextToSpeech, voice: Voice, tag: String) {
        check(tts.setVoice(voice) == TextToSpeech.SUCCESS) { "O motor recusou a voz offline $tag. Verifique os dados de voz nas configurações do Android." }
        val selected = tts.voice
        check(selected != null && selected.name == voice.name && isLocalVoice(selected, tag)) {
            "O motor não confirmou a voz offline $tag solicitada. Instale uma voz local compatível e prepare novamente."
        }
    }

    private fun missingVoice(tag: String) = IllegalStateException("Voz offline $tag ausente. Nas configurações de texto para fala do Android, instale os dados de voz desse idioma, conclua o download e use Preparar idiomas novamente.")

    @Suppress("DEPRECATION")
    private fun acquireFocus(id: String) {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: throw IllegalStateException("A saída de áudio do Android está indisponível.")
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                // Always post, including synchronous notifications from requestAudioFocus().
                main.post { finish(id, IllegalStateException("A reprodução foi interrompida por outra saída de áudio ou chamada. Tente novamente.")) }
            }
        }
        val result: Int
        val abandon: () -> Unit
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(listener, main)
                .build()
            result = manager.requestAudioFocus(request)
            abandon = { manager.abandonAudioFocusRequest(request); Unit }
        } else {
            result = manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            abandon = { manager.abandonAudioFocus(listener); Unit }
        }
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            abandon()
            throw IllegalStateException("O Android não liberou a saída de áudio. Encerre a chamada ou outra reprodução e tente novamente com o aplicativo aberto.")
        }
        focus = id to abandon
    }

    private fun releaseFocus(id: String? = null) {
        val old = focus?.takeIf { id == null || it.first == id } ?: return
        focus = null
        old.second()
    }

    private fun checkOpen() = check(!closed) { "O serviço de voz foi encerrado. Abra a tela novamente." }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }
}
