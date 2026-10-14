package pe.edu.esan.sportpro.domain.model

data class Team(
    val id: String = "",
    val academyId: String = "academy_1",
    val name: String = "",
    val category: Category = Category.SUB_10,
    val coachId: String? = null,
    val coachName: String? = null,
    val maxStarters: Int = 11,
    val maxSubstitutes: Int = 12,
    val active: Boolean = true,
    val playerCount: Int = 0
)
