package pe.edu.esan.sportpro.presentation.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.Invitation
import pe.edu.esan.sportpro.data.model.MIN_ACCOUNT_AGE
import pe.edu.esan.sportpro.data.model.RegisterForm
import pe.edu.esan.sportpro.data.remote.FirebaseAuthManager
import pe.edu.esan.sportpro.data.remote.InvitationRepository
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

// Errores por campo del formulario "Crea tu cuenta"
data class FormErrors(
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val birthDate: String? = null,
    val password: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(fullName, email, phone, birthDate, password).any { it != null }
}

// Estado de toda la US-001 (una sola fuente de verdad para las pantallas)
data class RegisterUiState(
    val code: String = "",
    val codeError: String? = null,
    val invitation: Invitation? = null,
    val form: RegisterForm = RegisterForm(),
    val formErrors: FormErrors = FormErrors(),
    val termsAccepted: Boolean = false,
    val termsError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val resendSeconds: Int = 0
)

class RegisterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null

    // ---------- Paso 1: código de invitación ----------
    fun onCodeChange(value: String) {
        _uiState.update { it.copy(code = value.uppercase().take(12), codeError = null, invitation = null) }
    }

    fun validateCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, codeError = null) }
            val result = InvitationRepository.findValidInvitation(_uiState.value.code)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    invitation = result.getOrNull(),
                    codeError = result.exceptionOrNull()?.message
                )
            }
        }
    }

    // ---------- Paso 2: crear cuenta ----------
    fun onFormChange(form: RegisterForm) {
        _uiState.update { it.copy(form = form, formErrors = FormErrors(), errorMessage = null) }
    }

    // Devuelve la edad si el formulario es válido; null si hay errores
    fun validateForm(): Int? {
        val form = _uiState.value.form
        val age = ageFrom(form.birthDate)
        val errors = FormErrors(
            fullName = if (form.fullName.trim().length < 3) "Escribe tu nombre completo." else null,
            email = if (!Patterns.EMAIL_ADDRESS.matcher(form.email.trim()).matches()) "Escribe un correo válido." else null,
            phone = if (!Regex("^9\\d{8}$").matches(form.phone.trim())) "El celular debe tener 9 dígitos y empezar con 9." else null,
            birthDate = if (age == null) "Usa el formato DD/MM/AAAA." else null,
            password = passwordError(form.password)
        )
        _uiState.update { it.copy(formErrors = errors) }
        return if (errors.hasErrors) null else age
    }

    // Casilla "Acepto los términos de uso"
    fun onTermsChange(accepted: Boolean) {
        _uiState.update { it.copy(termsAccepted = accepted, termsError = null) }
    }

    // RN-003: solo las personas de 18 años o más tienen cuenta
    fun isMinor(age: Int): Boolean = age < MIN_ACCOUNT_AGE

    // ---------- Paso 3: crear la cuenta ----------
    fun createAccount(onSuccess: () -> Unit) {
        val state = _uiState.value
        val invitation = state.invitation ?: return
        val age = ageFrom(state.form.birthDate) ?: return
        if (isMinor(age)) return
        if (!state.termsAccepted) {
            _uiState.update { it.copy(termsError = "Debes aceptar los términos de uso para continuar.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = FirebaseAuthManager.registerWithInvitation(state.form, invitation)
            _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
            if (result.isSuccess) {
                startResendCooldown()
                onSuccess()
            }
        }
    }

    // ---------- Paso 4: verificar correo (también se usa al iniciar sesión con un correo sin verificar) ----------
    fun currentEmail(): String = FirebaseAuthManager.currentEmail().ifEmpty { _uiState.value.form.email }

    fun resendEmail() {
        if (_uiState.value.resendSeconds > 0) return
        viewModelScope.launch {
            val result = FirebaseAuthManager.resendVerificationEmail()
            _uiState.update {
                it.copy(
                    infoMessage = if (result.isSuccess) "Te enviamos un nuevo enlace." else null,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
            if (result.isSuccess) startResendCooldown()
        }
    }

    fun checkVerified(onActive: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val verified = FirebaseAuthManager.reloadAndCheckVerified()
            if (verified.getOrNull() != true) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = verified.exceptionOrNull()?.message
                            ?: "Aún no confirmas tu correo. Abre el enlace que te enviamos."
                    )
                }
                return@launch
            }
            val status = FirebaseAuthManager.completeVerification()
            _uiState.update { it.copy(isLoading = false, errorMessage = status.exceptionOrNull()?.message) }
            if (status.getOrNull() == AccountStatus.ACTIVE) onActive()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun signOutAndReset() {
        FirebaseAuthManager.signOut()
        cooldownJob?.cancel()
        _uiState.value = RegisterUiState()
    }

    private fun startResendCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (s in 60 downTo 0) {
                _uiState.update { it.copy(resendSeconds = s) }
                if (s > 0) delay(1000)
            }
        }
    }

    // ---------- Reglas de validación ----------
    private fun passwordError(password: String): String? = when {
        password.length < 8 -> "Mínimo 8 caracteres."
        !password.any { it.isUpperCase() } -> "Incluye al menos una mayúscula."
        !password.any { it.isLowerCase() } -> "Incluye al menos una minúscula."
        !password.any { it.isDigit() } -> "Incluye al menos un número."
        else -> null
    }

    private fun ageFrom(birthDate: String): Int? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
            val date = LocalDate.parse(birthDate.trim(), formatter)
            if (date.isAfter(LocalDate.now())) null else Period.between(date, LocalDate.now()).years
        } catch (e: Exception) {
            null
        }
    }
}
