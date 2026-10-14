package pe.edu.esan.sportpro.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.edu.esan.sportpro.domain.model.Academy
import pe.edu.esan.sportpro.domain.model.AcademyUser
import pe.edu.esan.sportpro.domain.model.Category
import pe.edu.esan.sportpro.domain.model.InvitationCode
import pe.edu.esan.sportpro.domain.model.InvitationStatus
import pe.edu.esan.sportpro.domain.model.Team
import pe.edu.esan.sportpro.domain.model.UserRole
import java.util.UUID

interface AcademyRepository {
    val academy: StateFlow<Academy>
    val academies: StateFlow<List<Academy>>
    val teams: StateFlow<List<Team>>
    val invitations: StateFlow<List<InvitationCode>>
    val users: StateFlow<List<AcademyUser>>

    suspend fun updateAcademy(academy: Academy): Result<Unit>
    suspend fun createNewAcademy(
        name: String,
        city: String,
        institutionalEmail: String,
        callupNoticeHours: Int,
        callupConfirmHours: Int
    ): Result<Academy>
    suspend fun switchAcademy(academyId: String): Result<Unit>
    suspend fun saveTeam(team: Team): Result<Team>
    suspend fun reassignCoach(teamId: String, coachId: String?, coachName: String?): Result<Unit>
    suspend fun generateInvitation(teamId: String?, teamName: String?): Result<InvitationCode>
    suspend fun revokeInvitation(code: String): Result<Unit>
    suspend fun deleteInvitation(code: String): Result<Unit>
    suspend fun validateAndUseInvitationCode(code: String, userName: String): Result<InvitationCode>
    suspend fun regenerateInvitation(oldCode: String): Result<InvitationCode>
    suspend fun setUserActive(userId: String, active: Boolean): Result<Unit>
}

