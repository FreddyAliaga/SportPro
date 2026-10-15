package pe.edu.esan.sportpro.data.model

// Código de invitación guardado en Firestore: invitations/{codigo}
data class Invitation(
    val code: String = "",
    val academyId: String = "",
    val academyName: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val city: String = "",
    val role: String = "",          // "DT", "JUG" o "PAD"
    val playerId: String = "",      // solo si role = PAD (jugador que representa)
    val playerName: String = "",
    val invitedByName: String = ""
) {
    val roleName: String
        get() = when (role) {
            "DT" -> "entrenador"
            "JUG" -> "jugador"
            "PAD" -> "apoderado"
            else -> role.lowercase()
        }
}
