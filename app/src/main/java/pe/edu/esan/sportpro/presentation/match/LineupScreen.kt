package pe.edu.esan.sportpro.presentation.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.LineupSlot
import pe.edu.esan.sportpro.data.model.PLAYER_POSITIONS
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SelectablePill
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.TextLink
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.presentation.components.rememberNow
import pe.edu.esan.sportpro.ui.theme.BordeSuave
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario

// US-006 · Alineación (RN-009): solo convocados CONFIRMADOS; titulares con su posición y suplentes,
// dentro de los máximos del equipo y con un portero. El DT la modifica hasta el inicio;
// el ADM (y el DT después del inicio) la ven en solo lectura.
@Composable
fun LineupScreen(viewModel: MatchViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val now by rememberNow()
    val match = state.match
    val editable = state.canManageMatch && match?.isOpen(now) == true

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Alineación")
        Spacer(Modifier.height(8.dp))
        if (match == null || !state.callupsLoaded || !state.lineupLoaded) {
            val error = state.loadError
            if (error != null) ErrorBox(error) else LoadingBox()
            return@SportProScreen
        }
        Subtitle("vs ${match.rival} · solo con los convocados que confirmaron.")
        Spacer(Modifier.height(16.dp))

        if (!editable) {
            ReadOnlyLineup(state)
            return@SportProScreen
        }

        // Contador en vivo: "Titulares a/X · Suplentes b/Y" (en rojo si se pasa del máximo)
        val starters = state.draftStarters.size
        val substitutes = state.draftSubstitutes.size
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Titulares $starters/${state.maxStarters}",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = if (starters > state.maxStarters) ErrorTexto else TextoPrincipal
            )
            Text("·", fontSize = 18.sp, color = TextoSecundario)
            Text(
                "Suplentes $substitutes/${state.maxSubstitutes}",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = if (substitutes > state.maxSubstitutes) ErrorTexto else TextoPrincipal
            )
        }
        Spacer(Modifier.height(12.dp))

        if (state.lineupDroppedNames.isNotEmpty()) {
            InfoBox {
                Text(
                    if (state.lineupDroppedNames.size == 1) "${state.lineupDroppedNames.first()} ya no está confirmado y saldrá de la alineación al guardar."
                    else "${state.lineupDroppedNames.joinToString(", ")} ya no están confirmados y saldrán de la alineación al guardar.",
                    color = TextoPrincipal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.confirmedCallups.isEmpty()) {
            Subtitle("Aún nadie confirmó. Cuando las familias respondan “Confirmo”, aparecerán aquí.")
            return@SportProScreen
        }

        SportProCard {
            state.confirmedCallups.forEachIndexed { index, callup ->
                if (index > 0) HorizontalDivider(color = BordeSuave)
                LineupRow(
                    name = state.playerName(callup.playerId, callup.playerName),
                    slot = state.lineupDraft[callup.playerId],
                    onRole = { viewModel.setLineupRole(callup.playerId, it) },
                    onPosition = { viewModel.setLineupPosition(callup.playerId, it) }
                )
            }
        }

        state.lineupErrors.forEach {
            Spacer(Modifier.height(10.dp))
            ErrorBox(it)
        }
        Messages(state.errorMessage, state.infoMessage)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Guardar alineación", onClick = { viewModel.saveLineup(onSaved) }, loading = state.isLoading)
        Spacer(Modifier.height(8.dp))
        Text(
            "Puedes cambiarla hasta el inicio del partido. Después, los cambios se registran como eventos del partido.",
            color = TextoSecundario,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// Un confirmado: Titular / Suplente / Fuera y, si es titular, su posición
@Composable
private fun LineupRow(
    name: String,
    slot: LineupSlot?,
    onRole: (Boolean?) -> Unit,
    onPosition: (String) -> Unit
) {
    var showPositions by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectablePill("Titular", selected = slot?.starter == true) { onRole(true) }
            SelectablePill("Suplente", selected = slot?.starter == false) { onRole(false) }
            SelectablePill("Fuera", selected = slot == null) { onRole(null) }
        }
        if (slot?.starter == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Posición:", color = TextoSecundario, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Box {
                    TextLink("${slot.position} ▾") { showPositions = true }
                    DropdownMenu(
                        expanded = showPositions,
                        onDismissRequest = { showPositions = false },
                        containerColor = Tarjeta
                    ) {
                        PLAYER_POSITIONS.forEach { position ->
                            DropdownMenuItem(
                                text = { Text(position, fontWeight = FontWeight.Bold, color = TextoPrincipal) },
                                onClick = {
                                    onPosition(position)
                                    showPositions = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Alineación guardada, en solo lectura
@Composable
private fun ReadOnlyLineup(state: MatchUiState) {
    val lineup = state.lineup
    if (lineup == null || (lineup.starters.isEmpty() && lineup.substitutes.isEmpty())) {
        Subtitle("Este partido no tiene alineación registrada.")
        return
    }
    // Quien cambió a "No puede" (o ya no figura como confirmado) después de guardar la alineación
    val confirmed = state.confirmedCallups.map { it.playerId }.toSet()
    SectionLabel("Titulares ${lineup.starters.size}")
    Spacer(Modifier.height(10.dp))
    SportProCard {
        lineup.starters.forEach {
            ReadOnlyLineupRow(state.playerName(it.playerId, it.playerName), it.position, stillConfirmed = it.playerId in confirmed)
        }
    }
    if (lineup.substitutes.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionLabel("Suplentes ${lineup.substitutes.size}")
        Spacer(Modifier.height(10.dp))
        SportProCard {
            lineup.substitutes.forEach {
                ReadOnlyLineupRow(state.playerName(it.playerId, it.playerName), null, stillConfirmed = it.playerId in confirmed)
            }
        }
    }
}

@Composable
private fun ReadOnlyLineupRow(name: String, position: String?, stillConfirmed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.ExtraBold, color = TextoPrincipal)
            if (!stillConfirmed) Text("Ya no está confirmado", color = ErrorTexto, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (position != null) Text(position, color = TextoSecundario)
    }
}
