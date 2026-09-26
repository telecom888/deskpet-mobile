package com.shizuku.deskpet.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shizuku.deskpet.data.PetCatalog
import com.shizuku.deskpet.data.PreferencesManager
import com.shizuku.deskpet.model.Conversation
import com.shizuku.deskpet.model.ConversationMessage
import com.shizuku.deskpet.model.ConversationSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(prefsManager: PreferencesManager, initialPetId: String? = null, onBack: () -> Unit) {
    val repository = prefsManager.conversations
    val revision by repository.changes.collectAsState()
    var petId by rememberSaveable { mutableStateOf(PetCatalog.get(initialPetId ?: prefsManager.selectedPetId).id) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRoleMenu by remember { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var newTitle by rememberSaveable { mutableStateOf("") }
    var deleteConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTurnId by rememberSaveable { mutableStateOf<String?>(null) }
    var memoryEnabled by remember { mutableStateOf(prefsManager.memoryEnabled) }
    var snapshot by remember { mutableStateOf(ConversationSnapshot(emptyList(), emptyMap())) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollHolder = rememberSaveableStateHolder()
    val pet = PetCatalog.get(petId)
    val mainId = snapshot.mainConversationIds[petId]
    val conversations = snapshot.conversations.filter { it.petId == petId }
        .sortedWith(compareByDescending<Conversation> { it.id == mainId }.thenByDescending { it.updatedAt })
    val detail = conversations.firstOrNull { it.id == detailId }

    LaunchedEffect(petId, revision, refresh) {
        loading = true
        try {
            snapshot = withContext(Dispatchers.IO) {
                repository.mainConversation(petId)
                repository.snapshot()
            }
            loadError = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadError = error.message ?: "无法读取对话记录"
        } finally {
            loading = false
        }
    }

    fun mutate(message: String, operation: () -> Unit, onSuccess: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                snapshot = withContext(Dispatchers.IO) { operation(); repository.snapshot() }
                onSuccess()
                busy = false
                snackbar.showSnackbar(message)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                snackbar.showSnackbar(error.message ?: "操作未完成，请重试")
            } finally {
                busy = false
            }
        }
    }

    BackHandler { if (detailId != null) detailId = null else onBack() }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: "对话历史", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (detailId != null) detailId = null else onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { newTitle = ""; showCreate = true }, enabled = !busy && loadError == null) {
                        Icon(Icons.Default.Add, contentDescription = "新建对话")
                    }
                    if (detail != null) {
                        IconButton(onClick = { deleteConversationId = detail.id; deleteTurnId = null }, enabled = !busy) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除这段对话")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (detail != null) Surface(shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pet.name} · 返回桌面后，消息会进入该角色的主对话。", style = MaterialTheme.typography.bodySmall)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = detail.id != mainId && !busy,
                        onClick = { mutate("已设为${pet.name}的主对话", { repository.setMainConversation(petId, detail.id) }) }
                    ) { Text(if (detail.id == mainId) "当前主对话" else "设为主对话") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (detailId == null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = { showRoleMenu = true }, enabled = !busy) {
                        Text("查看角色：${pet.name}")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showRoleMenu, onDismissRequest = { showRoleMenu = false }) {
                        PetCatalog.characters.forEach { role ->
                            DropdownMenuItem(text = { Text(role.name) }, onClick = { petId = role.id; showRoleMenu = false })
                        }
                    }
                }
            }
            if (!memoryEnabled) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("多轮上下文已关闭，消息仍会保存。开启后，模型会读取主对话中已完成的问答。", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { prefsManager.memoryEnabled = true; memoryEnabled = true }) { Text("开启多轮上下文") }
                    }
                }
            }
            when {
                loadError != null -> {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(loadError!!, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { refresh++ }) { Text("重新读取") }
                    }
                }
                loading && conversations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                detailId == null -> {
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("每个角色分别选择一条主对话。点击卡片查看记录，不会自动更换主对话。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(conversations, key = { it.id }) { conversation ->
                            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable { detailId = conversation.id }) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(conversation.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (conversation.id == mainId) MainConversationLabel()
                                        IconButton(onClick = { deleteConversationId = conversation.id; deleteTurnId = null }, enabled = !busy) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除${conversation.title}")
                                        }
                                    }
                                    Text(
                                        conversation.messages.lastOrNull { it.content.isNotBlank() }?.content ?: "暂无消息",
                                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text("${historyTime(conversation.updatedAt)} · ${conversation.messages.count { it.content.isNotBlank() }} 条消息", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { detailId = conversation.id }) { Text("查看记录") }
                                        if (conversation.id != mainId) TextButton(
                                            enabled = !busy,
                                            onClick = { mutate("已设为${pet.name}的主对话", { repository.setMainConversation(petId, conversation.id) }) }
                                        ) { Text("设为主对话") }
                                    }
                                }
                            }
                        }
                    }
                }
                detail != null -> {
                    if (detail.messages.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("暂无消息。设为主对话后，返回桌面双击${pet.name}开始聊天。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else scrollHolder.SaveableStateProvider(detail.id) {
                        ConversationMessages(
                            detail, pet.name, prefsManager.foldThinking, busy,
                            onDeleteTurn = { deleteConversationId = detail.id; deleteTurnId = it }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) AlertDialog(
        onDismissRequest = { if (!busy) showCreate = false },
        title = { Text("新建${pet.name}的对话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("新建后设为该角色的主对话，已有记录继续保留。")
                OutlinedTextField(newTitle, onValueChange = { newTitle = it.take(50) }, label = { Text("对话名称（可不填）") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                var createdId: String? = null
                mutate("已新建主对话", { createdId = repository.createConversation(petId, newTitle).id }) {
                    showCreate = false
                    detailId = createdId
                }
            }) { Text("新建") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { showCreate = false }) { Text("取消") } }
    )

    if (deleteConversationId != null) AlertDialog(
        onDismissRequest = { if (!busy) { deleteConversationId = null; deleteTurnId = null } },
        title = { Text(if (deleteTurnId == null) "删除这段对话？" else "删除这轮问答？") },
        text = { Text(if (deleteTurnId == null) "将删除整段对话中的所有记录。若它是主对话，会自动选择另一条；没有剩余对话时创建空白主对话。此操作无法撤销。" else "将一起删除本轮用户消息、模型回复及思考过程；主动搭话只删除该条回复。此操作无法撤销。") },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val conversationId = deleteConversationId ?: return@TextButton
                val turnId = deleteTurnId
                mutate("记录已删除", {
                    if (turnId == null) repository.deleteConversation(petId, conversationId)
                    else repository.deleteTurn(petId, conversationId, turnId)
                }) {
                    if (turnId == null && detailId == conversationId) detailId = null
                    deleteConversationId = null
                    deleteTurnId = null
                }
            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleteConversationId = null; deleteTurnId = null }) { Text("取消") } }
    )
}

