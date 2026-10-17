package pe.edu.esan.sportpro.presentation.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.PlayerStatus
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-003 · "Mis jugadores" del apoderado: sus hijos con su estado y "Vincular otro jugador"
@Composable
fun MyPlayersScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenPlayer: (Player) -> Unit,
    onLinkPlayer: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Mis jugadores")
        Spacer(Modifier.height(8.dp))
        Subtitle("Aquí ves a los jugadores que representas. Toca uno para completar o actualizar sus datos.")

        state.loadError?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }
        PlayerMessages(state)
        Spacer(Modifier.height(20.dp))

        if (!state.myPlayersLoaded) {
            if (state.loadError == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Turquesa)
                }
            }
        } else {
            if (state.myPlayers.isEmpty()) {
                Subtitle("Aún no tienes jugadores vinculados. Pide al DT un código de apoderado y vincúlalo aquí.")
                Spacer(Modifier.height(16.dp))
            }
            state.sortedMyPlayers.forEach { player ->
                val detail = listOf(
                    state.teamName(player.teamId),
                    // RN-005: qué le falta para dejar de estar pendiente
                    if (player.status == PlayerStatus.PENDING_GUARDIAN && !player.hasEmergencyContact) "Falta su contacto de emergencia" else ""
                ).filter { it.isNotBlank() }.joinToString(" · ")
                PlayerRow(player, state.photos[player.id].orEmpty(), detail = detail) { onOpenPlayer(player) }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

        SecondaryButton("Vincular otro jugador", onLinkPlayer)
    }
}
