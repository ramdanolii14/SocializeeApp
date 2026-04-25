package com.nyantadev.socializee.api

import com.nyantadev.socializee.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ======= AUTH =======
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<AuthResponse>

    @Multipart
    @PUT("auth/profile")
    suspend fun updateProfile(
        @Part("display_name") displayName: RequestBody,
        @Part("bio") bio: RequestBody,
        @Part avatar: MultipartBody.Part?
    ): Response<AuthResponse>

    // ======= POSTS =======
    @GET("posts/feed")
    suspend fun getFeed(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<PostsResponse>

    @GET("posts/explore")
    suspend fun getExplore(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<PostsResponse>

    @GET("posts/user/{userId}")
    suspend fun getUserPosts(
        @Path("userId") userId: String,
        @Query("page") page: Int = 1
    ): Response<PostsResponse>

    @Multipart
    @POST("posts")
    suspend fun createPost(
        @Part("content") content: RequestBody,
        @Part images: List<MultipartBody.Part>,
        @Part videos: List<MultipartBody.Part>
    ): Response<PostResponse>

    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") id: String): Response<GenericResponse>

    @POST("posts/{id}/like")
    suspend fun toggleLike(@Path("id") id: String): Response<LikeResponse>

    @POST("posts/{id}/repost")
    suspend fun toggleRepost(@Path("id") id: String): Response<RepostResponse>

    @GET("posts/{id}/comments")
    suspend fun getComments(@Path("id") id: String): Response<CommentsResponse>

    @POST("posts/{id}/comments")
    suspend fun addComment(
        @Path("id") id: String,
        @Body request: CommentRequest
    ): Response<CommentResponse>

    // ======= USERS =======
    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<UsersResponse>

    @GET("users/{id}")
    suspend fun getUserProfile(@Path("id") id: String): Response<UserResponse>

    @POST("users/{id}/follow")
    suspend fun toggleFollow(@Path("id") id: String): Response<FollowResponse>

    @GET("users/{id}/followers")
    suspend fun getFollowers(@Path("id") id: String): Response<UsersResponse>

    @GET("users/{id}/following")
    suspend fun getFollowing(@Path("id") id: String): Response<UsersResponse>

    // ======= NOTIFICATIONS =======
    @GET("notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<NotificationsResponse>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<UnreadCountResponse>

    @PATCH("notifications/read-all")
    suspend fun markAllRead(): Response<GenericResponse>

    @PATCH("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<GenericResponse>

    @POST("notifications/device-token")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): Response<GenericResponse>

    @DELETE("notifications/device-token")
    suspend fun removeDeviceToken(@Body request: DeviceTokenRequest): Response<GenericResponse>
}