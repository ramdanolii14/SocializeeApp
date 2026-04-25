package com.nyantadev.socializee.models

import com.google.gson.annotations.SerializedName

data class User(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    @SerializedName("display_name") val displayName: String = "",
    val bio: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("posts_count") val postsCount: Int = 0,
    @SerializedName("is_following") val isFollowing: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("role") val role: String? = "user",         // [NEW]
    @SerializedName("is_banned") val isBanned: Boolean = false  // [NEW]
)

data class PostImage(
    val id: String = "",
    val url: String = "",
    val order: Int = 0
)

data class Post(
    val id: String = "",
    @SerializedName("user_id") val userId: String = "",
    val content: String = "",
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    @SerializedName("reposts_count") val repostsCount: Int = 0,
    val images: List<PostImage> = emptyList(),
    @SerializedName("created_at") val createdAt: String = "",

    // Penulis konten asli
    val username: String = "",
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",

    // Status interaksi user saat ini
    @SerializedName("is_liked") var isLiked: Boolean = false,
    @SerializedName("is_reposted") var isReposted: Boolean = false,

    // ── Kolom repost ──────────────────────────────────────────────────────────
    @SerializedName("is_repost") val isRepost: Boolean = false,
    @SerializedName("original_post_id") val originalPostId: String? = null,
    @SerializedName("reposted_by_user_id") val repostedByUserId: String? = null,
    @SerializedName("reposted_by_username") val repostedByUsername: String? = null,
    @SerializedName("reposted_by_display_name") val repostedByDisplayName: String? = null,
    @SerializedName("reposted_by_avatar_url") val repostedByAvatarUrl: String? = null,
)

data class Comment(
    val id: String = "",
    @SerializedName("post_id") val postId: String = "",
    @SerializedName("user_id") val userId: String = "",
    val content: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

// ── API Request Models ────────────────────────────────────────────────────────
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    @SerializedName("display_name") val displayName: String
)

data class LoginRequest(
    val login: String,
    val password: String
)

data class CommentRequest(
    val content: String
)

// ── API Response Models ───────────────────────────────────────────────────────
data class AuthResponse(
    val success: Boolean,
    val token: String?,
    val user: User?,
    val message: String?
)

data class PostsResponse(
    val success: Boolean,
    val posts: List<Post>?,
    val message: String?
)

data class PostResponse(
    val success: Boolean,
    val post: Post?,
    val message: String?
)

data class UsersResponse(
    val success: Boolean,
    val users: List<User>?,
    val message: String?
)

data class UserResponse(
    val success: Boolean,
    val user: User?,
    val message: String?
)

data class CommentsResponse(
    val success: Boolean,
    val comments: List<Comment>?,
    val message: String?
)

data class CommentResponse(
    val success: Boolean,
    val comment: Comment?,
    val message: String?
)

data class LikeResponse(
    val success: Boolean,
    val liked: Boolean?,
    @SerializedName("likes_count") val likesCount: Int?,
    val message: String?
)

data class FollowResponse(
    val success: Boolean,
    val following: Boolean?,
    @SerializedName("followers_count") val followersCount: Int?,
    val message: String?
)

data class RepostResponse(
    val success: Boolean,
    val reposted: Boolean?,
    @SerializedName("reposts_count") val repostsCount: Int?,
    val message: String?
)

data class GenericResponse(
    val success: Boolean,
    val message: String?
)