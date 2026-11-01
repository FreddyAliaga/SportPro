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
import pe.edu.esan.sportpro.data.model.Academy
import pe.edu.esan.sportpro.data.model.Callup
import pe.edu.esan.sportpro.data.model.CallupResponse
import pe.edu.esan.sportpro.data.model.DEFAULT_CALLUP_CONFIRM_HOURS
import pe.edu.esan.sportpro.data.model.DEFAULT_CALLUP_NOTICE_HOURS
import pe.edu.esan.sportpro.data.model.Lineup
import pe.edu.esan.sportpro.data.model.LineupSlot
import pe.edu.esan.sportpro.data.model.LineupStarter
import pe.edu.esan.sportpro.data.model.LineupSubstitute
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchForm
import pe.edu.esan.sportpro.data.model.MatchFormErrors
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.NoticeRecipient
import pe.edu.esan.sportpro.data.model.NotificationDraft
import pe.edu.esan.sportpro.data.model.PLAYER_POSITIONS
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.Team
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.categoryOrder
import pe.edu.esan.sportpro.data.model.matchCancelledNotice
import pe.edu.esan.sportpro.data.model.matchRescheduledNotice
import pe.edu.esan.sportpro.data.model.matchStartsAt
import pe.edu.esan.sportpro.data.model.newCallupNotice
import pe.edu.esan.sportpro.data.model.recipientsOf
import pe.edu.esan.sportpro.data.model.toForm
import pe.edu.esan.sportpro.data.model.validateLineup
import pe.edu.esan.sportpro.data.model.validateMatchForm
import pe.edu.esan.sportpro.data.remote.AcademyRepository
import pe.edu.esan.sportpro.data.remote.MatchRepository
import pe.edu.esan.sportpro.data.remote.NotificationRepository
import pe.edu.esan.sportpro.data.remote.PlayerRepository

// Avisos que fallaron y se pueden reintentar (sin duplicar: cada uno tiene un ID fijo)
data class PendingNotice(val draft: NotificationDraft, val recipients: List<NoticeRecipient>)

