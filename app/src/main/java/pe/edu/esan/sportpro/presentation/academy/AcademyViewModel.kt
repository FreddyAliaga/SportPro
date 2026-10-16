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
import pe.edu.esan.sportpro.data.model.DtInvitation
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
    val coachUid: String = ""          // "" = Sin DT
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
    val loadError: String? = null,
    // Editar academia
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
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val academyId: String get() = admin?.academyId.orEmpty()

    // DT del equipo = usuarios DT activos con ese teamId (normalmente 0 o 1)
    fun coachesOf(teamId: String): List<UserProfile> =
        if (teamId.isEmpty()) emptyList()
        else members.filter { it.role == "DT" && it.status == AccountStatus.ACTIVE && it.teamId == teamId }

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
            listen(AcademyRepository.academyFlow(id)) { state, academy -> state.copy(academy = academy) },
            listen(AcademyRepository.teamsFlow(id)) { state, teams -> state.copy(teams = teams) },
            listen(AcademyRepository.membersFlow(id)) { state, members -> state.copy(members = members) },
            listen(AcademyRepository.dtInvitationsFlow(id)) { state, invitations -> state.copy(invitations = invitations) }
        )
    }

    // Al cerrar sesión se dejan de escuchar los datos de la academia
    fun stop() {
        listenJobs.forEach { it.cancel() }
        listenJobs = emptyList()
        _uiState.value = AcademyUiState()
    }

    private fun <T> listen(flow: Flow<Result<T>>, onData: (AcademyUiState, T) -> AcademyUiState): Job =
        viewModelScope.launch {
            flow.collect { result ->
                _uiState.update { state ->
                    result.fold(
                        onSuccess = { onData(state, it).copy(loadError = null) },
                        onFailure = { state.copy(loadError = it.message) }
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
            it.copy(academyForm = academy.toForm(), academyErrors = AcademyFormErrors(), errorMessage = null, infoMessage = null)
        }
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
        val errors = validateAcademyForm(state.academyForm)
        _uiState.update { it.copy(academyErrors = errors) }
        if (errors.hasErrors) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = AcademyRepository.updateAcademy(state.academyForm.toAcademy(academy.id), previousName = academy.name)
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

    fun startEditTeam(team: Team) {
        val coachUid = _uiState.value.coachesOf(team.id).firstOrNull()?.uid.orEmpty()
        _uiState.update {
            it.copy(
                teamForm = TeamForm(
                    id = team.id,
                    name = team.name,
                    category = team.category,
                    maxStarters = team.maxStarters.toString(),
                    maxSubstitutes = team.maxSubstitutes.toString(),
                    coachUid = coachUid
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

        val team = Team(
            id = form.id,
            name = form.name.trim(),
            category = form.category,
            maxStarters = starters ?: 11,
            maxSubstitutes = substitutes ?: 12
        )
        val currentCoaches = state.coachesOf(form.id).map { it.uid }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = AcademyRepository.saveTeam(state.academyId, team, form.coachUid, currentCoaches)
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
        val currentCoaches = _uiState.value.coachesOf(team.id).map { it.uid }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.changeCoach(team.id, coachUid, currentCoaches)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "DT de ${team.name} actualizado." else null
                )
            }
        }
    }

    fun setTeamActive(team: Team, active: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.setTeamActive(_uiState.value.academyId, team.id, active)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) {
                        if (active) "Equipo ${team.name} reactivado." else "Equipo ${team.name} desactivado. Su historial se conserva."
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.setUserActive(user.uid, active)
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
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.createDtInvitation(academy, team, admin)
            _uiState.update {
                it.copy(
                    isLoading = false,
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
