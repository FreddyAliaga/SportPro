package pe.edu.esan.sportpro.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Date
import java.util.Locale

// US-006 · Partido del equipo: matches/{matchId} (colección raíz, como players)
// Nada se borra: un partido se cancela con status "CANCELADO".
data class Match(
    val id: String = "",
    val academyId: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val rival: String = "",
    val startsAt: Date? = null,          // fecha y hora elegidas en la zona del teléfono (Timestamp)
    val place: String = "",
    val status: String = MatchStatus.PROGRAMMED,
    val createdBy: String = "",          // DT que creó el partido (recibe el aviso de cada respuesta)
    val callupPublishedAt: Date? = null
) {
    fun hasStarted(now: Long = System.currentTimeMillis()): Boolean = (startsAt?.time ?: 0L) <= now

    // Se puede convocar, armar la alineación, reprogramar o cancelar: antes del inicio y sin cancelar
    fun isOpen(now: Long = System.currentTimeMillis()): Boolean =
        status in listOf(MatchStatus.PROGRAMMED, MatchStatus.CALLED) && !hasStarted(now)

    // RN-008: se responde hasta callupConfirmHours antes del inicio
    fun confirmDeadline(confirmHours: Int): Date? =
        startsAt?.let { Date(it.time - confirmHours * HOUR_MS) }

    fun canRespond(confirmHours: Int, now: Long = System.currentTimeMillis()): Boolean =
        status == MatchStatus.CALLED && (confirmDeadline(confirmHours)?.time ?: 0L) >= now

    // RN-008: falta menos que el plazo de la academia para avisar la convocatoria
    fun isShortNotice(noticeHours: Int, now: Long = System.currentTimeMillis()): Boolean =
        (startsAt?.time ?: 0L) - now < noticeHours * HOUR_MS
}

// Valores del campo "status" (EN_CURSO y FINALIZADO los usará la US-008)
object MatchStatus {
    const val PROGRAMMED = "PROGRAMADO"
    const val CALLED = "CONVOCADO"
    const val LIVE = "EN_CURSO"
    const val FINISHED = "FINALIZADO"
    const val CANCELLED = "CANCELADO"

    fun label(status: String): String = when (status) {
        PROGRAMMED -> "Programado"
        CALLED -> "Convocado"
        LIVE -> "En curso"
        FINISHED -> "Finalizado"
        CANCELLED -> "Cancelado"
        else -> status
    }
}

// Convocado: matches/{matchId}/callups/{playerId}
// Las copias (isMinor, playerUserId, guardianIds) son solo para mostrar: las reglas usan el jugador real.
data class Callup(
    val playerId: String = "",
    val playerName: String = "",         // nombre corto "Mateo R."
    val isMinor: Boolean = true,
    val playerUserId: String = "",       // cuenta del jugador mayor ("" si no tiene)
    val guardianIds: List<String> = emptyList(),
    val response: String = CallupResponse.PENDING,
    val respondedBy: String = "",
    val respondedByName: String = "",
    val respondedAt: Date? = null
) {
    // Quién puede responder: el apoderado si es menor; el propio jugador si es mayor (RN-003)
    val hasResponder: Boolean
        get() = if (isMinor) guardianIds.isNotEmpty() else playerUserId.isNotEmpty()
}

object CallupResponse {
    const val PENDING = "SIN_CONFIRMAR"
    const val CONFIRMED = "CONFIRMADO"
    const val DECLINED = "NO_PUEDE"

    fun label(response: String): String = when (response) {
        CONFIRMED -> "Confirmado"
        DECLINED -> "No puede"
        else -> "Sin confirmar"
    }
}

// Motivo de "No puedo": matches/{matchId}/callupReasons/{playerId}. Solo lo ven el DT y quien lo escribió.
data class CallupReason(
    val playerId: String = "",
    val reason: String = "",
    val writtenBy: String = "",
    val at: Date? = null
)

const val CALLUP_REASON_MAX = 140

// Alineación: matches/{matchId}/lineup/current. Solo con convocados CONFIRMADOS (RN-009).
// Se modifica hasta el inicio; después los cambios serán eventos 'cambio' (US-008).
// PRIVACIDAD: la leen los miembros de la academia, por eso lleva el nombre corto ("Mateo R.").
data class Lineup(
    val starters: List<LineupStarter> = emptyList(),
    val substitutes: List<LineupSubstitute> = emptyList(),
    val updatedBy: String = "",
    val updatedAt: Date? = null
)

data class LineupStarter(val playerId: String = "", val playerName: String = "", val position: String = "")
data class LineupSubstitute(val playerId: String = "", val playerName: String = "")

