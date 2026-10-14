package pe.edu.esan.sportpro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.edu.esan.sportpro.data.repository.AcademyRepositoryImpl
import pe.edu.esan.sportpro.domain.model.Category
import pe.edu.esan.sportpro.domain.model.UserRole
import pe.edu.esan.sportpro.ui.viewmodel.AcademyViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class AcademyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: AcademyRepositoryImpl
    private lateinit var viewModel: AcademyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = AcademyRepositoryImpl(firestore = null)
        viewModel = AcademyViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialAcademyAndTeamsLoaded() {
        val academy = viewModel.academy.value
        assertEquals("Academia Los Halcones", academy.name)
        assertEquals("Lima", academy.city)

        val teams = viewModel.teams.value
        assertTrue(teams.isNotEmpty())
        assertTrue(teams.any { it.name == "Sub-10 A" })
        assertTrue(teams.any { it.name == "Sub-12 B" && it.coachId == null })
    }

    @Test
    fun testSaveDuplicateTeamNameFails() {
        viewModel.saveTeam(
            teamId = "",
            name = "Sub-10 A", // Already exists in initial mock data
            category = Category.SUB_10,
            coachId = null,
            coachName = null,
            maxStarters = 11,
            maxSubstitutes = 12,
            onSuccess = {}
        )

        assertEquals("Ya existe un equipo con ese nombre.", viewModel.errorMessage.value)
    }

    @Test
    fun testGenerateInvitationCodeFormat() {
        var generatedCodeStr: String? = null
        viewModel.generateInvitation(
            teamId = "team_sub15_b",
            teamName = "Sub-15 B",
            onSuccess = { code -> generatedCodeStr = code.code }
        )

        assertNotNull(generatedCodeStr)
        assertTrue(generatedCodeStr!!.startsWith("HALC-"))
        assertEquals(9, generatedCodeStr?.length) // "HALC-" + 4 chars = 9 chars
    }

    @Test
    fun testFilterUsersByRole() {
        viewModel.setRoleFilter(UserRole.DT)
        val filtered = viewModel.filteredUsers.value
        assertTrue(filtered.all { it.role == UserRole.DT })

        viewModel.setRoleFilter(null) // All
        val allUsers = viewModel.filteredUsers.value
        assertTrue(allUsers.size >= filtered.size)
    }

    @Test
    fun testToggleUserActiveStatus() {
        val initialUser = viewModel.users.value.first { it.userId == "u_mario" }
        assertTrue(initialUser.active)

        viewModel.toggleUserActive(initialUser.userId, currentActive = true)
        val updatedUser = viewModel.users.value.first { it.userId == "u_mario" }
        assertEquals(false, updatedUser.active)
    }

    @Test
    fun testDeleteAndRevokeInvitation() {
        val initialInvitations = viewModel.invitations.value
        val codeToTest = initialInvitations.first().code

        viewModel.revokeInvitation(codeToTest)
        val revokedInv = viewModel.invitations.value.first { it.code == codeToTest }
        assertEquals(pe.edu.esan.sportpro.domain.model.InvitationStatus.REVOKED, revokedInv.status)

        viewModel.deleteInvitation(codeToTest)
        val remainingInvs = viewModel.invitations.value
        assertTrue(remainingInvs.none { it.code == codeToTest })
    }
}
