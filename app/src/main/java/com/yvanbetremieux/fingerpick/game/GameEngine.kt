package com.yvanbetremieux.fingerpick.game

import kotlin.random.Random

const val PALETTE_SIZE = MAX_PLAYERS

enum class Phase { Waiting, Countdown, Result }

data class Finger(val id: Long, val x: Float, val y: Float, val colorIndex: Int, val downAt: Long)

data class GameState(
    val phase: Phase = Phase.Waiting,
    val fingers: List<Finger> = emptyList(),
    /** 3, 2, 1 during [Phase.Countdown]; 0 otherwise. */
    val countdown: Int = 0,
    /** Non-empty only in [Phase.Result]. */
    val winnerIds: Set<Long> = emptySet(),
    val phaseStartedAt: Long = 0L,
)

/**
 * Rules of a round. Pure Kotlin: callers pass pointer events and a monotonic clock.
 *
 * Only the first [players] fingers are tracked; any other finger is ignored until it lifts.
 */
class GameEngine(
    private val players: Int,
    private val winners: Int,
    private val random: Random = Random.Default,
    private val countdownMillis: Long = 3_000L,
) {
    init {
        require(players in MIN_PLAYERS..MAX_PLAYERS) { "players must be in $MIN_PLAYERS..$MAX_PLAYERS" }
        require(winners in 1 until players) { "winners must be in 1..${players - 1}" }
    }

    private val tracked = LinkedHashMap<Long, Finger>()
    private val down = HashSet<Long>()
    private var phase = Phase.Waiting
    private var phaseStartedAt = 0L
    private var winnerIds: Set<Long> = emptySet()

    var state: GameState = GameState()
        private set

    fun fingerDown(id: Long, x: Float, y: Float, now: Long) {
        advance(now)
        if (!down.add(id)) return
        if (phase == Phase.Waiting && tracked.size < players) {
            val used = tracked.values.mapTo(HashSet()) { it.colorIndex }
            val color = (0 until PALETTE_SIZE).filter { it !in used }.random(random)
            tracked[id] = Finger(id, x, y, color, now)
            if (tracked.size == players) enter(Phase.Countdown, now)
        }
        publish(now)
    }

    fun fingerMove(id: Long, x: Float, y: Float, now: Long) {
        advance(now)
        if (phase != Phase.Result) {
            val f = tracked[id]
            if (f != null && (f.x != x || f.y != y)) tracked[id] = f.copy(x = x, y = y)
        }
        publish(now)
    }

    fun fingerUp(id: Long, now: Long) {
        advance(now)
        if (down.remove(id) && phase != Phase.Result && tracked.remove(id) != null && phase == Phase.Countdown) {
            enter(Phase.Waiting, now)
        }
        publish(now)
    }

    fun tick(now: Long) {
        advance(now)
        publish(now)
    }

    fun reset(now: Long) {
        tracked.clear()
        winnerIds = emptySet()
        enter(Phase.Waiting, now)
        publish(now)
    }

    fun cancelAll(now: Long) {
        down.clear()
        if (phase != Phase.Result) {
            tracked.clear()
            enter(Phase.Waiting, now)
        }
        publish(now)
    }

    private fun advance(now: Long) {
        if (phase == Phase.Countdown && now - phaseStartedAt >= countdownMillis) {
            winnerIds = tracked.keys.shuffled(random).take(winners).toSet()
            enter(Phase.Result, now)
        }
    }

    private fun enter(next: Phase, now: Long) {
        phase = next
        phaseStartedAt = now
    }

    private fun publish(now: Long) {
        val countdown = if (phase == Phase.Countdown) {
            val remaining = countdownMillis - (now - phaseStartedAt)
            ((remaining + 999) / 1000).toInt().coerceAtLeast(1)
        } else 0
        val next = GameState(
            phase = phase,
            fingers = tracked.values.toList(),
            countdown = countdown,
            winnerIds = if (phase == Phase.Result) winnerIds else emptySet(),
            phaseStartedAt = phaseStartedAt,
        )
        if (next != state) state = next
    }
}
