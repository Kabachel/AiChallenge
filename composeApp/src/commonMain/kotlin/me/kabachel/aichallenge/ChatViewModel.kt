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
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val modelName: String? = null
)

class ChatViewModel(
    private val api: ChatApi,
    private val apiKey: String
) : ViewModel() {

    private val promptBuilder = PromptBuilder()
    private val maxInputLength = Constants.MAX_TOKENS * Constants.APPROX_CHARS_PER_TOKEN

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
        chatMessages.add(ChatUiMessage(role = "user", type = "user", content = userInput, modelName = "User"))

        viewModelScope.launch {
            var processedInput = userInput

            // Summarization flow
            if (userInput.length > maxInputLength) {
                chatMessages.add(ChatUiMessage(role = "assistant", type = "system", content = "Текст слишком длинный, сокращаю...", modelName = selectedModel.name))
                val summarizerRequest = ChatRequest(
                    model = selectedModel.url,
                    messages = listOf(
                        ChatMessage("system", promptBuilder.buildSummarizerPrompt()),
                        ChatMessage("user", userInput)
                    ),
                    temperature = 0.0 // Use low temperature for summarization
                )
                val summarizerResponse = api.sendChatRequest(apiKey, summarizerRequest)
                processedInput = summarizerResponse.choices?.firstOrNull()?.message?.content.orEmpty()
                chatMessages.add(ChatUiMessage(role = "assistant", type = "summary", content = "Сокращенный текст:\n$processedInput", modelName = selectedModel.name))
            }

            // Story generation flow
            if (processedInput.lowercase().startsWith("напиши рассказ о")) {
                handleStoryGeneration(processedInput)
            } else { // Regular chat/interview flow
                handleRegularChat(processedInput)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun handleStoryGeneration(userInput: String) {
        val plannerModel = models.find { it.name.startsWith("Qwen3") } ?: selectedModel
        val writerModel = models.find { it.name.startsWith("GPT OSS") } ?: selectedModel

        // Agent 1: Planner
        chatMessages.add(ChatUiMessage(role = "assistant", type = "system", content = "Придумываю план для рассказа...", modelName = plannerModel.name))

        val plannerRequest = ChatRequest(
            model = plannerModel.url,
            messages = listOf(
                ChatMessage("system", promptBuilder.buildPlannerPrompt()),
                ChatMessage("user", userInput)
            ),
            temperature = temperature
        )

        val plannerResponse = api.sendChatRequest(apiKey, plannerRequest)
        val planJson = plannerResponse.choices?.firstOrNull()?.message?.content.orEmpty()

        try {
            val storyPlan = Json { ignoreUnknownKeys = true }.decodeFromString<StoryPlan>(planJson)

            chatMessages.add(
                ChatUiMessage(
                    role = "assistant",
                    type = "story_plan",
                    content = "План: ${storyPlan.title}\n" + storyPlan.plotPoints.joinToString("\n") { "- $it" },
                    modelName = plannerModel.name
                )
            )

            // Agent 2: Writer
            chatMessages.add(ChatUiMessage(role = "assistant", type = "system", content = "Пишу рассказ по плану...", modelName = writerModel.name))

            val writerRequest = ChatRequest(
                model = writerModel.url,
                messages = listOf(
                    ChatMessage("system", promptBuilder.buildWriterPrompt()),
                    ChatMessage("user", planJson) // Pass the JSON plan to the writer
                ),
                temperature = temperature
            )

            var storyResponse: ChatResponse? = null
            val responseTime = measureTime {
                storyResponse = api.sendChatRequest(apiKey, writerRequest)
            }.inWholeMilliseconds

            val storyText = storyResponse?.choices?.firstOrNull()?.message?.content.orEmpty()
            val now = Clock.System.now()
            val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val formattedTimestamp = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"

            chatMessages.add(ChatUiMessage(
                role = "assistant",
                type = "story",
                content = storyText,
                timestamp = formattedTimestamp,
                responseTime = responseTime,
                promptTokens = storyResponse?.usage?.promptTokens,
                completionTokens = storyResponse?.usage?.completionTokens,
                totalTokens = storyResponse?.usage?.totalTokens,
                modelName = writerModel.name
            ))

        } catch (e: Exception) {
            chatMessages.add(ChatUiMessage(role = "assistant", type = "error", content = "Не удалось обработать план рассказа: ${e.message} \nПлан: $planJson", modelName = plannerModel.name))
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun handleRegularChat(userInput: String) {
        val normalizedInput = userInput.lowercase()
        if (listOf("да", "готов", "поехали", "начинай").any { it == normalizedInput }) {
            interviewActive = true
        } else if (listOf("останови", "заверши", "прекрати", "хватит").any { normalizedInput.contains(it) }) {
            interviewActive = false
        }

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
                    promptTokens = response?.usage?.promptTokens,
                    completionTokens = response?.usage?.completionTokens,
                    totalTokens = response?.usage?.totalTokens,
                    modelName = selectedModel.name
                )
            )
        } catch (_: Exception) {
            chatMessages.add(ChatUiMessage(
                role = "assistant",
                type = "assistant",
                content = assistantText,
                responseTime = responseTime,
                promptTokens = response?.usage?.promptTokens,
                completionTokens = response?.usage?.completionTokens,
                totalTokens = response?.usage?.totalTokens,
                modelName = selectedModel.name
            ))
        }
    }
}
