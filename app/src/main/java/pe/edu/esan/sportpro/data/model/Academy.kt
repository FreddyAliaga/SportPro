package pe.edu.esan.sportpro.data.model

// Academia guardada en Firestore: academies/{academyId}
data class Academy(
    val id: String = "",
    val name: String = "",
    val city: String = "",
    val institutionalEmail: String = "",
    val logoBase64: String = "",                                  // "" si no tiene logo (máx. 150 KB comprimido)
    val callupNoticeHours: Int = DEFAULT_CALLUP_NOTICE_HOURS,     // RN-008: publicar la convocatoria con anticipación
    val callupConfirmHours: Int = DEFAULT_CALLUP_CONFIRM_HOURS,   // RN-008: confirmar hasta X horas antes
    val verified: Boolean = false,                                // solo se cambia desde la consola (RN-018)
    val adminUid: String = ""
)

// Datos que el ADM escribe en "Registra tu academia" / "Editar academia"
// (las horas se guardan como texto mientras se escriben, igual que RegisterForm)
data class AcademyForm(
    val name: String = "",
    val city: String = "",
    val institutionalEmail: String = "",
    val logoBase64: String = "",
    val callupNoticeHours: String = DEFAULT_CALLUP_NOTICE_HOURS.toString(),
    val callupConfirmHours: String = DEFAULT_CALLUP_CONFIRM_HOURS.toString()
) {
    // Usar solo después de validar el formulario (las horas ya son números)
    fun toAcademy(id: String = "") = Academy(
        id = id,
        name = name.trim(),
        city = city.trim(),
        institutionalEmail = institutionalEmail.trim(),
        logoBase64 = logoBase64,
        callupNoticeHours = callupNoticeHours.toIntOrNull() ?: DEFAULT_CALLUP_NOTICE_HOURS,
        callupConfirmHours = callupConfirmHours.toIntOrNull() ?: DEFAULT_CALLUP_CONFIRM_HOURS
    )
}

// Para "Editar academia": el formulario empieza con los datos actuales
fun Academy.toForm() = AcademyForm(
    name = name,
    city = city,
    institutionalEmail = institutionalEmail,
    logoBase64 = logoBase64,
    callupNoticeHours = callupNoticeHours.toString(),
    callupConfirmHours = callupConfirmHours.toString()
)

const val DEFAULT_CALLUP_NOTICE_HOURS = 24
const val DEFAULT_CALLUP_CONFIRM_HOURS = 2

// Equipo de la academia: academies/{academyId}/teams/{teamId}
// El DT NO se guarda aquí: es el usuario con role "DT", status "active" y teamId == id del equipo.
data class Team(
    val id: String = "",
    val name: String = "",
    val category: String = TEAM_CATEGORIES.first(),   // texto, ej. "Sub-12"
    val maxStarters: Int = 11,
    val maxSubstitutes: Int = 12,
    val active: Boolean = true                        // "Desactivar equipo" = false (no se borra)
)

// Categorías en el orden en que se listan los equipos (RN-004)
val TEAM_CATEGORIES = listOf("Sub-8", "Sub-10", "Sub-12", "Sub-15", "Sub-17", "Primera", "Otra")

// Posición de la categoría para ordenar; una categoría desconocida va al final
fun categoryOrder(category: String): Int =
    TEAM_CATEGORIES.indexOf(category).let { if (it < 0) TEAM_CATEGORIES.size else it }
