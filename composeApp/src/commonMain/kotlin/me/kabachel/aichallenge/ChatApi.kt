package me.kabachel.aichallenge

import io.ktor.client.* 
import io.ktor.client.call.* 
import io.ktor.client.plugins.ResponseException
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
    val choices: List<Choice>? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage
)

class ChatGptApi : ChatApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    println(message)
                }
            }
        }
    }

    override suspend fun sendChatRequest(apiKey: String, request: ChatRequest): ChatResponse {
        return client.post("https://llm.api.cloud.yandex.net/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
