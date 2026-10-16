package pe.edu.esan.sportpro.domain.model

data class AcademyUser(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.DT,
    val academyId: String = "academy_1",
    val teamId: String? = null,
    val teamName: String? = null,
    val assignedTeamNames: List<String> = emptyList(),
    val active: Boolean = true,
    val birthDate: String? = null,
    val phone: String? = null
)
