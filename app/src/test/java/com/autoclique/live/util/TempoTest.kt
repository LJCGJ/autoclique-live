package com.autoclique.live.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tempo e logica pura, sem Android — da para testar de verdade.
 * O que mais importa aqui: aceitar virgula (teclado brasileiro), nunca aceitar
 * valores absurdos, e a ida e volta ms <-> segundos nao perder informacao.
 */
class TempoTest {

    @Test
    fun `aceita ponto e virgula como separador decimal`() {
        assertEquals(1500L, Tempo.segundosParaMs("1,5"))
        assertEquals(1500L, Tempo.segundosParaMs("1.5"))
        assertEquals(1000L, Tempo.segundosParaMs("1"))
        assertEquals(30000L, Tempo.segundosParaMs("30"))
        assertEquals(250L, Tempo.segundosParaMs("0,25"))
    }

    @Test
    fun `ignora espacos em volta`() {
        assertEquals(2000L, Tempo.segundosParaMs("  2  "))
    }

    @Test
    fun `recusa entrada invalida em vez de virar zero`() {
        assertNull(Tempo.segundosParaMs("abc"))
        assertNull(Tempo.segundosParaMs(""))
        assertNull(Tempo.segundosParaMs(null))
        assertNull(Tempo.segundosParaMs("0"))
        assertNull(Tempo.segundosParaMs("-3"))
    }

    @Test
    fun `nunca deixa o intervalo abaixo do minimo`() {
        // 1 ms travaria o aparelho; o piso e 50 ms.
        assertEquals(Tempo.MIN_MS, Tempo.segundosParaMs("0,001"))
        assertEquals(Tempo.MIN_MS, Tempo.segundosParaMs("0,05"))
    }

    @Test
    fun `mostra segundos sem zeros inuteis`() {
        assertEquals("1", Tempo.msParaSegundos(1000L))
        assertEquals("30", Tempo.msParaSegundos(30000L))
        assertEquals("1,5", Tempo.msParaSegundos(1500L))
        assertEquals("0,25", Tempo.msParaSegundos(250L))
        assertEquals("0,2", Tempo.msParaSegundos(200L))
        assertEquals("0,05", Tempo.msParaSegundos(50L))
    }

    @Test
    fun `ida e volta preserva o valor`() {
        for (ms in listOf(50L, 200L, 250L, 500L, 1000L, 1500L, 2500L, 30000L)) {
            val texto = Tempo.msParaSegundos(ms)
            assertEquals("falhou em $ms ms", ms, Tempo.segundosParaMs(texto))
        }
    }

    @Test
    fun `taxa de cliques por segundo`() {
        assertEquals("1 clique por segundo", Tempo.cliquesPorSegundo(1000L))
        assertEquals("2 cliques por segundo", Tempo.cliquesPorSegundo(500L))
        assertEquals("4 cliques por segundo", Tempo.cliquesPorSegundo(250L))
        assertEquals("20 cliques por segundo", Tempo.cliquesPorSegundo(50L))
        // Meio segundo e meio: 1 / 1,5 = 0,67
        assertEquals("0,67 cliques por segundo", Tempo.cliquesPorSegundo(1500L))
        // Um clique a cada 30 s e bem menos de um por segundo
        assertEquals("0,03 cliques por segundo", Tempo.cliquesPorSegundo(30000L))
    }

    @Test
    fun `resumo da lista junta intervalo e taxa`() {
        assertEquals("a cada 1 s  -  1/s", Tempo.resumo(1000L))
        assertEquals("a cada 0,25 s  -  4/s", Tempo.resumo(250L))
    }
}
