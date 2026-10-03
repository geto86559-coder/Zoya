package com.example.presentation.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.models.ZoyaEmotion
import com.example.domain.models.ZoyaState
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoidBlack
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Clean architectural abstraction for Zoya Character Rendering.
 * Allows switching between vector/Compose anime face, 3D Filament/OpenGL, or Live2D.
 */
interface ZoyaCharacterRenderer {
    @Composable
    fun RenderCharacter(
        state: ZoyaState,
        emotion: ZoyaEmotion,
        outputAmplitude: Float,
        modifier: Modifier
    )
}

/**
 * High-fidelity Jetpack Compose implementation of Zoya Anime Presentation:
 * Features natural eye blinking, audio-synced lip sync, breathing motion, and dynamic facial expressions.
 */
class ComposeZoyaCharacterRenderer : ZoyaCharacterRenderer {

    @Composable
    override fun RenderCharacter(
        state: ZoyaState,
        emotion: ZoyaEmotion,
        outputAmplitude: Float,
        modifier: Modifier
    ) {
        // Natural blinking state
        var isBlinking by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                // Natural random intervals between blinks: 2.5 to 5.5 seconds
                val interval = Random.nextLong(2500, 5500)
                delay(interval)
                isBlinking = true
                delay(130)
                isBlinking = false

                // Occasional cute double-blink
                if (Random.nextFloat() < 0.25f) {
                    delay(120)
                    isBlinking = true
                    delay(110)
                    isBlinking = false
                }
            }
        }

        // Subtle breathing sway
        val infiniteTransition = rememberInfiniteTransition(label = "anime_sway")
        val breathingOffset by infiniteTransition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathing_offset"
        )

        // Head tilt angle based on emotion
        val headTilt = when (emotion) {
            ZoyaEmotion.TEASING -> 4f
            ZoyaEmotion.CONFUSED -> -6f
            ZoyaEmotion.PLAYFUL -> 3f
            ZoyaEmotion.THINKING -> -3f
            ZoyaEmotion.LISTENING -> 2f
            else -> 0f
        }

        Box(
            modifier = modifier
                .size(260.dp)
                .testTag("zoya_character_presentation"),
            contentAlignment = Alignment.Center
        ) {
            // Holographic glow ring background
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                NeonPurple.copy(alpha = 0.25f),
                                NeonCyan.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Animated character avatar frame
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .offset(y = breathingOffset.dp)
                    .rotate(headTilt)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(NeonCyan, NeonMagenta, NeonPurple, NeonCyan)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Background anime avatar image
                Image(
                    painter = painterResource(id = R.drawable.zoya_avatar_1791042285554),
                    contentDescription = "Zoya Anime Assistant",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay interactive Canvas for dynamic facial features (mouth sync, eye expressions, cyber visor)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("anime_face_canvas")
                ) {
                    val w = size.width
                    val h = size.height

                    // Cybernetic ear piece & holographic visor accent
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.85f),
                        radius = 7.dp.toPx(),
                        center = Offset(w * 0.18f, h * 0.52f)
                    )
                    drawCircle(
                        color = NeonMagenta,
                        radius = 3.dp.toPx(),
                        center = Offset(w * 0.18f, h * 0.52f)
                    )

                    // Cyber ear circuit trace
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.6f),
                        start = Offset(w * 0.18f, h * 0.52f),
                        end = Offset(w * 0.26f, h * 0.44f),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Dynamic Mouth Lip-Sync
                    // Driven by output audio amplitude when speaking
                    val mouthCenterX = w * 0.50f
                    val mouthCenterY = h * 0.68f

                    val mouthAmp = (outputAmplitude * 2.8f).coerceIn(0f, 1f)

                    if (state == ZoyaState.SPEAKING && mouthAmp > 0.05f) {
                        // Open animated mouth synchronized to speech
                        val mouthWidth = (16f + (mouthAmp * 12f)).dp.toPx()
                        val mouthHeight = (6f + (mouthAmp * 16f)).dp.toPx()

                        // Mouth cavity
                        drawRoundRect(
                            color = Color(0xFF6B1D2F),
                            topLeft = Offset(mouthCenterX - mouthWidth / 2, mouthCenterY - mouthHeight / 2),
                            size = Size(mouthWidth, mouthHeight),
                            cornerRadius = CornerRadius(mouthHeight / 2, mouthHeight / 2)
                        )
                        // Tongue highlight
                        drawCircle(
                            color = Color(0xFFFF6584),
                            radius = mouthHeight * 0.35f,
                            center = Offset(mouthCenterX, mouthCenterY + mouthHeight * 0.15f)
                        )
                    } else {
                        // Resting / expressive smile
                        val smilePath = Path().apply {
                            when (emotion) {
                                ZoyaEmotion.TEASING, ZoyaEmotion.PLAYFUL -> {
                                    // Playful slight smirk
                                    moveTo(mouthCenterX - 10.dp.toPx(), mouthCenterY - 1.dp.toPx())
                                    quadraticBezierTo(
                                        mouthCenterX, mouthCenterY + 4.dp.toPx(),
                                        mouthCenterX + 12.dp.toPx(), mouthCenterY - 4.dp.toPx()
                                    )
                                }
                                ZoyaEmotion.HAPPY -> {
                                    // Sweet curved smile
                                    moveTo(mouthCenterX - 12.dp.toPx(), mouthCenterY - 2.dp.toPx())
                                    quadraticBezierTo(
                                        mouthCenterX, mouthCenterY + 7.dp.toPx(),
                                        mouthCenterX + 12.dp.toPx(), mouthCenterY - 2.dp.toPx()
                                    )
                                }
                                ZoyaEmotion.CONFUSED -> {
                                    // Pout / small curious mouth
                                    moveTo(mouthCenterX - 6.dp.toPx(), mouthCenterY)
                                    lineTo(mouthCenterX + 6.dp.toPx(), mouthCenterY + 2.dp.toPx())
                                }
                                else -> {
                                    // Gentle confident neutral smile
                                    moveTo(mouthCenterX - 9.dp.toPx(), mouthCenterY)
                                    quadraticBezierTo(
                                        mouthCenterX, mouthCenterY + 3.dp.toPx(),
                                        mouthCenterX + 9.dp.toPx(), mouthCenterY
                                    )
                                }
                            }
                        }
                        drawPath(
                            path = smilePath,
                            color = Color(0xFF8B3A4A),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }

                    // Eye Expression overlays (Blink / Wink / Eye Glow)
                    val leftEyeCenter = Offset(w * 0.38f, h * 0.44f)
                    val rightEyeCenter = Offset(w * 0.62f, h * 0.44f)

                    if (isBlinking) {
                        // Closed curved anime blink lids
                        drawLine(
                            color = Color(0xFF2D1B36),
                            start = Offset(leftEyeCenter.x - 12.dp.toPx(), leftEyeCenter.y),
                            end = Offset(leftEyeCenter.x + 12.dp.toPx(), leftEyeCenter.y),
                            strokeWidth = 3.dp.toPx()
                        )
                        drawLine(
                            color = Color(0xFF2D1B36),
                            start = Offset(rightEyeCenter.x - 12.dp.toPx(), rightEyeCenter.y),
                            end = Offset(rightEyeCenter.x + 12.dp.toPx(), rightEyeCenter.y),
                            strokeWidth = 3.dp.toPx()
                        )
                    } else if (emotion == ZoyaEmotion.TEASING) {
                        // Playful wink on right eye
                        drawLine(
                            color = Color(0xFF2D1B36),
                            start = Offset(rightEyeCenter.x - 11.dp.toPx(), rightEyeCenter.y + 1.dp.toPx()),
                            end = Offset(rightEyeCenter.x + 11.dp.toPx(), rightEyeCenter.y - 2.dp.toPx()),
                            strokeWidth = 3.5.dp.toPx()
                        )
                        // Star sparkle near winking eye
                        drawCircle(
                            color = NeonCyan,
                            radius = 2.5.dp.toPx(),
                            center = Offset(rightEyeCenter.x + 15.dp.toPx(), rightEyeCenter.y - 6.dp.toPx())
                        )
                    }

                    // Futuristic HUD eye reflection highlights
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.5f),
                        radius = 2.dp.toPx(),
                        center = Offset(leftEyeCenter.x - 3.dp.toPx(), leftEyeCenter.y - 4.dp.toPx())
                    )
                }
            }

            // Emotion badge chip at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 12.dp)
                    .background(
                        color = DarkSurface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (emotion) {
                        ZoyaEmotion.TEASING -> "😏 Sassy Zoya"
                        ZoyaEmotion.PLAYFUL -> "✨ Playful"
                        ZoyaEmotion.HAPPY -> "🥰 Happy"
                        ZoyaEmotion.THINKING -> "🤔 Thinking"
                        ZoyaEmotion.CONFUSED -> "🧐 Curious"
                        ZoyaEmotion.LISTENING -> "👂 Listening"
                        else -> "⚡ Online"
                    },
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
