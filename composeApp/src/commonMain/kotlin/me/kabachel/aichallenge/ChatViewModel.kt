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

    val models = mapOf(
        "Qwen3 235B A22B Instruct 2507 FP8" to "gpt://b1gppgv3fk1p5vm1kq4f/qwen3-235b-a22b-fp8/latest",
        "GPT OSS 120B" to "gpt://b1gppgv3fk1p5vm1kq4f/gpt-oss-120b/latest",
        "YandexGPT 5.1 Pro" to "gpt://b1gppgv3fk1p5vm1kq4f/yandexgpt/rc"
    )

    val chatMessages = mutableStateListOf<ChatUiMessage>()
    var temperature by mutableStateOf(0.7)
    var selectedModel by mutableStateOf(models.values.first())

    private var interviewActive = false

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
            val request = ChatRequest(
                model = selectedModel,
                messages = buildList {
                    val systemPrompt = buildString {
                        appendLine("Ты — интеллектуальный ассистент, который может вести разные типы диалогов.")
                        appendLine()
                        appendLine("🔹 Если пользователь попросит провести собеседование (например: ‘проведи собеседование’, ‘interview’, ‘хочу пройти интервью’), действуй как опытный технический интервьюер и следуй этому плану:")
                        appendLine("1. Если текущее собеседование ещё не началось — начни с вопроса, на какую должность пользователь хочет пройти собеседование (предложи варианты: Frontend, Backend, Mobile, Data Science, DevOps).")
                        appendLine("2. Если пользователь уже выбрал направление и подтвердил готовность (например, ответил ‘да’, ‘готов’, ‘поехали’), не начинай заново и не повторяй вводные вопросы — просто переходи к первому техническому вопросу.")
                        appendLine("3. Задавай 3–5 вопросов разной сложности по выбранной теме (от простого к сложному). После каждого ответа пользователя анализируй ответ, кратко оцени его и переходи к следующему вопросу.")
                        appendLine("4. Не заверши интервью, пока не задал все вопросы. После последнего вопроса и ответа пользователя выдай итоговую оценку.")
                        appendLine("5. После того как пользователь ответил на все вопросы, оцени уровень кандидата (Junior, Middle, Senior), предложи примерную зарплату и дай совет.")
                        appendLine("6. Заверши фразой ‘Интервью завершено’. Все ответы и результат всегда возвращай в одном формате JSON:")
                        appendLine("{" )
                        appendLine("  \"type\": \"String\",        // тема разговора или тип (например: chat, interview, feedback, question, travel, code)")
                        appendLine("  \"content\": \"String\",     // основной текст ответа (включая вопросы и результаты интервью)")
                        appendLine("  \"language\": \"String\",    // язык ответа")
                        appendLine("  \"confidence\": Number       // от 0 до 1 уверенность")
                        appendLine("}")
                        appendLine("Никогда не используй несколько JSON-объектов, только один JSON с ключом content, даже если это интервью.")
                        appendLine()
                        appendLine("🔹 Если пользователь скажет что-то вроде ‘останови собеседование’, ‘хватит’, ‘прекрати’ или попросит завершить интервью — немедленно заверши процесс, выдай текущий результат в формате JSON и добавь комментарий, что интервью было прервано по запросу пользователя.")
                        appendLine()
                        if (interviewActive) {
                            appendLine("⚙️ Интервью уже в процессе. Не повторяй вводные вопросы, сразу продолжай с техническими вопросами.")
                            appendLine("После каждого ответа пользователя задай следующий вопрос, пока не задашь все 3–5.")
                        }
                    }
                    add(ChatMessage("system", systemPrompt))
                    chatMessages.forEach { add(ChatMessage(it.role, it.content)) }
                    add(ChatMessage("user", userInput))
                },
                temperature = temperature
            )
            val response = api.sendChatRequest(apiKey, request)
            val assistantText = response.choices?.firstOrNull()?.message?.content.orEmpty()
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
                        confidence = payload.confidence
                    )
                )
            } catch (_: Exception) {
                chatMessages.add(ChatUiMessage("assistant", type = "assistant", content = assistantText))
            }
        }
    }
}