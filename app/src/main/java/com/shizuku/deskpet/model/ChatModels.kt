package com.shizuku.deskpet.model

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?,
    val error: ErrorResponse?
)

data class Choice(
    val index: Int?,
    val message: ChatMessage?,
    val finishReason: String?
)

data class ErrorResponse(
    val message: String?,
    val type: String?,
    val code: String?
)
