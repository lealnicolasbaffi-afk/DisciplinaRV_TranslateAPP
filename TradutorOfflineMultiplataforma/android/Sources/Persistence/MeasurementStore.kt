package br.edu.tradutoroffline.persistence

import android.content.Context
import android.util.AtomicFile
import br.edu.tradutoroffline.domain.Measurement
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.OutputStream

/** Atomic replacement: a failed write keeps the previous complete collection. */
class MeasurementStore(context: Context) {
    private val file = AtomicFile(java.io.File(context.filesDir, "medicoes.json"))
    val rows = mutableListOf<Measurement>()

    init {
        // openRead performs AtomicFile's backup recovery, including when only .bak survives.
        val contents = try { file.openRead().bufferedReader().use { it.readText() } }
        catch (error: FileNotFoundException) {
            if (file.baseFile.exists() || java.io.File(file.baseFile.path + ".bak").exists()) throw error
            null // First launch has no collection yet; corruption is never treated as an empty file.
        }
        if (contents != null) {
            val array = JSONArray(contents)
            for (i in 0 until array.length()) rows += decode(array.getJSONObject(i))
        }
    }

    fun upsert(row: Measurement) {
        val next = rows.toMutableList()
        val index = next.indexOfFirst { it.id == row.id }
        if (index < 0) next += row else next[index] = row
        val stream = file.startWrite()
        try {
            stream.write(encodeAll(next).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            rows.clear()
            rows.addAll(next)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    fun export(output: OutputStream) {
        output.bufferedWriter(Charsets.UTF_8).use { it.write(encodeAll(rows)) }
    }

    companion object {
        fun encodeAll(rows: List<Measurement>): String = JSONArray().apply {
            rows.forEach { put(encode(it)) }
        }.toString(2)

        fun encode(row: Measurement) = JSONObject().apply {
            put("id", row.id); put("date", row.date); put("platform", row.platform)
            put("device", row.device); put("system", row.system); put("direction", row.direction)
            put("input", row.input); put("output", row.output); put("rawInput", row.rawInput)
            put("edited", row.edited); put("origin", row.origin)
            put("airplaneModeConfirmed", row.airplaneModeConfirmed)
            put("failure", row.failure ?: JSONObject.NULL); put("notes", row.notes)
            put("transcriptionMS", row.transcriptionMS ?: JSONObject.NULL)
            put("textMS", row.textMS ?: JSONObject.NULL)
            put("audioStartMS", row.audioStartMS ?: JSONObject.NULL)
            put("translationMS", row.translationMS ?: JSONObject.NULL)
            put("transcriptionQuality", row.transcriptionQuality)
            put("translationQuality", row.translationQuality)
        }

        fun decode(o: JSONObject) = Measurement(
            id = o.getString("id"), date = o.getString("date"), platform = o.getString("platform"),
            device = o.getString("device"), system = o.getString("system"),
            direction = o.getString("direction"), input = o.getString("input"),
            output = o.getString("output"), rawInput = o.getString("rawInput"),
            edited = o.getBoolean("edited"), origin = o.getString("origin"),
            airplaneModeConfirmed = o.getBoolean("airplaneModeConfirmed"),
            failure = if (o.isNull("failure")) null else o.getString("failure"),
            notes = o.getString("notes"), transcriptionMS = nullableNumber(o, "transcriptionMS"),
            textMS = nullableNumber(o, "textMS"), audioStartMS = nullableNumber(o, "audioStartMS"),
            translationMS = nullableNumber(o, "translationMS"),
            transcriptionQuality = o.getString("transcriptionQuality"),
            translationQuality = o.getString("translationQuality")
        )

        private fun nullableNumber(o: JSONObject, key: String) =
            if (o.isNull(key)) null else o.getDouble(key)
    }
}
