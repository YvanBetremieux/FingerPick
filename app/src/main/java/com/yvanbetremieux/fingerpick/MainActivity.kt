package com.yvanbetremieux.fingerpick

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yvanbetremieux.fingerpick.data.SettingsStore
import com.yvanbetremieux.fingerpick.platform.Haptics
import com.yvanbetremieux.fingerpick.ui.play.PlayScreen
import com.yvanbetremieux.fingerpick.ui.setup.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val store = SettingsStore(this)
        val haptics = Haptics(this)
        setContent {
            FingerPickApp(store, haptics, onImmersive = ::applyImmersive)
        }
    }

    private fun applyImmersive(immersive: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun FingerPickApp(store: SettingsStore, haptics: Haptics, onImmersive: (Boolean) -> Unit) {
    var settings by remember { mutableStateOf(store.load()) }
    var playing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(playing) { onImmersive(playing) }
    Crossfade(targetState = playing, animationSpec = tween(350), label = "screen") { isPlaying ->
        if (isPlaying) {
            PlayScreen(settings, haptics, onExit = { playing = false })
        } else {
            SetupScreen(
                settings = settings,
                onChange = { settings = it; store.save(it) },
                onPlay = { playing = true },
            )
        }
    }
}
