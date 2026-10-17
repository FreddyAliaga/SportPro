package pe.edu.esan.sportpro.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
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
    // "academyName" en los perfiles de la academia (es la que se muestra en el inicio)
    // y en sus códigos pendientes (es la que ve quien se registra con el código).
    suspend fun updateAcademy(academy: Academy, previousName: String): Result<Unit> {
        return try {
            val newName = academy.name.trim()
            val renamed = newName != previousName
            // Consultas en el servidor: sin internet falla aquí, antes de escribir nada
            val members = if (renamed) {
                firestore.collection("users").whereEqualTo("academyId", academy.id)
                    .get(Source.SERVER).await().documents
            } else emptyList()
            val pendingCodes = if (renamed) {
                invitationsRef.whereEqualTo("academyId", academy.id).whereEqualTo("used", false)
                    .get(Source.SERVER).await().documents
                    .filter { it.getString("status") != InvitationStatus.REVOKED }
            } else emptyList()

            firestore.runTransaction { transaction ->
                transaction.update(
                    academyRef(academy.id),
                    mapOf(
                        "name" to newName,
                        "city" to academy.city.trim(),
                        "institutionalEmail" to academy.institutionalEmail.trim(),
                        "logoBase64" to academy.logoBase64,
                        "callupNoticeHours" to academy.callupNoticeHours,
                        "callupConfirmHours" to academy.callupConfirmHours
                    )
                )
                members.forEach { transaction.update(it.reference, "academyName", newName) }
                pendingCodes.forEach { code ->
                    // invitedByName solo se cambia en los códigos que creó la administración
                    // (los de otros, como los del DT en la US-003, se dejan igual)
                    if (code.getString("invitedByName") == adminInviterName(previousName)) {
                        transaction.update(
                            code.reference,
                            mapOf("academyName" to newName, "invitedByName" to adminInviterName(newName))
                        )
                    } else {
                        transaction.update(code.reference, "academyName", newName)
                    }
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // ---------------------------------------------------------------- Equipos

    // Crea (team.id vacío) o actualiza un equipo y, en la misma operación, su DT.
    // currentCoachUids: DT activos que hoy tienen este equipo (normalmente 0 o 1).
    // Devuelve el ID del equipo.
    suspend fun saveTeam(
        academyId: String,
        team: Team,
        coachUid: String,
        currentCoachUids: List<String>
    ): Result<String> {
        return try {
            val isNew = team.id.isEmpty()
            val ref = if (isNew) teamsRef(academyId).document() else teamsRef(academyId).document(team.id)
            val data = mapOf(
                "name" to team.name.trim(),
                "category" to team.category,
                "maxStarters" to team.maxStarters,
                "maxSubstitutes" to team.maxSubstitutes
            )
            firestore.runTransaction { transaction ->
                if (isNew) {
                    transaction.set(ref, data + mapOf("active" to true, "createdAt" to FieldValue.serverTimestamp()))
                } else {
                    transaction.update(ref, data)
                }
                applyCoachChange(transaction, ref.id, coachUid, currentCoachUids)
            }.await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // Cambia el DT de un equipo (coachUid "" = Sin DT)
    suspend fun changeCoach(teamId: String, coachUid: String, currentCoachUids: List<String>): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                applyCoachChange(transaction, teamId, coachUid, currentCoachUids)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // El DT de un equipo es el usuario DT activo cuyo teamId es el del equipo.
    // Cambiar el DT = el DT anterior queda con teamId "" y el nuevo con el teamId del equipo.
    // (Un DT dirige un solo equipo: si el nuevo dirigía otro, ese otro queda "Sin DT".)
    private fun applyCoachChange(
        transaction: Transaction,
        teamId: String,
        coachUid: String,
        currentCoachUids: List<String>
    ) {
        currentCoachUids.filter { it != coachUid }.forEach { uid ->
            transaction.update(userRef(uid), "teamId", "")
        }
        if (coachUid.isNotEmpty() && coachUid !in currentCoachUids) {
            transaction.update(userRef(coachUid), "teamId", teamId)
        }
    }

    // "Desactivar equipo": no se borra, solo queda active = false (se conserva su historial).
    // En la misma operación se anulan sus códigos pendientes (de DT y, desde la US-003, también
    // los de jugador y apoderado) y su DT queda sin equipo. Sus jugadores se quedan en el equipo
    // (el ADM los puede trasladar desde "Ver plantel").
    // currentCoachUids: DT activos que hoy tienen este equipo.
    suspend fun setTeamActive(
        academyId: String,
        teamId: String,
        active: Boolean,
        currentCoachUids: List<String>
    ): Result<Unit> {
        return try {
            // Consulta en el servidor: sin internet falla aquí, antes de escribir nada
            val pendingCodes = if (!active) {
                invitationsRef.whereEqualTo("academyId", academyId)
                    .whereEqualTo("teamId", teamId)
                    .get(Source.SERVER).await().documents
                    .filter { it.getBoolean("used") != true && it.getString("status") != InvitationStatus.REVOKED }
            } else emptyList()

            firestore.runTransaction { transaction ->
                transaction.update(teamsRef(academyId).document(teamId), "active", active)
                if (!active) {
                    pendingCodes.forEach { transaction.update(it.reference, "status", InvitationStatus.REVOKED) }
                    currentCoachUids.forEach { uid -> transaction.update(userRef(uid), "teamId", "") }
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
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

    // Palabras que no sirven para el prefijo ("Academia Los Halcones" -> HALC)
    private val SKIP_WORDS = setOf("ACADEMIA", "ESCUELA", "CLUB", "DE", "DEL", "LA", "LAS", "LOS", "EL", "FC", "CF")

    private fun randomPart(length: Int = 6, alphabet: String = CODE_ALPHABET): String =
        (1..length).map { alphabet[secureRandom.nextInt(alphabet.length)] }.joinToString("")

    // 4 letras A-Z del nombre, sin tildes; si el nombre es muy corto se completa con letras al azar
    fun codePrefix(academyName: String): String {
        val words = Normalizer.normalize(academyName, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")   // quita tildes (Ñ -> N)
            .uppercase()
            .split(Regex("[^A-Z]+"))
            .filter { it.isNotEmpty() }
        val significant = words.filter { it !in SKIP_WORDS }.ifEmpty { words }
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
