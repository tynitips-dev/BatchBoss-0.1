package com.example.data.remote.geminilive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

sealed interface LiveConnectionStatus {
    data object Disconnected : LiveConnectionStatus
    data object Connecting : LiveConnectionStatus
    data object Connected : LiveConnectionStatus
    data class Error(val message: String) : LiveConnectionStatus
}

data class LiveChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "model", or "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class VoiceOption(
    val name: String,
    val label: String,
    val description: String
)

object GeminiLiveService {
    private const val TAG = "GeminiLiveService"

    // Supported Voices
    val AVAILABLE_VOICES = listOf(
        VoiceOption("Aoede", "Aoede", "Warm & expressive soprano voice"),
        VoiceOption("Puck", "Puck", "Energetic, clear & friendly"),
        VoiceOption("Charon", "Charon", "Calm, deep & authoritative"),
        VoiceOption("Kore", "Kore", "Smooth, relaxed & gentle"),
        VoiceOption("Fenrir", "Fenrir", "Strong, crisp & confident")
    )

    const val DEFAULT_MODEL = "models/gemini-3.8-live"
    const val FALLBACK_MODEL = "models/gemini-live-2.5-flash-native-audio"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordJob: Job? = null
    private var playJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep WebSocket open indefinitely
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    // State Flows
    private val _connectionStatus = MutableStateFlow<LiveConnectionStatus>(LiveConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<LiveConnectionStatus> = _connectionStatus.asStateFlow()

    private val _messages = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val messages: StateFlow<List<LiveChatMessage>> = _messages.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isSpeakerMuted = MutableStateFlow(false)
    val isSpeakerMuted: StateFlow<Boolean> = _isSpeakerMuted.asStateFlow()

    private val _selectedModel = MutableStateFlow(DEFAULT_MODEL)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _selectedVoice = MutableStateFlow("Aoede")
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _assistantAudioLevel = MutableStateFlow(0f)
    val assistantAudioLevel: StateFlow<Float> = _assistantAudioLevel.asStateFlow()

    // Audio Hardware
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()

    private var activeApiKey: String = ""
    private var isRecording = false

    fun getEffectiveApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("batchboss_gemini_prefs", Context.MODE_PRIVATE)
        val customKey = prefs.getString("gemini_api_key", "")?.trim().orEmpty()
        if (customKey.isNotBlank()) return customKey

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun saveCustomApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences("batchboss_gemini_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun setModel(model: String) {
        _selectedModel.value = model
    }

    fun setVoice(voice: String) {
        _selectedVoice.value = voice
    }

    fun toggleMic() {
        _isMicMuted.value = !_isMicMuted.value
    }

    fun toggleSpeaker() {
        _isSpeakerMuted.value = !_isSpeakerMuted.value
        if (_isSpeakerMuted.value) {
            audioQueue.clear()
            audioTrack?.pause()
            audioTrack?.flush()
            _assistantAudioLevel.value = 0f
        } else {
            audioTrack?.play()
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun connect(context: Context, customKeyOverride: String? = null) {
        if (_connectionStatus.value is LiveConnectionStatus.Connecting ||
            _connectionStatus.value is LiveConnectionStatus.Connected
        ) {
            return
        }

        val key = customKeyOverride?.trim()?.ifBlank { null } ?: getEffectiveApiKey(context)
        if (key.isBlank()) {
            _connectionStatus.value = LiveConnectionStatus.Error(
                "Gemini API key is missing. Please provide a valid Gemini API key to start real-time voice conversations."
            )
            return
        }
        activeApiKey = key

        _connectionStatus.value = LiveConnectionStatus.Connecting
        addSystemMessage("Connecting to Gemini Live (${_selectedModel.value.substringAfterLast('/')})...")

        val endpointUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$activeApiKey"

        val request = Request.Builder()
            .url(endpointUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully. Sending BidiGenerateContentSetup...")
                sendSetupMessage(webSocket)
                initAudioTrack()
                startAudioPlaybackLoop()
                startAudioRecording(context)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingAudioBytes(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed")
                cleanupAudio()
                _connectionStatus.value = LiveConnectionStatus.Disconnected
                addSystemMessage("Gemini Live session ended.")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                cleanupAudio()
                val responseMsg = response?.message.orEmpty()
                val errorText = when {
                    t.message?.contains("404") == true || responseMsg.contains("404") -> {
                        if (_selectedModel.value == DEFAULT_MODEL) {
                            // Automatically attempt fallback
                            serviceScope.launch {
                                delay(500)
                                addSystemMessage("Model '${_selectedModel.value}' not available. Retrying with fallback '$FALLBACK_MODEL'...")
                                _selectedModel.value = FALLBACK_MODEL
                                connect(context, activeApiKey)
                            }
                            return
                        }
                        "Model not found on live endpoint. Please verify API key access."
                    }
                    t.message?.contains("400") == true || responseMsg.contains("400") -> {
                        "Invalid connection request (HTTP 400). Please check your Gemini API key."
                    }
                    t.message?.contains("403") == true || responseMsg.contains("403") -> {
                        "Access denied (HTTP 403). Please ensure your Gemini API key has Live API permissions."
                    }
                    else -> t.localizedMessage ?: "Connection error. Please try again."
                }
                _connectionStatus.value = LiveConnectionStatus.Error(errorText)
                addSystemMessage("Error: $errorText")
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                val setupObj = JSONObject().apply {
                    put("model", _selectedModel.value)

                    val genConfig = JSONObject().apply {
                        val modalities = JSONArray().apply {
                            put("AUDIO")
                        }
                        put("responseModalities", modalities)

                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuilt = JSONObject().apply {
                                    put("voiceName", _selectedVoice.value)
                                }
                                put("prebuiltVoiceConfig", prebuilt)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", genConfig)

                    val systemInstruction = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            val part = JSONObject().apply {
                                put(
                                    "text",
                                    "You are Ask BatchBoss, a friendly South African bakery assistant. " +
                                        "Help bakers with recipe scaling, substitutions, baking times, ingredient costing and pricing calculations in South African rand. " +
                                        "Ask for missing quantities before estimating; distinguish assumptions from known facts. " +
                                        "Important: this voice session does not yet have authorised tool access to the baker's saved customers, invoices, stock, recipes or orders. " +
                                        "Never claim to have accessed private bakery records or created, updated, sent or saved an invoice, order or inventory item. " +
                                        "You may help prepare a draft in conversation, clearly state that it has not been saved, and remind the user to review it in BatchBoss. " +
                                        "Ingredient prices are estimates, not live retailer quotes. Keep spoken answers concise and helpful."
                                )
                            }
                            put(part)
                        }
                        put("parts", partsArray)
                    }
                    put("systemInstruction", systemInstruction)
                }
                put("setup", setupObj)
            }

            ws.send(setupJson.toString())
            Log.d(TAG, "Sent setup JSON for model: ${_selectedModel.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup message", e)
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Setup complete
            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete confirmed by server.")
                _connectionStatus.value = LiveConnectionStatus.Connected
                addSystemMessage("Connected! You can now speak with the BatchBoss Voice Assistant.")
                return
            }

            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                // Interruption check (barge-in)
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server reported interruption. Clearing audio playback.")
                    audioQueue.clear()
                    audioTrack?.pause()
                    audioTrack?.flush()
                    audioTrack?.play()
                    _assistantAudioLevel.value = 0f
                    return
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue

                            // 1. Text transcript chunk
                            val chunkText = part.optString("text", "")
                            if (chunkText.isNotBlank()) {
                                appendModelMessage(chunkText)
                            }

                            // 2. Audio data chunk (PCM 24kHz)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val b64Data = inlineData.optString("data", "")
                                if (b64Data.isNotBlank()) {
                                    val audioBytes = Base64.decode(b64Data, Base64.DEFAULT)
                                    handleIncomingAudioBytes(audioBytes)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    markModelMessageComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming WebSocket message: $text", e)
        }
    }

    private fun handleIncomingAudioBytes(audioBytes: ByteArray) {
        if (_isSpeakerMuted.value || audioBytes.isEmpty()) return
        audioQueue.add(audioBytes)

        // Calculate RMS audio level for visualizer
        val rms = calculateRms(audioBytes)
        _assistantAudioLevel.value = (rms / 32768f).coerceIn(0f, 1f)
    }

    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _connectionStatus.value !is LiveConnectionStatus.Connected) return

        // Add to local UI
        _messages.value = _messages.value + LiveChatMessage(
            role = "user",
            text = trimmed
        )

        serviceScope.launch {
            try {
                val clientMsg = JSONObject().apply {
                    val clientContent = JSONObject().apply {
                        val turns = JSONArray().apply {
                            val turn = JSONObject().apply {
                                put("role", "user")
                                val parts = JSONArray().apply {
                                    val part = JSONObject().apply {
                                        put("text", trimmed)
                                    }
                                    put(part)
                                }
                                put("parts", parts)
                            }
                            put(turn)
                        }
                        put("turns", turns)
                        put("turnComplete", true)
                    }
                    put("clientContent", clientContent)
                }

                webSocket?.send(clientMsg.toString())
                Log.d(TAG, "Sent client text turn: $trimmed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send text message", e)
            }
        }
    }

