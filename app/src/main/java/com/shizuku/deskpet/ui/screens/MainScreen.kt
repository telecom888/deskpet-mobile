package com.shizuku.deskpet.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shizuku.deskpet.data.PreferencesManager
import com.shizuku.deskpet.data.PetCatalog
import com.shizuku.deskpet.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefsManager: PreferencesManager,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    val revision by prefsManager.conversations.changes.collectAsState()
    var mainConversation by remember { mutableStateOf<Conversation?>(null) }
    var historyError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revision, prefsManager.selectedPetId) {
        try {
            mainConversation = withContext(Dispatchers.IO) { prefsManager.conversations.mainConversation(prefsManager.selectedPetId) }
            historyError = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            historyError = error.message ?: "对话记录无法读取"
        }
    }

    LaunchedEffect(Unit) {
        hasOverlayPermission = checkOverlayPermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("掌上桌宠") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "对话历史")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "当前角色 · ${PetCatalog.get(prefsManager.selectedPetId).name}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "返回桌面，和角色继续聊天。一次只会显示一位桌宠。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(onClick = onNavigateToSettings) { Text("更换角色与设置") }
                    TextButton(onClick = onNavigateToHistory) { Text("查看对话历史") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("主对话", style = MaterialTheme.typography.titleMedium)
                    Text(historyError ?: mainConversation?.title ?: "正在读取……")
                    Text("新消息与主动搭话保存在当前角色的主对话中。", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onNavigateToHistory) { Text("管理对话与新建") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("运行状态", style = MaterialTheme.typography.titleMedium)
                    Text(if (hasOverlayPermission) "悬浮窗权限已开启" else "需要悬浮窗权限", color = if (hasOverlayPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(if (prefsManager.isConfigured()) "AI 服务已配置" else "尚未配置 AI 服务")
                    Text(if (prefsManager.ttsEnabled) "语音播放已开启" else "语音播放已关闭")
                    if (!hasOverlayPermission) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        ) { Text("申请悬浮窗权限") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("操作说明", style = MaterialTheme.typography.titleMedium)
                    Text("拖动角色来调整位置")
                    Text("双击角色输入消息")
                    Text("AI 回复完成后可播放角色语音")
                }
            }

            if (!prefsManager.isConfigured()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "请先配置AI API",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onNavigateToSettings) {
                            Text("去设置")
                        }
                    }
                }
            }
        }
    }
}

private fun checkOverlayPermission(context: android.content.Context): Boolean {
    return Settings.canDrawOverlays(context)
}
