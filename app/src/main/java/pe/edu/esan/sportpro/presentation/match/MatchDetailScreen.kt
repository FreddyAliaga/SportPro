package pe.edu.esan.sportpro.presentation.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import pe.edu.esan.sportpro.data.model.Callup
import pe.edu.esan.sportpro.data.model.CallupResponse
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.toDeadlineText
import pe.edu.esan.sportpro.data.model.toMatchDateText
import pe.edu.esan.sportpro.data.model.toMatchTimeText
import pe.edu.esan.sportpro.presentation.components.CheckRow
import pe.edu.esan.sportpro.presentation.components.ConfirmDialog
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProDialog
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.presentation.components.rememberNow
import pe.edu.esan.sportpro.ui.theme.BordeSuave
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario

// US-006 · Detalle del partido: datos, estado y convocatoria.
// El DT de su equipo convoca y ve las respuestas en tiempo real (con el motivo de "No puedo");
// el ADM lo ve en solo lectura y sin motivos.
@Composable
fun MatchDetailScreen(
    viewModel: MatchViewModel,
    onBack: () -> Unit,
    onLineup: () -> Unit,
    onReschedule: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val now by rememberNow()
    val match = state.match
    var showCancel by remember { mutableStateOf(false) }

    if (showCancel && match != null) {
        ConfirmDialog(
            title = "¿Cancelar el partido contra ${match.rival}?",
            text = "El partido queda como Cancelado y se avisa a los convocados. No se borra nada: " +
                "la convocatoria, las respuestas y la alineación se conservan. Esta acción no se puede deshacer.",
            confirmText = "Cancelar partido",
            onDismiss = { showCancel = false },
            onConfirm = {
                showCancel = false
                viewModel.cancelMatch()
            }
        )
    }

    if (state.confirmShortNotice) {
        SportProDialog(
            title = "¿Publicar igual?",
            confirmText = "Publicar igual",
            onConfirm = { viewModel.publishCallup(confirmed = true) },
            onDismiss = viewModel::dismissShortNotice
        ) {
            Text(
                "Falta menos de ${state.noticeHours} h para el partido. Las familias tendrán poco tiempo para responder.",
                color = TextoPrincipal,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))

        if (match == null) {
            val error = state.loadErrors["partido"]
            if (error != null) ErrorBox(error) else LoadingBox()
            return@SportProScreen
        }

        Title("vs ${match.rival}")
        Spacer(Modifier.height(8.dp))
        MatchStatusChip(match.status)
        Spacer(Modifier.height(16.dp))
        MatchInfoCard(match, state.confirmHours)

        state.loadError?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }
        Messages(state.errorMessage, state.infoMessage)
        // Avisos que no se pudieron enviar (el reintento no duplica: cada aviso tiene un ID fijo)
        if (state.canManageMatch && state.pendingNotice != null) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Reintentar avisos", viewModel::retryNotices, enabled = !state.isLoading)
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Convocatoria")
        Spacer(Modifier.height(10.dp))
        if (!state.callupsLoaded) {
            if (state.loadErrors["convocados"] == null) LoadingBox()
            return@SportProScreen
        }

        // Resumen en tiempo real y detalle por jugador
        if (state.callups.isNotEmpty()) {
            CallupCounters(state.confirmedCount, state.declinedCount, state.pendingCount)
            Spacer(Modifier.height(12.dp))
            SportProCard {
                state.sortedCallups.forEachIndexed { index, callup ->
                    if (index > 0) HorizontalDivider(color = BordeSuave)
                    // Quién puede responder según el plantel ACTUAL (la copia del convocado puede estar desactualizada)
                    val player = state.roster.firstOrNull { it.id == callup.playerId }
                    CallupRow(
                        name = state.playerName(callup.playerId, callup.playerName),
                        callup = callup,
                        hasResponder = player?.let { if (it.isMinor) it.guardianIds.isNotEmpty() else it.userId.isNotEmpty() }
                            ?: callup.hasResponder,
                        reason = if (state.canManageMatch) state.reasons[callup.playerId] else null
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        } else if (!state.canManageMatch || !match.isOpen(now)) {
            Subtitle("Este partido no tiene convocados.")
        }

        // DT: elegir convocados mientras el partido no empiece
        if (state.canManageMatch && match.isOpen(now)) {
            val toAdd = state.activeRoster.filter { it.id !in state.calledIds }
            val firstTime = match.status == MatchStatus.PROGRAMMED
            // Un partido aún Programado que ya tiene convocados se puede "Publicar" sin marcar a nadie más
            val canRetryPublish = firstTime && state.callups.isNotEmpty()
            when {
                !state.rosterLoaded -> LoadingBox()
                state.activeRoster.isEmpty() && !canRetryPublish -> Subtitle("Tu plantel aún no tiene jugadores activos.")
                toAdd.isEmpty() -> {
                    Subtitle("Todo tu plantel activo ya está convocado.")
                    if (canRetryPublish) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Publicar convocatoria", onClick = { viewModel.publishCallup() }, loading = state.isLoading)
                    }
                }
                else -> {
                    Text(
                        if (firstTime) "Marca a los jugadores que quieres convocar." else "¿Quieres sumar a alguien más? Márcalo y agrégalo.",
                        color = TextoSecundario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    SportProCard {
                        toAdd.forEach { player ->
                            CheckRow(
                                text = player.name,
                                detail = listOfNotNull(
                                    player.dorsal?.let { "N.° $it" },
                                    player.positionMain,
                                    if (player.isMinor && player.guardianIds.isEmpty()) "Sin apoderado: no podrá responder" else null,
                                    if (!player.isMinor && player.userId.isEmpty()) "Sin cuenta: no podrá responder" else null
                                ).joinToString(" · "),
                                checked = player.id in state.selection,
                                onCheckedChange = { viewModel.toggleSelection(player.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val count = state.selection.count { id -> toAdd.any { it.id == id } }
                    PrimaryButton(
                        text = when {
                            firstTime && count > 0 -> "Publicar convocatoria ($count)"
                            firstTime -> "Publicar convocatoria"
                            count > 0 -> "Agregar a la convocatoria ($count)"
                            else -> "Agregar a la convocatoria"
                        },
                        onClick = { viewModel.publishCallup() },
                        enabled = count > 0 || canRetryPublish,
                        loading = state.isLoading
                    )
                    if (firstTime) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Después de publicar podrás agregar más jugadores, pero no quitar a los convocados.",
                            color = TextoSecundario,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Alineación (solo con convocados confirmados)
        val open = match.isOpen(now)
        val lineup = state.lineup
        if (state.callups.isNotEmpty() && (lineup != null || (state.canManageMatch && open))) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Alineación")
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    !state.lineupLoaded -> "Cargando…"
                    lineup == null -> "Aún no armas la alineación."
                    else -> "Titulares ${lineup.starters.size}/${state.maxStarters} · Suplentes ${lineup.substitutes.size}/${state.maxSubstitutes}"
                },
                color = TextoPrincipal,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                when {
                    !(state.canManageMatch && open) -> "Ver alineación"
                    lineup == null -> "Armar alineación"
                    else -> "Editar alineación"
                },
                onLineup
            )
        }

        // Reprogramar o cancelar (DT, antes del inicio)
        if (state.canManageMatch && open) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Partido")
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Reprogramar (fecha, hora o lugar)", onReschedule, enabled = !state.isLoading)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Cancelar partido", { showCancel = true }, enabled = !state.isLoading)
        }
        Spacer(Modifier.height(12.dp))
    }
}

// Datos del partido: equipo, fecha, hora, lugar y plazo para responder
@Composable
private fun MatchInfoCard(match: Match, confirmHours: Int) {
    SportProCard {
        InfoLine("Equipo", match.teamName)
        match.startsAt?.let {
            InfoLine("Fecha", it.toMatchDateText())
            InfoLine("Hora", it.toMatchTimeText())
        }
        InfoLine("Lugar", match.place)
        if (match.status == MatchStatus.CALLED) {
            match.confirmDeadline(confirmHours)?.let {
                InfoBox { Text("Responden hasta el ${it.toDeadlineText()}.", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, color = TextoSecundario, fontSize = 15.sp, modifier = Modifier.width(72.dp))
        Text(value, color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// Un convocado: nombre, respuesta, quién respondió y (solo el DT) el motivo de "No puedo"
@Composable
private fun CallupRow(name: String, callup: Callup, hasResponder: Boolean, reason: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
            when {
                callup.response != CallupResponse.PENDING && callup.respondedByName.isNotBlank() ->
                    Text("Respondió ${callup.respondedByName}", color = TextoSecundario, fontSize = 13.sp)
                !hasResponder ->
                    Text(
                        if (callup.isMinor) "Sin apoderado: no puede responder" else "Sin cuenta: no puede responder",
                        color = TextoSecundario,
                        fontSize = 13.sp
                    )
            }
            if (callup.response == CallupResponse.DECLINED && !reason.isNullOrBlank()) {
                Text("Motivo: $reason", color = TextoPrincipal, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        ResponseChip(callup.response)
    }
}
