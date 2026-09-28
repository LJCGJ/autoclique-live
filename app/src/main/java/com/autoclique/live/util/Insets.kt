package com.autoclique.live.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge: a partir do targetSdk 35 o Android desenha o app por baixo
 * da barra de status e da barra de navegação. Sem tratar os insets, o botão
 * de baixo fica escondido atrás da barra de navegação (relato dos testadores)
 * e o cabeçalho encosta na barra de status.
 *
 * [root] recebe o padding lateral e inferior (barra de navegação / gestos);
 * [header] recebe o padding superior (barra de status / recorte da câmera),
 * somado ao padding que ele já tinha no XML.
 */
object Insets {

    fun apply(root: View, header: View) {
        val headerTop = header.paddingTop
        val rootBottom = root.paddingBottom
        val rootLeft = root.paddingLeft
        val rootRight = root.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            root.setPadding(rootLeft + bars.left, root.paddingTop, rootRight + bars.right, rootBottom + bars.bottom)
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
