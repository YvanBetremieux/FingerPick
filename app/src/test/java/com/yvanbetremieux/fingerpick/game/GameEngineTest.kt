package com.yvanbetremieux.fingerpick.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private fun engine(players: Int = 3, winners: Int = 1, seed: Int = 42) =
        GameEngine(players, winners, Random(seed))

    private fun GameEngine.downAll(vararg ids: Long, now: Long = 0L) =
        ids.forEach { fingerDown(it, it * 10f, it * 20f, now) }

    private fun GameEngine.ids() = state.fingers.map { it.id }

    @Test fun startsEmptyAndWaiting() {
        val s = engine().state
        assertEquals(Phase.Waiting, s.phase)
        assertTrue(s.fingers.isEmpty())
        assertEquals(0, s.countdown)
    }

    @Test fun countdownStartsExactlyWhenAllPlayersAreDown() {
        val e = engine(players = 3)
        e.downAll(1, 2)
        assertEquals(Phase.Waiting, e.state.phase)
        e.fingerDown(3, 0f, 0f, now = 500)
        assertEquals(Phase.Countdown, e.state.phase)
        assertEquals(3, e.state.countdown)
        assertEquals(500L, e.state.phaseStartedAt)
    }

    @Test fun countdownGoesThreeTwoOneThenResult() {
        val e = engine(players = 2)
        e.downAll(1, 2, now = 0)
        e.tick(999); assertEquals(3, e.state.countdown)
        e.tick(1000); assertEquals(2, e.state.countdown)
        e.tick(2000); assertEquals(1, e.state.countdown)
        e.tick(2999); assertEquals(1, e.state.countdown)
        e.tick(3000)
        assertEquals(Phase.Result, e.state.phase)
        assertEquals(0, e.state.countdown)
    }

    @Test fun liftingDuringCountdownCancelsAndKeepsOthers() {
        val e = engine(players = 3)
        e.downAll(1, 2, 3)
        e.fingerUp(2, now = 1500)
        assertEquals(Phase.Waiting, e.state.phase)
        assertEquals(listOf(1L, 3L), e.ids())
        e.fingerDown(4, 0f, 0f, now = 1600)
        assertEquals(Phase.Countdown, e.state.phase)
        assertEquals(1600L, e.state.phaseStartedAt)
    }

    @Test fun movingDuringCountdownUpdatesPositionWithoutCancelling() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.fingerMove(1, 111f, 222f, now = 100)
        assertEquals(Phase.Countdown, e.state.phase)
        val f = e.state.fingers.first { it.id == 1L }
        assertEquals(111f, f.x); assertEquals(222f, f.y)
    }

    @Test fun extraFingersAreIgnoredEvenAfterATrackedLift() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.fingerDown(3, 0f, 0f, now = 10)       // extra during countdown
        assertEquals(listOf(1L, 2L), e.ids())
        e.fingerUp(1, now = 20)                  // cancel
        assertEquals(Phase.Waiting, e.state.phase)
        assertEquals(listOf(2L), e.ids())        // 3 still ignored
        e.fingerMove(3, 5f, 5f, now = 30)
        assertEquals(listOf(2L), e.ids())
        e.fingerDown(4, 0f, 0f, now = 40)
        assertEquals(listOf(2L, 4L), e.ids())
        assertEquals(Phase.Countdown, e.state.phase)
    }

    @Test fun liftingAnIgnoredFingerHasNoEffect() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.fingerDown(3, 0f, 0f, now = 10)
        val before = e.state
        e.fingerUp(3, now = 20)
        assertEquals(before, e.state)
    }

    @Test fun duplicateDownIsCountedOnce() {
        val e = engine(players = 3)
        e.fingerDown(1, 0f, 0f, now = 0)
        e.fingerDown(1, 0f, 0f, now = 5)
        assertEquals(listOf(1L), e.ids())
    }

    @Test fun unknownUpIsANoop() {
        val e = engine()
        e.downAll(1)
        val before = e.state
        e.fingerUp(99, now = 10)
        assertEquals(before, e.state)
    }

    @Test fun upAtExpiryRevealsInsteadOfCancelling() {
        val e = engine(players = 2)
        e.downAll(1, 2, now = 0)
        e.fingerUp(1, now = 3000)                // no tick happened at 3000
        assertEquals(Phase.Result, e.state.phase)
        assertEquals(listOf(1L, 2L), e.ids())
    }

    @Test fun resultHasExactlyWinnersDistinctAmongTracked() {
        repeat(50) { seed ->
            val e = engine(players = 5, winners = 2, seed = seed)
            e.downAll(1, 2, 3, 4, 5)
            e.tick(3000)
            val w = e.state.winnerIds
            assertEquals(2, w.size)
            assertTrue(w.all { it in 1L..5L })
        }
    }

    @Test fun everyFingerCanWin() {
        val winners = (0 until 200).flatMap { seed ->
            val e = engine(players = 4, winners = 1, seed = seed)
            e.downAll(1, 2, 3, 4)
            e.tick(3000)
            e.state.winnerIds
        }.toSet()
        assertEquals(setOf(1L, 2L, 3L, 4L), winners)
    }

    @Test fun resultFreezesFingersAndIgnoresInput() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.tick(3000)
        val frozen = e.state
        e.fingerMove(1, 999f, 999f, now = 3100)
        e.fingerUp(1, now = 3200)
        e.fingerUp(2, now = 3200)
        e.fingerDown(7, 0f, 0f, now = 3300)
        assertEquals(frozen, e.state)
    }

    @Test fun resetStartsNewRoundIgnoringFingersStillDown() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.tick(3000)
        e.fingerUp(2, now = 3100)                // 1 still resting on screen
        e.reset(now = 4000)
        assertEquals(Phase.Waiting, e.state.phase)
        assertTrue(e.state.fingers.isEmpty())
        assertTrue(e.state.winnerIds.isEmpty())
        e.fingerMove(1, 3f, 3f, now = 4100)
        assertTrue(e.state.fingers.isEmpty())    // 1 ignored until lifted
        e.fingerDown(5, 0f, 0f, now = 4200)
        assertEquals(listOf(5L), e.ids())
    }

    @Test fun colorsAreUniqueAmongSimultaneousFingers() {
        val e = engine(players = 12, winners = 1)
        (1L..11L).forEach { e.fingerDown(it, 0f, 0f, now = 0) }
        e.fingerUp(4, now = 1)
        e.fingerDown(20, 0f, 0f, now = 2)
        e.fingerDown(21, 0f, 0f, now = 3)
        val colors = e.state.fingers.map { it.colorIndex }
        assertEquals(12, colors.size)
        assertEquals(12, colors.toSet().size)
        assertTrue(colors.all { it in 0 until PALETTE_SIZE })
    }

    @Test fun cancelAllForgetsFingersOutsideResult() {
        val e = engine(players = 3)
        e.downAll(1, 2, 3)
        e.cancelAll(now = 100)
        assertEquals(Phase.Waiting, e.state.phase)
        assertTrue(e.state.fingers.isEmpty())
        e.fingerUp(1, now = 200)                 // stale up after cancel
        e.fingerDown(1, 0f, 0f, now = 300)       // same id comes back: tracked
        assertEquals(listOf(1L), e.ids())
    }

    @Test fun cancelAllKeepsResultVisible() {
        val e = engine(players = 2)
        e.downAll(1, 2)
        e.tick(3000)
        val result = e.state
        e.cancelAll(now = 3100)
        assertEquals(result, e.state)
        e.reset(now = 3200)
        e.fingerDown(1, 0f, 0f, now = 3300)      // no longer considered "still down"
        assertEquals(listOf(1L), e.ids())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWinnersNotBelowPlayers() {
        GameEngine(3, 3)
    }
}
