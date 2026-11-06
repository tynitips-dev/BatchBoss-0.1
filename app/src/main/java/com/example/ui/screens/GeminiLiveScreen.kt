package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.geminilive.GeminiLiveService
import com.example.data.remote.geminilive.LiveChatMessage
import com.example.data.remote.geminilive.LiveConnectionStatus
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BatchPink
import com.example.ui.theme.BatchPinkContainer
import com.example.ui.theme.BatchPinkDark
import com.example.ui.theme.BatchPinkLight
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CardBackground
import com.example.ui.theme.DarkText
import com.example.ui.theme.LightText
import com.example.ui.theme.MediumText
import com.example.ui.theme.MintGreen
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.WarmAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GeminiLiveScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val connectionStatus by GeminiLiveService.connectionStatus.collectAsStateWithLifecycle()
    val messages by GeminiLiveService.messages.collectAsStateWithLifecycle()
    val isMicMuted by GeminiLiveService.isMicMuted.collectAsStateWithLifecycle()
    val isSpeakerMuted by GeminiLiveService.isSpeakerMuted.collectAsStateWithLifecycle()
    val selectedModel by GeminiLiveService.selectedModel.collectAsStateWithLifecycle()
    val selectedVoice by GeminiLiveService.selectedVoice.collectAsStateWithLifecycle()
    val micLevel by GeminiLiveService.micLevel.collectAsStateWithLifecycle()
    val assistantAudioLevel by GeminiLiveService.assistantAudioLevel.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var customApiKeyInput by remember { mutableStateOf("") }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            GeminiLiveService.connect(context)
        }
    }

    // Auto-scroll messages
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Disconnect when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            if (connectionStatus is LiveConnectionStatus.Connected || connectionStatus is LiveConnectionStatus.Connecting) {
                GeminiLiveService.disconnect()
            }
        }
    }

    BackHandler {
        if (connectionStatus is LiveConnectionStatus.Connected || connectionStatus is LiveConnectionStatus.Connecting) {
            GeminiLiveService.disconnect()
        }
        onBack()
    }

    val quickPrompts = remember {
        listOf(
            "How many grams in 3 cups flour?",
            "Substitute for buttermilk in cake?",
            "Why did my sourdough collapse?",
            "Scale 12 muffin recipe to 75",
            "Butter conversion 250g to cups",
            "Calculate batch profit margin"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("screen_gemini_live")
    ) {
        // Top Header
        Surface(
            color = SurfaceWhite,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (connectionStatus is LiveConnectionStatus.Connected) {
                                GeminiLiveService.disconnect()
                            }
                            onBack()
                        },
                        modifier = Modifier.testTag("btn_back_gemini_live")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Gemini Live Voice",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BatchPinkLight
                            ) {
                                Text(
                                    text = if (selectedModel.contains("3.8")) "3.8 Live" else "2.0 Live",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BatchPink,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Live status indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (connectionStatus) {
                                            is LiveConnectionStatus.Connected -> MintGreen
                                            is LiveConnectionStatus.Connecting -> WarmAmber
                                            is LiveConnectionStatus.Error -> Color(0xFFD32F2F)
                                            LiveConnectionStatus.Disconnected -> LightText
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (connectionStatus) {
                                    is LiveConnectionStatus.Connected -> "Live Session Active • Voice: $selectedVoice"
                                    is LiveConnectionStatus.Connecting -> "Connecting to Live API..."
                                    is LiveConnectionStatus.Error -> "Connection Issue"
                                    LiveConnectionStatus.Disconnected -> "Ready to Connect"
                                },
                                fontSize = 11.sp,
                                color = MediumText
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("btn_gemini_live_settings")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = DarkText)
                    }
                    IconButton(
                        onClick = { GeminiLiveService.clearMessages() },
                        modifier = Modifier.testTag("btn_clear_live_transcript")
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Clear transcript", tint = MediumText)
                    }
                }
            }
        }

        // Animated Audio Orb / Visualizer Header Area
        Surface(
            color = CardBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Voice Orb
                LiveAudioOrb(
                    connectionStatus = connectionStatus,
                    micLevel = if (isMicMuted) 0f else micLevel,
                    assistantAudioLevel = if (isSpeakerMuted) 0f else assistantAudioLevel,
                    onConnectClick = {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (connectionStatus is LiveConnectionStatus.Connected || connectionStatus is LiveConnectionStatus.Connecting) {
                                GeminiLiveService.disconnect()
                            } else {
                                val currentKey = GeminiLiveService.getEffectiveApiKey(context)
                                if (currentKey.isBlank()) {
                                    showApiKeyDialog = true
                                } else {
                                    GeminiLiveService.connect(context)
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Voice Controls Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic Mute Toggle
                    IconButton(
                        onClick = { GeminiLiveService.toggleMic() },
                        enabled = connectionStatus is LiveConnectionStatus.Connected,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isMicMuted) Color(0xFFFFEBEE) else BatchPinkLight)
                            .testTag("btn_toggle_mic")
                    ) {
                        Icon(
                            if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) "Unmute Mic" else "Mute Mic",
                            tint = if (isMicMuted) Color(0xFFD32F2F) else BatchPink
                        )
                    }

                    // Main Connect / End Call Button
                    Button(
                        onClick = {
                            if (!hasMicPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                if (connectionStatus is LiveConnectionStatus.Connected || connectionStatus is LiveConnectionStatus.Connecting) {
                                    GeminiLiveService.disconnect()
                                } else {
                                    val currentKey = GeminiLiveService.getEffectiveApiKey(context)
                                    if (currentKey.isBlank()) {
                                        showApiKeyDialog = true
                                    } else {
                                        GeminiLiveService.connect(context)
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (connectionStatus) {
                                is LiveConnectionStatus.Connected -> Color(0xFFD32F2F)
                                is LiveConnectionStatus.Connecting -> WarmAmber
                                else -> BatchPink
                            }
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_connect_live")
                    ) {
                        Icon(
                            if (connectionStatus is LiveConnectionStatus.Connected) Icons.Default.CallEnd else Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (connectionStatus) {
                                is LiveConnectionStatus.Connected -> "End Live Call"
                                is LiveConnectionStatus.Connecting -> "Connecting..."
                                else -> "Start Live Voice"
                            },
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Speaker Mute Toggle
                    IconButton(
                        onClick = { GeminiLiveService.toggleSpeaker() },
                        enabled = connectionStatus is LiveConnectionStatus.Connected,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isSpeakerMuted) Color(0xFFFFEBEE) else BatchPinkLight)
                            .testTag("btn_toggle_speaker")
                    ) {
                        Icon(
                            if (isSpeakerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isSpeakerMuted) "Unmute Audio" else "Mute Audio",
                            tint = if (isSpeakerMuted) Color(0xFFD32F2F) else BatchPink
                        )
                    }
                }
            }
        }

        // Quick Prompts Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { prompt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.clickable {
                        if (connectionStatus !is LiveConnectionStatus.Connected) {
                            if (!hasMicPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                GeminiLiveService.connect(context)
                            }
                        }
                        GeminiLiveService.sendTextMessage(prompt)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = prompt, fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Live Conversation Transcript List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        tint = BatchPink.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Real-Time Bakery Voice Assistant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DarkText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Powered by Google's Gemini 3.8 Live API.\nTap 'Start Live Voice' to talk hands-free while baking, asking about recipe scaling, substitutions, oven times, or costs.",
                        fontSize = 13.sp,
                        color = MediumText,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        LiveMessageBubble(msg)
                    }
                }
            }
        }

        // Bottom Text Input Bar (Alternative to speaking)
        Surface(
            color = SurfaceWhite,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask voice assistant or type...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_live_text_message")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val toSend = textInput.trim()
                        if (toSend.isNotBlank()) {
                            if (connectionStatus !is LiveConnectionStatus.Connected) {
                                GeminiLiveService.connect(context)
                            }
                            GeminiLiveService.sendTextMessage(toSend)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BatchPink)
                        .testTag("btn_send_live_text")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Settings & Voice Dialog
    if (showSettingsDialog) {
        LiveSettingsDialog(
            currentModel = selectedModel,
            currentVoice = selectedVoice,
            onSelectModel = { GeminiLiveService.setModel(it) },
            onSelectVoice = { GeminiLiveService.setVoice(it) },
            onOpenApiKeyDialog = {
                showSettingsDialog = false
                showApiKeyDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = BatchPink)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini API Key", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter a Google Gemini API Key with Live API access. Keys can also be configured via the AI Studio Secrets panel.",
                        fontSize = 12.sp,
                        color = MediumText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customApiKeyInput,
                        onValueChange = { customApiKeyInput = it },
                        label = { Text("API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_gemini_api_key")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customApiKeyInput.isNotBlank()) {
                            GeminiLiveService.saveCustomApiKey(context, customApiKeyInput.trim())
                        }
                        showApiKeyDialog = false
                        GeminiLiveService.connect(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LiveAudioOrb(
    connectionStatus: LiveConnectionStatus,
    micLevel: Float,
    assistantAudioLevel: Float,
    onConnectClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Dynamic scale driven by audio volume
    val dynamicAudioScale = when {
        assistantAudioLevel > 0.05f -> 1.0f + (assistantAudioLevel * 0.4f)
        micLevel > 0.05f -> 1.0f + (micLevel * 0.35f)
        connectionStatus is LiveConnectionStatus.Connected -> pulseScale
        connectionStatus is LiveConnectionStatus.Connecting -> pulseScale
        else -> 1.0f
    }

    val orbColor1 by animateColorAsState(
        targetValue = when {
            assistantAudioLevel > 0.05f -> Color(0xFF9C27B0) // Purple speaking
            micLevel > 0.05f -> Color(0xFF00C853) // Green listening
            connectionStatus is LiveConnectionStatus.Connected -> BatchPink
            connectionStatus is LiveConnectionStatus.Connecting -> WarmAmber
            else -> Color(0xFF78909C)
        },
        label = "orb_color1"
    )

    val orbColor2 by animateColorAsState(
        targetValue = when {
            assistantAudioLevel > 0.05f -> Color(0xFFE91E63)
            micLevel > 0.05f -> Color(0xFF64DD17)
            connectionStatus is LiveConnectionStatus.Connected -> Color(0xFFFF5278)
            connectionStatus is LiveConnectionStatus.Connecting -> Color(0xFFFFB300)
            else -> Color(0xFFB0BEC5)
        },
        label = "orb_color2"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .scale(dynamicAudioScale.coerceIn(0.9f, 1.4f))
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(orbColor1, orbColor2, orbColor1.copy(alpha = 0.4f))
                    )
                )
                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onConnectClick)
                .testTag("orb_live_audio")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    when {
                        assistantAudioLevel > 0.05f -> Icons.Default.VolumeUp
                        micLevel > 0.05f -> Icons.Default.Mic
                        connectionStatus is LiveConnectionStatus.Connected -> Icons.Outlined.AutoAwesome
                        else -> Icons.Default.Phone
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        assistantAudioLevel > 0.05f -> "Speaking"
                        micLevel > 0.05f -> "Listening"
                        connectionStatus is LiveConnectionStatus.Connected -> "Live"
                        connectionStatus is LiveConnectionStatus.Connecting -> "Connecting"
                        else -> "Tap to Start"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun LiveMessageBubble(message: LiveChatMessage) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"

    val timeStr = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    if (isSystem) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
            ) {
                Text(
                    text = message.text,
                    fontSize = 11.sp,
                    color = MediumText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) BatchPink else SurfaceWhite,
            shadowElevation = 1.dp,
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, BorderLight) else null,
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "Gemini Live Assistant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isUser) Color.White.copy(alpha = 0.9f) else BatchPinkDark
                    )
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else LightText
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = if (isUser) Color.White else DarkText,
                    lineHeight = 20.sp
                )
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "● Speaking...",
                        fontSize = 10.sp,
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else BatchPink
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveSettingsDialog(
    currentModel: String,
    currentVoice: String,
    onSelectModel: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Gemini Live Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Model Picker
                Text("Select Live Model", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DarkText)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (currentModel == GeminiLiveService.DEFAULT_MODEL) BatchPinkLight else CardBackground,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (currentModel == GeminiLiveService.DEFAULT_MODEL) BatchPink else BorderLight
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectModel(GeminiLiveService.DEFAULT_MODEL) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("gemini-3.8-live", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BatchPink)
                            Text("Recommended for live voice", fontSize = 10.sp, color = MediumText)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (currentModel == GeminiLiveService.FALLBACK_MODEL) BatchPinkLight else CardBackground,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (currentModel == GeminiLiveService.FALLBACK_MODEL) BatchPink else BorderLight
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectModel(GeminiLiveService.FALLBACK_MODEL) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("gemini-2.0-flash", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                            Text("Experimental live model", fontSize = 10.sp, color = MediumText)
                        }
                    }
                }

                // Voice Picker
                Text("Voice Personality", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DarkText)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GeminiLiveService.AVAILABLE_VOICES.forEach { voice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectVoice(voice.name) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentVoice == voice.name,
                                onClick = { onSelectVoice(voice.name) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(voice.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                                Text(voice.description, fontSize = 11.sp, color = MediumText)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Configure API Key Button
                Button(
                    onClick = onOpenApiKeyDialog,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPinkContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = BatchPinkDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Gemini API Key", color = BatchPinkDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
            ) {
                Text("Done")
            }
        }
    )
}
