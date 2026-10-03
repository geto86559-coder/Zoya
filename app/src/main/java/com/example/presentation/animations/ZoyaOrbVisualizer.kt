package com.example.presentation.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.models.ZoyaState
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberRed
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.sin

/**
 * Central animated futuristic Zoya Orb with real-time audio reactivity.
 */
@Composable
fun ZoyaOrbVisualizer(
    state: ZoyaState,
    inputAmplitude: Float,
    outputAmplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "zoya_orb_anim")

    // Slow breathing animation for IDLE
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Orbital rotation for THINKING
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Glow pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("zoya_orb_visualizer"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.32f

            when (state) {
                ZoyaState.IDLE -> {
                    val radius = baseRadius * breathingScale
                    // Outer soft glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                NeonCyan.copy(alpha = 0.35f * glowPulse),
                                NeonPurple.copy(alpha = 0.15f * glowPulse),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 1.6f
                        ),
                        radius = radius * 1.6f,
                        center = center
                    )
                    // Core orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan, NeonPurple, Color(0xFF0F172A)),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                    // Delicate inner ring
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.7f),
                        radius = radius * 0.85f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                ZoyaState.LISTENING -> {
                    // Audio-reactive expanding waveforms
                    val dynamicAmp = (inputAmplitude * 1.8f).coerceIn(0f, 1f)
                    val dynamicRadius = baseRadius * (1f + dynamicAmp * 0.4f)

                    // Multiple concentric soundwave ripples
                    for (i in 1..3) {
                        val rippleRadius = dynamicRadius * (1f + (i * 0.22f) * (1f + dynamicAmp))
                        drawCircle(
                            color = NeonCyan.copy(alpha = (0.5f - (i * 0.12f)) * (0.6f + dynamicAmp * 0.4f)),
                            radius = rippleRadius,
                            center = center,
                            style = Stroke(width = (3.dp - (i * 0.5f).dp).toPx())
                        )
                    }

                    // Inner energetic core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan, NeonCyan.copy(alpha = 0.6f), Color.Transparent),
                            center = center,
                            radius = dynamicRadius
                        ),
                        radius = dynamicRadius,
                        center = center
                    )
                }

                ZoyaState.THINKING -> {
                    // Orbital rings and particles
                    val radius = baseRadius * breathingScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonPurple, NeonMagenta, Color(0xFF0F172A)),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )

                    // Rotating orbit ring
                    drawCircle(
                        color = NeonMagenta.copy(alpha = 0.8f),
                        radius = radius * 1.25f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Satellites / orbiting dots
                    val rad = Math.toRadians(rotationAngle.toDouble())
                    val dot1 = Offset(
                        (center.x + (radius * 1.25f) * cos(rad)).toFloat(),
                        (center.y + (radius * 1.25f) * sin(rad)).toFloat()
                    )
                    val dot2 = Offset(
                        (center.x + (radius * 1.25f) * cos(rad + Math.PI)).toFloat(),
                        (center.y + (radius * 1.25f) * sin(rad + Math.PI)).toFloat()
                    )
                    drawCircle(color = NeonCyan, radius = 6.dp.toPx(), center = dot1)
                    drawCircle(color = NeonMagenta, radius = 6.dp.toPx(), center = dot2)
                }

                ZoyaState.SPEAKING -> {
                    // Dynamic waveform synchronized with Zoya's output audio
                    val dynamicAmp = (outputAmplitude * 2.2f).coerceIn(0f, 1f)
                    val dynamicRadius = baseRadius * (1f + dynamicAmp * 0.5f)

                    // High-energy neon pink & magenta radiant field
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                NeonMagenta.copy(alpha = 0.8f),
                                NeonPurple.copy(alpha = 0.5f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = dynamicRadius * 1.7f
                        ),
                        radius = dynamicRadius * 1.7f,
                        center = center
                    )

                    // Speaking pulse rings
                    for (i in 1..4) {
                        val angleOffset = (i * 45f)
                        val pulseRad = dynamicRadius * (0.9f + (i * 0.18f) * (0.8f + dynamicAmp))
                        drawCircle(
                            color = if (i % 2 == 0) NeonMagenta.copy(alpha = 0.7f) else NeonCyan.copy(alpha = 0.7f),
                            radius = pulseRad,
                            center = center,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }

                    // Bright inner core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, NeonMagenta, NeonPurple),
                            center = center,
                            radius = dynamicRadius * 0.8f
                        ),
                        radius = dynamicRadius * 0.8f,
                        center = center
                    )
                }

                ZoyaState.ERROR -> {
                    // Subtle warning amber/crimson state with spoken explanation
                    val radius = baseRadius
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                CyberRed.copy(alpha = 0.7f * glowPulse),
                                CyberAmber.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 1.4f
                        ),
                        radius = radius * 1.4f,
                        center = center
                    )
                    drawCircle(
                        color = CyberAmber,
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }
    }
}
