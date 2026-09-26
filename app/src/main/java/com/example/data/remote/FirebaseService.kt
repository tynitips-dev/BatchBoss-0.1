package com.example.data.remote

import android.util.Log
import com.example.BatchBossApplication
import com.example.data.local.CustomerEntity
import com.example.data.local.InventoryItemEntity
import com.example.data.local.RecipeEntity
import com.example.data.local.RecipeIngredientEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FirebaseSyncResult(
    val success: Boolean,
    val message: String,
    val syncedRecipesCount: Int = 0,
    val syncedInventoryCount: Int = 0,
    val syncedCustomersCount: Int = 0
)

data class CloudBackupData(
    val recipes: List<RecipeEntity> = emptyList(),
    val ingredients: List<RecipeIngredientEntity> = emptyList(),
    val inventory: List<InventoryItemEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList()
)

data class FirebaseRestoreResult(
    val success: Boolean,
    val message: String,
    val data: CloudBackupData? = null
)

data class FirebaseWorkspace(
    val uid: String,
    val bakeryId: String,
    val email: String,
    val firstName: String = "",
    val surname: String = "",
    val bakeryName: String = ""
)

/**
 * Extension to safely await Google Play Services / Firebase Tasks with cancellation support.
 */
suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }

object FirebaseService {
    private const val TAG = "FirebaseService"
    const val APPLICATION_ID = "com.aistudio.batchboss.kqwxrv"

    val isConfigured: Boolean
        get() = try {
            FirebaseApp.getApps(BatchBossApplication.instance).isNotEmpty()
        } catch (e: Throwable) {
            false
        }

    val auth: FirebaseAuth?
        get() = try {
            if (isConfigured) FirebaseAuth.getInstance() else null
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseAuth not available: ${e.message}")
            null
        }

    val firestore: FirebaseFirestore?
        get() = try {
            if (isConfigured) FirebaseFirestore.getInstance() else null
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseFirestore not available: ${e.message}")
            null
        }

    val currentUser: FirebaseUser?
        get() = auth?.currentUser

