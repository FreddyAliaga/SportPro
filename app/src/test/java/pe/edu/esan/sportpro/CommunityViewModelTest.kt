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
import pe.edu.esan.sportpro.data.repository.CommunityRepositoryImpl
import pe.edu.esan.sportpro.domain.model.PostVisibility
import pe.edu.esan.sportpro.domain.model.ReportReason
import pe.edu.esan.sportpro.domain.model.UserRole
import pe.edu.esan.sportpro.ui.viewmodel.CommunityViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: CommunityRepositoryImpl
    private lateinit var viewModel: CommunityViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = CommunityRepositoryImpl(firestore = null)
        viewModel = CommunityViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCreatePostSuccess() {
        val initialCount = viewModel.feedPosts.value.size
        viewModel.createPost(
            authorId = "u_test",
            authorName = "Entrenador Test",
            authorRole = UserRole.DT,
            text = "Esta es una publicación de prueba para la comunidad.",
            visibility = PostVisibility.ACADEMIA
        )

        val updated = viewModel.feedPosts.value
        assertEquals(initialCount + 1, updated.size)
    }

    @Test
    fun testCreatePostRejectsSensitiveData() {
        viewModel.createPost(
            authorId = "u_test",
            authorName = "Entrenador Test",
            authorRole = UserRole.DT,
            text = "Llámenme al 987654321 o escriban a correo@test.com para más informes.",
            visibility = PostVisibility.ACADEMIA
        )

        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value!!.contains("celular") || viewModel.errorMessage.value!!.contains("correos"))
    }

    @Test
    fun testToggleLike() {
        val targetPost = viewModel.feedPosts.value.first()
        val initialLikes = targetPost.likesCount

        viewModel.toggleLike(targetPost.id, "u_tester")
        val updatedPost = viewModel.feedPosts.value.first { it.id == targetPost.id }
        assertEquals(initialLikes + 1, updatedPost.likesCount)
    }

    @Test
    fun testAutoHideOnThreeReports() {
        val targetPost = viewModel.feedPosts.value.first()

        // Send 3 reports
        viewModel.reportContent(targetPost.id, "POST", "u_user1", ReportReason.CONTENIDO_OFENSIVO)
        viewModel.reportContent(targetPost.id, "POST", "u_user2", ReportReason.CONTENIDO_OFENSIVO)
        viewModel.reportContent(targetPost.id, "POST", "u_user3", ReportReason.CONTENIDO_OFENSIVO)

        // Verify post is hidden from feed
        val updatedFeed = viewModel.feedPosts.value
        assertTrue(updatedFeed.none { it.id == targetPost.id })

        // Verify report appears in ADM moderation list
        val reportsList = viewModel.reports.value
        assertTrue(reportsList.any { it.contentId == targetPost.id })
    }

    @Test
    fun testModerateReportRestore() {
        val targetPost = viewModel.feedPosts.value.first()
        viewModel.reportContent(targetPost.id, "POST", "u_u1", ReportReason.CONTENIDO_OFENSIVO)
        viewModel.reportContent(targetPost.id, "POST", "u_u2", ReportReason.CONTENIDO_OFENSIVO)
        viewModel.reportContent(targetPost.id, "POST", "u_u3", ReportReason.CONTENIDO_OFENSIVO)

        val reportId = viewModel.reports.value.first { it.contentId == targetPost.id }.id
        viewModel.moderateReport(reportId, "RESTAURAR")

        // Post should be restored and visible in feed again
        val restoredFeed = viewModel.feedPosts.value
        assertTrue(restoredFeed.any { it.id == targetPost.id })
    }
}