class AcademyRepositoryImpl(
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
) : AcademyRepository {

    private val initialAcademy = Academy(
        id = "halcones_1",
        name = "Academia Los Halcones",
        logoBase64 = null,
        city = "Lima",
        institutionalEmail = "contacto@loshalcones.pe",
        verified = false,
        callupNoticeHours = 24,
        callupConfirmHours = 2
    )

    private val _academies = MutableStateFlow(listOf(initialAcademy))
    override val academies: StateFlow<List<Academy>> = _academies.asStateFlow()

    private val _academy = MutableStateFlow(initialAcademy)
    override val academy: StateFlow<Academy> = _academy.asStateFlow()

    private val _allTeams = MutableStateFlow(
        listOf(
            Team(
                id = "team_sub10_a",
                academyId = "halcones_1",
                name = "Sub-10 A",
                category = Category.SUB_10,
                coachId = "u_luis",
                coachName = "Luis Paredes",
                maxStarters = 11,
                maxSubstitutes = 12,
                playerCount = 14
            ),
            Team(
                id = "team_sub12_a",
                academyId = "halcones_1",
                name = "Sub-12 A",
                category = Category.SUB_12,
                coachId = "u_carlos",
                coachName = "Carlos Medina",
                maxStarters = 11,
                maxSubstitutes = 12,
                playerCount = 18
            ),
            Team(
                id = "team_sub12_b",
                academyId = "halcones_1",
                name = "Sub-12 B",
                category = Category.SUB_12,
                coachId = null,
                coachName = null,
                maxStarters = 11,
                maxSubstitutes = 12,
                playerCount = 9
            ),
            Team(
                id = "team_sub15_a",
                academyId = "halcones_1",
                name = "Sub-15 A",
                category = Category.SUB_15,
                coachId = "u_jorge",
                coachName = "Jorge Ruiz",
                maxStarters = 11,
                maxSubstitutes = 12,
                playerCount = 16
            )
        )
    )

    private val _teams = MutableStateFlow<List<Team>>(emptyList())
    override val teams: StateFlow<List<Team>> = _teams.asStateFlow()

    private val _allInvitations = MutableStateFlow(
        listOf(
            InvitationCode(
                code = "HALC-D4T9",
                academyId = "halcones_1",
                role = UserRole.DT,
                teamId = "team_sub15_b",
                teamName = "Sub-15 B",
                status = InvitationStatus.PENDING,
                expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
            ),
            InvitationCode(
                code = "HALC-Q7M2",
                academyId = "halcones_1",
                role = UserRole.DT,
                teamId = "team_sub12_a",
                teamName = "Sub-12 A",
                status = InvitationStatus.USED,
                usedByName = "Carlos Medina"
            ),
            InvitationCode(
                code = "HALC-Z1K8",
                academyId = "halcones_1",
                role = UserRole.DT,
                teamId = "team_sub12_b",
                teamName = "Sub-12 B",
                status = InvitationStatus.EXPIRED
            ),
            InvitationCode(
                code = "HALC-R5T3",
                academyId = "halcones_1",
                role = UserRole.DT,
                teamId = "team_sub10_a",
                teamName = "Sub-10 A",
                status = InvitationStatus.REVOKED,
                revokedBy = "Rosa Q."
            )
        )
    )

    private val _invitations = MutableStateFlow<List<InvitationCode>>(emptyList())
    override val invitations: StateFlow<List<InvitationCode>> = _invitations.asStateFlow()

    private val _allUsers = MutableStateFlow(
        listOf(
            AcademyUser(
                userId = "u_carlos",
                name = "Carlos Medina",
                email = "carlos.medina@loshalcones.pe",
                role = UserRole.DT,
                academyId = "halcones_1",
                teamId = "team_sub12_a",
                teamName = "Sub-12 A",
                assignedTeamNames = listOf("Sub-12 A"),
                active = true
            ),
            AcademyUser(
                userId = "u_luis",
                name = "Luis Paredes",
                email = "luis.paredes@loshalcones.pe",
                role = UserRole.DT,
                academyId = "halcones_1",
                teamId = "team_sub10_a",
                teamName = "Sub-10 A",
                assignedTeamNames = listOf("Sub-10 A"),
                active = true
            ),
            AcademyUser(
                userId = "u_jorge",
                name = "Jorge Ruiz",
                email = "jorge.ruiz@loshalcones.pe",
                role = UserRole.DT,
                academyId = "halcones_1",
                teamId = "team_sub15_a",
                teamName = "Sub-15 A",
                assignedTeamNames = listOf("Sub-15 A", "Sub-15 B"),
                active = true
            ),
            AcademyUser(
                userId = "u_pedro",
                name = "Pedro Salas",
                email = "pedro.salas@loshalcones.pe",
                role = UserRole.DT,
                academyId = "halcones_1",
                teamId = null,
                teamName = "Sin equipo",
                assignedTeamNames = emptyList(),
                active = false
            ),
            AcademyUser(
                userId = "u_mario",
                name = "Mario Torres",
                email = "mario.torres@gmail.com",
                role = UserRole.DT,
                academyId = "halcones_1",
                teamId = null,
                teamName = "Sin equipo",
                assignedTeamNames = emptyList(),
                active = true
            ),
            // Players
            AcademyUser(
                userId = "u_p1",
                name = "Mateo Rojas",
                email = "mateo@loshalcones.pe",
                role = UserRole.JUG,
                academyId = "halcones_1",
                teamId = "team_sub12_a",
                teamName = "Sub-12 A",
                active = true
            ),
            AcademyUser(
                userId = "u_p2",
                name = "Gabriel Paredes",
                email = "gabriel@loshalcones.pe",
                role = UserRole.JUG,
                academyId = "halcones_1",
                teamId = "team_sub10_a",
                teamName = "Sub-10 A",
                active = true
            ),
            AcademyUser(
                userId = "u_p3",
                name = "Lucas Medina",
                email = "lucas@loshalcones.pe",
                role = UserRole.JUG,
                academyId = "halcones_1",
                teamId = "team_sub12_b",
                teamName = "Sub-12 B",
                active = true
            ),
            // Parents / Apoderados
            AcademyUser(
                userId = "u_g1",
                name = "Ana Rojas Paredes",
                email = "ana.rojas@gmail.com",
                role = UserRole.PAD,
                academyId = "halcones_1",
                teamId = "team_sub12_a",
                teamName = "Sub-12 A",
                active = true
            ),
            AcademyUser(
                userId = "u_g2",
                name = "Carlos Paredes",
                email = "cparedes@gmail.com",
                role = UserRole.PAD,
                academyId = "halcones_1",
                teamId = "team_sub10_a",
                teamName = "Sub-10 A",
                active = true
            )
        )
    )

    private val _users = MutableStateFlow<List<AcademyUser>>(emptyList())
    override val users: StateFlow<List<AcademyUser>> = _users.asStateFlow()

    init {
        updateFilteredStateForActiveAcademy("halcones_1")
    }

    private fun updateFilteredStateForActiveAcademy(academyId: String) {
        _teams.value = _allTeams.value.filter { it.academyId == academyId }
        _invitations.value = _allInvitations.value.filter { it.academyId == academyId }
        _users.value = _allUsers.value.filter { it.academyId == academyId }
    }

    override suspend fun updateAcademy(academy: Academy): Result<Unit> {
        _academy.value = academy
        _academies.value = _academies.value.map { if (it.id == academy.id) academy else it }
        try {
            firestore?.collection("academies")?.document(academy.id)?.set(academy)
        } catch (e: Exception) {
            // Keep local state fallback
        }
        return Result.success(Unit)
    }

    override suspend fun createNewAcademy(
        name: String,
        city: String,
        institutionalEmail: String,
        callupNoticeHours: Int,
        callupConfirmHours: Int
    ): Result<Academy> {
        val newId = "acad_" + UUID.randomUUID().toString().take(8)
        val newAcademy = Academy(
            id = newId,
            name = name,
            city = city,
            institutionalEmail = institutionalEmail,
            verified = false,
            callupNoticeHours = callupNoticeHours,
            callupConfirmHours = callupConfirmHours
        )

        _academies.value = _academies.value + newAcademy
        _academy.value = newAcademy

        val newAdminUser = AcademyUser(
            userId = "admin_" + UUID.randomUUID().toString().take(6),
            name = "Administrador",
            email = institutionalEmail,
            role = UserRole.ADM,
            academyId = newId,
            active = true
        )
        _allUsers.value = _allUsers.value + newAdminUser

        updateFilteredStateForActiveAcademy(newId)

        try {
            firestore?.collection("academies")?.document(newId)?.set(newAcademy)
        } catch (e: Exception) {
            // Local fallback
        }

        return Result.success(newAcademy)
    }

    override suspend fun switchAcademy(academyId: String): Result<Unit> {
        val target = _academies.value.find { it.id == academyId }
        if (target != null) {
            _academy.value = target
            updateFilteredStateForActiveAcademy(academyId)
            return Result.success(Unit)
        }
        return Result.failure(IllegalArgumentException("Academia no encontrada"))
    }

    override suspend fun saveTeam(team: Team): Result<Team> {
        val currentAcademyId = _academy.value.id
        val teamWithAcademy = if (team.academyId.isBlank()) team.copy(academyId = currentAcademyId) else team

        // Validation: Unique team name within academy
        val currentTeams = _teams.value
        val existingWithSameName = currentTeams.find { 
            it.id != teamWithAcademy.id && it.name.equals(teamWithAcademy.name.trim(), ignoreCase = true) 
        }
        if (existingWithSameName != null) {
            return Result.failure(IllegalArgumentException("Ya existe un equipo con ese nombre."))
        }

        // Validation: Starters limit
        if (teamWithAcademy.maxStarters < 1 || teamWithAcademy.maxStarters > 11) {
            return Result.failure(IllegalArgumentException("Debe haber al menos 1 titular (máximo 11)."))
        }

        val updatedTeam = if (teamWithAcademy.id.isBlank()) {
            teamWithAcademy.copy(id = "team_" + UUID.randomUUID().toString().take(8))
        } else {
            teamWithAcademy
        }

        _allTeams.value = _allTeams.value.filterNot { it.id == updatedTeam.id } + updatedTeam
        updateFilteredStateForActiveAcademy(currentAcademyId)

        // Update assigned teams in users list if coach assigned
        if (updatedTeam.coachId != null) {
            _allUsers.value = _allUsers.value.map { user ->
                if (user.userId == updatedTeam.coachId) {
                    val assigned = (user.assignedTeamNames + updatedTeam.name).distinct()
                    user.copy(
                        teamId = updatedTeam.id,
                        teamName = updatedTeam.name,
                        assignedTeamNames = assigned
                    )
                } else user
            }
            updateFilteredStateForActiveAcademy(currentAcademyId)
        }

        try {
            firestore?.collection("academies")
                ?.document(updatedTeam.academyId)
                ?.collection("teams")
                ?.document(updatedTeam.id)
                ?.set(updatedTeam)
        } catch (e: Exception) {
            // Local state updated
        }

        return Result.success(updatedTeam)
    }

    override suspend fun reassignCoach(teamId: String, coachId: String?, coachName: String?): Result<Unit> {
        val currentAcademyId = _academy.value.id
        _allTeams.value = _allTeams.value.map { team ->
            if (team.id == teamId) {
                team.copy(coachId = coachId, coachName = coachName)
            } else team
        }

        if (coachId != null) {
            _allUsers.value = _allUsers.value.map { user ->
                if (user.userId == coachId) {
                    val currentTeamName = _allTeams.value.find { it.id == teamId }?.name ?: ""
                    val updatedAssignments = (user.assignedTeamNames + currentTeamName).distinct()
                    user.copy(
                        teamId = teamId,
                        teamName = currentTeamName,
                        assignedTeamNames = updatedAssignments
                    )
                } else user
            }
        }

        updateFilteredStateForActiveAcademy(currentAcademyId)
        return Result.success(Unit)
    }

    override suspend fun generateInvitation(teamId: String?, teamName: String?): Result<InvitationCode> {
        val currentAcademyId = _academy.value.id
        val randomSuffix = (1000..9999).random().toString(36).uppercase().take(4)
        val codeStr = "HALC-${randomSuffix.padStart(4, 'X')}"

        val newInvitation = InvitationCode(
            code = codeStr,
            academyId = currentAcademyId,
            role = UserRole.DT,
            teamId = teamId,
            teamName = teamName ?: "Sin equipo",
            status = InvitationStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
        )

        _allInvitations.value = listOf(newInvitation) + _allInvitations.value
        updateFilteredStateForActiveAcademy(currentAcademyId)

        try {
            firestore?.collection("invitations")?.document(codeStr)?.set(newInvitation)
        } catch (e: Exception) {
            // Local state updated
        }

        return Result.success(newInvitation)
    }

    override suspend fun revokeInvitation(code: String): Result<Unit> {
        val currentAcademyId = _academy.value.id
        _allInvitations.value = _allInvitations.value.map { inv ->
            if (inv.code == code) {
                inv.copy(status = InvitationStatus.REVOKED, revokedBy = "Administrador")
            } else inv
        }
        updateFilteredStateForActiveAcademy(currentAcademyId)
        return Result.success(Unit)
    }

    override suspend fun deleteInvitation(code: String): Result<Unit> {
        val currentAcademyId = _academy.value.id
        _allInvitations.value = _allInvitations.value.filterNot { it.code == code }
        updateFilteredStateForActiveAcademy(currentAcademyId)
        try {
            firestore?.collection("invitations")?.document(code)?.delete()
        } catch (e: Exception) {
            // Local state updated
        }
        return Result.success(Unit)
    }

    override suspend fun validateAndUseInvitationCode(code: String, userName: String): Result<InvitationCode> {
        val target = _allInvitations.value.find { it.code.equals(code.trim(), ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("Código no válido o vencido"))

        if (target.status != InvitationStatus.PENDING) {
            return Result.failure(IllegalArgumentException("Código no válido o vencido"))
        }

        if (System.currentTimeMillis() > target.expiresAt) {
            return Result.failure(IllegalArgumentException("Código no válido o vencido"))
        }

        val usedInvitation = target.copy(
            status = InvitationStatus.USED,
            usedBy = userName,
            usedByName = userName,
            usedAtDate = "Hoy"
        )

        val currentAcademyId = _academy.value.id
        _allInvitations.value = _allInvitations.value.map { if (it.code == target.code) usedInvitation else it }

        val newUser = AcademyUser(
            userId = "u_" + UUID.randomUUID().toString().take(6),
            name = userName,
            email = "$userName@loshalcones.pe",
            role = target.role,
            academyId = target.academyId,
            teamId = target.teamId,
            teamName = target.teamName,
            assignedTeamNames = if (target.teamName != null) listOf(target.teamName) else emptyList(),
            active = true
        )
        _allUsers.value = _allUsers.value + newUser
        updateFilteredStateForActiveAcademy(currentAcademyId)

        try {
            firestore?.collection("invitations")?.document(target.code)?.set(usedInvitation)
        } catch (e: Exception) {
            // Local fallback
        }

        return Result.success(usedInvitation)
    }

    override suspend fun regenerateInvitation(oldCode: String): Result<InvitationCode> {
        val target = _allInvitations.value.find { it.code == oldCode }
        return generateInvitation(target?.teamId, target?.teamName)
    }

    override suspend fun setUserActive(userId: String, active: Boolean): Result<Unit> {
        val currentAcademyId = _academy.value.id
        _allUsers.value = _allUsers.value.map { user ->
            if (user.userId == userId) {
                user.copy(active = active)
            } else user
        }
        updateFilteredStateForActiveAcademy(currentAcademyId)
        return Result.success(Unit)
    }
}
