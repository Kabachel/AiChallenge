package me.kabachel.aichallenge

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ChatApi {
    suspend fun getCompletion(message: String): String
}

@Serializable
data class ChatRequest(val model: String, val messages: List<Message>)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class ChatResponse(val choices: List<Choice>)

@Serializable
data class Choice(val message: Message)

class ChatGptApi : ChatApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun getCompletion(message: String): String {
        try {
            val response: ChatResponse = client.post("https://api.openai.com/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer YOUR_API_KEY") // TODO: Replace with your API key
                setBody(
                    ChatRequest(
                        model = "gpt-3.5-turbo",
                        messages = listOf(Message(role = "user", content = message))
                    )
                )
            }.body()
            return response.choices.first().message.content
        } catch (e: Exception) {
            return "Error: ${e.message}"
        }
    }
}
