package com.example.data.remote

import android.util.Log
import com.example.data.local.CustomerEntity
import com.example.data.local.RecipeEntity
import com.example.data.local.RecipeIngredientEntity
import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WebSyncService {
    private const val TAG = "WebSyncService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    // Candidates for the web backend server
    private val candidateBaseUrls = listOf(
        "http://10.0.2.2:3000",
        "http://127.0.0.1:3000",
        "http://localhost:3000"
    )

    private var activeBaseUrl: String? = null

    private suspend fun getWorkingBaseUrl(): String = withContext(Dispatchers.IO) {
        activeBaseUrl?.let { return@withContext it }
        for (url in candidateBaseUrls) {
            try {
                val req = Request.Builder()
                    .url("$url/api/health")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.i(TAG, "Connected to web backend at $url")
                        activeBaseUrl = url
                        return@withContext url
                    }
                }
            } catch (_: Throwable) {
                // Try next candidate
            }
        }
        // Fallback default
        "http://10.0.2.2:3000"
    }

    suspend fun syncCustomer(bakeryId: String, customer: CustomerEntity): Boolean = withContext(Dispatchers.IO) {
        val targetBakeryId = bakeryId.ifBlank { "bakery_1" }
        val baseUrl = getWorkingBaseUrl()
        val json = JSONObject().apply {
            put("id", customer.id)
            put("bakeryId", targetBakeryId)
            put("userId", customer.userId)
            put("name", customer.name)
            put("phone", customer.phone)
            put("email", customer.email)
            put("address", customer.address)
            put("notes", customer.notes)
            put("totalOrders", customer.totalOrders)
            put("totalSpend", customer.totalSpend)
            put("createdAt", customer.createdAt)
        }

        try {
            val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$baseUrl/api/bakery/$targetBakeryId/customers")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Log.d(TAG, "Customer '${customer.name}' synced to website successfully.")
                    true
                } else {
                    Log.w(TAG, "Failed to sync customer to website: code ${resp.code}")
                    false
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "WebSyncService.syncCustomer: ${e.message}")
            false
        }
    }

    suspend fun syncRecipe(
        bakeryId: String,
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientEntity> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val targetBakeryId = bakeryId.ifBlank { "bakery_1" }
        val baseUrl = getWorkingBaseUrl()
        val ingredientsJson = JSONArray()
        for (ing in ingredients) {
            val ingObj = JSONObject().apply {
                put("id", ing.id)
                put("name", ing.name)
                put("quantity", ing.quantity)
                put("unit", ing.unit)
                put("cost", ing.cost)
            }
            ingredientsJson.put(ingObj)
        }

        val json = JSONObject().apply {
            put("id", recipe.id)
            put("bakeryId", targetBakeryId)
            put("userId", recipe.userId)
            put("name", recipe.name)
            put("category", recipe.category)
            put("description", recipe.description)
            put("servings", recipe.servings)
            put("batchSize", recipe.batchSize)
            put("difficulty", recipe.difficulty)
            put("rating", recipe.rating)
            put("labourCost", recipe.labourCost)
            put("overheadsCost", recipe.overheadsCost)
            put("packagingCost", recipe.packagingCost)
            put("utilitiesCost", recipe.utilitiesCost)
            put("profitMarginPercent", recipe.profitMarginPercent)
            put("customSellingPrice", recipe.customSellingPrice)
            put("instructions", recipe.instructions)
            put("ingredients", ingredientsJson)
        }

        try {
            val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$baseUrl/api/bakery/$targetBakeryId/recipes")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Log.d(TAG, "Recipe '${recipe.name}' synced to website successfully.")
                    true
                } else {
                    Log.w(TAG, "Failed to sync recipe to website: code ${resp.code}")
                    false
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "WebSyncService.syncRecipe: ${e.message}")
            false
        }
    }

    suspend fun syncProfile(bakeryId: String, profile: UserProfileEntity): Boolean = withContext(Dispatchers.IO) {
        val targetBakeryId = bakeryId.ifBlank { profile.bakeryId.ifBlank { "bakery_1" } }
        val baseUrl = getWorkingBaseUrl()
        val json = JSONObject().apply {
            put("bakeryId", targetBakeryId)
            put("bakeryName", profile.bakeryName)
            put("fullName", profile.fullName)
            put("email", profile.email)
            put("phone", profile.phone)
            put("city", profile.city)
            put("operatingModel", profile.operatingModel)
            put("currency", profile.currency)
            put("specialty", profile.specialty)
            put("isPremium", profile.isPremium)
            put("bankName", profile.bankName)
            put("accountNumber", profile.accountNumber)
            put("branchCode", profile.branchCode)
            put("vatNumber", profile.vatNumber)
            put("address", profile.address)
        }

        try {
            val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$baseUrl/api/bakery/$targetBakeryId/profile")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Throwable) {
            Log.w(TAG, "WebSyncService.syncProfile: ${e.message}")
            false
        }
    }

    suspend fun fetchBakeryCustomers(bakeryId: String): List<CustomerEntity> = withContext(Dispatchers.IO) {
        val targetBakeryId = bakeryId.ifBlank { "bakery_1" }
        val baseUrl = getWorkingBaseUrl()
        val result = mutableListOf<CustomerEntity>()
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/bakery/$targetBakeryId/customers")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        result.add(
                            CustomerEntity(
                                id = obj.optLong("id", 0L),
                                userId = obj.optLong("userId", 0L),
                                bakeryId = targetBakeryId,
                                name = obj.optString("name", "Customer"),
                                phone = obj.optString("phone", ""),
                                email = obj.optString("email", ""),
                                address = obj.optString("address", ""),
                                notes = obj.optString("notes", ""),
                                totalOrders = obj.optInt("totalOrders", 0),
                                totalSpend = obj.optDouble("totalSpend", 0.0),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "WebSyncService.fetchBakeryCustomers: ${e.message}")
        }
        result
    }

    suspend fun fetchBakeryRecipes(bakeryId: String): List<RecipeEntity> = withContext(Dispatchers.IO) {
        val targetBakeryId = bakeryId.ifBlank { "bakery_1" }
        val baseUrl = getWorkingBaseUrl()
        val result = mutableListOf<RecipeEntity>()
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/bakery/$targetBakeryId/recipes")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        result.add(
                            RecipeEntity(
                                id = obj.optLong("id", 0L),
                                userId = obj.optLong("userId", 0L),
                                bakeryId = targetBakeryId,
                                name = obj.optString("name", "Recipe"),
                                category = obj.optString("category", "Cakes"),
                                description = obj.optString("description", ""),
                                servings = obj.optInt("servings", 12),
                                batchSize = obj.optInt("batchSize", 1),
                                difficulty = obj.optString("difficulty", "Medium"),
                                rating = obj.optDouble("rating", 5.0),
                                reviewCount = obj.optInt("reviewCount", 1),
                                imageResName = "cupcake",
                                labourCost = obj.optDouble("labourCost", 0.0),
                                overheadsCost = obj.optDouble("overheadsCost", 0.0),
                                packagingCost = obj.optDouble("packagingCost", 0.0),
                                utilitiesCost = obj.optDouble("utilitiesCost", 0.0),
                                profitMarginPercent = obj.optDouble("profitMarginPercent", 50.0),
                                customSellingPrice = obj.optDouble("customSellingPrice", 0.0),
                                instructions = obj.optString("instructions", "")
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "WebSyncService.fetchBakeryRecipes: ${e.message}")
        }
        result
    }
}
