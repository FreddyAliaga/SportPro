package pe.edu.esan.sportpro.data.model

import java.util.Date

// Código de invitación visto por el ADM en "Usuarios e invitaciones" (invitations/{codigo}).
// US-003: también se usa para los códigos JUG/PAD de un jugador (playerId / playerName).
data class DtInvitation(
    val code: String = "",
    val role: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val used: Boolean = false,
    val usedBy: String = "",
    val status: String = InvitationStatus.PENDING,
    val createdAt: Date? = null,
    val expiresAt: Date? = null
) {
    // Estado que se muestra (RN-002): Usado > Anulado > Vencido > Pendiente
    fun state(now: Date = Date()): InvitationState = when {
        used -> InvitationState.USED
        status == InvitationStatus.REVOKED -> InvitationState.REVOKED
        expiresAt != null && expiresAt.before(now) -> InvitationState.EXPIRED
        else -> InvitationState.PENDING
    }
}

enum class InvitationState(val label: String) {
    PENDING("Pendiente"),
    USED("Usado"),
    EXPIRED("Vencido"),
    REVOKED("Anulado")
}

// Valores del campo "status" en Firestore (anular = "ANULADO"; los códigos nunca se borran)
object InvitationStatus {
    const val PENDING = "PENDIENTE"
    const val REVOKED = "ANULADO"
}

// Días que dura un código de invitación (de DT, y de jugador o apoderado en la US-003)
const val INVITATION_VALID_DAYS = 7
