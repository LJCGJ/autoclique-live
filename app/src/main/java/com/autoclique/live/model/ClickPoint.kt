package com.autoclique.live.model

import org.json.JSONObject
import java.util.UUID

/**
 * Um ponto da tela em que o app deve tocar.
 *
 * As coordenadas x/y são em pixels reais da tela (o mesmo sistema usado pelo
 * MotionEvent.rawX/rawY e pelo dispatchGesture do serviço de acessibilidade).
 */
data class ClickPoint(
    val id: String = UUID.randomUUID().toString(),
    /** A qual bot este ponto pertence. */
    var botId: String = "",
    var name: String = "Ponto",
    var x: Int = 0,
    var y: Int = 0,
    /** Tempo entre um clique e o próximo neste mesmo ponto. */
    var intervalMs: Long = 1000L,
    /** Quanto tempo o dedo fica "encostado" na tela. */
    var tapDurationMs: Long = 40L,
    /** Com gatilho de cor ligado: de quanto em quanto tempo reconferir a cor. */
    var pollMs: Long = 250L,
    var enabled: Boolean = true,
    /** Se true, só clica quando a cor no ponto bater com targetColor. */
    var useColor: Boolean = false,
    /** Cor gravada (ARGB). */
    var targetColor: Int = 0,
    /** 0 = cor idêntica; 100 = qualquer cor. */
    var tolerance: Int = 12,
    /** Tamanho da tela quando o ponto foi marcado — usado para não clicar torto após girar. */
    var screenW: Int = 0,
    var screenH: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("botId", botId)
        put("name", name)
        put("x", x)
        put("y", y)
        put("intervalMs", intervalMs)
        put("tapDurationMs", tapDurationMs)
        put("pollMs", pollMs)
        put("enabled", enabled)
        put("useColor", useColor)
        put("targetColor", targetColor)
        put("tolerance", tolerance)
        put("screenW", screenW)
        put("screenH", screenH)
    }

    companion object {
        fun fromJson(o: JSONObject): ClickPoint = ClickPoint(
            id = o.optString("id", UUID.randomUUID().toString()),
            botId = o.optString("botId", ""),
            name = o.optString("name", "Ponto"),
            x = o.optInt("x", 0),
            y = o.optInt("y", 0),
            intervalMs = o.optLong("intervalMs", 1000L),
            tapDurationMs = o.optLong("tapDurationMs", 40L),
            pollMs = o.optLong("pollMs", 250L),
            enabled = o.optBoolean("enabled", true),
            useColor = o.optBoolean("useColor", false),
            targetColor = o.optInt("targetColor", 0),
            tolerance = o.optInt("tolerance", 12),
            screenW = o.optInt("screenW", 0),
            screenH = o.optInt("screenH", 0)
        )
    }
}
