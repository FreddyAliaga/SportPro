package pe.edu.esan.sportpro.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.edu.esan.sportpro.domain.model.CommunityComment
import pe.edu.esan.sportpro.domain.model.CommunityPost
import pe.edu.esan.sportpro.domain.model.CommunityReport
import pe.edu.esan.sportpro.domain.model.PostVisibility
import pe.edu.esan.sportpro.domain.model.ReportReason
import pe.edu.esan.sportpro.domain.model.UserRole
import java.util.UUID
import java.util.regex.Pattern

interface CommunityRepository {
    val posts: StateFlow<List<CommunityPost>>
    val comments: StateFlow<List<CommunityComment>>
    val reports: StateFlow<List<CommunityReport>>
    val blockedUserIds: StateFlow<Set<String>>

    suspend fun createPost(
        authorId: String,
        authorName: String,
        authorRole: UserRole,
        text: String,
        visibility: PostVisibility
    ): Result<CommunityPost>

    suspend fun toggleLike(postId: String, userId: String): Result<Unit>
    suspend fun addComment(postId: String, authorId: String, authorName: String, authorRole: UserRole, text: String): Result<CommunityComment>
    suspend fun getCommentsForPost(postId: String): List<CommunityComment>
    suspend fun reportContent(contentId: String, contentType: String, reportedByUserId: String, reason: ReportReason): Result<Unit>
    suspend fun moderateReport(reportId: String, action: String): Result<Unit>
    suspend fun blockUser(userId: String): Result<Unit>
}

