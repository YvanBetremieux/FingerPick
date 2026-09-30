package com.yvanbetremieux.fingerpick.ui.glow

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import com.yvanbetremieux.fingerpick.ui.theme.Ink
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette
import kotlin.math.cos
import kotlin.math.sin

private val AMBIENT_COLORS = intArrayOf(11, 9, 6, 1, 3)

/** Slow drifting colored glows behind the setup screen. */
@Composable
fun AmbientBackground(clock: State<Long>, modifier: Modifier = Modifier) {
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val sprite = createGlowSprite()
            val tints = AMBIENT_COLORS.map { tintFor(NeonPalette[it]) }
            val d = size.minDimension * 1.2f
            onDrawBehind {
                drawRect(Ink)
                val t = clock.value / 1000f
                for (i in tints.indices) {
                    val x = size.width * (0.5f + 0.40f * sin(t * (0.07f + i * 0.013f) + i * 1.3f))
                    val y = size.height * (0.5f + 0.42f * cos(t * (0.05f + i * 0.011f) + i * 2.1f))
                    drawGlow(sprite, tints[i], Offset(x, y), d, 0.17f)
                }
            }
        },
    )
}
