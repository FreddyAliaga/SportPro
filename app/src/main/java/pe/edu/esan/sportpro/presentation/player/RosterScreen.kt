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
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-003 · Plantel de un equipo (DT: su equipo; ADM: el equipo que tocó en "Academia y equipos").
// Muestra a cada jugador con su foto o iniciales, dorsal, posición y estado (RN-005).
@Composable
fun RosterScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNewPlayer: () -> Unit,
    onOpenPlayer: (Player) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val team = state.rosterTeam

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Plantel")
        Spacer(Modifier.height(8.dp))

        // Un DT sin equipo (por ejemplo, se desactivó su equipo en la US-002)
        if (state.rosterTeamId.isEmpty()) {
            Subtitle("Aún no tienes un equipo asignado. Pide al administrador de tu academia que te asigne uno.")
            return@SportProScreen
        }

        if (team != null) {
            Subtitle(
                if (team.active) "${team.name} · ${team.category}"
                else "${team.name} · equipo desactivado (su historial se conserva)"
            )
        }
        state.loadError?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }
        PlayerMessages(state)

        if (!state.rosterLoaded) {
            if (state.loadError == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Turquesa)
                }
            }
            return@SportProScreen
        }

        Spacer(Modifier.height(20.dp))
        if (team?.active == true) {
            PrimaryButton("+ Nuevo jugador", onNewPlayer)
            Spacer(Modifier.height(20.dp))
        }

        val active = state.activeRoster
        if (active.isEmpty()) {
            Subtitle("Aún no hay jugadores en este equipo. Agrega al primero con su fecha de nacimiento.")
        } else {
            val pending = state.pendingCount
            SectionLabel(
                "${active.size} ${if (active.size == 1) "jugador" else "jugadores"}" +
                    if (pending > 0) " · $pending pendiente${if (pending == 1) "" else "s"} de apoderado" else ""
            )
            Spacer(Modifier.height(10.dp))
            active.forEach { player ->
                PlayerRow(player, state.photos[player.id].orEmpty()) { onOpenPlayer(player) }
                Spacer(Modifier.height(10.dp))
            }
        }

        if (state.inactiveRoster.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionLabel("Inactivos")
            Spacer(Modifier.height(10.dp))
            state.inactiveRoster.forEach { player ->
                PlayerRow(player, state.photos[player.id].orEmpty()) { onOpenPlayer(player) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
