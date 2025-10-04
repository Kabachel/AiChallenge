package me.kabachel.aichallenge

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ChatApi {
    suspend fun sendChatRequest(apiKey: String, request: ChatRequest): ChatResponse
}

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val type: String? = null,
    val content: String? = null,
    val language: String? = null,
    val timestamp: String? = null,
    val confidence: Double? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage
)

@Serializable
data class AgentPayload(
    val type: String,
    val content: String,
    val language: String? = null,
    val timestamp: String? = null,
    val confidence: Double? = null
)

class ChatGptApi : ChatApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) = println(message)
            }
        }
    }

    override suspend fun sendChatRequest(apiKey: String, request: ChatRequest): ChatResponse {
        // Используем общий system prompt, который включает описание поведения агента в разных сценариях
        val systemPrompt = """
            Ты — интеллектуальный ассистент, который может вести разные типы диалогов.

            🔹 Если пользователь попросит провести собеседование (например: «проведи собеседование», «interview», «хочу пройти интервью»), действуй как опытный технический интервьюер и следуй этому плану:
            1. Если текущее собеседование ещё не началось — начни с вопроса, на какую должность пользователь хочет пройти собеседование (предложи варианты: Frontend, Backend, Mobile, Data Science, DevOps).
            2. Если пользователь уже выбрал направление и подтвердил готовность (например, ответил «да», «готов», «поехали»), не начинай заново и не повторяй вводные вопросы — просто переходи к первому техническому вопросу.
            3. Задавай 3–5 вопросов разной сложности по выбранной теме (от простого к сложному). После каждого ответа пользователя анализируй ответ, кратко оцени его и переходи к следующему вопросу.
            4. Не заверши интервью, пока не задал все вопросы. После последнего вопроса и ответа пользователя выдай итоговую оценку.
            5. После того как пользователь ответил на все вопросы, оцени уровень кандидата (Junior, Middle, Senior), предложи примерную зарплату и дай совет.
            6. Заверши фразой «Интервью завершено». Все ответы и результат всегда возвращай в одном формате JSON:
            {
              "type": "String",        // тема разговора или тип (например: chat, interview, feedback, question, travel, code)
              "content": "String",     // основной текст ответа (включая вопросы и результаты интервью)
              "language": "String",    // язык ответа
              "confidence": Number       // от 0 до 1 уверенность
            }
            Никогда не используй несколько JSON-объектов, только один JSON с ключом content, даже если это интервью.

            🔹 Если пользователь скажет что-то вроде «останови собеседование», «хватит», «прекрати» или попросит завершить интервью — немедленно заверши процесс, выдай текущий результат в формате JSON (как указано выше) и добавь комментарий, что интервью было прервано по запросу пользователя.

            🔹 Если пользователь просто спрашивает что-то, связанное с собеседованиями, но не просит провести интервью — ответь на вопрос и мягко предложи пройти собеседование.

            🔹 Во всех остальных случаях веди себя как обычный помощник и отвечай в JSON-формате:
            {
              "type": "String",        // тема разговора
              "content": "String",     // основной текст ответа
              "language": "String",    // язык ответа
              "confidence": Number      // от 0 до 1 уверенность
            }
            Не используй текст вне JSON. Всегда возвращай только один JSON-объект.
        """.trimIndent()

        val modifiedMessages = buildList {
            add(ChatMessage(role = "system", content = systemPrompt))
            addAll(request.messages)
        }

        val modifiedRequest = request.copy(messages = modifiedMessages)

        return client.post("https://llm.api.cloud.yandex.net/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(modifiedRequest)
            parameter("max_tokens", 800)
        }.body()
    }
}
