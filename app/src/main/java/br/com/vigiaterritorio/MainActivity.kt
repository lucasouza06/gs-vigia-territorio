package br.com.vigiaterritorio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import br.com.vigiaterritorio.ui.navigation.VigiaNavHost
import br.com.vigiaterritorio.ui.theme.VigiaTheme

class MainActivity : ComponentActivity() {

    /** Território vindo do toque numa notificação: abre direto o detalhe do alerta. */
    private val pendingTerritory = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingTerritory.value = intent.getStringExtra(EXTRA_TERRITORY_ID)
        setContent {
            VigiaTheme {
                VigiaNavHost(
                    openAlertFor = pendingTerritory.value,
                    onAlertOpened = { pendingTerritory.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingTerritory.value = intent.getStringExtra(EXTRA_TERRITORY_ID)
    }

    companion object {
        const val EXTRA_TERRITORY_ID = "territory_id"
    }
}
