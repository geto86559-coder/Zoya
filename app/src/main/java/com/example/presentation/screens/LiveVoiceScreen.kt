package com.example.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.models.ZoyaState
import com.example.presentation.animations.ComposeZoyaCharacterRenderer
import com.example.presentation.animations.ZoyaOrbVisualizer
import com.example.presentation.viewmodels.PresentationMode
import com.example.presentation.viewmodels.ZoyaViewModel
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

@Composable
fun LiveVoiceScreen(
    viewModel: ZoyaViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.zoyaState.collectAsState()
    val emotion by viewModel.zoyaEmotion.collectAsState()
    val presentationMode by viewModel.presentationMode.collectAsState()
    val inputAmplitude by viewModel.inputAmplitude.collectAsState()
    val outputAmplitude by viewModel.outputAmplitude.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val transcript by viewModel.liveTranscript.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val hasMicPermission by viewModel.hasMicPermission.collectAsState()
    val isScreenAssistantActive by viewModel.isScreenAssistantActive.collectAsState()
    val incomingCall by viewModel.incomingCall.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val announcementText by viewModel.announcementText.collectAsState()
    val activeAudioRoute by viewModel.activeAudioRoute.collectAsState()
    val isBluetoothConnected by viewModel.isBluetoothConnected.collectAsState()
    val startupState by viewModel.startupState.collectAsState()

    var showTextInputDialog by remember { mutableStateOf(false) }
    var textPromptInput by remember { mutableStateOf("") }

    val characterRenderer = remember { ComposeZoyaCharacterRenderer() }

    val quickChips = listOf(
        "Zoya Intro 🎙️" to "intro",
        "YouTube kholo ▶" to "YouTube kholo",
        "WhatsApp kholo 💬" to "WhatsApp kholo",
        "Messages padho 🔔" to "Jo notification aayi hai batao",
        "Wi-Fi settings 📶" to "Wi-Fi settings kholo",
        "Time & Date ⌚" to "Aaj ka exact time aur date batao",
        "Funny joke sunao 😏" to "Ek mazedaar one liner sunao Zoya!"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        VoidBlack,
                        DarkSurface,
                        Color(0xFF060913)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: Title, Online Status, Presentation Toggle & Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoya Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (state) {
                                    ZoyaState.ERROR -> CyberRed
                                    ZoyaState.IDLE -> TextMuted
                                    else -> CyberGreen
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ZOYA LIVE",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                if (isScreenAssistantActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(CyberGreen.copy(alpha = 0.2f))
                            .border(1.dp, CyberGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Screen Assistant: ON",
                            color = CyberGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Audio Route Badge (Bluetooth Earbuds vs Phone Speaker)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isBluetoothConnected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
                        .border(1.dp, if (isBluetoothConnected) NeonCyan.copy(alpha = 0.6f) else DarkSurfaceHighlight, RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isBluetoothConnected) "🎧 Earbuds" else "🔊 Speaker",
                        color = if (isBluetoothConnected) NeonCyan else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Controls: Mode switch & Privacy Settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mode Switch: Anime Character vs Cyber Orb
                    IconButton(
                        onClick = { viewModel.togglePresentationMode() },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .testTag("toggle_presentation_mode")
                    ) {
                        Icon(
                            imageVector = if (presentationMode == PresentationMode.ANIME_AVATAR) Icons.Filled.Face else Icons.Rounded.GraphicEq,
                            contentDescription = "Switch Visualizer",
                            tint = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Privacy Settings
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .testTag("privacy_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Settings",
                            tint = NeonMagenta
                        )
                    }
                }
            }

            // STARTUP INTRODUCTION BANNER
            if (startupState == com.example.domain.models.ZoyaStartupState.SPEAKING_INTRO) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "✨ Zoya is introducing herself...",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // INCOMING CALL CARD ASSISTANT (Section 7)
            if (incomingCall != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("incoming_call_card"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(CyberGreen, NeonCyan))
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CyberGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Phone,
                                    contentDescription = null,
                                    tint = CyberGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Incoming Call", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(incomingCall!!.callerName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = { viewModel.answerIncomingCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Pick karo", color = VoidBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.silenceIncomingCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberAmber),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Silent", color = VoidBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.endIncomingCall() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Disconnect", color = VoidBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // VOICE ANNOUNCEMENT BANNER (Section 8)
            if (announcementText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonPurple.copy(alpha = 0.2f))
                        .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "📢 ${announcementText!!}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // PERMISSION / ERROR WARNING BANNER
            AnimatedVisibility(visible = !hasMicPermission || errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!hasMicPermission) CyberAmber.copy(alpha = 0.2f) else CyberRed.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(CyberAmber, CyberRed)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Alert",
                            tint = CyberAmber
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (!hasMicPermission) {
                                "Mic permission off hai. Settings se allow kar do boss! 🎙"
                            } else {
                                errorMessage ?: "Connection retry ho raha hai..."
                            },
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                if (!hasMicPermission) {
                                    viewModel.refreshPermissions()
                                } else {
                                    viewModel.retryConnection()
                                }
                            }
                        ) {
                            Text("Retry", color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // CENTER: ZOYA PRESENTATION (Holographic Anime Avatar or Central Cyber Orb)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (presentationMode == PresentationMode.ANIME_AVATAR) {
                    characterRenderer.RenderCharacter(
                        state = state,
                        emotion = emotion,
                        outputAmplitude = outputAmplitude,
                        modifier = Modifier.testTag("anime_character_view")
                    )
                } else {
                    ZoyaOrbVisualizer(
                        state = state,
                        inputAmplitude = inputAmplitude,
                        outputAmplitude = outputAmplitude,
                        modifier = Modifier.testTag("cyber_orb_view")
                    )
                }
            }

            // LIVE TRANSCRIPT / ZOYA SPEECH STATUS PILL
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated.copy(alpha = 0.85f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.4f), NeonPurple.copy(alpha = 0.4f)))
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (state) {
                            ZoyaState.IDLE -> "Haan boss, bolo 😏 main sun rahi hoon!"
                            ZoyaState.LISTENING -> if (inputAmplitude > 0.08f) "Hearing your voice..." else "Sun rahi hoon... bolte raho 🎙"
                            ZoyaState.THINKING -> "Ek second... main handle kar rahi hoon ⚡"
                            ZoyaState.SPEAKING -> "Zoya speaking ✨"
                            ZoyaState.ERROR -> "Reconnecting to Gemini Live..."
                        },
                        color = when (state) {
                            ZoyaState.LISTENING -> NeonCyan
                            ZoyaState.THINKING -> NeonMagenta
                            ZoyaState.SPEAKING -> NeonPurple
                            else -> TextSecondary
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (transcript.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"$transcript\"",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 3
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // QUICK VOICE PROMPTS CAROUSEL
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickChips) { (label, command) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(20.dp))
                            .clickable {
                                viewModel.handleVoiceOrTextCommand(command)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // BOTTOM CONTROL BAR: Mute, Main Voice Session, Interruption, Keyboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Keyboard / Text fallback
                IconButton(
                    onClick = { showTextInputDialog = true },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .testTag("text_input_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = "Type Command",
                        tint = TextSecondary
                    )
                }

                // Mic Mute / Unmute
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) CyberRed.copy(alpha = 0.2f) else DarkSurfaceElevated)
                        .border(1.dp, if (isMuted) CyberRed else DarkSurfaceHighlight, CircleShape)
                        .testTag("mic_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Mic Toggle",
                        tint = if (isMuted) CyberRed else NeonCyan
                    )
                }

                // PRIMARY ACTION: Start / Stop Voice Session Button
                Button(
                    onClick = {
                        if (isSessionActive) {
                            viewModel.stopVoiceSession()
                        } else {
                            viewModel.startVoiceSession()
                        }
                    },
                    modifier = Modifier
                        .height(58.dp)
                        .padding(horizontal = 4.dp)
                        .testTag("main_voice_action_button"),
                    shape = RoundedCornerShape(29.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSessionActive) NeonMagenta else NeonCyan
                    )
                ) {
                    Icon(
                        imageVector = if (isSessionActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isSessionActive) "Stop" else "Talk to Zoya",
                        tint = VoidBlack
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSessionActive) "End Session" else "Start Voice",
                        color = VoidBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // INSTANT INTERRUPTION BUTTON: "Interrupt / Shh"
                IconButton(
                    onClick = { viewModel.interruptZoya() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkSurfaceHighlight, CircleShape)
                        .testTag("interrupt_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Interrupt Zoya",
                        tint = CyberAmber
                    )
                }
            }
        }

        // TEXT COMMAND INPUT DIALOG (Secondary accessibility fallback)
        if (showTextInputDialog) {
            AlertDialog(
                onDismissRequest = { showTextInputDialog = false },
                containerColor = DarkSurfaceElevated,
                title = {
                    Text(
                        text = "Talk to Zoya ✨",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Hinglish ya English mein bolo ya type karo:",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = textPromptInput,
                            onValueChange = { textPromptInput = it },
                            placeholder = { Text("Ex: Ek joke sunao Zoya!", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("text_prompt_input_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkSurfaceHighlight,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (textPromptInput.isNotBlank()) {
                                        viewModel.handleVoiceOrTextCommand(textPromptInput.trim())
                                        textPromptInput = ""
                                        showTextInputDialog = false
                                    }
                                }
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (textPromptInput.isNotBlank()) {
                                viewModel.handleVoiceOrTextCommand(textPromptInput.trim())
                                textPromptInput = ""
                                showTextInputDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.testTag("dialog_send_button")
                    ) {
                        Text("Send", color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextInputDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // LEVEL 2 CONFIRMATION SYSTEM DIALOG (Section 15)
        if (pendingConfirmation != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelPendingAction() },
                containerColor = DarkSurfaceElevated,
                title = {
                    Text(
                        text = pendingConfirmation!!.title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = pendingConfirmation!!.description,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmPendingAction() },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.testTag("confirm_action_button")
                    ) {
                        Text("Confirm ✓", color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.cancelPendingAction() },
                        modifier = Modifier.testTag("cancel_action_button")
                    ) {
                        Text("Cancel ✕", color = TextSecondary)
                    }
                }
            )
        }
    }
}
