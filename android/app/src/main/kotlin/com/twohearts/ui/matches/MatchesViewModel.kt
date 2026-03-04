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
    fun clearError() = _state.update { it.copy(error = null) }

    fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val questionResult = api.getTodayQuestion()
            val intentResult   = api.getTodayIntent()
            val matchesResult  = api.getMatches()
            _state.update { s -> s.copy(
                isLoading     = false,
                todayQuestion = (questionResult as? ApiResult.Success)?.data,
                todayIntent   = (intentResult as? ApiResult.Success)?.data,
                matches       = (matchesResult as? ApiResult.Success)?.data ?: emptyList(),
                error         = (matchesResult as? ApiResult.Error)?.message
            )}
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
                    loadAll()
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
                is ApiResult.Success -> _state.update { s -> s.copy(matches = s.matches.map { m ->
                    if (m.matchId == matchId) m.copy(
                        status = r.data.status,
                        conversationId = r.data.conversationId ?: m.conversationId
                    ) else m
                })}
                is ApiResult.Error -> _state.update { it.copy(error = r.message) }
            }
        }
    }
}
