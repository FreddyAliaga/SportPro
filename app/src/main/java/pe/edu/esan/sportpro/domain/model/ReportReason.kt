package pe.edu.esan.sportpro.domain.model

enum class ReportReason(val displayName: String) {
    CONTENIDO_OFENSIVO("Contenido ofensivo"),
    INFORMACION_FALSA("Información falsa"),
    DATOS_DE_MENOR("Datos de un menor"),
    CONVOCATORIA_ENGANOSA("Convocatoria engañosa"),
    OTRO("Otro")
}
