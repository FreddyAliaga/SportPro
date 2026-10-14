package pe.edu.esan.sportpro.domain.model

enum class InvitationStatus(val displayName: String) {
    PENDING("Pendiente"),
    USED("Usado"),
    EXPIRED("Vencido"),
    REVOKED("Anulado")
}
