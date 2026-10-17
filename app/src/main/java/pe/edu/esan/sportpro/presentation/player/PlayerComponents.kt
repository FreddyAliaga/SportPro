package pe.edu.esan.sportpro.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.PlayerStatus
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.PlayerAvatar
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.ui.theme.BordeSuave
import pe.edu.esan.sportpro.ui.theme.CoralSuave
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TurquesaOscuro

// US-003 · Piezas comunes de las pantallas del jugador

// Estado del jugador (RN-005): Activo, Pendiente de apoderado o Inactivo
@Composable
fun PlayerStatusChip(status: PlayerStatus) {
    val (background, text) = when (status) {
        PlayerStatus.ACTIVE -> SuperficieSuave to TurquesaOscuro
        PlayerStatus.PENDING_GUARDIAN -> CoralSuave to ErrorTexto
        PlayerStatus.INACTIVE -> BordeSuave to TextoSecundario
    }
    StatusChip(status.label, background, text)
}

// "#10 · Delantero · Sin cuenta"
fun playerSummary(player: Player): String = listOfNotNull(
    player.dorsal?.let { "#$it" },
    player.positionMain.ifBlank { null },
    if (!player.isMinor && !player.hasAccount && player.active) "Sin cuenta" else null
).joinToString(" · ")

// Fila de un jugador en el plantel o en "Mis jugadores"
@Composable
fun PlayerRow(player: Player, photoBase64: String, detail: String = playerSummary(player), onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (player.active) 1f else 0.6f)
            .clip(RoundedCornerShape(22.dp))
            .background(Tarjeta)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        PlayerAvatar(photoBase64, player.initials, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
            if (detail.isNotBlank()) Text(detail, color = TextoSecundario, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        PlayerStatusChip(player.status)
    }
}

// Una fila "Etiqueta ...... valor" dentro de una tarjeta
@Composable
fun DataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = TextoSecundario, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1.3f))
    }
}

// Mensajes de éxito o error de la última acción
@Composable
fun PlayerMessages(state: PlayerUiState) {
    state.errorMessage?.let {
        Spacer(Modifier.height(12.dp))
        ErrorBox(it)
    }
    state.infoMessage?.let {
        Spacer(Modifier.height(12.dp))
        InfoBox { Text(it, color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}
