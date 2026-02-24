package com.twohearts.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twohearts.data.api.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchesUiState(
    val todayQuestion: IntentQuestionResponse? = null,
    val todayIntent: IntentResponse?           = null,
    val intentAnswer: String                   = "",
    val matches: List<MatchResponse>           = emptyList(),
    val isLoading: Boolean                     = true,
    val isSubmittingIntent: Boolean            = false,
    val error: String?                         = null
)

@HiltViewModel
class MatchesViewModel @Inject constructor(private val api: ApiService) : ViewModel() {

    private val _state = MutableStateFlow(MatchesUiState())
    val state: StateFlow<MatchesUiState> = _state.asStateFlow()

    init { loadAll() }

    fun onIntentAnswerChanged(v: String) = _state.update { it.copy(intentAnswer = v) }

    fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // Load question + today's intent in parallel
            val questionResult = api.getTodayQuestion()
            val intentResult   = api.getTodayIntent()
            val matchesResult  = api.getMatches()

            _state.update { s ->
                s.copy(
                    isLoading     = false,
                    todayQuestion = (questionResult as? ApiResult.Success)?.data,
                    todayIntent   = (intentResult as? ApiResult.Success)?.data,
                    matches       = (matchesResult as? ApiResult.Success)?.data ?: emptyList(),
                    error         = (matchesResult as? ApiResult.Error)?.message
                )
            }
        }
    }

    fun submitIntent() {
        val qId    = _state.value.todayQuestion?.id ?: return
        val answer = _state.value.intentAnswer.trim()
        if (answer.length < 10) { _state.update { it.copy(error = "Answer too short (min 10 chars)") }; return }

        viewModelScope.launch {
            _state.update { it.copy(isSubmittingIntent = true, error = null) }
            when (val r = api.submitIntent(qId, answer)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isSubmittingIntent = false, todayIntent = r.data) }
                    loadAll()  // Refresh matches after intent submission
                }
                is ApiResult.Error -> _state.update { it.copy(isSubmittingIntent = false, error = r.message) }
            }
        }
    }

    fun like(matchId: String) = interact(matchId, "like")
    fun pass(matchId: String) = interact(matchId, "pass")

    private fun interact(matchId: String, action: String) {
        viewModelScope.launch {
            when (val r = api.interactWithMatch(matchId, action)) {
                is ApiResult.Success -> {
                    _state.update { s ->
                        s.copy(matches = s.matches.map { m ->
                            if (m.matchId == matchId) m.copy(
                                status         = r.data.status,
                                conversationId = r.data.conversationId ?: m.conversationId
                            ) else m
                        })
                    }
                }
                is ApiResult.Error -> _state.update { it.copy(error = r.message) }
            }
        }
    }
}

// ---- Screen ----

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    onOpenChat: (conversationId: String, recipientName: String) -> Unit,
    viewModel: MatchesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text("TwoHearts 💕") },
                actions = {
                    IconButton(onClick = viewModel::loadAll) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding),
            contentPadding  = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Daily Intent Card
            if (state.todayIntent == null) {
                item {
                    DailyIntentCard(
                        question       = state.todayQuestion?.text ?: "What's on your mind today?",
                        answer         = state.intentAnswer,
                        onAnswerChange = viewModel::onIntentAnswerChanged,
                        onSubmit       = viewModel::submitIntent,
                        isLoading      = state.isSubmittingIntent
                    )
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓  ", fontWeight = FontWeight.Bold)
                            Text("Today's reflection submitted. Matches updated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            // Section header
            item {
                Text("Today's Matches", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (state.matches.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("No matches yet. Submit today's reflection to find connections.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            items(state.matches, key = { it.matchId }) { match ->
                MatchCard(
                    match      = match,
                    onLike     = { viewModel.like(match.matchId) },
                    onPass     = { viewModel.pass(match.matchId) },
                    onOpenChat = { onOpenChat(match.conversationId!!, match.displayName) }
                )
            }

            state.error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun DailyIntentCard(
    question: String, answer: String,
    onAnswerChange: (String) -> Unit, onSubmit: () -> Unit, isLoading: Boolean
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Today's Reflection", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(question, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value         = answer,
                onValueChange = { if (it.length <= 500) onAnswerChange(it) },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Share your thoughts...") },
                minLines      = 3, maxLines = 6,
                supportingText = { Text("${answer.length}/500") }
            )
            Button(
                onClick  = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled  = answer.length >= 10 && !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Submit & Find Matches")
            }
        }
    }
}

@Composable
private fun MatchCard(
    match: MatchResponse,
    onLike: () -> Unit, onPass: () -> Unit, onOpenChat: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column {
            // Photo
            if (!match.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = match.photoUrl, contentDescription = "Photo",
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    ),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(120.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text("💕", style = MaterialTheme.typography.displaySmall) }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${match.displayName}, ${match.age}", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${(match.score * 100).toInt()}%",
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                match.city?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }

                // Compatibility explainer
                if (match.explainer.isNotEmpty()) {
                    Text("Why you might connect:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    match.explainer.forEach { point ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", color = MaterialTheme.colorScheme.primary)
                            Text(point, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                when (match.status) {
                    "mutual" -> Button(onClick = onOpenChat, Modifier.fillMaxWidth()) {
                        Text("Open Conversation 💬")
                    }
                    "pending", "user_b_liked" -> Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onPass, Modifier.weight(1f)) { Text("Not now") }
                        Button(onClick = onLike, Modifier.weight(1f)) { Text("I'm interested ✨") }
                    }
                    "user_a_liked" -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("Waiting for their response…",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun Modifier.background(color: androidx.compose.ui.graphics.Color) =
    this.then(Modifier)  // placeholder — use actual Compose background modifier
