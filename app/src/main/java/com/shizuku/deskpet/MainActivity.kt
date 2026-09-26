package com.shizuku.deskpet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.shizuku.deskpet.data.PreferencesManager
import com.shizuku.deskpet.service.FloatingService
import com.shizuku.deskpet.ui.screens.MainScreen
import com.shizuku.deskpet.ui.screens.SettingsScreen
import com.shizuku.deskpet.ui.screens.ConversationScreen
import com.shizuku.deskpet.ui.theme.DeskpetTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PreferencesManager

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefsManager = PreferencesManager(this)

        setContent {
            DeskpetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by rememberSaveable {
                        mutableStateOf(when {
                            intent.getBooleanExtra("open_ai_settings", false) -> Screen.Settings
                            intent.getBooleanExtra("open_conversations", false) -> Screen.Conversations
                            else -> Screen.Main
                        })
                    }
                    var initialSettingsPage by rememberSaveable {
                        mutableStateOf(if (intent.getBooleanExtra("open_ai_settings", false)) "ai" else null)
                    }
                    var initialHistoryPetId by rememberSaveable { mutableStateOf(intent.getStringExtra("history_pet_id")) }

                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_PAUSE -> {
                                    if (prefsManager.isFloatingEnabled) {
                                        startFloatingService()
                                    }
                                }
                                Lifecycle.Event.ON_RESUME -> {
                                    stopFloatingService()
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val direction = if (targetState != Screen.Main) 1 else -1
                            (slideInHorizontally { it * direction } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it * direction / 3 } + fadeOut())
                        },
                        label = "页面切换"
                    ) { screen -> when (screen) {
                        Screen.Main -> {
                            MainScreen(
                                prefsManager = prefsManager,
                                onNavigateToSettings = { initialSettingsPage = null; currentScreen = Screen.Settings },
                                onNavigateToHistory = { initialHistoryPetId = null; currentScreen = Screen.Conversations }
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                prefsManager = prefsManager,
                                onBack = { initialSettingsPage = null; currentScreen = Screen.Main },
                                initialPage = initialSettingsPage
                            )
                        }
                        Screen.Conversations -> {
                            ConversationScreen(
                                prefsManager = prefsManager,
                                initialPetId = initialHistoryPetId,
                                onBack = { initialHistoryPetId = null; currentScreen = Screen.Main }
                            )
                        }
                    } }
                }
            }
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java).apply {
            action = FloatingService.ACTION_SHOW
        }
        startService(intent)
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingService::class.java).apply {
            action = FloatingService.ACTION_HIDE
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

enum class Screen {
    Main,
    Settings,
    Conversations
}
