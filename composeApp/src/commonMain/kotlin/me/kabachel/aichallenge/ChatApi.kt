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
        val modifiedMessages = buildList {
            add(
                ChatMessage(
                    role = "system",
                    content = """
                        Ты всегда отвечаешь только в формате JSON.
                        Структура:
                        {
                          "type": "String",        // тема разговора (например: chat, feedback, question, travel, code)
                          "content": "String",     // основной текст ответа
                          "language": "String",    // язык ответа
                          "timestamp": "String",   // ISO8601 время генерации
                          "confidence": Number     // от 0 до 1 уверенность
                        }
                        Не используй текст вне JSON. Всегда пиши только один JSON-объект.
                    """.trimIndent()
                )
            )
            addAll(request.messages)
        }
        val modifiedRequest = request.copy(messages = modifiedMessages)

        return client.post("https://llm.api.cloud.yandex.net/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(modifiedRequest)
        }.body()
    }
}
