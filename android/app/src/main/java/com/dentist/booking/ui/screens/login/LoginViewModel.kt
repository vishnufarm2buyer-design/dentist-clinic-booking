package com.dentist.booking.ui.screens.login

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentist.booking.DentistApp
import com.dentist.booking.data.model.User
import com.dentist.booking.data.repository.AuthRepository
import com.dentist.booking.data.repository.DeviceTokenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _loginSuccessUser = MutableStateFlow<User?>(null)
    val loginSuccessUser: StateFlow<User?> = _loginSuccessUser.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    fun onPhoneChanged(value: String) {
        _phone.value = value
    }

    fun onPasswordChanged(value: String) {
        _password.value = value
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun getServerConfig(): Pair<String, String> {
        val url = sharedPreferences.getString("SUPABASE_URL", "https://your-project.supabase.co/") ?: "https://your-project.supabase.co/"
        val key = sharedPreferences.getString("SUPABASE_ANON_KEY", "your-anon-public-key") ?: "your-anon-public-key"
        return Pair(url, key)
    }

    fun saveServerConfig(url: String, anonKey: String) {
        DentistApp.instance.updateSupabaseConfig(url, anonKey)
    }

    fun login() {
        if (_phone.value.trim().isEmpty() || _password.value.isEmpty()) {
            _errorMessage.value = "Phone and password cannot be empty"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Perform custom authentication
                val user = authRepository.login(_phone.value.trim(), _password.value)
                
                // Fetch cached FCM push token and register it for the user
                val token = sharedPreferences.getString("FCM_TOKEN", null)
                if (token != null && user.id != null) {
                    try {
                        deviceTokenRepository.registerToken(user.id, token)
                    } catch (e: Exception) {
                        // Silent catch: FCM failure shouldn't lock out standard login
                    }
                }
                
                _loginSuccessUser.value = user
            } catch (e: Exception) {
                val message = e.message ?: "Authentication failed. Check credentials or network connection."
                _errorMessage.value = if (message.contains("28000")) "Invalid phone number or password" else message
            } finally {
                _isLoading.value = false
            }
        }
    }
}
