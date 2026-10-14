package pe.edu.esan.sportpro.domain.model

data class CommunityComment(
    val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: UserRole = UserRole.DT,
    val text: String = "",
    val reportCount: Int = 0,
    val isOculto: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class CommunityPost(
    val id: String = "",
    val academyId: String = "halcones_1",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: UserRole = UserRole.DT,
    val text: String = "",
    val visibility: PostVisibility = PostVisibility.ACADEMIA,
    val likesCount: Int = 0,
    val likedByUsers: Set<String> = emptySet(),
    val reportCount: Int = 0,
    val isOculto: Boolean = false,
    val commentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
