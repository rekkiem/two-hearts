package com.twohearts.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twohearts.data.api.ApiResult
import com.twohearts.data.api.ApiService
import com.twohearts.data.api.CreateProfileRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileFormState(
    val displayName: String = "",
    val birthDate: String   = "1995-01-01",
    val gender: String      = "woman",
    val bio: String         = "",
    val occupation: String  = "",
    val intent: String      = "open_to_anything",
    val city: String        = "",
    val isLoading: Boolean  = false,
    val error: String?      = null,
    val saved: Boolean      = false
)

@HiltViewModel
class CreateProfileViewModel @Inject constructor(private val api: ApiService) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state.asStateFlow()

    fun update(block: ProfileFormState.() -> ProfileFormState) = _state.update(block)

    fun saveProfile() {
        val s = _state.value
        if (s.displayName.isBlank()) { _state.update { it.copy(error = "Name is required") }; return }
        if (s.birthDate.isBlank())   { _state.update { it.copy(error = "Birth date is required") }; return }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val req = CreateProfileRequest(
                displayName       = s.displayName.trim(),
                birthDate         = s.birthDate,
                genderIdentity    = s.gender,
                bio               = s.bio.ifBlank { null },
                occupation        = s.occupation.ifBlank { null },
                relationshipIntent = s.intent,
                city              = s.city.ifBlank { null }
            )
            when (val r = api.createProfile(req)) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, saved = true) }
                is ApiResult.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }
}
