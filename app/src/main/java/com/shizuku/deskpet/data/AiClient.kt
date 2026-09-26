package com.shizuku.deskpet.data

import com.shizuku.deskpet.model.ChatMessage
import com.shizuku.deskpet.model.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
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

    sealed class StreamEvent {
        data class Answer(val text: String) : StreamEvent()
        data class Reasoning(val text: String) : StreamEvent()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendMessageStream(userMessage: String, isProactive: Boolean = false): Flow<StreamEvent> = flow {
        val repository = prefsManager.conversations
        val turn = repository.beginTurn(prefsManager.selectedPetId, userMessage, isProactive)
        var cancellationGuard: Job? = null
        try {
            val messages = ChatRequestMessages.build(
                systemPrompt = prefsManager.systemPrompt,
                history = if (prefsManager.memoryEnabled) turn.history else emptyList(),
                userMessage = userMessage,
                isProactive = isProactive,
                currentTime = ChatRequestMessages.currentTime(prefsManager.sendCurrentTime)
            )
            val requestBody = ChatRequest(model = prefsManager.model, messages = messages, stream = true)
            val request = Request.Builder()
                .url("${prefsManager.apiBaseUrl}/chat/completions")
                .addHeader("Authorization", "Bearer ${prefsManager.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(buildJsonBody(requestBody).toRequestBody(jsonMediaType))
                .build()
            val call = client.newCall(request)
            // Closing the HTTP call on cancellation also interrupts a blocked SSE read.
            cancellationGuard = CoroutineScope(currentCoroutineContext()).launch(
                start = CoroutineStart.UNDISPATCHED
            ) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("API请求失败: ${response.code} - ${response.body?.string()}")
                }
                val source = response.body?.source() ?: throw Exception("空响应")
                val answer = StringBuilder()
                val reasoning = StringBuilder()
                val parser = ThinkTagParser()
                var finished = false
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") { finished = true; break }
                    val json = try { JSONObject(data) } catch (_: org.json.JSONException) { continue }
                    if (json.has("error")) throw Exception("模型服务返回错误")
                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.getJSONObject(0)
                    if ((choice.opt("finish_reason") as? String)?.isNotBlank() == true) finished = true
                    val delta = choice.optJSONObject("delta") ?: continue
                    val thought = (delta.opt("reasoning_content") as? String)?.takeIf { it.isNotEmpty() }
                        ?: (delta.opt("reasoning") as? String).orEmpty()
                    if (thought.isNotEmpty()) {
                        reasoning.append(thought)
                        emit(StreamEvent.Reasoning(thought))
                    }
                    parser.feed((delta.opt("content") as? String).orEmpty()).forEach { event ->
                        when (event) {
                            is StreamEvent.Answer -> answer.append(event.text)
                            is StreamEvent.Reasoning -> reasoning.append(event.text)
                        }
                        emit(event)
                    }
                }
                if (!finished) throw Exception("回复传输中断，请重新发送")
                parser.finish().forEach { event ->
                    when (event) {
                        is StreamEvent.Answer -> answer.append(event.text)
                        is StreamEvent.Reasoning -> reasoning.append(event.text)
                    }
                    emit(event)
                }
                currentCoroutineContext().ensureActive()
                repository.completeTurn(turn, answer.toString(), reasoning.toString())
            }
        } catch (error: Exception) {
            val cancelled = error is CancellationException || !currentCoroutineContext().isActive
            withContext(NonCancellable) {
                runCatching { repository.failTurn(turn, cancelled) }
            }
            if (error is CancellationException) throw error
            if (cancelled) throw CancellationException("回复已停止").apply { initCause(error) }
            throw error
        } finally {
            cancellationGuard?.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildJsonBody(request: ChatRequest): String {
        val json = JSONObject()
        json.put("model", request.model)
        json.put("stream", request.stream)
        json.put("temperature", prefsManager.temperature.toDouble())
        json.put("max_tokens", prefsManager.maxTokens)
        if (prefsManager.thinkingEnabled) {
            when (prefsManager.thinkingProtocol) {
                "enable_thinking" -> json.put("enable_thinking", true)
                else -> json.put("reasoning_effort", "medium")
            }
        }

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

    private class ThinkTagParser {
        private var pending = ""
        private var inThought = false

        fun feed(chunk: String): List<StreamEvent> = consume(chunk, false)
        fun finish(): List<StreamEvent> = consume("", true)

        private fun consume(chunk: String, finished: Boolean): List<StreamEvent> {
            pending += chunk
            val result = mutableListOf<StreamEvent>()
            while (pending.isNotEmpty()) {
                val marker = if (inThought) "</think>" else "<think>"
                val index = pending.indexOf(marker)
                if (index >= 0) {
                    add(result, pending.substring(0, index))
                    pending = pending.substring(index + marker.length)
                    inThought = !inThought
                } else {
                    val count = if (finished) pending.length else (pending.length - marker.length + 1).coerceAtLeast(0)
                    if (count == 0) break
                    add(result, pending.substring(0, count))
                    pending = pending.substring(count)
                    break
                }
            }
            return result
        }

        private fun add(result: MutableList<StreamEvent>, text: String) {
            if (text.isNotEmpty()) result += if (inThought) StreamEvent.Reasoning(text) else StreamEvent.Answer(text)
        }
    }
}
