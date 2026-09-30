package com.yvanbetremieux.fingerpick.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameSettingsTest {
    @Test fun defaultsAreFourPlayersOneWinner() {
        assertEquals(GameSettings(4, 1), GameSettings())
    }

    @Test fun withPlayersClampsToRange() {
        assertEquals(2, GameSettings().withPlayers(1).players)
        assertEquals(12, GameSettings().withPlayers(13).players)
    }

    @Test fun loweringPlayersClampsWinners() {
        val s = GameSettings(6, 5).withPlayers(3)
        assertEquals(GameSettings(3, 2), s)
    }

    @Test fun withWinnersClampsBetweenOneAndPlayersMinusOne() {
        assertEquals(1, GameSettings(4, 2).withWinners(0).winners)
        assertEquals(3, GameSettings(4, 1).withWinners(4).winners)
    }

    @Test fun sanitizedClampsBothValues() {
        assertEquals(GameSettings(12, 11), GameSettings.sanitized(40, 40))
        assertEquals(GameSettings(2, 1), GameSettings.sanitized(-1, 9))
    }
}
