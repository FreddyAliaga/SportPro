package pe.edu.esan.sportpro.data.model

import java.util.Date

// US-003 · Jugador de la academia: players/{playerId} (datos deportivos)
// Los menores de 18 NO tienen cuenta: el DT crea su perfil y su apoderado (PAD) lo gestiona.
data class Player(
    val id: String = "",
    val academyId: String = "",
    val teamId: String = "",
    val name: String = "",
    val category: String = TEAM_CATEGORIES.first(),
    val positionMain: String = PLAYER_POSITIONS.first(),
    val positionSecondary: String = "",          // "" = Ninguna
    val dorsal: Int? = null,                     // opcional
    val foot: String = PLAYER_FEET.first(),
    val isMinor: Boolean = true,                 // lo calcula el DT con la fecha de nacimiento
    val userId: String = "",                     // "" si no tiene cuenta; uid del jugador mayor de edad
    val guardianIds: List<String> = emptyList(), // uid de sus apoderados
    val hasEmergencyContact: Boolean = false,    // la app lo mantiene al guardar los datos privados
    val active: Boolean = true                   // "Retirar" = false (nunca se borra, RN-004)
) {
    // RN-005: un menor queda "Pendiente de apoderado" hasta tener apoderado y contacto de emergencia
    val status: PlayerStatus
        get() = when {
            !active -> PlayerStatus.INACTIVE
            isMinor && (guardianIds.isEmpty() || !hasEmergencyContact) -> PlayerStatus.PENDING_GUARDIAN
            else -> PlayerStatus.ACTIVE
        }

    val hasAccount: Boolean
        get() = userId.isNotEmpty()

    val initials: String
        get() = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }

    // Nombre corto para los códigos de invitación, que cualquiera con el código puede leer: "Mateo R."
    val shortName: String
        get() = shortPlayerName(name)
}

enum class PlayerStatus(val label: String) {
    ACTIVE("Activo"),
    PENDING_GUARDIAN("Pendiente de apoderado"),
    INACTIVE("Inactivo")
}

// Datos privados: players/{playerId}/private/data (RN-005, RN-014)
data class PlayerPrivate(
    val birthDate: Date? = null,          // Timestamp al mediodía UTC (igual que en el registro)
    val contactPhone: String = "",
    val emergencyName: String = "",
    val emergencyRelation: String = "",
    val emergencyPhone: String = "",
    val photoBase64: String = "",         // "" si no tiene foto (máx. 150 KB comprimido)
    val heightCm: Int? = null,            // opcional
    val weightKg: Int? = null             // opcional
) {
    val hasEmergencyContact: Boolean
        get() = emergencyName.isNotBlank() && emergencyRelation.isNotBlank() && emergencyPhone.isNotBlank()
}

// Historial del jugador: players/{playerId}/history/{id} (RN-004). Nunca se edita ni se borra.
data class PlayerHistoryEntry(
    val id: String = "",
    val type: String = "",
    val fromTeamName: String = "",
    val toTeamName: String = "",
    val byName: String = "",
    val at: Date? = null
)

// Valores del campo "type" del historial
object PlayerHistoryType {
    const val CREATED = "ALTA"
    const val TRANSFER = "TRASLADO"
    const val RETIRE = "RETIRO"
    const val REACTIVATE = "REACTIVACION"
}

// Datos que el DT o el ADM escriben en "Nuevo jugador" / "Editar datos deportivos".
// Los números se guardan como texto mientras se escriben, igual que RegisterForm.
data class PlayerSportForm(
    val name: String = "",
    val category: String = TEAM_CATEGORIES.first(),
    val positionMain: String = PLAYER_POSITIONS.first(),
    val positionSecondary: String = "",
    val dorsal: String = "",
    val foot: String = PLAYER_FEET.first(),
    val birthDate: String = ""            // solo dígitos DDMMAAAA (solo la editan el DT o el ADM)
)

// Datos que el apoderado o el jugador mayor editan
data class PlayerPrivateForm(
    val contactPhone: String = "",
    val emergencyName: String = "",
    val emergencyRelation: String = "",
    val emergencyPhone: String = "",
    val photoBase64: String = "",
    val heightCm: String = "",
    val weightKg: String = ""
)

fun Player.toSportForm(birthDate: Date?) = PlayerSportForm(
    name = name,
    category = category,
    positionMain = positionMain,
    positionSecondary = positionSecondary,
    dorsal = dorsal?.toString().orEmpty(),
    foot = foot,
    birthDate = birthDate?.toBirthDigits().orEmpty()
)

fun PlayerPrivate.toForm() = PlayerPrivateForm(
    contactPhone = contactPhone,
    emergencyName = emergencyName,
    emergencyRelation = emergencyRelation,
    emergencyPhone = emergencyPhone,
    photoBase64 = photoBase64,
    heightCm = heightCm?.toString().orEmpty(),
    weightKg = weightKg?.toString().orEmpty()
)

// Posiciones (la secundaria es opcional: "" = Ninguna). Las usa también la alineación (US-006).
val PLAYER_POSITIONS = listOf(
    "Portero",
    "Defensa central",
    "Lateral",
    "Mediocampista defensivo",
    "Mediocampista",
    "Mediocampista ofensivo",
    "Extremo",
    "Delantero"
)

val PLAYER_FEET = listOf("Derecho", "Izquierdo", "Ambos")

// Rangos de los datos físicos opcionales (RN-014)
val HEIGHT_RANGE_CM = 100..220
val WEIGHT_RANGE_KG = 20..150
val DORSAL_RANGE = 1..99
