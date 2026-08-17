package com.autoclique.live.data

import android.content.Context
import android.content.SharedPreferences
import com.autoclique.live.model.ClickPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Guarda a lista de pontos em SharedPreferences (JSON). Nada sai do aparelho.
 */
object PointStore {

    private const val PREFS = "autoclique_prefs"
    private const val KEY_POINTS = "points"

    private var prefs: SharedPreferences? = null

    private val _points = MutableStateFlow<List<ClickPoint>>(emptyList())
    val points: StateFlow<List<ClickPoint>> = _points.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _points.value = readFromDisk()
    }

    private fun readFromDisk(): List<ClickPoint> {
        val raw = prefs?.getString(KEY_POINTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ClickPoint.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun writeToDisk(list: List<ClickPoint>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs?.edit()?.putString(KEY_POINTS, arr.toString())?.apply()
    }

    @Synchronized
    fun upsert(point: ClickPoint) {
        val list = _points.value.toMutableList()
        val idx = list.indexOfFirst { it.id == point.id }
        if (idx >= 0) list[idx] = point else list.add(point)
        _points.value = list
        writeToDisk(list)
    }

    @Synchronized
    fun remove(id: String) {
        val list = _points.value.filterNot { it.id == id }
        _points.value = list
        writeToDisk(list)
    }

    fun find(id: String?): ClickPoint? = _points.value.firstOrNull { it.id == id }

    fun suggestName(): String = "Ponto ${_points.value.size + 1}"
}
