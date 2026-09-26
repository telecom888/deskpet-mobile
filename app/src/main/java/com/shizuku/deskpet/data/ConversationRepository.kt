package com.shizuku.deskpet.data

import android.content.Context
import android.util.AtomicFile
import com.shizuku.deskpet.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

internal interface ConversationStorage {
    fun read(): String?
    fun write(content: String)
}

class ConversationRepository internal constructor(private val storage: ConversationStorage) {
    constructor(context: Context) : this(object : ConversationStorage {
        private val file = AtomicFile(File(context.applicationContext.filesDir, "conversations/history.json"))
        override fun read(): String? = try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
            null
        }

        override fun write(content: String) {
            val stream = file.startWrite()
            try {
                stream.write(content.toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw IOException("无法保存对话记录", error)
            }
        }
    })

    val changes get() = revision.asStateFlow()

    fun snapshot(): ConversationSnapshot = synchronized(lock) { read() }

    fun mainConversation(petId: String): Conversation = synchronized(lock) {
        val snapshot = read()
        snapshot.conversations.firstOrNull { it.petId == petId && it.id == snapshot.mainConversationIds[petId] }
            ?: create(petId, "", snapshot)
    }

    fun createConversation(petId: String, title: String = ""): Conversation = synchronized(lock) {
        create(petId, title, read())
    }