// Estado de la US-006 para el DT (crea partidos y convoca) y el ADM (solo lectura)
data class MatchUiState(
    val profile: UserProfile? = null,
    val academy: Academy? = null,                 // plazos de la convocatoria (RN-008)
    val teams: List<Team> = emptyList(),
    val matches: List<Match> = emptyList(),
    val matchesLoaded: Boolean = false,
    val teamFilter: String? = null,               // ADM: null = Todos los equipos
    // Plantel del DT (para elegir a los convocados)
    val roster: List<Player> = emptyList(),
    val rosterLoaded: Boolean = false,
    // Partido elegido (detalle)
    val selectedMatchId: String = "",
    val match: Match? = null,
    val callups: List<Callup> = emptyList(),
    val callupsLoaded: Boolean = false,
    val reasons: Map<String, String> = emptyMap(), // playerId -> motivo de "No puedo" (solo el DT)
    val selection: Set<String> = emptySet(),       // jugadores marcados que aún no están convocados
    val confirmShortNotice: Boolean = false,       // diálogo "Falta menos de X h para el partido"
    // Alineación (Parte C)
    val lineup: Lineup? = null,                    // la guardada (null = aún no se arma)
    val lineupLoaded: Boolean = false,
    val lineupDraft: Map<String, LineupSlot> = emptyMap(),   // playerId -> titular (con posición) o suplente
    val lineupDraftMatchId: String = "",           // partido con el que se llenó el borrador
    val lineupErrors: List<String> = emptyList(),
    // Avisos que no se pudieron enviar (botón "Reintentar avisos")
    val pendingNotice: PendingNotice? = null,
    // Nuevo partido / reprogramar
    val form: MatchForm = MatchForm(),
    val formErrors: MatchFormErrors = MatchFormErrors(),
    // Un error por fuente: que una lista cargue bien no borra el error de otra
    val loadErrors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val loadError: String? get() = loadErrors.values.firstOrNull()

    val isCoach: Boolean get() = profile?.role == "DT"
    val isAdmin: Boolean get() = profile?.role == "ADM"

    // El DT gestiona solo los partidos de SU equipo (las reglas lo vuelven a revisar)
    val canManageMatch: Boolean
        get() = isCoach && match != null && match.teamId == profile?.teamId

    val noticeHours: Int get() = academy?.callupNoticeHours ?: DEFAULT_CALLUP_NOTICE_HOURS
    val confirmHours: Int get() = academy?.callupConfirmHours ?: DEFAULT_CALLUP_CONFIRM_HOURS

    fun teamName(teamId: String): String = teams.firstOrNull { it.id == teamId }?.name.orEmpty()

    // ADM: equipos para el filtro (activos e inactivos, por categoría)
    val filterTeams: List<Team>
        get() = teams.sortedWith(compareBy({ !it.active }, { categoryOrder(it.category) }, { it.name.lowercase() }))

    private val visibleMatches: List<Match>
        get() = matches.filter { teamFilter == null || it.teamId == teamFilter }

    // Próximos: sin empezar (programados o convocados) y los que están en curso; el más cercano primero
    fun upcoming(now: Long): List<Match> =
        visibleMatches.filter { it.isOpen(now) || it.status == MatchStatus.LIVE }
            .sortedBy { it.startsAt?.time ?: Long.MAX_VALUE }

    // Anteriores: ya empezaron, finalizados o cancelados; el más reciente primero
    fun past(now: Long): List<Match> =
        visibleMatches.filter { !(it.isOpen(now) || it.status == MatchStatus.LIVE) }
            .sortedByDescending { it.startsAt?.time ?: 0L }

    // Resumen de la convocatoria
    val confirmedCount: Int get() = callups.count { it.response == CallupResponse.CONFIRMED }
    val declinedCount: Int get() = callups.count { it.response == CallupResponse.DECLINED }
    val pendingCount: Int get() = callups.count { it.response == CallupResponse.PENDING }

    val calledIds: Set<String> get() = callups.map { it.playerId }.toSet()

    // Jugadores ACTIVOS del plantel, por dorsal y nombre
    val activeRoster: List<Player>
        get() = roster.filter { it.active }.sortedWith(compareBy({ it.dorsal ?: Int.MAX_VALUE }, { it.name.lowercase() }))

    // Convocados ordenados: confirmados, no pueden y sin confirmar; luego por nombre
    val sortedCallups: List<Callup>
        get() = callups.sortedWith(
            compareBy(
                { listOf(CallupResponse.CONFIRMED, CallupResponse.DECLINED, CallupResponse.PENDING).indexOf(it.response) },
                { playerName(it.playerId, it.playerName).lowercase() }
            )
        )

    // Nombre completo del plantel si el DT lo tiene; si no, el nombre corto del convocado
    fun playerName(playerId: String, fallback: String): String =
        roster.firstOrNull { it.id == playerId }?.name ?: fallback

    // ---------- Alineación ----------
    val matchTeam: Team? get() = teams.firstOrNull { it.id == match?.teamId }
    val maxStarters: Int get() = matchTeam?.maxStarters ?: 11
    val maxSubstitutes: Int get() = matchTeam?.maxSubstitutes ?: 12

    // Solo los convocados que confirmaron pueden estar en la alineación
    val confirmedCallups: List<Callup>
        get() = callups.filter { it.response == CallupResponse.CONFIRMED }
            .sortedBy { playerName(it.playerId, it.playerName).lowercase() }

    private val confirmedIds: Set<String> get() = confirmedCallups.map { it.playerId }.toSet()

    val draftStarters: List<LineupStarter>
        get() = confirmedCallups.mapNotNull { callup ->
            lineupDraft[callup.playerId]?.takeIf { it.starter }?.let { LineupStarter(callup.playerId, callup.playerName, it.position) }
        }

    val draftSubstitutes: List<LineupSubstitute>
        get() = confirmedCallups.mapNotNull { callup ->
            lineupDraft[callup.playerId]?.takeIf { !it.starter }?.let { LineupSubstitute(callup.playerId, callup.playerName) }
        }

    // Estaban en la alineación guardada pero ya no están confirmados: al guardar salen de la alineación
    val lineupDroppedNames: List<String>
        get() = lineup?.let { saved ->
            (saved.starters.map { it.playerId to it.playerName } + saved.substitutes.map { it.playerId to it.playerName })
                .filter { (id, _) -> id !in confirmedIds }
                .map { (id, name) -> playerName(id, name) }
        }.orEmpty()
}

class MatchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var baseJobs: List<Job> = emptyList()
    private var detailJobs: List<Job> = emptyList()
    private var startedKey = ""   // uid + equipo con el que se escucha (al DT le pueden cambiar el equipo)

    // ---------- Inicio: según el rol del perfil con sesión (DT: su equipo; ADM: toda la academia) ----------
    fun start(profile: UserProfile) {
        if (profile.role !in listOf("DT", "ADM") || profile.academyId.isEmpty()) return
        val key = "${profile.uid}/${profile.teamId}"
        if (key == startedKey && baseJobs.isNotEmpty()) {
            _uiState.update { it.copy(profile = profile) }
            return
        }
        stop()
        startedKey = key
        _uiState.value = MatchUiState(profile = profile)
        val academyId = profile.academyId
        val jobs = mutableListOf(
            listen("academia", AcademyRepository.academyFlow(academyId)) { state, academy -> state.copy(academy = academy) },
            listen("equipos", AcademyRepository.teamsFlow(academyId)) { state, teams -> state.copy(teams = teams) }
        )
        if (profile.role == "ADM") {
            jobs += listen("partidos", MatchRepository.academyMatchesFlow(academyId)) { state, matches ->
                state.copy(matches = matches, matchesLoaded = true)
            }
        } else if (profile.teamId.isNotEmpty()) {
            jobs += listen("partidos", MatchRepository.teamMatchesFlow(academyId, profile.teamId)) { state, matches ->
                state.copy(matches = matches, matchesLoaded = true)
            }
            jobs += listen("plantel", PlayerRepository.teamPlayersFlow(academyId, profile.teamId)) { state, players ->
                state.copy(roster = players, rosterLoaded = true)
            }
        } else {
            // DT sin equipo: no hay partidos que mostrar
            _uiState.update { it.copy(matchesLoaded = true, rosterLoaded = true) }
        }
        baseJobs = jobs
    }

    // Al cerrar sesión se deja de escuchar todo
    fun stop() {
        baseJobs.forEach { it.cancel() }
        baseJobs = emptyList()
        clearMatch()
        startedKey = ""
        _uiState.value = MatchUiState()
    }

    // Escucha una fuente; su error se guarda (o se quita) solo con su propia clave
    private fun <T> listen(source: String, flow: Flow<Result<T>>, onData: (MatchUiState, T) -> MatchUiState): Job =
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
            }
        }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    // ADM: filtro por equipo (null = Todos)
    fun setTeamFilter(teamId: String?) {
        _uiState.update { it.copy(teamFilter = teamId) }
    }

    // ---------- Detalle de un partido ----------
    fun selectMatch(matchId: String) {
        if (matchId == _uiState.value.selectedMatchId && detailJobs.isNotEmpty()) return
        clearMatch()
        val isCoach = _uiState.value.isCoach
        _uiState.update { it.copy(selectedMatchId = matchId) }
        detailJobs = listOfNotNull(
            listen("partido", MatchRepository.matchFlow(matchId)) { state, match -> state.copy(match = match) },
            listen("convocados", MatchRepository.callupsFlow(matchId)) { state, callups ->
                // Quien ya quedó convocado deja de estar en la selección
                state.copy(callups = callups, callupsLoaded = true, selection = state.selection - callups.map { it.playerId }.toSet())
            },
            listen("alineación", MatchRepository.lineupFlow(matchId)) { state, lineup ->
                state.copy(lineup = lineup, lineupLoaded = true)
            },
            // PRIVACIDAD: el motivo de "No puedo" solo lo lee el DT del equipo
            if (isCoach) {
                listen("motivos", MatchRepository.reasonsFlow(matchId)) { state, reasons ->
                    state.copy(reasons = reasons.filter { it.reason.isNotBlank() }.associate { it.playerId to it.reason })
                }
            } else null
        )
    }

    fun clearMatch() {
        detailJobs.forEach { it.cancel() }
        detailJobs = emptyList()
        _uiState.update {
            it.copy(
                selectedMatchId = "",
                match = null,
                callups = emptyList(),
                callupsLoaded = false,
                reasons = emptyMap(),
                selection = emptySet(),
                confirmShortNotice = false,
                lineup = null,
                lineupLoaded = false,
                lineupDraft = emptyMap(),
                lineupDraftMatchId = "",
                lineupErrors = emptyList(),
                pendingNotice = null,
                loadErrors = it.loadErrors - "partido" - "convocados" - "motivos" - "alineación"
            )
        }
    }

    // ---------- Nuevo partido (DT) ----------
    fun startNewMatch() {
        _uiState.update { it.copy(form = MatchForm(), formErrors = MatchFormErrors(), errorMessage = null, infoMessage = null) }
    }

    fun onFormChange(form: MatchForm) {
        _uiState.update { it.copy(form = form, formErrors = MatchFormErrors(), errorMessage = null) }
    }

    fun createMatch(onCreated: (String) -> Unit) {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (!state.isCoach || state.isLoading) return   // solo el DT; evita el doble toque
        if (profile.teamId.isEmpty()) {
            _uiState.update { it.copy(errorMessage = NO_TEAM_MESSAGE) }
            return
        }
        val errors = validateMatchForm(state.form)
        _uiState.update { it.copy(formErrors = errors) }
        if (errors.hasErrors) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = MatchRepository.createMatch(profile, state.form)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "¡Partido creado! Ahora elige a los convocados." else null
                )
            }
            result.getOrNull()?.let(onCreated)
        }
    }

    // ---------- Convocatoria (DT) ----------

    // Marcar o desmarcar a un jugador. Antes de publicar se marca y desmarca libremente;
    // después de publicar solo se puede AGREGAR (quien ya está convocado no se quita).
    fun toggleSelection(playerId: String) {
        if (playerId in _uiState.value.calledIds) return
        _uiState.update {
            it.copy(
                selection = if (playerId in it.selection) it.selection - playerId else it.selection + playerId,
                errorMessage = null
            )
        }
    }

    fun dismissShortNotice() {
        _uiState.update { it.copy(confirmShortNotice = false) }
    }

    // "Publicar convocatoria" / "Agregar a la convocatoria".
    // confirmed = el DT ya aceptó el aviso "Falta menos de X h para el partido. ¿Publicar igual?" (RN-008).
    fun publishCallup(confirmed: Boolean = false) {
        val state = _uiState.value
        val match = state.match ?: return
        if (!state.canManageMatch || state.isLoading || !state.callupsLoaded) return
        if (!match.isOpen()) {
            _uiState.update { it.copy(errorMessage = MatchRepository.MATCH_CLOSED_MESSAGE, confirmShortNotice = false) }
            return
        }
        val players = state.activeRoster.filter { it.id in state.selection && it.id !in state.calledIds }
        val firstTime = match.status == MatchStatus.PROGRAMMED
        // Sin selección nueva solo se puede "Publicar" un partido aún Programado que ya tiene convocados (reintento)
        if (players.isEmpty() && !(firstTime && state.callups.isNotEmpty())) {
            _uiState.update { it.copy(errorMessage = "Marca al menos un jugador para convocar.", infoMessage = null) }
            return
        }
        if (!confirmed && match.isShortNotice(state.noticeHours)) {
            _uiState.update { it.copy(confirmShortNotice = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, confirmShortNotice = false, errorMessage = null, infoMessage = null) }
            val result = MatchRepository.publishCallup(match, players)
            // "Tienes una nueva convocatoria" a TODOS los convocados que aún no tengan su aviso
            // (los que ya lo tienen se saltan: el ID del aviso es fijo)
            val noticeError = result.getOrNull()?.let { created ->
                val allCallups = (state.callups + created).distinctBy { it.playerId }
                notify(recipientsOf(allCallups), newCallupNotice(match))
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    selection = if (result.isSuccess) emptySet() else it.selection,
                    errorMessage = result.exceptionOrNull()?.let { e ->
                        "${e.message} Puedes volver a intentarlo: no se repiten convocados ni avisos."
                    } ?: noticeError,
                    infoMessage = when {
                        result.isFailure -> null
                        firstTime -> "¡Convocatoria publicada! Aquí verás las respuestas en tiempo real."
                        players.size == 1 -> "Agregamos a ${players.first().name} a la convocatoria."
                        else -> "Agregamos ${players.size} jugadores a la convocatoria."
                    }
                )
            }
        }
    }

    // Escribe una notificación para cada destinatario. La acción principal ya quedó guardada:
    // si el aviso falla, se guarda como pendiente (botón "Reintentar avisos") y devuelve el mensaje
    // para mostrarlo (null si salió bien). El reintento no duplica: cada aviso tiene un ID fijo.
    private suspend fun notify(recipients: List<NoticeRecipient>, draft: NotificationDraft): String? {
        val profile = _uiState.value.profile ?: return null
        if (recipients.isEmpty()) return null
        val result = NotificationRepository.send(profile.academyId, recipients, draft, profile)
        _uiState.update { it.copy(pendingNotice = if (result.isFailure) PendingNotice(draft, recipients) else null) }
        return result.exceptionOrNull()?.let { "Se guardó, pero no pudimos enviar los avisos: ${it.message} Toca Reintentar avisos." }
    }

    // "Reintentar avisos": vuelve a enviar los avisos que fallaron (los que ya llegaron se saltan)
    fun retryNotices() {
        val state = _uiState.value
        val pending = state.pendingNotice ?: return
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val error = notify(pending.recipients, pending.draft)
            _uiState.update {
                it.copy(isLoading = false, errorMessage = error, infoMessage = if (error == null) "¡Listo! Enviamos los avisos." else null)
            }
        }
    }

    // ---------- Alineación (DT, hasta el inicio) ----------

    // Llena el borrador con la alineación guardada (solo confirmados) cuando ya llegaron los datos.
    // También sirve si Android cerró la app: se vuelve a llenar con el partido de la ruta.
    fun ensureLineupDraft() {
        val state = _uiState.value
        if (state.selectedMatchId.isEmpty() || state.lineupDraftMatchId == state.selectedMatchId) return
        if (!state.lineupLoaded || !state.callupsLoaded) return
        val confirmed = state.confirmedCallups.map { it.playerId }.toSet()
        val saved = state.lineup
        val draft = buildMap {
            saved?.starters?.filter { it.playerId in confirmed }?.forEach { put(it.playerId, LineupSlot(starter = true, position = it.position)) }
            saved?.substitutes?.filter { it.playerId in confirmed }?.forEach { put(it.playerId, LineupSlot(starter = false, position = "")) }
        }
        _uiState.update { it.copy(lineupDraft = draft, lineupDraftMatchId = state.selectedMatchId, lineupErrors = emptyList()) }
    }

    fun discardLineupDraft() {
        _uiState.update { it.copy(lineupDraft = emptyMap(), lineupDraftMatchId = "", lineupErrors = emptyList()) }
    }

    // Titular (starter = true), suplente (false) o fuera de la alineación (null).
    // Un titular empieza con la posición principal del jugador.
    fun setLineupRole(playerId: String, starter: Boolean?) {
        _uiState.update { state ->
            val draft = when (starter) {
                null -> state.lineupDraft - playerId
                false -> state.lineupDraft + (playerId to LineupSlot(starter = false, position = ""))
                true -> {
                    val current = state.lineupDraft[playerId]?.position.orEmpty()
                    val position = current.ifEmpty {
                        state.roster.firstOrNull { it.id == playerId }?.positionMain?.takeIf { it in PLAYER_POSITIONS } ?: PLAYER_POSITIONS[4]
                    }
                    state.lineupDraft + (playerId to LineupSlot(starter = true, position = position))
                }
            }
            state.copy(lineupDraft = draft, lineupErrors = emptyList(), errorMessage = null)
        }
    }

    fun setLineupPosition(playerId: String, position: String) {
        _uiState.update { state ->
            val slot = state.lineupDraft[playerId]?.takeIf { it.starter } ?: return@update state
            state.copy(lineupDraft = state.lineupDraft + (playerId to slot.copy(position = position)), lineupErrors = emptyList())
        }
    }

    fun saveLineup(onSaved: () -> Unit) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val match = state.match ?: return
        if (!state.canManageMatch || state.isLoading) return
        if (!match.isOpen()) {
            _uiState.update { it.copy(errorMessage = MatchRepository.MATCH_CLOSED_MESSAGE) }
            return
        }
        val errors = validateLineup(state.draftStarters, state.draftSubstitutes, state.maxStarters, state.maxSubstitutes)
        _uiState.update { it.copy(lineupErrors = errors) }
        if (errors.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = MatchRepository.saveLineup(match.id, Lineup(state.draftStarters, state.draftSubstitutes), profile)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "¡Alineación guardada!" else null
                )
            }
            if (result.isSuccess) onSaved()
        }
    }

    // ---------- Reprogramar y cancelar (DT, antes del inicio) ----------
    fun startReschedule() {
        val match = _uiState.value.match ?: return
        _uiState.update { it.copy(form = match.toForm(), formErrors = MatchFormErrors(), errorMessage = null, infoMessage = null) }
    }

    // Si Android cerró la app: el formulario se vuelve a llenar cuando llega el partido
    fun restoreRescheduleForm() {
        val state = _uiState.value
        if (state.match != null && state.form.id != state.match.id) startReschedule()
    }

    fun saveReschedule(onSaved: () -> Unit) {
        val state = _uiState.value
        val match = state.match ?: return
        if (!state.canManageMatch || state.isLoading) return
        val errors = validateMatchForm(state.form)
        _uiState.update { it.copy(formErrors = errors) }
        if (errors.hasErrors) return
        if (!match.isOpen()) {
            _uiState.update { it.copy(errorMessage = MatchRepository.MATCH_CLOSED_MESSAGE) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = MatchRepository.rescheduleMatch(match.id, state.form)
            // "Cambió el partido del <fecha anterior>" a los convocados (si ya hay convocatoria).
            // El ID lleva la hora del cambio: cada reprogramación avisa una vez.
            val previousStart = match.startsAt
            val newStart = matchStartsAt(state.form)
            val updatedAt = result.getOrNull()
            val noticeError = if (updatedAt != null && previousStart != null && newStart != null) {
                notify(recipientsOf(state.callups), matchRescheduledNotice(match, previousStart, newStart, updatedAt))
            } else null
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: noticeError,
                    infoMessage = if (result.isSuccess) "Partido reprogramado. Las respuestas de la convocatoria se mantienen." else null
                )
            }
            if (result.isSuccess) onSaved()
        }
    }

    fun cancelMatch() {
        val state = _uiState.value
        val match = state.match ?: return
        if (!state.canManageMatch || state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = MatchRepository.cancelMatch(match.id)
            // "Se canceló el partido del <fecha>" a los convocados
            val noticeError = if (result.isSuccess) notify(recipientsOf(state.callups), matchCancelledNotice(match)) else null
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: noticeError,
                    infoMessage = if (result.isSuccess) "Partido cancelado. No se borró nada: queda en Anteriores y cancelados." else null
                )
            }
        }
    }

    companion object {
        const val NO_TEAM_MESSAGE = "Aún no tienes un equipo asignado. Pide a la administración que te asigne uno."
    }
}