class CommunityRepositoryImpl(
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
) : CommunityRepository {

    private val phonePattern = Pattern.compile("(\\b9\\d{8}\\b|\\b\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{3}\\b)")
    private val emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    private val _posts = MutableStateFlow(
        listOf(
            CommunityPost(
                id = "post_1",
                academyId = "halcones_1",
                authorId = "u_carlos",
                authorName = "Carlos Medina",
                authorRole = UserRole.DT,
                text = "Gran desempeño del equipo Sub-12 A en la sesión táctica de hoy. ¡Seguimos preparando el próximo partido!",
                visibility = PostVisibility.ACADEMIA,
                likesCount = 3,
                likedByUsers = setOf("u_luis", "u_jorge", "u_g1"),
                reportCount = 0,
                isOculto = false,
                commentCount = 2,
                createdAt = System.currentTimeMillis() - 3600000L
            ),
            CommunityPost(
                id = "post_2",
                academyId = "halcones_1",
                authorId = "u_admin",
                authorName = "Rosa Quispe",
                authorRole = UserRole.ADM,
                text = "Recordatorio a todos los padres de familia: este sábado tendremos jornada de integración deportiva en el campo principal.",
                visibility = PostVisibility.PUBLICA,
                likesCount = 8,
                likedByUsers = setOf("u_carlos", "u_luis", "u_p1", "u_g2"),
                reportCount = 0,
                isOculto = false,
                commentCount = 1,
                createdAt = System.currentTimeMillis() - 7200000L
            )
        )
    )
    override val posts: StateFlow<List<CommunityPost>> = _posts.asStateFlow()

    private val _comments = MutableStateFlow(
        listOf(
            CommunityComment(
                id = "comm_1",
                postId = "post_1",
                authorId = "u_g1",
                authorName = "Ana Rojas",
                authorRole = UserRole.PAD,
                text = "¡Excelente trabajo del equipo y del entrenador!",
                createdAt = System.currentTimeMillis() - 1800000L
            ),
            CommunityComment(
                id = "comm_2",
                postId = "post_1",
                authorId = "u_p1",
                authorName = "Mateo Rojas",
                authorRole = UserRole.JUG,
                text = "¡A seguir entrenando fuerte para el sábado!",
                createdAt = System.currentTimeMillis() - 900000L
            )
        )
    )
    override val comments: StateFlow<List<CommunityComment>> = _comments.asStateFlow()

    private val _reports = MutableStateFlow<List<CommunityReport>>(emptyList())
    override val reports: StateFlow<List<CommunityReport>> = _reports.asStateFlow()

    private fun containsSensitiveData(text: String): Boolean {
        val hasPhone = phonePattern.matcher(text).find()
        val hasEmail = emailPattern.matcher(text).find()
        return hasPhone || hasEmail
    }

    override suspend fun createPost(
        authorId: String,
        authorName: String,
        authorRole: UserRole,
        text: String,
        visibility: PostVisibility
    ): Result<CommunityPost> {
        if (_blockedUserIds.value.contains(authorId)) {
            return Result.failure(IllegalStateException("Tu cuenta se encuentra bloqueada para realizar publicaciones."))
        }

        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("El texto de la publicación no puede estar vacío."))
        }

        if (text.length > 1000) {
            return Result.failure(IllegalArgumentException("La publicación no puede superar los 1,000 caracteres."))
        }

        if (containsSensitiveData(text)) {
            return Result.failure(IllegalArgumentException("No se permiten publicaciones con números de celular ni correos electrónicos."))
        }

        val newPost = CommunityPost(
            id = "post_" + UUID.randomUUID().toString().take(8),
            academyId = "halcones_1",
            authorId = authorId,
            authorName = authorName,
            authorRole = authorRole,
            text = text.trim(),
            visibility = visibility,
            createdAt = System.currentTimeMillis()
        )

        _posts.value = listOf(newPost) + _posts.value

        try {
            firestore?.collection("posts")?.document(newPost.id)?.set(newPost)
        } catch (e: Exception) {
            // Local state updated
        }

        return Result.success(newPost)
    }

    override suspend fun toggleLike(postId: String, userId: String): Result<Unit> {
        if (_blockedUserIds.value.contains(userId)) {
            return Result.failure(IllegalStateException("Tu cuenta se encuentra bloqueada."))
        }

        _posts.value = _posts.value.map { post ->
            if (post.id == postId) {
                val currentLikes = post.likedByUsers.toMutableSet()
                if (currentLikes.contains(userId)) {
                    currentLikes.remove(userId)
                } else {
                    currentLikes.add(userId)
                }
                post.copy(
                    likesCount = currentLikes.size,
                    likedByUsers = currentLikes
                )
            } else post
        }

        return Result.success(Unit)
    }

    override suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        authorRole: UserRole,
        text: String
    ): Result<CommunityComment> {
        if (_blockedUserIds.value.contains(authorId)) {
            return Result.failure(IllegalStateException("Tu cuenta se encuentra bloqueada para comentar."))
        }

        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("El comentario no puede estar vacío."))
        }

        if (text.length > 500) {
            return Result.failure(IllegalArgumentException("El comentario no puede superar los 500 caracteres."))
        }

        if (containsSensitiveData(text)) {
            return Result.failure(IllegalArgumentException("No se permiten comentarios con números de celular ni correos electrónicos."))
        }

        val newComment = CommunityComment(
            id = "comm_" + UUID.randomUUID().toString().take(8),
            postId = postId,
            authorId = authorId,
            authorName = authorName,
            authorRole = authorRole,
            text = text.trim(),
            createdAt = System.currentTimeMillis()
        )

        _comments.value = _comments.value + newComment

        _posts.value = _posts.value.map { post ->
            if (post.id == postId) post.copy(commentCount = post.commentCount + 1) else post
        }

        return Result.success(newComment)
    }

    override suspend fun getCommentsForPost(postId: String): List<CommunityComment> {
        return _comments.value.filter { it.postId == postId && !it.isOculto }
    }

    override suspend fun reportContent(
        contentId: String,
        contentType: String,
        reportedByUserId: String,
        reason: ReportReason
    ): Result<Unit> {
        if (contentType == "POST") {
            var updatedPost: CommunityPost? = null
            _posts.value = _posts.value.map { post ->
                if (post.id == contentId) {
                    val newCount = post.reportCount + 1
                    val autoHide = newCount >= 3 // RN-019: Auto-hide at 3 reports
                    val p = post.copy(reportCount = newCount, isOculto = autoHide)
                    updatedPost = p
                    p
                } else post
            }

            val targetPost = updatedPost ?: return Result.failure(IllegalArgumentException("Publicación no encontrada"))

            val report = CommunityReport(
                id = "rep_" + UUID.randomUUID().toString().take(8),
                contentId = contentId,
                contentType = "POST",
                contentPreview = targetPost.text.take(80),
                authorId = targetPost.authorId,
                authorName = targetPost.authorName,
                reportedByUserId = reportedByUserId,
                reason = reason,
                status = if (targetPost.isOculto) "MANTENIDO_OCULTO" else "PENDIENTE"
            )

            _reports.value = _reports.value + report
        } else {
            var updatedComment: CommunityComment? = null
            _comments.value = _comments.value.map { comm ->
                if (comm.id == contentId) {
                    val newCount = comm.reportCount + 1
                    val autoHide = newCount >= 3
                    val c = comm.copy(reportCount = newCount, isOculto = autoHide)
                    updatedComment = c
                    c
                } else comm
            }

            val targetComm = updatedComment ?: return Result.failure(IllegalArgumentException("Comentario no encontrado"))

            val report = CommunityReport(
                id = "rep_" + UUID.randomUUID().toString().take(8),
                contentId = contentId,
                contentType = "COMMENT",
                contentPreview = targetComm.text.take(80),
                authorId = targetComm.authorId,
                authorName = targetComm.authorName,
                reportedByUserId = reportedByUserId,
                reason = reason,
                status = if (targetComm.isOculto) "MANTENIDO_OCULTO" else "PENDIENTE"
            )

            _reports.value = _reports.value + report
        }

        return Result.success(Unit)
    }

    override suspend fun moderateReport(reportId: String, action: String): Result<Unit> {
        val targetReport = _reports.value.find { it.id == reportId } ?: return Result.failure(IllegalArgumentException("Reporte no encontrado"))

        when (action) {
            "RESTAURAR" -> {
                if (targetReport.contentType == "POST") {
                    _posts.value = _posts.value.map {
                        if (it.id == targetReport.contentId) it.copy(isOculto = false, reportCount = 0) else it
                    }
                } else {
                    _comments.value = _comments.value.map {
                        if (it.id == targetReport.contentId) it.copy(isOculto = false, reportCount = 0) else it
                    }
                }
            }
            "MANTENER_OCULTO" -> {
                if (targetReport.contentType == "POST") {
                    _posts.value = _posts.value.map {
                        if (it.id == targetReport.contentId) it.copy(isOculto = true) else it
                    }
                } else {
                    _comments.value = _comments.value.map {
                        if (it.id == targetReport.contentId) it.copy(isOculto = true) else it
                    }
                }
            }
            "ELIMINADO" -> {
                if (targetReport.contentType == "POST") {
                    _posts.value = _posts.value.filterNot { it.id == targetReport.contentId }
                } else {
                    _comments.value = _comments.value.filterNot { it.id == targetReport.contentId }
                }
            }
            "BLOQUEAR_AUTOR" -> {
                blockUser(targetReport.authorId)
            }
        }

        _reports.value = _reports.value.map {
            if (it.id == reportId) it.copy(status = action) else it
        }

        return Result.success(Unit)
    }

    override suspend fun blockUser(userId: String): Result<Unit> {
        _blockedUserIds.value = _blockedUserIds.value + userId
        return Result.success(Unit)
    }
}
