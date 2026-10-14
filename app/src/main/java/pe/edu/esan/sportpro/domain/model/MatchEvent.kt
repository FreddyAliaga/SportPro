package pe.edu.esan.sportpro.domain.model

import java.util.UUID

enum class TeamSide(val displayName: String) {
    LOCAL("Local"),
    RIVAL("Rival")
}

enum class EventStatus {
    VIGENTE,
    ANULADO
}

data class MatchEvent(
    val id: String = UUID.randomUUID().toString(),
    val matchId: String = "",
    val typeId: String = "",
    val typeName: String = "",
    val affectsScore: Boolean = false,
    val minute: Int = 1,
    val teamSide: TeamSide = TeamSide.LOCAL,
    val playerId: String? = null,
    val playerName: String? = null,
    val playerDorsal: String? = null,
    val playerOutId: String? = null,
    val observation: String = "",
    val status: EventStatus = EventStatus.VIGENTE,
    val version: Int = 1,
    val isOfflinePending: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
