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
    val email: String         = "",
    val tokenInput: String    = "",
    val isLoading: Boolean    = false,
    val step: LoginStep       = LoginStep.EMAIL,
    val error: String?        = null,
    val success: Boolean      = false
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

    // Auto-verify if a token was passed from deep link
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

// ---- Composable ----

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    initialToken: String? = null,
    onLoginSuccess: () -> Unit,
    onNeedProfile: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Auto-verify from deep link
    LaunchedEffect(initialToken) {
        if (!initialToken.isNullOrBlank()) viewModel.autoVerify(initialToken)
    }

    // Navigate on success
    LaunchedEffect(state.success) {
        if (state.success) onLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💕", fontSize = 64.sp)
        Spacer(Modifier.height(8.dp))
        Text("TwoHearts", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Meaningful connections", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(48.dp))

        if (state.step == LoginStep.EMAIL) {
            OutlinedTextField(
                value         = state.email,
                onValueChange = viewModel::onEmailChanged,
                label         = { Text("Email address") },
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine    = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = viewModel::sendMagicLink,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = !state.isLoading
            ) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Send Magic Link")
            }
        } else {
            Text("Check your email (or Mailhog at localhost:8025) and paste the token below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = state.tokenInput,
                onValueChange = viewModel::onTokenInputChanged,
                label         = { Text("Paste token from email") },
                modifier      = Modifier.fillMaxWidth(),
                maxLines      = 3
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = viewModel::verifyToken,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = !state.isLoading
            ) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Sign In")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.onEmailChanged(""); viewModel.onTokenInputChanged("") }) {
                Text("← Back")
            }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
