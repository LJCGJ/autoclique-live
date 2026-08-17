package com.autoclique.live.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.autoclique.live.R
import com.autoclique.live.capture.CaptureService
import com.autoclique.live.capture.ProjectionRequestActivity
import com.autoclique.live.capture.ScreenCapture
import com.autoclique.live.data.PointStore
import com.autoclique.live.databinding.ActivityMainBinding
import com.autoclique.live.databinding.DialogPointEditorBinding
import com.autoclique.live.engine.AutoClicker
import com.autoclique.live.model.ClickPoint
import com.autoclique.live.service.OverlayService
import com.autoclique.live.util.Consent
import com.autoclique.live.util.Perms
import com.autoclique.live.util.Tempo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_EDITOR_FOR = "openEditorFor"

        /** O overlay usa isto para não repetir os avisos que esta tela já mostra. */
        @Volatile
        var isVisible = false
            private set
    }

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: PointAdapter

    /** Evita que preencher o Spinner por código dispare onItemSelected. */
    private var ignorarSpinner = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sem o aceite da divulgacao, nenhuma parte do app fica acessivel —
        // inclusive os botoes que pedem acessibilidade e sobreposicao.
        if (!Consent.aceitou(this)) {
            startActivity(Intent(this, DisclosureActivity::class.java))
            finish()
            return
        }

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        PointStore.init(this)

        adapter = PointAdapter(
            onToggle = { point, checked -> PointStore.upsert(point.copy(enabled = checked)) },
            onEdit = { point -> showEditor(point) }
        )
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        b.btnAdd.setOnClickListener { launchPicker(null) }
        b.btnToggle.setOnClickListener { AutoClicker.toggle(this) }

        configurarBots()
        configurarPermissoes()
        observar()

        handleIntent(intent)
    }

    // ------------------------------------------------------------------ bots

    private fun configurarBots() {
        b.spBots.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (ignorarSpinner) return
                val bot = PointStore.bots.value.getOrNull(pos) ?: return
                PointStore.selecionarBot(bot.id)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        b.btnBotNovo.setOnClickListener {
            pedirNome(getString(R.string.bot_criar_titulo), "") { nome ->
                PointStore.criarBot(nome)
                toast("Bot “$nome” criado. Adicione os pontos dele.")
            }
        }

        b.btnBotRenomear.setOnClickListener {
            val bot = PointStore.botAtual() ?: return@setOnClickListener
            pedirNome(getString(R.string.bot_renomear_titulo), bot.name) { nome ->
                PointStore.renomearBot(bot.id, nome)
            }
        }

        b.btnBotExcluir.setOnClickListener {
            val bot = PointStore.botAtual() ?: return@setOnClickListener
            val qtd = PointStore.pontosDoBot(bot.id).size
            AlertDialog.Builder(this)
                .setTitle(R.string.bot_excluir_titulo)
                .setMessage(getString(R.string.bot_excluir_msg, bot.name, qtd))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    if (AutoClicker.running.value) AutoClicker.stop()
                    PointStore.excluirBot(bot.id)
                }
                .show()
        }
    }

    /** Caixa simples de texto, usada para criar e para renomear um bot. */
    private fun pedirNome(titulo: String, valorInicial: String, aoConfirmar: (String) -> Unit) {
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.bot_nome_hint)
            setText(valorInicial)
            setSelection(valorInicial.length)
        }
        val margem = (24 * resources.displayMetrics.density).toInt()
        val caixa = android.widget.FrameLayout(this).apply {
            setPadding(margem, margem / 2, margem, 0)
            addView(campo)
        }

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(caixa)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val nome = campo.text.toString().trim()
                if (nome.isNotBlank()) aoConfirmar(nome)
            }
            .show()
    }

    private fun redesenharBots() {
        val bots = PointStore.bots.value
        val nomes = bots.map { it.name }
        val ad = ArrayAdapter(this, android.R.layout.simple_spinner_item, nomes)
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        ignorarSpinner = true
        b.spBots.adapter = ad
        val idx = bots.indexOfFirst { it.id == PointStore.botAtivo.value }
        if (idx >= 0) b.spBots.setSelection(idx)
        ignorarSpinner = false

        // Com um bot só, excluir deixaria a lista vazia; o store recria outro
        // na hora, então o botão só confunde. Melhor escondê-lo.
        b.btnBotExcluir.isEnabled = bots.size > 1
        b.btnBotExcluir.alpha = if (bots.size > 1) 1f else 0.4f
    }

    // --------------------------------------------------------------- fluxos

    private fun observar() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    PointStore.bots.collect { redesenharBots() }
                }
                launch {
                    // A lista mostra só os pontos do bot selecionado, então
                    // precisa reagir tanto a mudanças nos pontos quanto no bot.
                    combine(PointStore.points, PointStore.botAtivo) { todos, botId ->
                        todos.filter { it.botId == botId }
                    }.collect { lista ->
                        adapter.submit(lista)
                        b.tvEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                        redesenharBots()
                    }
                }
                launch {
                    AutoClicker.running.collect { running ->
                        b.btnToggle.text = getString(if (running) R.string.stop else R.string.start)
                        b.tvStatus.text = if (running) "Clicando…" else "Parado"
                    }
                }
                launch { AutoClicker.messages.collect { toast(it) } }
                launch {
                    AutoClicker.clickCount.collect { n ->
                        if (AutoClicker.running.value) b.tvStatus.text = "Clicando — $n toque(s)"
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (!::b.isInitialized) return
        val id = intent?.getStringExtra(EXTRA_OPEN_EDITOR_FOR) ?: return
        intent.removeExtra(EXTRA_OPEN_EDITOR_FOR)
        PointStore.find(id)?.let { point ->
            b.root.post { if (!isFinishing && !isDestroyed) showEditor(point) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Se saímos no onCreate por falta de consentimento, o binding nem existe.
        if (!::b.isInitialized) return
        isVisible = true
        refreshPermissions()
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
    }

    // ----------------------------------------------------------- permissões

    private fun configurarPermissoes() {
        b.btnPermAccessibility.setOnClickListener {
            toast("Encontre “AutoClique Live” na lista e ative.")
            startActivity(Perms.accessibilitySettingsIntent())
        }
        b.btnPermOverlay.setOnClickListener { startActivity(Perms.overlaySettingsIntent(this)) }
        b.btnPermNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        b.btnPermCapture.setOnClickListener {
            if (ScreenCapture.ready) {
                CaptureService.stop(this)
                b.root.postDelayed({ refreshPermissions() }, 400)
            } else {
                ProjectionRequestActivity.request(this, autoStart = false)
            }
        }
    }

    private fun refreshPermissions() {
        fun mark(button: android.widget.Button, granted: Boolean) {
            button.text = if (granted) getString(R.string.granted) else getString(R.string.grant)
            button.alpha = if (granted) 0.5f else 1f
        }
        mark(b.btnPermAccessibility, Perms.isAccessibilityEnabled(this))
        mark(b.btnPermOverlay, Perms.canDrawOverlays(this))
        mark(b.btnPermNotification, Perms.hasNotificationPermission(this))
        mark(b.btnPermCapture, ScreenCapture.ready)
        b.btnPermNotification.isEnabled = !Perms.hasNotificationPermission(this)
    }

    // -------------------------------------------------------------- pontos

    /** Abre a mira em tela cheia e manda o app para segundo plano. */
    private fun launchPicker(pointId: String?) {
        if (!Perms.canDrawOverlays(this)) {
            toast("Primeiro permita “sobrepor outros apps”.")
            startActivity(Perms.overlaySettingsIntent(this))
            return
        }
        OverlayService.ensureRunning(this)
        OverlayService.showPicker(this, pointId)
        toast("Abra o app alvo, arraste a mira até o botão e confirme.")
        moveTaskToBack(true)
    }

    private fun showEditor(point: ClickPoint) {
        val d = DialogPointEditorBinding.inflate(LayoutInflater.from(this))

        d.etName.setText(point.name)
        d.etInterval.setText(Tempo.msParaSegundos(point.intervalMs))
        d.etDuration.setText(point.tapDurationMs.toString())
        d.etPoll.setText(Tempo.msParaSegundos(point.pollMs))
        d.tvCoords.text = "Posição: x ${point.x}, y ${point.y}"
        d.cbColor.isChecked = point.useColor
        d.sbTolerance.progress = point.tolerance
        d.colorBox.visibility = if (point.useColor) View.VISIBLE else View.GONE
        d.vColor.paintSwatch(point.targetColor)
        d.tvColor.text = if (point.useColor) "Cor gravada: ${hex(point.targetColor)}"
        else "Nenhuma cor gravada — use “Remarcar posição”."
        d.tvTolerance.text = "${getString(R.string.field_tolerance)}: ${point.tolerance}%"

        // Taxa de cliques recalculada a cada tecla digitada.
        fun atualizarTaxa() {
            val ms = Tempo.segundosParaMs(d.etInterval.text.toString())
            d.tvTaxa.text = if (ms == null) "—" else Tempo.cliquesPorSegundo(ms)
        }
        atualizarTaxa()
        d.etInterval.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = atualizarTaxa()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        d.cbColor.setOnCheckedChangeListener { _, checked ->
            d.colorBox.visibility = if (checked) View.VISIBLE else View.GONE
        }
        d.sbTolerance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                d.tvTolerance.text = "${getString(R.string.field_tolerance)}: $value%"
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.editor_title)
            .setView(d.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.delete, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val intervalo = Tempo.segundosParaMs(d.etInterval.text.toString())
                if (intervalo == null) {
                    toast("Informe o intervalo em segundos, por exemplo 1 ou 0,5.")
                    return@setOnClickListener
                }
                val updated = point.copy(
                    name = d.etName.text.toString().ifBlank { point.name },
                    intervalMs = intervalo,
                    tapDurationMs = d.etDuration.text.toString().toLongOrNull()?.coerceIn(1L, 5000L)
                        ?: point.tapDurationMs,
                    pollMs = Tempo.segundosParaMs(d.etPoll.text.toString()) ?: point.pollMs,
                    useColor = d.cbColor.isChecked,
                    tolerance = d.sbTolerance.progress
                )
                PointStore.upsert(updated)
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                PointStore.remove(point.id)
                dialog.dismiss()
            }
        }

        d.btnRemark.setOnClickListener {
            dialog.dismiss()
            launchPicker(point.id)
        }

        dialog.show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
