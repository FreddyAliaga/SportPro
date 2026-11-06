package pe.edu.esan.sportpro.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
import pe.edu.esan.sportpro.data.model.MAX_OPERATORS
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchForm
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.PublishCallupResult
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

    // US-008 · Partidos donde soy operador (para que el operador encuentre el partido aunque no sea de su equipo).
    // Igualdad + array-contains: Firestore lo resuelve con los índices simples (sin índice compuesto).
    fun operatorMatchesFlow(academyId: String, uid: String): Flow<Result<List<Match>>> =
        queryFlow(matchesRef.whereEqualTo("academyId", academyId).whereArrayContains("operatorIds", uid)) { it.toMatch() }

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
    //
    // Corrección US-006 (causa probable: reintento tras corte de red; confirmar con el Log): antes de mostrar un
    // error se pregunta al servidor si el lote quedó guardado. Si no se puede saber, el resultado es Unconfirmed.
    //
    // Límites conocidos:
    // - Un apoderado con dos hijos no recibe un segundo aviso si el segundo hijo se convoca después
    //   (el ID del aviso es por partido y persona).
    // - El botón "Comprobar y enviar avisos" se recuerda solo mientras la app sigue abierta. Si se cierra, los avisos
    //   pendientes salen al agregar a otro jugador.
    suspend fun publishCallup(match: Match, players: List<Player>): PublishCallupResult {
        if (!ConnectionChecker.isOnline()) return PublishCallupResult.Failed(FirebaseAuthManager.NO_CONNECTION_MESSAGE)
        // Sin jugadores nuevos: un solo "lote" vacío que solo cambia el estado (si sigue "Programado")
        val chunks = players.chunked(CALLUP_CHUNK).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { index, chunk ->
            try {
                firestore.runTransaction { transaction ->
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
                    chunk.zip(existing).filter { !it.second }.forEach { (player, _) ->
                        val callup = player.toNewCallup()
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
                    }
                }.await()
            } catch (e: Exception) {
                // El código se lee ANTES de comprobar. En Logcat queda el código real (sin datos personales).
                val code = firestoreErrorCode(e)
                Log.w("SportPro", "Falló la transacción de la convocatoria: ${code ?: e.javaClass.simpleName}")
                // Partido ya empezado o cancelado (checkOpen): solo ese mensaje, sin reintento ni botón de avisos
                if (e is IllegalStateException || e.cause is IllegalStateException) {
                    return PublishCallupResult.Failed(friendly(e))
                }
                val savedEarlier = index > 0
                val saved = try {
                    isCallupChunkSaved(match.id, chunk)
                } catch (checkError: Exception) {
                    val checkCode = firestoreErrorCode(checkError)
                    Log.w("SportPro", "No se pudo comprobar la convocatoria: ${checkCode ?: checkError.javaClass.simpleName}")
                    return if (checkCode == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        callupFailed(CALLUP_DENIED_MESSAGE, savedEarlier)
                    } else {
                        PublishCallupResult.Unconfirmed
                    }
                }
                if (!saved) {
                    val message = if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        CALLUP_DENIED_MESSAGE
                    } else {
                        "${friendly(e)} $CALLUP_RETRY_HINT"
                    }
                    return callupFailed(message, savedEarlier)
                }
            }
        }
        return checkSavedCallup(match.id)
    }

    // Lee del servidor el partido y sus convocados. Solo es Saved si el partido sigue abierto y quedó "Convocado".
    suspend fun checkSavedCallup(matchId: String): PublishCallupResult {
        if (!ConnectionChecker.isOnline()) return PublishCallupResult.Unconfirmed
        return try {
            val snapshot = matchRef(matchId).get(Source.SERVER).await()
            when {
                !snapshot.exists() || !snapshot.toMatch().isOpen() -> PublishCallupResult.Failed(MATCH_CLOSED_MESSAGE)
                snapshot.getString("status") != MatchStatus.CALLED ->
                    PublishCallupResult.Failed("La convocatoria no se guardó. $CALLUP_RETRY_HINT")
                else -> PublishCallupResult.Saved(
                    callupsRef(matchId).get(Source.SERVER).await().documents.map { it.toCallup() }
                )
            }
        } catch (e: Exception) {
            val code = firestoreErrorCode(e)
            Log.w("SportPro", "No se pudo leer la convocatoria guardada: ${code ?: e.javaClass.simpleName}")
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                PublishCallupResult.Failed(CALLUP_DENIED_MESSAGE)
            } else {
                PublishCallupResult.Unconfirmed
            }
        }
    }

    private fun callupFailed(message: String, savedEarlier: Boolean) = PublishCallupResult.Failed(
        message = if (savedEarlier) "$message $CALLUP_PARTIAL_HINT" else message,
        savedEarlier = savedEarlier
    )

    private fun Player.toNewCallup() = Callup(
        playerId = id,
        playerName = shortName,
        isMinor = isMinor,
        playerUserId = userId,
        guardianIds = guardianIds
    )

    // ¿El lote ya está en el servidor? El partido quedó "Convocado" y existen todos sus convocados
    private suspend fun isCallupChunkSaved(matchId: String, chunk: List<Player>): Boolean {
        val status = matchRef(matchId).get(Source.SERVER).await().getString("status")
        return status == MatchStatus.CALLED &&
            chunk.all { callupsRef(matchId).document(it.id).get(Source.SERVER).await().exists() }
    }

    // Código de Firestore del error (a veces viene dentro de otra excepción); null si no es de Firestore
    private fun firestoreErrorCode(e: Exception): FirebaseFirestoreException.Code? =
        (e as? FirebaseFirestoreException ?: e.cause as? FirebaseFirestoreException)?.code

    private const val CALLUP_RETRY_HINT = "Puedes volver a intentarlo: no se repiten convocados ni avisos."

    private const val CALLUP_PARTIAL_HINT =
        "Una parte de la convocatoria sí se guardó: toca \"Comprobar y enviar avisos\" para avisar a esos convocados."

    private const val CALLUP_DENIED_MESSAGE =
        "No tienes permiso para convocar en este partido. Puede que ya no seas el DT de este equipo, " +
            "que algún jugador ya no esté activo o haya cambiado de equipo, o que el partido ya haya empezado " +
            "según el servidor (revisa la hora del teléfono)."

    const val CALLUP_UNCONFIRMED_MESSAGE =
        "No pudimos confirmar si la convocatoria se guardó. Toca \"Comprobar y enviar avisos\" para revisarlo: " +
            "no se repiten avisos."

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

    // ---------------------------------------------------------------- Operadores (DT, US-008)

    // "Guardar operadores": hasta 5 usuarios activos de la academia (RN-010). Se puede cambiar mientras el
    // partido no esté Finalizado ni Cancelado (también en curso, por si un operador se queda sin batería).
    // Transacción: se hace antes del partido, con conexión, y así se ve el error de inmediato.
    suspend fun saveOperators(matchId: String, operatorIds: List<String>): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val ids = operatorIds.distinct()
            if (ids.size > MAX_OPERATORS) throw IllegalStateException("Puedes elegir como máximo $MAX_OPERATORS operadores.")
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(matchRef(matchId))
                val status = snapshot.getString("status")
                if (!snapshot.exists() || status == MatchStatus.FINISHED || status == MatchStatus.CANCELLED) {
                    throw IllegalStateException("Este partido ya terminó o fue cancelado: ya no se pueden cambiar los operadores.")
                }
                transaction.update(matchRef(matchId), mapOf("operatorIds" to ids, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            // La regla rechaza si alguien ya no está activo o no es de la academia, o si dejaste de ser el DT del equipo
            val denied = firestoreErrorCode(e) == FirebaseFirestoreException.Code.PERMISSION_DENIED
            Result.failure(Exception(if (denied) OPERATORS_DENIED_MESSAGE else friendly(e)))
        }
    }

    const val OPERATORS_DENIED_MESSAGE =
        "No pudimos guardar los operadores: alguna persona elegida ya no tiene su cuenta activa en la academia, " +
            "o ya no eres el DT de este equipo. Revisa la lista y vuelve a intentarlo."

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
        callupPublishedAt = time("callupPublishedAt"),
        operatorIds = (get("operatorIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        clockRunning = getBoolean("clockRunning") ?: false,
        clockRunningSince = time("clockRunningSince"),
        clockElapsedMs = getLong("clockElapsedMs") ?: 0L,
        startedAt = time("startedAt"),
        finishedAt = time("finishedAt"),
        startEventId = getString("startEventId") ?: "",
        endEventId = getString("endEventId") ?: ""
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
