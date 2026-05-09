package com.example.sondenit.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BigCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    pulsing: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val scale = if (pulsing) {
        val t = rememberInfiniteTransition(label = "pulse")
        val s by t.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse-scale",
        )
        s
    } else 1f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(secondaryColor, primaryColor),
                    )
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(diameter * 0.4f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MediumCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(secondaryColor, primaryColor),
                    )
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
