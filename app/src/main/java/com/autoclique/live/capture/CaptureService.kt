package com.autoclique.live.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.autoclique.live.R
import com.autoclique.live.engine.AutoClicker
import com.autoclique.live.ui.MainActivity
import com.autoclique.live.util.Notifs

/**
 * Serviço em primeiro plano exigido pelo Android para usar o MediaProjection.
 * Ele só existe para segurar a permissão de leitura de tela enquanto o
 * gatilho por cor estiver em uso.
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_START = "com.autoclique.live.CAPTURE_START"
        const val ACTION_STOP = "com.autoclique.live.CAPTURE_STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_AUTO_START = "autoStart"

        fun stop(ctx: Context) {
            runCatching {
                ctx.startService(Intent(ctx, CaptureService::class.java).setAction(ACTION_STOP))
            }.onFailure { ScreenCapture.stop() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifs.ensureChannels(this)

        if (intent?.action == ACTION_STOP) {
            ScreenCapture.stop()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        // Sem consentimento válido não podemos nem subir como foreground service do
        // tipo mediaProjection (o Android 14 recusa). Acontece quando o sistema
        // recria o serviço depois de matar o processo — o token não é reaproveitável.
        if (code == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Ordem obrigatória no Android 14: foreground primeiro, projeção depois.
        // Pode lançar se o app caiu para segundo plano no meio do caminho.
        val foregroundOk = runCatching { startAsForeground() }.isSuccess
        if (!foregroundOk) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Se o usuário encerrar o compartilhamento pela barra do sistema,
        // este serviço precisa sair junto — senão a notificação fica presa.
        ScreenCapture.onSessionEnded = { runCatching { stopSelf() } }

        val mgr = getSystemService(MediaProjectionManager::class.java)
        val projection = runCatching { mgr.getMediaProjection(code, data) }.getOrNull()
        if (projection == null) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val ok = ScreenCapture.start(this, projection)
        if (ok && intent?.getBooleanExtra(EXTRA_AUTO_START, false) == true) {
            AutoClicker.start(this)
        }
        // O token de captura é de uso único: recriar o serviço sozinho não funcionaria.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // A tela girou: o VirtualDisplay precisa do novo tamanho e o clicador
        // precisa reconferir as dimensões antes do próximo toque.
        AutoClicker.invalidateScreenSize()
        ScreenCapture.refreshSize(this)
    }

    override fun onDestroy() {
        ScreenCapture.stop()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, Notifs.CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_stat_click)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Lendo a cor da tela para o gatilho de cor")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, Notifs.ID_CAPTURE, n, type)
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
}
