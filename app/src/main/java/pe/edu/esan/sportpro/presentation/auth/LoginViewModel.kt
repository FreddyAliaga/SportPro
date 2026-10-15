package pe.edu.esan.sportpro.presentation.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.remote.FirebaseAuthManager
import pe.edu.esan.sportpro.data.remote.SignInOutcome

// Estado del inicio de sesión, la recuperación de contraseña y el perfil de la sesión (US-001)
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resetEmail: String = "",
    val resetEmailError: String? = null,
    val resetSent: Boolean = false,
    val profile: UserProfile? = null
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // ---------- Iniciar sesión ----------
    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun login(onActive: () -> Unit, onNotVerified: () -> Unit) {
        val state = _uiState.value
        val emailError = if (!Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()) "Escribe un correo válido." else null
        val passwordError = if (state.password.isEmpty()) "Escribe tu contraseña." else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = FirebaseAuthManager.signIn(state.email, state.password)
            _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
            when (result.getOrNull()) {
                SignInOutcome.ACTIVE -> {
                    _uiState.update { it.copy(password = "") }
                    loadProfile()
                    onActive()
                }
                SignInOutcome.NOT_VERIFIED -> onNotVerified()
                null -> Unit   // el error ya se muestra en pantalla
            }
        }
    }

    // ---------- Recuperar contraseña ----------
    fun startPasswordReset() {
        // Se propone el correo que ya escribió en "Iniciar sesión"
        _uiState.update { it.copy(resetEmail = it.email, resetEmailError = null, resetSent = false, errorMessage = null) }
    }

    fun onResetEmailChange(value: String) {
        _uiState.update { it.copy(resetEmail = value, resetEmailError = null, errorMessage = null) }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.resetEmail.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(resetEmailError = "Escribe un correo válido.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = FirebaseAuthManager.sendPasswordReset(email)
            _uiState.update {
                it.copy(isLoading = false, resetSent = result.isSuccess, errorMessage = result.exceptionOrNull()?.message)
            }
        }
    }

    // ---------- Sesión ----------
    // Al abrir la app: si la sesión sigue abierta y el correo está verificado, entra directo al inicio
    fun restoreSession(onLoggedIn: () -> Unit, onLoggedOut: () -> Unit) {
        if (!FirebaseAuthManager.hasVerifiedSession()) {
            FirebaseAuthManager.signOut()
            onLoggedOut()
            return
        }
        viewModelScope.launch {
            val result = FirebaseAuthManager.loadCurrentProfile()
            val profile = result.getOrNull()
            if (profile != null && profile.status == AccountStatus.ACTIVE) {
                _uiState.update { it.copy(profile = profile) }
                onLoggedIn()
            } else {
                FirebaseAuthManager.signOut()
                onLoggedOut()
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val result = FirebaseAuthManager.loadCurrentProfile()
            _uiState.update { it.copy(profile = result.getOrNull(), errorMessage = result.exceptionOrNull()?.message) }
        }
    }

    fun signOut() {
        FirebaseAuthManager.signOut()
        _uiState.value = LoginUiState()
    }
}
