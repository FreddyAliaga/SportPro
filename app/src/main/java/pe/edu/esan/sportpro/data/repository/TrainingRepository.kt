package pe.edu.esan.sportpro.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.edu.esan.sportpro.domain.model.AttendanceRecord
import pe.edu.esan.sportpro.domain.model.AttendanceStatus
import pe.edu.esan.sportpro.domain.model.Exercise
import pe.edu.esan.sportpro.domain.model.PlayerAttendanceSummary
import pe.edu.esan.sportpro.domain.model.SessionExercise
import pe.edu.esan.sportpro.domain.model.TrainingSession
import java.util.UUID

interface TrainingRepository {
    val exercises: StateFlow<List<Exercise>>
    val sessions: StateFlow<List<TrainingSession>>
    val attendanceRecords: StateFlow<List<AttendanceRecord>>

    suspend fun saveExercise(exercise: Exercise): Result<Exercise>
    suspend fun deleteExercise(exerciseId: String): Result<Unit>
    suspend fun saveSession(session: TrainingSession): Result<TrainingSession>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun recordAttendance(sessionId: String, playerId: String, playerName: String, status: AttendanceStatus): Result<Unit>
    suspend fun recordBulkAttendance(sessionId: String, records: List<Triple<String, String, AttendanceStatus>>): Result<Unit>
    fun getPlayerAttendanceHistory(playerId: String): List<AttendanceRecord>
    fun getPlayerAttendanceSummary(playerId: String, playerName: String): PlayerAttendanceSummary
}

