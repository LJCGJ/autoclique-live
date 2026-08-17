package com.autoclique.live.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.autoclique.live.R
import com.autoclique.live.capture.CaptureService
import com.autoclique.live.capture.ProjectionRequestActivity
import com.autoclique.live.capture.ScreenCapture
import com.autoclique.live.data.PointStore
import com.autoclique.live.databinding.OverlayBubbleBinding
import com.autoclique.live.databinding.OverlayPickerBinding
import com.autoclique.live.engine.AutoClicker
import com.autoclique.live.model.ClickPoint
import com.autoclique.live.ui.MainActivity
import com.autoclique.live.ui.hex
import com.autoclique.live.ui.paintSwatch
import com.autoclique.live.util.Notifs
import com.autoclique.live.util.Perms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mantém o botão flutuante (liga/desliga sem voltar ao app) e hospeda o
 * seletor de posição em tela cheia.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW_BUBBLE = "com.autoclique.live.SHOW_BUBBLE"
        const val ACTION_TOGGLE = "com.autoclique.live.TOGGLE"
        const val ACTION_SHOW_PICKER = "com.autoclique.live.SHOW_PICKER"
        const val ACTION_STOP_ALL = "com.autoclique.live.STOP_ALL"
        const val EXTRA_POINT_ID = "pointId"

        @Volatile
        private var instance: OverlayService? = null

        /** Afasta a bolha de qualquer ponto de clique — ela fica acima de tudo e roubaria o toque. */
        fun moveBubbleAwayFrom(points: List<ClickPoint>) {
            instance?.avoidPoints(points)
        }

        fun ensureRunning(ctx: Context) {
            if (!Perms.canDrawOverlays(ctx)) return
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, OverlayService::class.java).setAction(ACTION_SHOW_BUBBLE)
            )
        }

        /** Abre a mira em tela cheia. pointId nulo cria um ponto novo. */
        fun showPicker(ctx: Context, pointId: String?) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, OverlayService::class.java)
                    .setAction(ACTION_SHOW_PICKER)
                    .putExtra(EXTRA_POINT_ID, pointId)
            )
        }
    }

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var bubble: OverlayBubbleBinding? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var picker: OverlayPickerBinding? = null

    // Estado do seletor
    private var pickX = 0
    private var pickY = 0
    private var pickedColor: Int? = null
    private var colorInherited = false
    private var editingId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        Notifs.ensureChannels(this)
        PointStore.init(this)
        startAsForeground()
        observeEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> AutoClicker.toggle(this)
            ACTION_STOP_ALL -> {
                AutoClicker.stop()
                removePicker()
                removeBubble()
                // Encerra o serviço de captura também — senão a notificação de
                // "lendo a tela" fica presa para sempre.
                CaptureService.stop(this)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_PICKER -> {
                editingId = intent.getStringExtra(EXTRA_POINT_ID)
                showPickerOverlay()
            }
            else -> showBubble()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AutoClicker.invalidateScreenSize()
    }

    override fun onDestroy() {
        instance = null
        removePicker()
        removeBubble()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- bolha

    private fun showBubble() {
        if (bubble != null || !Perms.canDrawOverlays(this)) return
        val themed = ContextThemeWrapper(this, R.style.Theme_AutoClique)
        val b = OverlayBubbleBinding.inflate(LayoutInflater.from(themed))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // LAYOUT_IN_SCREEN/NO_LIMITS deixam x,y no mesmo espaço absoluto usado
            // pelos ClickPoint — sem isso a checagem de colisão erra pela status bar.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        b.root.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var dragged = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = e.rawX
                        touchY = e.rawY
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - touchX
                        val dy = e.rawY - touchY
                        if (abs(dx) > 12f || abs(dy) > 12f) dragged = true
                        if (dragged) {
                            params.x = startX + dx.roundToInt()
                            params.y = startY + dy.roundToInt()
                            runCatching { wm.updateViewLayout(b.root, params) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) AutoClicker.toggle(this@OverlayService)
                        return true
                    }
                }
                return false
            }
        })

        runCatching { wm.addView(b.root, params) }
            .onSuccess {
                bubble = b
                bubbleParams = params
                paintBubble(AutoClicker.running.value)
                // O serviço sobe de forma assíncrona, então a primeira checagem de
                // colisão precisa acontecer aqui, já com a view medida.
                b.root.post { avoidPoints(PointStore.points.value.filter { p -> p.enabled }) }
            }
    }

    private fun removeBubble() {
        bubble?.let { runCatching { wm.removeView(it.root) } }
        bubble = null
        bubbleParams = null
    }

    private fun paintBubble(running: Boolean) {
        val b = bubble ?: return
        b.bubbleLabel.text = if (running) "❚❚" else "▶"
        val color = ContextCompat.getColor(this, if (running) R.color.running else R.color.idle)
        // mutate() para não alterar o drawable compartilhado do recurso.
        val bg = b.bubbleLabel.background?.mutate()
        if (bg is GradientDrawable) {
            bg.setColor(color)
            b.bubbleLabel.background = bg
        } else {
            b.bubbleLabel.setBackgroundColor(color)
        }
    }

    /** Desloca a bolha até que ela não cubra nenhum ponto de clique configurado. */
    private fun avoidPoints(points: List<ClickPoint>) {
        if (points.isEmpty()) return
        scope.launch {
            val b = bubble ?: return@launch
            val params = bubbleParams ?: return@launch
            val fallback = (66 * resources.displayMetrics.density).toInt()
            val w = b.root.width.takeIf { it > 0 } ?: fallback
            val h = b.root.height.takeIf { it > 0 } ?: fallback
            val (sw, sh) = ScreenCapture.realSize(this@OverlayService)
            var moved = false
            var attempts = 0
            while (attempts < 12) {
                val rect = Rect(params.x - 8, params.y - 8, params.x + w + 8, params.y + h + 8)
                if (points.none { rect.contains(it.x, it.y) }) break
                params.y += h + 32
                if (params.y + h > sh) {
                    params.y = 32
                    params.x = (sw - w - 32).coerceAtLeast(0)
                }
                moved = true
                attempts++
            }
            if (moved) {
                runCatching { wm.updateViewLayout(b.root, params) }
                val free = Rect(params.x - 8, params.y - 8, params.x + w + 8, params.y + h + 8)
                    .let { r -> points.none { r.contains(it.x, it.y) } }
                toast(
                    if (free) "Movi a bolha para não cobrir um ponto de clique."
                    else "Atenção: a bolha está sobre um ponto de clique. Arraste-a para outro lugar."
                )
            }
        }
    }

    // -------------------------------------------------------------- seletor

    private fun showPickerOverlay() {
        if (!Perms.canDrawOverlays(this)) {
            toast("Permita “sobrepor outros apps” para marcar pontos.")
            return
        }
        // removePicker() zera editingId — guarde qual ponto estamos editando.
        val editing = editingId
        removePicker()
        editingId = editing

        val themed = ContextThemeWrapper(this, R.style.Theme_AutoClique)
        val p = OverlayPickerBinding.inflate(LayoutInflater.from(themed))

        val existing = PointStore.find(editingId)
        pickedColor = existing?.takeIf { it.useColor }?.targetColor
        colorInherited = pickedColor != null
        val metrics = ScreenCapture.realSize(this)
        pickX = existing?.x ?: (metrics.first / 2)
        pickY = existing?.y ?: (metrics.second / 2)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        p.touchArea.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    pickX = e.rawX.roundToInt()
                    pickY = e.rawY.roundToInt()
                    // Mudou de lugar: a cor gravada era do botão antigo, não vale mais.
                    if (colorInherited) {
                        colorInherited = false
                        pickedColor = null
                    }
                    updateCrosshair(p)
                    true
                }
                else -> false
            }
        }

        p.btnCancel.setOnClickListener { removePicker() }
        p.btnColor.setOnClickListener { grabColor(p) }
        p.btnConfirm.setOnClickListener { confirmPoint() }

        runCatching { wm.addView(p.root, params) }
            .onSuccess {
                picker = p
                bubble?.root?.visibility = View.GONE
                updateCrosshair(p)
            }
    }

    private fun updateCrosshair(p: OverlayPickerBinding) {
        val size = p.crosshair.layoutParams.width.takeIf { it > 0 } ?: 240
        val lp = p.crosshair.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = pickX - size / 2
        lp.topMargin = pickY - size / 2
        p.crosshair.layoutParams = lp
        p.coords.text = "x: $pickX   y: $pickY" +
            (pickedColor?.let { "   •   ${hex(it)}" } ?: "")
        p.swatch.paintSwatch(pickedColor)
    }

    private fun grabColor(p: OverlayPickerBinding) {
        if (!ScreenCapture.ready) {
            toast("Autorize a captura de tela e toque em “Gravar cor” de novo.")
            ProjectionRequestActivity.request(this, autoStart = false)
            return
        }
        // Esconde a mira para não gravar a cor do próprio overlay. Em vez de esperar
        // um tempo fixo, espera chegarem quadros NOVOS — senão a cor gravada seria a
        // do scrim escuro por cima do botão, e o gatilho nunca casaria.
        val before = ScreenCapture.frameId
        p.root.visibility = View.INVISIBLE
        scope.launch {
            var waited = 0L
            while (ScreenCapture.frameId < before + 2 && waited < 1500L) {
                delay(50)
                waited += 50
            }
            val fresh = ScreenCapture.frameId >= before + 2
            val color = if (fresh) ScreenCapture.pixelAt(pickX, pickY) else null
            p.root.visibility = View.VISIBLE
            if (color == null) {
                toast("Não consegui ler a tela agora. Tente de novo.")
            } else {
                pickedColor = color
                colorInherited = false
                updateCrosshair(p)
                toast("Cor gravada.")
            }
        }
    }

    private fun confirmPoint() {
        val existing = PointStore.find(editingId)
        val (sw, sh) = ScreenCapture.realSize(this)
        val point = (existing ?: ClickPoint(name = PointStore.suggestName())).copy(
            x = pickX,
            y = pickY,
            useColor = pickedColor != null || (existing?.useColor ?: false),
            targetColor = pickedColor ?: existing?.targetColor ?: 0,
            screenW = sw,
            screenH = sh
        )
        PointStore.upsert(point)
        removePicker()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_OPEN_EDITOR_FOR, point.id)
        )
    }

    private fun removePicker() {
        picker?.let { runCatching { wm.removeView(it.root) } }
        picker = null
        editingId = null
        pickedColor = null
        colorInherited = false
        bubble?.root?.visibility = View.VISIBLE
    }

    // --------------------------------------------------------- notificação

    private fun observeEngine() {
        scope.launch {
            AutoClicker.running.collectLatest { running ->
                paintBubble(running)
                pushNotification(running, AutoClicker.clickCount.value)
            }
        }
        scope.launch {
            while (isActive) {
                delay(3000)
                if (AutoClicker.running.value) {
                    pushNotification(true, AutoClicker.clickCount.value)
                }
            }
        }
        scope.launch {
            // A MainActivity também mostra as mensagens; evita toast duplicado.
            AutoClicker.messages.collectLatest { if (!MainActivity.isVisible) toast(it) }
        }
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifs.ID_OVERLAY, buildNotification(false, 0), type)
    }

    private fun pushNotification(running: Boolean, clicks: Int) {
        runCatching {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm?.notify(Notifs.ID_OVERLAY, buildNotification(running, clicks))
        }
    }

    private fun buildNotification(running: Boolean, clicks: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val close = PendingIntent.getService(
            this, 2,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, Notifs.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_click)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                if (running) "Clicando — $clicks toque(s) até agora"
                else "Parado — toque na bolha para iniciar"
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, if (running) getString(R.string.stop) else getString(R.string.start), toggle)
            .addAction(0, "Encerrar", close)
            .build()
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
