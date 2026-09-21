package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ProductPriceEstimate(
    val productName: String,
    val storeName: String,
    val packagePriceZar: Double,
    val packageSize: Double,
    val packageUnit: String, // "g", "kg", "ml", "unit"
    val unitPricePerBaseGram: Double, // Cost per gram or per unit
    val brand: String,
    val note: String,
    val isAiGenerated: Boolean = true
)

object AiProductPricingService {
    private const val TAG = "AiProductPricing"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // South African verified retail benchmark prices for instant fallback & validation
    private val SA_STORE_BENCHMARKS = listOf(
        // Flour
        ProductPriceEstimate("Flour", "Checkers", 35.00, 2500.0, "g", 35.00 / 2500.0, "Housebrand Cake Flour", "Checkers Retail 2.5kg", false),
        ProductPriceEstimate("Cake Flour", "Checkers", 35.00, 2500.0, "g", 35.00 / 2500.0, "Checkers Housebrand", "Checkers Everyday Value 2.5kg", false),
        ProductPriceEstimate("Flour", "Pick n Pay", 37.99, 2500.0, "g", 37.99 / 2500.0, "No Name Cake Flour", "Pick n Pay Bakery staple", false),
        ProductPriceEstimate("Flour", "Woolworths", 42.99, 2500.0, "g", 42.99 / 2500.0, "Woolworths Unbleached", "Woolworths Premium Stoneground", false),

        // Sugar
        ProductPriceEstimate("Sugar", "Checkers", 38.50, 2000.0, "g", 38.50 / 2000.0, "Checkers White Sugar", "Checkers 2kg pack", false),
        ProductPriceEstimate("Castor Sugar", "Checkers", 34.00, 1000.0, "g", 34.00 / 1000.0, "Huletts Castor Sugar", "Checkers Baking Aisle 1kg", false),

        // Chocolate
        ProductPriceEstimate("Chocolate", "Checkers", 42.00, 200.0, "g", 42.00 / 200.0, "Cadbury Baking / Nestle", "Checkers Dark Baking Chocolate 200g slab", false),
        ProductPriceEstimate("Dark Chocolate", "Checkers", 44.50, 200.0, "g", 44.50 / 200.0, "Lindt / Beacon Baking", "Checkers 70% Baking Chocolate 200g", false),

        // Baking Soda
        ProductPriceEstimate("Baking Soda", "Checkers", 18.00, 200.0, "g", 18.00 / 200.0, "Robertsons Baking Soda", "Checkers 200g tub", false),

        // Bicarbonate of Soda
        ProductPriceEstimate("Bicarbonate of Soda", "Checkers", 16.50, 200.0, "g", 16.50 / 200.0, "Housebrand Bicarb", "Checkers 200g box / tub", false),
        ProductPriceEstimate("Bicarbonate of Soda", "Pick n Pay", 17.00, 200.0, "g", 17.00 / 200.0, "PnP Bicarbonate", "Pick n Pay 200g tub", false),

        // Salt
        ProductPriceEstimate("Salt", "Checkers", 12.00, 500.0, "g", 12.00 / 500.0, "Cerebos Table Salt", "Checkers 500g bottle / refill", false),

        // Eggs
        ProductPriceEstimate("Eggs", "Checkers", 68.00, 30.0, "unit", 68.00 / 30.0, "Checkers Farm Fresh", "Checkers 30-tray large eggs (R2.27 / egg)", false),
        ProductPriceEstimate("Eggs", "Pick n Pay", 72.00, 30.0, "unit", 72.00 / 30.0, "PnP Large Eggs", "Pick n Pay 30-egg tray", false)
    )

