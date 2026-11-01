package pe.edu.esan.sportpro.data.model

import java.util.Date

// Base de la US-012: bandeja de notificaciones dentro de la app (sin FCM ni anuncios).
// notifications/{id}: la escribe la app de quien hace la acción, una por destinatario.
// PRIVACIDAD: los textos solo llevan equipo, rival y fecha del partido (nunca el motivo, datos del
// jugador ni su fecha de nacimiento). Toda notificación de un menor la recibe su apoderado (RN-003).
//
// ID fijo para que un reintento no duplique: {tipo}_{matchId}_{uid del destinatario}.
// La reprogramación y la respuesta llevan además la marca de tiempo del cambio (cada cambio avisa).
data class AppNotification(
    val id: String = "",
    val recipientUid: String = "",
    val academyId: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val matchId: String = "",
    val playerId: String = "",      // convocado por el que se avisa (la regla lo usa para revisar al destinatario)
    val read: Boolean = false,
    val createdAt: Date? = null
)

object NotificationType {
    const val CALLUP = "CONVOCATORIA"                  // al convocado (o su apoderado)
    const val CALLUP_RESPONSE = "RESPUESTA"            // al DT que creó el partido
    const val MATCH_RESCHEDULED = "PARTIDO_REPROGRAMADO"
    const val MATCH_CANCELLED = "PARTIDO_CANCELADO"
}

// Una notificación por escribir (la misma para todos sus destinatarios).
// idSuffix: marca de tiempo del cambio (reprogramación y respuesta); "" en las demás.
data class NotificationDraft(
    val type: String,
    val title: String,
    val body: String,
    val matchId: String,
    val idSuffix: String = ""
) {
    fun idFor(recipientUid: String): String =
        "${type}_${matchId}_$recipientUid" + if (idSuffix.isEmpty()) "" else "_$idSuffix"
}

// Un destinatario y el convocado por el que recibe el aviso
data class NoticeRecipient(val uid: String, val playerId: String)

const val NOTIFICATION_TITLE_MAX = 80
const val NOTIFICATION_BODY_MAX = 160

// ---------------------------------------------------------------- Textos (sin datos sensibles)

fun newCallupNotice(match: Match) = NotificationDraft(
    type = NotificationType.CALLUP,
    title = "Tienes una nueva convocatoria",
    body = "${match.teamName} vs ${match.rival}, ${match.startsAt?.let { "el ${it.toShortDateText()} a las ${it.toMatchTimeText()}" }.orEmpty()}. Responde en Convocatorias.",
    matchId = match.id
)

// respondedAtMillis = hora de la respuesta en el servidor (cada respuesta nueva avisa)
fun callupResponseNotice(match: Match, respondedAtMillis: Long) = NotificationDraft(
    type = NotificationType.CALLUP_RESPONSE,
    title = "Hay una nueva respuesta a tu convocatoria",
    body = "${match.teamName} vs ${match.rival}${match.startsAt?.let { ", ${it.toShortDateText()}" }.orEmpty()}. Mira el resumen en el partido.",
    matchId = match.id,
    idSuffix = respondedAtMillis.toString()
)

// previousStart = fecha antes del cambio (la que conocen las familias); updatedAtMillis = hora del cambio
fun matchRescheduledNotice(match: Match, previousStart: Date, newStart: Date, updatedAtMillis: Long) = NotificationDraft(
    type = NotificationType.MATCH_RESCHEDULED,
    title = "Cambió el partido del ${previousStart.toShortDateText()}",
    body = "${match.teamName} vs ${match.rival}: ahora es el ${newStart.toShortDateText()} a las ${newStart.toMatchTimeText()}. Revisa los detalles.",
    matchId = match.id,
    idSuffix = updatedAtMillis.toString()
)

fun matchCancelledNotice(match: Match) = NotificationDraft(
    type = NotificationType.MATCH_CANCELLED,
    title = "Se canceló el partido del ${match.startsAt?.toShortDateText().orEmpty()}",
    body = "${match.teamName} vs ${match.rival}.",
    matchId = match.id
)

// ---------------------------------------------------------------- Destinatarios (RN-003)

// Quién recibe lo que se dirige a un convocado: sus apoderados si es menor; él mismo si es mayor con cuenta.
// Se usa la copia del convocado porque es lo que revisa la regla de notifications.
fun recipientsOf(callup: Callup): List<NoticeRecipient> =
    (if (callup.isMinor) callup.guardianIds else listOfNotNull(callup.playerUserId.ifEmpty { null }))
        .map { NoticeRecipient(it, callup.playerId) }

// Destinatarios de varios convocados, sin repetir personas (un apoderado con dos hijos recibe un aviso)
fun recipientsOf(callups: List<Callup>): List<NoticeRecipient> =
    callups.flatMap { recipientsOf(it) }.distinctBy { it.uid }
