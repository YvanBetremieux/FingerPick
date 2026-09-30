package com.yvanbetremieux.fingerpick.ui.play

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yvanbetremieux.fingerpick.game.Finger
import com.yvanbetremieux.fingerpick.game.GameEngine
import com.yvanbetremieux.fingerpick.game.GameSettings
import com.yvanbetremieux.fingerpick.game.GameState
import com.yvanbetremieux.fingerpick.game.Phase

/** A light fading out after its finger lifted. */
data class Ghost(val finger: Finger, val liftedAt: Long)

const val GHOST_MILLIS = 320L

/** Bridges the pure [GameEngine] to Compose snapshot state. */
@Stable
class PlayController(val settings: GameSettings) {
    private val engine = GameEngine(settings.players, settings.winners)

    var state: GameState by mutableStateOf(engine.state)
        private set

    val ghosts = mutableStateListOf<Ghost>()

    /** Returns true when the finger became a tracked player (for haptics). */
    fun down(id: Long, x: Float, y: Float, now: Long): Boolean {
        val before = engine.state.fingers.size
        engine.fingerDown(id, x, y, now)
        sync(now)
        return engine.state.phase != Phase.Result && engine.state.fingers.size > before
    }

    fun move(id: Long, x: Float, y: Float, now: Long) { engine.fingerMove(id, x, y, now); sync(now) }
    fun up(id: Long, now: Long) { engine.fingerUp(id, now); sync(now) }
    fun tick(now: Long) { engine.tick(now); sync(now) }
    fun replay(now: Long) { engine.reset(now); sync(now) }
    fun cancelAll(now: Long) { engine.cancelAll(now); sync(now) }

    private fun sync(now: Long) {
        val next = engine.state
        if (next == state) return
        if (state.phase != Phase.Result && next.phase != Phase.Result) {
            for (f in state.fingers) if (next.fingers.none { it.id == f.id }) ghosts += Ghost(f, now)
        } else if (state.phase == Phase.Result && next.phase != Phase.Result) {
            // Rejouer: let the winners dissolve instead of vanishing.
            for (f in state.fingers) if (f.id in state.winnerIds) ghosts += Ghost(f, now)
        }
        if (ghosts.isNotEmpty()) ghosts.removeAll { now - it.liftedAt > GHOST_MILLIS }
        state = next
    }
}
