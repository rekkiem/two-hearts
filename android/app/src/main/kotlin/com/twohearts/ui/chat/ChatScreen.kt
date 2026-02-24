package com.twohearts.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twohearts.BuildConfig
import com.twohearts.data.api.*
import com.twohearts.di.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ChatUiState(
    val messages: List<MessageResponse> = emptyList(),
    val inputText: String    = "",
    val isLoading: Boolean   = true,
    val isConnected: Boolean = false,
    val error: String?       = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: ApiService,
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private var wsJob: Job? = null
    private var wsSession: DefaultClientWebSocketSession? = null

    init { loadMessages(); connectWebSocket() }

    override fun onCleared() {
        wsJob?.cancel()
        super.onCleared()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            when (val r = api.getMessages(conversationId)) {
                is ApiResult.Success -> _state.update { it.copy(messages = r.data, isLoading = false) }
                is ApiResult.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }

    private fun connectWebSocket() {
        wsJob = viewModelScope.launch(Dispatchers.IO) {
            val token = tokenStore.getAccessToken() ?: return@launch
            val wsUrl = "${BuildConfig.WS_BASE_URL}/ws/chat/$conversationId?token=$token"
            try {
                httpClient.webSocket(wsUrl) {
                    wsSession = this
                    _state.update { it.copy(isConnected = true) }
                    for (frame in incoming) {
                        if (frame is Frame.Text) processFrame(frame.readText())
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
            } catch (e: Exception) {
                _state.update { it.copy(error = "Connection lost. Messages still send via API.") }
            } finally {
                wsSession = null
                _state.update { it.copy(isConnected = false) }
            }
        }
    }

    private fun processFrame(raw: String) {
        try {
            val msg = json.decodeFromString<WsOutgoing>(raw)
            when (msg.type) {
                "message" -> {
                    val newMsg = MessageResponse(
                        id = msg.messageId ?: return,
                        conversationId = conversationId,
                        senderId = msg.senderId ?: return,
                        content = msg.content ?: return,
                        sentAt = msg.sentAt ?: "",
                        readAt = null
                    )
                    _state.update { s ->
                        // Avoid duplicates (may arrive from WS + REST fallback)
                        if (s.messages.any { it.id == newMsg.id }) s
                        else s.copy(messages = s.messages + newMsg)
                    }
                }
                "error" -> _state.update { it.copy(error = msg.error) }
                else -> {}
            }
        } catch (_: Exception) {}
    }

    fun onInputChanged(text: String) = _state.update { it.copy(inputText = text) }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) return
        _state.update { it.copy(inputText = "") }

        viewModelScope.launch(Dispatchers.IO) {
            // Try WebSocket first
            val ws = wsSession
            if (ws != null) {
                ws.send(json.encodeToString(WsIncoming(type = "message", content = text)))
                // Optimistic UI update
                val tempMsg = MessageResponse(
                    id = java.util.UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    senderId = "me",
                    content = text,
                    sentAt = java.time.OffsetDateTime.now().toString()
                )
                _state.update { s -> s.copy(messages = s.messages + tempMsg) }
            } else {
                // Fallback to REST
                when (val r = api.sendMessage(conversationId, text)) {
                    is ApiResult.Success -> _state.update { s -> s.copy(messages = s.messages + r.data) }
                    is ApiResult.Error   -> _state.update { it.copy(error = r.message) }
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) { wsSession?.close() }
    }
}

// ---- Screen ----

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    recipientName: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(recipientName, fontWeight = FontWeight.SemiBold)
                        if (state.recipientTyping)
                            Text("typing…", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        else if (!state.isConnected)
                            Text("connecting…", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(8.dp).navigationBarsPadding().imePadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value         = state.inputText,
                        onValueChange = viewModel::onInputChanged,
                        modifier      = Modifier.weight(1f),
                        placeholder   = { Text("Message…") },
                        shape         = RoundedCornerShape(24.dp),
                        maxLines      = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick  = viewModel::sendMessage,
                        enabled  = state.inputText.isNotBlank()
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state           = listState,
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentPadding  = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(msg, isMe = msg.senderId == "me" || msg.senderId == "self")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: com.twohearts.data.api.MessageResponse, isMe: Boolean) {
    val align = if (isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(msg.content, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = textColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
        if (text.isBlank()) return
        _state.update { it.copy(inputText = "") }

        viewModelScope.launch {
            // Try WebSocket first, fall back to REST
            val ws = wsSession
            if (ws != null && _state.value.isConnected) {
                try {
                    ws.send(json.encodeToString(WsIncoming(type = "message", content = text)))
                    return@launch
                } catch (_: Exception) {}
            }
            // REST fallback
            when (val r = api.sendMessage(conversationId, text)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(messages = s.messages + r.data)
                }
                is ApiResult.Error -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

// ---- Compose Screen ----

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twohearts.data.api.MessageResponse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    recipientName: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state    = viewModel.state.collectAsState().value
    val listState = rememberLazyListState()
    val scope    = rememberCoroutineScope()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(recipientName, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (state.isConnected) "● Connected" else "○ Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isConnected)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            ChatInput(
                text      = state.inputText,
                onChange  = viewModel::onInputChanged,
                onSend    = viewModel::sendMessage,
                enabled   = true
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state          = listState,
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Say hi! This is the beginning of your conversation 💬",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
                items(state.messages, key = { it.id }) { msg ->
                    val myUserId = "" // compare to actual userId from TokenStore if needed
                    Bubble(msg)
                }
            }
        }

        state.error?.let { err ->
            LaunchedEffect(err) {
                // Auto-clear after 3s
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action   = { TextButton(onClick = viewModel::clearError) { Text("Dismiss") } }
            ) { Text(err) }
        }
    }
}

@Composable
private fun Bubble(msg: MessageResponse) {
    // NOTE: In a real app, compare msg.senderId to the local user's id from TokenStore
    // For MVP, we show all messages as received (grey) — implement sender detection with injection
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                Text(
                    msg.sentAt.take(16).replace("T", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ChatInput(text: String, onChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value         = text,
                onValueChange = onChange,
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Message...") },
                shape         = RoundedCornerShape(24.dp),
                maxLines      = 5
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick  = onSend,
                enabled  = text.isNotBlank() && enabled
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}
