package com.autoclique.live.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
import com.autoclique.live.util.Perms
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

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    PointStore.points.collect { list ->
                        adapter.submit(list)
                        b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_OPEN_EDITOR_FOR) ?: return
        intent.removeExtra(EXTRA_OPEN_EDITOR_FOR)
        PointStore.find(id)?.let { point ->
            b.root.post { if (!isFinishing && !isDestroyed) showEditor(point) }
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        refreshPermissions()
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
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

    /** Abre a mira em tela cheia e manda o app para segundo plano. */
    private fun launchPicker(pointId: String?) {
        if (!Perms.canDrawOverlays(this)) {
            toast("Primeiro permita “sobrepor outros apps”.")
            startActivity(Perms.overlaySettingsIntent(this))
            return
        }
        OverlayService.ensureRunning(this)
        OverlayService.showPicker(this, pointId)
        toast("Abra o app da live, arraste a mira até o botão e confirme.")
        moveTaskToBack(true)
    }

    private fun showEditor(point: ClickPoint) {
        val d = DialogPointEditorBinding.inflate(LayoutInflater.from(this))

        d.etName.setText(point.name)
        d.etInterval.setText(point.intervalMs.toString())
        d.etDuration.setText(point.tapDurationMs.toString())
        d.etPoll.setText(point.pollMs.toString())
        d.tvCoords.text = "Posição: x ${point.x}, y ${point.y}"
        d.cbColor.isChecked = point.useColor
        d.sbTolerance.progress = point.tolerance
        d.colorBox.visibility = if (point.useColor) View.VISIBLE else View.GONE
        d.vColor.paintSwatch(point.targetColor)
        d.tvColor.text = if (point.useColor) "Cor gravada: ${hex(point.targetColor)}"
        else "Nenhuma cor gravada — use “Remarcar posição”."
        d.tvTolerance.text = "${getString(R.string.field_tolerance)}: ${point.tolerance}%"

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
                val updated = point.copy(
                    name = d.etName.text.toString().ifBlank { point.name },
                    intervalMs = d.etInterval.text.toString().toLongOrNull()?.coerceAtLeast(50L)
                        ?: point.intervalMs,
                    tapDurationMs = d.etDuration.text.toString().toLongOrNull()?.coerceIn(1L, 5000L)
                        ?: point.tapDurationMs,
                    pollMs = d.etPoll.text.toString().toLongOrNull()?.coerceAtLeast(50L)
                        ?: point.pollMs,
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
