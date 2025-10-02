package me.kabachel.aichallenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

data class ChatUiMessage(
    val role: String,
    val type: String? = null,
    val content: String,
    val language: String? = null,
    val timestamp: String? = null,
    val confidence: Double? = null
)

class ChatViewModel(
    private val api: ChatApi,
    private val apiKey: String
) : ViewModel() {

    val chatMessages = mutableStateListOf<ChatUiMessage>()

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        chatMessages.add(ChatUiMessage(role = "user", type = "user", content = userInput))

        viewModelScope.launch {
            val request = ChatRequest(
                model = "gpt://b1gppgv3fk1p5vm1kq4f/qwen3-235b-a22b-fp8/latest",
                messages = listOf(ChatMessage("system", ""), ChatMessage("user", userInput))
            )
            val response = api.sendChatRequest(apiKey, request)
            val assistantText = response.choices?.firstOrNull()?.message?.content.orEmpty()

            try {
                val payload = Json { ignoreUnknownKeys = true }
                    .decodeFromString<AgentPayload>(assistantText)
                chatMessages.add(
                    ChatUiMessage(
                        role = "assistant",
                        type = payload.type,
                        content = payload.content,
                        language = payload.language,
                        timestamp = payload.timestamp,
                        confidence = payload.confidence
                    )
                )
            } catch (_: Exception) {
                chatMessages.add(ChatUiMessage("assistant", type = "assistant", content = assistantText))
            }
        }
    }
}