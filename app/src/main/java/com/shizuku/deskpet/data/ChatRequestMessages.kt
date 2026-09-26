package com.shizuku.deskpet.data

import com.shizuku.deskpet.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object ChatRequestMessages {
    fun currentTime(enabled: Boolean, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): ChatMessage? {
        if (!enabled) return null
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).apply {
            timeZone = zone
        }.format(now)
        return ChatMessage(
            "system",
            "当前现实时间：$timestamp；时区：${zone.id}。这是本次请求发送时的设备时间，" +
                "可用于现实时间同步的问候和时间判断；若用户设定了故事内时间，请以故事设定为准。"
        )
    }

    fun build(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        isProactive: Boolean,
        currentTime: ChatMessage?
    ): List<ChatMessage> = buildList {
        if (systemPrompt.isNotBlank()) add(ChatMessage("system", systemPrompt))
        currentTime?.let { add(it) }
        addAll(history)
        if (isProactive) {
            add(ChatMessage("system", "请根据你的人设，主动找我搭话。可以是问候、分享一个有趣的冷知识、或者关心我现在的状态。要求：字数控制在20字以内，不要显得像机器回复，直接说出内容即可。"))
        } else {
            add(ChatMessage("user", userMessage))
        }
    }
}
