package com.shizuku.deskpet.model

data class ConversationMessage(
    val id: String,
    val turnId: String,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val createdAt: Long,
    val status: String = "completed",
    val proactive: Boolean = false
)

data class Conversation(
    val id: String,
    val petId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ConversationMessage> = emptyList()
) {
    fun contextMessages(): List<ChatMessage> {
        val completedTurns = messages.filter { it.role == "assistant" && it.status == "completed" }.map { it.turnId }.toSet()
        return messages.filter { it.status == "completed" && it.turnId in completedTurns && it.content.isNotBlank() }
            .map { ChatMessage(it.role, it.content) }
    }
}

data class ConversationSnapshot(val conversations: List<Conversation>, val mainConversationIds: Map<String, String>)

data class ConversationTurn(
    val conversationId: String,
    val assistantMessageId: String,
    val history: List<ChatMessage>
)
