package com.autoclique.live.util

import java.util.Locale

/**
 * Conversões entre o que o usuário lê (segundos) e o que o motor usa
 * (milissegundos), mais o cálculo de cliques por segundo.
 *
 * Por dentro tudo continua em ms: é a unidade dos relógios do Android e evita
 * erro de arredondamento acumulado. A troca para segundos é só na tela.
 */
object Tempo {

    private val BR = Locale("pt", "BR")

    /** Intervalo mínimo aceito: 50 ms, ou seja 20 cliques por segundo. */
    const val MIN_MS = 50L

    /** "1,5" ou "1.5" -> 1500 ms. Devolve null se não for um número válido. */
    fun segundosParaMs(texto: String?): Long? {
        val limpo = texto?.trim()?.replace(',', '.') ?: return null
        val segundos = limpo.toDoubleOrNull() ?: return null
        if (segundos <= 0.0) return null
        return (segundos * 1000.0).toLong().coerceAtLeast(MIN_MS)
    }

    /** 1500 ms -> "1,5" (sem zeros à toa: 1000 vira "1"). */
    fun msParaSegundos(ms: Long): String {
        val segundos = ms / 1000.0
        return if (segundos == segundos.toLong().toDouble()) {
            segundos.toLong().toString()
        } else {
            String.format(BR, "%.2f", segundos).trimEnd('0').trimEnd(',')
        }
    }

    /** "a cada 1,5 s" -> "0,67 clique/s". Texto pronto para exibir. */
    fun cliquesPorSegundo(ms: Long): String {
        if (ms <= 0L) return ""
        val taxa = 1000.0 / ms
        val numero = formatarTaxa(taxa)
        val unidade = if (taxa == 1.0) "clique" else "cliques"
        return "$numero $unidade por segundo"
    }

    /** Versão curta para a lista: "a cada 1,5 s  -  0,67/s". */
    fun resumo(ms: Long): String {
        val taxa = if (ms > 0) 1000.0 / ms else 0.0
        return "a cada ${msParaSegundos(ms)} s  -  ${formatarTaxa(taxa)}/s"
    }

    /** Sem casa decimal quando é número redondo: "1", "2", "1,5", "0,67". */
    private fun formatarTaxa(taxa: Double): String = when {
        taxa >= 10 -> String.format(BR, "%.0f", taxa)
        taxa >= 1 ->
            if (taxa == Math.floor(taxa)) String.format(BR, "%.0f", taxa)
            else String.format(BR, "%.1f", taxa)
        else -> String.format(BR, "%.2f", taxa)
    }
}
