package com.yvanbetremieux.fingerpick.ui.play

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yvanbetremieux.fingerpick.game.GameSettings
import com.yvanbetremieux.fingerpick.game.Phase
import com.yvanbetremieux.fingerpick.platform.Haptics
import com.yvanbetremieux.fingerpick.ui.components.GlowButton
import com.yvanbetremieux.fingerpick.ui.rememberUptimeClock
import com.yvanbetremieux.fingerpick.ui.theme.CaptionStyle
import com.yvanbetremieux.fingerpick.ui.theme.CountdownStyle
import com.yvanbetremieux.fingerpick.ui.theme.CounterStyle
import com.yvanbetremieux.fingerpick.ui.theme.Ink
import com.yvanbetremieux.fingerpick.ui.theme.LabelStyle
import com.yvanbetremieux.fingerpick.ui.theme.Mist
import com.yvanbetremieux.fingerpick.ui.theme.MistDim
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun PlayScreen(settings: GameSettings, haptics: Haptics, onExit: () -> Unit) {
    val controller = remember(settings) { PlayController(settings) }
    val clock = rememberUptimeClock()
    val phase by remember(controller) { derivedStateOf { controller.state.phase } }
    val count by remember(controller) { derivedStateOf { controller.state.fingers.size } }
    val countdown by remember(controller) { derivedStateOf { controller.state.countdown } }
    // With a single winner, the result headline takes that winner's color.
    val soleWinnerColor by remember(controller) {
        derivedStateOf {
            val s = controller.state
            if (s.winnerIds.size == 1) s.fingers.firstOrNull { it.id in s.winnerIds }?.let { NeonPalette[it.colorIndex] } else null
        }
    }
    var buttonsVisible by remember(controller) { mutableStateOf(false) }

    BackHandler(onBack = onExit)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.cancelAll(SystemClock.uptimeMillis())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(controller, phase) {
        if (phase == Phase.Countdown) {
            while (true) withFrameMillis { controller.tick(SystemClock.uptimeMillis()) }
        }
    }
    LaunchedEffect(countdown) { if (countdown > 0) haptics.tick() }
    LaunchedEffect(controller, phase) {
        buttonsVisible = false
        if (phase == Phase.Result) {
            haptics.reveal()
            delay(1100)
            buttonsVisible = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(controller) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val now = SystemClock.uptimeMillis()
                        for (c in event.changes) {
                            val id = c.id.value
                            when {
                                c.changedToDownIgnoreConsumed() ->
                                    if (controller.down(id, c.position.x, c.position.y, now)) haptics.touch()
                                c.changedToUpIgnoreConsumed() -> controller.up(id, now)
                                c.pressed -> controller.move(id, c.position.x, c.position.y, now)
                            }
                        }
                    }
                }
            },
    ) {
        GlowCanvas(controller, clock)

        Column(
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val headline = when (phase) {
                Phase.Result -> if (settings.winners == 1) "L'ÉLU" else "LES ÉLUS"
                else -> "$count / ${settings.players}"
            }
            AnimatedContent(
                targetState = headline,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                label = "headline",
            ) { text ->
                BasicText(text, style = CounterStyle.copy(color = if (phase == Phase.Result) soleWinnerColor ?: Mist else Mist))
            }
            Spacer(Modifier.height(6.dp))
            BasicText(if (settings.winners == 1) "1 à choisir" else "${settings.winners} à choisir", style = CaptionStyle)
        }

        AnimatedVisibility(
            visible = phase == Phase.Waiting && count == 0,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(200)),
        ) {
            BasicText(
                "Posez vos doigts",
                style = LabelStyle.copy(color = MistDim),
                modifier = Modifier.graphicsLayer { alpha = 0.55f + 0.45f * sin(clock.value / 500f) },
            )
        }

        AnimatedContent(
            targetState = if (phase == Phase.Countdown) countdown else 0,
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                if (targetState == 0) {
                    // Reveal or cancel: the last digit bursts outward and dissolves.
                    EnterTransition.None togetherWith
                        (scaleOut(tween(280, easing = FastOutSlowInEasing), targetScale = 1.6f) + fadeOut(tween(240)))
                } else {
                    (scaleIn(tween(420, easing = FastOutSlowInEasing), initialScale = 1.8f) + fadeIn(tween(220))) togetherWith
                        (scaleOut(tween(300), targetScale = 0.6f) + fadeOut(tween(300)))
                }
            },
            label = "countdown",
        ) { value ->
            if (value > 0) BasicText("$value", style = CountdownStyle, modifier = Modifier.graphicsLayer { alpha = 0.92f })
        }

        AnimatedVisibility(
            visible = buttonsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            enter = fadeIn(tween(400)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 2 },
            exit = fadeOut(tween(150)),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowButton("Réglages", onClick = onExit, modifier = Modifier.weight(1f), primary = false)
                GlowButton("Rejouer", onClick = { controller.replay(SystemClock.uptimeMillis()) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
