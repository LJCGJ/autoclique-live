package com.autoclique.live.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.autoclique.live.service.ClickAccessibilityService

object Perms {

    /**
     * O sistema grava a lista com flattenToShortString() ("pacote/.Classe"),
     * então comparar strings direto falha. unflattenFromString aceita as duas formas.
     */
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        if (ClickAccessibilityService.isRunning()) return true
        val expected = ComponentName(ctx, ClickAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { entry ->
            val cn = ComponentName.unflattenFromString(entry.trim())
            cn != null &&
                cn.packageName == expected.packageName &&
                cn.className == expected.className
        }
    }

    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun hasNotificationPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlaySettingsIntent(ctx: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
