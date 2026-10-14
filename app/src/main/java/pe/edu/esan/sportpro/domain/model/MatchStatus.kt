package pe.edu.esan.sportpro.domain.model

enum class MatchStatus(val displayName: String) {
    PROGRAMADO("Programado"),
    CONVOCADO("Convocado"),
    EN_CURSO("En curso"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado")
}
