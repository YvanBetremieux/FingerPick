package com.yvanbetremieux.fingerpick.game

const val MIN_PLAYERS = 2
const val MAX_PLAYERS = 12

/** Round configuration. Always valid: 2..12 players, 1..players-1 winners. */
data class GameSettings(val players: Int = 4, val winners: Int = 1) {
    fun withPlayers(value: Int): GameSettings {
        val p = value.coerceIn(MIN_PLAYERS, MAX_PLAYERS)
        return GameSettings(p, winners.coerceIn(1, p - 1))
    }

    fun withWinners(value: Int): GameSettings = copy(winners = value.coerceIn(1, players - 1))

    companion object {
        fun sanitized(players: Int, winners: Int): GameSettings =
            GameSettings().withPlayers(players).withWinners(winners)
    }
}
