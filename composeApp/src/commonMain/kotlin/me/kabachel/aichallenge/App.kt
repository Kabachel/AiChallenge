package me.kabachel.aichallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val chatApi: ChatApi = remember { ChatGptApi() }
        val viewModel: ChatViewModel = viewModel()
        val chatMessages = viewModel.chatMessages
        val coroutineScope = rememberCoroutineScope()
        var userInput by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                singleLine = true,
                modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyUp &&
                        keyEvent.key == androidx.compose.ui.input.key.Key.Enter
                    ) {
                        coroutineScope.launch {
                            val userMessage = ChatMessage("user", userInput)
                            chatMessages.add(userMessage)
                            val request = ChatRequest(
                                model = "gpt://b1gppgv3fk1p5vm1kq4f/qwen3-235b-a22b-fp8/latest",
                                messages = listOf(
                                    ChatMessage("system", ""),
                                    userMessage
                                )
                            )
                            val responseContent = chatApi.sendChatRequest(
                                BuildConfig.OPENAI_API_KEY,
                                request
                            ).choices?.firstOrNull()?.message?.content.orEmpty()
                            val botMessage = ChatMessage("assistant", responseContent)
                            chatMessages.add(botMessage)
                            userInput = ""
                        }
                        true
                    } else {
                        false
                    }
                }
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(chatMessages) { message ->
                    val prefix = if (message.role == "user") "Вы" else "Бот"
                    Text("$prefix: ${message.content}")
                }
            }
            Button(onClick = {
                coroutineScope.launch {
                    val userMessage = ChatMessage("user", userInput)
                    chatMessages.add(userMessage)
                    val request = ChatRequest(
                        model = "gpt://b1gppgv3fk1p5vm1kq4f/qwen3-235b-a22b-fp8/latest",
                        messages = listOf(
                            ChatMessage("system", ""),
                            userMessage
                        )
                    )
                    val responseContent = chatApi.sendChatRequest(
                        BuildConfig.OPENAI_API_KEY,
                        request
                    ).choices?.firstOrNull()?.message?.content.orEmpty()
                    val botMessage = ChatMessage("assistant", responseContent)
                    chatMessages.add(botMessage)
                    userInput = ""
                }
            }) {
                Text("Отправить")
            }
        }
    }
}