    private fun create(petId: String, title: String, snapshot: ConversationSnapshot): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            UUID.randomUUID().toString(), petId,
            title.trim().take(50).ifBlank { "对话 ${snapshot.conversations.count { it.petId == petId } + 1}" }, now, now
        )
        write(snapshot.copy(
            conversations = snapshot.conversations + conversation,
            mainConversationIds = snapshot.mainConversationIds + (petId to conversation.id)
        ))
        return conversation
    }

    fun setMainConversation(petId: String, conversationId: String) = synchronized(lock) {
        val snapshot = read()
        require(snapshot.conversations.any { it.id == conversationId && it.petId == petId }) { "该对话不属于当前角色" }
        write(snapshot.copy(mainConversationIds = snapshot.mainConversationIds + (petId to conversationId)))
    }

    fun deleteConversation(petId: String, conversationId: String) = synchronized(lock) {
        val snapshot = read()
        require(snapshot.conversations.any { it.id == conversationId && it.petId == petId }) { "该对话不属于当前角色" }
        val remaining = snapshot.copy(conversations = snapshot.conversations.filterNot { it.id == conversationId })
        val roleConversations = remaining.conversations.filter { it.petId == petId }
        if (roleConversations.isEmpty()) {
            create(petId, "", remaining)
        } else if (snapshot.mainConversationIds[petId] == conversationId) {
            val replacement = roleConversations.maxBy { it.updatedAt }
            write(remaining.copy(mainConversationIds = remaining.mainConversationIds + (petId to replacement.id)))
        } else {
            write(remaining)
        }
    }

    fun deleteTurn(petId: String, conversationId: String, turnId: String) = synchronized(lock) {
        val snapshot = read()
        val conversation = snapshot.conversations.firstOrNull { it.id == conversationId && it.petId == petId }
            ?: throw IllegalArgumentException("该对话不属于当前角色")
        val updated = conversation.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conversation.messages.filterNot { it.turnId == turnId }
        )
        write(snapshot.copy(conversations = snapshot.conversations.map { if (it.id == conversationId) updated else it }))
    }

    fun beginTurn(petId: String, text: String, proactive: Boolean): ConversationTurn = synchronized(lock) {
        val main = mainConversation(petId)
        val snapshot = read()
        val conversation = snapshot.conversations.first { it.id == main.id }
        val now = System.currentTimeMillis()
        val turnId = UUID.randomUUID().toString()
        val assistantId = UUID.randomUUID().toString()
        val messages = buildList {
            if (!proactive) add(ConversationMessage(UUID.randomUUID().toString(), turnId, "user", text, createdAt = now))
            add(ConversationMessage(assistantId, turnId, "assistant", "", createdAt = now, status = "pending", proactive = proactive))
        }
        write(snapshot.copy(conversations = snapshot.conversations.map {
            if (it.id == conversation.id) it.copy(updatedAt = now, messages = it.messages + messages) else it
        }))
        ConversationTurn(conversation.id, assistantId, conversation.contextMessages())
    }

    fun completeTurn(turn: ConversationTurn, answer: String, reasoning: String) {
        require(answer.isNotBlank()) { "模型未返回有效正文" }
        finishTurn(turn, "completed", answer, reasoning)
    }

    fun failTurn(turn: ConversationTurn, cancelled: Boolean) {
        finishTurn(turn, if (cancelled) "cancelled" else "failed", "", "")
    }

    private fun finishTurn(turn: ConversationTurn, status: String, content: String, reasoning: String) = synchronized(lock) {
        val snapshot = read()
        val conversation = snapshot.conversations.firstOrNull { it.id == turn.conversationId } ?: return@synchronized
        val message = conversation.messages.firstOrNull { it.id == turn.assistantMessageId } ?: return@synchronized
        if (message.status != "pending") return@synchronized
        val updated = conversation.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conversation.messages.map {
                if (it.id == message.id) it.copy(status = status, content = content, reasoning = reasoning) else it
            }
        )
        // Use the captured conversation ID: switching the main conversation must not reroute an in-flight reply.
        write(snapshot.copy(conversations = snapshot.conversations.map { if (it.id == updated.id) updated else it }))
    }

    private fun read(): ConversationSnapshot {
        val content = storage.read() ?: return ConversationSnapshot(emptyList(), emptyMap())
        try {
            val root = JSONObject(content)
            require(root.getInt("schema") == 1) { "不支持的历史记录版本" }
            val conversations = root.getJSONArray("conversations").let { array ->
                (0 until array.length()).map { index ->
                    val json = array.getJSONObject(index)
                    val messages = json.getJSONArray("messages").let { entries ->
                        (0 until entries.length()).map { entryIndex ->
                            val entry = entries.getJSONObject(entryIndex)
                            ConversationMessage(
                                id = entry.getString("id"), turnId = entry.getString("turnId"),
                                role = entry.getString("role"), content = entry.getString("content"),
                                reasoning = entry.optString("reasoning", ""), createdAt = entry.getLong("createdAt"),
                                status = entry.optString("status", "completed"), proactive = entry.optBoolean("proactive", false)
                            )
                        }
                    }
                    Conversation(json.getString("id"), json.getString("petId"), json.getString("title"), json.getLong("createdAt"), json.getLong("updatedAt"), messages)
                }
            }
            val main = root.getJSONObject("mainConversationIds")
            val ids = main.keys().asSequence().associateWith { main.getString(it) }
            return ConversationSnapshot(conversations, ids)
        } catch (error: Exception) {
            throw IOException("对话记录无法读取，请保留数据后重试", error)
        }
    }

    private fun write(snapshot: ConversationSnapshot) {
        val conversations = JSONArray()
        snapshot.conversations.forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.forEach { message ->
                messages.put(JSONObject().put("id", message.id).put("turnId", message.turnId)
                    .put("role", message.role).put("content", message.content).put("reasoning", message.reasoning)
                    .put("createdAt", message.createdAt).put("status", message.status).put("proactive", message.proactive))
            }
            conversations.put(JSONObject().put("id", conversation.id).put("petId", conversation.petId)
                .put("title", conversation.title).put("createdAt", conversation.createdAt).put("updatedAt", conversation.updatedAt).put("messages", messages))
        }
        storage.write(JSONObject().put("schema", 1).put("conversations", conversations)
            .put("mainConversationIds", JSONObject(snapshot.mainConversationIds)).toString())
        revision.value += 1
    }

    companion object {
        private val lock = Any()
        private val revision = MutableStateFlow(0L)
    }
}
