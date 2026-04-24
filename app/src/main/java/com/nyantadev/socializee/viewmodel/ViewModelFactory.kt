package com.nyantadev.socializee.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nyantadev.socializee.repository.AppRepository

class ViewModelFactory(private val repo: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repo) as T
            modelClass.isAssignableFrom(FeedViewModel::class.java) -> FeedViewModel(repo) as T
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(repo) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
