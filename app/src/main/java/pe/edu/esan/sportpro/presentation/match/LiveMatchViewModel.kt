package pe.edu.esan.sportpro.presentation.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.CHANGE_EVENT_ID
import pe.edu.esan.sportpro.data.model.Callup
import pe.edu.esan.sportpro.data.model.CallupResponse
import pe.edu.esan.sportpro.data.model.DOUBLE_TAP_WINDOW_MS
import pe.edu.esan.sportpro.data.model.EMPTY_CATALOG_MESSAGE
import pe.edu.esan.sportpro.data.model.END_EVENT_ID
import pe.edu.esan.sportpro.data.model.EVENT_MINUTE_MAX
import pe.edu.esan.sportpro.data.model.EVENT_MINUTE_MIN
import pe.edu.esan.sportpro.data.model.EVENT_NOTE_MAX
import pe.edu.esan.sportpro.data.model.EventDraft
import pe.edu.esan.sportpro.data.model.EventSide
import pe.edu.esan.sportpro.data.model.EventType
import pe.edu.esan.sportpro.data.model.FieldPlayers
import pe.edu.esan.sportpro.data.model.Lineup
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchEvent
import pe.edu.esan.sportpro.data.model.PlayerPick
import pe.edu.esan.sportpro.data.model.RIVAL_NUMBER_MAX
import pe.edu.esan.sportpro.data.model.START_EVENT_ID
import pe.edu.esan.sportpro.data.model.Score
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.fieldPlayersOf
import pe.edu.esan.sportpro.data.model.pendingCount
import pe.edu.esan.sportpro.data.model.scoreOf
import pe.edu.esan.sportpro.data.model.timelineOf
import pe.edu.esan.sportpro.data.model.validateEventDraft
import pe.edu.esan.sportpro.data.remote.ConnectionChecker
import pe.edu.esan.sportpro.data.remote.EventTypeRepository
import pe.edu.esan.sportpro.data.remote.MatchEventRepository
import pe.edu.esan.sportpro.data.remote.MatchRepository

// Pasos para registrar un evento (máximo tres toques obligatorios: tipo, equipo y jugador)
enum class RecordStep { TEAM, PLAYER, PLAYER_OUT, RIVAL_NUMBER, CONFIRM }

// Estado del partido en vivo: registro (US-008, DT y operadores) y vista en vivo (US-010, todos los miembros)
data class LiveMatchUiState(
    val profile: UserProfile? = null,
    val matchId: String = "",
    val match: Match? = null,
    val events: List<MatchEvent> = emptyList(),
    val eventsLoaded: Boolean = false,
    val types: List<EventType> = emptyList(),
    val lineup: Lineup? = null,
    val callups: List<Callup> = emptyList(),      // solo DT y ADM (las reglas no dejan a los demás)
    val notes: Map<String, String> = emptyMap(),  // eventId -> observación (solo DT, operadores y ADM)
    val online: Boolean = true,                   // "Sin conexión" (no bloquea nada)
    // Registro de un evento
    val draft: EventDraft? = null,
    val step: RecordStep? = null,
    val minuteText: String = "",                  // minuto editable (paso opcional)
    val showDetails: Boolean = false,             // "Cambiar minuto u observación"
    val rivalNumberText: String = "",
    val insertMode: Boolean = false,              // después del 'fin': "Insertar evento omitido"
    val confirmRepeat: EventType? = null,         // "¿Registrar otra vez «Gol»?" (mismo tipo en menos de 3 s)
    val confirmFinish: Boolean = false,
    val loadErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val loadError: String? get() = loadErrors.values.firstOrNull()

    // Puede registrar: el DT del equipo o un operador designado (RN-010)
    val canRecord: Boolean get() = match?.canRecord(profile) == true

    // PRIVACIDAD: la observación solo la ven el DT del equipo, los operadores y el ADM
    val canSeeNotes: Boolean
        get() = canRecord || (profile?.role == "ADM" && profile.academyId == match?.academyId)

    // RN-012: marcador calculado en este teléfono con los eventos vigentes
    val score: Score get() = scoreOf(events)
    val timeline: List<MatchEvent> get() = timelineOf(events)
    val pending: Int get() = pendingCount(events)

    // Botones del catálogo: tipos ACTIVOS, en el orden de la academia, sin 'inicio' ni 'fin' (van arriba)
    val catalogButtons: List<EventType>
        get() = types.filter { it.active && it.id != START_EVENT_ID && it.id != END_EVENT_ID }.sortedBy { it.order }

    // Solo el DT del equipo y el ADM pueden leer la lista de convocados (reglas de la US-006)
    val canListCallups: Boolean
        get() = canRecord && profile != null && match != null &&
            ((profile.role == "DT" && profile.teamId == match.teamId) || profile.role == "ADM")

    // Operador que no es DT ni ADM y el partido no tiene alineación: no hay jugadores que mostrarle
    val lineupMissingForOperator: Boolean
        get() = lineup == null && !canListCallups

    // Nuestros jugadores para elegir: en cancha y en la banca según la alineación y los cambios.
    // Sin alineación: los convocados que confirmaron (solo los ve el DT o el ADM).
    val fieldPlayers: FieldPlayers
        get() = lineup?.let { fieldPlayersOf(it, events) }
            ?: FieldPlayers(
                onField = callups.filter { it.response == CallupResponse.CONFIRMED }
                    .map { PlayerPick(it.playerId, it.playerName) }.sortedBy { it.playerName.lowercase() },
                bench = emptyList()
            )
}

class LiveMatchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LiveMatchUiState())
    val uiState: StateFlow<LiveMatchUiState> = _uiState.asStateFlow()

    private var jobs: List<Job> = emptyList()
    private var notesJob: Job? = null
    private var callupsJob: Job? = null
    private var startedKey = ""

    // Doble toque: último tipo registrado y cuándo
    private var lastTypeId = ""
    private var lastSavedAt = 0L

    // ---------- Inicio: escucha el partido, sus eventos, el catálogo, la alineación y la red ----------
    fun select(matchId: String, profile: UserProfile) {
        val key = "${profile.uid}/$matchId"
        if (key == startedKey && jobs.isNotEmpty()) {
            _uiState.update { it.copy(profile = profile) }
            return
        }
        stop()
        startedKey = key
        _uiState.value = LiveMatchUiState(profile = profile, matchId = matchId)
        jobs = listOf(
            listen("partido", MatchRepository.matchFlow(matchId)) { state, match -> state.copy(match = match) },
            listen("eventos", MatchEventRepository.eventsFlow(matchId)) { state, events -> state.copy(events = events, eventsLoaded = true) },
            listen("catálogo", EventTypeRepository.eventTypesFlow(profile.academyId)) { state, types -> state.copy(types = types) },
            listen("alineación", MatchRepository.lineupFlow(matchId)) { state, lineup -> state.copy(lineup = lineup) },
            viewModelScope.launch {
                ConnectionChecker.onlineFlow().collect { online ->
                    // Al volver la señal se borra el aviso del cronómetro (los demás errores se quedan)
                    _uiState.update { (if (online) it.withoutClockMessage() else it).copy(online = online) }
                }
            }
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        notesJob?.cancel()
        notesJob = null
        callupsJob?.cancel()
        callupsJob = null
        startedKey = ""
        lastTypeId = ""
        lastSavedAt = 0L
        _uiState.value = LiveMatchUiState()
    }

    // Escucha una fuente; su error se guarda (o se quita) solo con su propia clave
    private fun <T> listen(source: String, flow: Flow<Result<T>>, onData: (LiveMatchUiState, T) -> LiveMatchUiState): Job =
        viewModelScope.launch {
            flow.collect { result ->
                _uiState.update { state ->
                    result.fold(
                        onSuccess = { onData(state, it).copy(loadErrors = state.loadErrors - source) },
                        onFailure = {
                            state.copy(loadErrors = state.loadErrors + (source to (it.message ?: "No se pudo cargar la información.")))
                        }
                    )
                }
                if (source == "partido") syncPrivateSources()
            }
        }

    // Las observaciones y los convocados se escuchan SOLO si las reglas lo permiten (se sabe al llegar el partido)
    private fun syncPrivateSources() {
        val state = _uiState.value
        val matchId = state.matchId
        if (state.canSeeNotes && notesJob == null) {
            notesJob = listen("observaciones", MatchEventRepository.notesFlow(matchId)) { s, notes ->
                s.copy(notes = notes.associate { it.eventId to it.note })
            }
        } else if (!state.canSeeNotes && notesJob != null) {
            notesJob?.cancel()
            notesJob = null
            _uiState.update { it.copy(notes = emptyMap(), loadErrors = it.loadErrors - "observaciones") }
        }
        if (state.canListCallups && callupsJob == null) {
            callupsJob = listen("convocados", MatchRepository.callupsFlow(matchId)) { s, callups -> s.copy(callups = callups) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    // Quita solo el aviso "el cronómetro necesita conexión". Un rechazo del servidor no se borra aquí.
    private fun LiveMatchUiState.withoutClockMessage(): LiveMatchUiState =
        if (errorMessage == MatchEventRepository.CLOCK_NEEDS_CONNECTION_MESSAGE) copy(errorMessage = null) else this

    // Si el servidor rechaza una escritura (al instante o al reconectar)
    private val onRejected: (String) -> Unit = { message ->
        _uiState.update { it.copy(errorMessage = message, infoMessage = null) }
    }

    // ---------- Cronómetro (DT u operador) ----------

    // "Iniciar partido": evento 'inicio' + En curso + cronómetro, en un solo lote
    fun startMatch() {
        val state = _uiState.value
        val match = state.match ?: return
        val profile = state.profile ?: return
        if (!state.canRecord || match.hasLiveData) return
        val startType = state.types.firstOrNull { it.id == START_EVENT_ID }
            ?: return _uiState.update { it.copy(errorMessage = EMPTY_CATALOG_MESSAGE) }
        val result = MatchEventRepository.startMatch(match, startType, profile, MatchEventRepository.newEventId(), onRejected)
        _uiState.update {
            it.copy(
                errorMessage = result.exceptionOrNull()?.message,
                infoMessage = if (result.isSuccess) "¡Empezó el partido! El cronómetro ya corre." else null
            )
        }
    }

    fun pauseClock() {
        val state = _uiState.value
        val match = state.match ?: return
        if (!state.canRecord || !match.isLive || !match.clockRunning) return
        val result = MatchEventRepository.pauseClock(match, onRejected)
        _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message, infoMessage = null) }
    }

    fun resumeClock() {
        val state = _uiState.value
        val match = state.match ?: return
        if (!state.canRecord || !match.isLive || match.clockRunning) return
        val result = MatchEventRepository.resumeClock(match, onRejected)
        _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message, infoMessage = null) }
    }

    fun askFinish() {
        if (_uiState.value.match?.isLive == true) _uiState.update { it.withoutClockMessage().copy(confirmFinish = true) }
    }

    fun dismissFinish() {
        _uiState.update { it.copy(confirmFinish = false) }
    }

    // "Finalizar": evento 'fin' + Finalizado + cronómetro detenido, en un solo lote
    fun finishMatch() {
        val state = _uiState.value
        val match = state.match ?: return
        val profile = state.profile ?: return
        _uiState.update { it.copy(confirmFinish = false) }
        if (!state.canRecord || !match.isLive) return
        val endType = state.types.firstOrNull { it.id == END_EVENT_ID }
            ?: return _uiState.update { it.copy(errorMessage = EMPTY_CATALOG_MESSAGE) }
        val result = MatchEventRepository.finishMatch(match, endType, profile, MatchEventRepository.newEventId(), onRejected)
        _uiState.update {
            it.copy(
                draft = if (result.isSuccess) null else it.draft,
                step = if (result.isSuccess) null else it.step,
                errorMessage = result.exceptionOrNull()?.message,
                infoMessage = if (result.isSuccess) "Partido finalizado. Si olvidaste algo, usa “Insertar evento omitido”." else null
            )
        }
    }

    // ---------- Registrar un evento ----------

    // Después del 'fin': mostrar u ocultar el catálogo para insertar un evento omitido
    fun toggleInsertMode() {
        _uiState.update { it.withoutClockMessage().copy(insertMode = !it.insertMode, draft = null, step = null) }
    }

    // Toque 1: el tipo. Si es el mismo tipo de hace menos de 3 s, primero se pregunta (doble toque).
    fun onTypeTapped(type: EventType) {
        val state = _uiState.value
        val match = state.match ?: return
        if (!state.canRecord) return
        if (!(match.isLive || (match.isFinished && state.insertMode))) return
        if (type.id == lastTypeId && System.currentTimeMillis() - lastSavedAt < DOUBLE_TAP_WINDOW_MS) {
            _uiState.update { it.withoutClockMessage().copy(confirmRepeat = type) }
            return
        }
        beginDraft(type)
    }

    fun confirmRepeat() {
        val type = _uiState.value.confirmRepeat ?: return
        _uiState.update { it.copy(confirmRepeat = null) }
        beginDraft(type)
    }

    fun dismissRepeat() {
        _uiState.update { it.copy(confirmRepeat = null) }
    }

    private fun beginDraft(type: EventType) {
        val state = _uiState.value
        val match = state.match ?: return
        val minute = match.currentMinute().coerceIn(EVENT_MINUTE_MIN, EVENT_MINUTE_MAX)
        val draft = EventDraft(type = type, minute = minute, proposedMinute = minute, postInsert = match.isFinished)
        _uiState.update {
            it.copy(
                draft = draft,
                step = if (type.requiresTeam || type.requiresPlayer || type.affectsScore) RecordStep.TEAM else RecordStep.CONFIRM,
                minuteText = minute.toString(),
                showDetails = false,
                rivalNumberText = "",
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun cancelDraft() {
        _uiState.update { it.withoutClockMessage().copy(draft = null, step = null, showDetails = false) }
    }

    // Toque 2: el equipo. En TODOS los tipos con equipo se pide después el jugador o el dorsal; si el tipo no exige
    // jugador, "Sin identificar" o "Sin dorsal" lo guardan sin la etiqueta "Pendiente de identificar".
    fun onTeamChosen(side: String) {
        val state = _uiState.value
        val draft = state.draft ?: return
        val updated = draft.copy(side = side)
        if (side == EventSide.LOCAL) {
            _uiState.update { it.withoutClockMessage().copy(draft = updated, step = RecordStep.PLAYER) }
        } else {
            _uiState.update { it.withoutClockMessage().copy(draft = updated, step = RecordStep.RIVAL_NUMBER, rivalNumberText = "") }
        }
    }

    // Toque 3: el jugador (null = "Sin identificar"). En un 'cambio' es el que ENTRA y después se pide el que sale.
    fun onPlayerChosen(pick: PlayerPick?) {
        val draft = _uiState.value.draft ?: return
        val updated = draft.copy(playerId = pick?.playerId.orEmpty(), playerName = pick?.playerName.orEmpty())
        if (draft.type.id == CHANGE_EVENT_ID) {
            _uiState.update { it.withoutClockMessage().copy(draft = updated, step = RecordStep.PLAYER_OUT) }
        } else {
            save(updated)
        }
    }

    // 'Cambio': el jugador que SALE (null = "Sin identificar")
    fun onPlayerOutChosen(pick: PlayerPick?) {
        val draft = _uiState.value.draft ?: return
        save(draft.copy(playerOutId = pick?.playerId.orEmpty(), playerOutName = pick?.playerName.orEmpty()))
    }

    // Teclado del dorsal del rival (1–99)
    fun onRivalDigit(digit: Int) {
        _uiState.update { current ->
            val state = current.withoutClockMessage()
            val next = (state.rivalNumberText + digit).trimStart('0')
            if (next.isEmpty() || next.toInt() > RIVAL_NUMBER_MAX) state else state.copy(rivalNumberText = next)
        }
    }

    fun onRivalBackspace() {
        _uiState.update { it.withoutClockMessage().copy(rivalNumberText = it.rivalNumberText.dropLast(1)) }
    }

    // Guardar con el dorsal escrito, o sin dorsal (withNumber = false)
    fun onRivalNumberDone(withNumber: Boolean) {
        val state = _uiState.value
        val draft = state.draft ?: return
        save(draft.copy(rivalNumber = if (withNumber) state.rivalNumberText.toIntOrNull() else null))
    }

    // Paso final OPCIONAL: minuto y observación (no cuenta como toque obligatorio)
    fun toggleDetails() {
        _uiState.update { it.withoutClockMessage().copy(showDetails = !it.showDetails) }
    }

    fun onMinuteChange(text: String) {
        _uiState.update { it.copy(minuteText = text.filter { c -> c.isDigit() }.take(3), errorMessage = null) }
    }

    fun onNoteChange(text: String) {
        _uiState.update { state ->
            state.draft?.let { state.withoutClockMessage().copy(draft = it.copy(note = text.take(EVENT_NOTE_MAX))) } ?: state
        }
    }

    // Botón "Guardar" (tipos sin equipo, o después de ajustar los detalles)
    fun saveCurrent() {
        _uiState.value.draft?.let { save(it) }
    }

    // Guarda el evento. Funciona sin conexión: queda en la caché y se envía solo al reconectar.
    private fun save(draft: EventDraft) {
        val state = _uiState.value
        val match = state.match ?: return
        val profile = state.profile ?: return
        if (!state.canRecord) return
        val withMinute = draft.copy(minute = state.minuteText.toIntOrNull() ?: 0)
        validateEventDraft(withMinute)?.let { error ->
            _uiState.update { it.copy(draft = withMinute, showDetails = true, errorMessage = error, infoMessage = null) }
            return
        }
        val result = MatchEventRepository.recordEvent(
            match = match,
            draft = withMinute,
            by = profile,
            eventId = MatchEventRepository.newEventId(),
            onRejected = onRejected
        )
        if (result.isSuccess) {
            lastTypeId = draft.type.id
            lastSavedAt = System.currentTimeMillis()
        }
        _uiState.update {
            it.copy(
                draft = if (result.isSuccess) null else withMinute,
                step = if (result.isSuccess) null else it.step,
                showDetails = false,
                errorMessage = result.exceptionOrNull()?.message,
                infoMessage = if (result.isSuccess) {
                    // Textos neutros: el nombre del tipo puede ser femenino o masculino, y el ADM puede crear otros
                    if (it.online) "Se registró: ${draft.type.name}."
                    else "Se guardó en el teléfono: ${draft.type.name}. Se enviará al recuperar la conexión."
                } else null
            )
        }
    }
}
