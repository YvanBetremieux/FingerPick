package com.yvanbetremieux.fingerpick.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yvanbetremieux.fingerpick.ui.theme.InkRaised
import com.yvanbetremieux.fingerpick.ui.theme.LabelStyle
import com.yvanbetremieux.fingerpick.ui.theme.Mist
import com.yvanbetremieux.fingerpick.ui.theme.NeonPalette

private val PrimaryFill = Brush.horizontalGradient(listOf(NeonPalette[11], NeonPalette[9]))
private val SecondaryFill = SolidColor(InkRaised)

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(percent = 50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.35f
            }
            .height(64.dp)
            .then(
                if (primary) Modifier.shadow(28.dp, shape, ambientColor = NeonPalette[11], spotColor = NeonPalette[9])
                else Modifier,
            )
            .clip(shape)
            .background(if (primary) PrimaryFill else SecondaryFill)
            .border(1.dp, if (primary) Color.White.copy(alpha = 0.28f) else Mist.copy(alpha = 0.14f), shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = LabelStyle.copy(color = if (primary) Color.White else Mist))
    }
}
