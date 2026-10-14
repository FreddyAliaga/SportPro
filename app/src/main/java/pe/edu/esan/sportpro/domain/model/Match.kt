package pe.edu.esan.sportpro.domain.model

data class Match(
    val id: String = "",
    val academyId: String = "halcones_1",
    val teamId: String = "",
    val teamName: String = "Sub-12 A",
    val rivalName: String = "Águilas FC",
    val date: String = "2026-03-20",
    val time: String = "15:00",
    val location: String = "Estadio Municipal",
    val status: MatchStatus = MatchStatus.EN_CURSO,
    val localScore: Int = 0,
    val rivalScore: Int = 0,
    val timerElapsedSeconds: Long = 0L,
    val isTimerRunning: Boolean = false,
    val pendingEventsCount: Int = 0
)
