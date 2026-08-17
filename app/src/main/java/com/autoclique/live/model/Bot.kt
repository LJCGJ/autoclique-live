package com.autoclique.live.model

import org.json.JSONObject
import java.util.UUID

/**
 * Um conjunto nomeado de pontos de clique — por exemplo "Shopee moedas" ou
 * "Recompensa da live". Só o bot selecionado roda quando você aperta Iniciar,
 * então dá para ter vários configurados sem que um atrapalhe o outro.
 */
data class Bot(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Novo bot"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
    }

    companion object {
        fun fromJson(o: JSONObject): Bot = Bot(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Novo bot")
        )
    }
}
