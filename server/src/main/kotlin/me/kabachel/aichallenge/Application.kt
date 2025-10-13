package me.kabachel.aichallenge

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// Определим data-класс для инструмента прямо здесь, чтобы сервер был самодостаточным
@Serializable
data class McpTool(val name: String, val description: String)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/tools") {
                // Готовим заранее определенный список инструментов
                val tools = listOf(
                    McpTool("File Reader", "Читает файлы с диска."),
                    McpTool("Code Search", "Ищет фрагменты кода в проекте."),
                    McpTool("Web Search", "Выполняет поиск в интернете.")
                )
                // Отдаем список в формате JSON
                call.respond(tools)
            }
        }
    }.start(wait = true)
}
