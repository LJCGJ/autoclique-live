package com.autoclique.live.data

import android.content.Context
import android.content.SharedPreferences
import com.autoclique.live.model.Bot
import com.autoclique.live.model.ClickPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Guarda os bots e seus pontos em SharedPreferences (JSON). Nada sai do aparelho.
 *
 * Um "bot" e apenas um conjunto nomeado de pontos. So o bot selecionado roda
 * quando o clicador e ligado.
 */
object PointStore {

    private const val PREFS = "autoclique_prefs"
    private const val KEY_POINTS = "points"
    private const val KEY_BOTS = "bots"
    private const val KEY_BOT_ATIVO = "botAtivo"

    private var prefs: SharedPreferences? = null

    private val _points = MutableStateFlow<List<ClickPoint>>(emptyList())
    val points: StateFlow<List<ClickPoint>> = _points.asStateFlow()

    private val _bots = MutableStateFlow<List<Bot>>(emptyList())
    val bots: StateFlow<List<Bot>> = _bots.asStateFlow()

    private val _botAtivo = MutableStateFlow("")
    val botAtivo: StateFlow<String> = _botAtivo.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        _points.value = lerPontos()
        _bots.value = lerBots()
        _botAtivo.value = prefs?.getString(KEY_BOT_ATIVO, "").orEmpty()

        migrar()
    }

    /**
     * Garante que sempre exista pelo menos um bot e que nenhum ponto fique
     * orfao. Cobre tanto a instalacao nova quanto quem ja tinha pontos salvos
     * de antes dos bots existirem.
     */
    private fun migrar() {
        var bots = _bots.value
        var pontos = _points.value
        var mudou = false

        if (bots.isEmpty()) {
            bots = listOf(Bot(name = "Meu primeiro bot"))
            mudou = true
        }

        val idsValidos = bots.map { it.id }.toSet()
        val padrao = bots.first().id
        if (pontos.any { it.botId.isBlank() || it.botId !in idsValidos }) {
            pontos = pontos.map {
                if (it.botId.isBlank() || it.botId !in idsValidos) it.copy(botId = padrao) else it
            }
            mudou = true
        }

        if (_botAtivo.value.isBlank() || _botAtivo.value !in idsValidos) {
            _botAtivo.value = padrao
            prefs?.edit()?.putString(KEY_BOT_ATIVO, padrao)?.apply()
            mudou = true
        }

        if (mudou) {
            _bots.value = bots
            _points.value = pontos
            gravarBots(bots)
            gravarPontos(pontos)
        }
    }

    // ------------------------------------------------------------- leitura

    private fun lerPontos(): List<ClickPoint> {
        val raw = prefs?.getString(KEY_POINTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ClickPoint.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun lerBots(): List<Bot> {
        val raw = prefs?.getString(KEY_BOTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Bot.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun gravarPontos(lista: List<ClickPoint>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString(KEY_POINTS, arr.toString())?.apply()
    }

    private fun gravarBots(lista: List<Bot>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString(KEY_BOTS, arr.toString())?.apply()
    }

    // ---------------------------------------------------------------- bots

    @Synchronized
    fun criarBot(nome: String): Bot {
        val bot = Bot(name = nome.ifBlank { "Novo bot" })
        val lista = _bots.value + bot
        _bots.value = lista
        gravarBots(lista)
        selecionarBot(bot.id)
        return bot
    }

    @Synchronized
    fun renomearBot(id: String, nome: String) {
        if (nome.isBlank()) return
        val lista = _bots.value.map { if (it.id == id) it.copy(name = nome) else it }
        _bots.value = lista
        gravarBots(lista)
    }

    /** Remove o bot e todos os pontos dele. Nunca deixa a lista vazia. */
    @Synchronized
    fun excluirBot(id: String) {
        val restantes = _bots.value.filterNot { it.id == id }
        val lista = restantes.ifEmpty { listOf(Bot(name = "Meu primeiro bot")) }
        _bots.value = lista
        gravarBots(lista)

        val pontos = _points.value.filterNot { it.botId == id }
        _points.value = pontos
        gravarPontos(pontos)

        if (_botAtivo.value == id) selecionarBot(lista.first().id)
    }

    @Synchronized
    fun selecionarBot(id: String) {
        _botAtivo.value = id
        prefs?.edit()?.putString(KEY_BOT_ATIVO, id)?.apply()
    }

    fun botAtual(): Bot? = _bots.value.firstOrNull { it.id == _botAtivo.value }

    fun nomeDoBot(id: String): String = _bots.value.firstOrNull { it.id == id }?.name ?: ""

    // -------------------------------------------------------------- pontos

    /** Pontos do bot indicado (por padrao, o ativo). */
    fun pontosDoBot(botId: String = _botAtivo.value): List<ClickPoint> =
        _points.value.filter { it.botId == botId }

    /** Pontos ligados do bot ativo — e exatamente o que o motor executa. */
    fun pontosAtivos(): List<ClickPoint> =
        _points.value.filter { it.enabled && it.botId == _botAtivo.value }

    @Synchronized
    fun upsert(point: ClickPoint) {
        // Ponto novo sem bot definido entra no bot selecionado.
        val p = if (point.botId.isBlank()) point.copy(botId = _botAtivo.value) else point
        val lista = _points.value.toMutableList()
        val idx = lista.indexOfFirst { it.id == p.id }
        if (idx >= 0) lista[idx] = p else lista.add(p)
        _points.value = lista
        gravarPontos(lista)
    }

    @Synchronized
    fun remove(id: String) {
        val lista = _points.value.filterNot { it.id == id }
        _points.value = lista
        gravarPontos(lista)
    }

    fun find(id: String?): ClickPoint? = _points.value.firstOrNull { it.id == id }

    fun suggestName(): String = "Ponto ${pontosDoBot().size + 1}"
}
