package me.kabachel.aichallenge

import io.ktor.client.* 
import io.ktor.client.call.* 
import io.ktor.client.plugins.HttpTimeout 
import io.ktor.client.plugins.contentnegotiation.* 
import io.ktor.client.plugins.logging.* 
import io.ktor.client.request.* 
import io.ktor.http.* 
import io.ktor.serialization.kotlinx.json.* 
import kotlinx.serialization.SerialName
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
    val messages: List<ChatMessage>,
    val temperature: Double? = null
)

@Serializable
data class Usage(
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
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

@Serializable
data class StoryPlan(
    val type: String,
    val title: String,
    @SerialName("plot_points") val plotPoints: List<String>
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
        return client.post("https://llm.api.cloud.yandex.net/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
            parameter("max_tokens", 800)
        }.body()
    }
}
