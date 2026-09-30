package com.yvanbetremieux.fingerpick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yvanbetremieux.fingerpick.game.GameSettings
import com.yvanbetremieux.fingerpick.platform.Haptics
import com.yvanbetremieux.fingerpick.ui.play.PlayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val haptics = Haptics(this)
        setContent { PlayScreen(GameSettings(), haptics, onExit = ::finish) }
    }
}
