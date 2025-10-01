package me.kabachel.aichallenge

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val chatApi = ChatGptApi()
    val chatMessages = mutableStateListOf<ChatMessage>()

    fun sendMessage(userInput: String, apiKey: String) {
        viewModelScope.launch {
            val userMessage = ChatMessage("user", userInput)
            chatMessages.add(userMessage)

            val request = ChatRequest(
                model = "gpt://b1gppgv3fk1p5vm1kq4f/qwen3-235b-a22b-fp8/latest",
                messages = listOf(ChatMessage("system", ""), userMessage)
            )

            val responseContent = chatApi.sendChatRequest(apiKey, request)
                .choices?.firstOrNull()?.message?.content.orEmpty()

            chatMessages.add(ChatMessage("assistant", responseContent))
        }
    }
}