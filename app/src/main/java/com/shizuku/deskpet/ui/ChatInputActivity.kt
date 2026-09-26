package com.shizuku.deskpet.ui

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shizuku.deskpet.MainActivity
import com.shizuku.deskpet.data.PetCatalog
import com.shizuku.deskpet.data.PreferencesManager
import com.shizuku.deskpet.service.FloatingService
import com.shizuku.deskpet.ui.theme.DeskpetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ChatInputActivity : ComponentActivity() {
    private lateinit var prefs: PreferencesManager
    private lateinit var petId: String
    private var currentDraft = ""
    private var messageSubmitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = PreferencesManager(this)
        petId = savedInstanceState?.getString(EXTRA_PET_ID)
            ?: intent.getStringExtra(EXTRA_PET_ID) ?: prefs.selectedPetId
        currentDraft = prefs.inputDraft(petId)
        setContent {
            DeskpetTheme {
                ChatInputCard(
                    petId = petId,
                    compact = prefs.inputDialogStyle == "compact",
                    anchorX = intent.getIntExtra(EXTRA_ANCHOR_X, 0),
                    anchorY = intent.getIntExtra(EXTRA_ANCHOR_Y, 0),
                    initialDraft = currentDraft,
                    configured = prefs.isConfigured(),
                    onDraftChange = { currentDraft = it },
                    onSend = { text ->
                        try {
                            startService(Intent(this, FloatingService::class.java).apply {
                                action = FloatingService.ACTION_SEND_MESSAGE
                                putExtra(FloatingService.EXTRA_MESSAGE, text)
                            })
                            currentDraft = ""
                            messageSubmitted = true
                            prefs.saveInputDraft(petId, "")
                            true
                        } catch (_: IllegalStateException) {
                            false
                        } catch (_: SecurityException) {
                            false
                        }
                    },
                    onOpenSettings = {
                        prefs.saveInputDraft(petId, currentDraft)
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("open_ai_settings", true)
                        })
                        finish()
                    },
                    onOpenHistory = {
                        prefs.saveInputDraft(petId, currentDraft)
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("open_conversations", true)
                            putExtra("history_pet_id", petId)
                        })
                        finish()
                    },
                    onDismiss = {
                        if (!messageSubmitted) prefs.saveInputDraft(petId, currentDraft)
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                )
            }
        }
    }

    override fun onStop() {
        if (::prefs.isInitialized && !messageSubmitted) prefs.saveInputDraft(petId, currentDraft)
        super.onStop()
        // Reopen against the latest role and style after returning to the desktop.
        if (!isChangingConfigurations && !isFinishing) finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_PET_ID, petId)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_PET_ID = "pet_id"
        const val EXTRA_ANCHOR_X = "anchor_x"
        const val EXTRA_ANCHOR_Y = "anchor_y"
        private val portraits = LruCache<String, Bitmap>(5)
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
    @Composable
    private fun ChatInputCard(
        petId: String,
        compact: Boolean,
        anchorX: Int,
        anchorY: Int,
        initialDraft: String,
        configured: Boolean,
        onDraftChange: (String) -> Unit,
        onSend: (String) -> Boolean,
        onOpenSettings: () -> Unit,
        onOpenHistory: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val pet = PetCatalog.get(petId)
        var draft by rememberSaveable { mutableStateOf(initialDraft) }
        var visible by remember { mutableStateOf(false) }
        var closing by remember { mutableStateOf(false) }
        var sendError by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val imeVisible = WindowInsets.isImeVisible
        val scrimAlpha by animateFloatAsState(if (visible) 0.18f else 0f, tween(220), label = "背景淡入")
        val portrait by produceState<Bitmap?>(initialValue = portraits.get(petId), key1 = petId) {
            if (value == null) value = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        assets.openFd("pets/${pet.id}/standby.webm").use { asset ->
                            retriever.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                                val scaled = Bitmap.createScaledBitmap(frame, 128, (frame.height * 128 / frame.width).coerceAtLeast(1), true)
                                if (frame !== scaled) frame.recycle()
                                portraits.put(petId, scaled)
                                scaled
                            }
                        }
                    } finally {
                        retriever.release()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        fun close() {
            if (closing) return
            closing = true
            keyboard?.hide()
            visible = false
            scope.launch { delay(220); onDismiss() }
        }

        fun send() {
            if (closing || draft.isBlank() || !configured) return
            if (onSend(draft.trim())) {
                draft = ""
                onDraftChange("")
                close()
            } else {
                sendError = true
            }
        }

        LaunchedEffect(Unit) {
            visible = true
            delay(240)
            if (!closing) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
        // Restore the latest saveable draft after configuration changes.
        LaunchedEffect(draft) { onDraftChange(draft) }
        BackHandler {
            if (imeVisible) keyboard?.hide() else close()
        }

        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(remember { MutableInteractionSource() }, indication = null) { close() }
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                var origin by remember { mutableStateOf(IntOffset.Zero) }
                var cardSize by remember { mutableStateOf(IntSize.Zero) }
                val widthPx = with(density) { maxWidth.roundToPx() }
                val heightPx = with(density) { maxHeight.roundToPx() }
                val heightLimit = maxHeight
                val cardWidth = minOf(maxWidth, if (compact) 280.dp else 480.dp)
                Box(Modifier.fillMaxSize().onGloballyPositioned {
                    val position = it.positionInWindow()
                    origin = IntOffset(position.x.roundToInt(), position.y.roundToInt())
                }) {
                    val placement = if (compact) Modifier.align(Alignment.TopStart).offset {
                        IntOffset(
                            (anchorX - origin.x).coerceIn(0, (widthPx - cardSize.width).coerceAtLeast(0)),
                            (anchorY - origin.y).coerceIn(0, (heightPx - cardSize.height).coerceAtLeast(0))
                        )
                    } else Modifier.align(Alignment.BottomCenter)
                    AnimatedVisibility(
                        visible = visible,
                        modifier = placement,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 },
                        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 12 }
                    ) {
                        Surface(
                            modifier = Modifier.width(cardWidth).heightIn(max = heightLimit)
                                .onSizeChanged { cardSize = it }
                                .clickable(remember { MutableInteractionSource() }, indication = null) {},
                            shape = RoundedCornerShape(24.dp),
                            tonalElevation = 3.dp,
                            shadowElevation = 12.dp
                        ) {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                                        val image = portrait
                                        if (image != null) {
                                            Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop)
                                        } else Box(contentAlignment = Alignment.Center) {
                                            Text(pet.name.take(1), style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("和${pet.name}聊聊", style = MaterialTheme.typography.titleMedium)
                                        Text("有什么想告诉她的？", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { close() }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "关闭聊天窗口")
                                    }
                                }
                                OutlinedTextField(
                                    value = draft,
                                    enabled = !closing,
                                    onValueChange = { draft = it; onDraftChange(it); sendError = false },
                                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onPreviewKeyEvent {
                                        if (it.key == Key.Enter && it.isCtrlPressed) {
                                            if (it.type == KeyEventType.KeyUp) send()
                                            true
                                        } else false
                                    },
                                    placeholder = { Text("输入消息……") },
                                    minLines = 2,
                                    maxLines = if (compact) 4 else 6,
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                )
                                if (!configured) {
                                    Text("尚未配置 AI 服务，配置后即可发送消息。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = onOpenSettings, enabled = !closing) { Text("前往 AI 服务设置") }
                                }
                                if (sendError) Text("发送未成功，消息已保留，请重试。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = onOpenHistory, enabled = !closing) {
                                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("历史")
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Button(onClick = { send() }, enabled = draft.isNotBlank() && configured && !closing, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("发送")
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
