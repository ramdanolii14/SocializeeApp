package com.nyantadev.socializee.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyantadev.socializee.models.Post
import com.nyantadev.socializee.repository.AppRepository
import kotlinx.coroutines.launch
import java.io.File

sealed class FeedState {
    object Loading : FeedState()
    data class Success(val posts: List<Post>) : FeedState()
    data class Error(val message: String) : FeedState()
}

sealed class CreatePostState {
    object Idle : CreatePostState()
    object Loading : CreatePostState()
    data class Success(val post: Post) : CreatePostState()
    data class Error(val message: String) : CreatePostState()
}

// [NEW] One-shot event wrapper agar observer tidak dipicu ulang saat rotate
class Event<out T>(private val content: T) {
    private var hasBeenHandled = false
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) null
        else { hasBeenHandled = true; content }
    }
}

// [NEW] Alias supaya import di Fragment lebih jelas
typealias BannedEvent = Event<String>

class FeedViewModel(private val repo: AppRepository) : ViewModel() {

    private val _feedState = MutableLiveData<FeedState>()
    val feedState: LiveData<FeedState> = _feedState

    private val _exploreState = MutableLiveData<FeedState>()
    val exploreState: LiveData<FeedState> = _exploreState

    private val _createPostState = MutableLiveData<CreatePostState>(CreatePostState.Idle)
    val createPostState: LiveData<CreatePostState> = _createPostState

    private val _posts = MutableLiveData<MutableList<Post>>(mutableListOf())
    val posts: LiveData<MutableList<Post>> = _posts

    private val _explorePosts = MutableLiveData<MutableList<Post>>(mutableListOf())
    val explorePosts: LiveData<MutableList<Post>> = _explorePosts

    // [NEW] Event banned — di-observe oleh Feed/ExploreFragment untuk trigger logout
    private val _bannedEvent = MutableLiveData<BannedEvent>()
    val bannedEvent: LiveData<BannedEvent> = _bannedEvent

    private var feedPage = 1
    private var explorePage = 1
    var isFeedLoadingMore = false
    var isExploreLoadingMore = false

