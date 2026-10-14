package pe.edu.esan.sportpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.repository.MatchRepository
import pe.edu.esan.sportpro.data.repository.MatchRepositoryImpl
import pe.edu.esan.sportpro.domain.model.EventStatus
import pe.edu.esan.sportpro.domain.model.EventType
import pe.edu.esan.sportpro.domain.model.Match
import pe.edu.esan.sportpro.domain.model.MatchEvent
import pe.edu.esan.sportpro.domain.model.MatchStatus
import pe.edu.esan.sportpro.domain.model.TeamSide
import java.util.UUID

class MatchViewModel(
    private val repository: MatchRepository = MatchRepositoryImpl()
) : ViewModel() {

    val matches: StateFlow<List<Match>> = repository.matches
    val matchEvents: StateFlow<List<MatchEvent>> = repository.matchEvents
    val eventCatalog: StateFlow<List<EventType>> = repository.eventCatalog
    val isConnected: StateFlow<Boolean> = repository.isConnected

    private val _selectedMatchId = MutableStateFlow<String?>("match_1")
    val selectedMatchId: StateFlow<String?> = _selectedMatchId.asStateFlow()

    val activeMatch: StateFlow<Match?> = combine(matches, selectedMatchId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val activeMatchChronology: StateFlow<List<MatchEvent>> = combine(matchEvents, selectedMatchId) { events, id ->
        events.filter { it.matchId == id }.sortedByDescending { it.minute }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var timerJob: Job? = null

    init {
        startTimerJob()
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val currentMatch = activeMatch.value
                if (currentMatch != null && currentMatch.isTimerRunning && currentMatch.status == MatchStatus.EN_CURSO) {
                    val newSeconds = currentMatch.timerElapsedSeconds + 1
                    repository.updateMatchTimer(currentMatch.id, newSeconds, true)
                }
            }
        }
    }

    fun selectMatch(matchId: String) {
        _selectedMatchId.value = matchId
    }

    fun startMatch(matchId: String) {
        viewModelScope.launch {
            repository.updateMatchStatus(matchId, MatchStatus.EN_CURSO)
            val currentMatch = matches.value.find { it.id == matchId }
            val currentMinute = ((currentMatch?.timerElapsedSeconds ?: 0) / 60).toInt() + 1

            // Register 'Inicio' event if not registered
            val startEvent = MatchEvent(
                id = UUID.randomUUID().toString(),
                matchId = matchId,
                typeId = "type_inicio",
                typeName = "Inicio",
                affectsScore = false,
                minute = currentMinute,
                teamSide = TeamSide.LOCAL,
                status = EventStatus.VIGENTE
            )
            repository.registerEvent(startEvent)
            repository.updateMatchTimer(matchId, currentMatch?.timerElapsedSeconds ?: 0L, true)
            _successMessage.value = "Partido iniciado. Cronómetro en marcha."
        }
    }

    fun toggleTimer(matchId: String) {
        viewModelScope.launch {
            val currentMatch = matches.value.find { it.id == matchId } ?: return@launch
            val newRunning = !currentMatch.isTimerRunning
            repository.updateMatchTimer(matchId, currentMatch.timerElapsedSeconds, newRunning)
            _successMessage.value = if (newRunning) "Cronómetro reanudado." else "Cronómetro pausado."
        }
    }

    fun registerQuickEvent(
        matchId: String,
        eventType: EventType,
        teamSide: TeamSide,
        playerName: String? = null,
        dorsal: String? = null,
        observation: String = ""
    ) {
        viewModelScope.launch {
            val currentMatch = matches.value.find { it.id == matchId }
            val currentMinute = (((currentMatch?.timerElapsedSeconds ?: 0) / 60).toInt() + 1).coerceAtLeast(1)

            val event = MatchEvent(
                id = UUID.randomUUID().toString(),
                matchId = matchId,
                typeId = eventType.id,
                typeName = eventType.name,
                affectsScore = eventType.affectsScore,
                minute = currentMinute,
                teamSide = teamSide,
                playerName = playerName ?: if (teamSide == TeamSide.LOCAL) "Jugador" else "Rival",
                playerDorsal = dorsal,
                observation = observation,
                status = EventStatus.VIGENTE
            )

            repository.registerEvent(event)
            _successMessage.value = "Evento '${eventType.name}' registrado en minuto $currentMinute'."
        }
    }

    fun annulEvent(eventId: String) {
        viewModelScope.launch {
            val result = repository.annulEvent(eventId)
            result.onSuccess {
                _successMessage.value = "Evento anulado. El marcador se ha recalculado."
            }.onFailure { ex ->
                _errorMessage.value = ex.message
            }
        }
    }

    fun finishMatch(matchId: String) {
        viewModelScope.launch {
            val currentMatch = matches.value.find { it.id == matchId }
            val currentMinute = (((currentMatch?.timerElapsedSeconds ?: 0) / 60).toInt() + 1).coerceAtLeast(1)

            // Register 'Fin' event
            val endEvent = MatchEvent(
                id = UUID.randomUUID().toString(),
                matchId = matchId,
                typeId = "type_fin",
                typeName = "Fin",
                affectsScore = false,
                minute = currentMinute,
                teamSide = TeamSide.LOCAL,
                status = EventStatus.VIGENTE
            )
            repository.registerEvent(endEvent)
            repository.updateMatchStatus(matchId, MatchStatus.FINALIZADO)
            repository.updateMatchTimer(matchId, currentMatch?.timerElapsedSeconds ?: 0L, false)
            _successMessage.value = "Partido finalizado."
        }
    }

    fun toggleConnectionStatus() {
        viewModelScope.launch {
            val nowConnected = repository.toggleConnectionStatus()
            _successMessage.value = if (nowConnected) "Conexión restablecida. Sincronizando eventos..." else "Modo sin conexión activado."
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    class Factory(private val repository: MatchRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MatchViewModel(repository) as T
        }
    }
}
