package com.nyantadev.socializee.repository

import com.nyantadev.socializee.api.ApiService
import com.nyantadev.socializee.models.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

class AppRepository(private val api: ApiService) {

    // ===== AUTH =====
    suspend fun register(username: String, email: String, password: String, displayName: String) =
        api.register(RegisterRequest(username, email, password, displayName))

    suspend fun login(login: String, password: String) =
        api.login(LoginRequest(login, password))

    suspend fun getMe() = api.getMe()

    suspend fun updateProfile(displayName: String, bio: String, avatarFile: File?): Response<AuthResponse> {
        val namePart = displayName.toRequestBody("text/plain".toMediaTypeOrNull())
        val bioPart  = bio.toRequestBody("text/plain".toMediaTypeOrNull())
        val avatarPart = avatarFile?.let {
            val ext      = it.extension.lowercase()
            val fileName = "avatar_upload.${if (ext.isNotEmpty()) ext else "jpg"}"
            MultipartBody.Part.createFormData("avatar", fileName, it.toMediaTypedRequestBody())
        }
        return api.updateProfile(namePart, bioPart, avatarPart)
    }

    // ===== POSTS =====
    suspend fun getFeed(page: Int = 1)                    = api.getFeed(page)
    suspend fun getExplore(page: Int = 1)                 = api.getExplore(page)
    suspend fun getUserPosts(userId: String, page: Int = 1) = api.getUserPosts(userId, page)

    suspend fun createPost(content: String, imageFiles: List<File>): Response<PostResponse> {
        val contentPart    = content.toRequestBody("text/plain".toMediaTypeOrNull())
        val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "3gp", "wmv", "flv")

        val imageParts = imageFiles
            .filter { it.extension.lowercase() !in videoExtensions }
            .mapIndexed { i, file ->
                val ext      = file.extension.lowercase()
                val fileName = "image_$i.${if (ext.isNotEmpty()) ext else "jpg"}"
                MultipartBody.Part.createFormData("images", fileName, file.toMediaTypedRequestBody())
            }

        val videoParts = imageFiles
            .filter { it.extension.lowercase() in videoExtensions }
            .mapIndexed { i, file ->
                val ext      = file.extension.lowercase()
                val fileName = "video_$i.${if (ext.isNotEmpty()) ext else "mp4"}"
                MultipartBody.Part.createFormData("videos", fileName, file.toMediaTypedRequestBody())
            }

        return api.createPost(contentPart, imageParts, videoParts)
    }

    suspend fun deletePost(id: String)        = api.deletePost(id)
    suspend fun toggleLike(postId: String)    = api.toggleLike(postId)
    suspend fun toggleRepost(postId: String)  = api.toggleRepost(postId)
    suspend fun getComments(postId: String)   = api.getComments(postId)
    suspend fun addComment(postId: String, content: String) =
        api.addComment(postId, CommentRequest(content))

    // ===== USERS =====
    suspend fun searchUsers(query: String)          = api.searchUsers(query)
    suspend fun getUserProfile(userId: String)      = api.getUserProfile(userId)
    suspend fun toggleFollow(userId: String)        = api.toggleFollow(userId)
    suspend fun getFollowers(userId: String)        = api.getFollowers(userId)
    suspend fun getFollowing(userId: String)        = api.getFollowing(userId)

    // ===== NOTIFICATIONS =====
    suspend fun getNotifications(page: Int = 1)     = api.getNotifications(page)
    suspend fun getUnreadCount()                    = api.getUnreadCount()
    suspend fun markAllRead()                       = api.markAllRead()
    suspend fun markRead(id: String)                = api.markRead(id)
    suspend fun registerDeviceToken(token: String)  = api.registerDeviceToken(DeviceTokenRequest(token))
    suspend fun removeDeviceToken(token: String)    = api.removeDeviceToken(DeviceTokenRequest(token))

    // ===== HELPER =====
    private fun File.toMediaTypedRequestBody(): RequestBody {
        val mimeType = when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png"         -> "image/png"
            "gif"         -> "image/gif"
            "webp"        -> "image/webp"
            "bmp"         -> "image/bmp"
            "mp4"         -> "video/mp4"
            "mov"         -> "video/quicktime"
            "avi"         -> "video/x-msvideo"
            "mkv"         -> "video/x-matroska"
            "3gp"         -> "video/3gpp"
            "wmv"         -> "video/x-ms-wmv"
            "flv"         -> "video/x-flv"
            else          -> "application/octet-stream"
        }
        return asRequestBody(mimeType.toMediaType())
    }
}