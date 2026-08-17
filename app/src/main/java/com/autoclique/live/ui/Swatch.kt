package com.autoclique.live.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

/** Preenche uma View redonda com a cor indicada (ou vazia se null). */
fun View.paintSwatch(color: Int?) {
    val density = resources.displayMetrics.density
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color ?: Color.TRANSPARENT)
        setStroke((2 * density).toInt(), 0x88FFFFFF.toInt())
    }
}

fun hex(color: Int): String = "#%06X".format(color and 0xFFFFFF)
