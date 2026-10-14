package pe.edu.esan.sportpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.repository.TrainingRepository
import pe.edu.esan.sportpro.data.repository.TrainingRepositoryImpl
import pe.edu.esan.sportpro.domain.model.AttendanceRecord
import pe.edu.esan.sportpro.domain.model.AttendanceStatus
import pe.edu.esan.sportpro.domain.model.Exercise
import pe.edu.esan.sportpro.domain.model.PlayerAttendanceSummary
import pe.edu.esan.sportpro.domain.model.SessionExercise
import pe.edu.esan.sportpro.domain.model.TrainingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrainingViewModel(
    private val repository: TrainingRepository = TrainingRepositoryImpl()
) : ViewModel() {

    val exercises: StateFlow<List<Exercise>> = repository.exercises
    val sessions: StateFlow<List<TrainingSession>> = repository.sessions
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = repository.attendanceRecords

    private val _selectedTeamIdFilter = MutableStateFlow<String?>(null)
    val selectedTeamIdFilter: StateFlow<String?> = _selectedTeamIdFilter.asStateFlow()

    val filteredSessions: StateFlow<List<TrainingSession>> = combine(sessions, selectedTeamIdFilter) { list, filter ->
        if (filter.isNullOrBlank()) list else list.filter { it.teamId == filter }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    val filteredExercises: StateFlow<List<Exercise>> = combine(exercises, selectedCategoryFilter) { list, cat ->
        if (cat.isNullOrBlank() || cat == "Todos") list else list.filter { it.category.equals(cat, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _selectedSessionForEdit = MutableStateFlow<TrainingSession?>(null)
    val selectedSessionForEdit: StateFlow<TrainingSession?> = _selectedSessionForEdit.asStateFlow()

    fun setTeamFilter(teamId: String?) {
        _selectedTeamIdFilter.value = teamId
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun selectSessionForEdit(session: TrainingSession?) {
        _selectedSessionForEdit.value = session
    }

    fun saveExercise(
        id: String = "",
        name: String,
        objective: String,
        description: String,
        durationMinutes: Int,
        category: String,
        materials: List<String>
    ) {
        if (name.isBlank()) {
            _errorMessage.value = "El nombre del ejercicio es obligatorio."
            return
        }

        val exercise = Exercise(
            id = id,
            name = name.trim(),
            objective = objective.trim(),
            description = description.trim(),
            durationMinutes = if (durationMinutes > 0) durationMinutes else 15,
            category = category,
            materials = materials
        )

        viewModelScope.launch {
            repository.saveExercise(exercise)
            _successMessage.value = "Ejercicio guardado en la biblioteca."
        }
    }

    fun deleteExercise(exerciseId: String) {
        viewModelScope.launch {
            repository.deleteExercise(exerciseId)
            _successMessage.value = "Ejercicio eliminado de la biblioteca."
        }
    }

    fun saveSession(
        id: String = "",
        teamId: String,
        teamName: String,
        date: String,
        startTime: String,
        location: String,
        objective: String,
        exercises: List<SessionExercise>,
        onSuccess: () -> Unit
    ) {
        if (teamId.isBlank()) {
            _errorMessage.value = "Debe seleccionar un equipo para la sesión."
            return
        }
        if (exercises.isEmpty()) {
            _errorMessage.value = "Debe agregar al menos un ejercicio de la biblioteca."
            return
        }

        val session = TrainingSession(
            id = id,
            teamId = teamId,
            teamName = teamName,
            date = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) },
            startTime = startTime.ifBlank { "16:00" },
            location = location.ifBlank { "Campo Principal" },
            objective = objective.trim(),
            exercises = exercises
        )

        viewModelScope.launch {
            repository.saveSession(session)
            _successMessage.value = "Sesión de entrenamiento guardada (${session.totalDurationMinutes} min)."
            onSuccess()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            _successMessage.value = "Sesión eliminada."
        }
    }

    fun recordAttendance(sessionId: String, playerId: String, playerName: String, status: AttendanceStatus) {
        viewModelScope.launch {
            repository.recordAttendance(sessionId, playerId, playerName, status)
            _successMessage.value = "Asistencia de $playerName registrada como ${status.displayName}."
        }
    }

    fun markAllPresent(sessionId: String, players: List<Pair<String, String>>) {
        viewModelScope.launch {
            val records = players.map { (id, name) -> Triple(id, name, AttendanceStatus.PRESENTE) }
            repository.recordBulkAttendance(sessionId, records)
            _successMessage.value = "Todos los jugadores marcados como Presente."
        }
    }

    fun isAttendanceWindowValid(dateStr: String, startTimeStr: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val sessionDate = format.parse("$dateStr $startTimeStr") ?: return true
            val now = Date()

            // 30 min before
            val minTime = sessionDate.time - (30 * 60 * 1000L)
            // 24 hours after
            val maxTime = sessionDate.time + (24 * 60 * 60 * 1000L)

            now.time in minTime..maxTime
        } catch (e: Exception) {
            true // Fallback valid
        }
    }

    fun getPlayerAttendanceSummary(playerId: String, playerName: String): PlayerAttendanceSummary {
        return repository.getPlayerAttendanceSummary(playerId, playerName)
    }

    fun getPlayerAttendanceHistory(playerId: String): List<AttendanceRecord> {
        return repository.getPlayerAttendanceHistory(playerId)
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    class Factory(private val repository: TrainingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TrainingViewModel(repository) as T
        }
    }
}
