package pe.edu.esan.sportpro.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import pe.edu.esan.sportpro.data.model.CALLUP_REASON_MAX
import pe.edu.esan.sportpro.data.model.Callup
import pe.edu.esan.sportpro.data.model.CallupReason
import pe.edu.esan.sportpro.data.model.CallupResponse
import pe.edu.esan.sportpro.data.model.Lineup
import pe.edu.esan.sportpro.data.model.LineupStarter
import pe.edu.esan.sportpro.data.model.LineupSubstitute
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchForm
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.matchStartsAt

// US-006 · Partido, convocatoria y alineación.
// Mismo estilo que PlayerRepository:
// - Lecturas: listeners (el DT ve las respuestas en tiempo real).
// - Escrituras: transacciones con await() y Result (sin internet fallan de inmediato).
// Nada se borra: un partido se cancela (status "CANCELADO") y los convocados no se quitan después de publicar.
object MatchRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private val matchesRef = firestore.collection("matches")
    private fun matchRef(matchId: String) = matchesRef.document(matchId)
    private fun callupsRef(matchId: String) = matchRef(matchId).collection("callups")
    private fun reasonsRef(matchId: String) = matchRef(matchId).collection("callupReasons")
    private fun lineupRef(matchId: String) = matchRef(matchId).collection("lineup").document("current")
    private fun teamRef(academyId: String, teamId: String) =
        firestore.collection("academies").document(academyId).collection("teams").document(teamId)

    // Convocados por transacción: la regla lee el jugador de cada convocado y Firestore permite
    // a lo mucho 20 lecturas por operación (perfil, equipo y partido + 8 jugadores = 11)
    private const val CALLUP_CHUNK = 8

    const val MATCH_CLOSED_MESSAGE = "Este partido ya empezó o fue cancelado: ya no se puede cambiar."

    // ---------------------------------------------------------------- Lecturas (listeners)

    // Partidos de un equipo (DT, apoderado y jugador). Dos filtros de igualdad: sin índice compuesto
    // (la app los ordena por fecha).
    fun teamMatchesFlow(academyId: String, teamId: String): Flow<Result<List<Match>>> =
        queryFlow(matchesRef.whereEqualTo("academyId", academyId).whereEqualTo("teamId", teamId)) { it.toMatch() }

    // Todos los partidos de la academia (ADM, solo lectura)
    fun academyMatchesFlow(academyId: String): Flow<Result<List<Match>>> =
        queryFlow(matchesRef.whereEqualTo("academyId", academyId)) { it.toMatch() }

    fun matchFlow(matchId: String): Flow<Result<Match>> =
        documentFlow(matchRef(matchId), "No encontramos este partido.") { it.toMatch() }

    // Convocados de un partido (DT y ADM)
    fun callupsFlow(matchId: String): Flow<Result<List<Callup>>> =
        queryFlow(callupsRef(matchId)) { it.toCallup() }

    // La convocatoria de UN jugador (apoderado o jugador mayor). null = no está convocado.
    fun callupFlow(matchId: String, playerId: String): Flow<Result<Callup?>> =
        optionalDocumentFlow(callupsRef(matchId).document(playerId)) { it.toCallup() }

    // Motivos de "No puedo" (solo el DT del equipo)
    fun reasonsFlow(matchId: String): Flow<Result<List<CallupReason>>> =
        queryFlow(reasonsRef(matchId)) { it.toReason() }

    // Alineación del partido (null = aún no se arma)
    fun lineupFlow(matchId: String): Flow<Result<Lineup?>> =
        optionalDocumentFlow(lineupRef(matchId)) { it.toLineup() }

    // Motivo que escribió quien responde (para mostrárselo). null = no hay.
    fun reasonFlow(matchId: String, playerId: String): Flow<Result<CallupReason?>> =
        optionalDocumentFlow(reasonsRef(matchId).document(playerId)) { it.toReason() }

    // ---------------------------------------------------------------- Crear partido (DT)

    // "Nuevo partido": empieza "Programado". El nombre del equipo se lee del equipo real
    // (la regla comprueba que coincida). Devuelve el ID del partido. Usar con el formulario ya validado.
    suspend fun createMatch(dt: UserProfile, form: MatchForm): Result<String> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val startsAt = matchStartsAt(form) ?: throw IllegalStateException("Revisa la fecha y la hora.")
            val ref = matchesRef.document()
            firestore.runTransaction { transaction ->
                val team = transaction.get(teamRef(dt.academyId, dt.teamId))
                if (team.getBoolean("active") != true) {
                    throw IllegalStateException("Tu equipo está desactivado. Pide ayuda a la administración.")
                }
                transaction.set(
                    ref,
                    hashMapOf(
                        "academyId" to dt.academyId,
                        "teamId" to dt.teamId,
                        "teamName" to (team.getString("name") ?: ""),
                        "rival" to form.rival.trim(),
                        "startsAt" to Timestamp(startsAt),
                        "place" to form.place.trim(),
                        "status" to MatchStatus.PROGRAMMED,
                        "createdBy" to dt.uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    // ---------------------------------------------------------------- Convocatoria (DT)

    // "Publicar convocatoria" o "Agregar a la convocatoria".
    // Crea los convocados en lotes de 8 (cada lote es una transacción). Quien YA estaba convocado no se toca
    // (así un reintento no borra respuestas ni duplica convocados).
    // Si el partido está "Programado", pasa a "Convocado" en la MISMA transacción del primer lote: nunca queda
    // un partido con convocados pero sin publicar. Con players vacío solo se publica (reintento).
    // Devuelve los convocados nuevos.
    suspend fun publishCallup(match: Match, players: List<Player>): Result<List<Callup>> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val created = mutableListOf<Callup>()
            // Sin jugadores nuevos: un solo "lote" vacío que solo cambia el estado (si sigue "Programado")
            val chunks = players.chunked(CALLUP_CHUNK).ifEmpty { listOf(emptyList()) }
            chunks.forEachIndexed { index, chunk ->
                val added = firestore.runTransaction { transaction ->
                    // En una transacción primero se lee todo y después se escribe
                    val matchSnapshot = transaction.get(matchRef(match.id))
                    checkOpen(matchSnapshot)
                    val existing = chunk.map { transaction.get(callupsRef(match.id).document(it.id)).exists() }
                    if (index == 0 && matchSnapshot.getString("status") == MatchStatus.PROGRAMMED) {
                        transaction.update(
                            matchRef(match.id),
                            mapOf(
                                "status" to MatchStatus.CALLED,
                                "callupPublishedAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    chunk.zip(existing).filter { !it.second }.map { (player, _) ->
                        val callup = Callup(
                            playerId = player.id,
                            playerName = player.shortName,
                            isMinor = player.isMinor,
                            playerUserId = player.userId,
                            guardianIds = player.guardianIds
                        )
                        transaction.set(
                            callupsRef(match.id).document(player.id),
                            hashMapOf(
                                "playerId" to callup.playerId,
                                "playerName" to callup.playerName,
                                "isMinor" to callup.isMinor,
                                "playerUserId" to callup.playerUserId,
                                "guardianIds" to callup.guardianIds,
                                "response" to CallupResponse.PENDING,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        callup
                    }
                }.await()
                created += added
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    // El partido sigue abierto: "Programado" o "Convocado" y aún no empieza (la regla lo vuelve a revisar)
    private fun checkOpen(snapshot: DocumentSnapshot) {
        if (!snapshot.exists() || !snapshot.toMatch().isOpen()) throw IllegalStateException(MATCH_CLOSED_MESSAGE)
    }

    // ---------------------------------------------------------------- Alineación, reprogramar y cancelar (DT)

    // Guarda la alineación (reemplaza la anterior). Usar solo con la alineación ya validada.
    suspend fun saveLineup(matchId: String, lineup: Lineup, by: UserProfile): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            firestore.runTransaction { transaction ->
                checkOpen(transaction.get(matchRef(matchId)))
                transaction.set(
                    lineupRef(matchId),
                    mapOf(
                        "starters" to lineup.starters.map {
                            mapOf("playerId" to it.playerId, "playerName" to it.playerName, "position" to it.position)
                        },
                        "substitutes" to lineup.substitutes.map {
                            mapOf("playerId" to it.playerId, "playerName" to it.playerName)
                        },
                        "updatedBy" to by.uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    // "Reprogramar": cambia fecha, hora o lugar antes del inicio. Las respuestas de la convocatoria se mantienen.
    // Devuelve la hora del cambio en el servidor (updatedAt, en milisegundos): forma parte del ID del aviso,
    // así cada reprogramación avisa una vez. Usar solo con el formulario ya validado.
    suspend fun rescheduleMatch(matchId: String, form: MatchForm): Result<Long> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val startsAt = matchStartsAt(form) ?: throw IllegalStateException("Revisa la fecha y la hora.")
            firestore.runTransaction { transaction ->
                checkOpen(transaction.get(matchRef(matchId)))
                transaction.update(
                    matchRef(matchId),
                    mapOf(
                        "startsAt" to Timestamp(startsAt),
                        "place" to form.place.trim(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(serverTime(matchRef(matchId), "updatedAt"))
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    // "Cancelar partido": status "CANCELADO". No se borra nada (convocados, respuestas y alineación se conservan).
    suspend fun cancelMatch(matchId: String): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            firestore.runTransaction { transaction ->
                checkOpen(transaction.get(matchRef(matchId)))
                transaction.update(
                    matchRef(matchId),
                    mapOf("status" to MatchStatus.CANCELLED, "updatedAt" to FieldValue.serverTimestamp())
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    // ---------------------------------------------------------------- Responder (apoderado o jugador mayor)

    // "Confirmo" o "No puedo" (con motivo opcional). En la misma operación se guarda el motivo APARTE,
    // en callupReasons, para que solo lo vea el DT. Al confirmar, el motivo queda vacío.
    // Devuelve la hora de la respuesta en el servidor (respondedAt, en milisegundos) para el ID del aviso al DT.
    suspend fun respond(matchId: String, playerId: String, response: String, reason: String, by: UserProfile): Result<Long> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val callupRef = callupsRef(matchId).document(playerId)
            firestore.runTransaction { transaction ->
                if (!transaction.get(callupRef).exists()) throw IllegalStateException("No encontramos esta convocatoria.")
                transaction.update(
                    callupRef,
                    mapOf(
                        "response" to response,
                        "respondedBy" to by.uid,
                        "respondedByName" to by.name.trim().take(60),
                        "respondedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.set(
                    reasonsRef(matchId).document(playerId),
                    mapOf(
                        "reason" to if (response == CallupResponse.DECLINED) reason.trim().take(CALLUP_REASON_MAX) else "",
                        "writtenBy" to by.uid,
                        "at" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(serverTime(callupRef, "respondedAt"))
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    // Hora del servidor que se acaba de guardar en un campo (si no se puede leer, la del teléfono)
    private suspend fun serverTime(ref: DocumentReference, field: String): Long =
        try {
            ref.get(Source.SERVER).await().getTimestamp(field)?.toDate()?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }

    // Los errores propios (IllegalStateException) ya están en español
    private fun friendly(e: Exception): String =
        if (e is IllegalStateException) e.message ?: "Ocurrió un error. Inténtalo de nuevo."
        else (e.cause as? IllegalStateException)?.message ?: FirebaseAuthManager.friendlyMessage(e)

    // ---------------------------------------------------------------- Conversión de documentos

    // ESTIMATE: justo al guardar, la hora del servidor aún no llega; se usa la hora estimada del teléfono
    private fun DocumentSnapshot.time(field: String) =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()

    private fun DocumentSnapshot.toMatch() = Match(
        id = id,
        academyId = getString("academyId") ?: "",
        teamId = getString("teamId") ?: "",
        teamName = getString("teamName") ?: "",
        rival = getString("rival") ?: "",
        startsAt = getTimestamp("startsAt")?.toDate(),
        place = getString("place") ?: "",
        status = getString("status") ?: MatchStatus.PROGRAMMED,
        createdBy = getString("createdBy") ?: "",
        callupPublishedAt = time("callupPublishedAt")
    )

    private fun DocumentSnapshot.toCallup() = Callup(
        playerId = id,
        playerName = getString("playerName") ?: "",
        isMinor = getBoolean("isMinor") ?: true,
        playerUserId = getString("playerUserId") ?: "",
        guardianIds = (get("guardianIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        response = getString("response") ?: CallupResponse.PENDING,
        respondedBy = getString("respondedBy") ?: "",
        respondedByName = getString("respondedByName") ?: "",
        respondedAt = time("respondedAt")
    )

    private fun DocumentSnapshot.toLineup() = Lineup(
        starters = (get("starters") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
            LineupStarter(
                playerId = it["playerId"] as? String ?: "",
                playerName = it["playerName"] as? String ?: "",
                position = it["position"] as? String ?: ""
            )
        },
        substitutes = (get("substitutes") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
            LineupSubstitute(playerId = it["playerId"] as? String ?: "", playerName = it["playerName"] as? String ?: "")
        },
        updatedBy = getString("updatedBy") ?: "",
        updatedAt = time("updatedAt")
    )

    private fun DocumentSnapshot.toReason() = CallupReason(
        playerId = id,
        reason = getString("reason") ?: "",
        writtenBy = getString("writtenBy") ?: "",
        at = time("at")
    )
}
