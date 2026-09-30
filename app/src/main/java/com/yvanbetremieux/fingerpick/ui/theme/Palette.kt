package com.yvanbetremieux.fingerpick.ui.theme

import androidx.compose.ui.graphics.Color

/** Deep-water black with a faint blue cast: additive light reads as bioluminescence, not LEDs. */
val Ink = Color(0xFF04050A)
val InkRaised = Color(0xFF111421)
val Mist = Color(0xFFEDEFF8)
val MistDim = Color(0x99EDEFF8)

/**
 * One hue every ~30°, tuned so neighbours stay distinguishable once bloomed on black.
 * Index = Finger.colorIndex.
 */
val NeonPalette = listOf(
    Color(0xFFFF3D4F), // red
    Color(0xFFFF8A1F), // orange
    Color(0xFFFFD83D), // yellow
    Color(0xFFB2FF3A), // lime
    Color(0xFF34FF74), // green
    Color(0xFF22FFC8), // mint
    Color(0xFF2EDCFF), // cyan
    Color(0xFF3D98FF), // sky
    Color(0xFF6068FF), // blue
    Color(0xFFA05BFF), // violet
    Color(0xFFE84BFF), // magenta
    Color(0xFFFF4FA3), // pink
)
