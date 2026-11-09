package br.edu.tradutoroffline.features.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import br.edu.tradutoroffline.domain.Direction
import br.edu.tradutoroffline.services.translation.LocalTranslation
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*

/** Camera frames and OCR stay on-device. Only explicit preparation can download a model. */
class CameraTranslationActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translator = LocalTranslation()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var camera: LifecycleCameraController
    private lateinit var preview: PreviewView
    private lateinit var overlay: TranslationOverlay
    private lateinit var status: TextView
    private lateinit var prepare: Button
    private lateinit var directionButton: Button
    private var direction = Direction.EN_PT
    private var active = false
    private var ready = false
    private var generation = 0
    private var translationJob: Job? = null
    private var preparationJob: Job? = null
    private var visible = emptyList<DetectedText>()
    private val cache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 128
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && active) startCamera()
        else status.text = "Permita a câmera em Configurações → Aplicativos → Tradutor Offline → Permissões."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        direction = Direction.entries.getOrElse(savedInstanceState?.getInt("direction") ?: Direction.EN_PT.ordinal) { Direction.EN_PT }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { action() }; body.addView(this)
        }
        button("Voltar para conversa") { finish() }
        directionButton = button(direction.label) {
            generation++
            translationJob?.cancel(); translationJob = null
            direction = if (direction == Direction.EN_PT) Direction.PT_EN else Direction.EN_PT
            directionButton.text = direction.label
            cache.clear(); visible = emptyList(); overlay.show(emptyList())
            status.text = if (ready) "Aponte para um texto e mantenha a câmera estável." else "Prepare a tradução com Wi-Fi."
        }
        prepare = button("Preparar tradução (com Wi-Fi)") { prepareTranslation() }
        status = TextView(this).apply {
            setPadding(20, 12, 20, 12)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = "OCR local • aponte para texto em português ou inglês."
            body.addView(this)
        }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        overlay = TranslationOverlay(this)
        val viewport = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(this@CameraTranslationActivity.overlay, FrameLayout.LayoutParams(-1, -1))
        }
        body.addView(viewport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(body)
        camera = LifecycleCameraController(this).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(androidx.camera.view.CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
        preview.controller = camera
    }

    override fun onStart() {
        super.onStart()
        active = true
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
        scope.launch {
            try {
                ready = translator.isReady()
                if (active) status.text = if (ready) "Aponte para um texto e mantenha a câmera estável."
                    else "OCR disponível. Toque em Preparar tradução com Wi-Fi para baixar o idioma."
            } catch (error: Exception) { if (active) status.text = error.message }
        }
    }

    private fun startCamera() {
        try {
            val executor = ContextCompat.getMainExecutor(this)
            camera.setImageAnalysisAnalyzer(executor, MlKitAnalyzer(
                listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor
            ) { result ->
                if (!active) return@MlKitAnalyzer
                val text = result.getValue(recognizer)
                if (text == null) {
                    visible = emptyList(); overlay.show(emptyList())
                    status.text = "Não foi possível reconhecer o texto. Melhore a iluminação e tente novamente."
                    return@MlKitAnalyzer
                }
                visible = text.textBlocks.mapNotNull { block ->
                    val box = block.boundingBox ?: return@mapNotNull null
                    val value = block.text.trim()
                    if (value.isEmpty() || value.length > 1500) null else DetectedText(value, RectF(box))
                }.take(12)
                drawLatest()
                translateVisible()
            })
            camera.bindToLifecycle(this)
        } catch (error: Exception) {
            status.text = "Câmera indisponível. Feche outros apps de câmera e reabra esta tela. ${error.message.orEmpty()}"
        }
    }

    private fun prepareTranslation() {
        if (preparationJob?.isActive == true) return
        prepare.isEnabled = false
        status.text = "Preparando tradução pelo Wi-Fi…"
        preparationJob = scope.launch {
            try {
                withTimeout(120_000) { translator.prepare() }
                ready = true
                status.text = "Tradução preparada. Você já pode testar em modo avião."
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) status.text = "Download excedeu o tempo. Confira o Wi-Fi e tente novamente."
                else throw cancelled
            } catch (error: Exception) { status.text = error.message }
            finally { prepare.isEnabled = true }
        }
    }

    private fun drawLatest() {
        overlay.show(visible.map { OverlayText(it.box, cache[it.text]) })
    }

    private fun translateVisible() {
        if (!ready || translationJob?.isActive == true || preparationJob?.isActive == true) return
        val pending = visible.map { it.text }.distinct().filter { !cache.containsKey(it) }
        if (pending.isEmpty()) return
        val token = generation
        val selectedDirection = direction
        translationJob = scope.launch {
            try {
                for (text in pending) {
                    if (!active || token != generation) break
                    if (visible.none { it.text == text }) continue
                    val translated = withTimeout(15_000) { translator.translate(text, selectedDirection) }
                    if (!active || token != generation) break
                    cache[text] = translated
                    drawLatest() // Use the latest boxes, never geometry from an old frame.
                }
                if (active && token == generation) status.text = "Tradução local ao vivo • mantenha o texto estável e bem iluminado."
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) {
                    ready = false
                    status.text = "Tradução excedeu o tempo. Toque em Preparar tradução para tentar novamente."
                } else throw cancelled
            } catch (error: Exception) {
                ready = false
                status.text = "${error.message} Toque em Preparar tradução para tentar novamente."
            }
        }
    }

    override fun onStop() {
        active = false; generation++
        translationJob?.cancel(); preparationJob?.cancel()
        camera.clearImageAnalysisAnalyzer(); camera.unbind()
        visible = emptyList(); overlay.show(emptyList())
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("direction", direction.ordinal)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        scope.cancel(); recognizer.close(); translator.close()
        super.onDestroy()
    }

    private data class DetectedText(val text: String, val box: RectF)
}
