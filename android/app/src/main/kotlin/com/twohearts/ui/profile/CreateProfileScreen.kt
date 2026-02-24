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

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val req = CreateProfileRequest(
                displayName      = s.displayName.trim(),
                birthDate        = s.birthDate,
                genderIdentity   = s.gender,
                bio              = s.bio.ifBlank { null },
                occupation       = s.occupation.ifBlank { null },
                relationshipIntent = s.intent,
                city             = s.city.ifBlank { null }
            )
            when (val r = api.createProfile(req)) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, saved = true) }
                is ApiResult.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }
}

// ---- Screen ----

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileScreen(
    onProfileSaved: () -> Unit,
    viewModel: CreateProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) { if (state.saved) onProfileSaved() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Create Your Profile") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value         = state.displayName,
                onValueChange = { viewModel.update { copy(displayName = it) } },
                label         = { Text("Display name *") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )
            OutlinedTextField(
                value         = state.birthDate,
                onValueChange = { viewModel.update { copy(birthDate = it) } },
                label         = { Text("Birth date (YYYY-MM-DD) *") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )
            // Gender dropdown
            var genderExpanded by remember { mutableStateOf(false) }
            val genders = listOf("woman","man","non-binary","agender","genderfluid","prefer_not_to_say")
            ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = it }) {
                OutlinedTextField(
                    value         = state.gender,
                    onValueChange = {},
                    label         = { Text("Gender identity *") },
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                    modifier      = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                    genders.forEach { g ->
                        DropdownMenuItem(text = { Text(g) }, onClick = {
                            viewModel.update { copy(gender = g) }
                            genderExpanded = false
                        })
                    }
                }
            }
            OutlinedTextField(
                value         = state.bio,
                onValueChange = { viewModel.update { copy(bio = it) } },
                label         = { Text("Bio (what makes you, you?)") },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 3, maxLines = 5
            )
            OutlinedTextField(
                value         = state.occupation,
                onValueChange = { viewModel.update { copy(occupation = it) } },
                label         = { Text("Occupation") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )
            OutlinedTextField(
                value         = state.city,
                onValueChange = { viewModel.update { copy(city = it) } },
                label         = { Text("City") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )
            // Relationship intent
            var intentExpanded by remember { mutableStateOf(false) }
            val intents = listOf("long_term","casual_dating","friendship","open_to_anything","marriage")
            ExposedDropdownMenuBox(expanded = intentExpanded, onExpandedChange = { intentExpanded = it }) {
                OutlinedTextField(
                    value         = state.intent,
                    onValueChange = {},
                    label         = { Text("Looking for") },
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(intentExpanded) },
                    modifier      = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = intentExpanded, onDismissRequest = { intentExpanded = false }) {
                    intents.forEach { i ->
                        DropdownMenuItem(text = { Text(i.replace("_", " ")) }, onClick = {
                            viewModel.update { copy(intent = i) }
                            intentExpanded = false
                        })
                    }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = viewModel::saveProfile,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = !state.isLoading
            ) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Save Profile & Find Matches")
            }
        }
    }
}
