package it.fico.polso

import android.content.Intent
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
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun openHealthConnect() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (e: Exception) {
            // Connessione Salute non presente: niente da aprire
        }
    }
}
