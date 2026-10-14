package pe.edu.esan.sportpro.domain.model

enum class UserRole(val code: String, val displayName: String) {
    ADM("ADM", "Administrador"),
    DT("DT", "Entrenador"),
    JUG("JUG", "Jugador"),
    PAD("PAD", "Apoderado");

    companion object {
        fun fromCode(code: String): UserRole {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: DT
        }
    }
}