    // ── Helper: cek apakah response adalah banned ─────────────────────────────
    private fun <T> checkBanned(response: retrofit2.Response<T>): Boolean {
        if (response.code() == 403) {
            // Parse body untuk cek field "banned": true
            try {
                val errorBody = response.errorBody()?.string() ?: ""
                if (errorBody.contains("\"banned\":true")) {
                    val message = Regex("\"message\":\"([^\"]+)\"")
                        .find(errorBody)?.groupValues?.get(1)
                        ?: "Akun kamu telah dibanned oleh admin."
                    _bannedEvent.postValue(BannedEvent(message))
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    // ── Feed ──────────────────────────────────────────────────────────────────

    fun loadFeed(refresh: Boolean = false) {
        if (refresh) feedPage = 1
        viewModelScope.launch {
            if (feedPage == 1) _feedState.value = FeedState.Loading
            isFeedLoadingMore = true
            try {
                val res = repo.getFeed(feedPage)
                if (checkBanned(res)) return@launch
                if (res.isSuccessful && res.body()?.success == true) {
                    val newPosts = res.body()!!.posts ?: emptyList()
                    if (feedPage == 1) {
                        _posts.value = newPosts.toMutableList()
                    } else {
                        _posts.value?.addAll(newPosts)
                        _posts.value = _posts.value
                    }
                    feedPage++
                    _feedState.value = FeedState.Success(_posts.value ?: mutableListOf())
                } else {
                    _feedState.value = FeedState.Error(res.body()?.message ?: "Failed to load feed")
                }
            } catch (e: Exception) {
                _feedState.value = FeedState.Error("Network error: ${e.message}")
            } finally {
                isFeedLoadingMore = false
            }
        }
    }

    // ── Explore ───────────────────────────────────────────────────────────────

    fun loadExplore(refresh: Boolean = false) {
        if (refresh) explorePage = 1
        viewModelScope.launch {
            if (explorePage == 1) _exploreState.value = FeedState.Loading
            isExploreLoadingMore = true
            try {
                val res = repo.getExplore(explorePage)
                if (checkBanned(res)) return@launch
                if (res.isSuccessful && res.body()?.success == true) {
                    val newPosts = res.body()!!.posts ?: emptyList()
                    if (explorePage == 1) {
                        _explorePosts.value = newPosts.toMutableList()
                    } else {
                        _explorePosts.value?.addAll(newPosts)
                        _explorePosts.value = _explorePosts.value
                    }
                    explorePage++
                    _exploreState.value = FeedState.Success(_explorePosts.value ?: mutableListOf())
                } else {
                    _exploreState.value = FeedState.Error(res.body()?.message ?: "Failed")
                }
            } catch (e: Exception) {
                _exploreState.value = FeedState.Error("Network error: ${e.message}")
            } finally {
                isExploreLoadingMore = false
            }
        }
    }

    // ── Buat Post ─────────────────────────────────────────────────────────────

    fun createPost(content: String, imageFiles: List<File>) {
        viewModelScope.launch {
            _createPostState.value = CreatePostState.Loading
            try {
                val res = repo.createPost(content, imageFiles)
                if (checkBanned(res)) return@launch
                if (res.isSuccessful && res.body()?.success == true) {
                    val post = res.body()!!.post!!
                    val current = _posts.value ?: mutableListOf()
                    current.add(0, post)
                    _posts.value = current
                    _createPostState.value = CreatePostState.Success(post)
                } else {
                    _createPostState.value = CreatePostState.Error(res.body()?.message ?: "Failed to post")
                }
            } catch (e: Exception) {
                _createPostState.value = CreatePostState.Error("Network error: ${e.message}")
            }
        }
    }

    // ── Toggle Like ───────────────────────────────────────────────────────────

    fun toggleLike(post: Post, position: Int) {
        viewModelScope.launch {
            try {
                val targetId = post.originalPostId?.takeIf { post.isRepost } ?: post.id
                val res = repo.toggleLike(targetId)
                if (checkBanned(res)) return@launch
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    updatePostLike(_posts, post.id, body.liked!!, body.likesCount!!)
                    updatePostLike(_explorePosts, post.id, body.liked, body.likesCount)
                    if (post.isRepost && post.originalPostId != null) {
                        updatePostLike(_posts, post.originalPostId, body.liked, body.likesCount)
                        updatePostLike(_explorePosts, post.originalPostId, body.liked, body.likesCount)
                    }
                }
            } catch (e: Exception) { /* silent fail */ }
        }
    }

    private fun updatePostLike(liveData: MutableLiveData<MutableList<Post>>, postId: String, liked: Boolean, count: Int) {
        val list = liveData.value ?: return
        val idx = list.indexOfFirst { it.id == postId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(isLiked = liked, likesCount = count)
            liveData.value = list
        }
    }

    // ── Toggle Repost ─────────────────────────────────────────────────────────

    fun toggleRepost(post: Post) {
        viewModelScope.launch {
            try {
                val targetId = if (post.isRepost && post.originalPostId != null)
                    post.originalPostId else post.id

                val res = repo.toggleRepost(targetId)
                if (checkBanned(res)) return@launch
                if (res.isSuccessful && res.body()?.success == true) {
                    val body      = res.body()!!
                    val reposted  = body.reposted ?: return@launch
                    val newCount  = body.repostsCount ?: 0

                    if (reposted) {
                        updatePostRepostStatus(_posts.value, targetId, true, newCount)
                        updatePostRepostStatus(_explorePosts.value, targetId, true, newCount)
                        _posts.value = _posts.value
                        _explorePosts.value = _explorePosts.value
                    } else {
                        updatePostRepostStatus(_posts.value, targetId, false, newCount)
                        updatePostRepostStatus(_explorePosts.value, targetId, false, newCount)
                        removeRepostRows(_posts.value, targetId)
                        removeRepostRows(_explorePosts.value, targetId)
                        _posts.value = _posts.value
                        _explorePosts.value = _explorePosts.value
                    }
                }
            } catch (e: Exception) { /* silent fail */ }
        }
    }

    private fun updatePostRepostStatus(
        list: MutableList<Post>?,
        originalPostId: String,
        reposted: Boolean,
        count: Int
    ) {
        list ?: return
        val idx = list.indexOfFirst { it.id == originalPostId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(isReposted = reposted, repostsCount = count)
        }
        list.forEachIndexed { i, p ->
            if (p.originalPostId == originalPostId) {
                list[i] = p.copy(isReposted = reposted, repostsCount = count)
            }
        }
    }

    private fun removeRepostRows(list: MutableList<Post>?, originalPostId: String) {
        list?.removeAll { it.isRepost && it.originalPostId == originalPostId }
    }

    // ── Hapus Post ────────────────────────────────────────────────────────────

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                val res = repo.deletePost(postId)
                if (checkBanned(res)) return@launch
                _posts.value?.let { list ->
                    list.removeAll { it.id == postId || it.originalPostId == postId }
                    _posts.value = list
                }
                _explorePosts.value?.let { list ->
                    list.removeAll { it.id == postId || it.originalPostId == postId }
                    _explorePosts.value = list
                }
            } catch (e: Exception) { /* silent fail */ }
        }
    }

    fun resetCreatePost() {
        _createPostState.value = CreatePostState.Idle
    }
}