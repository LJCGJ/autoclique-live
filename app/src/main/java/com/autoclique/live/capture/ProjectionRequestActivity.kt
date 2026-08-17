package com.autoclique.live.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Tela invisível cuja única função é pedir a permissão de captura de tela.
 * Pode ser chamada tanto pelo app quanto pelo overlay flutuante.
 */
class ProjectionRequestActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_AUTO_START = "autoStart"

        fun request(ctx: Context, autoStart: Boolean) {
            val i = Intent(ctx, ProjectionRequestActivity::class.java)
                .putExtra(EXTRA_AUTO_START, autoStart)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ctx.startActivity(i)
        }
    }

    private lateinit var launcher: ActivityResultLauncher<Intent>
    private var autoStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)

        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val svc = Intent(this, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_START)
                    .putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
                    .putExtra(CaptureService.EXTRA_AUTO_START, autoStart)
                ContextCompat.startForegroundService(this, svc)
            }
            finish()
            overridePendingTransition(0, 0)
        }

        val mgr = getSystemService(MediaProjectionManager::class.java)
        runCatching { launcher.launch(mgr.createScreenCaptureIntent()) }
            .onFailure { finish() }
    }
}
