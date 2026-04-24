package com.nyantadev.socializee.repository

import com.nyantadev.socializee.api.ApiService
import com.nyantadev.socializee.models.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
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
        val bioPart = bio.toRequestBody("text/plain".toMediaTypeOrNull())
        val avatarPart = avatarFile?.let {
            val reqBody = it.asRequestBody("image/*".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("avatar", it.name, reqBody)
        }
        return api.updateProfile(namePart, bioPart, avatarPart)
    }

    // ===== POSTS =====
    suspend fun getFeed(page: Int = 1) = api.getFeed(page)

    suspend fun getExplore(page: Int = 1) = api.getExplore(page)

    suspend fun getUserPosts(userId: String, page: Int = 1) = api.getUserPosts(userId, page)

    suspend fun createPost(content: String, imageFiles: List<File>): Response<PostResponse> {
        val contentPart = content.toRequestBody("text/plain".toMediaTypeOrNull())
        val imageParts = imageFiles.mapIndexed { i, file ->
            val reqBody = file.asRequestBody("image/*".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("images", file.name, reqBody)
        }
        return api.createPost(contentPart, imageParts)
    }

    suspend fun deletePost(id: String) = api.deletePost(id)

    suspend fun toggleLike(postId: String) = api.toggleLike(postId)

    suspend fun getComments(postId: String) = api.getComments(postId)

    suspend fun addComment(postId: String, content: String) =
        api.addComment(postId, CommentRequest(content))

    // ===== USERS =====
    suspend fun searchUsers(query: String) = api.searchUsers(query)

    suspend fun getUserProfile(userId: String) = api.getUserProfile(userId)

    suspend fun toggleFollow(userId: String) = api.toggleFollow(userId)

    suspend fun getFollowers(userId: String) = api.getFollowers(userId)

    suspend fun getFollowing(userId: String) = api.getFollowing(userId)
}
