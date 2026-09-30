package com.yvanbetremieux.fingerpick.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yvanbetremieux.fingerpick.game.GameSettings
import com.yvanbetremieux.fingerpick.game.MAX_PLAYERS
import com.yvanbetremieux.fingerpick.game.MIN_PLAYERS
import com.yvanbetremieux.fingerpick.ui.components.GlowButton
import com.yvanbetremieux.fingerpick.ui.glow.AmbientBackground
import com.yvanbetremieux.fingerpick.ui.rememberUptimeClock
import com.yvanbetremieux.fingerpick.ui.theme.CaptionStyle
import com.yvanbetremieux.fingerpick.ui.theme.InkRaised
import com.yvanbetremieux.fingerpick.ui.theme.LabelStyle
import com.yvanbetremieux.fingerpick.ui.theme.Mist
import com.yvanbetremieux.fingerpick.ui.theme.MistDim
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette
import com.yvanbetremieux.fingerpick.ui.theme.NumberStyle
import com.yvanbetremieux.fingerpick.ui.theme.TitleStyle

@OptIn(ExperimentalTextApi::class)
@Composable
fun SetupScreen(settings: GameSettings, onChange: (GameSettings) -> Unit, onPlay: () -> Unit) {
    val clock = rememberUptimeClock()
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(clock)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            BasicText(
                "FINGER\nPICK",
                style = TitleStyle.copy(brush = Brush.linearGradient(listOf(NeonPalette[11], NeonPalette[9], NeonPalette[6]))),
            )
            Spacer(Modifier.height(12.dp))
            BasicText("Posez vos doigts. Le hasard choisit.", style = CaptionStyle)
            Spacer(Modifier.weight(1f))
            StepperCard(
                label = "Joueurs",
                value = settings.players,
                min = MIN_PLAYERS,
                max = MAX_PLAYERS,
                onValueChange = { onChange(settings.withPlayers(it)) },
                dotsTotal = MAX_PLAYERS,
                dotsLit = settings.players,
            )
            Spacer(Modifier.height(12.dp))
            StepperCard(
                label = "À choisir",
                value = settings.winners,
                min = 1,
                max = settings.players - 1,
                onValueChange = { onChange(settings.withWinners(it)) },
                dotsTotal = settings.players,
                dotsLit = settings.winners,
            )
            Spacer(Modifier.height(24.dp))
            GlowButton("Jouer", onClick = onPlay, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepperCard(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    dotsTotal: Int,
    dotsLit: Int,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(InkRaised.copy(alpha = 0.72f))
            .border(1.dp, Mist.copy(alpha = 0.08f), shape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        BasicText(label, style = LabelStyle.copy(color = MistDim, fontSize = 12.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton("−", enabled = value > min) { onValueChange(value - 1) }
            AnimatedContent(
                targetState = value,
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    } else {
                        (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                    }
                },
                label = label,
            ) { v ->
                BasicText("$v", style = NumberStyle.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
            }
            RoundButton("+", enabled = value < max) { onValueChange(value + 1) }
        }
        Spacer(Modifier.height(10.dp))
        DotRow(total = dotsTotal, lit = dotsLit)
    }
}

@Composable
private fun RoundButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.25f }
            .clip(CircleShape)
            .border(1.dp, Mist.copy(alpha = 0.18f), CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(symbol, style = NumberStyle.copy(fontSize = 28.sp))
    }
}

/** One dot per seat; lit dots take their palette color. */
@Composable
private fun DotRow(total: Int, lit: Int) {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val step = size.width / MAX_PLAYERS
        val radius = 4.dp.toPx()
        for (i in 0 until total) {
            val c = Offset(step * (i + 0.5f), size.height / 2f)
            if (i < lit) {
                drawCircle(NeonPalette[i], radius * 2.2f, c, alpha = 0.25f)
                drawCircle(NeonPalette[i], radius, c)
            } else {
                drawCircle(Mist, radius, c, alpha = 0.12f)
            }
        }
    }
}
