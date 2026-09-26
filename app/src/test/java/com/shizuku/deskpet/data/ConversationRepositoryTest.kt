package com.shizuku.deskpet.data

import com.shizuku.deskpet.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ConversationRepositoryTest {
    private class Storage(var content: String? = null) : ConversationStorage {
        var rejectWrites = false
        override fun read() = content
        override fun write(content: String) {
            if (rejectWrites) throw IOException("disk full")
            this.content = content
        }
    }

    @Test
    fun recordsAndMainSelectionSurviveRepositoryRecreation() {
        val storage = Storage()
        val repository = ConversationRepository(storage)
        val conversation = repository.createConversation("shiroko", "白子 · 夜间聊天")
        val turn = repository.beginTurn("shiroko", "带引号\"和换行\n的消息", false)
        repository.completeTurn(turn, "回复\n第二行", "独立思考")
        val restored = ConversationRepository(Storage(storage.content)).snapshot()
        assertEquals(repository.snapshot(), restored)
        assertEquals(conversation.id, restored.mainConversationIds["shiroko"])
        assertEquals("独立思考", restored.conversations.single().messages.last().reasoning)
        assertTrue(restored.conversations.single().messages.first().createdAt > 0)
    }

    @Test
    fun eachRoleHasAnIndependentMainAndCannotSelectAnotherRolesConversation() {
        val storage = Storage()
        val repository = ConversationRepository(storage)
        val first = repository.createConversation("shiroko")
        val second = repository.createConversation("shiroko")
        val otherRole = repository.createConversation("perlica")
        repository.setMainConversation("shiroko", first.id)
        assertEquals(first.id, repository.mainConversation("shiroko").id)
        assertEquals(otherRole.id, repository.mainConversation("perlica").id)
        val before = storage.content
        assertThrows(IllegalArgumentException::class.java) { repository.setMainConversation("shiroko", otherRole.id) }
        assertEquals(before, storage.content)
        assertTrue(repository.snapshot().conversations.any { it.id == second.id })
    }

    @Test
    fun switchingMainDuringARequestKeepsReplyInItsOriginalConversation() {
        val repository = ConversationRepository(Storage())
        val original = repository.mainConversation("shiroko")
        val turn = repository.beginTurn("shiroko", "原对话消息", false)
        val newMain = repository.createConversation("shiroko", "新对话")
        repository.completeTurn(turn, "原对话回复", "")
        assertEquals(newMain.id, repository.mainConversation("shiroko").id)
        assertTrue(repository.mainConversation("shiroko").messages.isEmpty())
        val recorded = repository.snapshot().conversations.first { it.id == original.id }
        assertEquals(listOf(ChatMessage("user", "原对话消息"), ChatMessage("assistant", "原对话回复")), recorded.contextMessages())
    }

    @Test
    fun deletingMainSelectsAnotherAndDeletingLastCreatesAnEmptyMain() {
        val repository = ConversationRepository(Storage())
        val first = repository.createConversation("shiroko")
        val second = repository.createConversation("shiroko")
        val other = repository.createConversation("perlica")
        repository.deleteConversation("shiroko", second.id)
        assertEquals(first.id, repository.mainConversation("shiroko").id)
        repository.deleteConversation("shiroko", first.id)
        val replacement = repository.mainConversation("shiroko")
        assertNotEquals(first.id, replacement.id)
        assertTrue(replacement.messages.isEmpty())
        assertEquals(other.id, repository.mainConversation("perlica").id)
        assertEquals(2, repository.snapshot().conversations.size)
    }

    @Test
    fun deletedConversationCannotBeRecreatedByItsLateReply() {
        val repository = ConversationRepository(Storage())
        val turn = repository.beginTurn("shiroko", "删除中的请求", false)
        repository.deleteConversation("shiroko", turn.conversationId)
        val snapshot = repository.snapshot()
        repository.completeTurn(turn, "迟到的回复", "思考")
        assertEquals(snapshot, repository.snapshot())
    }

    @Test
    fun deletingATurnRemovesQuestionAnswerAndThoughtAndIgnoresLateCompletion() {
        val repository = ConversationRepository(Storage())
        val first = repository.beginTurn("shiroko", "保留的问题", false)
        repository.completeTurn(first, "保留的回复", "")
        val deleted = repository.beginTurn("shiroko", "删除的问题", false)
        val conversation = repository.mainConversation("shiroko")
        val turnId = conversation.messages.first { it.id == deleted.assistantMessageId }.turnId
        repository.deleteTurn("shiroko", conversation.id, turnId)
        repository.completeTurn(deleted, "迟到的回复", "不应出现的思考")
        val kept = repository.mainConversation("shiroko")
        assertEquals(2, kept.messages.size)
        assertEquals(listOf(ChatMessage("user", "保留的问题"), ChatMessage("assistant", "保留的回复")), kept.contextMessages())
    }

    @Test
    fun failedAndCancelledTurnsRemainVisibleButAreExcludedFromContext() {
        val repository = ConversationRepository(Storage())
        val failed = repository.beginTurn("shiroko", "失败消息", false)
        repository.failTurn(failed, false)
        val cancelled = repository.beginTurn("shiroko", "取消消息", false)
        assertTrue(cancelled.history.isEmpty())
        repository.failTurn(cancelled, true)
        val next = repository.beginTurn("shiroko", "下一条", false)
        assertTrue(next.history.isEmpty())
        repository.completeTurn(next, "成功", "")
        val conversation = repository.mainConversation("shiroko")
        assertEquals(6, conversation.messages.size)
        assertEquals(listOf("failed", "cancelled", "completed"), conversation.messages.filter { it.role == "assistant" }.map { it.status })
        assertEquals(listOf(ChatMessage("user", "下一条"), ChatMessage("assistant", "成功")), conversation.contextMessages())
    }

    @Test
    fun proactiveRepliesAreRecordedWithoutInventingUserTextOrSendingThoughtsAsContext() {
        val repository = ConversationRepository(Storage())
        val turn = repository.beginTurn("shiroko", "", true)
        repository.completeTurn(turn, "晚上好", "思考内容")
        val conversation = repository.mainConversation("shiroko")
        assertEquals(1, conversation.messages.size)
        assertTrue(conversation.messages.single().proactive)
        assertEquals(listOf(ChatMessage("assistant", "晚上好")), conversation.contextMessages())
    }

    @Test
    fun corruptDataAndWriteFailuresDoNotReplaceExistingRecords() {
        val corrupted = Storage("{invalid json")
        val corruptedRepository = ConversationRepository(corrupted)
        assertThrows(IOException::class.java) { corruptedRepository.createConversation("shiroko") }
        assertEquals("{invalid json", corrupted.content)
        val storage = Storage()
        val repository = ConversationRepository(storage)
        val main = repository.mainConversation("shiroko")
        val before = storage.content
        storage.rejectWrites = true
        assertThrows(IOException::class.java) { repository.deleteConversation("shiroko", main.id) }
        assertEquals(before, storage.content)
        assertEquals(main.id, repository.mainConversation("shiroko").id)
    }
}
