package pe.edu.esan.sportpro.domain.model

data class InvitationCode(
    val code: String = "",
    val academyId: String = "academy_1",
    val role: UserRole = UserRole.DT,
    val teamId: String? = null,
    val teamName: String? = null,
    val status: InvitationStatus = InvitationStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L),
    val usedBy: String? = null,
    val usedByName: String? = null,
    val usedAtDate: String? = null,
    val revokedBy: String? = null
)
