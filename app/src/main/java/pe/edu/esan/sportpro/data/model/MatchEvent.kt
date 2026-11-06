package pe.edu.esan.sportpro.data.model

import java.util.Date

// US-008 · Evento del partido: matches/{matchId}/events/{eventId}
// - El ID es un UUID creado en el teléfono y se escribe con set(): un reintento no lo duplica (RN-013).
// - Copia nombre, ícono, color y "afecta el marcador" del tipo (RN-011): si el catálogo cambia después,
//   el evento se sigue viendo igual y el marcador no cambia.
// - Nunca se borra físicamente: la US-009 agregará la anulación (status "ANULADO") y las versiones.
// PRIVACIDAD: lo leen todos los miembros de la academia, por eso el jugador va con nombre corto ("Mateo R.")
// y la observación NO va aquí: va aparte en matches/{matchId}/eventNotes/{eventId} (solo DT, operadores y ADM).
data class MatchEvent(
    val id: String = "",
    val typeId: String = "",
    val typeName: String = "",
    val typeIcon: String = "",
    val typeColor: String = "",
    val affectsScore: Boolean = false,
    val minute: Int = 1,
    val side: String = EventSide.NONE,
    val playerId: String = "",           // "" = sin jugador (o es del rival)
    val playerName: String = "",         // nombre corto
    val rivalNumber: Int? = null,        // dorsal del rival (1–99); null = sin dorsal
    val playerOutId: String = "",        // solo 'cambio': jugador que sale
    val playerOutName: String = "",
    val hasNote: Boolean = false,        // tiene observación (en eventNotes)
    val pendingIdentification: Boolean = false,   // el tipo pide jugador y no se eligió
    val late: Boolean = false,           // el usuario bajó el minuto propuesto por el cronómetro
    val postInsert: Boolean = false,     // registrado después del 'fin' (evento omitido)
    val status: String = EventStatus.VALID,
    val version: Int = 1,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Date? = null,         // hora del servidor (estimada mientras no se sincroniza)
    val clientCreatedAt: Date? = null,   // hora del teléfono (ordena bien aunque no haya conexión)
    val hasPendingWrites: Boolean = false // solo en el teléfono: aún no llega al servidor
) {
    val isValid: Boolean get() = status == EventStatus.VALID

    // Quién aparece en la cronología: "Mateo R.", "Rival #7", "Sin identificar"…
    fun whoText(): String = when {
        typeId == CHANGE_EVENT_ID && side == EventSide.LOCAL ->
            "Entra ${playerName.ifEmpty { "sin identificar" }} · Sale ${playerOutName.ifEmpty { "sin identificar" }}"
        playerName.isNotEmpty() -> playerName
        side == EventSide.RIVAL -> rivalNumber?.let { "Rival #$it" } ?: "Rival"
        pendingIdentification -> "Sin identificar"
        else -> ""
    }
}

object EventSide {
    const val LOCAL = "LOCAL"   // nuestro equipo
    const val RIVAL = "RIVAL"
    const val NONE = ""         // el tipo no pide equipo (por ejemplo, 'inicio' y 'fin')
}

object EventStatus {
    const val VALID = "VIGENTE"   // la US-009 agregará "ANULADO"
}

// Observación de un evento: matches/{matchId}/eventNotes/{eventId}. Solo la leen el DT, los operadores y el ADM.
data class EventNote(
    val eventId: String = "",
    val note: String = "",
    val createdBy: String = ""
)

// Lo que el operador eligió antes de tocar "Guardar"
data class EventDraft(
    val type: EventType,
    val side: String = EventSide.NONE,
    val playerId: String = "",
    val playerName: String = "",
    val rivalNumber: Int? = null,
    val playerOutId: String = "",
    val playerOutName: String = "",
    val minute: Int = EVENT_MINUTE_MIN,
    // Minuto que propuso el cronómetro al empezar el registro. Si el usuario lo BAJA, queda "Registrado tarde".
    val proposedMinute: Int = EVENT_MINUTE_MIN,
    val note: String = "",
    val postInsert: Boolean = false
)

// Marcador: Nosotros x – y Rival
data class Score(val local: Int = 0, val rival: Int = 0)

