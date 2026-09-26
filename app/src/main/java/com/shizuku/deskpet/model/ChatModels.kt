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