    private fun startAudioRecording(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Microphone permission not granted. Audio recording skipped.")
            return
        }

        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufSize, 3200) // ~100ms chunk

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not be initialized")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordJob?.cancel()
            recordJob = serviceScope.launch {
                val buffer = ByteArray(3200)
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        val rms = calculateRms(chunk)
                        val normalized = (rms / 32768f).coerceIn(0f, 1f)
                        _micLevel.value = normalized

                        if (!_isMicMuted.value && _connectionStatus.value is LiveConnectionStatus.Connected) {
                            sendAudioChunk(chunk)
                        }
                    } else {
                        delay(20)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord", e)
        }
    }

    private fun sendAudioChunk(pcmBytes: ByteArray) {
        try {
            val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val json = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunk = JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        }
                        put(chunk)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk", e)
        }
    }

    private fun initAudioTrack() {
        try {
            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf * 2, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    private fun startAudioPlaybackLoop() {
        playJob?.cancel()
        playJob = serviceScope.launch {
            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null && !_isSpeakerMuted.value) {
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (audioQueue.isEmpty()) {
                        _assistantAudioLevel.value = 0f
                    }
                    delay(15)
                }
            }
        }
    }

    private fun calculateRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        var sum = 0.0
        val sampleCount = pcmBytes.size / 2
        for (i in 0 until sampleCount) {
            val sample = (pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)
            sum += sample * sample
        }
        return sqrt(sum / sampleCount).toFloat()
    }

    private fun appendModelMessage(textChunk: String) {
        val currentList = _messages.value
        val lastMessage = currentList.lastOrNull()
        if (lastMessage != null && lastMessage.role == "model" && lastMessage.isStreaming) {
            val updated = lastMessage.copy(text = lastMessage.text + textChunk)
            _messages.value = currentList.dropLast(1) + updated
        } else {
            _messages.value = currentList + LiveChatMessage(
                role = "model",
                text = textChunk,
                isStreaming = true
            )
        }
    }

    private fun markModelMessageComplete() {
        val currentList = _messages.value
        val lastMessage = currentList.lastOrNull()
        if (lastMessage != null && lastMessage.role == "model" && lastMessage.isStreaming) {
            val updated = lastMessage.copy(isStreaming = false)
            _messages.value = currentList.dropLast(1) + updated
        }
    }

    private fun addSystemMessage(text: String) {
        _messages.value = _messages.value + LiveChatMessage(
            role = "system",
            text = text
        )
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting Gemini Live session")
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        cleanupAudio()
        _connectionStatus.value = LiveConnectionStatus.Disconnected
        _micLevel.value = 0f
        _assistantAudioLevel.value = 0f
    }

    private fun cleanupAudio() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        playJob?.cancel()
        playJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
        audioQueue.clear()
    }
}
