package pe.edu.esan.sportpro.data.model

import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Date

// US-003 · Validación de los formularios del jugador. Coincide con validPlayer y validPrivate de firestore.rules.

// Errores por campo de "Nuevo jugador" / "Editar datos deportivos"
data class PlayerSportErrors(
    val name: String? = null,
    val dorsal: String? = null,
    val birthDate: String? = null,
    val guardianAuthorization: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(name, dorsal, birthDate, guardianAuthorization).any { it != null }
}

// Errores por campo de los datos privados
data class PlayerPrivateErrors(
    val contactPhone: String? = null,
    val emergencyName: String? = null,
    val emergencyRelation: String? = null,
    val emergencyPhone: String? = null,
    val heightCm: String? = null,
    val weightKg: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(contactPhone, emergencyName, emergencyRelation, emergencyPhone, heightCm, weightKg).any { it != null }
}

// requireGuardianAuthorization: se registra a un menor (nuevo, o un mayor que pasa a menor)
// y hace falta la casilla "Cuento con la autorización del apoderado"
fun validatePlayerSportForm(form: PlayerSportForm, requireGuardianAuthorization: Boolean = false): PlayerSportErrors {
    val dorsal = form.dorsal.trim()
    val age = ageFromDigits(form.birthDate)
    return PlayerSportErrors(
        name = when {
            form.name.trim().length < 3 -> "Escribe el nombre completo del jugador."
            form.name.trim().length > 60 -> "Máximo 60 caracteres."
            else -> null
        },
        dorsal = if (dorsal.isNotEmpty() && dorsal.toIntOrNull() !in DORSAL_RANGE) "Entre 1 y 99 (o déjalo vacío)." else null,
        birthDate = when {
            age == null -> "Usa el formato DD/MM/AAAA."
            age !in 3..70 -> "Revisa la fecha de nacimiento."
            else -> null
        },
        guardianAuthorization = if (requireGuardianAuthorization && !form.guardianAuthorized) {
            PrivacyTexts.GUARDIAN_AUTHORIZATION_ERROR
        } else null
    )
}

fun validatePlayerPrivateForm(form: PlayerPrivateForm): PlayerPrivateErrors {
    val emergencyStarted = listOf(form.emergencyName, form.emergencyRelation, form.emergencyPhone).any { it.isNotBlank() }
    return PlayerPrivateErrors(
        contactPhone = phoneError(form.contactPhone, required = false),
        // El contacto de emergencia es opcional, pero si se empieza hay que completarlo (RN-005)
        emergencyName = when {
            emergencyStarted && form.emergencyName.trim().length < 3 -> "Escribe el nombre del contacto."
            form.emergencyName.trim().length > 60 -> "Máximo 60 caracteres."
            else -> null
        },
        emergencyRelation = when {
            emergencyStarted && form.emergencyRelation.isBlank() -> "Escribe el parentesco (ej. Madre)."
            form.emergencyRelation.trim().length > 30 -> "Máximo 30 caracteres."
            else -> null
        },
        emergencyPhone = phoneError(form.emergencyPhone, required = emergencyStarted),
        heightCm = rangeError(form.heightCm, HEIGHT_RANGE_CM, "La estatura debe estar entre 100 y 220 cm."),
        weightKg = rangeError(form.weightKg, WEIGHT_RANGE_KG, "El peso debe estar entre 20 y 150 kg.")
    )
}

// Celular peruano: 9 dígitos empezando con 9 (igual que en "Crea tu cuenta")
private fun phoneError(phone: String, required: Boolean): String? = when {
    phone.isBlank() -> if (required) "Escribe el celular." else null
    !Regex("^9\\d{8}$").matches(phone.trim()) -> "El celular debe tener 9 dígitos y empezar con 9."
    else -> null
}

// Campo opcional: vacío está bien; si se escribe, debe estar en el rango
private fun rangeError(value: String, range: IntRange, message: String): String? {
    if (value.isBlank()) return null
    return if (value.trim().toIntOrNull() in range) null else message
}

// ---------------------------------------------------------------- Edad

// Edad en años cumplidos a partir de los dígitos DDMMAAAA; fecha incompleta, inválida o futura -> null
fun ageFromDigits(digits: String): Int? {
    if (!Regex("^\\d{8}$").matches(digits)) return null
    return try {
        val formatter = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)
        val date = LocalDate.parse(digits, formatter)
        if (date.isAfter(LocalDate.now())) null else Period.between(date, LocalDate.now()).years
    } catch (_: Exception) {
        null
    }
}

// Edad de una fecha guardada en Firestore (al mediodía UTC: se convierte también con UTC)
fun ageOn(birthDate: Date): Int =
    Period.between(birthDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate(), LocalDate.now()).years

// RN-003: menor de edad = menos de 18 años cumplidos
fun isMinorAge(age: Int): Boolean = age < MIN_ACCOUNT_AGE

// Fecha guardada -> dígitos DDMMAAAA para el formulario
fun Date.toBirthDigits(): String =
    toInstant().atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern("ddMMyyyy"))

// Fecha guardada -> "14/03/2014" para mostrar
fun Date.toBirthText(): String =
    toInstant().atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

// Momento con hora (historial, autorización, vencimiento de códigos) -> "17/10/2026" en la zona del teléfono.
// No usar con la fecha de nacimiento (va al mediodía UTC: toBirthText).
fun Date.toLocalDateText(): String =
    toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

// "Mateo Rodríguez Paz" -> "Mateo R." (privacidad: el código lo puede leer cualquiera que lo tenga)
fun shortPlayerName(fullName: String): String {
    val words = fullName.trim().split(" ").filter { it.isNotBlank() }
    return when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> "${words[0]} ${words[1].first().uppercase()}."
    }.take(30)
}

// Cierra una frase con punto sin duplicarlo: "apoderado de Mateo R." (no "Mateo R..")
fun withFinalPeriod(text: String): String = if (text.endsWith(".")) text else "$text."