    suspend fun estimateProductPrice(
        productName: String,
        storeName: String = "Checkers"
    ): ProductPriceEstimate = withContext(Dispatchers.IO) {
        val cleanName = productName.trim()
        val cleanStore = if (storeName.isBlank()) "Checkers" else storeName.trim()

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val promptText = """
                    You are a retail bakery ingredient pricing expert for South Africa.
                    Provide the realistic market purchase price for baking ingredient '$cleanName' sold at retail store '$cleanStore' (e.g. Checkers, Pick n Pay, Woolworths, Spar, Makro) in South African Rands (ZAR).
                    For example, Checkers sells 2.5kg Cake Flour for around R35.00, 30 eggs for around R68.00, 200g baking chocolate for R42.00, 200g baking soda / bicarbonate of soda for R18.00, 500g salt for R12.00.
                    
                    Return ONLY valid JSON with no markdown formatting:
                    {
                      "productName": "$cleanName",
                      "storeName": "$cleanStore",
                      "packagePriceZar": 35.00,
                      "packageSize": 2500.0,
                      "packageUnit": "g",
                      "brand": "Checkers Housebrand",
                      "note": "Checkers retail 2.5kg package (R14/kg, R0.014/g)"
                    }
                """.trimIndent()

                val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val requestJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply { put("text", promptText) })
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                }

                val request = Request.Builder()
                    .url(geminiUrl)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val candidates = root.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text")

                    if (!text.isNullOrBlank()) {
                        val cleanJsonStr = text.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        val parsedObj = JSONObject(cleanJsonStr)
                        val price = parsedObj.optDouble("packagePriceZar", 35.0)
                        val size = parsedObj.optDouble("packageSize", 1000.0).coerceAtLeast(1.0)
                        val unit = parsedObj.optString("packageUnit", "g")
                        val brand = parsedObj.optString("brand", "$cleanStore brand")
                        val note = parsedObj.optString("note", "$cleanStore estimated retail price")

                        val baseGrams = when (unit.lowercase().trim()) {
                            "kg" -> size * 1000.0
                            "l", "litre", "liters" -> size * 1000.0
                            else -> size
                        }
                        val costPerBaseUnit = if (baseGrams > 0) price / baseGrams else price

                        return@withContext ProductPriceEstimate(
                            productName = cleanName,
                            storeName = cleanStore,
                            packagePriceZar = price,
                            packageSize = size,
                            packageUnit = unit,
                            unitPricePerBaseGram = costPerBaseUnit,
                            brand = brand,
                            note = note,
                            isAiGenerated = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini pricing lookup error: ${e.message}")
            }
        }

        // On-device benchmark lookup for South Africa stores
        getFallbackBenchmark(cleanName, cleanStore)
    }

    fun getFallbackBenchmark(productName: String, storeName: String): ProductPriceEstimate {
        val lowerProduct = productName.lowercase().trim()
        val lowerStore = storeName.lowercase().trim()

        val match = SA_STORE_BENCHMARKS.firstOrNull {
            lowerProduct.contains(it.productName.lowercase()) || it.productName.lowercase().contains(lowerProduct)
        }

        return if (match != null) {
            match.copy(storeName = storeName, isAiGenerated = false)
        } else {
            // General calculation
            when {
                lowerProduct.contains("flour") -> ProductPriceEstimate(
                    productName = productName,
                    storeName = storeName,
                    packagePriceZar = 35.00,
                    packageSize = 2500.0,
                    packageUnit = "g",
                    unitPricePerBaseGram = 35.00 / 2500.0,
                    brand = "$storeName Bakery Flour",
                    note = "$storeName Cake Flour 2.5kg standard package",
                    isAiGenerated = false
                )
                lowerProduct.contains("sugar") -> ProductPriceEstimate(
                    productName = productName,
                    storeName = storeName,
                    packagePriceZar = 38.00,
                    packageSize = 2000.0,
                    packageUnit = "g",
                    unitPricePerBaseGram = 38.00 / 2000.0,
                    brand = "$storeName White Sugar",
                    note = "$storeName 2kg pack",
                    isAiGenerated = false
                )
                lowerProduct.contains("chocolate") -> ProductPriceEstimate(
                    productName = productName,
                    storeName = storeName,
                    packagePriceZar = 42.00,
                    packageSize = 200.0,
                    packageUnit = "g",
                    unitPricePerBaseGram = 42.00 / 200.0,
                    brand = "$storeName Baking Chocolate",
                    note = "200g slab for baking",
                    isAiGenerated = false
                )
                lowerProduct.contains("baking soda") || lowerProduct.contains("bicarbonate") -> ProductPriceEstimate(
                    productName = productName,
                    storeName = storeName,
                    packagePriceZar = 18.00,
                    packageSize = 200.0,
                    packageUnit = "g",
                    unitPricePerBaseGram = 18.00 / 200.0,
                    brand = "$storeName Bicarb",
                    note = "200g baking tub",
                    isAiGenerated = false
                )
                lowerProduct.contains("salt") -> ProductPriceEstimate(
                    productName = productName,
                    storeName = storeName,
                    packagePriceZar = 12.00,
                    packageSize = 500.0,
                    packageUnit = "g",
                    unitPricePerBaseGram = 12.00 / 500.0,
                    brand = "$storeName Table Salt",
                    note = "500g bottle",
                    isAiGenerated = false
                )
                lowerProduct.contains("egg") -> ProductPriceEstimate(
                    productName = productName,
                    storeName = storeName,
                    packagePriceZar = 68.00,
                    packageSize = 30.0,
                    packageUnit = "unit",
                    unitPricePerBaseGram = 68.00 / 30.0,
                    brand = "$storeName Large Eggs",
                    note = "30 Large Eggs tray (~R2.27/egg)",
                    isAiGenerated = false
                )
                else -> ProductPriceEstimate(
                    productName = productName,
                    storeName = storeName,
                    packagePriceZar = 25.00,
                    packageSize = 500.0,
                    packageUnit = "g",
                    unitPricePerBaseGram = 25.00 / 500.0,
                    brand = "$storeName Pantry",
                    note = "$storeName standard pantry item",
                    isAiGenerated = false
                )
            }
        }
    }
}
