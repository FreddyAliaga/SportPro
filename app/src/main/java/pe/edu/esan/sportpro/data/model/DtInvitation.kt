package pe.edu.esan.sportpro.data.model

import java.util.Date

// Código de invitación visto por el ADM en "Usuarios e invitaciones" (invitations/{codigo})
data class DtInvitation(
    val code: String = "",
    val role: String = "",
    val teamId: String = "",
    val teamName: String = "",
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

// Días que dura un código de invitación de DT
const val INVITATION_VALID_DAYS = 7