const val START_EVENT_ID = "inicio"
const val END_EVENT_ID = "fin"
const val CHANGE_EVENT_ID = "cambio"
const val EVENT_MINUTE_MIN = 1
const val EVENT_MINUTE_MAX = 130
const val EVENT_NOTE_MAX = 140
const val RIVAL_NUMBER_MIN = 1
const val RIVAL_NUMBER_MAX = 99
const val MAX_OPERATORS = 5
const val DOUBLE_TAP_WINDOW_MS = 3_000L     // mismo tipo en menos de 3 s: se pide confirmación

// RN-012 · Marcador calculado en cada dispositivo: suma, por equipo, de los eventos VIGENTES
// que afectan el marcador. (Lo usan US-008, US-010, US-009 y US-011.)
fun scoreOf(events: List<MatchEvent>): Score {
    val counted = events.filter { it.isValid && it.affectsScore }
    return Score(
        local = counted.count { it.side == EventSide.LOCAL },
        rival = counted.count { it.side == EventSide.RIVAL }
    )
}

// Cronología: por minuto y, dentro del mismo minuto, por hora del teléfono.
// newestFirst = true pone los más nuevos arriba (pantalla de registro y vista en vivo).
fun timelineOf(events: List<MatchEvent>, newestFirst: Boolean = true): List<MatchEvent> {
    val ascending = events.sortedWith(
        compareBy<MatchEvent>({ it.minute }, { it.clientCreatedAt?.time ?: it.createdAt?.time ?: 0L }, { it.id })
    )
    return if (newestFirst) ascending.reversed() else ascending
}

// Un jugador para elegir al registrar (nombre corto)
data class PlayerPick(val playerId: String, val playerName: String)

// Jugadores en cancha y en la banca: la alineación + los 'cambio' VIGENTES de nuestro equipo, en orden.
// (El que sale pasa a la banca y el que entra pasa a la cancha.)
data class FieldPlayers(val onField: List<PlayerPick>, val bench: List<PlayerPick>)

fun fieldPlayersOf(lineup: Lineup, events: List<MatchEvent>): FieldPlayers {
    val names = (lineup.starters.map { it.playerId to it.playerName } + lineup.substitutes.map { it.playerId to it.playerName })
        .toMap().toMutableMap()
    val onField = lineup.starters.map { it.playerId }.toMutableList()
    val bench = lineup.substitutes.map { it.playerId }.toMutableList()
    timelineOf(events, newestFirst = false)
        .filter { it.isValid && it.typeId == CHANGE_EVENT_ID && it.side == EventSide.LOCAL }
        .forEach { change ->
            if (change.playerOutId.isNotEmpty() && onField.remove(change.playerOutId)) {
                bench.add(change.playerOutId)
                names.putIfAbsent(change.playerOutId, change.playerOutName)
            }
            if (change.playerId.isNotEmpty()) {
                bench.remove(change.playerId)
                if (change.playerId !in onField) onField.add(change.playerId)
                names.putIfAbsent(change.playerId, change.playerName)
            }
        }
    fun picks(ids: List<String>) = ids.distinct().map { PlayerPick(it, names[it].orEmpty()) }.sortedBy { it.playerName.lowercase() }
    return FieldPlayers(picks(onField), picks(bench.filter { it !in onField }))
}

// Eventos que aún no llegan al servidor ("X eventos pendientes de sincronizar")
fun pendingCount(events: List<MatchEvent>): Int = events.count { it.hasPendingWrites }

// Error del borrador (null si se puede guardar). La regla de Firestore lo vuelve a revisar.
fun validateEventDraft(draft: EventDraft): String? = when {
    draft.minute !in EVENT_MINUTE_MIN..EVENT_MINUTE_MAX -> "El minuto debe estar entre $EVENT_MINUTE_MIN y $EVENT_MINUTE_MAX."
    draft.note.trim().length > EVENT_NOTE_MAX -> "La observación tiene como máximo $EVENT_NOTE_MAX caracteres."
    draft.rivalNumber != null && draft.rivalNumber !in RIVAL_NUMBER_MIN..RIVAL_NUMBER_MAX ->
        "El dorsal debe estar entre $RIVAL_NUMBER_MIN y $RIVAL_NUMBER_MAX."
    draft.type.requiresTeam && draft.side !in listOf(EventSide.LOCAL, EventSide.RIVAL) -> "Elige el equipo."
    else -> null
}
