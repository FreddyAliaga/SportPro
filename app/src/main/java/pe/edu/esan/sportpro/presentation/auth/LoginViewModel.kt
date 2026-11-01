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
    val profile: UserProfile? = null,
    // US-002: el ADM desactivó (o se bloqueó) la cuenta con la sesión abierta: hay que cerrarla
    val sessionEndedMessage: String? = null,
    // No se pudo volver a cargar el perfil (tras un cierre de la app): se ofrece "Reintentar"
    val profileError: String? = null
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var restoreJob: Job? = null
    private var profileJob: Job? = null
    // true tras "Cerrar sesión": las pantallas que aún se ven un instante no deben recargar el perfil
    private var sessionClosed = false

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
            when (result.getOrNull()) {
                SignInOutcome.ACTIVE -> {
                    // Se carga el perfil ANTES de ir al inicio, para que el menú ya tenga el rol
                    val profile = FirebaseAuthManager.loadCurrentProfile()
                    if (profile.isSuccess) {
                        _uiState.update { it.copy(isLoading = false, password = "", profile = profile.getOrNull()) }
                        watchProfile()
                        onActive()
                    } else {
                        FirebaseAuthManager.signOut()
                        _uiState.update { it.copy(isLoading = false, errorMessage = profile.exceptionOrNull()?.message) }
                    }
                }
                SignInOutcome.NOT_VERIFIED -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onNotVerified()
                }
                null -> _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
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
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            var alreadyInHome = false
            while (true) {
                val result = FirebaseAuthManager.loadCurrentProfile()
                if (result.exceptionOrNull()?.message == FirebaseAuthManager.NO_CONNECTION_MESSAGE) {
                    // Sin internet: NO se cierra la sesión. Entra al inicio (con indicador de carga)
                    // y se vuelve a intentar cada 5 segundos hasta tener el perfil.
                    if (!alreadyInHome) {
                        alreadyInHome = true
                        onLoggedIn()
                    }
                    delay(5000)
                    continue
                }
                val profile = result.getOrNull()
                // Se cierra la sesión solo si el perfil no existe o la cuenta está bloqueada/desactivada
                if (profile == null || profile.status == AccountStatus.BLOCKED || profile.status == AccountStatus.INACTIVE) {
                    FirebaseAuthManager.signOut()
                    onLoggedOut()
                } else {
                    _uiState.update { it.copy(profile = profile) }
                    watchProfile()
                    if (!alreadyInHome) onLoggedIn()
                }
                break
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val result = FirebaseAuthManager.loadCurrentProfile()
            _uiState.update { it.copy(profile = result.getOrNull(), errorMessage = result.exceptionOrNull()?.message) }
            if (result.isSuccess) watchProfile()
        }
    }

    // Si Android cerró la app (process death), al volver se abre directo la última pantalla sin pasar
    // por "start": el perfil de la sesión está vacío y se vuelve a cargar.
    // - Sin internet: se reintenta cada 5 segundos hasta que vuelva la conexión.
    // - Sin sesión o sin perfil: se cierra la sesión ("No encontramos tu cuenta…").
    // - Otro error: se muestra con el botón "Reintentar" (profileError), en vez de quedarse cargando.
    fun ensureProfile() {
        if (_uiState.value.profile != null || restoreJob?.isActive == true) return
        if (sessionClosed) return   // se acaba de cerrar la sesión a propósito
        if (!FirebaseAuthManager.hasVerifiedSession()) {
            endSession(FirebaseAuthManager.ACCOUNT_NOT_FOUND_MESSAGE)
            return
        }
        _uiState.update { it.copy(profileError = null) }
        restoreJob = viewModelScope.launch {
            while (true) {
                val result = FirebaseAuthManager.loadCurrentProfile()
                val profile = result.getOrNull()
                val error = result.exceptionOrNull()?.message
                when {
                    profile != null -> {
                        _uiState.update { it.copy(profile = profile) }
                        watchProfile()   // si la cuenta está desactivada, watchProfile cierra la sesión
                    }
                    error == FirebaseAuthManager.NO_CONNECTION_MESSAGE -> {
                        delay(5000)
                        continue
                    }
                    error == FirebaseAuthManager.PROFILE_NOT_FOUND_MESSAGE ->
                        endSession(FirebaseAuthManager.ACCOUNT_NOT_FOUND_MESSAGE)
                    else -> _uiState.update { it.copy(profileError = error ?: "No pudimos cargar tu cuenta.") }
                }
                break
            }
        }
    }

    // US-002: mientras hay sesión se escucha el perfil propio. Si el ADM desactiva la cuenta, se pide
    // cerrar la sesión (lo hace AppNavGraph); si cambia algo (por ejemplo, el equipo del DT), se
    // actualiza el perfil de la sesión y el inicio y el plantel se actualizan solos.
    private fun watchProfile() {
        sessionClosed = false
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            FirebaseAuthManager.currentProfileFlow().collect { profile ->
                when {
                    profile == null -> endSession(FirebaseAuthManager.ACCOUNT_NOT_FOUND_MESSAGE)
                    profile.status == AccountStatus.INACTIVE -> endSession("Tu cuenta fue desactivada por la administración.")
                    profile.status == AccountStatus.BLOCKED ->
                        endSession("Tu cuenta está bloqueada. Comunícate con el administrador de tu academia.")
                    profile != _uiState.value.profile -> _uiState.update { it.copy(profile = profile) }
                }
            }
        }
    }

    // Pide a AppNavGraph cerrar la sesión y explicar el motivo en "Iniciar sesión"
    private fun endSession(message: String) {
        _uiState.update { it.copy(sessionEndedMessage = message) }
    }

    // Tras cerrar una sesión que terminó la administración: se explica el motivo en "Iniciar sesión"
    fun showMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun signOut() {
        sessionClosed = true
        restoreJob?.cancel()
        profileJob?.cancel()
        FirebaseAuthManager.signOut()
        _uiState.value = LoginUiState()
    }
}
