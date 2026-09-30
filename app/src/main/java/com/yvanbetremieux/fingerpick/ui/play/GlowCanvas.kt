package com.yvanbetremieux.fingerpick.ui.play

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.yvanbetremieux.fingerpick.game.Phase
import com.yvanbetremieux.fingerpick.ui.glow.createGlowSprite
import com.yvanbetremieux.fingerpick.ui.glow.drawGlow
import com.yvanbetremieux.fingerpick.ui.glow.drawOrb
import com.yvanbetremieux.fingerpick.ui.glow.tintFor
import com.yvanbetremieux.fingerpick.ui.theme.Ink
import com.yvanbetremieux.fingerpick.ui.theme.Mist
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette
import kotlin.math.sin
import kotlin.random.Random

private const val SPAWN_MS = 380f
private const val SPAWN_RIPPLE_MS = 560f
private const val COUNTDOWN_MS = 3000f
private const val LOSER_FADE_MS = 520f
private const val FLASH_MS = 320f
private const val WAVE_MS = 1300f
private const val WAVE_STAGGER_MS = 190f
private const val WAVES = 3
private const val GRAIN_TILE = 128
private const val GRAIN_STEP_MS = 70L
private const val STROKE_STEPS = 12

/**
 * Draws every light. Reads [controller] state and [clock] only inside the draw lambda,
 * so finger moves and animation frames redraw without recomposing.
 *
 * Everything reused per frame (sprite, tints, strokes, brushes) is built once in the cache block.
 */
