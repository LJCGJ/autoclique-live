package com.autoclique.live.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.autoclique.live.R

object Notifs {
    const val CHANNEL_OVERLAY = "autoclique_overlay"
    const val CHANNEL_CAPTURE = "autoclique_capture"

    const val ID_OVERLAY = 1001
    const val ID_CAPTURE = 1002

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_OVERLAY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_OVERLAY,
                    ctx.getString(R.string.channel_overlay_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_CAPTURE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CAPTURE,
                    ctx.getString(R.string.channel_capture_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply { setShowBadge(false) }
            )
        }
    }
}
