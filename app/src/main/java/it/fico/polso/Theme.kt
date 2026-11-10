package it.fico.polso

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF0B0E13)
    val Card = Color(0xFF161A21)
    val CardHi = Color(0xFF1C222B)
    val Track = Color(0xFF262C36)
    val Grid = Color(0xFF2C333E)
    val GridHi = Color(0xFF46505F)
    val Text = Color(0xFFF2F4F7)
    val Muted = Color(0xFF8A93A3)

    // un colore per ogni statistica
    val Steps = Color(0xFFB8F24A)    // chartreuse
    val Distance = Color(0xFF5AC8FA) // azzurro
    val Kcal = Color(0xFFFF9F43)     // arancio
    val Heart = Color(0xFFFF5C6C)    // rosso
    val Resting = Color(0xFFFF8FC7)  // rosa
    val Hrv = Color(0xFFFFD166)      // giallo
    val Sleep = Color(0xFFA78BFA)    // viola
    val Spo2 = Color(0xFF34D399)     // verde acqua
    val Breath = Color(0xFF60A5FA)   // blu
    val Workout = Color(0xFFFF7A59)  // corallo

    val Awake = Color(0xFFFFB454)
    val Rem = Color(0xFFC4A1FF)
    val Light = Color(0xFF7FA8FF)
    val Deep = Color(0xFF4A55C9)
}

@Composable
fun PolsoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Steps,
            onPrimary = Color.Black,
            background = Palette.Bg,
            surface = Palette.Card,
            onSurface = Palette.Text,
            onBackground = Palette.Text,
            surfaceVariant = Palette.CardHi,
            onSurfaceVariant = Palette.Muted,
        ),
        content = content,
    )
}
