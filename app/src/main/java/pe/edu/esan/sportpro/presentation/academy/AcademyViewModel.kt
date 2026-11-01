package pe.edu.esan.sportpro.presentation.academy

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.Academy
import pe.edu.esan.sportpro.data.model.AcademyForm
import pe.edu.esan.sportpro.data.model.AcademyFormErrors
import pe.edu.esan.sportpro.data.model.validateAcademyForm
import pe.edu.esan.sportpro.data.model.DtInvitation
import pe.edu.esan.sportpro.data.model.InvitationState
import pe.edu.esan.sportpro.data.model.TEAM_CATEGORIES
import pe.edu.esan.sportpro.data.model.Team
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.categoryOrder
import pe.edu.esan.sportpro.data.model.toForm
import pe.edu.esan.sportpro.data.remote.AcademyRepository
import pe.edu.esan.sportpro.presentation.components.compressImageToBase64

// Datos que el ADM escribe en "Nuevo equipo" / "Editar equipo"
data class TeamForm(
    val id: String = "",               // "" = equipo nuevo
    val name: String = "",
    val category: String = TEAM_CATEGORIES.first(),
    val maxStarters: String = "11",
    val maxSubstitutes: String = "12",
    val coachUid: String = "",         // "" = Sin DT
    val coachChanged: Boolean = false  // true = el ADM tocó "Cambiar" (si no, no se toca a ningún DT)
)

data class TeamFormErrors(
    val name: String? = null,
    val maxStarters: String? = null,
    val maxSubstitutes: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(name, maxStarters, maxSubstitutes).any { it != null }
}

// Estado de toda la US-002 para el ADM (una sola fuente de verdad para las pantallas)
data class AcademyUiState(
    val admin: UserProfile? = null,
    val academy: Academy? = null,
    val teams: List<Team> = emptyList(),
    val members: List<UserProfile> = emptyList(),
    val invitations: List<DtInvitation> = emptyList(),
    // Ya llegó la primera lectura de cada lista (mientras tanto se muestra la carga, no "vacío")
    val teamsLoaded: Boolean = false,
    val membersLoaded: Boolean = false,
    val invitationsLoaded: Boolean = false,
    // Un error por fuente ("academia", "equipos", "usuarios", "códigos"): que una lista
    // cargue bien no borra el error de otra
    val loadErrors: Map<String, String> = emptyMap(),
    // Editar academia (editingAcademy = el formulario ya se llenó con los datos de la academia)
    val editingAcademy: Boolean = false,
    val academyForm: AcademyForm = AcademyForm(),
    val academyErrors: AcademyFormErrors = AcademyFormErrors(),
    val logoLoading: Boolean = false,
    // Nuevo / editar equipo
    val teamForm: TeamForm = TeamForm(),
    val teamErrors: TeamFormErrors = TeamFormErrors(),
    // Último código generado (pantalla "Invita a un entrenador")
    val generatedCode: String = "",
    val generatedTeamName: String = "",
    // Filtro de "Usuarios" (null = Todos)
    val roleFilter: String? = null,
    val isLoading: Boolean = false,
    // Carga aparte para "Invitar DT" (así "Guardar equipo" no muestra carga al invitar)
    val invitationLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val academyId: String get() = admin?.academyId.orEmpty()

    // Primer error de lectura pendiente (null si todo cargó bien)
    val loadError: String? get() = loadErrors.values.firstOrNull()

    // DT del equipo = usuarios DT con ese teamId cuya cuenta NO está desactivada (normalmente 0 o 1).
    // Incluye al DT que aún no verifica su correo: al verificarlo ya dirige el equipo.
    fun coachesOf(teamId: String): List<UserProfile> =
        if (teamId.isEmpty()) emptyList()
        else members.filter { it.role == "DT" && it.status != AccountStatus.INACTIVE && it.teamId == teamId }

    // Código de DT pendiente (sin usar, sin anular y sin vencer) de un equipo, o null
    fun pendingDtCodeOf(teamId: String): DtInvitation? =
        invitations.firstOrNull { it.role == "DT" && it.teamId == teamId && it.state() == InvitationState.PENDING }

    // DT que muestra "Editar equipo": el elegido con "Cambiar" o, si no se tocó, el DT actual
    val teamFormCoachUid: String
        get() = if (teamForm.coachChanged) teamForm.coachUid else coachesOf(teamForm.id).firstOrNull()?.uid.orEmpty()

    fun isTeamActive(teamId: String): Boolean = teams.firstOrNull { it.id == teamId }?.active == true

    // Solo se invita a un DT para un equipo activo, sin DT y sin otro código de DT pendiente
    fun canInviteCoach(teamId: String): Boolean =
        isTeamActive(teamId) && coachesOf(teamId).isEmpty() && pendingDtCodeOf(teamId) == null

    // DT que se pueden asignar a un equipo
    val activeCoaches: List<UserProfile>
        get() = members.filter { it.role == "DT" && it.status == AccountStatus.ACTIVE }.sortedBy { it.name.lowercase() }

    // Equipos activos agrupados por categoría, en el orden Sub-8 … Otra (RN-004)
    val activeTeamsByCategory: List<Pair<String, List<Team>>>
        get() = teams.filter { it.active }
            .sortedWith(compareBy({ categoryOrder(it.category) }, { it.name.lowercase() }))
            .groupBy { it.category }
            .toList()

    val inactiveTeams: List<Team>
        get() = teams.filter { !it.active }.sortedBy { it.name.lowercase() }

    fun teamName(teamId: String): String = teams.firstOrNull { it.id == teamId }?.name.orEmpty()

    fun memberName(uid: String): String = members.firstOrNull { it.uid == uid }?.name.orEmpty()

    val filteredMembers: List<UserProfile>
        get() = members.filter { roleFilter == null || it.role == roleFilter }
            .sortedWith(compareBy({ ROLE_ORDER.indexOf(it.role) }, { it.name.lowercase() }))

    // Los más nuevos primero (un código recién creado aún no tiene fecha del servidor)
    val sortedInvitations: List<DtInvitation>
        get() = invitations.sortedByDescending { it.createdAt?.time ?: Long.MAX_VALUE }

    companion object {
        val ROLE_ORDER = listOf("ADM", "DT", "JUG", "PAD")
    }
}

