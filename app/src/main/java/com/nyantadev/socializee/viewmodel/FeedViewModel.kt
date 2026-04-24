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

    private var feedPage = 1
    private var explorePage = 1
    var isFeedLoadingMore = false
    var isExploreLoadingMore = false

    fun loadFeed(refresh: Boolean = false) {
        if (refresh) feedPage = 1
        viewModelScope.launch {
            if (feedPage == 1) _feedState.value = FeedState.Loading
            isFeedLoadingMore = true
            try {
                val res = repo.getFeed(feedPage)
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

    fun loadExplore(refresh: Boolean = false) {
        if (refresh) explorePage = 1
        viewModelScope.launch {
            if (explorePage == 1) _exploreState.value = FeedState.Loading
            isExploreLoadingMore = true
            try {
                val res = repo.getExplore(explorePage)
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

    fun createPost(content: String, imageFiles: List<File>) {
        viewModelScope.launch {
            _createPostState.value = CreatePostState.Loading
            try {
                val res = repo.createPost(content, imageFiles)
                if (res.isSuccessful && res.body()?.success == true) {
                    val post = res.body()!!.post!!
                    // Prepend to feed
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

    fun toggleLike(post: Post, position: Int) {
        viewModelScope.launch {
            try {
                val res = repo.toggleLike(post.id)
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    // Update in feed list
                    updatePostLike(_posts.value, post.id, body.liked!!, body.likesCount!!)
                    updatePostLike(_explorePosts.value, post.id, body.liked, body.likesCount)
                }
            } catch (e: Exception) { /* silent fail */ }
        }
    }

    private fun updatePostLike(list: MutableList<Post>?, postId: String, liked: Boolean, count: Int) {
        list?.let { posts ->
            val idx = posts.indexOfFirst { it.id == postId }
            if (idx >= 0) {
                posts[idx] = posts[idx].copy(isLiked = liked, likesCount = count)
                _posts.value = _posts.value // trigger observer
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                repo.deletePost(postId)
                _posts.value?.removeAll { it.id == postId }
                _posts.value = _posts.value
                _explorePosts.value?.removeAll { it.id == postId }
                _explorePosts.value = _explorePosts.value
            } catch (e: Exception) { /* silent fail */ }
        }
    }

    fun resetCreatePost() {
        _createPostState.value = CreatePostState.Idle
    }
}
