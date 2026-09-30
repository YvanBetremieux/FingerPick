package com.yvanbetremieux.fingerpick.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.yvanbetremieux.fingerpick.R

@OptIn(ExperimentalTextApi::class)
private fun unbounded(weight: FontWeight) = Font(
    R.font.unbounded,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Unbounded = FontFamily(
    unbounded(FontWeight.Light),
    unbounded(FontWeight.Normal),
    unbounded(FontWeight.SemiBold),
    unbounded(FontWeight.Black),
)

val TitleStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 58.sp, lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = Mist)

/** The digits glow like the lights: a wide soft halo baked into the text shadow. */
val CountdownStyle = TextStyle(
    fontFamily = Unbounded,
    fontWeight = FontWeight.Black,
    fontSize = 184.sp,
    lineHeight = 184.sp,
    letterSpacing = (-4).sp,
    textAlign = TextAlign.Center,
    color = Mist,
    shadow = Shadow(color = Color.White.copy(alpha = 0.55f), blurRadius = 56f),
)
val NumberStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, letterSpacing = (-1).sp, color = Mist)
val LabelStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.5.sp, color = Mist)
val CaptionStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.6.sp, color = MistDim)
val CounterStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Light, fontSize = 24.sp, letterSpacing = 1.5.sp, color = Mist)
