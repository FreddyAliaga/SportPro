package pe.edu.esan.sportpro.presentation.callup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.Academy
import pe.edu.esan.sportpro.data.model.Callup
import pe.edu.esan.sportpro.data.model.CallupResponse
import pe.edu.esan.sportpro.data.model.DEFAULT_CALLUP_CONFIRM_HOURS
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.NoticeRecipient
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.PrivacyTexts
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.remote.AcademyRepository
import pe.edu.esan.sportpro.data.model.callupResponseNotice
import pe.edu.esan.sportpro.data.remote.MatchRepository
import pe.edu.esan.sportpro.data.remote.NotificationRepository
import pe.edu.esan.sportpro.data.remote.PlayerRepository

// Una tarjeta de "Convocatorias": un partido y UNO de mis jugadores convocados
data class CallupCard(
    val match: Match,
    val player: Player,
    val callup: Callup,
    val myReason: String       // motivo que escribí ("" si no hay o lo escribió otra persona)
) {
    val key: String get() = callupKey(match.id, player.id)
}

private fun callupKey(matchId: String, playerId: String) = "$matchId/$playerId"

// Estado de "Convocatorias" (US-006) para el apoderado (una tarjeta por cada hijo convocado)
// y el jugador mayor (la suya)
data class CallupUiState(
    val profile: UserProfile? = null,
    val academy: Academy? = null,
    val players: List<Player> = emptyList(),
    val playersLoaded: Boolean = false,
    val matchesByTeam: Map<String, List<Match>> = emptyMap(),
    val callups: Map<String, Callup?> = emptyMap(),   // "matchId/playerId" -> convocatoria (null = no convocado)
    val reasons: Map<String, String> = emptyMap(),    // "matchId/playerId" -> mi motivo
    val loadErrors: Map<String, String> = emptyMap(),
    val respondingKey: String = "",                   // tarjeta que se está guardando
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val loadError: String? get() = loadErrors.values.firstOrNull()

    val confirmHours: Int get() = academy?.callupConfirmHours ?: DEFAULT_CALLUP_CONFIRM_HOURS

    // Ya se sabe qué mostrar: llegaron los jugadores y los partidos de sus equipos
    val loaded: Boolean
        get() = playersLoaded && players.map { it.teamId }.distinct().all { it in matchesByTeam }

    private val cards: List<CallupCard>
        get() = players.flatMap { player ->
            matchesByTeam[player.teamId].orEmpty().mapNotNull { match ->
                val key = callupKey(match.id, player.id)
                callups[key]?.let { CallupCard(match, player, it, reasons[key].orEmpty()) }
            }
        }

    // Próximas (aún no empiezan), la más cercana primero
    fun upcoming(now: Long): List<CallupCard> =
        cards.filter { !it.match.hasStarted(now) && it.match.status != MatchStatus.CANCELLED }
            .sortedBy { it.match.startsAt?.time ?: Long.MAX_VALUE }

    // Anteriores o canceladas, la más reciente primero
    fun past(now: Long): List<CallupCard> =
        cards.filter { it.match.hasStarted(now) || it.match.status == MatchStatus.CANCELLED }
            .sortedByDescending { it.match.startsAt?.time ?: 0L }
}

class CallupViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CallupUiState())
    val uiState: StateFlow<CallupUiState> = _uiState.asStateFlow()

    private var baseJobs: List<Job> = emptyList()
    private val teamJobs = mutableMapOf<String, Job>()      // teamId -> partidos del equipo
    private val callupJobs = mutableMapOf<String, Job>()    // "matchId/playerId" -> convocatoria
    private val reasonJobs = mutableMapOf<String, Job>()    // "matchId/playerId" -> mi motivo
    private var startedUid = ""

    // ---------- Inicio: apoderado (sus hijos) o jugador mayor (su perfil) ----------
    fun start(profile: UserProfile) {
        if (profile.role !in listOf("PAD", "JUG") || profile.academyId.isEmpty()) return
        if (profile.uid == startedUid && baseJobs.isNotEmpty()) {
            _uiState.update { it.copy(profile = profile) }
            return
        }
        stop()
        startedUid = profile.uid
        _uiState.value = CallupUiState(profile = profile)
        val academyJob = viewModelScope.launch {
            AcademyRepository.academyFlow(profile.academyId).collect { result ->
                _uiState.update { state -> state.withResult("academia", result) { copy(academy = it) } }
            }
        }
        val playersJob = viewModelScope.launch {
            // Apoderado: sus hijos (guardianIds). Jugador mayor: su propio perfil, como una lista de uno.
            val flow: Flow<Result<List<Player>>> = when {
                profile.role == "PAD" -> PlayerRepository.guardianPlayersFlow(profile.uid)
                profile.playerId.isNotEmpty() -> PlayerRepository.playerFlow(profile.playerId).map { r -> r.map { listOf(it) } }
                else -> flowOf(Result.success(emptyList()))
            }
            flow.collect { result ->
                _uiState.update { state ->
                    state.withResult("jugadores", result) { players ->
                        copy(players = players.filter { it.active }, playersLoaded = true)
                    }
                }
                syncTeams()
            }
        }
        baseJobs = listOf(academyJob, playersJob)
    }

    // Al cerrar sesión se deja de escuchar todo
    fun stop() {
        baseJobs.forEach { it.cancel() }
        baseJobs = emptyList()
        teamJobs.values.forEach { it.cancel() }
        teamJobs.clear()
        callupJobs.values.forEach { it.cancel() }
        callupJobs.clear()
        reasonJobs.values.forEach { it.cancel() }
        reasonJobs.clear()
        startedUid = ""
        _uiState.value = CallupUiState()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    // Guarda el dato o el error de una fuente (que una cargue bien no borra el error de otra)
    private fun <T> CallupUiState.withResult(source: String, result: Result<T>, onData: CallupUiState.(T) -> CallupUiState) =
        result.fold(
            onSuccess = { onData(it).copy(loadErrors = loadErrors - source) },
            onFailure = { copy(loadErrors = loadErrors + (source to (it.message ?: "No se pudo cargar la información."))) }
        )

    // Escucha los partidos de cada equipo de mis jugadores (un hijo puede estar en otro equipo)
    private fun syncTeams() {
        val state = _uiState.value
        val academyId = state.profile?.academyId ?: return
        val teamIds = state.players.map { it.teamId }.filter { it.isNotEmpty() }.toSet()
        (teamJobs.keys - teamIds).forEach { teamJobs.remove(it)?.cancel() }
        (teamIds - teamJobs.keys).forEach { teamId ->
            teamJobs[teamId] = viewModelScope.launch {
                MatchRepository.teamMatchesFlow(academyId, teamId).collect { result ->
                    _uiState.update { s ->
                        s.withResult("partidos", result) { matches -> copy(matchesByTeam = matchesByTeam + (teamId to matches)) }
                    }
                    syncCallups()
                }
            }
        }
        syncCallups()
    }

    // Para cada partido con convocatoria (no "Programado") de los últimos 30 días o futuro, y cada jugador
    // de ese equipo: escucha su convocatoria y su motivo (leídos por ID, sin consultas de grupo ni índices)
    private fun syncCallups() {
        val state = _uiState.value
        val since = System.currentTimeMillis() - RECENT_DAYS * 24L * 60 * 60 * 1000
        val wanted = state.players.flatMap { player ->
            state.matchesByTeam[player.teamId].orEmpty()
                .filter { it.status != MatchStatus.PROGRAMMED && (it.startsAt?.time ?: 0L) >= since }
                .map { match -> Triple(callupKey(match.id, player.id), match.id, player.id) }
        }
        val wantedKeys = wanted.map { it.first }.toSet()
        (callupJobs.keys - wantedKeys).forEach { callupJobs.remove(it)?.cancel() }
        (reasonJobs.keys - wantedKeys).forEach { reasonJobs.remove(it)?.cancel() }
        wanted.filter { it.first !in callupJobs }.forEach { (key, matchId, playerId) ->
            callupJobs[key] = viewModelScope.launch {
                MatchRepository.callupFlow(matchId, playerId).collect { result ->
                    _uiState.update { s -> s.withResult("convocatorias", result) { copy(callups = callups + (key to it)) } }
                }
            }
            listenReason(key, matchId, playerId)
        }
    }

    // Mi motivo. Si lo escribió otra persona (otro apoderado), las reglas no dejan leerlo: Firestore corta
    // ese listener, el motivo se muestra vacío y se vuelve a escuchar cuando ESTE usuario responde
    // (entonces el motivo vuelve a ser suyo).
    private fun listenReason(key: String, matchId: String, playerId: String) {
        reasonJobs.remove(key)?.cancel()
        val uid = _uiState.value.profile?.uid
        reasonJobs[key] = viewModelScope.launch {
            MatchRepository.reasonFlow(matchId, playerId).collect { result ->
                val reason = result.getOrNull()?.takeIf { it.writtenBy == uid }?.reason.orEmpty()
                _uiState.update { s -> s.copy(reasons = s.reasons + (key to reason)) }
            }
        }
    }

    // "Confirmo" / "No puedo" (con motivo opcional que solo ve el DT). Se puede cambiar dentro del plazo.
    fun respond(card: CallupCard, response: String, reason: String = "") {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (state.respondingKey.isNotEmpty()) return   // evita el doble toque
        if (!card.match.canRespond(state.confirmHours)) {
            _uiState.update { it.copy(errorMessage = "El plazo para responder ya terminó.", infoMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(respondingKey = card.key, errorMessage = null, infoMessage = null) }
            val result = MatchRepository.respond(card.match.id, card.player.id, response, reason, profile)
            // Ahora el motivo vuelve a ser mío: se vuelve a escuchar (por si antes lo había escrito otro apoderado)
            if (result.isSuccess) listenReason(card.key, card.match.id, card.player.id)
            // "Hay una nueva respuesta a tu convocatoria" al DT que creó el partido. El aviso no lleva el motivo
            // ni el nombre del jugador. Límite conocido: si la academia cambió de DT, la regla rechaza el aviso;
            // no se muestra error porque el DT ve las respuestas en tiempo real en el detalle del partido.
            val respondedAt = result.getOrNull()
            val noticeError = if (respondedAt != null && card.match.createdBy.isNotEmpty()) {
                NotificationRepository.send(
                    profile.academyId,
                    listOf(NoticeRecipient(card.match.createdBy, card.player.id)),
                    callupResponseNotice(card.match, respondedAt),
                    profile
                ).exceptionOrNull()
                    ?.takeIf { it.message != PrivacyTexts.PERMISSION_DENIED }
                    ?.let { "Tu respuesta se guardó, pero no pudimos avisar al DT: ${it.message}" }
            } else null
            _uiState.update {
                it.copy(
                    respondingKey = "",
                    errorMessage = result.exceptionOrNull()?.message ?: noticeError,
                    infoMessage = when {
                        result.isFailure -> null
                        response == CallupResponse.CONFIRMED -> "¡Gracias! Confirmaste a ${card.player.shortName} para el partido contra ${card.match.rival}."
                        else -> "Listo, avisamos al DT que ${card.player.shortName} no puede ir."
                    }
                )
            }
        }
    }

    private companion object {
        const val RECENT_DAYS = 30
    }
}
