package com.twohearts.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

val GENDER_OPTIONS  = listOf("woman","man","non-binary","agender","genderfluid","prefer_not_to_say")
val INTENT_OPTIONS  = listOf("long_term","casual_dating","friendship","open_to_anything","marriage")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileScreen(
    onProfileSaved: () -> Unit,
    viewModel: CreateProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onProfileSaved() }

    Scaffold(topBar = { TopAppBar(title = { Text("Create Your Profile 💕") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = { viewModel.update { copy(displayName = it) } },
                label = { Text("Display name *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = state.birthDate,
                onValueChange = { viewModel.update { copy(birthDate = it) } },
                label = { Text("Birth date (YYYY-MM-DD) *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            ProfileDropdown(
                label = "Gender identity *",
                selected = state.gender,
                options = GENDER_OPTIONS,
                onSelected = { viewModel.update { copy(gender = it) } }
            )
            OutlinedTextField(
                value = state.bio,
                onValueChange = { viewModel.update { copy(bio = it) } },
                label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 5
            )
            OutlinedTextField(
                value = state.occupation,
                onValueChange = { viewModel.update { copy(occupation = it) } },
                label = { Text("Occupation") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = state.city,
                onValueChange = { viewModel.update { copy(city = it) } },
                label = { Text("City") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            ProfileDropdown(
                label = "Looking for",
                selected = state.intent,
                options = INTENT_OPTIONS,
                onSelected = { viewModel.update { copy(intent = it) } }
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::saveProfile,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Save Profile & Find Matches")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.replace("_", " "), onValueChange = {},
            label = { Text(label) }, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.replace("_", " ")) },
                    onClick = { onSelected(opt); expanded = false }
                )
            }
        }
    }
}
