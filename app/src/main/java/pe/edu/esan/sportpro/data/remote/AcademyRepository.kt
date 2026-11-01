package pe.edu.esan.sportpro.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pe.edu.esan.sportpro.data.model.ACADEMY_ARTICLE_WORDS
import pe.edu.esan.sportpro.data.model.ACADEMY_TYPE_WORDS
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.Academy
import pe.edu.esan.sportpro.data.model.DEFAULT_CALLUP_CONFIRM_HOURS
import pe.edu.esan.sportpro.data.model.DEFAULT_CALLUP_NOTICE_HOURS
import pe.edu.esan.sportpro.data.model.DtInvitation
import pe.edu.esan.sportpro.data.model.INVITATION_VALID_DAYS
import pe.edu.esan.sportpro.data.model.InvitationStatus
import pe.edu.esan.sportpro.data.model.Team
import pe.edu.esan.sportpro.data.model.UserProfile
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Date

// US-002 · Academia, equipos, usuarios e invitaciones de DT (lo usa el ADM).
// - Lecturas: listeners de Firestore (las listas se actualizan solas).
// - Escrituras: transacciones con await() y Result. Se usa transacción (y no batch) porque,
//   sin internet, la transacción falla de inmediato y se puede mostrar el mensaje;
//   un batch se quedaría esperando hasta que vuelva la conexión.
object AcademyRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun academyRef(academyId: String) = firestore.collection("academies").document(academyId)
    private fun teamsRef(academyId: String) = academyRef(academyId).collection("teams")
    private fun userRef(uid: String) = firestore.collection("users").document(uid)
    private val invitationsRef = firestore.collection("invitations")

    // ---------------------------------------------------------------- Lecturas (listeners)

    fun academyFlow(academyId: String): Flow<Result<Academy>> = callbackFlow {
        val registration = academyRef(academyId).addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(Exception(FirebaseAuthManager.friendlyMessage(error))))
                snapshot == null -> Unit
                snapshot.exists() -> trySend(Result.success(snapshot.toAcademy()))
                // Sin internet y sin copia en el teléfono: "no existe" solo significa que no se pudo leer
                snapshot.metadata.isFromCache ->
                    trySend(Result.failure(Exception(FirebaseAuthManager.NO_CONNECTION_MESSAGE)))
                // Si no existe se avisa (en vez de quedarse cargando para siempre)
                else -> trySend(Result.failure(Exception("No encontramos tu academia.")))
            }
        }
        awaitClose { registration.remove() }
    }

    fun teamsFlow(academyId: String): Flow<Result<List<Team>>> =
        queryFlow(teamsRef(academyId)) { it.toTeam() }

    // Usuarios de la academia (las reglas dejan que el ADM los lea)
    fun membersFlow(academyId: String): Flow<Result<List<UserProfile>>> =
        queryFlow(firestore.collection("users").whereEqualTo("academyId", academyId)) { it.toUserProfile() }

    // Códigos de invitación de DT de la academia (dos filtros de igualdad: no necesitan índice compuesto)
    fun dtInvitationsFlow(academyId: String): Flow<Result<List<DtInvitation>>> =
        queryFlow(
            invitationsRef.whereEqualTo("academyId", academyId).whereEqualTo("role", "DT")
        ) { it.toDtInvitation() }

    private fun <T> queryFlow(query: Query, map: (DocumentSnapshot) -> T): Flow<Result<List<T>>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(Exception(FirebaseAuthManager.friendlyMessage(error))))
                snapshot != null -> trySend(Result.success(snapshot.documents.map(map)))
            }
        }
        awaitClose { registration.remove() }
    }

    // ---------------------------------------------------------------- Academia

    // Guarda los datos de la academia. Si cambió el nombre, también se actualiza la copia
    // "academyName" en los perfiles de la academia (es la que se muestra en el inicio).
    // Si cambió el nombre o la ciudad, también se actualizan en sus códigos sin usar
    // (es lo que ve quien se registra con el código). previous: la academia antes del cambio.
    suspend fun updateAcademy(academy: Academy, previous: Academy): Result<Unit> {
        return try {
            val newName = academy.name.trim()
            val newCity = academy.city.trim()
            val renamed = newName != previous.name
            val cityChanged = newCity != previous.city
            // Consultas en el servidor: sin internet falla aquí, antes de escribir nada
            val members = if (renamed) {
                firestore.collection("users").whereEqualTo("academyId", academy.id)
                    .get(Source.SERVER).await().documents
            } else emptyList()
            val pendingCodes = if (renamed || cityChanged) {
                invitationsRef.whereEqualTo("academyId", academy.id).whereEqualTo("used", false)
                    .get(Source.SERVER).await().documents
                    .filter(::isOpen)
            } else emptyList()

            firestore.runTransaction { transaction ->
                val codes = stillOpen(transaction, pendingCodes, stopIfDtCodeUsed = false)   // primero se lee, luego se escribe
                transaction.update(
                    academyRef(academy.id),
                    mapOf(
                        "name" to newName,
                        "city" to newCity,
                        "institutionalEmail" to academy.institutionalEmail.trim(),
                        "logoBase64" to academy.logoBase64,
                        "callupNoticeHours" to academy.callupNoticeHours,
                        "callupConfirmHours" to academy.callupConfirmHours
                    )
                )
                members.forEach { transaction.update(it.reference, "academyName", newName) }
                codes.forEach { code ->
                    val changes = mutableMapOf<String, Any>()
                    if (renamed) {
                        changes["academyName"] = newName
                        // invitedByName solo se cambia en los códigos que creó la administración
                        // (los de otros, como los del DT en la US-003, se dejan igual)
                        if (code.getString("invitedByName") == adminInviterName(previous.name)) {
                            changes["invitedByName"] = adminInviterName(newName)
                        }
                    }
                    if (cityChanged) changes["city"] = newCity
                    transaction.update(code.reference, changes)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(saveMessage(e)))
        }
    }

    // ---------------------------------------------------------------- Equipos

    // Crea (team.id vacío) o actualiza un equipo y, en la misma operación, su DT.
    // Devuelve el ID del equipo.
    // previousName: nombre antes de editar ("" si es nuevo). Si cambió, se actualiza la copia
    // "teamName" en los códigos sin usar del equipo (es la que ve quien se registra con el código).
    // coachChanged: false = el ADM no tocó "Cambiar" y no se toca a ningún DT.
    suspend fun saveTeam(
        academyId: String,
        team: Team,
        previousName: String,
        coachChanged: Boolean,
        coachUid: String
    ): Result<String> {
        return try {
            val isNew = team.id.isEmpty()
            val ref = if (isNew) teamsRef(academyId).document() else teamsRef(academyId).document(team.id)
            val newName = team.name.trim()
            val data = mapOf(
                "name" to newName,
                "category" to team.category,
                "maxStarters" to team.maxStarters,
                "maxSubstitutes" to team.maxSubstitutes
            )
            val renamed = !isNew && newName != previousName
            // Los DT actuales se consultan en el servidor (la lista del teléfono puede estar desactualizada)
            val currentCoachUids = if (coachChanged && !isNew) teamCoachUids(academyId, team.id) else emptyList()
            val assignsCoach = coachChanged && assignsNewCoach(coachUid, currentCoachUids)
            // Un equipo nuevo aún no tiene códigos
            val openCodes = if (!isNew && (renamed || assignsCoach)) pendingTeamCodes(academyId, team.id) else emptyList()

            firestore.runTransaction { transaction ->
                // Primero todas las lecturas (Firestore lo exige), luego las escrituras
                val codes = stillOpen(transaction, openCodes)
                val change = if (coachChanged) readCoachChange(transaction, ref.id, coachUid, currentCoachUids) else null
                // Los códigos de DT se anulan si se asigna un DT; esos ya no hace falta renombrarlos
                val codesToRevoke = if (change?.toAssign != null) codes.filter { it.getString("role") == "DT" } else emptyList()

                if (isNew) {
                    transaction.set(ref, data + mapOf("active" to true, "createdAt" to FieldValue.serverTimestamp()))
                } else {
                    transaction.update(ref, data)
                }
                if (renamed) {
                    (codes - codesToRevoke.toSet()).forEach { transaction.update(it.reference, "teamName", newName) }
                }
                if (change != null) applyCoachChange(transaction, ref.id, change, codesToRevoke)
            }.await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(Exception(saveMessage(e)))
        }
    }

    // Cambia el DT de un equipo (coachUid "" = Sin DT)
    suspend fun changeCoach(academyId: String, teamId: String, coachUid: String): Result<Unit> {
        return try {
            val currentCoachUids = teamCoachUids(academyId, teamId)
            val openDtCodes = if (assignsNewCoach(coachUid, currentCoachUids)) {
                pendingTeamCodes(academyId, teamId).filter { it.getString("role") == "DT" }
            } else emptyList()
            firestore.runTransaction { transaction ->
                val codes = stillOpen(transaction, openDtCodes)
                val change = readCoachChange(transaction, teamId, coachUid, currentCoachUids)
                applyCoachChange(transaction, teamId, change, codes)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(saveMessage(e)))
        }
    }

    private fun assignsNewCoach(coachUid: String, currentCoachUids: List<String>) =
        coachUid.isNotEmpty() && coachUid !in currentCoachUids

    // Códigos sin usar ni anular de un equipo (de DT y, desde la US-003, de jugador y apoderado).
    // Consulta en el servidor: sin internet falla aquí, antes de escribir nada.
    private suspend fun pendingTeamCodes(academyId: String, teamId: String): List<DocumentSnapshot> =
        invitationsRef.whereEqualTo("academyId", academyId)
            .whereEqualTo("teamId", teamId)
            .get(Source.SERVER).await().documents
            .filter(::isOpen)

    // DT del equipo según el servidor: role DT, ese teamId y cuenta no desactivada
    // (incluye al que aún no verifica su correo). Sin internet falla aquí, antes de escribir nada.
    private suspend fun teamCoachUids(academyId: String, teamId: String): List<String> =
        firestore.collection("users")
            .whereEqualTo("academyId", academyId)
            .whereEqualTo("role", "DT")
            .whereEqualTo("teamId", teamId)
            .get(Source.SERVER).await().documents
            .filter { it.getString("status") != AccountStatus.INACTIVE }
            .map { it.id }

    private fun isOpen(code: DocumentSnapshot) =
        code.getBoolean("used") != true && code.getString("status") != InvitationStatus.REVOKED

    // Dentro de la transacción se vuelven a leer los códigos consultados antes.
    // - Un código de DT que se usó entretanto: alguien acaba de registrarse como DT del equipo y se
    //   cuenta con un DT que no está en la consulta. Se detiene todo ("Algo cambió…") para no dejar 2 DT.
    // - Cualquier otro código usado o anulado entretanto se salta.
    // stopIfDtCodeUsed = false (cambiar datos de la academia): ahí un DT nuevo no causa problemas.
    private fun stillOpen(
        transaction: Transaction,
        codes: List<DocumentSnapshot>,
        stopIfDtCodeUsed: Boolean = true
    ): List<DocumentSnapshot> =
        codes.map { transaction.get(it.reference) }.filter { fresh ->
            if (stopIfDtCodeUsed && fresh.getString("role") == "DT" && fresh.getBoolean("used") == true) {
                throw ChangedMeanwhileException()
            }
            isOpen(fresh)
        }

    // Cambio de DT leído dentro de la transacción:
    // toClear = DT que dejan el equipo; toAssign = DT nuevo (null si no hay)
    private class CoachChange(val toClear: List<String>, val toAssign: String?)

    private fun readCoachChange(
        transaction: Transaction,
        teamId: String,
        coachUid: String,
        currentCoachUids: List<String>
    ): CoachChange {
        // Se vuelven a leer los perfiles de los DT consultados: al que entretanto dejó el equipo
        // (lo desactivaron o lo movieron) ya no hay que quitarlo
        val toClear = currentCoachUids.filter { it != coachUid }
            .filter { uid -> transaction.get(userRef(uid)).getString("teamId") == teamId }
        val toAssign = if (assignsNewCoach(coachUid, currentCoachUids)) coachUid else null
        // El DT elegido pudo ser desactivado mientras el ADM elegía
        if (toAssign != null && transaction.get(userRef(toAssign)).getString("status") != AccountStatus.ACTIVE) {
            throw ChangedMeanwhileException()
        }
        return CoachChange(toClear, toAssign)
    }

    // El DT de un equipo es el usuario DT (no desactivado) cuyo teamId es el del equipo.
    // Cambiar el DT = el DT anterior queda con teamId "" y el nuevo con el teamId del equipo.
    // (Un DT dirige un solo equipo: si el nuevo dirigía otro, ese otro queda "Sin DT".)
    // Al asignar un DT se anulan, en la misma operación, los códigos de DT pendientes del equipo:
    // si alguien usara uno después, el equipo quedaría con 2 DT.
    private fun applyCoachChange(
        transaction: Transaction,
        teamId: String,
        change: CoachChange,
        codesToRevoke: List<DocumentSnapshot>
    ) {
        change.toClear.forEach { uid -> transaction.update(userRef(uid), "teamId", "") }
        change.toAssign?.let { uid ->
            transaction.update(userRef(uid), "teamId", teamId)
            codesToRevoke.forEach { transaction.update(it.reference, "status", InvitationStatus.REVOKED) }
        }
    }

    // "Desactivar equipo": no se borra, solo queda active = false (se conserva su historial).
    // En la misma operación se anulan sus códigos pendientes (de DT y, desde la US-003, también
    // los de jugador y apoderado) y su DT queda sin equipo. Sus jugadores se quedan en el equipo
    // (el ADM los puede trasladar desde "Ver plantel").
    suspend fun setTeamActive(academyId: String, teamId: String, active: Boolean): Result<Unit> {
        return try {
            val pendingCodes = if (!active) pendingTeamCodes(academyId, teamId) else emptyList()
            // DT del equipo según el servidor (también el que aún no verifica su correo)
            val currentCoachUids = if (!active) teamCoachUids(academyId, teamId) else emptyList()

            firestore.runTransaction { transaction ->
                // Primero se lee (Firestore lo exige): solo los códigos que siguen abiertos
                // y los DT que siguen en el equipo
                val codes = stillOpen(transaction, pendingCodes)
                val coaches = if (!active) {
                    currentCoachUids.filter { uid -> transaction.get(userRef(uid)).getString("teamId") == teamId }
                } else emptyList()

                transaction.update(teamsRef(academyId).document(teamId), "active", active)
                codes.forEach { transaction.update(it.reference, "status", InvitationStatus.REVOKED) }
                coaches.forEach { uid -> transaction.update(userRef(uid), "teamId", "") }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(saveMessage(e)))
        }
    }

    // Algo cambió entre la consulta y la escritura (por ejemplo, desactivaron al DT elegido)
    private class ChangedMeanwhileException : Exception(CHANGED_MEANWHILE_MESSAGE)

    const val CHANGED_MEANWHILE_MESSAGE = "Algo cambió mientras guardabas. Inténtalo de nuevo."

    // Mensaje de las operaciones que escriben varios documentos a la vez: "Algo cambió…" solo si la
    // app detectó un cambio entre la consulta y la escritura, o si Firestore abortó la transacción
    // por cambios simultáneos (ABORTED). Cualquier otro error (también un permiso negado) usa el
    // mensaje de siempre.
    private fun saveMessage(e: Exception): String {
        val firestoreCode = (e as? FirebaseFirestoreException ?: e.cause as? FirebaseFirestoreException)?.code
        return when {
            e is ChangedMeanwhileException || e.cause is ChangedMeanwhileException -> CHANGED_MEANWHILE_MESSAGE
            firestoreCode == FirebaseFirestoreException.Code.ABORTED -> CHANGED_MEANWHILE_MESSAGE
            else -> FirebaseAuthManager.friendlyMessage(e)
        }
    }

    // ---------------------------------------------------------------- Usuarios

    // "Desactivar cuenta" = status "inactive"; "Reactivar" = "active". Nunca se borra.
    // Al desactivar a un DT, en la misma operación queda sin equipo (teamId ""): así, al
    // reactivarlo, no aparecen 2 DT en el mismo equipo. El ADM le asigna equipo con "Cambiar DT".
    suspend fun setUserActive(user: UserProfile, active: Boolean): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val ref = userRef(user.uid)
                transaction.update(ref, "status", if (active) AccountStatus.ACTIVE else AccountStatus.INACTIVE)
                if (!active && user.role == "DT" && user.teamId.isNotEmpty()) {
                    transaction.update(ref, "teamId", "")
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // ---------------------------------------------------------------- Invitaciones de DT

    // Crea un código PREFIJO-XXXXXX (ej. HALC-7K2P9Q) para invitar a un DT a un equipo.
    // Mismo formato que lee la US-001 (InvitationRepository). Devuelve el código.
    suspend fun createDtInvitation(academy: Academy, team: Team, admin: UserProfile): Result<String> {
        return try {
            // Segunda barrera (la lista del teléfono puede estar desactualizada): un solo DT por equipo.
            // Si el equipo ya tiene DT (aunque aún no verifique su correo) o un código de DT
            // pendiente, no se crea otro.
            val now = Date()
            val hasPendingCode = pendingTeamCodes(academy.id, team.id)
                .any { it.getString("role") == "DT" && (it.getTimestamp("expiresAt")?.toDate()?.after(now) ?: true) }
            val hasCoach = teamCoachUids(academy.id, team.id).isNotEmpty()
            if (hasPendingCode || hasCoach) throw IllegalStateException(COACH_TAKEN_MESSAGE)

            val prefix = codePrefix(academy.name)
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + INVITATION_VALID_DAYS * 24L * 60 * 60 * 1000))
            val data = hashMapOf(
                "academyId" to academy.id,
                "academyName" to academy.name,
                "city" to academy.city,
                "role" to "DT",
                "teamId" to team.id,
                "teamName" to team.name,
                "invitedByName" to adminInviterName(academy.name),
                "playerId" to "",
                "playerName" to "",
                "used" to false,
                "status" to InvitationStatus.PENDING,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt,
                "createdBy" to admin.uid
            )

            val code = firestore.runTransaction { transaction ->
                val ref = pickFreeCode(transaction, prefix)
                transaction.set(ref, data)
                ref.id
            }.await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // Elige, dentro de una transacción, un código PREFIJO-XXXXXX que aún no exista
    // (es casi imposible que se repita, pero se revisa igual). También lo usa la US-003.
    fun pickFreeCode(transaction: Transaction, prefix: String): DocumentReference {
        repeat(5) {
            val candidate = invitationsRef.document("$prefix-${randomPart()}")
            if (!transaction.get(candidate).exists()) return candidate
        }
        throw IllegalStateException("No se pudo generar el código. Inténtalo de nuevo.")
    }

    const val COACH_TAKEN_MESSAGE = "Este equipo ya tiene DT (o un código pendiente). Anúlalo o cambia el DT primero."

    // Texto "invitado por …" de los códigos que crea el ADM (la regla usa el mismo texto)
    fun adminInviterName(academyName: String) = "la administración de $academyName"

    // Anular = status "ANULADO" (RN-002). El código no se borra; un código usado no se puede anular.
    suspend fun revokeInvitation(code: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val ref = invitationsRef.document(code)
                val snapshot = transaction.get(ref)
                if (snapshot.getBoolean("used") == true) {
                    throw IllegalStateException("Este código ya fue usado y no se puede anular.")
                }
                transaction.update(ref, "status", InvitationStatus.REVOKED)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // ---------------------------------------------------------------- Código de invitación

    // Formato del código: PREFIJO (4 letras A-Z) + "-" + 6 caracteres del alfabeto sin confusiones.
    // Los códigos JUG/PAD de la US-003 usan el mismo formato (pickFreeCode + codePrefix).
    // Coincide con la regla: code.matches('^[A-Z]{4}-[A-Z2-9]{6}$')
    // Parte al azar: sin caracteres que se confunden (sin I, O, 0 ni 1)
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    // Relleno del prefijo: solo letras (el prefijo nunca lleva números)
    private const val PREFIX_FILL_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val secureRandom = SecureRandom()

    // Palabras que no sirven para el prefijo ("Academia Los Halcones" -> HALC): la misma lista
    // que las iniciales de la academia (Academy.kt)
    private val SKIP_WORDS = ACADEMY_TYPE_WORDS + ACADEMY_ARTICLE_WORDS

    private fun randomPart(length: Int = 6, alphabet: String = CODE_ALPHABET): String =
        (1..length).map { alphabet[secureRandom.nextInt(alphabet.length)] }.joinToString("")

    // 4 letras A-Z del nombre, sin tildes; si el nombre es muy corto se completa con letras al azar
    fun codePrefix(academyName: String): String {
        val words = Normalizer.normalize(academyName, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")   // quita tildes (Ñ -> N)
            .uppercase()
            .split(Regex("[^A-Z]+"))
            .filter { it.isNotEmpty() }
        val significant = words.filter { it.lowercase() !in SKIP_WORDS }.ifEmpty { words }
        val letters = significant.joinToString("").take(4)
        return letters + randomPart(4 - letters.length, PREFIX_FILL_ALPHABET)
    }

    // ---------------------------------------------------------------- Conversión de documentos

    private fun DocumentSnapshot.toAcademy() = Academy(
        id = id,
        name = getString("name") ?: "",
        city = getString("city") ?: "",
        institutionalEmail = getString("institutionalEmail") ?: "",
        logoBase64 = getString("logoBase64") ?: "",
        callupNoticeHours = getLong("callupNoticeHours")?.toInt() ?: DEFAULT_CALLUP_NOTICE_HOURS,
        callupConfirmHours = getLong("callupConfirmHours")?.toInt() ?: DEFAULT_CALLUP_CONFIRM_HOURS,
        verified = getBoolean("verified") ?: false,
        adminUid = getString("adminUid") ?: ""
    )

    private fun DocumentSnapshot.toTeam() = Team(
        id = id,
        name = getString("name") ?: "",
        category = getString("category") ?: "Otra",
        maxStarters = getLong("maxStarters")?.toInt() ?: 11,
        maxSubstitutes = getLong("maxSubstitutes")?.toInt() ?: 12,
        active = getBoolean("active") ?: true
    )
}

// Convierte invitations/{codigo} en DtInvitation.
// Está fuera del object para que también la use PlayerRepository (códigos JUG/PAD, US-003).
fun DocumentSnapshot.toDtInvitation() = DtInvitation(
    code = id,
    role = getString("role") ?: "",
    teamId = getString("teamId") ?: "",
    teamName = getString("teamName") ?: "",
    playerId = getString("playerId") ?: "",
    playerName = getString("playerName") ?: "",
    used = getBoolean("used") ?: false,
    usedBy = getString("usedBy") ?: "",
    status = getString("status") ?: InvitationStatus.PENDING,
    createdAt = getTimestamp("createdAt")?.toDate(),
    expiresAt = getTimestamp("expiresAt")?.toDate()
)
