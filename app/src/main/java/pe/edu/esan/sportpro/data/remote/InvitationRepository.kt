package pe.edu.esan.sportpro.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pe.edu.esan.sportpro.data.model.Invitation
import pe.edu.esan.sportpro.data.model.InvitationStatus
import java.util.Date

// Lectura de códigos de invitación (colección "invitations", el ID del documento es el código)
object InvitationRepository {

    private val firestore = FirebaseFirestore.getInstance()

    const val INVALID_CODE_MESSAGE = "Código no válido o vencido. Pide uno nuevo a tu academia o a tu entrenador."

    // Normaliza lo que escribe la persona: sin espacios y en mayúsculas
    fun normalize(code: String): String = code.trim().uppercase()

    suspend fun findValidInvitation(rawCode: String): Result<Invitation> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val code = normalize(rawCode)
            if (code.isEmpty()) throw IllegalArgumentException(INVALID_CODE_MESSAGE)

            val snapshot = firestore.collection("invitations").document(code).get().await()
            if (!isUsable(snapshot)) throw IllegalArgumentException(INVALID_CODE_MESSAGE)

            Result.success(snapshot.toInvitation())
        } catch (e: IllegalArgumentException) {
            Result.failure(e)   // el código realmente no sirve
        } catch (e: Exception) {
            // Problema de red u otro error: no se dice que el código es inválido
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // Un código sirve si existe, no fue usado, no fue anulado y no venció (RN-002)
    fun isUsable(snapshot: DocumentSnapshot): Boolean {
        if (!snapshot.exists()) return false
        if (snapshot.getBoolean("used") == true) return false
        if (snapshot.getString("status") == InvitationStatus.REVOKED) return false   // el ADM o el DT lo anuló (US-002)
        val expiresAt = snapshot.getTimestamp("expiresAt")?.toDate()
        if (expiresAt != null && expiresAt.before(Date())) return false
        return true
    }

    private fun DocumentSnapshot.toInvitation() = Invitation(
        code = id,
        academyId = getString("academyId") ?: "",
        academyName = getString("academyName") ?: "",
        teamId = getString("teamId") ?: "",
        teamName = getString("teamName") ?: "",
        city = getString("city") ?: "",
        role = getString("role") ?: "",
        playerId = getString("playerId") ?: "",
        playerName = getString("playerName") ?: "",
        invitedByName = getString("invitedByName") ?: ""
    )
}
