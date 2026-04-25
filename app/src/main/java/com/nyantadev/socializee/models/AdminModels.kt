package com.nyantadev.socializee.models

import com.google.gson.annotations.SerializedName

// ── Tambahkan field ini ke data class User yang sudah ada ─────────────────────
// Catatan: hanya tambah field baru; jangan ubah field yang sudah ada.
//
// Di file User.kt kamu yang sudah ada, tambahkan dua field berikut:
//
//   @SerializedName("role")       val role: String? = "user",
//   @SerializedName("is_banned")  val isBanned: Boolean = false,
//
// Contoh hasil akhir User.kt (sesuaikan dengan field yang sudah kamu punya):
//
// data class User(
//     @SerializedName("id")              val id: String,
//     @SerializedName("username")        val username: String,
//     @SerializedName("display_name")    val displayName: String,
//     @SerializedName("bio")             val bio: String = "",
//     @SerializedName("avatar_url")      val avatarUrl: String = "",
//     @SerializedName("followers_count") val followersCount: Int = 0,
//     @SerializedName("following_count") val followingCount: Int = 0,
//     @SerializedName("posts_count")     val postsCount: Int = 0,
//     @SerializedName("is_following")    val isFollowing: Boolean = false,
//     @SerializedName("role")            val role: String? = "user",       // [NEW]
//     @SerializedName("is_banned")       val isBanned: Boolean = false,    // [NEW]
// )

// ── Response untuk GET /api/admin/users/:id/status ────────────────────────────
data class AdminUserStatusResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("user")     val user: AdminUserStatus?
)

data class AdminUserStatus(
    @SerializedName("id")        val id: String,
    @SerializedName("username")  val username: String,
    @SerializedName("role")      val role: String,
    @SerializedName("is_banned") val isBanned: Boolean,
    @SerializedName("banned_at") val bannedAt: String?
)