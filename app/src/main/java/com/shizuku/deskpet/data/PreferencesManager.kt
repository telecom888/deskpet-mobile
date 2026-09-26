package com.shizuku.deskpet.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    val conversations = ConversationRepository(context)

    var inputDialogStyle: String
        get() = prefs.getString("input_dialog_style", "bottom_card")?.takeIf { it in setOf("compact", "bottom_card") } ?: "bottom_card"
        set(value) = prefs.edit().putString("input_dialog_style", value).apply()

    var sendCurrentTime: Boolean
        get() = prefs.getBoolean("send_current_time", false)
        set(value) = prefs.edit().putBoolean("send_current_time", value).apply()

    fun inputDraft(petId: String): String = prefs.getString("input_draft_$petId", "") ?: ""

    fun saveInputDraft(petId: String, text: String) {
        prefs.edit().putString("input_draft_$petId", text).apply()
    }

    var selectedPetId: String
        get() = prefs.getString("selected_pet_id", "perlica") ?: "perlica"
        set(value) = prefs.edit().putString("selected_pet_id", value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", false)
        set(value) = prefs.edit().putBoolean("tts_enabled", value).apply()

    var ttsProvider: String
        get() = prefs.getString("tts_provider", "mimo") ?: "mimo"
        set(value) = prefs.edit().putString("tts_provider", value).apply()

    var ttsBaseUrl: String
        get() = prefs.getString("tts_base_url", "https://api.xiaomimimo.com/v1") ?: "https://api.xiaomimimo.com/v1"
        set(value) = prefs.edit().putString("tts_base_url", value).apply()

    var ttsApiKey: String
        get() = prefs.getString("tts_api_key", "") ?: ""
        set(value) = prefs.edit().putString("tts_api_key", value).apply()

    var ttsModel: String
        get() = prefs.getString("tts_model", "mimo-v2.5-tts-voiceclone") ?: "mimo-v2.5-tts-voiceclone"
        set(value) = prefs.edit().putString("tts_model", value).apply()

    var ttsReferencePath: String
        get() = prefs.getString("tts_reference_path", "") ?: ""
        set(value) = prefs.edit().putString("tts_reference_path", value).apply()

    var ttsVoice: String
        get() = prefs.getString("tts_voice", "") ?: ""
        set(value) = prefs.edit().putString("tts_voice", value).apply()

    var thinkingEnabled: Boolean
        get() = prefs.getBoolean("thinking_enabled", false)
        set(value) = prefs.edit().putBoolean("thinking_enabled", value).apply()

    var thinkingProtocol: String
        get() = prefs.getString("thinking_protocol", "reasoning_effort") ?: "reasoning_effort"
        set(value) = prefs.edit().putString("thinking_protocol", value).apply()

    var foldThinking: Boolean
        get() = prefs.getBoolean("fold_thinking", true)
        set(value) = prefs.edit().putBoolean("fold_thinking", value).apply()

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    var apiBaseUrl: String
        get() = prefs.getString(KEY_API_BASE_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
        set(value) = prefs.edit().putString(KEY_API_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var maxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS)
        set(value) = prefs.edit().putInt(KEY_MAX_TOKENS, value).apply()

    var isFloatingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_ENABLED, value).apply()

    var showAiBubble: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AI_BUBBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_AI_BUBBLE, value).apply()

    var playSound: Boolean
        get() = prefs.getBoolean(KEY_PLAY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_PLAY_SOUND, value).apply()

    var aiBubbleDuration: Int
        get() = prefs.getInt(KEY_AI_BUBBLE_DURATION, DEFAULT_BUBBLE_DURATION)
        set(value) = prefs.edit().putInt(KEY_AI_BUBBLE_DURATION, value).apply()

    var floatingWindowWidth: Int
        get() = prefs.getInt(KEY_FLOATING_WIDTH, DEFAULT_FLOATING_WIDTH)
        set(value) = prefs.edit().putInt(KEY_FLOATING_WIDTH, value).apply()

    var floatingWindowHeight: Int
        get() = prefs.getInt(KEY_FLOATING_HEIGHT, DEFAULT_FLOATING_HEIGHT)
        set(value) = prefs.edit().putInt(KEY_FLOATING_HEIGHT, value).apply()

    var smileEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMILE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SMILE_ENABLED, value).apply()

    var smileProbability: Float
        get() = prefs.getFloat(KEY_SMILE_PROBABILITY, DEFAULT_SMILE_PROBABILITY)
        set(value) = prefs.edit().putFloat(KEY_SMILE_PROBABILITY, value).apply()

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    var memoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MEMORY_ENABLED, value).apply()

    var proactiveChatLevel: Int
        get() = prefs.getInt(KEY_PROACTIVE_CHAT_LEVEL, 2)
        set(value) = prefs.edit().putInt(KEY_PROACTIVE_CHAT_LEVEL, value).apply()

    var proactiveChatMinInterval: Int
        get() = prefs.getInt(KEY_PROACTIVE_CHAT_MIN_INTERVAL, 60)
        set(value) = prefs.edit().putInt(KEY_PROACTIVE_CHAT_MIN_INTERVAL, value).apply()

    var proactiveChatMaxInterval: Int
        get() = prefs.getInt(KEY_PROACTIVE_CHAT_MAX_INTERVAL, 120)
        set(value) = prefs.edit().putInt(KEY_PROACTIVE_CHAT_MAX_INTERVAL, value).apply()

    fun isConfigured(): Boolean {
        return apiKey.isNotBlank() && apiBaseUrl.isNotBlank()
    }

    companion object {
        private const val PREFS_NAME = "deskpet_prefs"

        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_FLOATING_ENABLED = "floating_enabled"
        private const val KEY_SHOW_AI_BUBBLE = "show_ai_bubble"
        private const val KEY_PLAY_SOUND = "play_sound"
        private const val KEY_AI_BUBBLE_DURATION = "ai_bubble_duration"
        private const val KEY_FLOATING_WIDTH = "floating_width"
        private const val KEY_FLOATING_HEIGHT = "floating_height"
        private const val KEY_SMILE_ENABLED = "smile_enabled"
        private const val KEY_SMILE_PROBABILITY = "smile_probability"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_MEMORY_ENABLED = "memory_enabled"
        private const val KEY_PROACTIVE_CHAT_LEVEL = "proactive_chat_level"
        private const val KEY_PROACTIVE_CHAT_MIN_INTERVAL = "proactive_chat_min_interval"
        private const val KEY_PROACTIVE_CHAT_MAX_INTERVAL = "proactive_chat_max_interval"

        private const val DEFAULT_API_URL = "https://api.openai.com/v1"
        private const val DEFAULT_MODEL = "gpt-3.5-turbo"
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_MAX_TOKENS = 1000
        private const val DEFAULT_BUBBLE_DURATION = 5000
        private const val DEFAULT_FLOATING_WIDTH = 120
        private const val DEFAULT_FLOATING_HEIGHT = 160
        private const val DEFAULT_SMILE_PROBABILITY = 0.1f
    }
}
