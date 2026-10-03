package com.example.presentation.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.security.PermissionManager
import com.example.security.PermissionRequirement
import com.example.security.PermissionState
import com.example.security.PrivacyManager
import com.example.ui.theme.CyberGreen
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
fun OnboardingScreen(
    permissionManager: PermissionManager,
    privacyManager: PrivacyManager,
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionStates by permissionManager.permissionStates.collectAsState()

    // Activity result launcher for single permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionManager.refreshAll()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VoidBlack, DarkSurface, Color(0xFF070B14))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER HERO
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .border(2.dp, Brush.linearGradient(listOf(NeonCyan, NeonMagenta)), CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.zoya_avatar_1791042285554),
                    contentDescription = "Zoya Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Welcome to Zoya ✨",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "“Main tumhari personal AI assistant hoon.\nTum decide karoge ki mujhe kya access milega.”",
                color = NeonCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // LIST OF PROGRESSIVE PERMISSION CARDS
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(PermissionRequirement.ONBOARDING_ITEMS) { req ->
                    val state = permissionStates[req.key] ?: permissionManager.checkPermission(req)
                    val isGranted = state == PermissionState.GRANTED

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permission_card_${req.key}"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(16.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = if (isGranted) {
                                Brush.linearGradient(listOf(CyberGreen.copy(alpha = 0.6f), CyberGreen.copy(alpha = 0.2f)))
                            } else {
                                Brush.linearGradient(listOf(DarkSurfaceHighlight, DarkSurfaceHighlight))
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon Box
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isGranted) CyberGreen.copy(alpha = 0.15f) else DarkSurfaceHighlight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (req) {
                                        is PermissionRequirement.Microphone -> Icons.Filled.Mic
                                        is PermissionRequirement.Notifications -> Icons.Filled.Notifications
                                        is PermissionRequirement.Contacts -> Icons.Filled.Contacts
                                        is PermissionRequirement.PhoneCalls -> Icons.Filled.Phone
                                        is PermissionRequirement.ScreenAssistant -> Icons.Filled.Visibility
                                        is PermissionRequirement.FloatingOrb -> Icons.Filled.Widgets
                                    },
                                    contentDescription = req.title,
                                    tint = if (isGranted) CyberGreen else NeonCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Text Content
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = req.title,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (req.isCritical) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Required",
                                            color = NeonMagenta,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = req.description,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Action Button or Check
                            if (isGranted) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(CyberGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Granted",
                                        tint = CyberGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        if (req is PermissionRequirement.FloatingOrb) {
                                            permissionManager.openOverlaySettings(context)
                                        } else if (req.permissionName.isNotEmpty()) {
                                            permissionLauncher.launch(req.permissionName)
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.testTag("grant_button_${req.key}")
                                ) {
                                    Text("Allow", fontSize = 12.sp, color = NeonCyan)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // FINISH / LAUNCH BUTTON
            Button(
                onClick = {
                    privacyManager.setOnboardingCompleted(true)
                    onCompleteOnboarding()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("launch_zoya_button"),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Icon(
                    imageVector = Icons.Filled.RocketLaunch,
                    contentDescription = null,
                    tint = VoidBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ready to Launch 🚀",
                    color = VoidBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
