package com.shizuku.deskpet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.shizuku.deskpet.data.PreferencesManager
import com.shizuku.deskpet.service.FloatingService
import com.shizuku.deskpet.ui.screens.MainScreen
import com.shizuku.deskpet.ui.screens.SettingsScreen
import com.shizuku.deskpet.ui.theme.DeskpetTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PreferencesManager

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
                    var currentScreen by remember { mutableStateOf(Screen.Main) }

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

                    when (currentScreen) {
                        Screen.Main -> {
                            MainScreen(
                                prefsManager = prefsManager,
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                onStartFloatingService = { startFloatingService() }
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                prefsManager = prefsManager,
                                onBack = { currentScreen = Screen.Main }
                            )
                        }
                    }
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
    Settings
}
