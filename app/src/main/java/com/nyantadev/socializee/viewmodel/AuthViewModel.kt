package com.nyantadev.socializee.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyantadev.socializee.models.User
import com.nyantadev.socializee.repository.AppRepository
import kotlinx.coroutines.launch
import java.io.File

sealed class AuthState {
    object Loading : AuthState()
    data class Success(val token: String, val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class ProfileUpdateState {
    object Loading : ProfileUpdateState()
    data class Success(val user: User) : ProfileUpdateState()
    data class Error(val message: String) : ProfileUpdateState()
}

class AuthViewModel(private val repo: AppRepository) : ViewModel() {

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    private val _profileState = MutableLiveData<ProfileUpdateState>()
    val profileState: LiveData<ProfileUpdateState> = _profileState

    fun register(username: String, email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val res = repo.register(username, email, password, displayName)
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    _authState.value = AuthState.Success(body.token!!, body.user!!)
                } else {
                    _authState.value = AuthState.Error(res.body()?.message ?: "Registration failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Network error: ${e.message}")
            }
        }
    }

    fun login(login: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val res = repo.login(login, password)
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    _authState.value = AuthState.Success(body.token!!, body.user!!)
                } else {
                    _authState.value = AuthState.Error(res.body()?.message ?: "Login failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Network error: ${e.message}")
            }
        }
    }

    fun updateProfile(displayName: String, bio: String, avatarFile: File?) {
        viewModelScope.launch {
            _profileState.value = ProfileUpdateState.Loading
            try {
                val res = repo.updateProfile(displayName, bio, avatarFile)
                if (res.isSuccessful && res.body()?.success == true) {
                    _profileState.value = ProfileUpdateState.Success(res.body()!!.user!!)
                } else {
                    _profileState.value = ProfileUpdateState.Error(res.body()?.message ?: "Update failed")
                }
            } catch (e: Exception) {
                _profileState.value = ProfileUpdateState.Error("Network error: ${e.message}")
            }
        }
    }
}