    /**
     * Sign in with email and password.
     */
    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase is not yet initialized with google-services.json."))
        return try {
            val authResult = authInstance.signInWithEmailAndPassword(email, pass).awaitTask()
            val user = authResult.user ?: throw IllegalStateException("User null after sign in.")
            Result.success(user)
        } catch (e: Throwable) {
            Log.e(TAG, "Error signing in with email", e)
            Result.failure(e)
        }
    }

    /**
     * Create account with email and password.
     */
    suspend fun createAccountWithEmail(email: String, pass: String): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase is not yet initialized with google-services.json."))
        return try {
            val authResult = authInstance.createUserWithEmailAndPassword(email, pass).awaitTask()
            val user = authResult.user ?: throw IllegalStateException("User null after registration.")
            Result.success(user)
        } catch (e: Throwable) {
            Log.e(TAG, "Error registering account", e)
            Result.failure(e)
        }
    }

    suspend fun createWorkspace(
        email: String,
        password: String,
        bakeryId: String,
        bakeryName: String,
        firstName: String,
        surname: String
    ): Result<FirebaseWorkspace> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
        return createAccountWithEmail(email, password).fold(
            onSuccess = { user ->
                try {
                    val targetBakeryId = bakeryId.ifBlank { "bakery_${user.uid}" }
                    val userData = hashMapOf<String, Any>(
                        "uid" to user.uid,
                        "bakeryId" to targetBakeryId,
                        "email" to email,
                        "firstName" to firstName,
                        "surname" to surname,
                        "role" to "owner",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    val bakeryData = hashMapOf<String, Any>(
                        "name" to bakeryName,
                        "ownerUid" to user.uid,
                        "currency" to "ZAR",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    val memberData = hashMapOf<String, Any>(
                        "uid" to user.uid,
                        "email" to email,
                        "role" to "owner",
                        "joinedAt" to FieldValue.serverTimestamp()
                    )
                    val batch = db.batch()
                    batch.set(db.collection("bakeries").document(targetBakeryId), bakeryData)
                    batch.set(db.collection("users").document(user.uid), userData)
                    batch.set(db.collection("bakeries").document(targetBakeryId).collection("members").document(user.uid), memberData)
                    batch.commit().awaitTask()
                    Result.success(FirebaseWorkspace(user.uid, targetBakeryId, email, firstName, surname, bakeryName))
                } catch (e: Throwable) {
                    try { user.delete().awaitTask() } catch (_: Throwable) {}
                    Result.failure(e)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun signInToWorkspace(email: String, password: String): Result<FirebaseWorkspace> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
        return signInWithEmail(email, password).fold(
            onSuccess = { user ->
                try {
                    val snapshot = db.collection("users").document(user.uid).get().awaitTask()
                    val bakeryId = snapshot.getString("bakeryId").orEmpty()
                    if (bakeryId.isBlank()) throw IllegalStateException("This account has no BatchBoss bakery workspace yet.")
                    val bakerySnapshot = db.collection("bakeries").document(bakeryId).get().awaitTask()
                    Result.success(
                        FirebaseWorkspace(
                            uid = user.uid,
                            bakeryId = bakeryId,
                            email = snapshot.getString("email") ?: email,
                            firstName = snapshot.getString("firstName").orEmpty(),
                            surname = snapshot.getString("surname").orEmpty(),
                            bakeryName = bakerySnapshot.getString("name").orEmpty()
                        )
                    )
                } catch (e: Throwable) {
                    signOut()
                    Result.failure(e)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Throwable) {
            Log.w(TAG, "Error during signOut: ${e.message}")
        }
    }

    /**
     * Back up local Room database recipes, ingredients, inventory items, and customers to Cloud Firestore.
     */
    suspend fun backupDataToCloud(
        recipes: List<RecipeEntity>,
        ingredients: List<RecipeIngredientEntity>,
        inventory: List<InventoryItemEntity>,
        customers: List<CustomerEntity> = emptyList(),
        bakeryId: String = ""
    ): FirebaseSyncResult {
        if (!isConfigured) {
            return FirebaseSyncResult(
                success = false,
                message = "Firebase is running in Local Mode. To enable Cloud Backup, add google-services.json to the app/ directory."
            )
        }

        val db = firestore ?: return FirebaseSyncResult(
            success = false,
            message = "Firestore service is unavailable."
        )

        if (currentUser == null) return FirebaseSyncResult(false, "Sign in before syncing your bakery data.")
        if (bakeryId.isBlank()) return FirebaseSyncResult(false, "Your account is missing its bakery workspace ID.")
        val targetBakeryId = bakeryId

        return try {
            val batch = db.batch()
            val recipesColl = db.collection("bakeries").document(targetBakeryId).collection("recipes")
            val inventoryColl = db.collection("bakeries").document(targetBakeryId).collection("inventory")
            val customersColl = db.collection("bakeries").document(targetBakeryId).collection("customers")

            // Sync recipes
            for (recipe in recipes) {
                val recipeDoc = recipesColl.document(recipe.id.toString())
                val recipeData = hashMapOf(
                    "id" to recipe.id,
                    "bakeryId" to targetBakeryId,
                    "name" to recipe.name,
                    "category" to recipe.category,
                    "description" to recipe.description,
                    "servings" to recipe.servings,
                    "batchSize" to recipe.batchSize,
                    "difficulty" to recipe.difficulty,
                    "rating" to recipe.rating,
                    "reviewCount" to recipe.reviewCount,
                    "imageResName" to recipe.imageResName,
                    "labourCost" to recipe.labourCost,
                    "overheadsCost" to recipe.overheadsCost,
                    "packagingCost" to recipe.packagingCost,
                    "utilitiesCost" to recipe.utilitiesCost,
                    "profitMarginPercent" to recipe.profitMarginPercent,
                    "isFavorite" to recipe.isFavorite,
                    "customSellingPrice" to recipe.customSellingPrice,
                    "laborHours" to recipe.laborHours,
                    "laborRatePerHour" to recipe.laborRatePerHour,
                    "instructions" to recipe.instructions,
                    "photoUri" to recipe.photoUri,
                    "updatedAt" to System.currentTimeMillis()
                )
                batch.set(recipeDoc, recipeData, SetOptions.merge())

                // Attach related ingredients inside the recipe document sub-collection
                val matchingIngredients = ingredients.filter { it.recipeId == recipe.id }
                for (ing in matchingIngredients) {
                    val ingDoc = recipeDoc.collection("ingredients").document(ing.id.toString())
                    val ingData = hashMapOf(
                        "id" to ing.id,
                        "recipeId" to ing.recipeId,
                        "name" to ing.name,
                        "quantity" to ing.quantity,
                        "unit" to ing.unit,
                        "cost" to ing.cost
                    )
                    batch.set(ingDoc, ingData, SetOptions.merge())
                }
            }

            // Sync inventory items
            for (item in inventory) {
                val invDoc = inventoryColl.document(item.id.toString())
                val invData = hashMapOf(
                    "id" to item.id,
                    "bakeryId" to targetBakeryId,
                    "name" to item.name,
                    "currentStock" to item.currentStock,
                    "minStock" to item.minStock,
                    "unit" to item.unit,
                    "isLowStock" to item.isLowStock,
                    "alertEnabled" to item.alertEnabled,
                    "unitPrice" to item.unitPrice,
                    "packagePrice" to item.packagePrice,
                    "gramsPerUnit" to item.gramsPerUnit,
                    "category" to item.category,
                    "barcode" to item.barcode,
                    "updatedAt" to System.currentTimeMillis()
                )
                batch.set(invDoc, invData, SetOptions.merge())
            }

            // Sync customers
            for (cust in customers) {
                val custDoc = customersColl.document(cust.id.toString())
                val custData = hashMapOf(
                    "id" to cust.id,
                    "bakeryId" to targetBakeryId,
                    "name" to cust.name,
                    "phone" to cust.phone,
                    "email" to cust.email,
                    "address" to cust.address,
                    "notes" to cust.notes,
                    "totalOrders" to cust.totalOrders,
                    "totalSpend" to cust.totalSpend,
                    "createdAt" to cust.createdAt,
                    "updatedAt" to System.currentTimeMillis()
                )
                batch.set(custDoc, custData, SetOptions.merge())
            }

            batch.commit().awaitTask()

            FirebaseSyncResult(
                success = true,
                message = "Cloud Backup complete for bakery '$targetBakeryId'! Uploaded ${recipes.size} recipes, ${customers.size} customers, and ${inventory.size} items to Firestore.",
                syncedRecipesCount = recipes.size,
                syncedInventoryCount = inventory.size,
                syncedCustomersCount = customers.size
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to backup data to Firestore", e)
            FirebaseSyncResult(
                success = false,
                message = "Cloud sync encountered an issue: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    /**
     * Restore recipes, inventory items, and customers from Firestore into memory to import into Room.
     */
    suspend fun restoreDataFromCloud(bakeryId: String = ""): FirebaseRestoreResult {
        if (!isConfigured) {
            return FirebaseRestoreResult(
                success = false,
                message = "Firebase is in Local Mode. Add google-services.json to connect to Cloud Firestore."
            )
        }

        val db = firestore ?: return FirebaseRestoreResult(
            success = false,
            message = "Firestore is currently unavailable."
        )

        if (currentUser == null) return FirebaseRestoreResult(false, "Sign in before restoring your bakery data.")
        if (bakeryId.isBlank()) return FirebaseRestoreResult(false, "Your account is missing its bakery workspace ID.")
        val targetBakeryId = bakeryId

        return try {
            val recipesColl = db.collection("bakeries").document(targetBakeryId).collection("recipes").get().awaitTask()
            val inventoryColl = db.collection("bakeries").document(targetBakeryId).collection("inventory").get().awaitTask()
            val customersColl = db.collection("bakeries").document(targetBakeryId).collection("customers").get().awaitTask()

            val restoredRecipes = mutableListOf<RecipeEntity>()
            val restoredIngredients = mutableListOf<RecipeIngredientEntity>()
            val restoredInventory = mutableListOf<InventoryItemEntity>()
            val restoredCustomers = mutableListOf<CustomerEntity>()

            for (doc in customersColl.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: "Customer"
                val phone = doc.getString("phone") ?: ""
                val email = doc.getString("email") ?: ""
                val address = doc.getString("address") ?: ""
                val notes = doc.getString("notes") ?: ""
                val totalOrders = doc.getLong("totalOrders")?.toInt() ?: 0
                val totalSpend = doc.getDouble("totalSpend") ?: 0.0
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                restoredCustomers.add(
                    CustomerEntity(
                        id = id,
                        bakeryId = targetBakeryId,
                        name = name,
                        phone = phone,
                        email = email,
                        address = address,
                        notes = notes,
                        totalOrders = totalOrders,
                        totalSpend = totalSpend,
                        createdAt = createdAt
                    )
                )
            }

            for (doc in recipesColl.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: "Unnamed Recipe"
                val category = doc.getString("category") ?: "Cakes"
                val description = doc.getString("description") ?: ""
                val servings = doc.getLong("servings")?.toInt() ?: 12
                val batchSize = doc.getLong("batchSize")?.toInt() ?: 1
                val difficulty = doc.getString("difficulty") ?: "Medium"
                val rating = doc.getDouble("rating") ?: 5.0
                val reviewCount = doc.getLong("reviewCount")?.toInt() ?: 1
                val imageResName = doc.getString("imageResName") ?: "recipe_placeholder"
                val labourCost = doc.getDouble("labourCost") ?: 0.0
                val overheadsCost = doc.getDouble("overheadsCost") ?: 0.0
                val packagingCost = doc.getDouble("packagingCost") ?: 0.0
                val utilitiesCost = doc.getDouble("utilitiesCost") ?: 0.0
                val profitMarginPercent = doc.getDouble("profitMarginPercent") ?: 50.0
                val isFavorite = doc.getBoolean("isFavorite") ?: false
                val customSellingPrice = doc.getDouble("customSellingPrice") ?: 0.0
                val laborHours = doc.getDouble("laborHours") ?: 1.5
                val laborRatePerHour = doc.getDouble("laborRatePerHour") ?: 120.0
                val instructions = doc.getString("instructions") ?: ""
                val photoUri = doc.getString("photoUri") ?: ""

                restoredRecipes.add(
                    RecipeEntity(
                        id = id,
                        name = name,
                        category = category,
                        description = description,
                        servings = servings,
                        batchSize = batchSize,
                        difficulty = difficulty,
                        rating = rating,
                        reviewCount = reviewCount,
                        imageResName = imageResName,
                        labourCost = labourCost,
                        overheadsCost = overheadsCost,
                        packagingCost = packagingCost,
                        utilitiesCost = utilitiesCost,
                        profitMarginPercent = profitMarginPercent,
                        isFavorite = isFavorite,
                        customSellingPrice = customSellingPrice,
                        laborHours = laborHours,
                        laborRatePerHour = laborRatePerHour,
                        instructions = instructions,
                        photoUri = photoUri
                    )
                )

                // Fetch ingredients for this recipe
                val ingDocs = doc.reference.collection("ingredients").get().awaitTask()
                for (iDoc in ingDocs.documents) {
                    val ingId = iDoc.getLong("id") ?: 0L
                    val ingRecipeId = iDoc.getLong("recipeId") ?: id
                    val ingName = iDoc.getString("name") ?: ""
                    val quantity = iDoc.getDouble("quantity") ?: 0.0
                    val unit = iDoc.getString("unit") ?: "g"
                    val cost = iDoc.getDouble("cost") ?: 0.0

                    restoredIngredients.add(
                        RecipeIngredientEntity(
                            id = ingId,
                            recipeId = ingRecipeId,
                            name = ingName,
                            quantity = quantity,
                            unit = unit,
                            cost = cost
                        )
                    )
                }
            }

            for (doc in inventoryColl.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: "Item"
                val currentStock = doc.getDouble("currentStock") ?: 0.0
                val minStock = doc.getDouble("minStock") ?: 0.0
                val unit = doc.getString("unit") ?: "kg"
                val isLowStock = doc.getBoolean("isLowStock") ?: false
                val alertEnabled = doc.getBoolean("alertEnabled") ?: true
                val unitPrice = doc.getDouble("unitPrice") ?: 0.0
                val packagePrice = doc.getDouble("packagePrice") ?: 0.0
                val gramsPerUnit = doc.getDouble("gramsPerUnit") ?: 1000.0
                val category = doc.getString("category") ?: "Baking Staples"
                val barcode = doc.getString("barcode") ?: ""

                restoredInventory.add(
                    InventoryItemEntity(
                        id = id,
                        name = name,
                        currentStock = currentStock,
                        minStock = minStock,
                        unit = unit,
                        isLowStock = isLowStock,
                        alertEnabled = alertEnabled,
                        unitPrice = unitPrice,
                        packagePrice = packagePrice,
                        gramsPerUnit = gramsPerUnit,
                        category = category,
                        barcode = barcode
                    )
                )
            }

            FirebaseRestoreResult(
                success = true,
                message = "Restored ${restoredRecipes.size} recipes, ${restoredCustomers.size} customers, and ${restoredInventory.size} inventory items from Firestore.",
                data = CloudBackupData(
                    recipes = restoredRecipes,
                    ingredients = restoredIngredients,
                    inventory = restoredInventory,
                    customers = restoredCustomers
                )
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to restore data from Firestore", e)
            FirebaseRestoreResult(
                success = false,
                message = "Error fetching cloud data: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    /**
     * Adds an online Recipe object directly to the "recipes" Firestore collection.
     */
    suspend fun addOnlineRecipe(recipe: com.example.data.model.Recipe): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
        return try {
            val docRef = db.collection("recipes").add(recipe).awaitTask()
            Result.success(docRef.id)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to add online recipe", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches all online Recipe POJO objects from the "recipes" Firestore collection.
     */
    suspend fun getOnlineRecipes(): Result<List<com.example.data.model.Recipe>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
        return try {
            val snapshot = db.collection("recipes").get().awaitTask()
            val list = snapshot.documents.mapNotNull { it.toObject(com.example.data.model.Recipe::class.java) }
            Result.success(list)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to fetch online recipes", e)
            Result.failure(e)
        }
    }
}
