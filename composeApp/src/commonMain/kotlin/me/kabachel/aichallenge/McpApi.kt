package me.kabachel.aichallenge

import io.ktor.client.* 
import io.ktor.client.call.* 
import io.ktor.client.plugins.contentnegotiation.* 
import io.ktor.client.request.* 
import io.ktor.serialization.kotlinx.json.* 
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 1. Data-класс для инструмента (предполагаем, что сервер вернет name и description)
@Serializable
data class McpTool(
    val name: String,
    val description: String
)

// 2. Интерфейс для API
interface McpApi {
    suspend fun getTools(): List<McpTool>
}

// 3. Реализация интерфейса с помощью Ktor
class McpApiImpl : McpApi {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    override suspend fun getTools(): List<McpTool> {
        try {
            // Указываем адрес нашего локального сервера
            val serverUrl = "http://127.0.0.1:8080/tools"
            
            // Выполняем GET-запрос и парсим JSON-ответ в список McpTool
            return client.get(serverUrl).body()
            
        } catch (e: Exception) {
            // В случае ошибки (например, сервер недоступен) возвращаем информацию об ошибке
            // чтобы ViewModel мог ее обработать
            return listOf(McpTool("Error", "Failed to connect to MCP server: ${e.message}"))
        }
    }
}
