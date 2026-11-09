package br.edu.tradutoroffline.features.conversation

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import br.edu.tradutoroffline.domain.*
import br.edu.tradutoroffline.persistence.MeasurementStore
import br.edu.tradutoroffline.services.audio.LocalAudio
import br.edu.tradutoroffline.services.speech.LocalSpeech
import br.edu.tradutoroffline.services.synthesis.LocalSynthesis
import br.edu.tradutoroffline.services.translation.LocalTranslation
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** One active job and a generation guard prevent stale callbacks changing a later turn. */
class ConversationController(private val context: Context, private var changed: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val speech: SpeechRecognitionService = LocalSpeech(context)
    private val audio: AudioService = LocalAudio(context)
    private val translation: TranslationService = LocalTranslation()
    private val synthesis: SynthesisService = LocalSynthesis(context)
    private var store: MeasurementStore? = null
    private var job: Job? = null
    private var generation = 0L
    private var stopSignal: CompletableDeferred<Unit>? = null
    private var timing = TurnTiming()
    var stage = Stage.READY; private set
    var direction = Direction.PT_EN; private set
    var message = "Com Wi-Fi, toque em Baixar e preparar idiomas. Depois, use o tradutor sem internet."; private set
    var input = ""
    var output = ""; private set
    var current: Measurement? = null; private set
    var original: File? = null; private set
    var speechReady = false; private set
    var translationReady = false; private set
    var voiceReady = false; private set
    var preparation = "Modelos e vozes ainda não verificados."; private set
    var storageFailure: String? = null; private set
    var airplane = false
    var notes = ""
    var transcriptionQuality = Quality.UNRATED.label
    var translationQuality = Quality.UNRATED.label
    val busy get() = stage != Stage.READY && stage != Stage.ERROR
    val count get() = store?.rows?.size ?: 0
    val canRecord get() = !busy && speechReady && store != null
    val canTranslate get() = !busy && translationReady && store != null
    val canExport get() = !busy && store != null

    init {
        try { store = MeasurementStore(context) }
        catch (error: Exception) {
            stage = Stage.ERROR
            message = "Não foi possível carregar medicoes.json. Preserve os dados do app e corrija o arquivo antes de novas rodadas: ${error.message}"
            storageFailure = message
        }
    }

    fun selectDirection(value: Direction) {
        if (busy || direction == value) return
        reset()
        direction = value
        changed()
    }

    fun reset() {
        if (busy) return
        generation++
        original?.delete()
        original = null
        input = ""; output = ""; current = null
        timing = TurnTiming()
        notes = ""
        transcriptionQuality = Quality.UNRATED.label
        translationQuality = Quality.UNRATED.label
        stage = Stage.READY
        message = "Nova rodada. Escolha Gravar, Importar áudio de reserva ou digite uma frase."
        changed()
    }

    private fun newMeasurement(origin: Origin): Measurement {
        notes = ""
        transcriptionQuality = Quality.UNRATED.label
        translationQuality = Quality.UNRATED.label
        return Measurement(
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            system = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            direction = direction.label, origin = origin.label,
            airplaneModeConfirmed = airplane
        )
    }

    fun observe(observer: () -> Unit) { changed = observer }

    /** Automatic recheck is local only, so reopening the app in airplane mode remains usable. */
    fun prepare(download: Boolean = true) = operation(Stage.PREPARING, persistFailure = false) { token ->
        speechReady = false; translationReady = false; voiceReady = false
        val failures = mutableListOf<String>()
        try {
            message = if (download) "Preparando tradução PT/EN…" else "Verificando recursos instalados no aparelho…"; changed()
            if (download) withTimeout(180_000) { translation.prepare() }
            translationReady = translation.isReady()
            if (!translationReady) failures += "Tradução ausente. Com Wi-Fi, toque em Baixar e preparar idiomas."
        } catch (e: Exception) { if (e is CancellationException && e !is TimeoutCancellationException) throw e; failures += "Tradução: ${e.message ?: "tempo limite"}" }
        ensure(token)
        try {
            if (download) withTimeout(600_000) {
                speech.downloadMissingModels { status ->
                    scope.launch { if (generation == token) { message = status; changed() } }
                }
            }
            message = "Verificando reconhecimento local…"; changed()
            withTimeout(90_000) { speech.prepare() }; speechReady = true
        }
        catch (e: Exception) { if (e is CancellationException && e !is TimeoutCancellationException) throw e; failures += "Reconhecimento: ${e.message ?: "tempo limite"}" }
        ensure(token)
        var voices = "Vozes indisponíveis."
        try { voices = withTimeout(30_000) { synthesis.prepare() }; voiceReady = true }
        catch (e: Exception) { if (e is CancellationException && e !is TimeoutCancellationException) throw e; failures += "Voz: ${e.message ?: "tempo limite"}" }
        ensure(token)
        preparation = "${speech.modelStatus()}\nTradução: ${if (translationReady) "pacote local preparado" else "ausente"}.\n$voices\nEspaço dos pacotes ML Kit/vozes: indisponível pela API usada."
        stage = if (failures.isEmpty()) Stage.READY else Stage.ERROR
        message = if (failures.isEmpty()) "Idiomas preparados. Confirme o fluxo em modo avião com Wi-Fi desligado." else failures.joinToString("\n")
    }

    fun importModel(uri: Uri, language: String) = operation(Stage.PREPARING, false) { token ->
        message = "Importando modelo ${language.uppercase()} do arquivo local…"; changed()
        withTimeout(120_000) {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { speech.importModel(language, it) }
                    ?: error("Não foi possível abrir o ZIP. Selecione um arquivo local disponível.")
            }
        }
        ensure(token)
        speechReady = false
        preparation = speech.modelStatus()
        message = "Modelo importado. Toque em Verificar recursos locais para concluir a preparação."
        stage = Stage.READY
    }

    fun record() {
        if (!canRecord) return
        reset()
        current = newMeasurement(Origin.MICROPHONE)
        val signal = CompletableDeferred<Unit>()
        stopSignal = signal
        operation(Stage.RECORDING) { token ->
            message = "Fale por até 20 segundos e toque em Parar."; changed()
            val file = File(context.cacheDir, "original-${UUID.randomUUID()}.wav")
            original = file
            audio.capture(file, signal)
            ensure(token)
            if (timing.referenceMS == null) timing.referenceMS = audio.lastCaptureStoppedMS ?: now()
            stopSignal = null
            transcribeAndTranslate(file, token)
        }
    }

    fun stopRecording() {
        if (stage != Stage.RECORDING || stopSignal?.isCompleted != false) return
        timing.referenceMS = now()
        stopSignal?.complete(Unit)
        message = "Finalizando captura…"; changed()
    }

    fun importAudio(uri: Uri) {
        if (!canRecord) return
        reset()
        current = newMeasurement(Origin.FILE)
        operation(Stage.TRANSCRIBING) { token ->
            message = "Importando WAV PCM16 mono, 16 kHz, até 20 segundos…"; changed()
            original = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { audio.importWav(it) }
                    ?: error("Não foi possível abrir o áudio. Copie o arquivo para o aparelho.")
            }
            ensure(token)
            timing.referenceMS = now() // Import duration is deliberately outside speech metrics.
            transcribeAndTranslate(requireNotNull(original), token)
        }
    }

    private suspend fun transcribeAndTranslate(file: File, token: Long) {
        stage = Stage.TRANSCRIBING; message = "Transcrevendo com Vosk local…"; changed()
        val text = withTimeout(45_000) { speech.transcribe(file, direction) }.trim()
        ensure(token)
        current = requireNotNull(current).copy(rawInput = text, input = text, transcriptionMS = timing.elapsed(now()))
        input = text
        persist()
        check(text.isNotBlank()) { "Nenhuma fala reconhecida. Ouça o original, aproxime o microfone ou importe outro áudio." }
        translateAndSpeak(token)
    }

    fun translate() {
        if (!canTranslate) return
        val text = input.trim()
        if (text.isEmpty()) { notice("Digite uma frase ou grave a fala antes de traduzir."); return }
        if (current == null) {
            timing = TurnTiming()
            current = newMeasurement(Origin.TEXT).copy(input = text, rawInput = text)
        } else {
            current = requireNotNull(current).correcting(text)
        }
        output = ""
        translationQuality = Quality.UNRATED.label
        input = text
        operation(Stage.TRANSLATING) { token -> translateAndSpeak(token) }
    }

    private suspend fun translateAndSpeak(token: Long) {
        stage = Stage.TRANSLATING; message = "Traduzindo no aparelho…"; changed()
        val text = withTimeout(45_000) { translation.translate(input, direction) }
        ensure(token)
        output = text
        current = requireNotNull(current).copy(output = text, translationMS = translation.lastTranslationMS, textMS = timing.elapsed(now()), failure = null)
        persist()
        stage = Stage.PLAYING; message = "Reproduzindo tradução…"; changed()
        val rowId = requireNotNull(current).id
        withTimeout(45_000) {
            synthesis.speak(text, direction) {
                val startMS = now()
                // LocalSynthesis serializes callbacks on Main. Throwing here ends synthesis
                // through its error handler; a persistence failure must not unlock a busy UI.
                if (generation == token && current?.id == rowId && current?.audioStartMS == null) {
                    current = requireNotNull(current).copy(audioStartMS = timing.elapsed(startMS))
                    persist()
                    changed()
                }
            }
        }
        ensure(token)
        stage = Stage.READY; message = "Rodada concluída. Avalie a transcrição original e a tradução; salve a avaliação."
        persist()
    }

    fun replayTranslation() {
        if (busy || output.isBlank() || !voiceReady) return
        operation(Stage.PLAYING) { token ->
            withTimeout(45_000) { synthesis.speak(output, direction) {} }
            ensure(token)
            stage = Stage.READY; message = "Tradução reproduzida. A primeira latência foi preservada."
        }
    }

    fun replayOriginal() {
        val file = original ?: return
        if (busy) return
        operation(Stage.PLAYING) { token ->
            message = "Ouvindo original…"; changed()
            val completed = CompletableDeferred<Unit>()
            try {
                audio.play(file, { completed.complete(Unit) }, { completed.completeExceptionally(IllegalStateException(it)) })
                withTimeout(30_000) { completed.await() }
            } finally { audio.stop() }
            ensure(token)
            stage = Stage.READY; message = "Original reproduzido."
        }
    }

    fun saveEvaluation() {
        if (busy || current == null) return
        try {
            current = requireNotNull(current).copy(notes = notes, airplaneModeConfirmed = airplane,
                transcriptionQuality = transcriptionQuality, translationQuality = translationQuality)
            persist()
            message = "Avaliação salva no aparelho."; changed()
        } catch (e: Exception) { notice("Não foi possível salvar a avaliação: ${e.message}") }
    }

    fun export(uri: Uri) = operation(Stage.PREPARING, false) { _ ->
        context.contentResolver.openOutputStream(uri, "wt")?.use { requireNotNull(store).export(it) }
            ?: error("Não foi possível gravar o JSON. Escolha outro destino.")
        stage = Stage.READY; message = "$count medições exportadas conforme contrato v1."
    }

    fun notice(text: String) { message = text; stage = Stage.ERROR; changed() }
    private fun persist() { current?.let { requireNotNull(store) { "Armazenamento indisponível." }.upsert(it) } }
    private fun fail(text: String, save: Boolean = true) {
        stage = Stage.ERROR; message = text
        if (save && current != null) {
            current = requireNotNull(current).copy(failure = text)
            try { persist() } catch (e: Exception) { message += "\nFalha ao salvar: ${e.message}" }
        }
    }

    private fun operation(state: Stage, persistFailure: Boolean = true, block: suspend (Long) -> Unit) {
        if (busy) return
        val token = ++generation
        stage = state
        job = scope.launch {
            try { block(token) }
            catch (e: TimeoutCancellationException) { if (generation == token) fail("Tempo limite. Verifique os pacotes locais e tente novamente.", persistFailure) }
            catch (e: CancellationException) {
                if (generation == token && currentCoroutineContext().isActive) fail("A operação local foi cancelada. Tente novamente.", persistFailure)
                else throw e
            }
            catch (e: Exception) { if (generation == token) fail(e.message ?: "Recurso local indisponível. Prepare os idiomas e tente novamente.", persistFailure) }
            finally { if (generation == token) { if (busy) stage = Stage.READY; changed() } }
        }
        changed()
    }

    /** Called on Activity.onStop, including incoming calls/switching away. */
    fun interrupt(reason: String = "Operação interrompida ao sair da tela. Tente novamente.") {
        if (!busy) return
        generation++
        job?.cancel(); stopSignal?.cancel(); stopSignal = null
        synthesis.stop(); audio.stop()
        fail(reason, stage != Stage.PREPARING)
        changed()
    }

    fun close() {
        interrupt()
        scope.cancel(); synthesis.close(); audio.close(); translation.close(); speech.close()
    }

    private fun ensure(token: Long) { if (generation != token) throw CancellationException("Rodada substituída") }
    private fun now() = SystemClock.elapsedRealtime()
}
