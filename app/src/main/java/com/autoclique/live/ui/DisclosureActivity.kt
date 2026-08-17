package com.autoclique.live.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autoclique.live.R
import com.autoclique.live.databinding.ActivityDisclosureBinding
import com.autoclique.live.util.Consent

/**
 * Divulgação proeminente exigida pela política de uso da AccessibilityService.
 *
 * Requisitos que esta tela cumpre:
 *  - aparece dentro do app, no uso normal, ANTES de qualquer permissão;
 *  - descreve o que é acessado, para que serve e se sai do aparelho;
 *  - o aceite e uma ação afirmativa (botão), nunca um "ao continuar voce aceita";
 *  - recusar e uma saída legítima, que encerra o app.
 */
class DisclosureActivity : AppCompatActivity() {

    private lateinit var b: ActivityDisclosureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDisclosureBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnPolitica.setOnClickListener {
            val url = getString(R.string.privacy_policy_url)
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure {
                Toast.makeText(this, url, Toast.LENGTH_LONG).show()
            }
        }

        b.btnRecusar.setOnClickListener {
            // Recusar precisa ser uma opção real: sem consentimento o app nao opera.
            finishAffinity()
        }

        b.btnAceitar.setOnClickListener {
            Consent.registrarAceite(this)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
    }
}
