package com.shizuku.deskpet.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.shizuku.deskpet.data.PreferencesManager
import com.shizuku.deskpet.data.PetCatalog
import java.io.File
import kotlinx.coroutines.launch

private enum class SettingsPage(val title: String) {
    Home("设置"),
    Appearance("角色与外观"),
    Desktop("桌面交互"),
    Ai("AI 服务"),
    Dialogue("人设与对话"),
    Voice("语音合成"),
    About("关于与开源")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    onBack: () -> Unit
) {
    var apiBaseUrl by rememberSaveable { mutableStateOf(prefsManager.apiBaseUrl) }
    var apiKey by rememberSaveable { mutableStateOf(prefsManager.apiKey) }
    var model by rememberSaveable { mutableStateOf(prefsManager.model) }
    var temperature by rememberSaveable { mutableFloatStateOf(prefsManager.temperature) }
    var maxTokens by rememberSaveable { mutableIntStateOf(prefsManager.maxTokens) }
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    var showFloatingEnabled by rememberSaveable { mutableStateOf(prefsManager.isFloatingEnabled) }
    var showAiBubble by rememberSaveable { mutableStateOf(prefsManager.showAiBubble) }
    var playSound by rememberSaveable { mutableStateOf(prefsManager.playSound) }
    var aiBubbleDuration by rememberSaveable { mutableIntStateOf(prefsManager.aiBubbleDuration / 1000) }
    var floatingWidth by rememberSaveable { mutableIntStateOf(prefsManager.floatingWindowWidth) }
    var floatingHeight by rememberSaveable { mutableIntStateOf(prefsManager.floatingWindowHeight) }
    var smileEnabled by rememberSaveable { mutableStateOf(prefsManager.smileEnabled) }
    var smileProbability by rememberSaveable { mutableStateOf((prefsManager.smileProbability * 100).toInt()) }
    var systemPrompt by rememberSaveable { mutableStateOf(prefsManager.systemPrompt) }
    var memoryEnabled by rememberSaveable { mutableStateOf(prefsManager.memoryEnabled) }
    var proactiveChatLevel by rememberSaveable { mutableStateOf(prefsManager.proactiveChatLevel) }
    var proactiveChatMinInterval by rememberSaveable { mutableStateOf(prefsManager.proactiveChatMinInterval) }
    var proactiveChatMaxInterval by rememberSaveable { mutableStateOf(prefsManager.proactiveChatMaxInterval) }
    var selectedPetId by rememberSaveable { mutableStateOf(prefsManager.selectedPetId) }
    var ttsEnabled by rememberSaveable { mutableStateOf(prefsManager.ttsEnabled) }
    var ttsProvider by rememberSaveable { mutableStateOf(prefsManager.ttsProvider) }
    var ttsBaseUrl by rememberSaveable { mutableStateOf(prefsManager.ttsBaseUrl) }
    var ttsApiKey by rememberSaveable { mutableStateOf(prefsManager.ttsApiKey) }
    var ttsModel by rememberSaveable { mutableStateOf(prefsManager.ttsModel) }
    var ttsVoice by rememberSaveable { mutableStateOf(prefsManager.ttsVoice) }
    var ttsReferencePath by rememberSaveable { mutableStateOf(prefsManager.ttsReferencePath) }
    var thinkingEnabled by rememberSaveable { mutableStateOf(prefsManager.thinkingEnabled) }
    var thinkingProtocol by rememberSaveable { mutableStateOf(prefsManager.thinkingProtocol) }
    var foldThinking by rememberSaveable { mutableStateOf(prefsManager.foldThinking) }
    var audioSelectionError by rememberSaveable { mutableStateOf("") }


    val context = LocalContext.current
    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri) ?: ""
                val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: ""
                val extension = if (mime.contains("wav") || displayName.endsWith(".wav", true)) "wav" else "mp3"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取音频")
                require(bytes.size <= 7_000_000) { "参考音频须小于 7 MB" }
                // Stage the sample so choosing audio does not replace the saved voice before Save.
                val target = File(context.filesDir, "tts_reference_draft.$extension")
                target.writeBytes(bytes)
                ttsReferencePath = target.absolutePath
                audioSelectionError = ""
            } catch (error: Exception) {
                audioSelectionError = error.message ?: "音频读取失败"
            }
        }
    }
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Keep a separate position for every page, outside AnimatedContent.
    val pageScrollStates = SettingsPage.values().associateWith { page ->
        key(page) { rememberScrollState() }
    }
    var savedRevision by remember { mutableIntStateOf(0) }
    val hasUnsavedChanges = savedRevision.let {
        apiBaseUrl != prefsManager.apiBaseUrl ||
        apiKey != prefsManager.apiKey ||
        model != prefsManager.model ||
        temperature != prefsManager.temperature ||
        maxTokens != prefsManager.maxTokens ||
        showFloatingEnabled != prefsManager.isFloatingEnabled ||
        showAiBubble != prefsManager.showAiBubble ||
        playSound != prefsManager.playSound ||
        aiBubbleDuration * 1000 != prefsManager.aiBubbleDuration ||
        floatingWidth != prefsManager.floatingWindowWidth ||
        floatingHeight != prefsManager.floatingWindowHeight ||
        smileEnabled != prefsManager.smileEnabled ||
        smileProbability / 100f != prefsManager.smileProbability ||
        systemPrompt != prefsManager.systemPrompt ||
        memoryEnabled != prefsManager.memoryEnabled ||
        proactiveChatLevel != prefsManager.proactiveChatLevel ||
        proactiveChatMinInterval != prefsManager.proactiveChatMinInterval ||
        proactiveChatMaxInterval != prefsManager.proactiveChatMaxInterval ||
        selectedPetId != prefsManager.selectedPetId ||
        ttsEnabled != prefsManager.ttsEnabled ||
        ttsProvider != prefsManager.ttsProvider ||
        ttsBaseUrl != prefsManager.ttsBaseUrl ||
        ttsApiKey != prefsManager.ttsApiKey ||
        ttsModel != prefsManager.ttsModel ||
        ttsVoice != prefsManager.ttsVoice ||
        ttsReferencePath != prefsManager.ttsReferencePath ||
        thinkingEnabled != prefsManager.thinkingEnabled ||
        thinkingProtocol != prefsManager.thinkingProtocol ||
        foldThinking != prefsManager.foldThinking
    }

    fun saveSettings(): Boolean {
        if (proactiveChatLevel == 4 && proactiveChatMinInterval > proactiveChatMaxInterval) {
            currentPage = SettingsPage.Dialogue
            scope.launch { snackbarHostState.showSnackbar("最小间隔不能大于最大间隔") }
            return false
        }
        if (ttsReferencePath.isNotBlank()) {
            val draft = File(ttsReferencePath)
            if (draft.parentFile == context.filesDir && draft.name.startsWith("tts_reference_draft.")) {
                try {
                    val savedAudio = File(context.filesDir, "tts_reference.${draft.extension}")
                    draft.copyTo(savedAudio, overwrite = true)
                    ttsReferencePath = savedAudio.absolutePath
                    draft.delete()
                } catch (_: java.io.IOException) {
                    currentPage = SettingsPage.Voice
                    scope.launch { snackbarHostState.showSnackbar("参考音频保存失败，请重新选择") }
                    return false
                }
            }
        }
        prefsManager.apiBaseUrl = apiBaseUrl
        prefsManager.apiKey = apiKey
        prefsManager.model = model
        prefsManager.temperature = temperature
        prefsManager.maxTokens = maxTokens
        prefsManager.isFloatingEnabled = showFloatingEnabled
        prefsManager.showAiBubble = showAiBubble
        prefsManager.playSound = playSound
        prefsManager.aiBubbleDuration = aiBubbleDuration * 1000
        prefsManager.floatingWindowWidth = floatingWidth
        prefsManager.floatingWindowHeight = floatingHeight
        prefsManager.smileEnabled = smileEnabled
        prefsManager.smileProbability = smileProbability / 100f
        prefsManager.systemPrompt = systemPrompt
        prefsManager.memoryEnabled = memoryEnabled
        prefsManager.proactiveChatLevel = proactiveChatLevel
        prefsManager.proactiveChatMinInterval = proactiveChatMinInterval
        prefsManager.proactiveChatMaxInterval = proactiveChatMaxInterval
        prefsManager.selectedPetId = selectedPetId
        prefsManager.ttsEnabled = ttsEnabled
        prefsManager.ttsProvider = ttsProvider
        prefsManager.ttsBaseUrl = ttsBaseUrl
        prefsManager.ttsApiKey = ttsApiKey
        prefsManager.ttsModel = ttsModel
        prefsManager.ttsVoice = ttsVoice
        prefsManager.ttsReferencePath = ttsReferencePath
        prefsManager.thinkingEnabled = thinkingEnabled
        prefsManager.thinkingProtocol = thinkingProtocol
        prefsManager.foldThinking = foldThinking
        // Preference writes are not Compose state; request a refresh after saving.
        savedRevision++
        return true
    }

    fun navigateBack() {
        if (currentPage != SettingsPage.Home) {
            currentPage = SettingsPage.Home
        } else if (hasUnsavedChanges) {
            showLeaveDialog = true
        } else {
            onBack()
        }
    }

    BackHandler { navigateBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(currentPage.title) },
                navigationIcon = {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        if (hasUnsavedChanges) "有未保存的更改" else "设置已保存",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        enabled = hasUnsavedChanges,
                        onClick = {
                            if (saveSettings()) scope.launch {
                                snackbarHostState.showSnackbar("设置已保存")
                            }
                        }
                    ) { Text("保存设置") }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            transitionSpec = {
                val direction = if (targetState == SettingsPage.Home) -1 else 1
                (slideInHorizontally(tween(240)) { it * direction / 5 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it * direction / 6 } + fadeOut(tween(120)))
            },
            label = "设置二级页面"
        ) { page ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(pageScrollStates.getValue(page))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (page) {
                    SettingsPage.Home -> {
                        SettingsSection("角色与桌面", "角色外观、悬浮显示和互动方式") {
                            SettingsNavigationRow(
                                "角色与外观",
                                "${PetCatalog.get(selectedPetId).name} · ${floatingWidth} × ${floatingHeight} dp",
                                Icons.Default.Pets
                            ) { currentPage = SettingsPage.Appearance }
                            Divider()
                            SettingsNavigationRow(
                                "桌面交互",
                                if (showFloatingEnabled) "悬浮窗已启用 · 气泡、视频声音与待机动作" else "悬浮窗已关闭",
                                Icons.Default.TouchApp
                            ) { currentPage = SettingsPage.Desktop }
                        }
                        SettingsSection("对话与声音", "连接模型、设置人设和配置语音") {
                            SettingsNavigationRow(
                                "AI 服务",
                                if (apiKey.isBlank()) "待配置 · 接口、模型、生成参数与思考模式" else model.ifBlank { "尚未选择模型" },
                                Icons.Default.SmartToy
                            ) { currentPage = SettingsPage.Ai }
                            Divider()
                            SettingsNavigationRow(
                                "人设与对话",
                                "角色提示词、上下文记忆与主动搭话",
                                Icons.Default.ChatBubbleOutline
                            ) { currentPage = SettingsPage.Dialogue }
                            Divider()
                            SettingsNavigationRow(
                                "语音合成",
                                if (ttsEnabled) "已开启 · ${if (ttsProvider == "mimo") "MiMo 音色复刻" else "自定义 TTS"}" else "已关闭 · 服务商、音色与参考音频",
                                Icons.Default.VolumeUp
                            ) { currentPage = SettingsPage.Voice }
                        }
                        SettingsSection("应用信息") {
                            SettingsNavigationRow(
                                "关于与开源", "项目地址、代码许可与角色素材说明", Icons.Default.Info
                            ) { currentPage = SettingsPage.About }
                        }
                        Text(
                            "修改会保留在各页面中，点击底部“保存设置”后生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SettingsPage.Appearance -> {
                        SettingsSection("角色选择", "同一时间仅显示一个角色") {
                            PetCatalog.characters.forEach { pet ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedPetId = pet.id },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedPetId == pet.id, onClick = { selectedPetId = pet.id })
                                    Text(pet.name)
                                }
                            }
                        }
                        SettingsSection("悬浮窗尺寸", "调整角色在桌面的显示大小") {
                            Text(
                                text = "悬浮窗宽度: ${floatingWidth}dp",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = floatingWidth.toFloat(),
                                onValueChange = { floatingWidth = it.toInt() },
                                valueRange = 60f..300f,
                                steps = 23
                            )

                            Text(
                                text = "悬浮窗高度: ${floatingHeight}dp",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = floatingHeight.toFloat(),
                                onValueChange = { floatingHeight = it.toInt() },
                                valueRange = 80f..400f,
                                steps = 31
                            )
                        }
                    }
                    SettingsPage.Desktop -> {
                        SettingsSection("桌面显示", "悬浮窗、回复气泡与视频原声") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("启用悬浮窗")
                                Switch(
                                    checked = showFloatingEnabled,
                                    onCheckedChange = { showFloatingEnabled = it }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("显示AI回复气泡")
                                Switch(
                                    checked = showAiBubble,
                                    onCheckedChange = { showAiBubble = it }
                                )
                            }

                            if (showAiBubble) {
                                Text(
                                    text = "气泡显示时长: ${aiBubbleDuration}秒",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = aiBubbleDuration.toFloat(),
                                    onValueChange = { aiBubbleDuration = it.toInt() },
                                    valueRange = 2f..15f,
                                    steps = 12
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("播放视频声音")
                                Switch(
                                    checked = playSound,
                                    onCheckedChange = { playSound = it }
                                )
                            }
                        }
                        SettingsSection("待机互动") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("待机随机微笑")
                                Switch(
                                    checked = smileEnabled,
                                    onCheckedChange = { smileEnabled = it }
                                )
                            }

                            if (smileEnabled) {
                                OutlinedTextField(
                                    value = smileProbability.toString(),
                                    onValueChange = { value ->
                                        value.toIntOrNull()?.let {
                                            if (it in 0..100) smileProbability = it
                                        }
                                    },
                                    label = { Text("微笑概率 (0-100)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    suffix = { Text("%") }
                                )
                            }
                        }
                    }
                    SettingsPage.Ai -> {
                        SettingsSection("连接配置", "填写 OpenAI 兼容聊天接口") {
                            OutlinedTextField(
                                value = apiBaseUrl,
                                onValueChange = { apiBaseUrl = it },
                                label = { Text("API 地址") },
                                placeholder = { Text("https://api.openai.com/v1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showApiKey) "隐藏" else "显示"
                                        )
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                label = { Text("模型") },
                                placeholder = { Text("gpt-3.5-turbo") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        SettingsSection("生成参数") {
                            Text(
                                text = "Temperature: ${String.format("%.1f", temperature)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = temperature,
                                onValueChange = { temperature = it },
                                valueRange = 0f..2f,
                                steps = 19
                            )

                            Text(
                                text = "Max Tokens: $maxTokens",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = maxTokens.toFloat(),
                                onValueChange = { maxTokens = it.toInt() },
                                valueRange = 100f..4000f,
                                steps = 38
                            )
                        }
                        SettingsSection("思考模式", "控制模型是否请求思考，显示方式在人设与对话中设置") {
                            SettingSwitch("开启思考模式", thinkingEnabled) { thinkingEnabled = it }
                            AnimatedVisibility(thinkingEnabled) {
                                Column {
                                    Text("请求格式由模型供应商决定；不支持的模型可能拒绝该参数。", style = MaterialTheme.typography.bodySmall)
                                    listOf("reasoning_effort" to "reasoning_effort（OpenAI 兼容）", "enable_thinking" to "enable_thinking（兼容服务）").forEach { (value, label) ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { thinkingProtocol = value }) {
                                            RadioButton(selected = thinkingProtocol == value, onClick = { thinkingProtocol = value })
                                            Text(label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SettingsPage.Dialogue -> {
                        SettingsSection("人设与记忆") {
                            OutlinedTextField(
                                value = systemPrompt,
                                onValueChange = { systemPrompt = it },
                                label = { Text("系统提示词（AI人设）") },
                                placeholder = { Text("你是一个可爱活泼的桌面宠物...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("多轮对话（记忆上下文）")
                                Switch(
                                    checked = memoryEnabled,
                                    onCheckedChange = { memoryEnabled = it }
                                )
                            }
                        }
                        SettingsSection("思考过程显示") {
                            SettingSwitch("默认折叠思考过程", foldThinking) { foldThinking = it }
                            Text("只影响思考过程的显示，不影响 AI 服务中的思考模式开关。", style = MaterialTheme.typography.bodySmall)
                        }
                        SettingsSection("主动搭话", "选择频率，或设置自定义间隔") {
                            val proactiveChatLevelText = when (proactiveChatLevel) {
                                0 -> "从不打扰 (关闭)"
                                1 -> "偶尔 (约5-10分钟)"
                                2 -> "正常 (约2-5分钟)"
                                3 -> "话痨模式 (频繁)"
                                4 -> "自定义"
                                else -> "未知"
                            }
                            Text(
                                text = "主动搭话频率: $proactiveChatLevelText",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = proactiveChatLevel.toFloat(),
                                onValueChange = { proactiveChatLevel = it.toInt() },
                                valueRange = 0f..4f,
                                steps = 3
                            )

                            if (proactiveChatLevel == 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = proactiveChatMinInterval.toString(),
                                        onValueChange = { value ->
                                            value.toIntOrNull()?.let {
                                                if (it in 10..3600) proactiveChatMinInterval = it
                                            }
                                        },
                                        label = { Text("最小间隔(秒)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = proactiveChatMaxInterval.toString(),
                                        onValueChange = { value ->
                                            value.toIntOrNull()?.let {
                                                if (it in 10..3600) proactiveChatMaxInterval = it
                                            }
                                        },
                                        label = { Text("最大间隔(秒)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }
                            if (proactiveChatLevel == 4 && proactiveChatMinInterval > proactiveChatMaxInterval) {
                                Text("最小间隔不能大于最大间隔", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    SettingsPage.Voice -> {
                        SettingsSection("播放方式", "模型完成文字回复后，等待语音生成并播放") {
                            SettingSwitch("AI 回复后生成并播放语音", ttsEnabled) { ttsEnabled = it }
                        }
                        AnimatedVisibility(ttsEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SettingsSection("服务配置", "语音密钥独立于 AI 聊天密钥") {
                                    Text("语音服务", style = MaterialTheme.typography.labelLarge)
                                    listOf("mimo" to "MiMo 2.5 音色复刻", "custom" to "自定义 OpenAI 兼容 TTS").forEach { (value, label) ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                            ttsProvider = value
                                            if (value == "mimo") {
                                                ttsBaseUrl = "https://api.xiaomimimo.com/v1"
                                                ttsModel = "mimo-v2.5-tts-voiceclone"
                                            } else if (ttsModel.startsWith("mimo-")) {
                                                ttsBaseUrl = "https://api.openai.com/v1"
                                                ttsModel = "tts-1"
                                                ttsVoice = "alloy"
                                            }
                                        }) {
                                            RadioButton(selected = ttsProvider == value, onClick = {
                                                ttsProvider = value
                                                if (value == "mimo") {
                                                    ttsBaseUrl = "https://api.xiaomimimo.com/v1"
                                                    ttsModel = "mimo-v2.5-tts-voiceclone"
                                                } else if (ttsModel.startsWith("mimo-")) {
                                                    ttsBaseUrl = "https://api.openai.com/v1"
                                                    ttsModel = "tts-1"
                                                    ttsVoice = "alloy"
                                                }
                                            })
                                            Text(label)
                                        }
                                    }
                                    OutlinedTextField(value = ttsBaseUrl, onValueChange = { ttsBaseUrl = it }, label = { Text("TTS API 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = ttsApiKey, onValueChange = { ttsApiKey = it }, label = { Text("TTS API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                                    OutlinedTextField(value = ttsModel, onValueChange = { ttsModel = it }, label = { Text("TTS 模型") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                }
                                SettingsSection(if (ttsProvider == "mimo") "参考音频" else "音色") {
                                    if (ttsProvider == "mimo") {
                                        Text("MiMo 音色复刻需要 mp3/wav 参考音频，无需音频对应文字。", style = MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick = { referencePicker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav")) }) { Text("选择参考音频") }
                                        Text(if (ttsReferencePath.isBlank()) "尚未选择" else File(ttsReferencePath).name, style = MaterialTheme.typography.bodySmall)
                                        if (audioSelectionError.isNotBlank()) Text(audioSelectionError, color = MaterialTheme.colorScheme.error)
                                    } else {
                                        OutlinedTextField(value = ttsVoice, onValueChange = { ttsVoice = it }, label = { Text("音色 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                        Text("调用 API 地址下的 /audio/speech，接收 WAV 音频。", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    SettingsPage.About -> {
                        SettingsSection("掌上桌宠") {
                            Text("Android 桌面宠物，支持单角色 WebM 动画、AI 对话和语音合成。")
                            OutlinedButton(onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/telecom888/deskpet-mobile")))
                                } catch (_: android.content.ActivityNotFoundException) {
                                    scope.launch { snackbarHostState.showSnackbar("没有可打开链接的应用") }
                                }
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("查看开源项目")
                            }
                        }
                        SettingsSection("代码许可") {
                            Text("项目代码与文档采用 MIT 协议，完整文本见仓库 LICENSE。")
                        }
                        SettingsSection("角色素材") {
                            Text("角色 WebM 素材单独适用仓库 ASSETS.md 的说明，不属于 MIT 授权内容，不授予额外使用或再分发许可。")
                        }
                    }
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("保存设置后退出？") },
            text = { Text("有未保存的更改。返回二级页面时会保留草稿，离开设置前可以保存或放弃。") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    if (saveSettings()) onBack()
                }) { Text("保存并退出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showLeaveDialog = false }) { Text("继续编辑") }
                    TextButton(onClick = {
                        if (ttsReferencePath.isNotBlank()) {
                            val draft = File(ttsReferencePath)
                            if (draft.parentFile == context.filesDir && draft.name.startsWith("tts_reference_draft.")) draft.delete()
                        }
                        showLeaveDialog = false
                        onBack()
                    }) { Text("放弃更改") }
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
