package me.kabachel.aichallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun formatTimestamp(ts: String?): String {
    if (ts.isNullOrBlank()) return ""
    return try {
        val dt = Instant.parse(ts).toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    } catch (_: Exception) { "" }
}

fun formatConfidence(c: Double?): String {
    if (c == null) return ""
    val v = (c * 100.0).roundToInt() / 100.0
    return v.toString()
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val chatApi = remember { ChatGptApi() }
        val viewModel = viewModel { ChatViewModel(chatApi, BuildConfig.OPENAI_API_KEY) }
        val chatMessages = viewModel.chatMessages
        var userInput by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                reverseLayout = true
            ) {
                items(chatMessages.asReversed()) { message ->
                    val isUser = message.role == "user"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.large
                                )
                                .padding(12.dp)
                                .widthIn(max = 280.dp)
                        ) {
                            message.type?.let {
                                Text(
                                    text = it,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            SelectionContainer {
                                Text(
                                    text = message.content,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = message.language ?: "",
                                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = formatTimestamp(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            message.confidence?.let {
                                Text(
                                    text = "Уверенность в ответе: ${formatConfidence(it)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                                val text = userInput
                                if (text.isNotBlank()) {
                                    viewModel.sendMessage(text)
                                    userInput = ""
                                }
                                true
                            } else false
                        }
                )
                Button(onClick = {
                    val text = userInput
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                        userInput = ""
                    }
                }) {
                    Text("Отправить")
                }
            }
        }
    }
}