package com.nyantadev.socializee.models

import com.google.gson.annotations.SerializedName

data class Notification(
    val id: String,
    val type: String,                          // follow | like | comment | mention | repost

    @SerializedName("actor_id")
    val actorId: String,

    @SerializedName("actor_username")
    val actorUsername: String,

    @SerializedName("actor_display_name")
    val actorDisplayName: String,

    @SerializedName("actor_avatar_url")
    val actorAvatarUrl: String?,

    @SerializedName("post_id")
    val postId: String?,

    @SerializedName("post_content")
    val postContent: String?,

    @SerializedName("comment_id")
    val commentId: String?,

    @SerializedName("is_read")
    val isRead: Boolean,

    @SerializedName("created_at")
    val createdAt: String
)

data class NotificationsResponse(
    val success: Boolean,
    val notifications: List<Notification>,

    @SerializedName("unread_count")
    val unreadCount: Int,

    val page: Int
)

data class UnreadCountResponse(
    val success: Boolean,
    val count: Int
)

data class DeviceTokenRequest(
    @SerializedName("fcm_token")
    val fcmToken: String,
    val platform: String = "android"
)