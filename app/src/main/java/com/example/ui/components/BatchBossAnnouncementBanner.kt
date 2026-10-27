package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BatchPinkLight
import com.example.ui.theme.DarkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

private data class Announcement(val id: String, val message: String, val href: String?, val label: String)

private fun announcementTime(value: String): Long? {
    val match = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,3}))?(Z|[+-]\\d{2}:\\d{2})$").matchEntire(value) ?: return null
    val fraction = match.groupValues[2].padEnd(3, '0')
    val zone = match.groupValues[3].let { if (it == "Z") "+0000" else it.replace(":", "") }
    val normalized = "${match.groupValues[1]}.$fraction$zone"
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply { isLenient = false }
    val position = ParsePosition(0)
    return format.parse(normalized, position)?.takeIf { position.index == normalized.length }?.time
}

private fun loadAnnouncement(): Announcement? = runCatching {
    val connection = URL("https://app.batchboss.co.za/announcements.json").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        if (connection.responseCode != 200) return@runCatching null
        val text = connection.inputStream.bufferedReader().use { reader ->
            val buffer = CharArray(8193)
            var count = 0
            while (count < buffer.size) { val read = reader.read(buffer, count, buffer.size - count); if (read < 0) break; count += read }
            if (count > 8192) return@runCatching null
            String(buffer, 0, count)
        }
        val item = JSONObject(text)
        val id = item.optString("id").trim()
        val message = item.optString("message").trim()
        if (!item.optBoolean("enabled", false) || id.isEmpty() || id.length > 100 || message.isEmpty() || message.length > 300) return@runCatching null
        val now = System.currentTimeMillis()
        for (key in listOf("startsAt", "expiresAt")) {
            if (!item.isNull(key) && item.optString(key).isNotBlank()) {
                val time = announcementTime(item.optString(key)) ?: return@runCatching null
                if ((key == "startsAt" && now < time) || (key == "expiresAt" && now >= time)) return@runCatching null
            }
        }
        val href = runCatching { URI(item.optString("href")) }.getOrNull()?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }?.toString()
        Announcement(id, message, href, item.optString("linkLabel", "Learn more").take(60).ifBlank { "Learn more" })
    } finally { connection.disconnect() }
}.getOrNull()

@Composable
fun BatchBossAnnouncementBanner() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val preferences = remember(context) { context.getSharedPreferences("batchboss_announcements", Context.MODE_PRIVATE) }
    var announcement by remember { mutableStateOf<Announcement?>(null) }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { loadAnnouncement() }
        if (loaded != null && preferences.getString("dismissed_id", null) != loaded.id) announcement = loaded
    }
    val item = announcement ?: return
    Surface(color = BatchPinkLight, contentColor = DarkText, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("BATCHBOSS UPDATE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(item.message, style = MaterialTheme.typography.bodyMedium)
                if (item.href != null) TextButton(onClick = { runCatching { uriHandler.openUri(item.href) } }) { Text(item.label) }
            }
            IconButton(onClick = { preferences.edit().putString("dismissed_id", item.id).apply(); announcement = null }) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss announcement")
            }
        }
    }
}
