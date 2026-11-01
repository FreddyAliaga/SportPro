package pe.edu.esan.sportpro.presentation.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.AppNotification
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.remote.NotificationRepository

// Estado de la bandeja de notificaciones (todos los roles). Se escucha desde el inicio para la insignia.
data class NotificationUiState(
    val profile: UserProfile? = null,
    val notifications: List<AppNotification> = emptyList(),
    val loaded: Boolean = false,
    val loadError: String? = null,
    val errorMessage: String? = null
) {
    // Más nuevas primero (una recién llegada aún sin hora del servidor va arriba)
    val sorted: List<AppNotification>
        get() = notifications.sortedByDescending { it.createdAt?.time ?: Long.MAX_VALUE }

    val unreadCount: Int get() = notifications.count { !it.read }
}

class NotificationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var listeningUid = ""

    fun start(profile: UserProfile) {
        if (profile.uid.isEmpty()) return
        if (profile.uid == listeningUid && listenJob != null) {
            _uiState.update { it.copy(profile = profile) }
            return
        }
        stop()
        listeningUid = profile.uid
        _uiState.value = NotificationUiState(profile = profile)
        listenJob = viewModelScope.launch {
            NotificationRepository.myNotificationsFlow(profile.uid).collect { result ->
                _uiState.update { state ->
                    result.fold(
                        onSuccess = { state.copy(notifications = it, loaded = true, loadError = null) },
                        onFailure = { state.copy(loadError = it.message ?: "No se pudieron cargar tus notificaciones.") }
                    )
                }
            }
        }
    }

    // Al cerrar sesión se deja de escuchar
    fun stop() {
        listenJob?.cancel()
        listenJob = null
        listeningUid = ""
        _uiState.value = NotificationUiState()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Al tocarla se marca como leída (si falla, se vuelve a intentar la próxima vez que se toque)
    fun markRead(notification: AppNotification) {
        if (notification.read) return
        viewModelScope.launch {
            NotificationRepository.markRead(notification.id).exceptionOrNull()?.let { e ->
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }
}