@Composable
fun GlowCanvas(controller: PlayController, clock: State<Long>, modifier: Modifier = Modifier) {
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val sprite = createGlowSprite()
            val tints = NeonPalette.map(::tintFor)
            val mistTint = tintFor(Mist)
            val r = 52.dp.toPx()
            val ring = Stroke(width = 2.5.dp.toPx())
            val thinRing = Stroke(width = 1.5.dp.toPx())
            val tether = 1.5.dp.toPx()
            // Shockwave strokes thin out as they expand; pre-built at fixed widths.
            val waveStrokes = Array(STROKE_STEPS) { i ->
                Stroke(width = (10f - 9f * i / (STROKE_STEPS - 1)).dp.toPx())
            }
            val vignette = Brush.radialGradient(
                listOf(Color(0xFF0B0E1A), Color(0xFF070810), Ink),
                center = Offset(size.width / 2f, size.height * 0.42f),
                radius = size.maxDimension * 0.8f,
            )
            val grain = ShaderBrush(ImageShader(grainTile(), TileMode.Repeated, TileMode.Repeated))
            val grainSize = Size(size.width + GRAIN_TILE, size.height + GRAIN_TILE)

            onDrawBehind {
                val s = controller.state
                val now = clock.value
                val t = (now - s.phaseStartedAt).toFloat()
                val fingers = s.fingers

                drawRect(vignette)
                // Film grain: one tiled draw, shifted in steps so it shimmers without per-frame noise.
                val step = now / GRAIN_STEP_MS
                val gx = ((step * 7919L) % GRAIN_TILE).toFloat()
                val gy = ((step * 104729L) % GRAIN_TILE).toFloat()
                translate(-gx, -gy) { drawRect(grain, size = grainSize) }

                when (s.phase) {
                    Phase.Waiting, Phase.Countdown -> {
                        val counting = s.phase == Phase.Countdown
                        val progress = if (counting) (t / COUNTDOWN_MS).coerceIn(0f, 1f) else 0f
                        val beat = if (counting) (t % 1000f) / 1000f else 0f
                        // Each second lands as a punch that settles back.
                        val punch = if (counting) 1f + 0.10f * (1f - easeOutCubic(beat / 0.45f)) else 1f

                        if (counting && fingers.isNotEmpty()) {
                            var cx = 0f
                            var cy = 0f
                            for (i in fingers.indices) { cx += fingers[i].x; cy += fingers[i].y }
                            val centroid = Offset(cx / fingers.size, cy / fingers.size)
                            val tetherAlpha = 0.10f + 0.30f * progress
                            for (i in fingers.indices) {
                                val f = fingers[i]
                                drawLine(
                                    NeonPalette[f.colorIndex], Offset(f.x, f.y), centroid, tether,
                                    alpha = tetherAlpha, blendMode = BlendMode.Plus,
                                )
                            }
                            // Energy gathering where the tethers meet.
                            drawGlow(sprite, mistTint, centroid, r * (1.5f + 3.5f * progress) * punch, 0.10f + 0.30f * progress)
                        }

                        for (i in fingers.indices) {
                            val f = fingers[i]
                            val c = Offset(f.x, f.y)
                            val color = NeonPalette[f.colorIndex]
                            val age = (now - f.downAt).toFloat()
                            val spawn = easeOutBack(age / SPAWN_MS)
                            val breathe = 1f + 0.05f * sin(now / 380f + f.id * 1.7f)
                            val intensity = 1f + 0.18f * progress
                            drawOrb(sprite, tints[f.colorIndex], color, c, r * spawn * breathe * intensity * punch, 1f)

                            // Touch-down ripple.
                            val w = age / SPAWN_RIPPLE_MS
                            if (w < 1f) {
                                drawCircle(
                                    color, r * (0.7f + 1.9f * easeOutCubic(w)), c,
                                    alpha = (1f - w) * 0.7f, style = thinRing, blendMode = BlendMode.Plus,
                                )
                            }

                            // Breathing outer ring; during the countdown it snaps out on every second and contracts.
                            val ringScale = if (counting) 1.95f - 0.75f * easeOutCubic(beat) else 1.25f + 0.08f * sin(now / 300f + f.id)
                            val ringAlpha = if (counting) 0.35f + 0.6f * beat else 0.5f
                            drawCircle(color, r * ringScale * spawn, c, alpha = ringAlpha, style = ring, blendMode = BlendMode.Plus)
                        }
                    }

                    Phase.Result -> {
                        for (i in fingers.indices) {
                            val f = fingers[i]
                            val c = Offset(f.x, f.y)
                            val color = NeonPalette[f.colorIndex]
                            val tint = tints[f.colorIndex]
                            if (f.id in s.winnerIds) {
                                // The winner's color floods the room, then settles.
                                val flood = easeOutCubic(t / 900f)
                                drawGlow(sprite, tint, c, r * 18f * flood, 0.30f - 0.10f * flood)
                                for (k in 0 until WAVES) {
                                    val w = (t - k * WAVE_STAGGER_MS) / WAVE_MS
                                    if (w in 0f..1f) {
                                        val e = easeOutCubic(w)
                                        val stroke = waveStrokes[(e * (STROKE_STEPS - 1)).toInt()]
                                        drawCircle(
                                            color, r * (1f + e * 9f), c,
                                            alpha = (1f - w) * (0.85f - 0.2f * k), style = stroke, blendMode = BlendMode.Plus,
                                        )
                                    }
                                }
                                val bloom = easeOutBack(t / 650f)
                                val breathe = 1f + 0.06f * sin(now / 420f + f.id)
                                val radius = r * (1.15f + 0.35f * bloom) * breathe
                                drawOrb(sprite, tint, color, c, radius, 1f)
                                drawCircle(color, radius * 1.4f, c, alpha = 0.7f * bloom.coerceAtMost(1f), style = ring, blendMode = BlendMode.Plus)
                            } else {
                                // Losers implode: shrink toward the core as they go dark.
                                val e = easeInCubic(t / LOSER_FADE_MS)
                                val fade = 1f - e
                                if (fade > 0f) drawOrb(sprite, tint, color, c, r * 1.15f * (1f - 0.65f * e), fade)
                            }
                        }
                        // Brief white flash on the reveal beat.
                        val flash = 1f - t / FLASH_MS
                        if (flash > 0f) drawRect(Mist, alpha = 0.10f * flash * flash, blendMode = BlendMode.Plus)
                    }
                }

                val ghosts = controller.ghosts
                for (i in ghosts.indices) {
                    val g = ghosts[i]
                    val fade = 1f - (now - g.liftedAt) / GHOST_MILLIS.toFloat()
                    if (fade <= 0f) continue
                    val f = g.finger
                    drawOrb(
                        sprite, tints[f.colorIndex], NeonPalette[f.colorIndex], Offset(f.x, f.y),
                        r * (1f + 0.3f * (1f - fade)), fade * fade * 0.8f,
                    )
                }
            }
        },
    )
}

/** Monochrome noise tile, built once per canvas size. Faint enough to only texture the black. */
private fun grainTile(): ImageBitmap {
    val random = Random(7)
    val pixels = IntArray(GRAIN_TILE * GRAIN_TILE) {
        val a = random.nextInt(0, 16)
        (a shl 24) or 0x00FFFFFF
    }
    return android.graphics.Bitmap
        .createBitmap(pixels, GRAIN_TILE, GRAIN_TILE, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

private fun easeOutCubic(v: Float): Float {
    val x = 1f - v.coerceIn(0f, 1f)
    return 1f - x * x * x
}

private fun easeInCubic(v: Float): Float {
    val x = v.coerceIn(0f, 1f)
    return x * x * x
}

private fun easeOutBack(v: Float): Float {
    val x = v.coerceIn(0f, 1f) - 1f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * x * x * x + c1 * x * x
}
