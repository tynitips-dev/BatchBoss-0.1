package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PublicSupplier(val id: String, val businessName: String, val category: String, val city: String, val province: String, val description: String, val delivery: String)
data class PublicSupplierPage(val suppliers: List<PublicSupplier>, val nextCursor: String?)

/** Public callable protocol, with no baker credentials and no supplier-project sign-in. */
object PublicSupplierDirectory {
    private val idPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    fun validId(value: String) = idPattern.matches(value)
    fun parse(text: String): PublicSupplierPage {
        val envelope = JSONObject(text)
        require(!envelope.has("error")) { "Supplier directory is unavailable." }
        val page = envelope.optJSONObject("result") ?: envelope.getJSONObject("data")
        val cursor = if (page.isNull("nextCursor")) null else page.getString("nextCursor").also { require(validId(it)) }
        val rows = page.getJSONArray("suppliers")
        require(rows.length() <= 25)
        val suppliers = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val fields = listOf("id", "businessName", "category", "city", "province", "description", "delivery")
            if (fields.any { row.opt(it) !is String || row.getString(it).length > 2000 }) return@mapNotNull null
            if (!validId(row.getString("id")) || row.getString("category") !in listOf("Ingredients", "Packaging", "Equipment", "Decorating")) return@mapNotNull null
            PublicSupplier(row.getString("id"), row.getString("businessName"), row.getString("category"), row.getString("city"), row.getString("province"), row.getString("description"), row.getString("delivery"))
        }
        return PublicSupplierPage(suppliers, cursor)
    }
    suspend fun load(cursor: String? = null): PublicSupplierPage = withContext(Dispatchers.IO) {
        require(cursor == null || validId(cursor))
        val connection = URL("https://us-central1-batchboss-suppliers.cloudfunctions.net/listPublicSuppliers").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            val data = JSONObject().put("limit", 25).also { if (cursor != null) it.put("cursor", cursor) }
            connection.outputStream.use { it.write(JSONObject().put("data", data).toString().toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200) { "Supplier directory is unavailable." }
            val bytes = connection.inputStream.use { input ->
                val result = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) { val size = input.read(buffer); if (size < 0) break; result.write(buffer, 0, size); require(result.size() <= 262144) }
                result.toByteArray()
            }
            parse(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
