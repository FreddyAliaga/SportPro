package pe.edu.esan.sportpro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.OutlinedFlag
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sports
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.data.model.EVENT_ICONS
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-007 · Íconos y colores del catálogo de eventos (también los usará el registro del partido, US-008)

// Dibujo de cada clave de EVENT_ICONS (material-icons-extended). Una clave desconocida muestra el balón.
fun eventIconVector(key: String): ImageVector = when (key) {
    "balon" -> Icons.Outlined.SportsSoccer
    "inicio" -> Icons.Outlined.PlayArrow
    "fin" -> Icons.Outlined.SportsScore
    "tarjeta" -> Icons.Outlined.CropPortrait
    "cambio" -> Icons.Outlined.SwapHoriz
    "silbato" -> Icons.Outlined.Sports
    "penal" -> Icons.Outlined.GpsFixed
    "prohibido" -> Icons.Outlined.Block
    "banderin" -> Icons.Outlined.OutlinedFlag
    "mano" -> Icons.Outlined.PanTool
    "escudo" -> Icons.Outlined.Shield
    "reloj" -> Icons.Outlined.AccessTime
    "advertencia" -> Icons.Outlined.WarningAmber
    "estrella" -> Icons.Outlined.Star
    else -> Icons.Outlined.SportsSoccer
}

// Texto para TalkBack de cada ícono
fun eventIconLabel(key: String): String = EVENT_ICONS.firstOrNull { it.key == key }?.label ?: "Evento"

// "#0E6B6F" -> Color. Un valor inválido usa el turquesa de SportPro.
fun eventColor(hex: String): Color =
    try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        Turquesa
    }

// Insignia del tipo de evento: fondo de su color y el ícono en blanco
// (sobre colores claros, como el amarillo, el ícono va oscuro para que se vea bien)
@Composable
fun EventTypeBadge(iconKey: String, colorHex: String, size: Dp = 44.dp, contentDescription: String? = null) {
    val background = eventColor(colorHex)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            eventIconVector(iconKey),
            contentDescription = contentDescription,
            tint = if (background.luminance() > 0.5f) TextoPrincipal else Tarjeta,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
