package pe.edu.esan.sportpro.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import pe.edu.esan.sportpro.data.model.CHANGE_EVENT_ID
import pe.edu.esan.sportpro.data.model.EVENT_NOTE_MAX
import pe.edu.esan.sportpro.data.model.EventDraft
import pe.edu.esan.sportpro.data.model.EventNote
import pe.edu.esan.sportpro.data.model.EventSide
import pe.edu.esan.sportpro.data.model.EventStatus
import pe.edu.esan.sportpro.data.model.EventType
import pe.edu.esan.sportpro.data.model.MAX_CLOCK_MS
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchEvent
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.UserProfile
import java.util.Date
import java.util.UUID

// US-008 · ÚNICO repositorio de los eventos del partido: matches/{matchId}/events/{eventId}
// (también lo usarán US-009 Corrección, US-010 Tiempo real y US-011 Estadísticas).
//
// ¿Por qué los EVENTOS no usan transacciones ni esperan al servidor (a diferencia de MatchRepository)?
// En la cancha la señal se cae. Una transacción sin internet falla; un set()/WriteBatch, en cambio, se guarda
// al instante en la caché de Firestore del teléfono, aparece en la cronología y se envía SOLO al reconectar.
// Por eso recordEvent NO espera al servidor (no hay await): devuelve apenas queda guardado en el teléfono.
// Si el servidor lo rechaza después (reglas), se avisa con onRejected.
//
// El CRONÓMETRO (iniciar, pausar, reanudar y finalizar) SÍ exige conexión (ConnectionChecker): la regla compara
// lo acumulado con la hora del servidor (request.time), y un cambio guardado sin conexión llegaría con una hora
// distinta a la del momento en que se tocó el botón. Se escribe igual con WriteBatch (con conexión llega en segundos).
//
// Casos sucios (sección 7.5 del documento de requisitos) y cómo se cubren:
// - Doble toque: el ViewModel pide confirmación si se registra el mismo tipo en menos de 3 s
//   ("¿Registrar otra vez «Gol»?").
// - Reintento / reconexión: cada evento tiene un UUID creado en el teléfono ANTES de guardarlo y se escribe con
//   set() en ese ID. Si Firestore reenvía la escritura al reconectar, escribe el MISMO documento: no se duplica (RN-013).
// - Sin conexión: el evento queda en la caché con hasPendingWrites = true; la pantalla muestra
//   "X eventos pendientes de sincronizar" y la etiqueta "Sin sincronizar" en cada uno. Al reconectar se envían
//   solos, en el mismo orden en que se registraron.
// - Evento sin conexión que llega DESPUÉS del 'fin': un operador registra sin señal mientras otro finaliza con
//   señal. Al reconectar, el partido ya está Finalizado; la regla lo acepta igual (sin "Inserción posterior")
//   si su hora del teléfono (clientCreatedAt) es anterior o igual a finishedAt, es decir, si ocurrió antes del 'fin'.
//   Si el reloj del teléfono está adelantado, puede ser rechazado: se avisa y se puede volver a registrar como
//   "Insertar evento omitido".
// - Minuto tarde: el minuto se propone con el cronómetro y es editable; si el usuario lo BAJA (minuto menor que el
//   propuesto), el evento queda con late = true ("Registrado tarde").
// - Sin jugador: el registro continúa; solo si el tipo exige jugador (requiresPlayer) queda con
//   pendingIdentification = true ("Pendiente de identificar").
// - Inserción posterior: después del 'fin' se registra con postInsert = true ("Inserción posterior").
// - Dos operadores a la vez: cada evento es un documento propio con su UUID, así que dos registros simultáneos
//   nunca se pisan; ambos aparecen en los dos teléfonos por el listener. 'inicio' y 'fin' van en un lote con el
//   cambio de estado (y startEventId / endEventId): si dos personas lo tocan a la vez, el segundo lote lo rechazan
//   las reglas (el partido ya no está en el estado anterior) y se avisa.
// - Observación: va en un documento aparte (eventNotes) en el MISMO lote, para que solo la lean el DT,
//   los operadores y el ADM.
//
// Límites conocidos (puntos 10 a 13 de la revisión, aceptados; no se corrigen en esta historia):
// - Copia del tipo: la regla NO compara typeName, typeIcon ni typeColor con el catálogo (solo affectsScore).
//   No se agrega para no rechazar eventos registrados sin conexión si el ADM edita el catálogo mientras tanto.
// - Hora del teléfono: el minuto se calcula con la hora del teléfono contra clockRunningSince (hora del servidor).
//   Un teléfono con la hora desfasada puede mostrar otro minuto y desordenar la cronología entre operadores
//   (que se ordena con clientCreatedAt dentro del mismo minuto).
// - createdByName: el nombre completo del operador (un adulto de la academia) va en el evento y lo pueden leer los
//   miembros de la academia; la vista "En vivo" lo oculta a quien no es DT, operador o ADM. Se acepta porque no
//   sale de la academia.
// - Mismo gol registrado por dos operadores: cuenta dos veces (el aviso de los 3 segundos es local, de cada
//   teléfono). Se corrige anulando uno de los dos con la US-009.
// - Dorsal del rival: para el rival, escribir el dorsal ES el paso "jugador", así que son 4 o 5 toques
//   (tipo, Rival, 1 o 2 dígitos, Guardar) en lugar de 3. Con "Sin dorsal" son 3 toques.
object MatchEventRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun matchRef(matchId: String) = firestore.collection("matches").document(matchId)
    private fun eventsRef(matchId: String) = matchRef(matchId).collection("events")
    private fun notesRef(matchId: String) = matchRef(matchId).collection("eventNotes")

    const val REJECTED_MESSAGE =
        "El servidor no aceptó un registro: puede que el partido haya cambiado de estado o que ya no seas operador."
    const val CLOCK_NEEDS_CONNECTION_MESSAGE =
        "Para iniciar, pausar, reanudar o finalizar se necesita conexión. Los eventos sí puedes registrarlos sin conexión."

    // ID del evento: se crea ANTES de guardar (si se reintenta, se usa el mismo)
    fun newEventId(): String = UUID.randomUUID().toString()

    // ---------------------------------------------------------------- Lecturas (listeners)

    // Eventos del partido en tiempo real (US-010). Con MetadataChanges.INCLUDE también avisa cuando un evento
    // pendiente llega al servidor, para que el contador "pendientes de sincronizar" baje solo.
    // Sin internet sigue entregando lo último que tiene la caché (último estado conocido).
    fun eventsFlow(matchId: String): Flow<Result<List<MatchEvent>>> = callbackFlow {
        val registration = eventsRef(matchId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(Exception(FirebaseAuthManager.friendlyMessage(error))))
                snapshot != null -> trySend(Result.success(snapshot.documents.map { it.toEvent() }))
            }
        }
        awaitClose { registration.remove() }
    }

    // Observaciones (solo DT del equipo, operadores y ADM: las reglas no dejan leerlas a nadie más)
    fun notesFlow(matchId: String): Flow<Result<List<EventNote>>> =
        queryFlow(notesRef(matchId)) {
            EventNote(eventId = it.id, note = it.getString("note") ?: "", createdBy = it.getString("createdBy") ?: "")
        }

    // ---------------------------------------------------------------- Eventos (funcionan sin conexión)

    // Registra un evento del catálogo (no 'inicio' ni 'fin')
    fun recordEvent(
        match: Match,
        draft: EventDraft,
        by: UserProfile,
        eventId: String,
        onRejected: (String) -> Unit
    ): Result<Unit> = write(onRejected) { batch ->
        val note = draft.note.trim().take(EVENT_NOTE_MAX)
        batch.set(eventsRef(match.id).document(eventId), eventData(draft, by, note.isNotEmpty()))
        if (note.isNotEmpty()) batch.set(notesRef(match.id).document(eventId), noteData(note, by))
    }

    // ---------------------------------------------------------------- Cronómetro (necesita conexión)

    // 'Inicio': el partido pasa a "En curso" y el cronómetro arranca, en el MISMO lote que el evento
    fun startMatch(match: Match, startType: EventType, by: UserProfile, eventId: String, onRejected: (String) -> Unit): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return Result.failure(Exception(CLOCK_NEEDS_CONNECTION_MESSAGE))
        return write(onRejected) { batch ->
            batch.set(
                eventsRef(match.id).document(eventId),
                eventData(EventDraft(type = startType, minute = 1, proposedMinute = 1), by, false)
            )
            batch.update(
                matchRef(match.id),
                mapOf(
                    "status" to MatchStatus.LIVE,
                    "clockRunning" to true,
                    "clockRunningSince" to FieldValue.serverTimestamp(),
                    "clockElapsedMs" to 0L,
                    "startedAt" to FieldValue.serverTimestamp(),
                    "startEventId" to eventId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    // 'Fin': el partido pasa a "Finalizado" y el cronómetro se detiene, en el MISMO lote que el evento
    fun finishMatch(match: Match, endType: EventType, by: UserProfile, eventId: String, onRejected: (String) -> Unit): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return Result.failure(Exception(CLOCK_NEEDS_CONNECTION_MESSAGE))
        return write(onRejected) { batch ->
            val now = System.currentTimeMillis()
            val minute = match.currentMinute(now)
            batch.set(
                eventsRef(match.id).document(eventId),
                eventData(EventDraft(type = endType, minute = minute, proposedMinute = minute), by, false)
            )
            batch.update(
                matchRef(match.id),
                mapOf(
                    "status" to MatchStatus.FINISHED,
                    "clockRunning" to false,
                    "clockRunningSince" to null,
                    "clockElapsedMs" to minOf(match.clockMs(now), MAX_CLOCK_MS),
                    "finishedAt" to FieldValue.serverTimestamp(),
                    "endEventId" to eventId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    // Pausar: se guarda lo acumulado y el cronómetro queda detenido
    fun pauseClock(match: Match, onRejected: (String) -> Unit): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return Result.failure(Exception(CLOCK_NEEDS_CONNECTION_MESSAGE))
        return write(onRejected) { batch ->
            batch.update(
                matchRef(match.id),
                mapOf(
                    "clockRunning" to false,
                    "clockRunningSince" to null,
                    "clockElapsedMs" to minOf(match.clockMs(System.currentTimeMillis()), MAX_CLOCK_MS),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    // Reanudar: sigue contando desde lo acumulado (no cambia), con la hora del servidor
    fun resumeClock(match: Match, onRejected: (String) -> Unit): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return Result.failure(Exception(CLOCK_NEEDS_CONNECTION_MESSAGE))
        return write(onRejected) { batch ->
            batch.update(
                matchRef(match.id),
                mapOf(
                    "clockRunning" to true,
                    "clockRunningSince" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    // Arma el lote y lo envía SIN esperar al servidor: queda en la caché al instante y se sincroniza solo.
    // Si las reglas lo rechazan (al instante o al reconectar), Firestore lo quita de la caché y se avisa.
    private fun write(onRejected: (String) -> Unit, build: (WriteBatch) -> Unit): Result<Unit> =
        try {
            val batch = firestore.batch()
            build(batch)
            batch.commit().addOnFailureListener { e -> onRejected(rejectedMessage(e)) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }

    private fun rejectedMessage(e: Exception): String =
        if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) REJECTED_MESSAGE
        else FirebaseAuthManager.friendlyMessage(e)

    // Campos del evento (los mismos que valida firestore.rules)
    private fun eventData(draft: EventDraft, by: UserProfile, hasNote: Boolean): Map<String, Any?> {
        val type = draft.type
        val side = if (type.requiresTeam || type.requiresPlayer || type.affectsScore) draft.side else EventSide.NONE
        val local = side == EventSide.LOCAL
        val rival = side == EventSide.RIVAL
        val playerId = if (local) draft.playerId else ""
        val playerOutId = if (local && type.id == CHANGE_EVENT_ID) draft.playerOutId else ""
        val rivalNumber = if (rival) draft.rivalNumber else null
        // Pendiente de identificar: el tipo pide jugador y no se eligió (en un cambio, falta quien entra o quien sale)
        val pending = type.requiresPlayer && when {
            local -> playerId.isEmpty() || (type.id == CHANGE_EVENT_ID && playerOutId.isEmpty())
            rival -> rivalNumber == null
            else -> true
        }
        return mapOf(
            "typeId" to type.id,
            "typeName" to type.name,
            "typeIcon" to type.icon,
            "typeColor" to type.color,
            "affectsScore" to type.affectsScore,
            "minute" to draft.minute.toLong(),
            "side" to side,
            "playerId" to playerId,
            "playerName" to if (playerId.isNotEmpty()) draft.playerName.take(30) else "",
            "rivalNumber" to rivalNumber?.toLong(),
            "playerOutId" to playerOutId,
            "playerOutName" to if (playerOutId.isNotEmpty()) draft.playerOutName.take(30) else "",
            "hasNote" to hasNote,
            "pendingIdentification" to pending,
            // "Registrado tarde": solo si el usuario BAJÓ el minuto que propuso el cronómetro
            "late" to (!draft.postInsert && draft.minute < draft.proposedMinute),
            "postInsert" to draft.postInsert,
            "status" to EventStatus.VALID,
            "version" to 1L,
            "createdBy" to by.uid,
            // Igual al nombre del perfil (la regla lo compara con users/{uid}.name): no se recorta
            "createdByName" to by.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "clientCreatedAt" to Date()
        )
    }

    private fun noteData(note: String, by: UserProfile): Map<String, Any> = mapOf(
        "note" to note,
        "createdBy" to by.uid,
        "createdAt" to FieldValue.serverTimestamp()
    )

    // ---------------------------------------------------------------- Conversión

    private fun DocumentSnapshot.toEvent() = MatchEvent(
        id = id,
        typeId = getString("typeId") ?: "",
        typeName = getString("typeName") ?: "",
        typeIcon = getString("typeIcon") ?: "",
        typeColor = getString("typeColor") ?: "",
        affectsScore = getBoolean("affectsScore") ?: false,
        minute = getLong("minute")?.toInt() ?: 1,
        side = getString("side") ?: EventSide.NONE,
        playerId = getString("playerId") ?: "",
        playerName = getString("playerName") ?: "",
        rivalNumber = getLong("rivalNumber")?.toInt(),
        playerOutId = getString("playerOutId") ?: "",
        playerOutName = getString("playerOutName") ?: "",
        hasNote = getBoolean("hasNote") ?: false,
        pendingIdentification = getBoolean("pendingIdentification") ?: false,
        late = getBoolean("late") ?: false,
        postInsert = getBoolean("postInsert") ?: false,
        status = getString("status") ?: EventStatus.VALID,
        version = getLong("version")?.toInt() ?: 1,
        createdBy = getString("createdBy") ?: "",
        createdByName = getString("createdByName") ?: "",
        // ESTIMATE: mientras no llega al servidor se usa la hora estimada del teléfono
        createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate(),
        clientCreatedAt = getTimestamp("clientCreatedAt")?.toDate(),
        hasPendingWrites = metadata.hasPendingWrites()
    )
}
