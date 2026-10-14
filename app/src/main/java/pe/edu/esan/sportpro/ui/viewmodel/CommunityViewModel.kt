package pe.edu.esan.sportpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.repository.CommunityRepository
import pe.edu.esan.sportpro.data.repository.CommunityRepositoryImpl
import pe.edu.esan.sportpro.domain.model.CommunityComment
import pe.edu.esan.sportpro.domain.model.CommunityPost
import pe.edu.esan.sportpro.domain.model.CommunityReport
import pe.edu.esan.sportpro.domain.model.PostVisibility
import pe.edu.esan.sportpro.domain.model.ReportReason
import pe.edu.esan.sportpro.domain.model.UserRole

class CommunityViewModel(
    private val repository: CommunityRepository = CommunityRepositoryImpl()
) : ViewModel() {

    // Feed showing non-hidden posts
    val feedPosts: StateFlow<List<CommunityPost>> = repository.posts.map { list ->
        list.filter { !it.isOculto }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val reports: StateFlow<List<CommunityReport>> = repository.reports
    val blockedUserIds: StateFlow<Set<String>> = repository.blockedUserIds

    private val _selectedPost = MutableStateFlow<CommunityPost?>(null)
    val selectedPost: StateFlow<CommunityPost?> = _selectedPost.asStateFlow()

    private val _postComments = MutableStateFlow<List<CommunityComment>>(emptyList())
    val postComments: StateFlow<List<CommunityComment>> = _postComments.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    fun selectPost(post: CommunityPost?) {
        _selectedPost.value = post
        if (post != null) {
            loadCommentsForPost(post.id)
        } else {
            _postComments.value = emptyList()
        }
    }

    fun loadCommentsForPost(postId: String) {
        viewModelScope.launch {
            _postComments.value = repository.getCommentsForPost(postId)
        }
    }

    fun createPost(
        authorId: String,
        authorName: String,
        authorRole: UserRole,
        text: String,
        visibility: PostVisibility
    ) {
        viewModelScope.launch {
            val result = repository.createPost(authorId, authorName, authorRole, text, visibility)
            result.onSuccess {
                _successMessage.value = "Publicación realizada con éxito."
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Error al publicar."
            }
        }
    }

    fun toggleLike(postId: String, userId: String) {
        viewModelScope.launch {
            val result = repository.toggleLike(postId, userId)
            result.onFailure { ex ->
                _errorMessage.value = ex.message
            }
        }
    }

    fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        authorRole: UserRole,
        text: String
    ) {
        viewModelScope.launch {
            val result = repository.addComment(postId, authorId, authorName, authorRole, text)
            result.onSuccess {
                _successMessage.value = "Comentario agregado."
                loadCommentsForPost(postId)
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Error al comentar."
            }
        }
    }

    fun reportContent(
        contentId: String,
        contentType: String,
        reportedByUserId: String,
        reason: ReportReason
    ) {
        viewModelScope.launch {
            val result = repository.reportContent(contentId, contentType, reportedByUserId, reason)
            result.onSuccess {
                _successMessage.value = "Gracias por tu reporte. El contenido será revisado."
            }.onFailure { ex ->
                _errorMessage.value = ex.message
            }
        }
    }

    fun moderateReport(reportId: String, action: String) {
        viewModelScope.launch {
            val result = repository.moderateReport(reportId, action)
            result.onSuccess {
                val actionDesc = when (action) {
                    "RESTAURAR" -> "Contenido restaurado."
                    "MANTENER_OCULTO" -> "Contenido mantenido oculto."
                    "ELIMINADO" -> "Contenido eliminado."
                    "BLOQUEAR_AUTOR" -> "Autor bloqueado."
                    else -> "Acción realizada."
                }
                _successMessage.value = actionDesc
            }.onFailure { ex ->
                _errorMessage.value = ex.message
            }
        }
    }

    fun blockUser(userId: String) {
        viewModelScope.launch {
            repository.blockUser(userId)
            _successMessage.value = "Usuario bloqueado. Ya no podrá publicar ni comentar."
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    class Factory(private val repository: CommunityRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CommunityViewModel(repository) as T
        }
    }
}
