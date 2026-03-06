package com.shizuku.deskpet.data

import com.shizuku.deskpet.model.ChatMessage
import com.shizuku.deskpet.model.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiClient(private val prefsManager: PreferencesManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val messageHistory = mutableListOf<ChatMessage>()

    fun clearHistory() {
        messageHistory.clear()
    }

    fun sendMessageStream(userMessage: String, isProactive: Boolean = false): Flow<String> = flow {
        val messages = mutableListOf<ChatMessage>()

        if (prefsManager.systemPrompt.isNotBlank()) {
            messages.add(ChatMessage("system", prefsManager.systemPrompt))
        }

        if (prefsManager.memoryEnabled) {
            messages.addAll(messageHistory)
        }

        if (isProactive) {
            val proactivePrompt = "请根据你的人设，主动找我搭话。可以是问候、分享一个有趣的冷知识、或者关心我现在的状态。要求：字数控制在20字以内，不要显得像机器回复，直接说出内容即可。"
            messages.add(ChatMessage("system", proactivePrompt))
        } else {
            messages.add(ChatMessage("user", userMessage))
        }

        val requestBody = ChatRequest(
            model = prefsManager.model,
            messages = messages,
            stream = true
        )

        val jsonBody = buildJsonBody(requestBody)

        val request = Request.Builder()
            .url("${prefsManager.apiBaseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${prefsManager.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("API请求失败: ${response.code} - $errorBody")
            }

            val source = response.body?.source() ?: throw Exception("空响应")
            val fullContentBuilder = StringBuilder()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break

                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break

                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""

                            if (content.isNotEmpty()) {
                                fullContentBuilder.append(content)
                                emit(content)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }

            if (prefsManager.memoryEnabled) {
                if (!isProactive) {
                    messageHistory.add(ChatMessage("user", userMessage))
                }
                messageHistory.add(ChatMessage("assistant", fullContentBuilder.toString()))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildJsonBody(request: ChatRequest): String {
        val json = JSONObject()
        json.put("model", request.model)
        json.put("stream", request.stream)

        val messagesArray = JSONArray()
        request.messages.forEach { msg ->
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            messagesArray.put(msgObj)
        }
        json.put("messages", messagesArray)

        return json.toString()
    }
}