@Composable
private fun MainConversationLabel() {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text("主对话", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ConversationMessages(
    conversation: Conversation,
    petName: String,
    foldThinking: Boolean,
    busy: Boolean,
    onDeleteTurn: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var positioned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(conversation.id) {
        if (!positioned) {
            listState.scrollToItem(conversation.messages.lastIndex.coerceAtLeast(0))
            positioned = true
        }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(conversation.messages, key = { it.id }) { message ->
            HistoryMessage(message, petName, foldThinking, busy, onDeleteTurn)
        }
    }
}

@Composable
private fun HistoryMessage(message: ConversationMessage, petName: String, foldThinking: Boolean, busy: Boolean, onDeleteTurn: (String) -> Unit) {
    val own = message.role == "user"
    var reasoningExpanded by rememberSaveable(message.id) { mutableStateOf(!foldThinking) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f), shape = RoundedCornerShape(18.dp),
            color = if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (own) "我" else petName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    if (message.proactive) Text("主动搭话", style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { onDeleteTurn(message.turnId) }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除本轮问答", modifier = Modifier.size(18.dp))
                    }
                }
                if (message.reasoning.isNotBlank()) {
                    TextButton(onClick = { reasoningExpanded = !reasoningExpanded }) { Text(if (reasoningExpanded) "收起思考过程" else "查看思考过程") }
                    if (reasoningExpanded) SelectionContainer { Text(message.reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val text = when (message.status) {
                    "pending" -> "回复尚未完成"
                    "failed" -> "生成失败，原消息保留在记录中"
                    "cancelled" -> "回复已停止"
                    else -> message.content
                }
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
                Text(historyTime(message.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun historyTime(timestamp: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
