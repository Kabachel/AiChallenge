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
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    println(message)
                }
            }
        }
    }

    override suspend fun getCompletion(message: String): String {
        return try {
            val response: ChatResponse = client.post("https://api.openai.com/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                setBody(
                    ChatRequest(
                        model = "gpt-3.5-turbo",
                        messages = listOf(Message(role = "user", content = message))
                    )
                )
            }.body()
            response.choices.firstOrNull()?.message?.content ?: "Error: No response from API"
        } catch (e: ResponseException) {
            val statusCode = e.response.status.value
            when (statusCode) {
                401 -> "Error: Unauthorized. Please check your API key."
                429 -> "Error: Rate limit exceeded. Please try again later."
                in 500..599 -> "Error: Server failed with status $statusCode."
                else -> "Error: Request failed with status $statusCode."
            }
        } catch (e: Exception) {
            "Error: An unexpected error occurred: ${e.message}"
        }
    }
}
