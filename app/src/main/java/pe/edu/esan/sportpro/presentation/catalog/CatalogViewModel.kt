package pe.edu.esan.sportpro.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.DUPLICATE_EVENT_NAME_MESSAGE
import pe.edu.esan.sportpro.data.model.EventType
import pe.edu.esan.sportpro.data.model.EventTypeForm
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.toForm
import pe.edu.esan.sportpro.data.model.validateEventTypeName
import pe.edu.esan.sportpro.data.remote.EventTypeRepository

// Estado de la US-007 (catálogo de eventos). El ADM lo edita; el DT solo lo ve.
data class CatalogUiState(
    val profile: UserProfile? = null,
    val types: List<EventType> = emptyList(),
    val loaded: Boolean = false,          // ya llegó la primera lectura (mientras tanto, carga y no "vacío")
    val loadError: String? = null,
    val defaultsError: String? = null,    // no se pudo crear el catálogo de fábrica (por ejemplo, sin internet)
    val creatingDefaults: Boolean = false,
    // Nuevo / editar tipo de evento
    val form: EventTypeForm = EventTypeForm(),
    val nameError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val canEdit: Boolean get() = profile?.role == "ADM"

    val activeTypes: List<EventType>
        get() = types.filter { it.active }.sortedWith(compareBy({ it.order }, { it.name.lowercase() }))

    val inactiveTypes: List<EventType>
        get() = types.filter { !it.active }.sortedBy { it.name.lowercase() }

    // Tipo que se está editando (null si es nuevo)
    val editingType: EventType? get() = types.firstOrNull { it.id == form.id && form.id.isNotEmpty() }

    // Posición de un tipo nuevo: al final de la lista
    val nextOrder: Int get() = (types.maxOfOrNull { it.order } ?: -1) + 1
}

class CatalogViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var listeningAcademyId = ""
    private var defaultsChecked = false   // el ADM revisa el catálogo de fábrica una vez por sesión

    // ---------- Inicio: escuchar el catálogo de la academia del perfil (ADM o DT) ----------
    fun start(profile: UserProfile) {
        if (profile.role !in listOf("ADM", "DT") || profile.academyId.isEmpty()) return
        if (profile.academyId == listeningAcademyId && listenJob != null) {
            _uiState.update { it.copy(profile = profile) }
            return
        }
        stop()
        listeningAcademyId = profile.academyId
        _uiState.value = CatalogUiState(profile = profile)
        listenJob = viewModelScope.launch {
            EventTypeRepository.eventTypesFlow(profile.academyId).collect { result ->
                _uiState.update { state ->
                    result.fold(
                        onSuccess = { state.copy(types = it, loaded = true, loadError = null) },
                        onFailure = { state.copy(loadError = it.message ?: "No se pudo cargar el catálogo.") }
                    )
                }
            }
        }
        // Al entrar el ADM (al inicio o al catálogo): si su academia no tiene catálogo, se crea el de fábrica
        if (profile.role == "ADM" && !defaultsChecked) ensureDefaults()
    }

    // Al cerrar sesión se deja de escuchar el catálogo
    fun stop() {
        listenJob?.cancel()
        listenJob = null
        listeningAcademyId = ""
        defaultsChecked = false
        _uiState.value = CatalogUiState()
    }

    // "Crear catálogo" / reintento si no se pudo crear (solo el ADM)
    fun ensureDefaults() {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (profile.role != "ADM" || state.creatingDefaults) return
        viewModelScope.launch {
            _uiState.update { it.copy(creatingDefaults = true, defaultsError = null) }
            val result = EventTypeRepository.ensureDefaults(profile.academyId)
            if (result.isSuccess) defaultsChecked = true
            _uiState.update {
                it.copy(
                    creatingDefaults = false,
                    defaultsError = result.exceptionOrNull()?.message,
                    infoMessage = if (result.getOrNull() == true) "¡Listo! Creamos el catálogo de eventos de tu academia." else it.infoMessage
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    // ---------- Nuevo / editar tipo de evento (ADM) ----------
    fun startNew() {
        _uiState.update { it.copy(form = EventTypeForm(), nameError = null, errorMessage = null, infoMessage = null) }
    }

    fun startEdit(type: EventType) {
        _uiState.update { it.copy(form = type.toForm(), nameError = null, errorMessage = null, infoMessage = null) }
    }

    // Si Android cerró la app: el formulario se vuelve a llenar con el id de la ruta cuando llega el catálogo.
    // Devuelve false si el tipo ya no existe (la pantalla vuelve a la lista).
    fun restoreForm(typeId: String): Boolean {
        val state = _uiState.value
        if (typeId.isEmpty() || state.form.id == typeId || !state.loaded) return true
        val type = state.types.firstOrNull { it.id == typeId } ?: return false
        startEdit(type)
        return true
    }

    // Afectar el marcador o pedir jugador exige saber de qué equipo es: se enciende "Requiere equipo".
    // Inicio y Fin nunca cambian esas tres propiedades.
    fun onFormChange(form: EventTypeForm) {
        val current = _uiState.value.form
        val fixed = when {
            form.alwaysActive -> form.copy(affectsScore = false, requiresPlayer = false, requiresTeam = false)
            (form.affectsScore && !current.affectsScore) || (form.requiresPlayer && !current.requiresPlayer) ->
                form.copy(requiresTeam = true)
            // Al apagar "Requiere equipo" se apagan también marcador y jugador
            !form.requiresTeam && current.requiresTeam -> form.copy(affectsScore = false, requiresPlayer = false)
            else -> form
        }
        _uiState.update { it.copy(form = fixed, nameError = null, errorMessage = null) }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (!state.canEdit || state.isLoading) return   // solo el ADM; evita el doble toque
        val nameError = validateEventTypeName(state.form, state.types)
        _uiState.update { it.copy(nameError = nameError) }
        if (nameError != null) return

        val isNew = state.form.id.isEmpty()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = EventTypeRepository.saveEventType(profile.academyId, state.form, state.nextOrder)
            val error = result.exceptionOrNull()?.message
            _uiState.update {
                it.copy(
                    isLoading = false,
                    // El nombre repetido se muestra debajo del campo
                    nameError = if (error == DUPLICATE_EVENT_NAME_MESSAGE) error else null,
                    errorMessage = if (error == DUPLICATE_EVENT_NAME_MESSAGE) null else error,
                    infoMessage = if (result.isSuccess) {
                        if (isNew) "¡Listo! Agregamos ${state.form.name.trim()} al catálogo." else "Cambios guardados en ${state.form.name.trim()}."
                    } else null
                )
            }
            if (result.isSuccess) onSaved()
        }
    }

    // "Desactivar / Reactivar". Nada se borra: un tipo desactivado deja de ofrecerse en los partidos nuevos,
    // pero los eventos ya registrados no cambian.
    fun setActive(type: EventType, active: Boolean, onDone: () -> Unit) {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (!state.canEdit || state.isLoading) return
        if (type.alwaysActive && !active) return   // Inicio y Fin siempre están activos
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = EventTypeRepository.setActive(profile.academyId, type.id, active)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) {
                        if (active) "${type.name} está activo otra vez." else "${type.name} se desactivó. Los partidos ya registrados no cambian."
                    } else null
                )
            }
            if (result.isSuccess) onDone()
        }
    }
}
