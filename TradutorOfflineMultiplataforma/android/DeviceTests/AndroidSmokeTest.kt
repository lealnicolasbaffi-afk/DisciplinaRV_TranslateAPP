package br.edu.tradutoroffline.tests

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.edu.tradutoroffline.app.MainActivity
import br.edu.tradutoroffline.domain.Measurement
import br.edu.tradutoroffline.persistence.MeasurementStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidSmokeTest {
    @Test fun launchesAndKeepsEditedTextAfterActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val views = descendants(activity.window.decorView)
                assertTrue(views.filterIsInstance<Button>().any { it.text == "Baixar e preparar idiomas (Wi-Fi)" })
                val input = views.filterIsInstance<EditText>().first()
                input.setText("Texto preservado ao girar a tela")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("Texto preservado ao girar a tela", descendants(activity.window.decorView).filterIsInstance<EditText>().first().text.toString())
                assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text == "Tradutor Offline" })
            }
        }
    }

    @Test fun savesAndRecoversAtomicMeasurementBackupOnAndroid() {
        val context = isolatedContext("measurement")
        try {
            val row = Measurement(device = "instrumentação Android", system = "teste", direction = "pt-BR → en-US", origin = "texto digitado", input = "Olá", rawInput = "Olá")
            MeasurementStore(context).upsert(row)
            val file = File(context.filesDir, "medicoes.json")
            file.copyTo(File(file.path + ".bak"), overwrite = true)
            file.writeText("gravação interrompida")
            val recovered = MeasurementStore(context)
            assertEquals(row, recovered.rows.single())
            val export = ByteArrayOutputStream()
            recovered.export(export)
            assertTrue(export.toString("UTF-8").contains("Olá"))
        } finally { context.filesDir.deleteRecursively() }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
}

internal fun isolatedContext(label: String): Context {
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val directory = File(app.cacheDir, "instrumentation-$label-${UUID.randomUUID()}").apply { check(mkdirs()) }
    return object : ContextWrapper(app) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }
}
