package com.autoclique.live.util

import android.content.Context

/**
 * Guarda o aceite da divulgação proeminente.
 *
 * A política do Google exige, para apps que usam AccessibilityService sem serem
 * ferramentas de acessibilidade, que o usuário veja DENTRO do app o que é
 * acessado e dê um consentimento por ação afirmativa — antes de qualquer uso.
 * Enterrar isso na política de privacidade não vale.
 *
 * Guardamos a versão aceita: se um dia o texto mudar de forma relevante,
 * basta subir VERSAO_ATUAL para pedir o aceite de novo.
 */
object Consent {

    private const val PREFS = "autoclique_consent"
    private const val KEY_VERSAO_ACEITA = "versaoAceita"

    /** Suba este número sempre que o texto da divulgação mudar de sentido. */
    const val VERSAO_ATUAL = 1

    fun aceitou(ctx: Context): Boolean {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt(KEY_VERSAO_ACEITA, 0) >= VERSAO_ATUAL
    }

    fun registrarAceite(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_VERSAO_ACEITA, VERSAO_ATUAL)
            .apply()
    }

    fun revogar(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_VERSAO_ACEITA)
            .apply()
    }
}