// Lugar de un jugador en el borrador de la alineación (los que no están, no aparecen)
data class LineupSlot(val starter: Boolean, val position: String)

// Errores de la alineación (vacío = está bien). Un jugador no puede estar en las dos listas.
fun validateLineup(
    starters: List<LineupStarter>,
    substitutes: List<LineupSubstitute>,
    maxStarters: Int,
    maxSubstitutes: Int
): List<String> = buildList {
    if (starters.size > maxStarters) add("Máximo $maxStarters titulares.")
    if (substitutes.size > maxSubstitutes) add("Máximo $maxSubstitutes suplentes.")
    if (starters.none { it.position == GOALKEEPER }) add("Falta un portero entre los titulares.")
    val starterIds = starters.map { it.playerId }.toSet()
    if (substitutes.any { it.playerId in starterIds }) add("Un jugador no puede ser titular y suplente a la vez.")
}

val GOALKEEPER = PLAYER_POSITIONS.first()   // "Portero"

// Datos que el DT escribe en "Nuevo partido" / "Reprogramar"
data class MatchForm(
    val id: String = "",       // "" = partido nuevo
    val rival: String = "",
    val date: String = "",     // solo dígitos DDMMAAAA (las barras son visuales)
    val time: String = "",     // solo dígitos HHMM (los dos puntos son visuales)
    val place: String = ""
)

data class MatchFormErrors(
    val rival: String? = null,
    val date: String? = null,
    val time: String? = null,
    val place: String? = null
) {
    val hasErrors: Boolean get() = listOf(rival, date, time, place).any { it != null }
}

const val RIVAL_MIN = 2
const val RIVAL_MAX = 40
const val PLACE_MIN = 2
const val PLACE_MAX = 80

fun validateMatchForm(form: MatchForm, now: Long = System.currentTimeMillis()): MatchFormErrors {
    val date = parseMatchDate(form.date)
    val time = parseMatchTime(form.time)
    val startsAt = matchStartsAt(form)
    return MatchFormErrors(
        rival = if (form.rival.trim().length < RIVAL_MIN) "Escribe el nombre del rival." else null,
        date = when {
            date == null -> "Escribe una fecha válida (DD/MM/AAAA)."
            date.isBefore(LocalDate.now()) -> "La fecha ya pasó."
            else -> null
        },
        time = when {
            time == null -> "Escribe una hora válida (HH:MM, de 00:00 a 23:59)."
            date != null && startsAt != null && startsAt.time <= now -> "Esa hora ya pasó."
            else -> null
        },
        place = if (form.place.trim().length < PLACE_MIN) "Escribe el lugar del partido." else null
    )
}

private fun parseMatchDate(digits: String): LocalDate? {
    if (!Regex("^\\d{8}$").matches(digits)) return null
    return try {
        LocalDate.parse(digits, DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT))
    } catch (_: Exception) {
        null
    }
}

private fun parseMatchTime(digits: String): LocalTime? {
    if (!Regex("^\\d{4}$").matches(digits)) return null
    val hour = digits.take(2).toInt()
    val minute = digits.takeLast(2).toInt()
    return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}

// Fecha y hora del formulario en la zona del teléfono -> momento exacto (se guarda como Timestamp)
fun matchStartsAt(form: MatchForm): Date? {
    val date = parseMatchDate(form.date) ?: return null
    val time = parseMatchTime(form.time) ?: return null
    return Date.from(LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant())
}

// Para "Reprogramar": el formulario empieza con los datos actuales
fun Match.toForm(): MatchForm {
    val local = startsAt?.toInstant()?.atZone(ZoneId.systemDefault())
    return MatchForm(
        id = id,
        rival = rival,
        date = local?.format(DateTimeFormatter.ofPattern("ddMMyyyy")).orEmpty(),
        time = local?.format(DateTimeFormatter.ofPattern("HHmm")).orEmpty(),
        place = place
    )
}

private const val HOUR_MS = 60L * 60 * 1000
private val SPANISH = Locale.forLanguageTag("es-PE")

// Textos de fecha y hora (siempre en la zona del teléfono)
// "sábado 07/11/2026"
fun Date.toMatchDateText(): String =
    toInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", SPANISH))

// "16:30"
fun Date.toMatchTimeText(): String =
    toInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

// "07/11" (notificaciones: "Cambió el partido del 07/11")
fun Date.toShortDateText(): String =
    toInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM"))

// "sábado 07/11 a las 14:30" (plazo para responder)
fun Date.toDeadlineText(): String =
    toInstant().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE dd/MM 'a las' HH:mm", SPANISH))
