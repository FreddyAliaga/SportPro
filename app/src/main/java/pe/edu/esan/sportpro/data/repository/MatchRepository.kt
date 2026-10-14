package pe.edu.esan.sportpro.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.edu.esan.sportpro.domain.model.EventStatus
import pe.edu.esan.sportpro.domain.model.EventType
import pe.edu.esan.sportpro.domain.model.Match
import pe.edu.esan.sportpro.domain.model.MatchEvent
import pe.edu.esan.sportpro.domain.model.MatchStatus
import pe.edu.esan.sportpro.domain.model.TeamSide
import java.util.UUID

interface MatchRepository {
    val matches: StateFlow<List<Match>>
    val matchEvents: StateFlow<List<MatchEvent>>
    val eventCatalog: StateFlow<List<EventType>>
    val isConnected: StateFlow<Boolean>

    suspend fun registerEvent(event: MatchEvent): Result<MatchEvent>
    suspend fun annulEvent(eventId: String): Result<Unit>
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus): Result<Unit>
    suspend fun updateMatchTimer(matchId: String, elapsedSeconds: Long, isRunning: Boolean): Result<Unit>
    suspend fun toggleConnectionStatus(): Boolean
}

class MatchRepositoryImpl(
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
) : MatchRepository {

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _eventCatalog = MutableStateFlow(
        listOf(
            EventType("type_gol", "Gol", "sports_soccer", "#2E7D32", affectsScore = true, requiresPlayer = true),
            EventType("type_amarilla", "Tarjeta Amarilla", "square", "#FBC02D", affectsScore = false, requiresPlayer = true),
            EventType("type_roja", "Tarjeta Roja", "square", "#C62828", affectsScore = false, requiresPlayer = true),
            EventType("type_falta", "Falta", "sports_kabaddi", "#E65100", affectsScore = false, requiresPlayer = true),
            EventType("type_esquina", "Tiro de Esquina", "flag", "#006874", affectsScore = false, requiresPlayer = false),
            EventType("type_penal", "Penal", "sports", "#6A1B9A", affectsScore = false, requiresPlayer = true),
            EventType("type_cambio", "Cambio", "swap_horiz", "#1565C0", affectsScore = false, requiresPlayer = true),
            EventType("type_inicio", "Inicio", "play_arrow", "#006874", affectsScore = false, requiresPlayer = false),
            EventType("type_fin", "Fin", "stop", "#006874", affectsScore = false, requiresPlayer = false)
        )
    )
    override val eventCatalog: StateFlow<List<EventType>> = _eventCatalog.asStateFlow()

    private val _matchEvents = MutableStateFlow(
        listOf(
            MatchEvent(
                id = "evt_1",
                matchId = "match_1",
                typeId = "type_inicio",
                typeName = "Inicio",
                affectsScore = false,
                minute = 1,
                teamSide = TeamSide.LOCAL,
                status = EventStatus.VIGENTE
            ),
            MatchEvent(
                id = "evt_2",
                matchId = "match_1",
                typeId = "type_gol",
                typeName = "Gol",
                affectsScore = true,
                minute = 12,
                teamSide = TeamSide.LOCAL,
                playerId = "u_p1",
                playerName = "Mateo Rojas",
                playerDorsal = "10",
                status = EventStatus.VIGENTE
            ),
            MatchEvent(
                id = "evt_3",
                matchId = "match_1",
                typeId = "type_amarilla",
                typeName = "Tarjeta Amarilla",
                affectsScore = false,
                minute = 23,
                teamSide = TeamSide.LOCAL,
                playerId = "u_p2",
                playerName = "Gabriel Paredes",
                playerDorsal = "4",
                status = EventStatus.VIGENTE
            ),
            MatchEvent(
                id = "evt_4",
                matchId = "match_1",
                typeId = "type_gol",
                typeName = "Gol",
                affectsScore = true,
                minute = 35,
                teamSide = TeamSide.RIVAL,
                playerName = "Rival #7",
                playerDorsal = "7",
                status = EventStatus.VIGENTE
            ),
            MatchEvent(
                id = "evt_5",
                matchId = "match_1",
                typeId = "type_gol",
                typeName = "Gol",
                affectsScore = true,
                minute = 58,
                teamSide = TeamSide.LOCAL,
                playerId = "u_p3",
                playerName = "Lucas Medina",
                playerDorsal = "9",
                status = EventStatus.VIGENTE
            )
        )
    )
    override val matchEvents: StateFlow<List<MatchEvent>> = _matchEvents.asStateFlow()

    private val _matches = MutableStateFlow(
        listOf(
            Match(
                id = "match_1",
                academyId = "halcones_1",
                teamId = "team_sub12_a",
                teamName = "Sub-12 A",
                rivalName = "Águilas FC",
                date = "2026-03-20",
                time = "15:00",
                location = "Estadio Municipal",
                status = MatchStatus.EN_CURSO,
                localScore = 2,
                rivalScore = 1,
                timerElapsedSeconds = 4020L, // 67 minutes
                isTimerRunning = true
            ),
            Match(
                id = "match_2",
                academyId = "halcones_1",
                teamId = "team_sub10_a",
                teamName = "Sub-10 A",
                rivalName = "Deportivo Sur",
                date = "2026-03-22",
                time = "10:00",
                location = "Campo Auxiliar 2",
                status = MatchStatus.PROGRAMADO,
                localScore = 0,
                rivalScore = 0
            )
        )
    )
    override val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private fun recalculateScoresForMatch(matchId: String) {
        val validEvents = _matchEvents.value.filter { it.matchId == matchId && it.status == EventStatus.VIGENTE && it.affectsScore }
        val localCount = validEvents.count { it.teamSide == TeamSide.LOCAL }
        val rivalCount = validEvents.count { it.teamSide == TeamSide.RIVAL }

        val pendingCount = if (_isConnected.value) 0 else _matchEvents.value.count { it.isOfflinePending }

        _matches.value = _matches.value.map { m ->
            if (m.id == matchId) {
                m.copy(
                    localScore = localCount,
                    rivalScore = rivalCount,
                    pendingEventsCount = pendingCount
                )
            } else m
        }
    }

    override suspend fun registerEvent(event: MatchEvent): Result<MatchEvent> {
        val newId = if (event.id.isBlank()) UUID.randomUUID().toString() else event.id
        val finalEvent = event.copy(
            id = newId,
            isOfflinePending = !_isConnected.value
        )

        _matchEvents.value = _matchEvents.value + finalEvent
        recalculateScoresForMatch(finalEvent.matchId)

        if (_isConnected.value) {
            try {
                firestore?.collection("matches")
                    ?.document(finalEvent.matchId)
                    ?.collection("events")
                    ?.document(newId)
                    ?.set(finalEvent)
            } catch (e: Exception) {
                // Local fallback
            }
        }

        return Result.success(finalEvent)
    }

    override suspend fun annulEvent(eventId: String): Result<Unit> {
        val target = _matchEvents.value.find { it.id == eventId } ?: return Result.failure(IllegalArgumentException("Evento no encontrado"))

        val annulledEvent = target.copy(
            status = EventStatus.ANULADO,
            version = target.version + 1,
            isOfflinePending = !_isConnected.value
        )

        _matchEvents.value = _matchEvents.value.map { if (it.id == eventId) annulledEvent else it }
        recalculateScoresForMatch(target.matchId)

        return Result.success(Unit)
    }

    override suspend fun updateMatchStatus(matchId: String, status: MatchStatus): Result<Unit> {
        _matches.value = _matches.value.map { if (it.id == matchId) it.copy(status = status) else it }
        return Result.success(Unit)
    }

    override suspend fun updateMatchTimer(matchId: String, elapsedSeconds: Long, isRunning: Boolean): Result<Unit> {
        _matches.value = _matches.value.map {
            if (it.id == matchId) it.copy(timerElapsedSeconds = elapsedSeconds, isTimerRunning = isRunning) else it
        }
        return Result.success(Unit)
    }

    override suspend fun toggleConnectionStatus(): Boolean {
        val next = !_isConnected.value
        _isConnected.value = next

        if (next) {
            // Reconnected: sync pending offline events
            _matchEvents.value = _matchEvents.value.map { it.copy(isOfflinePending = false) }
            _matches.value = _matches.value.map { it.copy(pendingEventsCount = 0) }
        }

        return next
    }
}
