package pe.edu.esan.sportpro.data.model

import java.text.Normalizer

// US-007 · Tipo de evento del catálogo de la academia: academies/{academyId}/eventTypes/{typeId}
// Nada se borra: un tipo que ya no se usa se desactiva (active = false).
// Los eventos del partido (US-008) copian nombre y afectaMarcador: cambiar el catálogo no los modifica.
data class EventType(
    val id: String = "",
    val name: String = "",
    val icon: String = EVENT_ICONS.first().key,     // clave de EVENT_ICONS
    val color: String = EVENT_COLORS.first().hex,   // hex de EVENT_COLORS
    val affectsScore: Boolean = false,
    val requiresPlayer: Boolean = false,
    val requiresTeam: Boolean = false,
    val allowsNote: Boolean = true,
    val active: Boolean = true,
    val system: Boolean = false,                    // true en los de fábrica
    val order: Int = 0
) {
    // 'Inicio' y 'Fin' no se desactivan ni cambian sus propiedades de marcador, jugador y equipo
    val alwaysActive: Boolean
        get() = id in ALWAYS_ACTIVE_EVENT_IDS
}

// Datos que el ADM escribe en "Nuevo / Editar tipo de evento"
data class EventTypeForm(
    val id: String = "",               // "" = tipo nuevo
    val name: String = "",
    val icon: String = EVENT_ICONS.first().key,
    val color: String = EVENT_COLORS.first().hex,
    val affectsScore: Boolean = false,
    val requiresPlayer: Boolean = false,
    val requiresTeam: Boolean = true,
    val allowsNote: Boolean = true
) {
    val alwaysActive: Boolean
        get() = id in ALWAYS_ACTIVE_EVENT_IDS
}

fun EventType.toForm() = EventTypeForm(
    id = id,
    name = name,
    icon = icon,
    color = color,
    affectsScore = affectsScore,
    requiresPlayer = requiresPlayer,
    requiresTeam = requiresTeam,
    allowsNote = allowsNote
)

// Un color de la paleta del catálogo (son datos, no decoración: identifican al evento)
data class EventColor(val hex: String, val label: String)

val EVENT_COLORS = listOf(
    EventColor("#0E6B6F", "Turquesa"),
    EventColor("#2E7D32", "Verde"),
    EventColor("#1565C0", "Azul"),
    EventColor("#F2B705", "Amarillo"),
    EventColor("#C2410C", "Rojo"),
    EventColor("#6A4C93", "Morado"),
    EventColor("#5F6B6D", "Gris"),
    EventColor("#E8710A", "Naranja")
)

// Un ícono del catálogo: la clave se guarda en Firestore y el dibujo lo pone la pantalla
// (presentation/components/EventTypeVisuals.kt). label = contentDescription en español.
data class EventIcon(val key: String, val label: String)

val EVENT_ICONS = listOf(
    EventIcon("balon", "Balón"),
    EventIcon("inicio", "Inicio"),
    EventIcon("fin", "Bandera de llegada"),
    EventIcon("tarjeta", "Tarjeta"),
    EventIcon("cambio", "Cambio de jugadores"),
    EventIcon("silbato", "Silbato"),
    EventIcon("penal", "Punto penal"),
    EventIcon("prohibido", "Prohibido"),
    EventIcon("banderin", "Banderín"),
    EventIcon("mano", "Mano"),
    EventIcon("escudo", "Escudo"),
    EventIcon("reloj", "Reloj"),
    EventIcon("advertencia", "Advertencia"),
    EventIcon("estrella", "Estrella")
)

const val EVENT_NAME_MIN = 2
const val EVENT_NAME_MAX = 30

// 'Inicio' y 'Fin' siempre están activos
val ALWAYS_ACTIVE_EVENT_IDS = setOf("inicio", "fin")

// Catálogo de fábrica (id fijo = clave, system = true). Solo 'gol' afecta el marcador.
val DEFAULT_EVENT_TYPES = listOf(
    EventType("inicio", "Inicio", "inicio", "#0E6B6F", affectsScore = false, requiresPlayer = false, requiresTeam = false),
    EventType("fin", "Fin", "fin", "#5F6B6D", affectsScore = false, requiresPlayer = false, requiresTeam = false),
    EventType("gol", "Gol", "balon", "#2E7D32", affectsScore = true, requiresPlayer = true, requiresTeam = true),
    EventType("tarjeta_amarilla", "Tarjeta amarilla", "tarjeta", "#F2B705", requiresPlayer = true, requiresTeam = true),
    EventType("tarjeta_roja", "Tarjeta roja", "tarjeta", "#C2410C", requiresPlayer = true, requiresTeam = true),
    // La US-008 pedirá también al jugador que sale
    EventType("cambio", "Cambio", "cambio", "#1565C0", requiresPlayer = true, requiresTeam = true),
    EventType("falta", "Falta", "silbato", "#E8710A", requiresTeam = true),
    EventType("penal", "Penal", "penal", "#6A4C93", requiresTeam = true),
    EventType("fuera_de_juego", "Fuera de juego", "prohibido", "#5F6B6D", requiresTeam = true),
    EventType("tiro_de_esquina", "Tiro de esquina", "banderin", "#0E6B6F", requiresTeam = true),
    EventType("saque_lateral", "Saque lateral", "mano", "#1565C0", requiresTeam = true),
    EventType("saque_de_meta", "Saque de meta", "escudo", "#2E7D32", requiresTeam = true)
).mapIndexed { index, type -> type.copy(allowsNote = true, active = true, system = true, order = index) }

const val DUPLICATE_EVENT_NAME_MESSAGE = "Ya existe un tipo con ese nombre."
const val EMPTY_CATALOG_MESSAGE = "Tu academia aún no tiene catálogo. Pide a la administración que lo revise."

// Nombre para comparar: sin espacios de más, sin tildes y en minúsculas ("Tarjeta  Roja" == "tarjeta roja")
fun eventNameKey(name: String): String =
    Normalizer.normalize(name.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

// Error del nombre (null si está bien). others = los demás tipos de la academia.
fun validateEventTypeName(form: EventTypeForm, others: List<EventType>): String? {
    val name = form.name.trim()
    return when {
        name.length < EVENT_NAME_MIN -> "Escribe un nombre de al menos $EVENT_NAME_MIN letras."
        name.length > EVENT_NAME_MAX -> "Máximo $EVENT_NAME_MAX caracteres."
        others.any { it.id != form.id && eventNameKey(it.name) == eventNameKey(name) } -> DUPLICATE_EVENT_NAME_MESSAGE
        else -> null
    }
}
