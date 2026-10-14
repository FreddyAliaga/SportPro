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
import pe.edu.esan.sportpro.data.repository.MatchRepositoryImpl
import pe.edu.esan.sportpro.domain.model.EventType
import pe.edu.esan.sportpro.domain.model.MatchStatus
import pe.edu.esan.sportpro.domain.model.TeamSide
import pe.edu.esan.sportpro.ui.viewmodel.MatchViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: MatchRepositoryImpl
    private lateinit var viewModel: MatchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = MatchRepositoryImpl(firestore = null)
        viewModel = MatchViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialMatchAndEventsLoaded() {
        val activeMatch = viewModel.activeMatch.value
        assertNotNull(activeMatch)
        assertEquals("Sub-12 A", activeMatch?.teamName)
        assertEquals("Águilas FC", activeMatch?.rivalName)
        assertEquals(MatchStatus.EN_CURSO, activeMatch?.status)
        assertEquals(2, activeMatch?.localScore)
        assertEquals(1, activeMatch?.rivalScore)
    }

    @Test
    fun testScoreRecalculatedWhenGoalAdded() {
        val initialMatch = viewModel.activeMatch.value
        val initialLocalScore = initialMatch?.localScore ?: 0

        val goalType = EventType("type_gol", "Gol", affectsScore = true)
        viewModel.registerQuickEvent(
            matchId = "match_1",
            eventType = goalType,
            teamSide = TeamSide.LOCAL,
            playerName = "Mateo Rojas",
            dorsal = "10"
        )

        val updatedMatch = viewModel.activeMatch.value
        assertEquals(initialLocalScore + 1, updatedMatch?.localScore)
    }

    @Test
    fun testScoreRecalculatedWhenGoalAnnulled() {
        val initialMatch = viewModel.activeMatch.value
        val initialLocalScore = initialMatch?.localScore ?: 2

        // Find a local goal event (evt_2)
        val localGoalEvent = viewModel.activeMatchChronology.value.find { it.typeName == "Gol" && it.teamSide == TeamSide.LOCAL }
        assertNotNull(localGoalEvent)

        viewModel.annulEvent(localGoalEvent!!.id)

        val updatedMatch = viewModel.activeMatch.value
        assertEquals(initialLocalScore - 1, updatedMatch?.localScore)
    }

    @Test
    fun testToggleConnectionStatus() {
        assertTrue(viewModel.isConnected.value)
        viewModel.toggleConnectionStatus()
        assertEquals(false, viewModel.isConnected.value)
        viewModel.toggleConnectionStatus()
        assertEquals(true, viewModel.isConnected.value)
    }
}
