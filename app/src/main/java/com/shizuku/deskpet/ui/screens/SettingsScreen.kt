package com.shizuku.deskpet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import com.shizuku.deskpet.data.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    onBack: () -> Unit
) {
    var apiBaseUrl by remember { mutableStateOf(prefsManager.apiBaseUrl) }
    var apiKey by remember { mutableStateOf(prefsManager.apiKey) }
    var model by remember { mutableStateOf(prefsManager.model) }
    var temperature by remember { mutableFloatStateOf(prefsManager.temperature) }
    var maxTokens by remember { mutableIntStateOf(prefsManager.maxTokens) }
    var showApiKey by remember { mutableStateOf(false) }
    var showFloatingEnabled by remember { mutableStateOf(prefsManager.isFloatingEnabled) }
    var showAiBubble by remember { mutableStateOf(prefsManager.showAiBubble) }
    var playSound by remember { mutableStateOf(prefsManager.playSound) }
    var aiBubbleDuration by remember { mutableIntStateOf(prefsManager.aiBubbleDuration / 1000) }
    var floatingWidth by remember { mutableIntStateOf(prefsManager.floatingWindowWidth) }
    var floatingHeight by remember { mutableIntStateOf(prefsManager.floatingWindowHeight) }
    var smileEnabled by remember { mutableStateOf(prefsManager.smileEnabled) }
    var smileProbability by remember { mutableStateOf((prefsManager.smileProbability * 100).toInt()) }
    var systemPrompt by remember { mutableStateOf(prefsManager.systemPrompt) }
    var memoryEnabled by remember { mutableStateOf(prefsManager.memoryEnabled) }
    var proactiveChatLevel by remember { mutableStateOf(prefsManager.proactiveChatLevel) }
    var proactiveChatMinInterval by remember { mutableStateOf(prefsManager.proactiveChatMinInterval) }
    var proactiveChatMaxInterval by remember { mutableStateOf(prefsManager.proactiveChatMaxInterval) }

    var showSaveSuccess by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AI 配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

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

            Divider()

            Text(
                text = "悬浮窗设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

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

            Divider()

            Button(
                onClick = {
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
                    showSaveSuccess = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }

            if (showSaveSuccess) {
                Text(
                    text = "设置已保存",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "开源项目",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/telecom888/deskpet-mobile"))
                        context.startActivity(intent)
                    }
            )
        }
    }
}
