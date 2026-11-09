package br.edu.tradutoroffline.domain

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred

/** Platform adapters must fail explicitly; none of these contracts allows remote fallback. */
interface SpeechRecognitionService : AutoCloseable {
    fun modelStatus(): String
    suspend fun downloadMissingModels(progress: (String) -> Unit)
    suspend fun importModel(language: String, input: InputStream)
    suspend fun prepare()
    suspend fun transcribe(file: File, direction: Direction): String
}

interface TranslationService : AutoCloseable {
    val lastTranslationMS: Double?
    suspend fun prepare()
    suspend fun isReady(): Boolean
    suspend fun translate(text: String, direction: Direction): String
}

interface SynthesisService : AutoCloseable {
    suspend fun prepare(): String
    /** onStart is delivered synchronously on Main; it approximates synthesis availability. */
    suspend fun speak(text: String, direction: Direction, onStart: () -> Unit)
    fun stop()
}

interface AudioService : AutoCloseable {
    val lastCaptureStoppedMS: Long?
    suspend fun capture(file: File, stopSignal: CompletableDeferred<Unit>): File
    suspend fun importWav(input: InputStream): File
    fun play(file: File, onDone: () -> Unit, onError: (String) -> Unit)
    fun stop()
}
