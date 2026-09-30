package com.yvanbetremieux.fingerpick.data

import android.content.Context
import com.yvanbetremieux.fingerpick.game.GameSettings

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): GameSettings {
        val d = GameSettings()
        return GameSettings.sanitized(prefs.getInt(KEY_PLAYERS, d.players), prefs.getInt(KEY_WINNERS, d.winners))
    }

    fun save(settings: GameSettings) {
        prefs.edit().putInt(KEY_PLAYERS, settings.players).putInt(KEY_WINNERS, settings.winners).apply()
    }

    private companion object {
        const val KEY_PLAYERS = "players"
        const val KEY_WINNERS = "winners"
    }
}
