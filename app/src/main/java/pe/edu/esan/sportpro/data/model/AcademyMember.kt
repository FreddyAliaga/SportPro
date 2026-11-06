package pe.edu.esan.sportpro.data.model

// US-008 · Directorio mínimo de la academia: academies/{academyId}/members/{uid}
// Sirve para que el DT elija a los operadores del partido sin leer los perfiles completos (users/{uid}).
// PRIVACIDAD: solo nombre, rol y estado. Nunca correo, celular ni fecha de nacimiento.
// - Cada usuario activo escribe su propia ficha al iniciar sesión (así su nombre queda al día).
// - El ADM actualiza el estado al desactivar o reactivar una cuenta (US-002).
// - Solo la leen el DT y el ADM de la academia.
data class AcademyMember(
    val uid: String = "",
    val name: String = "",
    val role: String = "",
    val status: String = ""
) {
    val isActive: Boolean get() = status == AccountStatus.ACTIVE

    val roleName: String
        get() = when (role) {
            "ADM" -> "Administrador"
            "DT" -> "Entrenador"
            "JUG" -> "Jugador"
            "PAD" -> "Apoderado"
            else -> role
        }
}
