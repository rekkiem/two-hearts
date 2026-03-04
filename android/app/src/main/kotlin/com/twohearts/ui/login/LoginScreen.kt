package com.twohearts.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

    LaunchedEffect(initialToken) {
        if (!initialToken.isNullOrBlank()) viewModel.autoVerify(initialToken)
    }
    LaunchedEffect(state.success) {
        if (state.success) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💕", fontSize = 64.sp)
        Spacer(Modifier.height(8.dp))
        Text("TwoHearts",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Meaningful connections",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(48.dp))

        when (state.step) {
            LoginStep.EMAIL -> EmailStep(
                email = state.email,
                isLoading = state.isLoading,
                onEmailChanged = viewModel::onEmailChanged,
                onSend = viewModel::sendMagicLink
            )
            LoginStep.TOKEN -> TokenStep(
                tokenInput = state.tokenInput,
                isLoading = state.isLoading,
                onTokenChanged = viewModel::onTokenInputChanged,
                onVerify = viewModel::verifyToken,
                onBack = viewModel::goBack
            )
        }

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmailStep(
    email: String, isLoading: Boolean,
    onEmailChanged: (String) -> Unit, onSend: () -> Unit
) {
    OutlinedTextField(
        value = email, onValueChange = onEmailChanged,
        label = { Text("Email address") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSend,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = !isLoading
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else Text("Send Magic Link")
    }
}

@Composable
private fun TokenStep(
    tokenInput: String, isLoading: Boolean,
    onTokenChanged: (String) -> Unit, onVerify: () -> Unit, onBack: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Check your email (or Mailhog at localhost:8025) and paste the token below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = tokenInput, onValueChange = onTokenChanged,
        label = { Text("Paste token from email") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onVerify,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = !isLoading
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else Text("Sign In")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("← Use a different email")
    }
}
