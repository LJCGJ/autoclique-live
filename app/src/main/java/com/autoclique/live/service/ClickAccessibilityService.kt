package com.autoclique.live.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Único componente capaz de tocar na tela por cima de outros apps.
 * Não lê conteúdo de tela — só executa gestos.
 */
class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickA11y"

        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* não usamos eventos */ }

    override fun onInterrupt() { /* nada a interromper */ }

    /**
     * Executa um toque em (x, y). Nunca lança: coordenadas fora da tela fazem o
     * GestureDescription jogar IllegalArgumentException, e isso derrubaria o app.
     */
    fun tap(x: Int, y: Int, durationMs: Long = 40L): Boolean = runCatching {
        if (x < 0 || y < 0) return false
        val fx = x.toFloat()
        val fy = y.toFloat()
        val path = Path().apply {
            moveTo(fx, fy)
            // Um Path totalmente vazio é rejeitado; 1px mantém o gesto como um toque.
            lineTo(fx + 1f, fy + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(1L, 5000L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }.getOrElse {
        Log.w(TAG, "Toque recusado em ($x, $y): ${it.message}")
        false
    }
}
