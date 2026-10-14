package pe.edu.esan.sportpro.domain.model

data class EventType(
    val id: String = "",
    val name: String = "",
    val iconName: String = "sports",
    val colorHex: String = "#006874",
    val affectsScore: Boolean = false,
    val requiresPlayer: Boolean = true,
    val active: Boolean = true
)
