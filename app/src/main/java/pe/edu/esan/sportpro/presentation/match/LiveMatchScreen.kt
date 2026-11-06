package pe.edu.esan.sportpro.presentation.match

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.CHANGE_EVENT_ID
import pe.edu.esan.sportpro.data.model.EVENT_MINUTE_MAX
import pe.edu.esan.sportpro.data.model.EVENT_NOTE_MAX
import pe.edu.esan.sportpro.data.model.EventSide
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.PlayerPick
import pe.edu.esan.sportpro.data.remote.MatchEventRepository
import pe.edu.esan.sportpro.presentation.components.ConfirmDialog
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.EventTypeBadge
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProDialog
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.TextLink
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.presentation.components.rememberNow
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TurquesaOscuro

// US-008 · "Registrar partido" (solo el DT del equipo y los operadores designados).
// Arriba: marcador, cronómetro y controles. En medio: botones del catálogo. Abajo: la cronología (US-010).
// Funciona sin conexión: los eventos quedan en el teléfono y se envían solos al reconectar.
@Composable
fun LiveMatchScreen(viewModel: LiveMatchViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val now by rememberNow(1_000)
    val match = state.match

    state.confirmRepeat?.let { type ->
        SportProDialog(
            title = "¿Registrar otra vez «${type.name}»?",
            confirmText = "Sí, registrar otra vez",
            onConfirm = viewModel::confirmRepeat,
            onDismiss = viewModel::dismissRepeat
        ) {
            Text(
                "Acabas de registrar «${type.name}» hace menos de 3 segundos. ¿Es un evento nuevo o fue un doble toque?",
                color = TextoPrincipal,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
    if (state.confirmFinish && match != null) {
        ConfirmDialog(
            title = "¿Finalizar el partido?",
            text = "El cronómetro se detiene y el partido queda como Finalizado. Después solo podrás agregar eventos " +
                "olvidados como “Inserción posterior”.",
            confirmText = "Finalizar",
            onDismiss = viewModel::dismissFinish,
            onConfirm = viewModel::finishMatch
        )
    }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Registrar partido")

        if (match == null) {
            Spacer(Modifier.height(16.dp))
            val error = state.loadErrors["partido"]
            if (error != null) ErrorBox(error) else LoadingBox()
            return@SportProScreen
        }
        Spacer(Modifier.height(4.dp))
        Subtitle("${match.teamName} vs ${match.rival}")
        Spacer(Modifier.height(16.dp))

        if (!state.canRecord) {
            InfoBox {
                Text(
                    "Solo el DT del equipo y los operadores que él elija pueden registrar este partido.",
                    color = TextoPrincipal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            return@SportProScreen
        }

        // Avisos de conexión y de sincronización (no bloquean nada)
        if (!state.online) {
            OfflineBanner()
            Spacer(Modifier.height(10.dp))
        }
        if (state.pending > 0) {
            PendingBanner(state.pending)
            Spacer(Modifier.height(10.dp))
        }

        ScoreBoard(match, state.score, now, showClock = true)
        state.loadError?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }
        Messages(state.errorMessage, state.infoMessage)
        Spacer(Modifier.height(16.dp))

        // Controles del partido
        when (match.status) {
            MatchStatus.PROGRAMMED, MatchStatus.CALLED -> {
                PrimaryButton("Iniciar partido", viewModel::startMatch, enabled = state.types.isNotEmpty())
                Spacer(Modifier.height(8.dp))
                Text(
                    "Antes del inicio solo se puede tocar “Iniciar partido”.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            MatchStatus.LIVE -> {
                if (match.clockRunning) SecondaryButton("Pausar cronómetro", viewModel::pauseClock)
                else PrimaryButton("Reanudar cronómetro", viewModel::resumeClock)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Finalizar partido", viewModel::askFinish)
            }
            MatchStatus.FINISHED -> {
                SecondaryButton(if (state.insertMode) "Ocultar catálogo" else "Insertar evento omitido", viewModel::toggleInsertMode)
            }
            MatchStatus.CANCELLED -> Subtitle("Este partido fue cancelado: no se puede registrar.")
        }
        // El cronómetro necesita conexión; los eventos no
        if (!state.online && (match.isLive || match.status in listOf(MatchStatus.PROGRAMMED, MatchStatus.CALLED))) {
            Spacer(Modifier.height(8.dp))
            Text(
                MatchEventRepository.CLOCK_NEEDS_CONNECTION_MESSAGE,
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Evento que se está registrando
        if (state.draft != null) {
            Spacer(Modifier.height(16.dp))
            DraftPanel(state, viewModel)
        }

        // Cuadrícula del catálogo activo (sin Inicio ni Fin, que van arriba)
        val showGrid = match.status in listOf(MatchStatus.PROGRAMMED, MatchStatus.CALLED, MatchStatus.LIVE) ||
            (match.isFinished && state.insertMode)
        if (showGrid && state.draft == null) {
            Spacer(Modifier.height(20.dp))
            SectionLabel(if (match.isFinished) "Elige el evento omitido" else "Eventos")
            Spacer(Modifier.height(10.dp))
            val enabled = match.isLive || (match.isFinished && state.insertMode)
            if (state.catalogButtons.isEmpty()) {
                Subtitle(if (state.loadErrors["catálogo"] != null) "No pudimos cargar el catálogo." else "Tu academia aún no tiene eventos activos en el catálogo.")
            }
            state.catalogButtons.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { type ->
                        EventTypeButton(type.name, type.icon, type.color, enabled, Modifier.weight(1f)) { viewModel.onTypeTapped(type) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Cronología")
        Spacer(Modifier.height(10.dp))
        if (!state.eventsLoaded && state.loadErrors["eventos"] == null) LoadingBox()
        else EventTimeline(state.timeline, if (state.canSeeNotes) state.notes else emptyMap(), showAuthor = true)
        Spacer(Modifier.height(12.dp))
    }
}

// Pasos para registrar un evento: equipo → jugador (y en un cambio, el que sale). El minuto y la observación
// son un paso OPCIONAL: al elegir el jugador el evento se guarda directo.
@Composable
private fun DraftPanel(state: LiveMatchUiState, viewModel: LiveMatchViewModel) {
    val draft = state.draft ?: return
    val match = state.match ?: return
    val type = draft.type
    SportProCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EventTypeBadge(type.icon, type.color, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(type.name, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextoPrincipal)
                Text("Minuto ${state.minuteText.ifEmpty { "?" }}'", color = TextoSecundario, fontSize = 14.sp)
            }
            TextLink("Cancelar", color = TextoSecundario, onClick = viewModel::cancelDraft)
        }
        if (draft.postInsert) StatusChip("Inserción posterior", SuperficieSuave, TurquesaOscuro)

        when (state.step) {
            RecordStep.TEAM -> {
                StepText("¿De qué equipo?")
                PrimaryButton("Nosotros (${match.teamName})", { viewModel.onTeamChosen(EventSide.LOCAL) })
                SecondaryButton("Rival (${match.rival})", { viewModel.onTeamChosen(EventSide.RIVAL) })
            }
            RecordStep.PLAYER -> {
                val change = type.id == CHANGE_EVENT_ID
                StepText(if (change) "¿Quién entra?" else "¿Quién fue?")
                val players = state.fieldPlayers
                // En un cambio, primero los suplentes (los que pueden entrar)
                if (change) {
                    PlayerGroup("Suplentes", players.bench, viewModel::onPlayerChosen)
                    PlayerGroup("En cancha", players.onField, viewModel::onPlayerChosen)
                } else {
                    PlayerGroup("En cancha", players.onField, viewModel::onPlayerChosen)
                    PlayerGroup("Suplentes", players.bench, viewModel::onPlayerChosen)
                }
                NoPlayersHint(players.onField.isEmpty() && players.bench.isEmpty(), state.lineupMissingForOperator)
                SecondaryButton("Sin identificar", { viewModel.onPlayerChosen(null) })
            }
            RecordStep.PLAYER_OUT -> {
                StepText("¿Quién sale?")
                val players = state.fieldPlayers
                val outCandidates = players.onField.filter { it.playerId != draft.playerId }
                PlayerGroup("En cancha", outCandidates, viewModel::onPlayerOutChosen)
                NoPlayersHint(outCandidates.isEmpty(), state.lineupMissingForOperator)
                SecondaryButton("Sin identificar", { viewModel.onPlayerOutChosen(null) })
            }
            RecordStep.RIVAL_NUMBER -> {
                StepText("Dorsal del rival")
                RivalKeypad(state.rivalNumberText, viewModel::onRivalDigit, viewModel::onRivalBackspace)
                PrimaryButton(
                    if (state.rivalNumberText.isEmpty()) "Guardar con dorsal" else "Guardar con #${state.rivalNumberText}",
                    { viewModel.onRivalNumberDone(withNumber = true) },
                    enabled = state.rivalNumberText.isNotEmpty()
                )
                SecondaryButton("Sin dorsal", { viewModel.onRivalNumberDone(withNumber = false) })
            }
            RecordStep.CONFIRM, null -> PrimaryButton("Guardar", viewModel::saveCurrent)
        }

        // Paso opcional: minuto (propuesto por el cronómetro) y observación
        TextLink(
            if (state.showDetails) "Ocultar minuto y observación" else "Cambiar minuto u observación (opcional)",
            onClick = viewModel::toggleDetails
        )
        if (state.showDetails) {
            SportProTextField(
                value = state.minuteText,
                onValueChange = viewModel::onMinuteChange,
                label = "Minuto (1 a $EVENT_MINUTE_MAX)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (type.allowsNote) {
                SportProTextField(
                    value = draft.note,
                    onValueChange = viewModel::onNoteChange,
                    label = "Observación (${draft.note.length}/$EVENT_NOTE_MAX)",
                    placeholder = "Solo la ven el DT, los operadores y la administración"
                )
            }
            if (state.step != RecordStep.CONFIRM) {
                Text("Se guarda al elegir la opción de arriba.", color = TextoSecundario, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StepText(text: String) {
    Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
}

@Composable
private fun NoPlayersHint(show: Boolean, lineupMissingForOperator: Boolean) {
    if (show) {
        Text(
            // Un operador que no es DT ni ADM no puede ver los convocados: solo la alineación
            if (lineupMissingForOperator) "Pide al DT que arme la alineación para elegir jugadores; mientras tanto puedes registrar como Sin identificar."
            else "No hay alineación ni convocados confirmados para elegir. Puedes registrarlo como “Sin identificar”.",
            color = TextoSecundario,
            fontSize = 14.sp
        )
    }
}

// Un grupo de jugadores (nombre corto) como botones de 48 dp
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerGroup(title: String, players: List<PlayerPick>, onPick: (PlayerPick) -> Unit) {
    if (players.isEmpty()) return
    Text(title, color = TextoSecundario, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        players.forEach { player ->
            ChoiceBox(player.playerName.ifEmpty { "Jugador" }) { onPick(player) }
        }
    }
}

@Composable
private fun ChoiceBox(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SuperficieSuave)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
    }
}

// Teclado numérico del dorsal del rival (1–99)
@Composable
private fun RivalKeypad(value: String, onDigit: (Int) -> Unit, onBackspace: () -> Unit) {
    Text(
        if (value.isEmpty()) "#  –" else "#$value",
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        color = TextoPrincipal,
        modifier = Modifier.fillMaxWidth()
    )
    listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { digit -> ChoiceBox(digit.toString(), Modifier.weight(1f)) { onDigit(digit) } }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        ChoiceBox("0", Modifier.weight(1f)) { onDigit(0) }
        ChoiceBox("Borrar", Modifier.weight(1f), onBackspace)
    }
}
