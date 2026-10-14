package pe.edu.esan.sportpro.domain.model

data class CommunityReport(
    val id: String = "",
    val contentId: String = "",
    val contentType: String = "POST", // "POST" or "COMMENT"
    val contentPreview: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val reportedByUserId: String = "",
    val reason: ReportReason = ReportReason.CONTENIDO_OFENSIVO,
    val status: String = "PENDIENTE", // "PENDIENTE", "RESTAURADO", "MANTENIDO_OCULTO", "ELIMINADO"
    val createdAt: Long = System.currentTimeMillis()
)
