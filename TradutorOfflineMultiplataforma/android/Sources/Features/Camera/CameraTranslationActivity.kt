package br.edu.tradutoroffline.features.camera

import br.edu.tradutoroffline.ui.IconButton
import br.edu.tradutoroffline.ui.AppUi
import android.graphics.Color
import android.view.Gravity
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
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
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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
    private val tracker = TextRegionTracker()
    private var visible = emptyList<TrackedText>()
    private lateinit var frozenImage: ImageView
    private lateinit var freezeButton: Button
    private var frozen = false
    private var frozenBitmap: Bitmap? = null
    private var expiryJob: Job? = null
    private var readinessJob: Job? = null
    private var analysisGeneration = 0
    private val cache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 128
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && active) startCamera()
        else status.text = "Permita a câmera em Configurações → Aplicativos → Tradutor Offline → Permissões."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        direction = Direction.entries.getOrElse(savedInstanceState?.getInt("direction") ?: Direction.EN_PT.ordinal) { Direction.EN_PT }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppUi.night)
        }
        fun dp(value: Int) = AppUi.dp(body, value)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        body.addView(header)
        var section = header
        body.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        fun button(label: String, action: () -> Unit) = IconButton(this).apply {
            text = label; AppUi.button(this, dark = true)
            setOnClickListener { action() }
            section.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        val back = button("‹  Conversa") { finish() }
        header.removeView(back)
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        AppUi.icon(back, "back", "Voltar para conversa", dark = true)
        toolbar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(TextView(this).apply {
            text = "Traduzir"; textSize = 20f; setTextColor(Color.WHITE)
            setPadding(dp(16), 0, 0, 0)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(toolbar)
        directionButton = button(cameraDirectionLabel()) {
            generation++
            translationJob?.cancel(); translationJob = null
            direction = if (direction == Direction.EN_PT) Direction.PT_EN else Direction.EN_PT
            directionButton.text = cameraDirectionLabel()
            directionButton.contentDescription = "${direction.label}. Toque para inverter os idiomas."
            cache.clear(); tracker.clear(); visible = emptyList(); overlay.show(emptyList())
            if (frozen) recognizeFrozen()
            status.text = if (ready) "Aponte para um texto e mantenha a câmera estável." else "Prepare a tradução com Wi-Fi."
        }
        directionButton.contentDescription = "${direction.label}. Toque para inverter os idiomas."
        section = controls
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        prepare = button("Baixar idioma · Wi-Fi") { prepareTranslation() }
        controls.removeView(prepare)
        AppUi.icon(prepare, "download", "Baixar idioma com Wi-Fi", dark = true)
        actionRow.addView(prepare, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(16) })
        freezeButton = button("Congelar para ler") { toggleFreeze() }
        AppUi.icon(freezeButton, "pause", "Congelar imagem para ler", primary = true)
        controls.removeView(freezeButton)
        actionRow.gravity = Gravity.CENTER
        actionRow.addView(freezeButton, LinearLayout.LayoutParams(dp(64), dp(64)))
        actionRow.addView(View(this), LinearLayout.LayoutParams(dp(68), 1))
        controls.addView(actionRow)
        status = TextView(this).apply {
            setPadding(0, dp(8), 0, dp(4))
            textSize = 14f; setTextColor(Color.rgb(208, 225, 219))
            gravity = Gravity.CENTER_VERTICAL
            minLines = 2; maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = "OCR local • aponte para texto em português ou inglês."
            controls.addView(this, 0)
        }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP && !frozen) status.text = "Ajustando foco… mantenha o texto estável."
                false // Let PreviewView/CameraController handle tap-to-focus and pinch-to-zoom.
            }
        }
        overlay = TranslationOverlay(this)
        frozenImage = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY; visibility = View.GONE }
        val viewport = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(frozenImage, FrameLayout.LayoutParams(-1, -1))
            addView(this@CameraTranslationActivity.overlay, FrameLayout.LayoutParams(-1, -1))
        }
        body.addView(viewport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(controls)
        setContentView(body)
        camera = LifecycleCameraController(this).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(androidx.camera.view.CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 960),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()
            isTapToFocusEnabled = true
            isPinchToZoomEnabled = true
        }
        preview.controller = camera
    }

    override fun onStart() {
        super.onStart()
        active = true
        preview.keepScreenOn = true
        expiryJob = scope.launch {
            while (isActive) {
                delay(50)
                if (!frozen) { visible = tracker.snapshot(now()); drawLatest() }
            }
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
        readinessJob = scope.launch {
            try {
                ready = translator.isReady()
                if (active) { drawLatest(); translateVisible() }
                if (active) status.text = if (ready) "Aponte para um texto e mantenha a câmera estável."
                    else "OCR disponível. Toque em Baixar idioma para baixar o idioma."
            } catch (error: Exception) { if (active) status.text = error.message }
        }
    }

    private fun startCamera() {
        try {
            installAnalyzer()
            camera.bindToLifecycle(this)
        } catch (error: Exception) {
            status.text = "Câmera indisponível. Feche outros apps de câmera e reabra esta tela. ${error.message.orEmpty()}"
        }
    }

    private fun cameraDirectionLabel() = if (direction == Direction.EN_PT) "Inglês   ⇄   Português" else "Português   ⇄   Inglês"

    private fun now() = SystemClock.elapsedRealtime()

    private fun observations(text: Text): List<TextObservation> = text.textBlocks.mapNotNull { block ->
        val points = block.cornerPoints ?: return@mapNotNull null
        if (points.size != 4) return@mapNotNull null
        val confidence = block.lines.map { it.confidence }.filter { it > 0f }.average()
            .takeIf { !it.isNaN() }?.toFloat() ?: 1f
        TextObservation(block.text, TextQuad(points.map { Vertex(it.x.toFloat(), it.y.toFloat()) }), confidence)
    }.take(12)

    private fun installAnalyzer() {
        val token = ++analysisGeneration
        val executor = ContextCompat.getMainExecutor(this)
        camera.setImageAnalysisAnalyzer(executor, MlKitAnalyzer(
            listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor
        ) { result ->
            if (!active || frozen || token != analysisGeneration) return@MlKitAnalyzer
            val text = result.getValue(recognizer)
            visible = tracker.update(if (text == null) emptyList() else observations(text), now())
            drawLatest()
            translateVisible()
        })
    }

    private fun toggleFreeze() {
        if (frozen) {
            frozen = false; generation++; analysisGeneration++
            translationJob?.cancel(); translationJob = null
            frozenImage.setImageDrawable(null); frozenImage.visibility = View.GONE
            frozenBitmap = null
            tracker.clear(); visible = emptyList(); drawLatest()
            AppUi.icon(freezeButton, "pause", "Congelar imagem para ler", primary = true)
            installAnalyzer()
            status.text = "Ao vivo • toque para focar; use dois dedos para ampliar."
        } else {
            val bitmap = preview.bitmap
            if (bitmap == null) { status.text = "Aguarde a câmera iniciar antes de congelar."; return }
            frozen = true; generation++; analysisGeneration++
            translationJob?.cancel(); translationJob = null
            camera.clearImageAnalysisAnalyzer()
            frozenBitmap = bitmap
            frozenImage.setImageBitmap(bitmap); frozenImage.visibility = View.VISIBLE
            AppUi.icon(freezeButton, "play", "Retomar câmera ao vivo", primary = true)
            // Recognize the exact displayed bitmap, so the frozen overlay and image agree.
            recognizeFrozen()
        }
    }

    private fun recognizeFrozen() {
        val bitmap = frozenBitmap ?: return
        val token = generation
        tracker.clear(); visible = emptyList(); drawLatest()
        status.text = "Lendo imagem congelada…"
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                if (!active || !frozen || generation != token) return@addOnSuccessListener
                val observations = observations(text)
                val instant = now()
                visible = tracker.stillImage(observations, instant)
                drawLatest(); translateVisible()
                status.text = if (visible.isEmpty()) "Nenhum texto legível. Retome a câmera e ajuste foco/iluminação."
                    else "Imagem congelada • tradução alinhada à captura."
            }.addOnFailureListener { error ->
                if (active && frozen && generation == token) status.text = "Falha ao ler captura: ${error.message}"
            }
    }

    private fun prepareTranslation() {
        if (preparationJob?.isActive == true) return
        ready = false
        prepare.isEnabled = false
        status.text = "Preparando tradução pelo Wi-Fi…"
        preparationJob = scope.launch {
            try {
                withTimeout(120_000) { translator.prepare() }
                ready = true
                drawLatest(); translateVisible()
                status.text = "Tradução preparada. Você já pode testar em modo avião."
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) status.text = "Download excedeu o tempo. Confira o Wi-Fi e tente novamente."
                else throw cancelled
            } catch (error: Exception) { status.text = error.message }
            finally { prepare.isEnabled = !ready }
        }
    }

    private fun drawLatest() {
        // Reserve the same control space so readiness never resizes the camera viewport.
        val label = if (ready) "Idioma disponível offline" else "Baixar idioma com Wi-Fi"
        val kind = if (ready) "check" else "download"
        if (prepare.contentDescription != label) AppUi.icon(prepare, kind, label, dark = true)
        prepare.isEnabled = !ready && preparationJob?.isActive != true
        overlay.show(visible.map { OverlayText(it.id, it.quad,
            if (it.confirmed) cache[TextRegionTracker.key(it.text)] else null, it.opacity) })
    }

    private fun translateVisible() {
        if (!ready || translationJob?.isActive == true) return
        val pending = visible.filter { it.confirmed }.map { it.text }.distinctBy { TextRegionTracker.key(it) }
            .filter { !cache.containsKey(TextRegionTracker.key(it)) }
        if (pending.isEmpty()) return
        val token = generation
        val selectedDirection = direction
        translationJob = scope.launch {
            try {
                for (text in pending) {
                    if (!active || token != generation) break
                    if (visible.none { it.confirmed && TextRegionTracker.key(it.text) == TextRegionTracker.key(text) }) continue
                    val translated = withTimeout(15_000) { translator.translate(text, selectedDirection) }
                    if (!active || token != generation) break
                    cache[TextRegionTracker.key(text)] = translated
                    drawLatest() // Use the latest boxes, never geometry from an old frame.
                }
                if (active && token == generation) status.text = if (frozen) "Imagem congelada • tradução alinhada à captura." else "Ao vivo • toque para focar; use dois dedos para ampliar."
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) {
                    ready = false
                    drawLatest()
                    status.text = "Tradução excedeu o tempo. Toque em Baixar idioma para tentar novamente."
                } else throw cancelled
            } catch (error: Exception) {
                ready = false
                drawLatest()
                status.text = "${error.message} Toque em Baixar idioma para tentar novamente."
            }
        }
    }

    override fun onStop() {
        active = false; generation++; analysisGeneration++
        expiryJob?.cancel(); readinessJob?.cancel()
        preview.keepScreenOn = false
        frozen = false
        frozenImage.setImageDrawable(null); frozenImage.visibility = View.GONE
        // A pending ML Kit task may still read the bitmap; let GC reclaim it safely.
        frozenBitmap = null
        AppUi.icon(freezeButton, "pause", "Congelar imagem para ler", primary = true)
        tracker.clear()
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

}
