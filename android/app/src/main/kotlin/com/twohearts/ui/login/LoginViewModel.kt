package com.twohearts.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twohearts.data.api.ApiResult
import com.twohearts.data.api.ApiService
import com.twohearts.di.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String       = "",
    val tokenInput: String  = "",
    val isLoading: Boolean  = false,
    val step: LoginStep     = LoginStep.EMAIL,
    val error: String?      = null,
    val success: Boolean    = false
)

enum class LoginStep { EMAIL, TOKEN }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(v: String)      = _uiState.update { it.copy(email = v) }
    fun onTokenInputChanged(v: String) = _uiState.update { it.copy(tokenInput = v) }
    fun clearError()                   = _uiState.update { it.copy(error = null) }
    fun goBack()                       = _uiState.update { LoginUiState() }

    fun sendMagicLink() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) { _uiState.update { it.copy(error = "Enter your email") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = api.requestMagicLink(email)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, step = LoginStep.TOKEN) }
                is ApiResult.Error   -> _uiState.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }

    fun verifyToken() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isBlank()) { _uiState.update { it.copy(error = "Paste your token") }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = api.verifyToken(token)) {
                is ApiResult.Success -> {
                    tokenStore.saveTokens(r.data.accessToken, r.data.refreshToken, r.data.userId)
                    _uiState.update { it.copy(isLoading = false, success = true) }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }

    fun autoVerify(token: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val r = api.verifyToken(token)) {
                is ApiResult.Success -> {
                    tokenStore.saveTokens(r.data.accessToken, r.data.refreshToken, r.data.userId)
                    _uiState.update { it.copy(isLoading = false, success = true) }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }
}
