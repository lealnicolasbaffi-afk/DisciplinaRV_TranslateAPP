package br.edu.tradutoroffline.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import br.edu.tradutoroffline.domain.*
import br.edu.tradutoroffline.features.conversation.ConversationController

/** Single native screen, deliberately small enough for an academic demonstration. */
class MainActivity : Activity() {
    private lateinit var controller: ConversationController
    private lateinit var direction: Spinner
    private lateinit var state: TextView
    private lateinit var preparation: TextView
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var raw: TextView
    private lateinit var metrics: TextView
    private lateinit var notes: EditText
    private lateinit var airplane: CheckBox
    private lateinit var asrQuality: Spinner
    private lateinit var translationQuality: Spinner
    private lateinit var record: Button
    private lateinit var stop: Button
    private lateinit var prepare: Button
    private lateinit var verify: Button
    private lateinit var voices: Button
    private lateinit var cancel: Button
    private lateinit var setupToggle: Button
    private lateinit var setupPanel: LinearLayout
    private lateinit var measurementsToggle: Button
    private lateinit var measurementsPanel: LinearLayout
    private var setupExpanded = true
    private var measurementsExpanded = false
    private var lastResourcesReady = false
    private lateinit var modelPT: Button
    private lateinit var modelEN: Button
    private lateinit var importAudio: Button
    private lateinit var translate: Button
    private lateinit var original: Button
    private lateinit var replay: Button
    private lateinit var fresh: Button
    private lateinit var evaluate: Button
    private lateinit var export: Button
    private lateinit var cameraTranslation: Button
    private var rendering = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val retained = lastNonConfigurationInstance as? ConversationController
        controller = retained ?: ConversationController(applicationContext) {}
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        var section = body
        scroll.addView(body)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        fun text(value: String, size: Float = 16f): TextView = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.rgb(24, 38, 48)); setPadding(0, dp(9), 0, dp(5)); section.addView(this)
        }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label; isAllCaps = false; minHeight = dp(48)
            setOnClickListener { action() }; section.addView(this)
        }
        fun edit(hintText: String, minLinesCount: Int): EditText = EditText(this).apply {
            hint = hintText; contentDescription = hintText; minLines = minLinesCount
            gravity = android.view.Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            section.addView(this)
        }
        fun spinner(labels: List<String>, selected: (Int) -> Unit): Spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            minimumHeight = dp(48)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (!rendering) selected(position) }
            }
            section.addView(this)
        }

        text("Tradutor Offline", 26f)
        text("Português brasileiro ↔ inglês americano · turnos de até 20 s")
        cameraTranslation = button("Traduzir com a câmera") {
            if (!controller.busy) startActivity(Intent(this, br.edu.tradutoroffline.features.camera.CameraTranslationActivity::class.java))
        }
        text("Direção", 20f)
        direction = spinner(Direction.entries.map { it.label }) { controller.selectDirection(Direction.entries[it]) }
        direction.contentDescription = "Direção da tradução"
        setupToggle = button("Configurar idiomas e vozes") { setupExpanded = !setupExpanded; render() }
        setupPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; body.addView(this) }
        section = setupPanel
        prepare = button("Baixar e preparar idiomas (Wi-Fi)") { controller.prepare() }
        verify = button("Verificar recursos locais (sem baixar)") { controller.prepare(download = false) }
        voices = button("Instalar ou configurar vozes offline") {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent("com.android.settings.TTS_SETTINGS"), VOICES)
            } catch (_: ActivityNotFoundException) {
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(android.provider.Settings.ACTION_SETTINGS), VOICES)
                } catch (_: ActivityNotFoundException) {
                    controller.notice("Abra as configurações do aparelho e instale as vozes offline pt-BR e en-US em Texto para fala.")
                }
            }
        }
        text("A preparação baixa cerca de 72 MB de reconhecimento e o pacote de tradução. Instale também as vozes pt-BR e en-US nas configurações de voz.", 14f)
        modelPT = button("Importar modelo Vosk PT (ZIP)") { openFile(MODEL_PT, "application/zip") }
        modelEN = button("Importar modelo Vosk EN (ZIP)") { openFile(MODEL_EN, "application/zip") }
        preparation = text("")
        section = body
        text("Estado", 20f)
        state = text("")
        state.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        cancel = button("Cancelar operação") { controller.interrupt("Operação cancelada. Os recursos já instalados foram preservados.") }
        text("Gravação", 20f)
        record = button("Gravar") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.record()
            else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE)
        }
        stop = button("Parar") { controller.stopRecording() }
        importAudio = button("Importar áudio de reserva") { openFile(AUDIO, "audio/*") }
        text("Transcrição (editável)", 20f)
        input = edit("Fale, importe um áudio ou digite uma frase", 3)
        original = button("Ouvir original") { controller.replayOriginal() }
        translate = button("Traduzir e falar") { controller.translate() }
        input.addTextChangedListener(watcher { value ->
            if (!rendering) {
                controller.input = value
                translate.isEnabled = controller.canTranslate && value.isNotBlank()
            }
        })
        text("Tradução", 20f)
        output = text("")
        output.setTextIsSelectable(true)
        replay = button("Reproduzir tradução") { controller.replayTranslation() }
        fresh = button("Nova rodada") { controller.reset() }
        measurementsToggle = button("Medições e avaliação") { measurementsExpanded = !measurementsExpanded; render() }
        measurementsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; body.addView(this) }
        section = measurementsPanel
        metrics = text("")
        raw = text("")
        raw.setTextIsSelectable(true)
        airplane = CheckBox(this).apply {
            text = "Confirmo modo avião e Wi-Fi desligado (declaração manual)"
            setOnCheckedChangeListener { _, checked -> if (!rendering) controller.airplane = checked }
            section.addView(this)
        }
        text("Qualidade da transcrição original")
        asrQuality = spinner(Quality.entries.map { it.label }) { controller.transcriptionQuality = Quality.entries[it].label }
        asrQuality.contentDescription = "Qualidade da transcrição original"
        text("Qualidade da tradução")
        translationQuality = spinner(Quality.entries.map { it.label }) { controller.translationQuality = Quality.entries[it].label }
        translationQuality.contentDescription = "Qualidade da tradução"
        notes = edit("Notas: frase/repetição, ruído, distância, falante, rede, bateria, aquecimento, fria/quente", 3)
        notes.addTextChangedListener(watcher { if (!rendering) controller.notes = it })
        evaluate = button("Salvar avaliação") { controller.saveEvaluation() }
        export = button("Exportar medições JSON") {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "medicoes.json")
            }, EXPORT)
        }
        section = body
        text("Tempos são aproximações por callbacks. Nenhum teste físico é criado automaticamente. Modelos e vozes devem ser preparados antes do modo avião.", 14f)
        setContentView(scroll)
        controller.observe { render() }
        render()
        if (retained == null) controller.prepare(download = false)
    }

    private fun render() {
        rendering = true
        try {
            state.text = if (controller.storageFailure == null) "${controller.stage.label}\n${controller.message}"
                else "Erro de armazenamento\n${controller.storageFailure}\n${controller.message}"
            preparation.text = controller.preparation
            val resourcesReady = controller.speechReady && controller.translationReady && controller.voiceReady
            if (resourcesReady && !lastResourcesReady) setupExpanded = false
            lastResourcesReady = resourcesReady
            setupPanel.visibility = if (setupExpanded) View.VISIBLE else View.GONE
            setupToggle.text = if (setupExpanded) "Ocultar configuração de idiomas" else if (resourcesReady) "Idiomas prontos · configurar" else "Configurar idiomas e vozes"
            measurementsPanel.visibility = if (measurementsExpanded) View.VISIBLE else View.GONE
            measurementsToggle.text = "${if (measurementsExpanded) "Ocultar" else "Mostrar"} medições e avaliação · ${controller.count} salvas"
            direction.setSelection(controller.direction.ordinal)
            if (input.text.toString() != controller.input) input.setText(controller.input)
            output.text = controller.output.ifBlank { "A tradução aparecerá aqui." }
            if (notes.text.toString() != controller.notes) notes.setText(controller.notes)
            airplane.isChecked = controller.airplane
            asrQuality.setSelection(Quality.entries.indexOfFirst { it.label == controller.transcriptionQuality }.coerceAtLeast(0))
            translationQuality.setSelection(Quality.entries.indexOfFirst { it.label == controller.translationQuality }.coerceAtLeast(0))
            val m = controller.current
            fun ms(value: Double?) = value?.let { "%.0f ms".format(java.util.Locale.ROOT, it) } ?: "não medido"
            metrics.text = "${controller.count} rodadas salvas no aparelho\nOrigem: ${m?.origin ?: "nenhuma"} · editada: ${m?.edited ?: false}\nParar → transcrição: ${ms(m?.transcriptionMS)}\nParar → texto: ${ms(m?.textMS)}\nParar → início de síntese: ${ms(m?.audioStartMS)}\nChamada de tradução: ${ms(m?.translationMS)}"
            raw.text = "Transcrição/texto original para avaliação: ${m?.rawInput.orEmpty()}"
            direction.isEnabled = !controller.busy
            cameraTranslation.isEnabled = !controller.busy
            prepare.isEnabled = !controller.busy
            verify.isEnabled = !controller.busy
            voices.isEnabled = !controller.busy
            cancel.visibility = if (controller.busy && controller.stage != Stage.RECORDING) View.VISIBLE else View.GONE
            modelPT.isEnabled = !controller.busy; modelEN.isEnabled = !controller.busy
            record.isEnabled = controller.canRecord
            importAudio.isEnabled = controller.canRecord
            stop.isEnabled = controller.stage == Stage.RECORDING
            translate.isEnabled = controller.canTranslate && controller.input.isNotBlank()
            input.isEnabled = !controller.busy
            original.isEnabled = !controller.busy && controller.original?.exists() == true
            replay.isEnabled = !controller.busy && controller.output.isNotBlank() && controller.voiceReady
            fresh.isEnabled = !controller.busy
            notes.isEnabled = !controller.busy
            airplane.isEnabled = !controller.busy
            asrQuality.isEnabled = !controller.busy; translationQuality.isEnabled = !controller.busy
            evaluate.isEnabled = !controller.busy && controller.current != null
            export.isEnabled = controller.canExport
        } finally { rendering = false }
    }

    private fun openFile(code: Int, mime: String) {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }, code)
    }

    @Deprecated("Native Activity callback kept to avoid an additional UI dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICES) { controller.prepare(download = false); return }
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            MODEL_PT -> controller.importModel(uri, "pt")
            MODEL_EN -> controller.importModel(uri, "en")
            AUDIO -> controller.importAudio(uri)
            EXPORT -> controller.export(uri)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MICROPHONE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) controller.record()
            else controller.notice("Permissão de microfone negada. Autorize em Configurações → Apps → Tradutor Offline → Permissões, ou importe um áudio de reserva.")
        }
    }

    override fun onStop() { controller.interrupt(); super.onStop() }
    @Deprecated("Native Activity retention keeps the current turn across configuration changes")
    override fun onRetainNonConfigurationInstance(): Any = controller

    override fun onDestroy() {
        controller.observe {}
        if (!isChangingConfigurations) controller.close()
        super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun watcher(update: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update(s?.toString().orEmpty()) }
        override fun afterTextChanged(s: Editable?) {}
    }
    companion object {
        private const val MODEL_PT = 10
        private const val MODEL_EN = 11
        private const val AUDIO = 12
        private const val EXPORT = 13
        private const val VOICES = 14
        private const val MICROPHONE = 20
    }
}
