package me.kabachel.aichallenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

data class ChatUiMessage(
    val role: String,
    val type: String? = null,
    val content: String,
    val language: String? = null,
    val timestamp: String? = null,
    val confidence: Double? = null,
    val responseTime: Long? = null,
    val totalTokens: Int? = null
)

class ChatViewModel(
    private val api: ChatApi,
    private val apiKey: String
) : ViewModel() {

    private val promptBuilder = PromptBuilder()

    val models = listOf(
        AiModel(
            name = "Qwen3 235B A22B Instruct 2507 FP8",
            url = "gpt://b1gppgv3fk1p5vm1kq4f/qwen3-235b-a22b-fp8/latest",
            temperatures = listOf(0.0, 0.7, 1.2)
        ),
        AiModel(
            name = "GPT OSS 120B",
            url = "gpt://b1gppgv3fk1p5vm1kq4f/gpt-oss-120b/latest",
            temperatures = listOf(0.0, 0.7, 1.2)
        ),
        AiModel(
            name = "YandexGPT 5.1 Pro",
            url = "gpt://b1gppgv3fk1p5vm1kq4f/yandexgpt/rc",
            temperatures = listOf(0.0, 0.5, 1.0)
        )
    )

    val chatMessages = mutableStateListOf<ChatUiMessage>()
    var selectedModel by mutableStateOf(models.first())
    var temperature by mutableStateOf(selectedModel.temperatures[1])
    var chainOfThoughtEnabled by mutableStateOf(false)

    private var interviewActive = false

    fun selectModel(model: AiModel) {
        selectedModel = model
        temperature = model.temperatures[1] // Reset to middle temperature on model change
    }

    @OptIn(ExperimentalTime::class)
    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        chatMessages.add(ChatUiMessage(role = "user", type = "user", content = userInput))

        val normalizedInput = userInput.lowercase()
        if (listOf("да", "готов", "поехали", "начинай").any { it == normalizedInput }) {
            interviewActive = true
        } else if (listOf("останови", "заверши", "прекрати", "хватит").any { normalizedInput.contains(it) }) {
            interviewActive = false
        }

        viewModelScope.launch {
            val systemPrompt = promptBuilder.buildSystemPrompt(chainOfThoughtEnabled, interviewActive)
            val request = ChatRequest(
                model = selectedModel.url,
                messages = buildList {
                    add(ChatMessage("system", systemPrompt))
                    chatMessages.forEach { add(ChatMessage(it.role, it.content)) }
                    add(ChatMessage("user", userInput))
                },
                temperature = temperature
            )
            
            var response: ChatResponse? = null
            val responseTime = measureTime {
                response = api.sendChatRequest(apiKey, request)
            }.inWholeMilliseconds

            val assistantText = response?.choices?.firstOrNull()?.message?.content.orEmpty()
            val now = Clock.System.now()
            val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val formattedTimestamp = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"

            try {
                val payload = Json { ignoreUnknownKeys = true }
                    .decodeFromString<AgentPayload>(assistantText)
                chatMessages.add(
                    ChatUiMessage(
                        role = "assistant",
                        type = payload.type,
                        content = payload.content,
                        language = payload.language,
                        timestamp = formattedTimestamp,
                        confidence = payload.confidence,
                        responseTime = responseTime,
                        totalTokens = response?.usage?.totalTokens
                    )
                )
            } catch (_: Exception) {
                chatMessages.add(ChatUiMessage(
                    role = "assistant", 
                    type = "assistant", 
                    content = assistantText,
                    responseTime = responseTime,
                    totalTokens = response?.usage?.totalTokens
                ))
            }
        }
    }
}