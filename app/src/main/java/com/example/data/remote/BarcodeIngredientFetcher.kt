package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ScannedProductInfo(
    val barcode: String,
    val name: String,
    val brand: String,
    val quantity: Double,
    val unit: String,
    val category: String,
    val imageUrl: String? = null,
    val estimatedPriceZar: Double = 0.0
)

object BarcodeIngredientFetcher {
    private const val TAG = "BarcodeFetcher"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Bakery Barcode Presets (South African & Global bakery staples)
    val SAMPLE_BAKERY_BARCODES = listOf(
        ScannedProductInfo(
            barcode = "6001007010023",
            name = "Golden Cloud Cake Wheat Flour",
            brand = "Golden Cloud",
            quantity = 2500.0,
            unit = "g",
            category = "Flour & Grains",
            estimatedPriceZar = 46.99
        ),
        ScannedProductInfo(
            barcode = "5099864000122",
            name = "Kerrygold Pure Irish Salted Butter",
            brand = "Kerrygold",
            quantity = 500.0,
            unit = "g",
            category = "Dairy & Eggs",
            estimatedPriceZar = 79.99
        ),
        ScannedProductInfo(
            barcode = "6001062000014",
            name = "Huletts Pure White Sugar",
            brand = "Huletts",
            quantity = 1000.0,
            unit = "g",
            category = "Sugars & Sweeteners",
            estimatedPriceZar = 29.50
        ),
        ScannedProductInfo(
            barcode = "6009653890014",
            name = "Royal Baking Powder",
            brand = "Royal",
            quantity = 200.0,
            unit = "g",
            category = "Leaveners & Rising",
            estimatedPriceZar = 24.99
        ),
        ScannedProductInfo(
            barcode = "7622210408571",
            name = "Cadbury Bournville Cocoa Powder",
            brand = "Cadbury",
            quantity = 250.0,
            unit = "g",
            category = "Chocolate & Cocoa",
            estimatedPriceZar = 52.00
        ),
        ScannedProductInfo(
            barcode = "6009802870012",
            name = "Pure Bourbon Vanilla Extract",
            brand = "Nielsen-Massey",
            quantity = 100.0,
            unit = "ml",
            category = "Flavorings & Extracts",
            estimatedPriceZar = 115.00
        )
    )

    suspend fun fetchProduct(barcode: String): Result<ScannedProductInfo> = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim().replace(Regex("[^0-9]"), "")
        if (cleanBarcode.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Barcode cannot be empty"))
        }

        // 1. Check local bakery presets first for instant response
        SAMPLE_BAKERY_BARCODES.find { it.barcode == cleanBarcode }?.let {
            return@withContext Result.success(it)
        }

        // 2. Fetch from Open Food Facts API
        try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$cleanBarcode.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BatchBossBakeryApp/1.0 (Android; tyne@batchboss.com)")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()

            if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                val json = JSONObject(bodyString)
                val status = json.optInt("status", 0)
                if (status == 1 && json.has("product")) {
                    val product = json.getJSONObject("product")
                    val rawName = product.optString("product_name")
                        .ifBlank { product.optString("product_name_en") }
                        .ifBlank { "Bakery Ingredient ($cleanBarcode)" }
                    val brands = product.optString("brands", "").trim()
                    val fullName = if (brands.isNotBlank() && !rawName.startsWith(brands, ignoreCase = true)) {
                        "$brands $rawName"
                    } else rawName

                    val rawQuantity = product.optString("quantity", "")
                    val (parsedQty, parsedUnit) = parseQuantityAndUnit(rawQuantity)

                    val categories = product.optString("categories", "")
                    val detectedCategory = mapCategory(categories, fullName)
                    val imageUrl = product.optString("image_front_url")
                        .ifBlank { product.optString("image_url") }
                        .takeIf { it.isNotBlank() }

                    val estimatedPrice = estimatePrice(fullName, detectedCategory)

                    return@withContext Result.success(
                        ScannedProductInfo(
                            barcode = cleanBarcode,
                            name = fullName,
                            brand = brands,
                            quantity = parsedQty,
                            unit = parsedUnit,
                            category = detectedCategory,
                            imageUrl = imageUrl,
                            estimatedPriceZar = estimatedPrice
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenFoodFacts network lookup failed: ${e.message}")
        }

        // 3. Fallback: Generic ingredient candidate with scanned barcode
        Result.success(
            ScannedProductInfo(
                barcode = cleanBarcode,
                name = "Ingredient ($cleanBarcode)",
                brand = "",
                quantity = 1000.0,
                unit = "g",
                category = "Baking Staples",
                estimatedPriceZar = 35.00
            )
        )
    }

    private fun parseQuantityAndUnit(raw: String): Pair<Double, String> {
        if (raw.isBlank()) return 1000.0 to "g"
        val lower = raw.lowercase()
        return when {
            lower.contains("kg") -> {
                val num = Regex("([0-9]+(?:\\.[0-9]+)?)").find(lower)?.value?.toDoubleOrNull() ?: 1.0
                (num * 1000.0) to "g"
            }
            lower.contains("g") -> {
                val num = Regex("([0-9]+(?:\\.[0-9]+)?)").find(lower)?.value?.toDoubleOrNull() ?: 500.0
                num to "g"
            }
            lower.contains("ml") -> {
                val num = Regex("([0-9]+(?:\\.[0-9]+)?)").find(lower)?.value?.toDoubleOrNull() ?: 250.0
                num to "ml"
            }
            lower.contains("l") -> {
                val num = Regex("([0-9]+(?:\\.[0-9]+)?)").find(lower)?.value?.toDoubleOrNull() ?: 1.0
                (num * 1000.0) to "ml"
            }
            else -> 1000.0 to "g"
        }
    }

    private fun mapCategory(categories: String, name: String): String {
        val combined = "$categories $name".lowercase()
        return when {
            combined.contains("flour") || combined.contains("wheat") || combined.contains("grain") || combined.contains("cereal") -> "Flour & Grains"
            combined.contains("butter") || combined.contains("milk") || combined.contains("cream") || combined.contains("cheese") || combined.contains("egg") -> "Dairy & Eggs"
            combined.contains("sugar") || combined.contains("syrup") || combined.contains("honey") || combined.contains("sweetener") -> "Sugars & Sweeteners"
            combined.contains("chocolate") || combined.contains("cocoa") || combined.contains("cacao") -> "Chocolate & Cocoa"
            combined.contains("yeast") || combined.contains("baking powder") || combined.contains("bicarbonate") || combined.contains("raising") -> "Leaveners & Rising"
            combined.contains("vanilla") || combined.contains("extract") || combined.contains("cinnamon") || combined.contains("spice") -> "Flavorings & Extracts"
            combined.contains("nut") || combined.contains("almond") || combined.contains("walnut") || combined.contains("pecan") -> "Nuts & Seeds"
            else -> "Baking Staples"
        }
    }

    private fun estimatePrice(name: String, category: String): Double {
        val lower = name.lowercase()
        return when {
            lower.contains("butter") -> 75.00
            lower.contains("flour") -> 45.00
            lower.contains("sugar") -> 32.00
            lower.contains("cocoa") -> 55.00
            lower.contains("vanilla") -> 85.00
            lower.contains("egg") -> 50.00
            category == "Dairy & Eggs" -> 45.00
            category == "Flour & Grains" -> 40.00
            category == "Sugars & Sweeteners" -> 30.00
            else -> 35.00
        }
    }
}
