package pe.edu.esan.sportpro.data.model

// Datos que la persona escribe en "Crea tu cuenta"
data class RegisterForm(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val birthDate: String = "",   // DD/MM/AAAA
    val password: String = ""
)

// Estado de la cuenta guardado en users/{uid}.status
object AccountStatus {
    const val PENDING_VERIFICATION = "pending_verification"
    const val ACTIVE = "active"
    const val BLOCKED = "blocked"     // bloqueado por moderación (US-013)
    const val INACTIVE = "inactive"   // desactivado por el ADM (US-002)
}

// Versión de los términos de uso que la persona acepta al registrarse (Supuestos 2.0)
const val TERMS_VERSION = "v1-2026-10"

// Edad mínima para tener cuenta en SportPro (RN-003)
const val MIN_ACCOUNT_AGE = 18
