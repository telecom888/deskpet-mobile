package com.shizuku.deskpet.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.shizuku.deskpet.R
import com.shizuku.deskpet.data.AiClient
import com.shizuku.deskpet.data.PetCatalog
import com.shizuku.deskpet.data.PreferencesManager
import com.shizuku.deskpet.data.TtsClient
import com.shizuku.deskpet.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.random.Random

class FloatingView(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var windowParams: WindowManager.LayoutParams? = null
    private var rootView: View? = null
    private var webView: WebView? = null
    private var bubbleWindow: PopupWindow? = null
    private var currentBubbleTextView: TextView? = null
    private var reasoningTextView: TextView? = null
    private var reasoningToggle: TextView? = null
    private var reasoningScroll: ScrollView? = null
    private var ttsClient: TtsClient? = null
    private var requestJob: Job? = null
    private var ttsPlaying = false
    private val bubbleHandler = Handler(Looper.getMainLooper())
    private val dismissBubbleRunnable = Runnable {
        hideAiBubble()
        if (currentState == PetState.TALKING) updateState(PetState.IDLE)
    }
    private var statusIndicator: ImageView? = null

    private lateinit var prefsManager: PreferencesManager
    private var aiClient: AiClient? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isDragging = false
    private var currentState = PetState.IDLE
    private var isAnimating = false
    private var isVideoPrepared = false
    private var isWebViewInitialized = false

    private val edgeThreshold = 100
    private var screenWidth = 0

    private val gestureDetector: GestureDetector

    private var onStateChanged: ((PetState) -> Unit)? = null

    private var pendingVideoState: PetState? = null
    private val videoChangeHandler = Handler(Looper.getMainLooper())
    private val videoChangeRunnable = Runnable {
        pendingVideoState?.let { playVideo(it) }
        pendingVideoState = null
    }

    private var isSmileCheckEnabled = false
    private val smileCheckHandler = Handler(Looper.getMainLooper())
    
    private val revertSmileRunnable = Runnable {
        if (currentState == PetState.SMILE) {
            updateState(PetState.IDLE)
        }
    }

    private val smileCheckRunnable = object : Runnable {
        override fun run() {
            if (!isSmileCheckEnabled) return
            
            if (currentState == PetState.IDLE && prefsManager.smileEnabled) {
                val random = Random.nextFloat()
                if (random < prefsManager.smileProbability) {
                    updateState(PetState.SMILE)
                    
                    videoChangeHandler.removeCallbacks(revertSmileRunnable)
                    videoChangeHandler.postDelayed(revertSmileRunnable, 5000)
                }
            }
            
            smileCheckHandler.postDelayed(this, 3000)
        }
    }

    private var isProactiveChatEnabled = false
    private val proactiveChatHandler = Handler(Looper.getMainLooper())
    private val proactiveChatRunnable = object : Runnable {
        override fun run() {
            if (!isProactiveChatEnabled) return

            val level = prefsManager.proactiveChatLevel

            if (level == 0) {
                proactiveChatHandler.postDelayed(this, 5000)
                return
            }

            var minDelay = 60000L
            var maxDelay = 120000L
            var probability = 0.5f

            when (level) {
                1 -> {
                    minDelay = 5 * 60 * 1000L
                    maxDelay = 10 * 60 * 1000L
                    probability = 0.3f
                }
                2 -> {
                    minDelay = 2 * 60 * 1000L
                    maxDelay = 5 * 60 * 1000L
                    probability = 0.5f
                }
                3 -> {
                    minDelay = 30 * 1000L
                    maxDelay = 60 * 1000L
                    probability = 0.8f
                }
                4 -> {
                    minDelay = prefsManager.proactiveChatMinInterval * 1000L
                    maxDelay = prefsManager.proactiveChatMaxInterval * 1000L
                    probability = 0.5f
                }
            }

            if (currentState == PetState.IDLE && bubbleWindow == null) {
                val random = Random.nextFloat()
                if (random < probability) {
                    triggerProactiveChat()
                }
            }

            val nextIntervalMs = Random.nextLong(minDelay, maxDelay)
            proactiveChatHandler.postDelayed(this, nextIntervalMs)
        }
    }

    enum class PetState {
        IDLE,
        TALKING,
        SMILE,
        DRAGGING
    }

    private fun triggerProactiveChat() {
        showLoading()
        updateState(PetState.SMILE)

        requestJob = coroutineScope.launch {
            val client = aiClient
            if (client == null || prefsManager.apiKey.isBlank()) {
                hideLoading()
                updateState(PetState.IDLE)
                return@launch
            }

            updateState(PetState.TALKING)
            if (prefsManager.showAiBubble) initAiBubble()
            
            val stringBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            var isFirstChunk = true

            try {
                client.sendMessageStream("", isProactive = true)
                    .collect { chunk ->
                        if (isFirstChunk) {
                            hideLoading()
                            isFirstChunk = false
                        }
                        when (chunk) {
                            is AiClient.StreamEvent.Answer -> {
                                stringBuilder.append(chunk.text)
                                if (prefsManager.showAiBubble) updateAiBubbleText(stringBuilder.toString())
                            }
                            is AiClient.StreamEvent.Reasoning -> {
                                reasoningBuilder.append(chunk.text)
                                if (prefsManager.showAiBubble) updateReasoning(reasoningBuilder.toString())
                            }
                        }
                    }
                hideLoading()
                speakAnswer(stringBuilder.toString())
                finishAiBubble(prefsManager.aiBubbleDuration)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hideLoading()
                hideAiBubble()
                updateState(PetState.IDLE)
            }
        }
    }

    init {
        screenWidth = context.resources.displayMetrics.widthPixels
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                showInputDialog()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                return true
            }
        })
    }

    fun show() {
        if (rootView != null) return

        prefsManager = PreferencesManager(context)
        aiClient = AiClient(prefsManager)
        ttsClient = TtsClient(context, prefsManager)

        rootView = LayoutInflater.from(context).inflate(R.layout.floating_window, null)
        
        webView = rootView?.findViewById(R.id.web_view)
        setupWebView()
        
        statusIndicator = rootView?.findViewById(R.id.status_indicator)

        setupTouchListener()

        val widthPx = dpToPx(prefsManager.floatingWindowWidth)
        val heightPx = dpToPx(prefsManager.floatingWindowHeight)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - widthPx
            y = dpToPx(200)
        }

        windowManager.addView(rootView, windowParams)

        rootView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        playVideo(PetState.IDLE)
        
        isSmileCheckEnabled = true
        smileCheckHandler.post(smileCheckRunnable)
        
        isProactiveChatEnabled = true
        proactiveChatHandler.postDelayed(proactiveChatRunnable, 20000)
    }

    private fun setupWebView() {
        webView?.apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                mediaPlaybackRequiresUserGesture = false
            }
        }
    }

    private fun playVideo(state: PetState) {
        val videoFileName = PetCatalog.assetPath(prefsManager.selectedPetId, when (state) {
            PetState.IDLE -> "standby"
            PetState.TALKING -> "speak"
            PetState.SMILE -> "smile"
            PetState.DRAGGING -> "touch"
        })

        val isMuted = !prefsManager.playSound || ttsPlaying

        if (!isWebViewInitialized) {
            val initialVideo = PetCatalog.assetPath(prefsManager.selectedPetId, "standby")
            
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        body {
                            margin: 0;
                            padding: 0;
                            background-color: transparent;
                            overflow: hidden;
                            -webkit-tap-highlight-color: transparent;
                        }
                        video {
                            position: absolute;
                            top: 0;
                            left: 0;
                            width: 100vw;
                            height: 100vh;
                            object-fit: contain;
                            object-position:bottom;
                            background-color: transparent;
                            outline: none;
                            transition: opacity 0.1s;
                            z-index: 1;
                        }
                        #video2 {
                            z-index: 2;
                            opacity: 0;
                        }
                        video::-webkit-media-controls { display: none !important; }
                        video::-webkit-media-controls-enclosure { display: none !important; }
                        video::-webkit-media-controls-panel { display: none !important; }
                        video::-webkit-media-controls-play-button { display: none !important; }
                        video::-webkit-media-controls-start-playback-button {
                            display: none !important;
                            opacity: 0 !important;
                            -webkit-appearance: none;
                        }
                    </style>
                </head>
                <body>
                    <video id="video1" poster="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7" src="$initialVideo" autoplay loop playsinline ${if (isMuted) "muted" else ""}></video>
                    <video id="video2" poster="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7" autoplay loop playsinline style="opacity: 0;"></video>
                    
                    <script>
                        const v1 = document.getElementById('video1');
                        const v2 = document.getElementById('video2');
                        let currentV = v1;
                        let nextV = v2;
                        let lastSrc = '$initialVideo';
                        
                        function changeVideo(newSrc, muted) {
                            if (newSrc === lastSrc) {
                                currentV.muted = muted;
                                return;
                            }
                            lastSrc = newSrc;
                            
                            nextV.muted = muted;
                            nextV.src = newSrc;
                            nextV.load();
                            
                            nextV.oncanplay = function() {
                                this.oncanplay = null;
                                
                                currentV.pause();
                                this.play().catch(() => {});
                                
                                this.style.zIndex = 2;
                                this.style.opacity = 1;
                                
                                currentV.style.zIndex = 1;
                                currentV.style.opacity = 0;
                                
                                let temp = currentV;
                                currentV = this;
                                nextV = temp;
                            };
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()

            webView?.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )
            isWebViewInitialized = true
        } else {
            val jsCommand = "javascript:changeVideo('$videoFileName', $isMuted);"
            webView?.evaluateJavascript(jsCommand, null)
        }

        currentState = state
        onStateChanged?.invoke(state)
        isVideoPrepared = true
    }

    fun hide() {
        requestJob?.cancel()
        coroutineScope.cancel()
        ttsClient?.stop()
        videoChangeHandler.removeCallbacks(videoChangeRunnable)
        videoChangeHandler.removeCallbacks(revertSmileRunnable)
        smileCheckHandler.removeCallbacks(smileCheckRunnable)
        isSmileCheckEnabled = false
        
        proactiveChatHandler.removeCallbacks(proactiveChatRunnable)
        isProactiveChatEnabled = false
        
        webView?.apply {
            loadUrl("about:blank")
            destroy()
        }
        webView = null
        isWebViewInitialized = false
        
        hideAiBubble()
        rootView?.let {
            windowManager.removeView(it)
            rootView = null
        }
    }

    private fun setupTouchListener() {
        val touchListener = View.OnTouchListener { _, event ->
            if (isAnimating) return@OnTouchListener true

            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams?.x ?: 0
                    initialY = windowParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = true

                    if (currentState == PetState.IDLE || currentState == PetState.SMILE || 
                        currentState == PetState.TALKING) {
                        scheduleVideoChange(PetState.DRAGGING)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        windowParams?.x = initialX + deltaX
                        windowParams?.y = initialY + deltaY
                        windowManager.updateViewLayout(rootView, windowParams)
                        updateBubblePosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    if (currentState == PetState.DRAGGING || pendingVideoState == PetState.DRAGGING) {
                        scheduleVideoChange(PetState.IDLE)
                    }
                    true
                }
                else -> false
            }
        }

        rootView?.setOnTouchListener(touchListener)
        webView?.setOnTouchListener(touchListener)
    }

    private fun scheduleVideoChange(state: PetState) {
        pendingVideoState = state
        videoChangeHandler.removeCallbacks(videoChangeRunnable)
        videoChangeHandler.postDelayed(videoChangeRunnable, 100)
    }

    private fun updateState(state: PetState) {
        currentState = state
        playVideo(state)
        onStateChanged?.invoke(state)
    }

    fun getState(): PetState = currentState

    fun setOnStateChangedListener(listener: (PetState) -> Unit) {
        onStateChanged = listener
    }

    fun showAiBubble(text: String, durationMs: Int = 5000) {
        hideAiBubble()

        val bubbleView = LayoutInflater.from(context).inflate(R.layout.bubble_popup, null)
        val bubbleTextView = bubbleView.findViewById<TextView>(R.id.bubble_text)
        bubbleTextView.text = text

        bubbleWindow = PopupWindow(
            bubbleView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isTouchable = true
            setBackgroundDrawable(null)
        }

        showBubbleAtPosition()
        updateBubblePosition()

        bubbleHandler.removeCallbacks(dismissBubbleRunnable)
        bubbleHandler.postDelayed(dismissBubbleRunnable, durationMs.toLong())
    }

    fun initAiBubble() {
        hideAiBubble()
        val bubbleView = LayoutInflater.from(context).inflate(R.layout.bubble_popup, null)
        currentBubbleTextView = bubbleView.findViewById(R.id.bubble_text)
        reasoningTextView = bubbleView.findViewById(R.id.reasoning_text)
        reasoningToggle = bubbleView.findViewById(R.id.reasoning_toggle)
        reasoningScroll = bubbleView.findViewById(R.id.reasoning_scroll)
        reasoningToggle?.setOnClickListener {
            val expanded = reasoningScroll?.visibility != View.VISIBLE
            reasoningScroll?.visibility = if (expanded) View.VISIBLE else View.GONE
            reasoningToggle?.text = if (expanded) "思考过程 ▾" else "思考过程 ▸"
            updateBubblePosition()
        }
        currentBubbleTextView?.text = "思考中..."

        bubbleWindow = PopupWindow(
            bubbleView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isTouchable = true
            setBackgroundDrawable(null)
        }
        showBubbleAtPosition()
        updateBubblePosition()
    }

    fun updateAiBubbleText(text: String) {
        currentBubbleTextView?.text = text
        updateBubblePosition()
    }

    private fun updateReasoning(text: String) {
        reasoningTextView?.text = text
        reasoningToggle?.visibility = View.VISIBLE
        if (!prefsManager.foldThinking) {
            reasoningScroll?.visibility = View.VISIBLE
            reasoningToggle?.text = "思考过程 ▾"
        }
        updateBubblePosition()
    }

    private suspend fun speakAnswer(text: String) {
        if (!prefsManager.ttsEnabled || text.isBlank()) return
        try {
            ttsPlaying = true
            playVideo(PetState.TALKING)
            ttsClient?.synthesizeAndPlay(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (prefsManager.showAiBubble) updateAiBubbleText("$text\n\n语音播放失败：${e.message}")
        } finally {
            ttsPlaying = false
            playVideo(PetState.TALKING)
        }
    }

    fun finishAiBubble(durationMs: Int = 5000) {
        bubbleHandler.removeCallbacks(dismissBubbleRunnable)
        bubbleHandler.postDelayed(dismissBubbleRunnable, durationMs.toLong())
    }

    // 调整气泡窗口y轴偏移量
    private val bubbleYOffsetDp = 150

    private fun showBubbleAtPosition() {
        if (bubbleWindow == null || rootView == null || windowParams == null) return

        val x = windowParams!!.x
        val rootHeight = rootView?.height ?: 0
        // val y = windowParams!!.y + rootHeight
        val y = windowParams!!.y + rootHeight - dpToPx(bubbleYOffsetDp)

        bubbleWindow?.showAtLocation(
            rootView,
            Gravity.TOP or Gravity.START,
            x,
            y
        )
    }

    fun updateBubblePosition() {
        if (bubbleWindow == null || rootView == null || !bubbleWindow!!.isShowing || windowParams == null) return

        val x = windowParams!!.x
        val rootHeight = rootView?.height ?: 0
        // val y = windowParams!!.y + rootHeight
        val y = windowParams!!.y + rootHeight - dpToPx(bubbleYOffsetDp)

        bubbleWindow?.update(x, y, -1, -1)
    }

    fun hideAiBubble() {
        bubbleHandler.removeCallbacks(dismissBubbleRunnable)
        bubbleWindow?.dismiss()
        bubbleWindow = null
        currentBubbleTextView = null
        reasoningTextView = null
        reasoningToggle = null
        reasoningScroll = null
    }

    fun showLoading() {
        statusIndicator?.setImageResource(R.drawable.ic_loading)
        statusIndicator?.visibility = View.VISIBLE
    }

    fun hideLoading() {
        statusIndicator?.visibility = View.GONE
    }

    private fun showInputDialog() {
        val petView = rootView ?: return
        val location = IntArray(2)
        petView.getLocationOnScreen(location)
        try {
            context.startActivity(android.content.Intent(context, com.shizuku.deskpet.ui.ChatInputActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(com.shizuku.deskpet.ui.ChatInputActivity.EXTRA_PET_ID, prefsManager.selectedPetId)
                putExtra(com.shizuku.deskpet.ui.ChatInputActivity.EXTRA_ANCHOR_X, location[0])
                putExtra(com.shizuku.deskpet.ui.ChatInputActivity.EXTRA_ANCHOR_Y, location[1] + petView.height)
            })
        } catch (_: android.content.ActivityNotFoundException) {
            showAiBubble("聊天窗口无法打开，请重新启动应用。")
        }
    }

    fun sendMessage(message: String) {
        val sendingPetId = prefsManager.selectedPetId

        fun restoreFailedDraft() {
            if (prefsManager.inputDraft(sendingPetId).isBlank()) prefsManager.saveInputDraft(sendingPetId, message)
        }

        requestJob?.cancel()
        ttsClient?.stop()
        showLoading()
        updateState(PetState.SMILE)

        requestJob = coroutineScope.launch {
            val client = aiClient
            if (client == null) {
                restoreFailedDraft()
                hideLoading()
                initAiBubble()
                updateAiBubbleText("请先在设置中配置API Key")
                finishAiBubble(5000)
                updateState(PetState.IDLE)
                return@launch
            }

            if (prefsManager.apiKey.isBlank()) {
                restoreFailedDraft()
                hideLoading()
                initAiBubble()
                updateAiBubbleText("请先在设置中配置API Key")
                finishAiBubble(5000)
                updateState(PetState.IDLE)
                return@launch
            }

            updateState(PetState.TALKING)
            if (prefsManager.showAiBubble) initAiBubble()
            
            val stringBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            var isFirstChunk = true

            try {
                client.sendMessageStream(message)
                    .collect { chunk ->
                        if (isFirstChunk) {
                            hideLoading()
                            isFirstChunk = false
                        }

                        when (chunk) {
                            is AiClient.StreamEvent.Answer -> {
                                stringBuilder.append(chunk.text)
                                if (prefsManager.showAiBubble) updateAiBubbleText(stringBuilder.toString())
                            }
                            is AiClient.StreamEvent.Reasoning -> {
                                reasoningBuilder.append(chunk.text)
                                if (prefsManager.showAiBubble) updateReasoning(reasoningBuilder.toString())
                            }
                        }
                    }
                hideLoading()
                speakAnswer(stringBuilder.toString())
                finishAiBubble(prefsManager.aiBubbleDuration)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hideLoading()
                restoreFailedDraft()
                if (!prefsManager.showAiBubble) initAiBubble()
                updateAiBubbleText("发送失败，消息已恢复为草稿：${e.message}")
                finishAiBubble(5000)
                updateState(PetState.IDLE)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        this.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) { action() }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }
}
