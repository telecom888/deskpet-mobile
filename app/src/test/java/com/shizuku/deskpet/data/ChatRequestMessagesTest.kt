package com.shizuku.deskpet.data

import com.shizuku.deskpet.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class ChatRequestMessagesTest {
    @Test
    fun disabledTimeAddsNoContext() {
        assertNull(ChatRequestMessages.currentTime(false, Date(0), TimeZone.getTimeZone("Asia/Shanghai")))
        val messages = ChatRequestMessages.build("", emptyList(), "你好", false, null)
        assertEquals(listOf(ChatMessage("user", "你好")), messages)
    }

    @Test
    fun timestampIncludesLocalDateUtcOffsetAndZone() {
        val context = ChatRequestMessages.currentTime(true, Date(0), TimeZone.getTimeZone("Asia/Shanghai"))!!
        assertEquals("system", context.role)
        assertTrue(context.content.contains("1970-01-01T08:00:00+08:00"))
        assertTrue(context.content.contains("Asia/Shanghai"))
        assertTrue(context.content.contains("故事设定"))
        val previousDay = ChatRequestMessages.currentTime(true, Date(0), TimeZone.getTimeZone("Pacific/Honolulu"))!!
        assertTrue(previousDay.content.contains("1969-12-31T14:00:00-10:00"))
    }

    @Test
    fun timestampUsesDaylightSavingOffsetForRequestDate() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.JULY, 1, 0, 0, 0)
        }.time
        val context = ChatRequestMessages.currentTime(true, now, TimeZone.getTimeZone("America/New_York"))!!
        assertTrue(context.content.contains("2026-06-30T20:00:00-04:00"))
    }

    @Test
    fun currentTimeIsEphemeralAndDoesNotChangeHistoryOrUserText() {
        val history = mutableListOf(ChatMessage("user", "上一条"), ChatMessage("assistant", "回答"))
        val snapshot = history.toList()
        val time = ChatRequestMessages.currentTime(true, Date(0), TimeZone.getTimeZone("UTC"))!!
        val messages = ChatRequestMessages.build("人设", history, "新消息", false, time)
        assertEquals(listOf("system", "system", "user", "assistant", "user"), messages.map { it.role })
        assertEquals(time, messages[1])
        assertEquals(ChatMessage("user", "新消息"), messages.last())
        assertEquals(snapshot, history)
        val nextRequest = ChatRequestMessages.build("人设", history, "继续", false, null)
        assertFalse(nextRequest.contains(time))
    }

    @Test
    fun proactiveRequestsAlsoReceiveTimeWithoutFakeUserMessage() {
        val time = ChatRequestMessages.currentTime(true, Date(0), TimeZone.getTimeZone("UTC"))!!
        val messages = ChatRequestMessages.build("人设", emptyList(), "", true, time)
        assertEquals(time, messages[1])
        assertTrue(messages.last().content.contains("主动找我搭话"))
        assertTrue(messages.none { it.role == "user" })
    }
}
