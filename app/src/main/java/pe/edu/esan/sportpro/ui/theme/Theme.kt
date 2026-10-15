package pe.edu.esan.sportpro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// SportPro usa siempre el tema claro "Turquesa familiar" (sin colores dinámicos)
private val SportProColors = lightColorScheme(
    primary = Turquesa,
    onPrimary = Tarjeta,
    primaryContainer = SuperficieSuave,
    onPrimaryContainer = TextoSobreSuave,
    secondary = Coral,
    onSecondary = Tarjeta,
    secondaryContainer = CoralSuave,
    onSecondaryContainer = CoralTexto,
    background = Fondo,
    onBackground = TextoPrincipal,
    surface = Tarjeta,
    onSurface = TextoPrincipal,
    surfaceVariant = Fondo,
    onSurfaceVariant = TextoSecundario,
    outline = Borde,
    outlineVariant = BordeSuave,
    error = ErrorBorde,
    onError = Tarjeta
)

@Composable
fun SportProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SportProColors,
        typography = Typography,
        content = content
    )
}
