package pe.edu.esan.sportpro.domain.model

data class Academy(
    val id: String = "academy_1",
    val name: String = "Academia Los Halcones",
    val logoBase64: String? = null,
    val city: String = "Lima",
    val institutionalEmail: String = "contacto@loshalcones.pe",
    val verified: Boolean = false,
    val callupNoticeHours: Int = 24,
    val callupConfirmHours: Int = 2,
    val createdAt: Long = System.currentTimeMillis()
)
