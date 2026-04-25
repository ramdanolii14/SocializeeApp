package com.nyantadev.socializee.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyantadev.socializee.models.Notification
import com.nyantadev.socializee.repository.AppRepository
import kotlinx.coroutines.launch

sealed class NotificationState {
    object Loading : NotificationState()
    data class Success(val notifications: List<Notification>, val unreadCount: Int) : NotificationState()
    data class Error(val message: String) : NotificationState()
}

class NotificationViewModel(private val repo: AppRepository) : ViewModel() {

    private val _state       = MutableLiveData<NotificationState>()
    val state: LiveData<NotificationState> = _state

    private val _unreadCount = MutableLiveData(0)
    val unreadCount: LiveData<Int> = _unreadCount

    fun loadNotifications() {
        _state.value = NotificationState.Loading
        viewModelScope.launch {
            try {
                val response = repo.getNotifications()
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    _state.value       = NotificationState.Success(body.notifications, body.unreadCount)
                    _unreadCount.value = body.unreadCount
                } else {
                    _state.value = NotificationState.Error("Gagal memuat notifikasi")
                }
            } catch (e: Exception) {
                _state.value = NotificationState.Error(e.message ?: "Terjadi kesalahan")
            }
        }
    }

    fun fetchUnreadCount() {
        viewModelScope.launch {
            try {
                val response = repo.getUnreadCount()
                if (response.isSuccessful && response.body()?.success == true) {
                    _unreadCount.value = response.body()!!.count
                }
            } catch (e: Exception) {
                // Abaikan error untuk polling badge
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                repo.markAllRead()
                _unreadCount.value = 0
                // Refresh list supaya tampilan is_read terupdate
                loadNotifications()
            } catch (e: Exception) {
                // Abaikan
            }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            try {
                repo.markRead(id)
                val current = _unreadCount.value ?: 0
                if (current > 0) _unreadCount.value = current - 1
            } catch (e: Exception) {
                // Abaikan
            }
        }
    }
}