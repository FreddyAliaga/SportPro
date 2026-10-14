package pe.edu.esan.sportpro.domain.model

enum class Category(val displayName: String, val sortOrder: Int) {
    SUB_8("Sub-8", 1),
    SUB_10("Sub-10", 2),
    SUB_12("Sub-12", 3),
    SUB_15("Sub-15", 4),
    SUB_17("Sub-17", 5),
    PRIMERA("Primera", 6),
    OTRA("Otra", 7);

    companion object {
        fun fromDisplayName(name: String): Category {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: SUB_10
        }
    }
}
