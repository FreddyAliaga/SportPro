package pe.edu.esan.sportpro.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pe.edu.esan.sportpro.data.model.DtInvitation
import pe.edu.esan.sportpro.data.model.INVITATION_VALID_DAYS
import pe.edu.esan.sportpro.data.model.Invitation
import pe.edu.esan.sportpro.data.model.InvitationStatus
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.PlayerHistoryEntry
import pe.edu.esan.sportpro.data.model.PlayerHistoryType
import pe.edu.esan.sportpro.data.model.PlayerPrivate
import pe.edu.esan.sportpro.data.model.PlayerPrivateForm
import pe.edu.esan.sportpro.data.model.PlayerSportForm
import pe.edu.esan.sportpro.data.model.Team
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.ageFromDigits
import pe.edu.esan.sportpro.data.model.isMinorAge
import java.util.Date

// US-003 · Plantel y perfil del jugador (lo usan el DT, el ADM, el apoderado y el jugador mayor).
// Mismo estilo que AcademyRepository:
// - Lecturas: listeners de Firestore (las listas se actualizan solas).
// - Escrituras: transacciones con await() y Result (sin internet fallan de inmediato).
// Un jugador NUNCA se borra: retirar = active false, y cada movimiento queda en su historial (RN-004).
object PlayerRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private val playersRef = firestore.collection("players")
    private fun playerRef(playerId: String) = playersRef.document(playerId)
    private fun privateRef(playerId: String) = playerRef(playerId).collection("private").document("data")
    private fun historyRef(playerId: String) = playerRef(playerId).collection("history")
    private val invitationsRef = firestore.collection("invitations")

    // ---------------------------------------------------------------- Lecturas (listeners)

    // Plantel de un equipo (DT o ADM). Dos filtros de igualdad: no necesitan índice compuesto.
    // El filtro por academyId es necesario para que las reglas acepten la consulta.
    fun teamPlayersFlow(academyId: String, teamId: String): Flow<Result<List<Player>>> =
        queryFlow(playersRef.whereEqualTo("academyId", academyId).whereEqualTo("teamId", teamId)) { it.toPlayer() }

    // "Mis jugadores" del apoderado: un PAD puede tener varios hijos
    fun guardianPlayersFlow(uid: String): Flow<Result<List<Player>>> =
        queryFlow(playersRef.whereArrayContains("guardianIds", uid)) { it.toPlayer() }

    fun playerFlow(playerId: String): Flow<Result<Player>> =
        documentFlow(playerRef(playerId), "No encontramos a este jugador.") { it.toPlayer() }

    fun privateFlow(playerId: String): Flow<Result<PlayerPrivate>> =
        documentFlow(privateRef(playerId), "No encontramos los datos privados del jugador.") { it.toPlayerPrivate() }

    fun historyFlow(playerId: String): Flow<Result<List<PlayerHistoryEntry>>> =
        queryFlow(historyRef(playerId)) { it.toHistoryEntry() }

    // Códigos JUG/PAD de un jugador (DT o ADM). Filtros de igualdad e "in": sin índice compuesto.
    fun playerInvitationsFlow(player: Player): Flow<Result<List<DtInvitation>>> =
        queryFlow(playerInvitationsQuery(player)) { it.toDtInvitation() }

    // El filtro por rol es necesario para que las reglas acepten la consulta del DT
    // (el DT solo lista códigos JUG/PAD de su equipo)
    private fun playerInvitationsQuery(player: Player): Query =
        invitationsRef.whereEqualTo("academyId", player.academyId)
            .whereEqualTo("teamId", player.teamId)
            .whereEqualTo("playerId", player.id)
            .whereIn("role", listOf("JUG", "PAD"))

    // Foto del jugador para la lista del plantel (la foto es un dato privado: está en private/data).
    // Se lee una vez por jugador. Devuelve "" si no tiene foto y null si no se pudo leer
    // (la lista muestra las iniciales y se vuelve a intentar más tarde).
    suspend fun loadPhoto(playerId: String): String? =
        try {
            privateRef(playerId).get().await().getString("photoBase64") ?: ""
        } catch (_: Exception) {
            null
        }

    private fun <T> queryFlow(query: Query, map: (DocumentSnapshot) -> T): Flow<Result<List<T>>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(Exception(FirebaseAuthManager.friendlyMessage(error))))
                snapshot != null -> trySend(Result.success(snapshot.documents.map(map)))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun <T> documentFlow(
        ref: com.google.firebase.firestore.DocumentReference,
        notFoundMessage: String,
        map: (DocumentSnapshot) -> T
    ): Flow<Result<T>> = callbackFlow {
        val registration = ref.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(Exception(FirebaseAuthManager.friendlyMessage(error))))
                snapshot == null -> Unit
                snapshot.exists() -> trySend(Result.success(map(snapshot)))
                // Si no existe se avisa (en vez de quedarse cargando para siempre)
                else -> trySend(Result.failure(Exception(notFoundMessage)))
            }
        }
        awaitClose { registration.remove() }
    }

    // ---------------------------------------------------------------- Crear y editar (DT o ADM)

    // "Nuevo jugador": crea el perfil, sus datos privados (fecha de nacimiento) y la primera
    // entrada del historial en la MISMA operación. Devuelve el ID del jugador.
    // Usar solo con el formulario ya validado.
    suspend fun createPlayer(academyId: String, team: Team, form: PlayerSportForm, by: UserProfile): Result<String> {
        return try {
            val ref = playersRef.document()   // ID automático
            val playerData = hashMapOf<String, Any?>(
                "academyId" to academyId,
                "teamId" to team.id,
                "lastInvitationCode" to "",
                "userId" to "",
                "guardianIds" to emptyList<String>(),
                "hasEmergencyContact" to false,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "createdBy" to by.uid
            ) + sportData(form)
            val privateData = hashMapOf<String, Any?>(
                "birthDate" to FirebaseAuthManager.toTimestamp(form.birthDate),
                "contactPhone" to "",
                "emergencyName" to "",
                "emergencyRelation" to "",
                "emergencyPhone" to "",
                "photoBase64" to "",
                "heightCm" to null,
                "weightKg" to null
            )
            firestore.runTransaction { transaction ->
                transaction.set(ref, playerData)
                transaction.set(privateRef(ref.id), privateData)
                addHistory(transaction, ref.id, PlayerHistoryType.CREATED, null, team, by)
            }.await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // "Editar datos deportivos" (DT o ADM). La fecha de nacimiento también se guarda aquí:
    // solo la editan el DT o el ADM, y con ella se vuelve a calcular si es menor de edad.
    suspend fun updateSportData(playerId: String, form: PlayerSportForm): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                transaction.update(playerRef(playerId), sportData(form))
                transaction.update(privateRef(playerId), "birthDate", FirebaseAuthManager.toTimestamp(form.birthDate))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // Campos deportivos que escriben "Nuevo jugador" y "Editar datos deportivos"
    private fun sportData(form: PlayerSportForm): Map<String, Any?> = mapOf(
        "name" to form.name.trim(),
        "category" to form.category,
        "positionMain" to form.positionMain,
        "positionSecondary" to form.positionSecondary,
        "dorsal" to form.dorsal.trim().toIntOrNull(),   // null = sin dorsal
        "foot" to form.foot,
        "isMinor" to isMinorAge(ageFromDigits(form.birthDate) ?: 0)
    )

    // ---------------------------------------------------------------- Datos privados (apoderado o jugador mayor)

    // Guarda contacto, contacto de emergencia, foto y datos físicos (nunca la fecha de nacimiento).
    // En la misma operación se actualiza hasEmergencyContact del jugador (RN-005: estado del plantel).
    // Usar solo con el formulario ya validado.
    suspend fun savePrivateData(playerId: String, form: PlayerPrivateForm): Result<Unit> {
        return try {
            val data = hashMapOf<String, Any?>(
                "contactPhone" to form.contactPhone.trim(),
                "emergencyName" to form.emergencyName.trim(),
                "emergencyRelation" to form.emergencyRelation.trim(),
                "emergencyPhone" to form.emergencyPhone.trim(),
                "photoBase64" to form.photoBase64,
                "heightCm" to form.heightCm.trim().toIntOrNull(),
                "weightKg" to form.weightKg.trim().toIntOrNull()
            )
            val hasEmergency = form.emergencyName.isNotBlank() && form.emergencyRelation.isNotBlank() &&
                form.emergencyPhone.isNotBlank()
            firestore.runTransaction { transaction ->
                transaction.update(privateRef(playerId), data)
                transaction.update(playerRef(playerId), "hasEmergencyContact", hasEmergency)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // ---------------------------------------------------------------- Trasladar, retirar y reactivar (DT o ADM)

    // Traslada al jugador a otro equipo ACTIVO de la misma academia.
    // En la misma operación: queda en el historial y se anulan sus códigos pendientes
    // (mostrarían el equipo anterior).
    suspend fun transferPlayer(player: Player, fromTeam: Team, toTeam: Team, by: UserProfile): Result<Unit> {
        return try {
            val pendingCodes = pendingCodes(player)   // sin internet falla aquí, antes de escribir nada
            firestore.runTransaction { transaction ->
                transaction.update(playerRef(player.id), mapOf("teamId" to toTeam.id, "category" to toTeam.category))
                addHistory(transaction, player.id, PlayerHistoryType.TRANSFER, fromTeam, toTeam, by)
                pendingCodes.forEach { transaction.update(it, "status", InvitationStatus.REVOKED) }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // "Retirar" (Inactivo) o "Reactivar". Nunca se borra: se conserva su historial (RN-004).
    // Al retirarlo se anulan sus códigos pendientes en la misma operación.
    suspend fun setPlayerActive(player: Player, team: Team, active: Boolean, by: UserProfile): Result<Unit> {
        return try {
            val pendingCodes = if (!active) pendingCodes(player) else emptyList()
            firestore.runTransaction { transaction ->
                transaction.update(playerRef(player.id), "active", active)
                val type = if (active) PlayerHistoryType.REACTIVATE else PlayerHistoryType.RETIRE
                addHistory(transaction, player.id, type, team, team, by)
                pendingCodes.forEach { transaction.update(it, "status", InvitationStatus.REVOKED) }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // Códigos del jugador sin usar ni anular (consulta en el servidor)
    private suspend fun pendingCodes(player: Player) =
        playerInvitationsQuery(player).get(Source.SERVER).await().documents
            .filter { it.getBoolean("used") != true && it.getString("status") != InvitationStatus.REVOKED }
            .map { it.reference }

    // Una entrada del historial (nunca se edita ni se borra)
    private fun addHistory(transaction: Transaction, playerId: String, type: String, from: Team?, to: Team?, by: UserProfile) {
        transaction.set(
            historyRef(playerId).document(),
            mapOf(
                "type" to type,
                "fromTeamId" to (from?.id ?: ""),
                "fromTeamName" to (from?.name ?: ""),
                "toTeamId" to (to?.id ?: ""),
                "toTeamName" to (to?.name ?: ""),
                "byUid" to by.uid,
                "byName" to by.name,
                "at" to FieldValue.serverTimestamp()
            )
        )
    }

    // ---------------------------------------------------------------- Códigos JUG / PAD (DT o ADM)

    // Crea un código para el apoderado de un menor (role "PAD") o para el propio jugador
    // mayor de edad (role "JUG", también cuando un menor cumple 18). Mismo formato que la US-002.
    // PRIVACIDAD: el código lo puede leer cualquiera que lo tenga, por eso lleva el nombre corto ("Mateo R.").
    // Devuelve el código.
    suspend fun createPlayerInvitation(player: Player, role: String, by: UserProfile): Result<String> {
        return try {
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + INVITATION_VALID_DAYS * 24L * 60 * 60 * 1000))
            val academyRef = firestore.collection("academies").document(player.academyId)
            val teamRef = academyRef.collection("teams").document(player.teamId)

            val code = firestore.runTransaction { transaction ->
                // Nombre y ciudad reales: la regla comprueba que coincidan con la academia y el equipo
                val academy = transaction.get(academyRef)
                val team = transaction.get(teamRef)
                val academyName = academy.getString("name") ?: ""
                val ref = AcademyRepository.pickFreeCode(transaction, AcademyRepository.codePrefix(academyName))
                transaction.set(
                    ref,
                    hashMapOf(
                        "academyId" to player.academyId,
                        "academyName" to academyName,
                        "city" to (academy.getString("city") ?: ""),
                        "role" to role,
                        "teamId" to player.teamId,
                        "teamName" to (team.getString("name") ?: ""),
                        "invitedByName" to inviterName(by, academyName),
                        "playerId" to player.id,
                        "playerName" to player.shortName,
                        "used" to false,
                        "status" to InvitationStatus.PENDING,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "expiresAt" to expiresAt,
                        "createdBy" to by.uid
                    )
                )
                ref.id
            }.await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // "Invitado por …": el DT con su nombre; el ADM, como la administración (igual que en la US-002,
    // así el texto se actualiza si se renombra la academia)
    private fun inviterName(by: UserProfile, academyName: String) =
        if (by.role == "ADM") AcademyRepository.adminInviterName(academyName) else "el DT ${by.name.trim()}".take(80)

    // ---------------------------------------------------------------- Uso del código

    // Lo que hace un código JUG/PAD sobre el jugador, dentro de la transacción que marca el código como usado:
    // - PAD: agrega al apoderado al jugador.
    // - JUG: el jugador asume su cuenta y su gestión (se retira el vínculo con sus apoderados).
    // lastInvitationCode le permite a la regla comprobar que el código se usó en esta misma operación.
    // Lo usan el registro (US-001) y "Vincular otro jugador".
    fun applyInvitationToPlayer(transaction: Transaction, invitation: Invitation, uid: String) {
        if (invitation.playerId.isEmpty()) return
        val ref = playerRef(invitation.playerId)
        when (invitation.role) {
            "PAD" -> transaction.update(
                ref,
                mapOf(
                    "guardianIds" to FieldValue.arrayUnion(uid),
                    "lastInvitationCode" to invitation.code
                )
            )
            "JUG" -> transaction.update(
                ref,
                mapOf(
                    "userId" to uid,
                    "guardianIds" to emptyList<String>(),
                    "isMinor" to false,
                    "lastInvitationCode" to invitation.code
                )
            )
        }
    }

    // "Vincular otro jugador": un apoderado que YA tiene cuenta usa un nuevo código PAD.
    // En una transacción marca el código como usado y lo agrega como apoderado del jugador.
    suspend fun linkAnotherPlayer(invitation: Invitation, profile: UserProfile): Result<Unit> {
        return try {
            if (invitation.role != "PAD" || invitation.playerId.isEmpty()) {
                throw IllegalStateException("Este código no es de apoderado. Pide al DT un código para apoderado.")
            }
            if (invitation.academyId != profile.academyId) {
                throw IllegalStateException("Este código es de otra academia. Solo puedes vincular jugadores de ${profile.academyName}.")
            }
            val invitationRef = invitationsRef.document(invitation.code)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(invitationRef)
                if (!InvitationRepository.isUsable(snapshot)) {
                    throw IllegalStateException(InvitationRepository.INVALID_CODE_MESSAGE)
                }
                transaction.update(
                    invitationRef,
                    mapOf(
                        "used" to true,
                        "usedBy" to profile.uid,
                        "usedAt" to FieldValue.serverTimestamp()
                    )
                )
                applyInvitationToPlayer(transaction, invitation, profile.uid)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.codeUseMessage(e, invitation)))
        }
    }

    // ---------------------------------------------------------------- Conversión de documentos

    private fun DocumentSnapshot.toPlayer() = Player(
        id = id,
        academyId = getString("academyId") ?: "",
        teamId = getString("teamId") ?: "",
        name = getString("name") ?: "",
        category = getString("category") ?: "Otra",
        positionMain = getString("positionMain") ?: "",
        positionSecondary = getString("positionSecondary") ?: "",
        dorsal = getLong("dorsal")?.toInt(),
        foot = getString("foot") ?: "",
        isMinor = getBoolean("isMinor") ?: true,
        userId = getString("userId") ?: "",
        guardianIds = (get("guardianIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        hasEmergencyContact = getBoolean("hasEmergencyContact") ?: false,
        active = getBoolean("active") ?: true
    )

    private fun DocumentSnapshot.toPlayerPrivate() = PlayerPrivate(
        birthDate = getTimestamp("birthDate")?.toDate(),
        contactPhone = getString("contactPhone") ?: "",
        emergencyName = getString("emergencyName") ?: "",
        emergencyRelation = getString("emergencyRelation") ?: "",
        emergencyPhone = getString("emergencyPhone") ?: "",
        photoBase64 = getString("photoBase64") ?: "",
        heightCm = getLong("heightCm")?.toInt(),
        weightKg = getLong("weightKg")?.toInt()
    )

    private fun DocumentSnapshot.toHistoryEntry() = PlayerHistoryEntry(
        id = id,
        type = getString("type") ?: "",
        fromTeamName = getString("fromTeamName") ?: "",
        toTeamName = getString("toTeamName") ?: "",
        byName = getString("byName") ?: "",
        at = getTimestamp("at")?.toDate()
    )
}