private const val INACTIVE_TEAM_COACH_MESSAGE = "Ese equipo está desactivado. Reactívalo para asignarle un DT."

class AcademyViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AcademyUiState())
    val uiState: StateFlow<AcademyUiState> = _uiState.asStateFlow()

    private var listenJobs: List<Job> = emptyList()

    // ---------- Inicio: escuchar la academia del ADM (el academyId sale de su perfil) ----------
    fun start(profile: UserProfile) {
        if (profile.role != "ADM" || profile.academyId.isEmpty()) return
        if (profile.academyId == _uiState.value.academyId && listenJobs.isNotEmpty()) {
            _uiState.update { it.copy(admin = profile) }
            return
        }
        stop()
        _uiState.value = AcademyUiState(admin = profile)
        val id = profile.academyId
        listenJobs = listOf(
            listen("academia", AcademyRepository.academyFlow(id)) { state, academy -> state.copy(academy = academy) },
            listen("equipos", AcademyRepository.teamsFlow(id)) { state, teams -> state.copy(teams = teams, teamsLoaded = true) },
            listen("usuarios", AcademyRepository.membersFlow(id)) { state, members -> state.copy(members = members, membersLoaded = true) },
            listen("códigos", AcademyRepository.dtInvitationsFlow(id)) { state, invitations ->
                state.copy(invitations = invitations, invitationsLoaded = true)
            }
        )
    }

    // Al cerrar sesión se dejan de escuchar los datos de la academia
    fun stop() {
        listenJobs.forEach { it.cancel() }
        listenJobs = emptyList()
        _uiState.value = AcademyUiState()
    }

    // Escucha una fuente; su error se guarda (o se quita) solo con su propia clave
    private fun <T> listen(
        source: String,
        flow: Flow<Result<T>>,
        onData: (AcademyUiState, T) -> AcademyUiState
    ): Job =
        viewModelScope.launch {
            flow.collect { result ->
                _uiState.update { state ->
                    result.fold(
                        onSuccess = { onData(state, it).copy(loadErrors = state.loadErrors - source) },
                        onFailure = {
                            state.copy(loadErrors = state.loadErrors + (source to (it.message ?: "No se pudo cargar la información.")))
                        }
                    )
                }
            }
        }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    // ---------- Editar academia ----------
    fun startEditAcademy() {
        val academy = _uiState.value.academy ?: return
        _uiState.update {
            it.copy(
                editingAcademy = true,
                academyForm = academy.toForm(),
                academyErrors = AcademyFormErrors(),
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    // ---------- Si Android cerró la app (process death) ----------
    // Al volver, el ViewModel está vacío: cada pantalla recarga sus datos con el id de su ruta
    // cuando llegan las listas. Devuelven false si ya no existe (la pantalla vuelve a la lista).

    fun restoreAcademyForm() {
        if (!_uiState.value.editingAcademy) startEditAcademy()   // no hace nada hasta que llegue la academia
    }

    fun restoreTeamForm(teamId: String): Boolean {
        val state = _uiState.value
        if (teamId.isEmpty() || state.teamForm.id == teamId || !state.teamsLoaded) return true
        val team = state.teams.firstOrNull { it.id == teamId } ?: return false
        startEditTeam(team)
        return true
    }

    fun restoreGeneratedCode(code: String): Boolean {
        val state = _uiState.value
        if (state.generatedCode == code || !state.invitationsLoaded) return true
        val invitation = state.invitations.firstOrNull { it.code == code } ?: return false
        _uiState.update { it.copy(generatedCode = invitation.code, generatedTeamName = invitation.teamName) }
        return true
    }

    fun onAcademyFormChange(form: AcademyForm) {
        _uiState.update { it.copy(academyForm = form, academyErrors = AcademyFormErrors(), errorMessage = null) }
    }

    // Comprime el logo elegido a 150 KB o menos (fuera del hilo principal)
    fun onLogoPicked(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(logoLoading = true, errorMessage = null) }
            try {
                val base64 = compressImageToBase64(resolver, uri)
                _uiState.update { it.copy(logoLoading = false, academyForm = it.academyForm.copy(logoBase64 = base64)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(logoLoading = false, errorMessage = "No pudimos usar esa imagen. Prueba con otra.") }
            }
        }
    }

    fun saveAcademy(onSaved: () -> Unit) {
        val state = _uiState.value
        val academy = state.academy ?: return
        if (state.isLoading) return   // evita el doble toque
        val errors = validateAcademyForm(state.academyForm)
        _uiState.update { it.copy(academyErrors = errors) }
        if (errors.hasErrors) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = AcademyRepository.updateAcademy(state.academyForm.toAcademy(academy.id), previous = academy)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "Datos de la academia guardados." else null
                )
            }
            if (result.isSuccess) onSaved()
        }
    }

    // ---------- Equipos ----------
    fun startNewTeam() {
        _uiState.update { it.copy(teamForm = TeamForm(), teamErrors = TeamFormErrors(), errorMessage = null, infoMessage = null) }
    }

    // El DT no se copia al formulario: se muestra el DT actual (teamFormCoachUid) y solo se cambia
    // si el ADM toca "Cambiar". Así, si el equipo tenía 2 DT, guardar no saca a uno sin avisar.
    fun startEditTeam(team: Team) {
        _uiState.update {
            it.copy(
                teamForm = TeamForm(
                    id = team.id,
                    name = team.name,
                    category = team.category,
                    maxStarters = team.maxStarters.toString(),
                    maxSubstitutes = team.maxSubstitutes.toString()
                ),
                teamErrors = TeamFormErrors(),
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onTeamFormChange(form: TeamForm) {
        _uiState.update { it.copy(teamForm = form, teamErrors = TeamFormErrors(), errorMessage = null) }
    }

    fun saveTeam(onSaved: () -> Unit) {
        val state = _uiState.value
        val form = state.teamForm
        val starters = form.maxStarters.toIntOrNull()
        val substitutes = form.maxSubstitutes.toIntOrNull()
        val sameName = state.teams.any { it.id != form.id && it.name.trim().equals(form.name.trim(), ignoreCase = true) }
        val errors = TeamFormErrors(
            name = when {
                form.name.trim().length < 2 -> "Escribe el nombre del equipo."
                sameName -> "Ya existe un equipo con ese nombre."
                else -> null
            },
            maxStarters = if (starters == null || starters !in 1..11) "Entre 1 y 11." else null,
            maxSubstitutes = if (substitutes == null || substitutes !in 0..20) "Entre 0 y 20." else null
        )
        _uiState.update { it.copy(teamErrors = errors) }
        if (errors.hasErrors || state.isLoading) return
        // A un equipo desactivado no se le asigna DT (la regla también lo impide)
        if (form.id.isNotEmpty() && !state.isTeamActive(form.id) && form.coachChanged && form.coachUid.isNotEmpty()) {
            _uiState.update { it.copy(errorMessage = INACTIVE_TEAM_COACH_MESSAGE, infoMessage = null) }
            return
        }

        val team = Team(
            id = form.id,
            name = form.name.trim(),
            category = form.category,
            maxStarters = starters ?: 11,
            maxSubstitutes = substitutes ?: 12
        )
        val previousName = state.teams.firstOrNull { it.id == form.id }?.name.orEmpty()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Los DT actuales los consulta el repositorio en el servidor
            val result = AcademyRepository.saveTeam(state.academyId, team, previousName, form.coachChanged, form.coachUid)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "Equipo ${team.name} guardado." else null
                )
            }
            if (result.isSuccess) onSaved()
        }
    }

    // Cambiar el DT desde la lista de equipos (coachUid "" = Sin DT)
    fun changeCoach(team: Team, coachUid: String) {
        val state = _uiState.value
        if (state.isLoading) return   // evita el doble toque
        if (!state.isTeamActive(team.id)) {
            _uiState.update { it.copy(errorMessage = INACTIVE_TEAM_COACH_MESSAGE, infoMessage = null) }
            return
        }
        val currentCoaches = state.coachesOf(team.id).map { it.uid }
        // Eligió al mismo DT (o "Sin DT" y no tenía): no hay nada que cambiar
        if (currentCoaches == listOfNotNull(coachUid.ifEmpty { null })) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.changeCoach(state.academyId, team.id, coachUid)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "DT de ${team.name} actualizado." else null
                )
            }
        }
    }

    // Al desactivar: se anulan sus códigos pendientes (DT, JUG y PAD) y su DT queda sin equipo (misma operación)
    fun setTeamActive(team: Team, active: Boolean, onDone: () -> Unit) {
        val state = _uiState.value
        if (state.isLoading) return   // evita el doble toque
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.setTeamActive(state.academyId, team.id, active)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) {
                        if (active) "Equipo ${team.name} reactivado." else "Equipo ${team.name} desactivado. Sus códigos pendientes se anularon y su historial se conserva."
                    } else null
                )
            }
            if (result.isSuccess) onDone()
        }
    }

    // ---------- Usuarios ----------
    fun setRoleFilter(role: String?) {
        _uiState.update { it.copy(roleFilter = role) }
    }

    fun setUserActive(user: UserProfile, active: Boolean) {
        if (user.uid == _uiState.value.admin?.uid) return   // el ADM no se desactiva a sí mismo
        if (_uiState.value.isLoading) return                 // evita el doble toque
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.setUserActive(user, active)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) {
                        if (active) "Cuenta de ${user.name} reactivada." else "Cuenta de ${user.name} desactivada."
                    } else null
                )
            }
        }
    }

    // ---------- Invitaciones de DT ----------
    fun createInvitation(team: Team, onCreated: () -> Unit) {
        val state = _uiState.value
        val academy = state.academy ?: return
        val admin = state.admin ?: return
        if (state.invitationLoading) return   // evita el doble toque
        // No se invita a equipos desactivados (la regla también lo impide)
        if (!state.isTeamActive(team.id)) {
            _uiState.update { it.copy(errorMessage = "Ese equipo está desactivado. Reactívalo para invitar a su DT.", infoMessage = null) }
            return
        }
        // Un solo DT por equipo: si ya tiene DT o un código de DT pendiente, no se genera otro
        if (!state.canInviteCoach(team.id)) {
            _uiState.update { it.copy(errorMessage = AcademyRepository.COACH_TAKEN_MESSAGE, infoMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(invitationLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.createDtInvitation(academy, team, admin)
            _uiState.update {
                it.copy(
                    invitationLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    generatedCode = result.getOrNull() ?: it.generatedCode,
                    generatedTeamName = if (result.isSuccess) team.name else it.generatedTeamName
                )
            }
            if (result.isSuccess) onCreated()
        }
    }

    // "Generar otro" para un código vencido o anulado: nuevo código para el mismo equipo
    fun regenerateInvitation(invitation: DtInvitation, onCreated: () -> Unit) {
        val team = _uiState.value.teams.firstOrNull { it.id == invitation.teamId && it.active }
        if (team == null) {
            _uiState.update { it.copy(errorMessage = "Ese equipo ya no está activo. Crea una invitación para otro equipo.", infoMessage = null) }
            return
        }
        createInvitation(team, onCreated)
    }

    fun revokeInvitation(code: String) {
        if (_uiState.value.isLoading) return   // evita el doble toque
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.revokeInvitation(code)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "Código $code anulado." else null
                )
            }
        }
    }
}
