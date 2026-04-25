package com.nyantadev.socializee.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyantadev.socializee.models.Post
import com.nyantadev.socializee.models.User
import com.nyantadev.socializee.repository.AppRepository
import kotlinx.coroutines.launch

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val user: User, val posts: List<Post>) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

class ProfileViewModel(private val repo: AppRepository) : ViewModel() {

    private val _profileState = MutableLiveData<ProfileState>()
    val profileState: LiveData<ProfileState> = _profileState

    private val _followState = MutableLiveData<Pair<Boolean, Int>>()
    val followState: LiveData<Pair<Boolean, Int>> = _followState

    private val _searchResults = MutableLiveData<List<User>>()
    val searchResults: LiveData<List<User>> = _searchResults

    private val _searchLoading = MutableLiveData<Boolean>(false)
    val searchLoading: LiveData<Boolean> = _searchLoading

    // ── Baru: list followers & following ──────────────────────────────────────
    private val _followersList = MutableLiveData<List<User>>()
    val followersList: LiveData<List<User>> = _followersList

    private val _followingList = MutableLiveData<List<User>>()
    val followingList: LiveData<List<User>> = _followingList
    // ──────────────────────────────────────────────────────────────────────────

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                val userRes  = repo.getUserProfile(userId)
                val postsRes = repo.getUserPosts(userId)

                if (userRes.isSuccessful && userRes.body()?.success == true) {
                    val user  = userRes.body()!!.user!!
                    val posts = if (postsRes.isSuccessful) postsRes.body()?.posts ?: emptyList() else emptyList()
                    _profileState.value = ProfileState.Success(user, posts)
                } else {
                    _profileState.value = ProfileState.Error("Failed to load profile")
                }
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error("Network error: ${e.message}")
            }
        }
    }

    fun toggleFollow(userId: String) {
        viewModelScope.launch {
            try {
                val res = repo.toggleFollow(userId)
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    _followState.value = Pair(body.following!!, body.followersCount!!)
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchLoading.value = true
            try {
                val res = repo.searchUsers(query)
                if (res.isSuccessful && res.body()?.success == true) {
                    _searchResults.value = res.body()!!.users ?: emptyList()
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _searchLoading.value = false
            }
        }
    }

    // ── Baru ──────────────────────────────────────────────────────────────────

    fun loadFollowers(userId: String) {
        viewModelScope.launch {
            try {
                val res = repo.getFollowers(userId)
                _followersList.value =
                    if (res.isSuccessful && res.body()?.success == true)
                        res.body()!!.users ?: emptyList()
                    else emptyList()
            } catch (e: Exception) {
                _followersList.value = emptyList()
            }
        }
    }

    fun loadFollowing(userId: String) {
        viewModelScope.launch {
            try {
                val res = repo.getFollowing(userId)
                _followingList.value =
                    if (res.isSuccessful && res.body()?.success == true)
                        res.body()!!.users ?: emptyList()
                    else emptyList()
            } catch (e: Exception) {
                _followingList.value = emptyList()
            }
        }
    }
    // ──────────────────────────────────────────────────────────────────────────
}