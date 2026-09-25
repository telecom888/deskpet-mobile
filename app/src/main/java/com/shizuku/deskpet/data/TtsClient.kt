package com.shizuku.deskpet.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TtsClient(private val context: Context, private val prefs: PreferencesManager) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
    private var player: MediaPlayer? = null

    suspend fun synthesizeAndPlay(text: String) {
        val output = withContext(Dispatchers.IO) { synthesize(text) }
        try {
            play(output)
        } finally {
            output.delete()
        }
    }

    private fun synthesize(text: String): File {
        require(prefs.ttsApiKey.isNotBlank()) { "请配置 TTS API Key" }
        val isMimo = prefs.ttsProvider == "mimo"
        val body: String
        val endpoint: String
        if (isMimo) {
            val reference = File(prefs.ttsReferencePath)
            require(reference.isFile) { "请选择参考音频" }
            val bytes = reference.readBytes()
            require(bytes.isNotEmpty() && bytes.size <= 7_000_000) { "参考音频须小于 7 MB" }
            val mime = if (reference.extension.equals("wav", true)) "audio/wav" else "audio/mpeg"
            val voice = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            body = JSONObject().apply {
                put("model", prefs.ttsModel.ifBlank { "mimo-v2.5-tts-voiceclone" })
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", ""))
                    .put(JSONObject().put("role", "assistant").put("content", text)))
                put("audio", JSONObject().put("format", "wav").put("voice", voice))
            }.toString()
            endpoint = "${prefs.ttsBaseUrl.trimEnd('/')}/chat/completions"
        } else {
            require(prefs.ttsVoice.isNotBlank()) { "请配置音色 ID" }
            body = JSONObject().apply {
                put("model", prefs.ttsModel)
                put("input", text)
                put("voice", prefs.ttsVoice)
                put("response_format", "wav")
            }.toString()
            endpoint = "${prefs.ttsBaseUrl.trimEnd('/')}/audio/speech"
        }
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer ${prefs.ttsApiKey}")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val audio = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("TTS 请求失败 (${response.code})")
            val responseBody = response.body ?: throw IllegalStateException("TTS 响应为空")
            if (isMimo) {
                val data = JSONObject(responseBody.string()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getJSONObject("audio").getString("data")
                Base64.decode(data, Base64.DEFAULT)
            } else {
                responseBody.bytes()
            }
        }
        require(audio.isNotEmpty()) { "TTS 未返回音频" }
        return File.createTempFile("deskpet_tts_", ".wav", context.cacheDir).also { it.writeBytes(audio) }
    }

    private suspend fun play(file: File) = suspendCancellableCoroutine<Unit> { continuation ->
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        continuation.invokeOnCancellation {
            mediaPlayer.setOnCompletionListener(null)
            mediaPlayer.setOnErrorListener(null)
            mediaPlayer.release()
            if (player === mediaPlayer) player = null
        }
        try {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnCompletionListener {
                it.release()
                if (player === it) player = null
                if (continuation.isActive) continuation.resume(Unit)
            }
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (player === mp) player = null
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("音频播放失败"))
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (error: Exception) {
            mediaPlayer.release()
            player = null
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    fun stop() {
        player?.runCatching { release() }
        player = null
    }
}
