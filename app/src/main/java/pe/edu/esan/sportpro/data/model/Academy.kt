package pe.edu.esan.sportpro.data.model

import java.text.Normalizer

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

// Insignia del equipo: "Sub-12" -> "S12", "Primera" -> "1ra"; otra categoría -> sus 3 primeros caracteres
fun shortCategory(category: String): String {
    val sub = Regex("^Sub-(\\d+)$", RegexOption.IGNORE_CASE).find(category.trim())
    return when {
        sub != null -> "S${sub.groupValues[1]}"
        category.trim().equals("Primera", ignoreCase = true) -> "1ra"
        else -> category.trim().take(3)
    }
}

// Palabras que no cuentan para las iniciales de la academia ni para el prefijo de sus códigos de
// invitación (AcademyRepository.codePrefix). Se comparan sin tildes y en minúsculas.
val ACADEMY_TYPE_WORDS = setOf("academia", "club", "escuela", "fc", "cf")   // en las iniciales: se quitan siempre
val ACADEMY_ARTICLE_WORDS = setOf("los", "las", "la", "el", "de", "del")   // en las iniciales: solo si quedan 2 o más palabras

// Iniciales de la academia, en mayúscula y sin tilde:
// "Academia Los Cóndores" -> "LC", "El Unico Grande" -> "UG", "La U El más grande" -> "UM".
// Si queda una sola palabra, sus dos primeras letras; si no queda ninguna, se usan las palabras originales.
fun academyInitials(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    val withoutType = words.filter { withoutAccents(it).lowercase() !in ACADEMY_TYPE_WORDS }.ifEmpty { words }
    val withoutArticles = withoutType.filter { withoutAccents(it).lowercase() !in ACADEMY_ARTICLE_WORDS }
    val significant = if (withoutArticles.size >= 2) withoutArticles else withoutType
    val initials = when (significant.size) {
        0 -> "SP"
        1 -> significant[0].take(2)
        else -> significant.take(2).joinToString("") { it.first().toString() }
    }
    return withoutAccents(initials).uppercase()
}

private fun withoutAccents(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
