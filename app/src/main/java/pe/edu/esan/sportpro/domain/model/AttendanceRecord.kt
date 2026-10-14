package pe.edu.esan.sportpro.domain.model

enum class AttendanceStatus(val displayName: String) {
    PRESENTE("Presente"),
    TARDANZA("Tardanza"),
    AUSENTE("Ausente"),
    JUSTIFICADO("Justificado")
}

data class AttendanceRecord(
    val sessionId: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val status: AttendanceStatus = AttendanceStatus.PRESENTE,
    val registeredBy: String = "Entrenador",
    val registeredAt: Long = System.currentTimeMillis()
)

data class PlayerAttendanceSummary(
    val playerId: String = "",
    val playerName: String = "",
    val monthlyPercentage: Float = 100f,
    val totalSessions: Int = 0,
    val attendedCount: Int = 0,
    val lateCount: Int = 0,
    val absentCount: Int = 0,
    val justifiedCount: Int = 0
)
