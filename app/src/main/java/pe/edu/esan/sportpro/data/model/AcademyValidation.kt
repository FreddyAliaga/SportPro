package pe.edu.esan.sportpro.data.model

import android.util.Patterns

// Errores por campo del formulario de la academia
data class AcademyFormErrors(
    val name: String? = null,
    val city: String? = null,
    val institutionalEmail: String? = null,
    val callupNoticeHours: String? = null,
    val callupConfirmHours: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(name, city, institutionalEmail, callupNoticeHours, callupConfirmHours).any { it != null }
}

// Validación del formulario de la academia (la usan el registro y "Editar academia").
// Coincide con validAcademy de firestore.rules.
fun validateAcademyForm(form: AcademyForm): AcademyFormErrors {
    val notice = form.callupNoticeHours.toIntOrNull()
    val confirm = form.callupConfirmHours.toIntOrNull()
    return AcademyFormErrors(
        name = if (form.name.trim().length < 3) "Escribe el nombre de la academia." else null,
        city = if (form.city.isBlank()) "Escribe la ciudad." else null,
        institutionalEmail = if (!Patterns.EMAIL_ADDRESS.matcher(form.institutionalEmail.trim()).matches()) "Escribe un correo válido." else null,
        callupNoticeHours = if (notice == null || notice !in 1..168) "Entre 1 y 168 horas." else null,
        callupConfirmHours = when {
            confirm == null || confirm < 0 -> "Escribe las horas."
            notice != null && confirm >= notice -> "Debe ser menor que la anticipación."
            else -> null
        }
    )
}
