package it.fico.polso

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController

class MainActivity : ComponentActivity() {

    private val vm: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            PolsoTheme {
                val launcher = rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract()
                ) { vm.refresh() }

                DashboardScreen(
                    vm = vm,
                    onRequestPermissions = { launcher.launch(HealthRepo.PERMISSIONS) },
                    onRequestHistory = { launcher.launch(setOf(HealthRepo.HISTORY_PERMISSION)) },
                    onOpenHealthConnect = { openHealthConnect() },
                    onExport = { exportData() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun exportData() {
        lifecycleScope.launch {
            val file = vm.buildExport()
            if (file == null) {
                Toast.makeText(this@MainActivity, "Esportazione non riuscita", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Dati Polso ${file.name}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Ecco i miei dati di Polso: analizzali e dammi una valutazione.",
                )
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Invia i dati per l'analisi"))
        }
    }

    private fun openHealthConnect() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (e: Exception) {
            // Connessione Salute non presente: niente da aprire
        }
    }
}
