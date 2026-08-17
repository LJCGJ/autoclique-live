package com.autoclique.live.engine

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import com.autoclique.live.capture.ProjectionRequestActivity
import com.autoclique.live.capture.ScreenCapture
import com.autoclique.live.data.PointStore
import com.autoclique.live.service.ClickAccessibilityService
import com.autoclique.live.service.OverlayService
import com.autoclique.live.util.Perms
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Agendador dos cliques. Cada ponto tem seu próprio relógio, então dá para ter
 * um ponto clicando a cada 500 ms e outro a cada 30 s ao mesmo tempo.
 */
object AutoClicker {

    private const val TAG = "AutoClicker"

    /** Resolução do laço principal. Menor = mais preciso, mais CPU. */
    private const val TICK_MS = 30L

    /** De quanto em quanto tempo reconferir o tamanho da tela (detecção de rotação). */
    private const val SCREEN_CHECK_MS = 1000L

    private val handler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Erro no laço de cliques", t)
        _running.value = false
    }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + handler)

    private var job: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _clickCount = MutableStateFlow(0)
    val clickCount: StateFlow<Int> = _clickCount.asStateFlow()

    private val _messages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Marcado quando a tela gira, para o laço não usar o tamanho antigo por até 1 s. */
    @Volatile
    private var screenDirty = true

    fun invalidateScreenSize() {
        screenDirty = true
    }

    fun toggle(ctx: Context) {
        if (_running.value) stop() else start(ctx)
    }

    @Synchronized
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        PointStore.init(app)
        if (_running.value) return

        val active = PointStore.points.value.filter { it.enabled }
        if (active.isEmpty()) {
            say("Nenhum ponto ativo. Adicione ou ligue um ponto primeiro.")
            return
        }
        if (!Perms.isAccessibilityEnabled(app)) {
            say("Ative a Acessibilidade do AutoClique Live para ele conseguir tocar na tela.")
            return
        }
        if (!Perms.canDrawOverlays(app)) {
            say("Permita “sobrepor outros apps” para o botão flutuante.")
            return
        }
        if (active.any { it.useColor } && !ScreenCapture.ready) {
            say("Autorize a captura de tela — ela é usada só para conferir a cor do botão.")
            ProjectionRequestActivity.request(app, autoStart = true)
            return
        }

        OverlayService.ensureRunning(app)
        // A bolha fica acima de tudo: se ela estiver em cima de um alvo, o toque
        // injetado cairia nela em vez de no botão da live.
        OverlayService.moveBubbleAwayFrom(active)

        _clickCount.value = 0
        _running.value = true
        val newJob = scope.launch { loop(app) }
        job = newJob
        say("Clicador ligado.")
    }

    @Synchronized
    fun stop() {
        val current = job
        job = null
        current?.cancel()
        if (_running.value) {
            _running.value = false
            say("Clicador parado.")
        }
    }

    private suspend fun loop(ctx: Context) {
        val me = coroutineContext[Job]
        // elapsedRealtime do próximo clique permitido, por ponto.
        val nextDue = HashMap<String, Long>()
        var screen = ScreenCapture.realSize(ctx)
        var lastScreenCheck = SystemClock.elapsedRealtime()
        var warnedRotation = false
        screenDirty = false

        try {
            while (coroutineContext.isActive) {
                val svc = ClickAccessibilityService.instance
                if (svc == null) {
                    say("O serviço de acessibilidade foi desligado. Clicador parado.")
                    break
                }

                val now = SystemClock.elapsedRealtime()
                if (screenDirty || now - lastScreenCheck > SCREEN_CHECK_MS) {
                    lastScreenCheck = now
                    screenDirty = false
                    val fresh = ScreenCapture.realSize(ctx)
                    if (fresh != screen) {
                        screen = fresh
                        // Nova orientação: volta a avisar se algum ponto ficar pausado.
                        warnedRotation = false
                    }
                }

                for (p in PointStore.points.value) {
                    if (!coroutineContext.isActive) break
                    if (!p.enabled) continue
                    if (now < (nextDue[p.id] ?: 0L)) continue

                    // Ponto marcado em outra orientação/resolução: clicar aqui acertaria
                    // um lugar aleatório da tela. Melhor esperar voltar.
                    if (p.screenW > 0 && (p.screenW != screen.first || p.screenH != screen.second)) {
                        nextDue[p.id] = now + 1000L
                        if (!warnedRotation) {
                            warnedRotation = true
                            say("Tela girou: pontos pausados até voltar à orientação original.")
                        }
                        continue
                    }

                    if (p.useColor) {
                        val pixel = ScreenCapture.pixelAt(p.x, p.y)
                        if (pixel == null || !colorMatches(pixel, p.targetColor, p.tolerance)) {
                            nextDue[p.id] = now + p.pollMs.coerceAtLeast(50L)
                            continue
                        }
                    }

                    svc.tap(p.x, p.y, p.tapDurationMs)
                    _clickCount.value = _clickCount.value + 1
                    nextDue[p.id] = now + p.intervalMs.coerceAtLeast(50L)
                }
                delay(TICK_MS)
            }
        } finally {
            // Só desliga a flag se este laço ainda for o laço atual — senão um job
            // antigo sendo cancelado apagaria o estado de um job novo.
            synchronized(this) {
                if (job === me || job == null) _running.value = false
            }
        }
    }

    /**
     * Distância euclidiana em RGB, normalizada para 0–100.
     * tolerance = 0 exige cor idêntica; 100 aceita qualquer cor.
     */
    fun colorMatches(actual: Int, target: Int, tolerancePercent: Int): Boolean {
        val dr = Color.red(actual) - Color.red(target)
        val dg = Color.green(actual) - Color.green(target)
        val db = Color.blue(actual) - Color.blue(target)
        val distance = sqrt((dr * dr + dg * dg + db * db).toDouble())
        val maxDistance = sqrt(3.0) * 255.0
        return (distance / maxDistance) * 100.0 <= tolerancePercent.toDouble()
    }

    private fun say(msg: String) {
        _messages.tryEmit(msg)
    }
}
