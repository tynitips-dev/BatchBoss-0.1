package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class PublicSupplierAd(val id: String, val title: String, val description: String, val businessName: String, val artworkUrl: String)

/** Public advertising only. Never sends bakery or supplier authentication. */
object PublicSupplierAds {
    private const val host = "us-central1-batchboss-suppliers.cloudfunctions.net"
    suspend fun load(): List<PublicSupplierAd> = withContext(Dispatchers.IO) {
        val connection = URL("https://$host/listPublicSupplierAds").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write("""{"data":{"placement":"marketplace-banner"}}""".toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200)
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count); require(output.size() <= 262144) }
                output.toByteArray()
            }
            val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
            require(!envelope.has("error"))
            val rows = (envelope.optJSONObject("result") ?: envelope.getJSONObject("data")).getJSONArray("ads")
            require(rows.length() <= 20)
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                if (listOf("id", "title", "description", "businessName", "artworkUrl").any { row.opt(it) !is String || row.getString(it).length > 2000 }) return@mapNotNull null
                val id = row.getString("id")
                if (!PublicSupplierDirectory.validId(id)) return@mapNotNull null
                val image = runCatching { URI(row.getString("artworkUrl")) }.getOrNull() ?: return@mapNotNull null
                if (image.scheme != "https" || image.host != host || image.path != "/getPublicSupplierAdArtwork" || image.rawQuery != "ad=$id" || image.userInfo != null || image.port != -1) return@mapNotNull null
                PublicSupplierAd(id, row.getString("title"), row.getString("description"), row.getString("businessName"), image.toString())
            }.take(3)
        } finally { connection.disconnect() }
    }
}
