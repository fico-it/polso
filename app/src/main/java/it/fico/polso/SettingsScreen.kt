package it.fico.polso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: DashboardViewModel,
    onRequestPermissions: () -> Unit,
    onRequestHistory: () -> Unit,
) {
    val s = vm.settings
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.showSettings = false }) {
                Text("‹ Cruscotto", color = Palette.Steps, fontSize = 15.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("Personalizza", fontSize = 22.sp, fontWeight = FontWeight.Black)
        }

        Section("Obiettivo passi giornaliero") {
            var local by remember { mutableStateOf(s.stepGoal.toFloat()) }
            Text(
                fmtInt(local.roundToInt().toLong()) + " passi",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Palette.Steps,
            )
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { s.setGoal((local / 500f).roundToInt() * 500) },
                valueRange = 2000f..20000f,
                steps = 35,
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Steps,
                    activeTrackColor = Palette.Steps,
                    inactiveTrackColor = Palette.Track,
                ),
            )
            Text(
                "Compare come anello nella scheda Passi e come linea tratteggiata nei grafici dei passi.",
                fontSize = 12.sp,
                color = Palette.Muted,
            )
        }

        Section("Schede del cruscotto") {
            Text(
                "Accendi o spegni le schede e cambiane l'ordine con le frecce.",
                fontSize = 12.sp,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(8.dp))
            s.order.forEachIndexed { i, card ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        card.label,
                        fontSize = 14.sp,
                        color = if (s.isVisible(card)) Palette.Text else Palette.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { s.move(card, -1) }, enabled = i > 0) {
                        Text("▲", color = if (i > 0) Palette.Text else Palette.Grid, fontSize = 13.sp)
                    }
                    TextButton(onClick = { s.move(card, 1) }, enabled = i < s.order.size - 1) {
                        Text("▼", color = if (i < s.order.size - 1) Palette.Text else Palette.Grid, fontSize = 13.sp)
                    }
                    Switch(
                        checked = s.isVisible(card),
                        onCheckedChange = { s.toggle(card) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Palette.Steps,
                            uncheckedThumbColor = Palette.Muted,
                            uncheckedTrackColor = Palette.Track,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { s.resetLayout() }, modifier = Modifier.fillMaxWidth()) {
                Text("Ripristina ordine predefinito", color = Palette.Text)
            }
        }

        Section("Dati storici") {
            if (vm.hasHistoryPermission) {
                Text("Accesso allo storico completo attivo.", color = Palette.Steps, fontSize = 14.sp)
            } else {
                Text(
                    "Senza questo permesso Connessione Salute mostra solo i dati dei 30 giorni precedenti " +
                        "al primo consenso. Attivalo per vedere mesi e anni interi.",
                    fontSize = 13.sp,
                    color = Palette.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onRequestHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Steps, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Abilita lo storico completo") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Lo storico mostra solo ciò che Health Sync ha copiato in Connessione Salute: " +
                    "per i mesi passati usa in Health Sync la sincronizzazione dei giorni precedenti.",
                fontSize = 12.sp,
                color = Palette.Muted,
            )
        }

        Section("Permessi") {
            OutlinedButton(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.missingPermissions) "Concedi i permessi mancanti" else "Rivedi i permessi", color = Palette.Text)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Card)
            .padding(18.dp)
    ) {
        Text(title.uppercase(), fontSize = 12.sp, color = Palette.Steps, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
