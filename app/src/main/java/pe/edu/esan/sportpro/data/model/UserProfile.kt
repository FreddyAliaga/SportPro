package pe.edu.esan.sportpro.data.model

// Perfil de la persona que inició sesión (users/{uid})
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",          // "DT", "JUG", "PAD" o "ADM"
    val academyId: String = "",
    val academyName: String = "",
    val teamId: String = "",
    val playerId: String = "",      // US-003: perfil de jugador de un JUG (un PAD puede tener varios: no usar para listarlos)
    val status: String = ""
) {
    val firstName: String
        get() = name.trim().split(" ").firstOrNull().orEmpty()

    val initials: String
        get() = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }

    val roleName: String
        get() = when (role) {
            "ADM" -> "Administrador"
            "DT" -> "Entrenador"
            "JUG" -> "Jugador"
            "PAD" -> "Apoderado"
            else -> role
        }
}