class TrainingRepositoryImpl(
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
) : TrainingRepository {

    private val _exercises = MutableStateFlow(
        listOf(
            Exercise(
                id = "ex_1",
                academyId = "halcones_1",
                name = "Pases y Control Orientado",
                objective = "Mejorar la precisión del pase corto y primer toque",
                description = "Parejas a 5 metros realizando pases con borde interno y control orientado con pie opuesto.",
                durationMinutes = 20,
                category = "Técnica",
                materials = listOf("Balones", "Conos")
            ),
            Exercise(
                id = "ex_2",
                academyId = "halcones_1",
                name = "Circuito de Agilidad y Velocidad",
                objective = "Desarrollar explosividad y coordinación motora",
                description = "Pasos en escalera táctica, slalom entre estacas y sprint final de 10 metros.",
                durationMinutes = 15,
                category = "Física",
                materials = listOf("Escalera táctica", "Estacas", "Conos")
            ),
            Exercise(
                id = "ex_3",
                academyId = "halcones_1",
                name = "Posicionamiento y Cobertura Táctica",
                objective = "Coordinación de la línea defensiva en bloque medio",
                description = "Ejercicio 4v3 donde la línea defensiva debe bascular ante pases laterales.",
                durationMinutes = 25,
                category = "Táctica",
                materials = listOf("Petos de 2 colores", "Conos")
            ),
            Exercise(
                id = "ex_4",
                academyId = "halcones_1",
                name = "Definición y Tiro a Puerta",
                objective = "Remate tras pared y definición ante portero",
                description = "Pared en borde del área y remate de primera intención al arco.",
                durationMinutes = 20,
                category = "Técnica",
                materials = listOf("Balones", "Arcos")
            ),
            Exercise(
                id = "ex_5",
                academyId = "halcones_1",
                name = "Calentamiento Articular y Trote",
                objective = "Activación cardiovascular y movilidad dinámica",
                description = "Trote suave, movilidad articular de tren inferior y estiramientos dinámicos.",
                durationMinutes = 10,
                category = "Calentamiento",
                materials = listOf("Ninguno")
            )
        )
    )
    override val exercises: StateFlow<List<Exercise>> = _exercises.asStateFlow()

    private val _sessions = MutableStateFlow(
        listOf(
            TrainingSession(
                id = "sess_1",
                academyId = "halcones_1",
                teamId = "team_sub12_a",
                teamName = "Sub-12 A",
                date = "2026-03-15",
                startTime = "16:00",
                location = "Campo Principal 1",
                objective = "Técnica individual y circulación rápida de balón",
                exercises = listOf(
                    SessionExercise("ex_5", "Calentamiento Articular y Trote", 10, 1),
                    SessionExercise("ex_1", "Pases y Control Orientado", 20, 2),
                    SessionExercise("ex_4", "Definición y Tiro a Puerta", 20, 3)
                ),
                totalDurationMinutes = 50
            ),
            TrainingSession(
                id = "sess_2",
                academyId = "halcones_1",
                teamId = "team_sub10_a",
                teamName = "Sub-10 A",
                date = "2026-03-16",
                startTime = "17:00",
                location = "Campo Auxiliar B",
                objective = "Coordinación física y agilidad con balón",
                exercises = listOf(
                    SessionExercise("ex_5", "Calentamiento Articular y Trote", 10, 1),
                    SessionExercise("ex_2", "Circuito de Agilidad y Velocidad", 15, 2)
                ),
                totalDurationMinutes = 25
            )
        )
    )
    override val sessions: StateFlow<List<TrainingSession>> = _sessions.asStateFlow()

    private val _attendanceRecords = MutableStateFlow(
        listOf(
            AttendanceRecord("sess_1", "u_p1", "Mateo Rojas", AttendanceStatus.PRESENTE),
            AttendanceRecord("sess_1", "u_p2", "Gabriel Paredes", AttendanceStatus.TARDANZA),
            AttendanceRecord("sess_1", "u_p3", "Lucas Medina", AttendanceStatus.PRESENTE)
        )
    )
    override val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords.asStateFlow()

    override suspend fun saveExercise(exercise: Exercise): Result<Exercise> {
        val updated = if (exercise.id.isBlank()) {
            exercise.copy(id = "ex_" + UUID.randomUUID().toString().take(8))
        } else exercise

        _exercises.value = _exercises.value.filterNot { it.id == updated.id } + updated

        try {
            firestore?.collection("academies")
                ?.document(updated.academyId)
                ?.collection("exercises")
                ?.document(updated.id)
                ?.set(updated)
        } catch (e: Exception) {
            // Local fallback
        }

        return Result.success(updated)
    }

    override suspend fun deleteExercise(exerciseId: String): Result<Unit> {
        _exercises.value = _exercises.value.filterNot { it.id == exerciseId }
        return Result.success(Unit)
    }

    override suspend fun saveSession(session: TrainingSession): Result<TrainingSession> {
        val totalMins = session.exercises.sumOf { it.durationMinutes }
        val updatedSession = if (session.id.isBlank()) {
            session.copy(
                id = "sess_" + UUID.randomUUID().toString().take(8),
                totalDurationMinutes = totalMins
            )
        } else {
            session.copy(totalDurationMinutes = totalMins)
        }

        _sessions.value = _sessions.value.filterNot { it.id == updatedSession.id } + updatedSession

        try {
            firestore?.collection("academies")
                ?.document(updatedSession.academyId)
                ?.collection("sessions")
                ?.document(updatedSession.id)
                ?.set(updatedSession)
        } catch (e: Exception) {
            // Local fallback
        }

        return Result.success(updatedSession)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
        _attendanceRecords.value = _attendanceRecords.value.filterNot { it.sessionId == sessionId }
        return Result.success(Unit)
    }

    override suspend fun recordAttendance(
        sessionId: String,
        playerId: String,
        playerName: String,
        status: AttendanceStatus
    ): Result<Unit> {
        val newRecord = AttendanceRecord(
            sessionId = sessionId,
            playerId = playerId,
            playerName = playerName,
            status = status,
            registeredBy = "Entrenador",
            registeredAt = System.currentTimeMillis()
        )

        _attendanceRecords.value = _attendanceRecords.value
            .filterNot { it.sessionId == sessionId && it.playerId == playerId } + newRecord

        return Result.success(Unit)
    }

    override suspend fun recordBulkAttendance(
        sessionId: String,
        records: List<Triple<String, String, AttendanceStatus>>
    ): Result<Unit> {
        val newRecords = records.map { (playerId, playerName, status) ->
            AttendanceRecord(
                sessionId = sessionId,
                playerId = playerId,
                playerName = playerName,
                status = status,
                registeredBy = "Entrenador",
                registeredAt = System.currentTimeMillis()
            )
        }

        val updatedIds = newRecords.map { it.playerId }.toSet()
        val remaining = _attendanceRecords.value.filterNot { it.sessionId == sessionId && it.playerId in updatedIds }

        _attendanceRecords.value = remaining + newRecords
        return Result.success(Unit)
    }

    override fun getPlayerAttendanceHistory(playerId: String): List<AttendanceRecord> {
        return _attendanceRecords.value.filter { it.playerId == playerId }
    }

    override fun getPlayerAttendanceSummary(playerId: String, playerName: String): PlayerAttendanceSummary {
        val playerRecords = _attendanceRecords.value.filter { it.playerId == playerId }
        val total = playerRecords.size
        if (total == 0) {
            return PlayerAttendanceSummary(
                playerId = playerId,
                playerName = playerName,
                monthlyPercentage = 100f,
                totalSessions = 0
            )
        }

        val attended = playerRecords.count { it.status == AttendanceStatus.PRESENTE }
        val late = playerRecords.count { it.status == AttendanceStatus.TARDANZA }
        val absent = playerRecords.count { it.status == AttendanceStatus.AUSENTE }
        val justified = playerRecords.count { it.status == AttendanceStatus.JUSTIFICADO }

        // Percentage calculation: PRESENTE = 100%, TARDANZA = 75%, JUSTIFICADO = 100%, AUSENTE = 0%
        val weightedScore = (attended * 1.0f) + (late * 0.75f) + (justified * 1.0f)
        val pct = (weightedScore / total) * 100f

        return PlayerAttendanceSummary(
            playerId = playerId,
            playerName = playerName,
            monthlyPercentage = pct.coerceIn(0f, 100f),
            totalSessions = total,
            attendedCount = attended,
            lateCount = late,
            absentCount = absent,
            justifiedCount = justified
        )
    }
}
