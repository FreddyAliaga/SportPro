package pe.edu.esan.sportpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.repository.AcademyRepository
import pe.edu.esan.sportpro.data.repository.AcademyRepositoryImpl
import pe.edu.esan.sportpro.domain.model.Academy
import pe.edu.esan.sportpro.domain.model.AcademyUser
import pe.edu.esan.sportpro.domain.model.Category
import pe.edu.esan.sportpro.domain.model.InvitationCode
import pe.edu.esan.sportpro.domain.model.Team
import pe.edu.esan.sportpro.domain.model.UserRole

class AcademyViewModel(
    private val repository: AcademyRepository = AcademyRepositoryImpl()
) : ViewModel() {

    val academy: StateFlow<Academy> = repository.academy
    val academies: StateFlow<List<Academy>> = repository.academies
    val teams: StateFlow<List<Team>> = repository.teams
    val invitations: StateFlow<List<InvitationCode>> = repository.invitations
    val users: StateFlow<List<AcademyUser>> = repository.users

    // Teams grouped by Category sorted by sortOrder
    val teamsByCategory: StateFlow<Map<Category, List<Team>>> = teams.combine(repository.teams) { teamList, _ ->
        teamList.groupBy { it.category }
            .toSortedMap(compareBy { it.sortOrder })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private val _selectedRoleFilter = MutableStateFlow<UserRole?>(null) // null = Todos
    val selectedRoleFilter: StateFlow<UserRole?> = _selectedRoleFilter.asStateFlow()

    val filteredUsers: StateFlow<List<AcademyUser>> = combine(users, selectedRoleFilter) { userList, roleFilter ->
        if (roleFilter == null) userList else userList.filter { it.role == roleFilter }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _generatedCode = MutableStateFlow<InvitationCode?>(null)
    val generatedCode: StateFlow<InvitationCode?> = _generatedCode.asStateFlow()

    private val _selectedTeamForEdit = MutableStateFlow<Team?>(null)
    val selectedTeamForEdit: StateFlow<Team?> = _selectedTeamForEdit.asStateFlow()

    private val _selectedUserForAction = MutableStateFlow<AcademyUser?>(null)
    val selectedUserForAction: StateFlow<AcademyUser?> = _selectedUserForAction.asStateFlow()

    fun updateAcademy(
        name: String,
        city: String,
        institutionalEmail: String,
        callupNoticeHours: Int,
        callupConfirmHours: Int,
        logoBase64: String? = null
    ) {
        viewModelScope.launch {
            val current = academy.value.copy(
                name = name,
                city = city,
                institutionalEmail = institutionalEmail,
                callupNoticeHours = callupNoticeHours,
                callupConfirmHours = callupConfirmHours,
                logoBase64 = logoBase64
            )
            repository.updateAcademy(current)
            _successMessage.value = "Datos de la academia actualizados."
        }
    }

    fun createNewAcademy(
        name: String,
        city: String,
        institutionalEmail: String,
        callupNoticeHours: Int,
        callupConfirmHours: Int
    ) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _errorMessage.value = "El nombre de la academia es obligatorio."
                return@launch
            }
            repository.createNewAcademy(
                name = name.trim(),
                city = city.trim(),
                institutionalEmail = institutionalEmail.trim(),
                callupNoticeHours = callupNoticeHours,
                callupConfirmHours = callupConfirmHours
            )
            _successMessage.value = "Nueva academia registrada exitosamente."
        }
    }

    fun switchAcademy(academyId: String) {
        viewModelScope.launch {
            val result = repository.switchAcademy(academyId)
            result.onSuccess {
                _successMessage.value = "Cambiado a ${academy.value.name}"
            }
        }
    }

    fun selectTeamForEdit(team: Team?) {
        _selectedTeamForEdit.value = team
    }

    fun saveTeam(
        teamId: String,
        name: String,
        category: Category,
        coachId: String?,
        coachName: String?,
        maxStarters: Int,
        maxSubstitutes: Int,
        onSuccess: (Team) -> Unit
    ) {
        if (name.isBlank()) {
            _errorMessage.value = "El nombre del equipo es obligatorio."
            return
        }

        val team = Team(
            id = teamId,
            academyId = academy.value.id,
            name = name.trim(),
            category = category,
            coachId = coachId,
            coachName = coachName,
            maxStarters = maxStarters,
            maxSubstitutes = maxSubstitutes
        )

        viewModelScope.launch {
            val result = repository.saveTeam(team)
            result.onSuccess { saved ->
                _successMessage.value = "Equipo guardado correctamente."
                _errorMessage.value = null
                onSuccess(saved)
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Error al guardar el equipo."
            }
        }
    }

    fun generateInvitation(teamId: String?, teamName: String?, onSuccess: (InvitationCode) -> Unit) {
        viewModelScope.launch {
            val result = repository.generateInvitation(teamId, teamName)
            result.onSuccess { code ->
                _generatedCode.value = code
                _successMessage.value = "Código de invitación generado: ${code.code}"
                onSuccess(code)
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Error al generar invitación."
            }
        }
    }

    fun revokeInvitation(code: String) {
        viewModelScope.launch {
            repository.revokeInvitation(code)
            _successMessage.value = "Código $code anulado."
        }
    }

    fun deleteInvitation(code: String) {
        viewModelScope.launch {
            repository.deleteInvitation(code)
            _successMessage.value = "Invitación $code eliminada."
        }
    }

    fun regenerateInvitation(oldCode: String, onSuccess: (InvitationCode) -> Unit) {
        viewModelScope.launch {
            val result = repository.regenerateInvitation(oldCode)
            result.onSuccess { code ->
                _generatedCode.value = code
                _successMessage.value = "Nuevo código generado: ${code.code}"
                onSuccess(code)
            }
        }
    }

    fun reassignCoach(teamId: String, coachId: String?, coachName: String?) {
        viewModelScope.launch {
            repository.reassignCoach(teamId, coachId, coachName)
            _successMessage.value = "DT asignado correctamente."
        }
    }

    fun selectUserForAction(user: AcademyUser?) {
        _selectedUserForAction.value = user
    }

    fun toggleUserActive(userId: String, currentActive: Boolean) {
        viewModelScope.launch {
            repository.setUserActive(userId, !currentActive)
            _successMessage.value = if (currentActive) "Cuenta desactivada." else "Cuenta activada."
        }
    }

    fun setRoleFilter(role: UserRole?) {
        _selectedRoleFilter.value = role
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    class Factory(private val repository: AcademyRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AcademyViewModel(repository) as T
        }
    }
}
