package pe.edu.esan.sportpro.domain.model

data class Exercise(
    val id: String = "",
    val academyId: String = "halcones_1",
    val name: String = "",
    val objective: String = "",
    val description: String = "",
    val durationMinutes: Int = 15,
    val category: String = "Técnica",
    val materials: List<String> = emptyList()
)
