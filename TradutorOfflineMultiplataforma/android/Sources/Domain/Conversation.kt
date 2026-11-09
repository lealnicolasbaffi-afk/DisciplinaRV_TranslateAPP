package br.edu.tradutoroffline.domain

import java.time.Instant
import java.util.UUID

enum class Direction(val label: String, val sourceTag: String, val targetTag: String) {
    PT_EN("pt-BR → en-US", "pt-BR", "en-US"),
    EN_PT("en-US → pt-BR", "en-US", "pt-BR")
}

enum class Stage(val label: String) {
    READY("Pronto"), PREPARING("Preparando idiomas"), RECORDING("Gravando"),
    TRANSCRIBING("Transcrevendo"), TRANSLATING("Traduzindo"),
    PLAYING("Reproduzindo"), ERROR("Erro")
}

enum class Origin(val label: String) {
    MICROPHONE("microfone"), FILE("arquivo pré-gravado"), TEXT("texto digitado")
}

enum class Quality(val label: String) {
    UNRATED("não avaliada"), CORRECT("correta"),
    UNDERSTANDABLE("compreensível com erros"), INCORRECT("incorreta")
}

/** Only a monotonic clock may be passed here. Null reference means typed text. */
class TurnTiming(var referenceMS: Long? = null) {
    fun elapsed(nowMS: Long): Double? = referenceMS?.let { (nowMS - it).coerceAtLeast(0).toDouble() }
}

/** Property names and values mirror shared/medicoes.schema.json, version 1. */
data class Measurement(
    val id: String = UUID.randomUUID().toString(),
    val date: String = Instant.now().toString(),
    val platform: String = "android",
    val device: String,
    val system: String,
    val direction: String,
    val input: String = "",
    val output: String = "",
    val rawInput: String = "",
    val edited: Boolean = false,
    val origin: String,
    val airplaneModeConfirmed: Boolean = false,
    val failure: String? = null,
    val notes: String = "",
    val transcriptionMS: Double? = null,
    val textMS: Double? = null,
    val audioStartMS: Double? = null,
    val translationMS: Double? = null,
    val transcriptionQuality: String = Quality.UNRATED.label,
    val translationQuality: String = Quality.UNRATED.label
) {
    fun correcting(text: String) = copy(
        input = text, edited = text != rawInput, output = "", failure = null,
        textMS = null, audioStartMS = null, translationMS = null,
        translationQuality = Quality.UNRATED.label
    )
}
