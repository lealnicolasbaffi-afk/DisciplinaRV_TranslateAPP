package br.edu.tradutoroffline.services.translation

import android.os.SystemClock
import br.edu.tradutoroffline.domain.TranslationService
import br.edu.tradutoroffline.domain.Direction
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

/** Downloads occur only in [prepare]; translating never requests a model download. */
class LocalTranslation : TranslationService {
    private val manager = RemoteModelManager.getInstance()
    // English is built into ML Kit. Its TranslateRemoteModel must not be downloaded:
    // https://developers.google.com/android/reference/com/google/mlkit/nl/translate/TranslateRemoteModel
    private val portuguese = TranslateRemoteModel.Builder(TranslateLanguage.PORTUGUESE).build()
    private val clients = mutableMapOf<Direction, Translator>()
    private var closed = false
    override var lastTranslationMS: Double? = null
        private set

    override suspend fun prepare() {
        checkOpen()
        try {
            if (!isReady()) {
                manager.download(portuguese, DownloadConditions.Builder().requireWifi().build()).awaitLocal()
            }
            currentCoroutineContext().ensureActive()
            check(isReady()) { "O modelo de tradução não ficou disponível. Tente Preparar idiomas novamente com Wi-Fi." }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IllegalStateException(
                "Não foi possível preparar a tradução. Ative Wi-Fi, verifique o espaço livre e tente novamente. ${error.message.orEmpty()}",
                error,
            )
        }
    }

    override suspend fun isReady(): Boolean {
        checkOpen()
        val ready = manager.isModelDownloaded(portuguese).awaitLocal()
        checkOpen()
        return ready
    }

    override suspend fun translate(text: String, direction: Direction): String {
        lastTranslationMS = null
        require(text.isNotBlank()) { "Digite ou grave um texto antes de traduzir." }
        check(isReady()) { "Pacote de tradução ausente. Use Preparar idiomas com Wi-Fi antes de ficar offline." }
        currentCoroutineContext().ensureActive()
        val translator = client(direction)
        val started = SystemClock.elapsedRealtimeNanos()
        val result = try {
            translator.translate(text).awaitLocal()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IllegalStateException("Falha na tradução local. Revise o texto; se o pacote foi removido, use Preparar idiomas com Wi-Fi. ${error.message.orEmpty()}", error)
        }
        val durationMS = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        currentCoroutineContext().ensureActive()
        checkOpen()
        check(result.isNotBlank()) { "A tradução retornou vazia. Revise o texto e tente novamente." }
        lastTranslationMS = durationMS
        return result
    }

    @Synchronized
    private fun client(direction: Direction): Translator {
        checkOpen()
        return clients.getOrPut(direction) {
            val source = if (direction == Direction.PT_EN) TranslateLanguage.PORTUGUESE else TranslateLanguage.ENGLISH
            val target = if (direction == Direction.PT_EN) TranslateLanguage.ENGLISH else TranslateLanguage.PORTUGUESE
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
    }

    @Synchronized
    private fun checkOpen() = check(!closed) { "O serviço de tradução foi encerrado. Abra a tela novamente." }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        clients.values.forEach { it.close() }
        clients.clear()
    }
}

/** Cancels the waiter, not an ML Kit download already accepted by the SDK. */
private suspend fun <T> Task<T>.awaitLocal(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(Executor { command -> command.run() }) { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            task.isCanceled -> continuation.cancel(CancellationException("Operação de tradução cancelada."))
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Falha no ML Kit."))
        }
    }
}
