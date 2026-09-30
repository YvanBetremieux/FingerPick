package com.yvanbetremieux.fingerpick.ui.glow

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * White radial falloff (roughly gaussian, with a long faint tail), tinted at draw time.
 * Drawing a bitmap is far cheaper than building a gradient shader per frame.
 */
fun createGlowSprite(size: Int = 256): ImageBitmap {
    val bitmap = ImageBitmap(size, size)
    val c = size / 2f
    val paint = Paint().apply {
        shader = RadialGradientShader(
            center = Offset(c, c),
            radius = c,
            colors = listOf(
                Color.White,
                Color.White.copy(alpha = 0.70f),
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.035f),
                Color.Transparent,
            ),
            colorStops = listOf(0f, 0.08f, 0.22f, 0.44f, 0.70f, 1f),
        )
    }
    Canvas(bitmap).drawCircle(Offset(c, c), c, paint)
    return bitmap
}

fun tintFor(color: Color): ColorFilter = ColorFilter.tint(color, BlendMode.Modulate)

fun DrawScope.drawGlow(sprite: ImageBitmap, tint: ColorFilter, center: Offset, diameter: Float, alpha: Float) {
    val d = diameter.roundToInt()
    if (d <= 0 || alpha <= 0f) return
    drawImage(
        image = sprite,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(sprite.width, sprite.height),
        dstOffset = IntOffset((center.x - d / 2f).roundToInt(), (center.y - d / 2f).roundToInt()),
        dstSize = IntSize(d, d),
        alpha = alpha.coerceIn(0f, 1f),
        colorFilter = tint,
        blendMode = BlendMode.Plus,
    )
}

/**
 * A finger light, all additive: wide halo, dense colored body, crisp colored core and a
 * white-hot heart. Overlapping lights mix their colors instead of occluding each other.
 */
fun DrawScope.drawOrb(sprite: ImageBitmap, tint: ColorFilter, color: Color, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawGlow(sprite, tint, center, radius * 5.2f, alpha * 0.34f)
    drawGlow(sprite, tint, center, radius * 2.3f, alpha * 0.95f)
    drawCircle(color, radius * 0.56f, center, alpha = alpha * 0.5f, blendMode = BlendMode.Plus)
    drawCircle(Color.White, radius * 0.30f, center, alpha = alpha * 0.35f, blendMode = BlendMode.Plus)
    drawCircle(Color.White, radius * 0.18f, center, alpha = alpha * 0.85f, blendMode = BlendMode.Plus)
}
