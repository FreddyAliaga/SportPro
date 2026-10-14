package pe.edu.esan.sportpro.domain.model

data class SessionExercise(
    val exerciseId: String = "",
    val exerciseName: String = "",
    val durationMinutes: Int = 15,
    val order: Int = 1
)

data class TrainingSession(
    val id: String = "",
    val academyId: String = "halcones_1",
    val teamId: String = "",
    val teamName: String = "",
    val date: String = "", // YYYY-MM-DD
    val startTime: String = "", // HH:mm
    val location: String = "",
    val objective: String = "",
    val exercises: List<SessionExercise> = emptyList(),
    val totalDurationMinutes: Int = exercises.sumOf { it.durationMinutes },
    val createdAt: Long = System.currentTimeMillis()
)
