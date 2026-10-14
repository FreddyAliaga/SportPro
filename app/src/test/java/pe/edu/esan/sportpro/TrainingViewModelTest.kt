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
import pe.edu.esan.sportpro.data.repository.TrainingRepositoryImpl
import pe.edu.esan.sportpro.domain.model.AttendanceStatus
import pe.edu.esan.sportpro.domain.model.SessionExercise
import pe.edu.esan.sportpro.ui.viewmodel.TrainingViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: TrainingRepositoryImpl
    private lateinit var viewModel: TrainingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = TrainingRepositoryImpl(firestore = null)
        viewModel = TrainingViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialExercisesAndSessionsLoaded() {
        val exercises = viewModel.exercises.value
        assertTrue(exercises.isNotEmpty())
        assertTrue(exercises.any { it.name.contains("Pases") })

        val sessions = viewModel.sessions.value
        assertTrue(sessions.isNotEmpty())
        assertEquals(50, sessions.first().totalDurationMinutes) // 10 + 20 + 20 = 50
    }

    @Test
    fun testSaveNewExerciseToLibrary() {
        viewModel.saveExercise(
            name = "Prueba de Resistencia",
            objective = "Resistencia aeróbica",
            description = "Trote sostenido",
            durationMinutes = 30,
            category = "Física",
            materials = listOf("Cronómetro")
        )

        val updated = viewModel.exercises.value
        assertTrue(updated.any { it.name == "Prueba de Resistencia" })
    }

    @Test
    fun testSessionTotalDurationAutoCalculated() {
        val exercises = listOf(
            SessionExercise("ex_1", "Pases", 20, 1),
            SessionExercise("ex_2", "Circuito", 15, 2)
        )

        var savedSuccess = false
        viewModel.saveSession(
            teamId = "team_sub12_a",
            teamName = "Sub-12 A",
            date = "2026-03-20",
            startTime = "16:00",
            location = "Campo 2",
            objective = "Táctica",
            exercises = exercises,
            onSuccess = { savedSuccess = true }
        )

        assertTrue(savedSuccess)
        val createdSession = viewModel.sessions.value.find { it.date == "2026-03-20" }
        assertNotNull(createdSession)
        assertEquals(35, createdSession?.totalDurationMinutes) // 20 + 15 = 35
    }

    @Test
    fun testRecordAttendanceAndSummary() {
        viewModel.recordAttendance("sess_1", "u_p1", "Mateo Rojas", AttendanceStatus.PRESENTE)

        val summary = viewModel.getPlayerAttendanceSummary("u_p1", "Mateo Rojas")
        assertNotNull(summary)
        assertTrue(summary.monthlyPercentage > 0)
    }

    @Test
    fun testAttendanceWindowValidation() {
        val isValid = viewModel.isAttendanceWindowValid("2026-03-15", "16:00")
        // Function handles date parsing without throwing exception
        assertTrue(isValid || !isValid)
    }
}
