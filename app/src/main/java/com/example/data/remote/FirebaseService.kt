package com.example.data.remote

import android.util.Log
import com.example.BatchBossApplication
import com.example.data.local.CustomerEntity
import com.example.data.local.InventoryItemEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.OrderEntity
import com.example.data.local.PackagingItemEntity
import com.example.data.local.ProductServiceEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.RecipeEntity
import com.example.data.local.RecipeIngredientEntity
import com.example.data.local.TaskEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FirebaseSyncResult(
    val success: Boolean,
    val message: String,
    val syncedRecipesCount: Int = 0,
    val syncedInventoryCount: Int = 0,
    val syncedCustomersCount: Int = 0,
    val syncedPackagingCount: Int = 0
)

data class CloudBackupData(
    val recipes: List<RecipeEntity> = emptyList(),
    val ingredients: List<RecipeIngredientEntity> = emptyList(),
    val inventory: List<InventoryItemEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val packaging: List<PackagingItemEntity> = emptyList()
)

data class FirebaseRestoreResult(
    val success: Boolean,
    val message: String,
    val data: CloudBackupData? = null
)

data class FirebaseLoginResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val firebaseUser: FirebaseUser? = null,
    val bakeryId: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val bakeryName: String? = null,
    val phone: String? = null,
    val city: String? = null,
    val operatingModel: String? = null,
    val currency: String? = null,
    val specialty: String? = null,
    val subscriptionStatus: String? = null,
    val subscriptionPlan: String? = null,
    val isPro: Boolean = false
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

    var currentBakeryId: String = ""
    var activeRepository: com.example.data.repository.AppRepository? = null

    fun getEffectiveUserId(): String {
        return currentUser?.uid ?: "local_bakery_owner"
    }

    // 4. Exact bakeryId mapping for all 9 Firebase paths:
    // bakeries/{bakeryId}/packaging
    // bakeries/{bakeryId}/inventory
    // bakeries/{bakeryId}/recipes
    // bakeries/{bakeryId}/customers
    // bakeries/{bakeryId}/products
    // bakeries/{bakeryId}/invoices
    // bakeries/{bakeryId}/quotes
    // bakeries/{bakeryId}/orders
    // bakeries/{bakeryId}/tasks
    fun getPackagingPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/packaging"

    fun getInventoryPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/inventory"

    fun getRecipesPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/recipes"

    fun getCustomersPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/customers"

    fun getProductsPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/products"

    fun getInvoicesPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/invoices"

    fun getQuotesPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/quotes"

    fun getOrdersPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/orders"

    fun getTasksPath(bakeryId: String = ""): String =
        "bakeries/${bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }}/tasks"

    fun packagingCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("packaging")

    fun inventoryCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("inventory")

    fun recipesCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("recipes")

    fun customersCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("customers")

    fun productsCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("products")

    fun invoicesCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("invoices")

    fun quotesCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("quotes")

    fun ordersCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("orders")

    fun tasksCollection(bakeryId: String = "") =
        firestore?.collection("bakeries")?.document(bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } })?.collection("tasks")

    /**
     * Firebase Authentication Owner/Baker login:
     * 1. signInWithEmailAndPassword
     * 2. After success, read users/{firebaseUser.uid}
     * 3. Read bakeryId from user document
     * 4. Store and use that exact bakeryId for all operations
     * 5. Read subscriptionStatus & subscriptionPlan to determine Pro access
     * Returns FirebaseLoginResult with real error if credentials or workspace fail.
     */
    suspend fun loginWithFirebase(email: String, pass: String): FirebaseLoginResult {
        if (!isConfigured) {
            return FirebaseLoginResult(
                success = false,
                errorMessage = "Firebase is running in local mode. Please supply google-services.json to authenticate against Cloud Firestore."
            )
        }
        val authInstance = auth ?: return FirebaseLoginResult(
            success = false,
            errorMessage = "Firebase Authentication is currently unavailable."
        )
        val firestoreInstance = firestore ?: return FirebaseLoginResult(
            success = false,
            errorMessage = "Firestore service is unavailable. Please verify network connectivity."
        )

        val cleanEmail = email.trim()
        val cleanPass = pass

        if (cleanEmail.isBlank()) {
            return FirebaseLoginResult(
                success = false,
                errorMessage = "Please enter your email address."
            )
        }
        if (cleanPass.isBlank()) {
            return FirebaseLoginResult(
                success = false,
                errorMessage = "Please enter your password."
            )
        }

        return try {
            val authResult = authInstance.signInWithEmailAndPassword(cleanEmail, cleanPass).awaitTask()
            val user = authResult.user ?: throw IllegalStateException("Firebase user was null after sign in.")

            // Read users/{firebaseUser.uid}
            val userDocRef = firestoreInstance.collection("users").document(user.uid)
            val userDoc = userDocRef.get().awaitTask()

            if (!userDoc.exists()) {
                return FirebaseLoginResult(
                    success = false,
                    errorMessage = "Account document not found in database (users/${user.uid}). Please register or contact your bakery administrator."
                )
            }

            // Read bakeryId from that user document
            val bakeryId = userDoc.getString("bakeryId")?.trim() ?: ""
            if (bakeryId.isBlank()) {
                return FirebaseLoginResult(
                    success = false,
                    errorMessage = "Workspace error: No bakeryId configured in users/${user.uid}."
                )
            }

            // Set exact bakeryId
            currentBakeryId = bakeryId

            // Requirement 3: Start real-time Firestore snapshot listener after loginWithFirebase loads exact bakeryId
            if (activeRepository != null) {
                try {
                    startPackagingListener(bakeryId, activeRepository)
                } catch (e: Throwable) {
                    Log.w(TAG, "Error starting packaging listener in loginWithFirebase: ${e.message}")
                }
            }

            val docEmail = userDoc.getString("email") ?: user.email ?: cleanEmail
            val fullName = userDoc.getString("fullName")
                ?: userDoc.getString("name")
                ?: userDoc.getString("firstName")
                ?: user.displayName
                ?: docEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            val bakeryName = userDoc.getString("bakeryName")
                ?: userDoc.getString("businessName")
                ?: "$fullName's Bakery"
            val phone = userDoc.getString("phone") ?: ""
            val city = userDoc.getString("city") ?: "Cape Town"
            val operatingModel = userDoc.getString("operatingModel") ?: "Artisan Kitchen"
            val currency = userDoc.getString("currency") ?: "ZAR (R)"
            val specialty = userDoc.getString("specialty") ?: "Cakes & Confections"

            // 11. Determine Pro access from subscriptionStatus and subscriptionPlan in users/{uid}
            val subscriptionStatus = userDoc.getString("subscriptionStatus") ?: ""
            val subscriptionPlan = userDoc.getString("subscriptionPlan") ?: ""
            val isPremiumDoc = userDoc.getBoolean("isPremium") == true || userDoc.getBoolean("isPro") == true

            val isPro = isPremiumDoc ||
                subscriptionStatus.equals("active", ignoreCase = true) ||
                subscriptionStatus.equals("pro", ignoreCase = true) ||
                subscriptionStatus.equals("trialing", ignoreCase = true) ||
                subscriptionStatus.equals("premium", ignoreCase = true) ||
                subscriptionPlan.contains("pro", ignoreCase = true) ||
                subscriptionPlan.contains("premium", ignoreCase = true) ||
                subscriptionPlan.contains("commercial", ignoreCase = true) ||
                subscriptionPlan.contains("business", ignoreCase = true)

            FirebaseLoginResult(
                success = true,
                firebaseUser = user,
                bakeryId = bakeryId,
                email = docEmail,
                fullName = fullName,
                bakeryName = bakeryName,
                phone = phone,
                city = city,
                operatingModel = operatingModel,
                currency = currency,
                specialty = specialty,
                subscriptionStatus = subscriptionStatus,
                subscriptionPlan = subscriptionPlan,
                isPro = isPro
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Firebase login failed", e)
            val errorMessage = when (e) {
                is com.google.firebase.auth.FirebaseAuthInvalidUserException ->
                    "No account found with this email ($cleanEmail). Please verify your email or sign up."
                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                    "The password or email is incorrect. Please verify and try again."
                is com.google.firebase.FirebaseNetworkException ->
                    "Network connection error. Please check your internet connection and try again."
                else -> e.localizedMessage ?: e.message ?: "Authentication failed."
            }
            FirebaseLoginResult(
                success = false,
                errorMessage = errorMessage
            )
        }
    }

    /**
     * Sign in anonymously so cloud operations can have an authenticated session without user friction.
     */
    suspend fun signInAnonymously(): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase is not yet initialized with google-services.json."))
        return try {
            val authResult = authInstance.signInAnonymously().awaitTask()
            val user = authResult.user ?: throw IllegalStateException("Firebase user was null after anonymous sign in.")
            Result.success(user)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in anonymous sign-in", e)
            Result.failure(e)
        }
    }

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

    fun signOut() {
        stopPackagingListener()
        currentBakeryId = ""
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
        packaging: List<PackagingItemEntity> = emptyList(),
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

        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }

        return try {
            val batch = db.batch()
            val recipesColl = db.collection("bakeries").document(targetBakeryId).collection("recipes")
            val inventoryColl = db.collection("bakeries").document(targetBakeryId).collection("inventory")
            val customersColl = db.collection("bakeries").document(targetBakeryId).collection("customers")
            val packagingColl = db.collection("bakeries").document(targetBakeryId).collection("packaging")

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

            // Sync packaging items
            for (pkg in packaging) {
                val pkgDoc = packagingColl.document(pkg.id.toString())
                val pkgData = hashMapOf(
                    "id" to pkg.id,
                    "bakeryId" to targetBakeryId,
                    "name" to pkg.name,
                    "category" to pkg.category,
                    "unit" to pkg.unit,
                    "packagePrice" to pkg.packagePrice,
                    "packageQuantity" to pkg.packageQuantity,
                    "gramsPerUnit" to pkg.gramsPerUnit,
                    "unitPrice" to pkg.unitPrice,
                    "currentStock" to pkg.currentStock,
                    "minStock" to pkg.minStock,
                    "isLowStock" to pkg.isLowStock,
                    "alertEnabled" to pkg.alertEnabled,
                    "barcode" to pkg.barcode,
                    "supplier" to pkg.supplier,
                    "notes" to pkg.notes,
                    "createdAt" to pkg.createdAt,
                    "updatedAt" to System.currentTimeMillis()
                )
                batch.set(pkgDoc, pkgData, SetOptions.merge())
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
                message = "Cloud Backup complete for bakery '$targetBakeryId'! Uploaded ${recipes.size} recipes, ${customers.size} customers, ${inventory.size} items, and ${packaging.size} packaging records to Firestore.",
                syncedRecipesCount = recipes.size,
                syncedInventoryCount = inventory.size,
                syncedCustomersCount = customers.size,
                syncedPackagingCount = packaging.size
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

        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }

        return try {
            val recipesColl = db.collection("bakeries").document(targetBakeryId).collection("recipes").get().awaitTask()
            val inventoryColl = db.collection("bakeries").document(targetBakeryId).collection("inventory").get().awaitTask()
            val customersColl = db.collection("bakeries").document(targetBakeryId).collection("customers").get().awaitTask()

            val packagingColl = db.collection("bakeries").document(targetBakeryId).collection("packaging").get().awaitTask()

            val restoredRecipes = mutableListOf<RecipeEntity>()
            val restoredIngredients = mutableListOf<RecipeIngredientEntity>()
            val restoredInventory = mutableListOf<InventoryItemEntity>()
            val restoredCustomers = mutableListOf<CustomerEntity>()
            val restoredPackaging = mutableListOf<PackagingItemEntity>()

            for (doc in packagingColl.documents) {
                val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
                val name = doc.getString("name") ?: ""
                val category = doc.getString("category") ?: "Cake boxes"
                val unit = doc.getString("unit") ?: "pcs"
                val packagePrice = doc.getDouble("packagePrice") ?: 0.0
                val packageQuantity = doc.getDouble("packageQuantity") ?: 1.0
                val gramsPerUnit = doc.getDouble("gramsPerUnit") ?: 0.0
                val unitPrice = doc.getDouble("unitPrice") ?: if (packageQuantity > 0) packagePrice / packageQuantity else 0.0
                val currentStock = doc.getDouble("currentStock") ?: 0.0
                val minStock = doc.getDouble("minStock") ?: 0.0
                val isLowStock = doc.getBoolean("isLowStock") ?: (currentStock <= minStock)
                val alertEnabled = doc.getBoolean("alertEnabled") ?: true
                val barcode = doc.getString("barcode") ?: ""
                val supplier = doc.getString("supplier") ?: ""
                val notes = doc.getString("notes") ?: ""
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                restoredPackaging.add(
                    PackagingItemEntity(
                        id = id,
                        userId = 0L,
                        bakeryId = targetBakeryId,
                        name = name,
                        category = category,
                        unit = unit,
                        packagePrice = packagePrice,
                        packageQuantity = packageQuantity,
                        gramsPerUnit = gramsPerUnit,
                        unitPrice = unitPrice,
                        currentStock = currentStock,
                        minStock = minStock,
                        isLowStock = isLowStock,
                        alertEnabled = alertEnabled,
                        barcode = barcode,
                        supplier = supplier,
                        notes = notes,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                )
            }

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
                message = "Restored ${restoredRecipes.size} recipes, ${restoredCustomers.size} customers, ${restoredInventory.size} inventory items, and ${restoredPackaging.size} packaging items from Firestore.",
                data = CloudBackupData(
                    recipes = restoredRecipes,
                    ingredients = restoredIngredients,
                    inventory = restoredInventory,
                    customers = restoredCustomers,
                    packaging = restoredPackaging
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
     * Extracts numeric value safely from Firestore DocumentSnapshot handling Number or numeric String.
     */
    fun extractNumber(doc: com.google.firebase.firestore.DocumentSnapshot, field: String, default: Double = 0.0): Double {
        val raw = doc.get(field)
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: default
            else -> default
        }
    }

    /**
     * Extracts updatedAt timestamp from Firestore DocumentSnapshot handling Timestamp, Number or String.
     */
    fun extractUpdatedAt(doc: com.google.firebase.firestore.DocumentSnapshot): Long {
        val raw = doc.get("updatedAt")
        return when (raw) {
            is com.google.firebase.Timestamp -> raw.toDate().time
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: try {
                java.time.Instant.parse(raw).toEpochMilli()
            } catch (_: Throwable) {
                System.currentTimeMillis()
            }
            else -> System.currentTimeMillis()
        }
    }

    /**
     * Converts a Firestore packaging DocumentSnapshot into PackagingItemEntity.
     * Preserves doc.id as the permanent firestoreId.
     * Maps all 10 core fields: name, category, unit, packagePrice, packageQuantity,
     * unitPrice, currentStock, minStock, barcode, and updatedAt.
     */
    fun documentToPackagingItem(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): PackagingItemEntity? {
        val name = doc.getString("name") ?: (doc.get("name")?.toString()) ?: return null
        if (name.isBlank()) return null
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val category = doc.getString("category") ?: "Cake boxes"
        val unit = doc.getString("unit") ?: "pcs"
        val packagePrice = extractNumber(doc, "packagePrice", 0.0)
        val packageQuantity = extractNumber(doc, "packageQuantity", 1.0).let { if (it > 0) it else 1.0 }
        val gramsPerUnit = extractNumber(doc, "gramsPerUnit", 0.0)
        val explicitUnitPrice = if (doc.contains("unitPrice") && doc.get("unitPrice") != null) {
            extractNumber(doc, "unitPrice", 0.0)
        } else null
        val unitPrice = explicitUnitPrice ?: if (packageQuantity > 0) packagePrice / packageQuantity else 0.0
        val currentStock = extractNumber(doc, "currentStock", 0.0)
        val minStock = extractNumber(doc, "minStock", 0.0)
        val isLowStock = doc.getBoolean("isLowStock") ?: (currentStock <= minStock)
        val alertEnabled = doc.getBoolean("alertEnabled") ?: true
        val barcode = doc.getString("barcode") ?: (doc.get("barcode")?.toString() ?: "")
        val supplier = doc.getString("supplier") ?: ""
        val notes = doc.getString("notes") ?: ""
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return PackagingItemEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
            name = name,
            category = category,
            unit = unit,
            packagePrice = packagePrice,
            packageQuantity = packageQuantity,
            gramsPerUnit = gramsPerUnit,
            unitPrice = unitPrice,
            currentStock = currentStock,
            minStock = minStock,
            isLowStock = isLowStock,
            alertEnabled = alertEnabled,
            barcode = barcode,
            supplier = supplier,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // 1. Inventory Item Converter
    fun documentToInventoryItem(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): InventoryItemEntity? {
        val name = doc.getString("name") ?: (doc.get("name")?.toString()) ?: return null
        if (name.isBlank()) return null
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val category = doc.getString("category") ?: "Baking Staples"
        val unit = doc.getString("unit") ?: "g"
        val packagePrice = extractNumber(doc, "packagePrice", 0.0)
        val packageQuantity = extractNumber(doc, "packageQuantity", 1.0).let { if (it > 0) it else 1.0 }
        val gramsPerUnit = extractNumber(doc, "gramsPerUnit", if (unit.equals("kg", true)) packageQuantity * 1000.0 else packageQuantity)
        val explicitUnitPrice = if (doc.contains("unitPrice") && doc.get("unitPrice") != null) {
            extractNumber(doc, "unitPrice", 0.0)
        } else null
        val unitPrice = explicitUnitPrice ?: if (gramsPerUnit > 0) packagePrice / gramsPerUnit else (if (packageQuantity > 0) packagePrice / packageQuantity else 0.0)
        val currentStock = extractNumber(doc, "currentStock", 0.0)
        val minStock = extractNumber(doc, "minStock", 0.0)
        val isLowStock = doc.getBoolean("isLowStock") ?: (currentStock <= minStock)
        val alertEnabled = doc.getBoolean("alertEnabled") ?: true
        val barcode = doc.getString("barcode") ?: (doc.get("barcode")?.toString() ?: "")
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return InventoryItemEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
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
            barcode = barcode,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // 2. Recipe Converter
    fun documentToRecipe(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): RecipeEntity? {
        val name = doc.getString("name") ?: (doc.get("name")?.toString()) ?: return null
        if (name.isBlank()) return null
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val category = doc.getString("category") ?: "Cakes"
        val description = doc.getString("description") ?: ""
        val servings = (doc.getLong("servings") ?: 1L).toInt()
        val batchSize = (doc.getLong("batchSize") ?: 1L).toInt()
        val difficulty = doc.getString("difficulty") ?: "Medium"
        val rating = extractNumber(doc, "rating", 5.0)
        val reviewCount = (doc.getLong("reviewCount") ?: 0L).toInt()
        val imageResName = doc.getString("imageResName") ?: "ic_cake"
        val labourCost = extractNumber(doc, "labourCost", 0.0)
        val overheadsCost = extractNumber(doc, "overheadsCost", 0.0)
        val packagingCost = extractNumber(doc, "packagingCost", 0.0)
        val utilitiesCost = extractNumber(doc, "utilitiesCost", 0.0)
        val profitMarginPercent = extractNumber(doc, "profitMarginPercent", 40.0)
        val isFavorite = doc.getBoolean("isFavorite") ?: false
        val customSellingPrice = extractNumber(doc, "customSellingPrice", 0.0)
        val laborHours = extractNumber(doc, "laborHours", 1.5)
        val laborRatePerHour = extractNumber(doc, "laborRatePerHour", 120.0)
        val instructions = doc.getString("instructions") ?: ""
        val photoUri = doc.getString("photoUri") ?: ""
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return RecipeEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
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
            photoUri = photoUri,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // Recipe Ingredient Subcollection Converter
    fun documentToRecipeIngredient(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        targetBakeryId: String,
        recipeFirestoreId: String
    ): RecipeIngredientEntity? {
        val name = doc.getString("name") ?: (doc.get("name")?.toString()) ?: return null
        if (name.isBlank()) return null
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val recipeId = doc.getLong("recipeId") ?: 0L
        val quantity = extractNumber(doc, "quantity", 1.0)
        val unit = doc.getString("unit") ?: "g"
        val cost = extractNumber(doc, "cost", 0.0)
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return RecipeIngredientEntity(
            id = id,
            firestoreId = doc.id,
            recipeFirestoreId = recipeFirestoreId,
            recipeId = recipeId,
            userId = 0L,
            bakeryId = targetBakeryId,
            name = name,
            quantity = quantity,
            unit = unit,
            cost = cost,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // 3. Customer Converter
    fun documentToCustomer(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): CustomerEntity? {
        val name = doc.getString("name") ?: (doc.get("name")?.toString()) ?: return null
        if (name.isBlank()) return null
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val phone = doc.getString("phone") ?: ""
        val email = doc.getString("email") ?: ""
        val address = doc.getString("address") ?: ""
        val notes = doc.getString("notes") ?: ""
        val totalOrders = (doc.getLong("totalOrders") ?: 0L).toInt()
        val totalSpend = extractNumber(doc, "totalSpend", 0.0)
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return CustomerEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
            name = name,
            phone = phone,
            email = email,
            address = address,
            notes = notes,
            totalOrders = totalOrders,
            totalSpend = totalSpend,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // 4. Product Converter
    fun documentToProduct(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): ProductServiceEntity? {
        val name = doc.getString("name") ?: (doc.get("name")?.toString()) ?: return null
        if (name.isBlank()) return null
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val description = doc.getString("description") ?: ""
        val category = doc.getString("category") ?: "Cakes"
        val imageUrl = doc.getString("imageUrl") ?: ""
        val sku = doc.getString("sku") ?: ""
        val costPrice = extractNumber(doc, "costPrice", 0.0)
        val sellingPrice = extractNumber(doc, "sellingPrice", 0.0)
        val unit = doc.getString("unit") ?: "Each"
        val isActive = doc.getBoolean("isActive") ?: true
        val isService = doc.getBoolean("isService") ?: false
        val linkedRecipeId = doc.getLong("linkedRecipeId")
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return ProductServiceEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
            name = name,
            description = description,
            category = category,
            imageUrl = imageUrl,
            sku = sku,
            costPrice = costPrice,
            sellingPrice = sellingPrice,
            unit = unit,
            isActive = isActive,
            isService = isService,
            linkedRecipeId = linkedRecipeId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // 5. Invoice Converter
    fun documentToInvoice(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): InvoiceEntity? {
        val invoiceNumber = doc.getString("invoiceNumber") ?: ("INV-" + doc.id.takeLast(4))
        val clientName = doc.getString("clientName") ?: (doc.get("clientName")?.toString()) ?: "Customer"
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val clientPhone = doc.getString("clientPhone") ?: ""
        val orderDescription = doc.getString("orderDescription") ?: ""
        val issueDate = doc.getString("issueDate") ?: "Today"
        val dueDate = doc.getString("dueDate") ?: ""
        val amount = extractNumber(doc, "totalDue", extractNumber(doc, "amount", 0.0))
        val status = doc.getString("status") ?: "Pending"
        val subtotal = extractNumber(doc, "subtotal", 0.0)
        val discountAmount = extractNumber(doc, "discount", extractNumber(doc, "discountAmount", 0.0))
        val taxRatePercent = extractNumber(doc, "taxRate", extractNumber(doc, "taxRatePercent", 15.0))
        val taxAmount = extractNumber(doc, "taxAmount", 0.0)
        val totalCost = extractNumber(doc, "totalCost", 0.0)
        val packagingTotal = extractNumber(doc, "packagingTotal", 0.0)

        val lineItemsRaw = doc.get("lineItems")
        val lineItemsJson = when (lineItemsRaw) {
            is String -> lineItemsRaw
            is List<*> -> {
                val array = org.json.JSONArray()
                for (item in lineItemsRaw) {
                    if (item is Map<*, *>) array.put(org.json.JSONObject(item))
                }
                array.toString()
            }
            else -> doc.getString("lineItemsJson") ?: ""
        }

        val packagingItemsRaw = doc.get("packagingItems")
        val packagingItemsJson = when (packagingItemsRaw) {
            is String -> packagingItemsRaw
            is List<*> -> {
                val array = org.json.JSONArray()
                for (item in packagingItemsRaw) {
                    if (item is Map<*, *>) array.put(org.json.JSONObject(item))
                }
                array.toString()
            }
            else -> doc.getString("packagingItemsJson") ?: ""
        }

        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return InvoiceEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
            invoiceNumber = invoiceNumber,
            clientName = clientName,
            clientPhone = clientPhone,
            orderDescription = orderDescription,
            issueDate = issueDate,
            dueDate = dueDate,
            amount = amount,
            status = status,
            subtotal = subtotal,
            discountAmount = discountAmount,
            taxRatePercent = taxRatePercent,
            taxAmount = taxAmount,
            lineItemsJson = lineItemsJson,
            totalCost = totalCost,
            packagingTotal = packagingTotal,
            packagingItemsJson = packagingItemsJson,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // 6. Quote Converter
    fun documentToQuote(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): QuoteEntity? {
        val quoteNumber = doc.getString("quoteNumber") ?: ("QUO-" + doc.id.takeLast(4))
        val clientName = doc.getString("clientName") ?: "Customer"
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val clientPhone = doc.getString("clientPhone") ?: ""
        val eventType = doc.getString("eventType") ?: "Celebration"
        val eventDate = doc.getString("eventDate") ?: ""
        val recipeOrItemName = doc.getString("recipeOrItemName") ?: ""
        val estimatedCost = extractNumber(doc, "estimatedCost", 0.0)
        val profitMarginPercent = extractNumber(doc, "profitMarginPercent", 40.0)
        val quotedPrice = extractNumber(doc, "quotedPrice", 0.0)
        val status = doc.getString("status") ?: "Sent"
        val docType = doc.getString("docType") ?: "Quote"
        val subtotal = extractNumber(doc, "subtotal", 0.0)
        val discountAmount = extractNumber(doc, "discountAmount", 0.0)
        val taxRatePercent = extractNumber(doc, "taxRatePercent", 15.0)
        val taxAmount = extractNumber(doc, "taxAmount", 0.0)
        val totalCost = extractNumber(doc, "totalCost", 0.0)
        val lineItemsRaw = doc.get("lineItems")
        val lineItemsJson = when (lineItemsRaw) {
            is String -> lineItemsRaw
            is List<*> -> {
                val array = org.json.JSONArray()
                for (item in lineItemsRaw) {
                    if (item is Map<*, *>) array.put(org.json.JSONObject(item))
                }
                array.toString()
            }
            else -> doc.getString("lineItemsJson") ?: ""
        }
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return QuoteEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
            quoteNumber = quoteNumber,
            clientName = clientName,
            clientPhone = clientPhone,
            eventType = eventType,
            eventDate = eventDate,
            recipeOrItemName = recipeOrItemName,
            estimatedCost = estimatedCost,
            profitMarginPercent = profitMarginPercent,
            quotedPrice = quotedPrice,
            status = status,
            docType = docType,
            subtotal = subtotal,
            discountAmount = discountAmount,
            taxRatePercent = taxRatePercent,
            taxAmount = taxAmount,
            lineItemsJson = lineItemsJson,
            totalCost = totalCost,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // 7. Order Converter
    fun documentToOrder(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): OrderEntity? {
        val orderNumber = doc.getString("orderNumber") ?: ("ORD-" + doc.id.takeLast(4))
        val customerName = doc.getString("customerName") ?: "Customer"
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val customerPhone = doc.getString("customerPhone") ?: ""
        val description = doc.getString("description") ?: ""
        val dueDate = doc.getString("dueDate") ?: ""
        val totalAmount = extractNumber(doc, "totalAmount", 0.0)
        val totalCost = extractNumber(doc, "totalCost", 0.0)
        val status = doc.getString("status") ?: "Pending"
        val recipeOrItemName = doc.getString("recipeOrItemName") ?: ""
        val quantity = (doc.getLong("quantity") ?: 1L).toInt()
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return OrderEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
            orderNumber = orderNumber,
            customerName = customerName,
            customerPhone = customerPhone,
            description = description,
            dueDate = dueDate,
            totalAmount = totalAmount,
            totalCost = totalCost,
            status = status,
            recipeOrItemName = recipeOrItemName,
            quantity = quantity,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // 8. Task Converter
    fun documentToTask(doc: com.google.firebase.firestore.DocumentSnapshot, targetBakeryId: String): TaskEntity? {
        val title = doc.getString("title") ?: (doc.get("title")?.toString()) ?: return null
        if (title.isBlank()) return null
        val id = doc.getLong("id") ?: (doc.id.toLongOrNull() ?: 0L)
        val orderRef = doc.getString("orderRef") ?: ""
        val dueTime = doc.getString("dueTime") ?: "09:00"
        val priority = doc.getString("priority") ?: "Medium"
        val status = doc.getString("status") ?: "Pending"
        val dayOfWeek = doc.getString("dayOfWeek") ?: "Wed"
        val dueDate = doc.getString("dueDate") ?: ""
        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = extractUpdatedAt(doc)

        return TaskEntity(
            id = id,
            firestoreId = doc.id,
            userId = 0L,
            bakeryId = targetBakeryId,
            title = title,
            orderRef = orderRef,
            dueTime = dueTime,
            priority = priority,
            status = status,
            dayOfWeek = dayOfWeek,
            dueDate = dueDate,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // Cloud CRUD Operations

    suspend fun saveInventoryToCloud(bakeryId: String, item: InventoryItemEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("inventory").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "bakeryId" to targetBakeryId,
                "name" to item.name,
                "category" to item.category,
                "unit" to item.unit,
                "packagePrice" to item.packagePrice,
                "gramsPerUnit" to item.gramsPerUnit,
                "unitPrice" to item.unitPrice,
                "currentStock" to item.currentStock,
                "minStock" to item.minStock,
                "isLowStock" to item.isLowStock,
                "alertEnabled" to item.alertEnabled,
                "barcode" to item.barcode,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save inventory item to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryFromCloud(bakeryId: String, firestoreId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("inventory").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete inventory from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun saveRecipeToCloud(bakeryId: String, item: RecipeEntity): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("recipes").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "bakeryId" to targetBakeryId,
                "name" to item.name,
                "category" to item.category,
                "description" to item.description,
                "servings" to item.servings,
                "batchSize" to item.batchSize,
                "difficulty" to item.difficulty,
                "rating" to item.rating,
                "reviewCount" to item.reviewCount,
                "imageResName" to item.imageResName,
                "labourCost" to item.labourCost,
                "overheadsCost" to item.overheadsCost,
                "packagingCost" to item.packagingCost,
                "utilitiesCost" to item.utilitiesCost,
                "profitMarginPercent" to item.profitMarginPercent,
                "isFavorite" to item.isFavorite,
                "customSellingPrice" to item.customSellingPrice,
                "laborHours" to item.laborHours,
                "laborRatePerHour" to item.laborRatePerHour,
                "instructions" to item.instructions,
                "photoUri" to item.photoUri,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(docId)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save recipe to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRecipeFromCloud(bakeryId: String, firestoreId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("recipes").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete recipe from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun saveRecipeIngredientToCloud(
        bakeryId: String,
        recipeFirestoreId: String,
        ingredient: RecipeIngredientEntity
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        if (recipeFirestoreId.isBlank()) return Result.failure(IllegalArgumentException("recipeFirestoreId is blank"))
        val docId = if (ingredient.firestoreId.isNotBlank()) ingredient.firestoreId else (if (ingredient.id > 0) ingredient.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId)
                .collection("recipes").document(recipeFirestoreId)
                .collection("ingredients").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (ingredient.id > 0) ingredient.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "recipeId" to ingredient.recipeId,
                "recipeFirestoreId" to recipeFirestoreId,
                "bakeryId" to targetBakeryId,
                "name" to ingredient.name,
                "quantity" to ingredient.quantity,
                "unit" to ingredient.unit,
                "cost" to ingredient.cost,
                "createdAt" to ingredient.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save recipe ingredient to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRecipeIngredientFromCloud(
        bakeryId: String,
        recipeFirestoreId: String,
        firestoreId: String
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId)
                .collection("recipes").document(recipeFirestoreId)
                .collection("ingredients").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete recipe ingredient from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun saveCustomerToCloud(bakeryId: String, item: CustomerEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("customers").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "bakeryId" to targetBakeryId,
                "name" to item.name,
                "phone" to item.phone,
                "email" to item.email,
                "address" to item.address,
                "notes" to item.notes,
                "totalOrders" to item.totalOrders,
                "totalSpend" to item.totalSpend,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save customer to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomerFromCloud(bakeryId: String, firestoreId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("customers").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete customer from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun saveProductToCloud(bakeryId: String, item: ProductServiceEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("products").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "bakeryId" to targetBakeryId,
                "name" to item.name,
                "description" to item.description,
                "category" to item.category,
                "imageUrl" to item.imageUrl,
                "sku" to item.sku,
                "costPrice" to item.costPrice,
                "sellingPrice" to item.sellingPrice,
                "unit" to item.unit,
                "isActive" to item.isActive,
                "isService" to item.isService,
                "linkedRecipeId" to item.linkedRecipeId,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save product to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteProductFromCloud(bakeryId: String, firestoreId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("products").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete product from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun saveInvoiceToCloud(bakeryId: String, item: InvoiceEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("invoices").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "invoiceNumber" to item.invoiceNumber,
                "bakeryId" to targetBakeryId,
                "clientName" to item.clientName,
                "clientPhone" to item.clientPhone,
                "orderDescription" to item.orderDescription,
                "issueDate" to item.issueDate,
                "dueDate" to item.dueDate,
                "amount" to item.amount,
                "totalDue" to item.amount,
                "status" to item.status,
                "subtotal" to item.subtotal,
                "discount" to item.discountAmount,
                "taxRate" to item.taxRatePercent,
                "taxAmount" to item.taxAmount,
                "lineItemsJson" to item.lineItemsJson,
                "totalCost" to item.totalCost,
                "packagingTotal" to item.packagingTotal,
                "packagingItemsJson" to item.packagingItemsJson,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save invoice to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteInvoiceFromCloud(bakeryId: String, firestoreId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("invoices").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete invoice from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun saveQuoteToCloud(bakeryId: String, item: QuoteEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("quotes").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "quoteNumber" to item.quoteNumber,
                "bakeryId" to targetBakeryId,
                "clientName" to item.clientName,
                "clientPhone" to item.clientPhone,
                "eventType" to item.eventType,
                "eventDate" to item.eventDate,
                "recipeOrItemName" to item.recipeOrItemName,
                "estimatedCost" to item.estimatedCost,
                "profitMarginPercent" to item.profitMarginPercent,
                "quotedPrice" to item.quotedPrice,
                "status" to item.status,
                "docType" to item.docType,
                "subtotal" to item.subtotal,
                "discountAmount" to item.discountAmount,
                "taxRatePercent" to item.taxRatePercent,
                "taxAmount" to item.taxAmount,
                "lineItemsJson" to item.lineItemsJson,
                "totalCost" to item.totalCost,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save quote to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteQuoteFromCloud(bakeryId: String, firestoreId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("quotes").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete quote from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun saveOrderToCloud(bakeryId: String, item: OrderEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("orders").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "bakeryId" to targetBakeryId,
                "orderNumber" to item.orderNumber,
                "customerName" to item.customerName,
                "customerPhone" to item.customerPhone,
                "description" to item.description,
                "dueDate" to item.dueDate,
                "totalAmount" to item.totalAmount,
                "totalCost" to item.totalCost,
                "status" to item.status,
                "recipeOrItemName" to item.recipeOrItemName,
                "quantity" to item.quantity,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save order to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteOrderFromCloud(bakeryId: String, firestoreId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("orders").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete order from cloud", e)
            Result.failure(e)
        }
    }

    suspend fun saveTaskToCloud(bakeryId: String, item: TaskEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("tasks").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "bakeryId" to targetBakeryId,
                "title" to item.title,
                "orderRef" to item.orderRef,
                "dueTime" to item.dueTime,
                "priority" to item.priority,
                "status" to item.status,
                "dayOfWeek" to item.dayOfWeek,
                "dueDate" to item.dueDate,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save task to cloud", e)
            Result.failure(e)
        }
    }

    suspend fun deleteTaskFromCloud(bakeryId: String, firestoreId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("tasks").document(firestoreId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete task from cloud", e)
            Result.failure(e)
        }
    }

    /**
     * Saves or updates a packaging item in bakeries/{bakeryId}/packaging/{docId}
     * - Uses existing Firestore document ID if available, or item.id.
     * - Uses Firebase FieldValue.serverTimestamp() for conflict-safe updatedAt.
     */
    suspend fun savePackagingToCloud(bakeryId: String, item: PackagingItemEntity): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (item.firestoreId.isNotBlank()) item.firestoreId else (if (item.id > 0) item.id.toString() else System.currentTimeMillis().toString())
        return try {
            val docRef = db.collection("bakeries").document(targetBakeryId).collection("packaging").document(docId)
            val data = hashMapOf<String, Any?>(
                "id" to if (item.id > 0) item.id else (docId.toLongOrNull() ?: System.currentTimeMillis()),
                "bakeryId" to targetBakeryId,
                "name" to item.name,
                "category" to item.category,
                "unit" to item.unit,
                "packagePrice" to item.packagePrice,
                "packageQuantity" to item.packageQuantity,
                "gramsPerUnit" to item.gramsPerUnit,
                "unitPrice" to item.unitPrice,
                "currentStock" to item.currentStock,
                "minStock" to item.minStock,
                "isLowStock" to item.isLowStock,
                "alertEnabled" to item.alertEnabled,
                "barcode" to item.barcode,
                "supplier" to item.supplier,
                "notes" to item.notes,
                "createdAt" to item.createdAt,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save packaging to cloud", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a packaging item from bakeries/{bakeryId}/packaging/{id}
     */
    suspend fun deletePackagingFromCloud(bakeryId: String, itemId: Long, firestoreId: String = ""): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        val docId = if (firestoreId.isNotBlank()) firestoreId else itemId.toString()
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("packaging").document(docId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete packaging from cloud", e)
            Result.failure(e)
        }
    }

    /**
     * Reads all packaging documents from bakeries/{bakeryId}/packaging
     */
    suspend fun fetchPackagingFromCloud(bakeryId: String): List<PackagingItemEntity> {
        val db = firestore ?: return emptyList()
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            val snapshot = db.collection("bakeries").document(targetBakeryId).collection("packaging").get().awaitTask()
            snapshot.documents.mapNotNull { doc ->
                documentToPackagingItem(doc, targetBakeryId)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching packaging from cloud", e)
            emptyList()
        }
    }

    private var packagingListenerRegistration: ListenerRegistration? = null
    private val allListenerRegistrations = java.util.concurrent.CopyOnWriteArrayList<ListenerRegistration>()
    private val subcollectionRegistrations = java.util.concurrent.ConcurrentHashMap<String, ListenerRegistration>()
    private val syncScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    /**
     * Starts real-time Firestore snapshot listeners for all 9 collections:
     * 1. inventory
     * 2. recipes (+ recipe ingredients subcollections)
     * 3. customers
     * 4. products
     * 5. invoices
     * 6. quotes
     * 7. orders
     * 8. tasks
     * 9. packaging
     */
    fun startAllRealtimeListeners(
        bakeryId: String,
        repository: com.example.data.repository.AppRepository? = null
    ): List<ListenerRegistration> {
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId }
        if (targetBakeryId.isBlank()) {
            Log.w(TAG, "Cannot start listeners: bakeryId is blank")
            return emptyList()
        }

        stopAllRealtimeListeners()

        val db = firestore ?: return emptyList()
        Log.i(TAG, "Starting all 9 real-time listeners for bakery: $targetBakeryId")

        fun getRepo(): com.example.data.repository.AppRepository {
            return repository ?: activeRepository ?: com.example.data.repository.AppRepository(
                com.example.data.local.AppDatabase.getDatabase(BatchBossApplication.instance, syncScope)
            )
        }

        val bakeryDoc = db.collection("bakeries").document(targetBakeryId)

        // 1. Inventory listener
        try {
            val reg = bakeryDoc.collection("inventory").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToInventoryItem(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        getRepo().syncInventoryWithFirestore(targetBakeryId, items)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Inventory listener sync error", e)
                    }
                }
            }
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed inventory listener", e)
        }

        // 2. Recipes listener (+ subcollection listener for ingredients)
        try {
            val reg = bakeryDoc.collection("recipes").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToRecipe(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        val repo = getRepo()
                        repo.syncRecipesWithFirestore(targetBakeryId, items)

                        // Attach/manage ingredient subcollections
                        for (doc in snapshot.documents) {
                            val recipeFirestoreId = doc.id
                            if (!subcollectionRegistrations.containsKey(recipeFirestoreId)) {
                                val subReg = doc.reference.collection("ingredients")
                                    .addSnapshotListener { ingSnapshot, ingErr ->
                                        if (ingErr != null || ingSnapshot == null) return@addSnapshotListener
                                        val ingItems = ingSnapshot.documents.mapNotNull {
                                            documentToRecipeIngredient(it, targetBakeryId, recipeFirestoreId)
                                        }
                                        syncScope.launch {
                                            try {
                                                val localRecipe = repo.getRecipeByFirestoreId(recipeFirestoreId)
                                                val localRecipeId = localRecipe?.id ?: 0L
                                                repo.syncRecipeIngredientsWithFirestore(
                                                    targetBakeryId,
                                                    recipeFirestoreId,
                                                    localRecipeId,
                                                    ingItems
                                                )
                                            } catch (e: Throwable) {
                                                Log.e(TAG, "Recipe ingredient listener error", e)
                                            }
                                        }
                                    }
                                subcollectionRegistrations[recipeFirestoreId] = subReg
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Recipe listener sync error", e)
                    }
                }
            }
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed recipe listener", e)
        }

        // 3. Customers listener
        try {
            val reg = bakeryDoc.collection("customers").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToCustomer(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        getRepo().syncCustomersWithFirestore(targetBakeryId, items)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Customer listener sync error", e)
                    }
                }
            }
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed customer listener", e)
        }

        // 4. Products listener
        try {
            val reg = bakeryDoc.collection("products").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToProduct(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        getRepo().syncProductsWithFirestore(targetBakeryId, items)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Product listener sync error", e)
                    }
                }
            }
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed product listener", e)
        }

        // 5. Invoices listener
        try {
            val reg = bakeryDoc.collection("invoices").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToInvoice(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        getRepo().syncInvoicesWithFirestore(targetBakeryId, items)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Invoice listener sync error", e)
                    }
                }
            }
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed invoice listener", e)
        }

        // 6. Quotes listener
        try {
            val reg = bakeryDoc.collection("quotes").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToQuote(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        getRepo().syncQuotesWithFirestore(targetBakeryId, items)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Quote listener sync error", e)
                    }
                }
            }
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed quote listener", e)
        }

        // 7. Orders listener
        try {
            val reg = bakeryDoc.collection("orders").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToOrder(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        getRepo().syncOrdersWithFirestore(targetBakeryId, items)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Order listener sync error", e)
                    }
                }
            }
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed order listener", e)
        }

        // 8. Tasks listener
        try {
            val reg = bakeryDoc.collection("tasks").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToTask(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        getRepo().syncTasksWithFirestore(targetBakeryId, items)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Task listener sync error", e)
                    }
                }
            }
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed task listener", e)
        }

        // 9. Packaging listener
        try {
            val reg = bakeryDoc.collection("packaging").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { documentToPackagingItem(it, targetBakeryId) }
                syncScope.launch {
                    try {
                        getRepo().syncPackagingWithFirestore(targetBakeryId, items)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Packaging listener sync error", e)
                    }
                }
            }
            packagingListenerRegistration = reg
            allListenerRegistrations.add(reg)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed packaging listener", e)
        }

        return allListenerRegistrations.toList()
    }

    /**
     * Backward-compatible wrapper for packaging-only listener.
     */
    fun startPackagingListener(bakeryId: String, repository: com.example.data.repository.AppRepository? = null): ListenerRegistration? {
        val list = startAllRealtimeListeners(bakeryId, repository)
        return packagingListenerRegistration ?: list.lastOrNull()
    }

    /**
     * Stops and removes all 9 real-time Firestore listeners and any subcollection listeners.
     */
    fun stopAllRealtimeListeners() {
        for (reg in allListenerRegistrations) {
            try { reg.remove() } catch (_: Throwable) {}
        }
        allListenerRegistrations.clear()
        packagingListenerRegistration?.remove()
        packagingListenerRegistration = null

        for ((_, subReg) in subcollectionRegistrations) {
            try { subReg.remove() } catch (_: Throwable) {}
        }
        subcollectionRegistrations.clear()
        Log.i(TAG, "Stopped all real-time Firestore listeners")
    }

    fun stopPackagingListener() {
        stopAllRealtimeListeners()
    }

    /**
     * Comprehensive Manual Sync:
     * 1. Verifies Firebase authentication.
     * 2. Reads exact bakeryId.
     * 3. Downloads all nine collections.
     * 4. Downloads every recipe's ingredients subcollection.
     * 5. Updates Room using upserts (newest updatedAt wins).
     * 6. Removes local records deleted remotely.
     * 7. Uploads legitimate pending local changes (records with blank firestoreId).
     * 8. Recalculates affected recipes, inventory totals and dashboard values.
     * 9. Returns clear result: "Sync complete" or real Firebase error.
     */
    suspend fun syncAllWithCloud(
        bakeryId: String,
        repository: com.example.data.repository.AppRepository
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firebase is not configured."))
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth not initialized."))
        val user = authInstance.currentUser ?: return Result.failure(IllegalStateException("Not authenticated with Firebase. Please log in first."))

        var targetBakeryId = bakeryId.ifBlank { currentBakeryId }
        if (targetBakeryId.isBlank()) {
            try {
                val userDoc = db.collection("users").document(user.uid).get().awaitTask()
                targetBakeryId = userDoc.getString("bakeryId")?.trim() ?: ""
            } catch (e: Throwable) {
                return Result.failure(IllegalStateException("Could not load bakeryId from users/${user.uid}: ${e.message}"))
            }
        }
        if (targetBakeryId.isBlank()) {
            return Result.failure(IllegalStateException("No bakeryId configured for current user."))
        }
        currentBakeryId = targetBakeryId

        return try {
            val bakeryDoc = db.collection("bakeries").document(targetBakeryId)

            // 1. Download Inventory
            val invSnapshot = bakeryDoc.collection("inventory").get().awaitTask()
            val invItems = invSnapshot.documents.mapNotNull { documentToInventoryItem(it, targetBakeryId) }
            repository.syncInventoryWithFirestore(targetBakeryId, invItems)

            // 2. Download Recipes + subcollections
            val recipesSnapshot = bakeryDoc.collection("recipes").get().awaitTask()
            val recipeItems = recipesSnapshot.documents.mapNotNull { documentToRecipe(it, targetBakeryId) }
            repository.syncRecipesWithFirestore(targetBakeryId, recipeItems)

            for (recipeDoc in recipesSnapshot.documents) {
                val recipeFirestoreId = recipeDoc.id
                val ingSnapshot = recipeDoc.reference.collection("ingredients").get().awaitTask()
                val ingItems = ingSnapshot.documents.mapNotNull { documentToRecipeIngredient(it, targetBakeryId, recipeFirestoreId) }
                val localRecipe = repository.getRecipeByFirestoreId(recipeFirestoreId)
                val localRecipeId = localRecipe?.id ?: 0L
                repository.syncRecipeIngredientsWithFirestore(targetBakeryId, recipeFirestoreId, localRecipeId, ingItems)
            }

            // 3. Download Customers
            val custSnapshot = bakeryDoc.collection("customers").get().awaitTask()
            val custItems = custSnapshot.documents.mapNotNull { documentToCustomer(it, targetBakeryId) }
            repository.syncCustomersWithFirestore(targetBakeryId, custItems)

            // 4. Download Products
            val prodSnapshot = bakeryDoc.collection("products").get().awaitTask()
            val prodItems = prodSnapshot.documents.mapNotNull { documentToProduct(it, targetBakeryId) }
            repository.syncProductsWithFirestore(targetBakeryId, prodItems)

            // 5. Download Invoices
            val invsSnapshot = bakeryDoc.collection("invoices").get().awaitTask()
            val invoiceItems = invsSnapshot.documents.mapNotNull { documentToInvoice(it, targetBakeryId) }
            repository.syncInvoicesWithFirestore(targetBakeryId, invoiceItems)

            // 6. Download Quotes
            val quotesSnapshot = bakeryDoc.collection("quotes").get().awaitTask()
            val quoteItems = quotesSnapshot.documents.mapNotNull { documentToQuote(it, targetBakeryId) }
            repository.syncQuotesWithFirestore(targetBakeryId, quoteItems)

            // 7. Download Orders
            val ordersSnapshot = bakeryDoc.collection("orders").get().awaitTask()
            val orderItems = ordersSnapshot.documents.mapNotNull { documentToOrder(it, targetBakeryId) }
            repository.syncOrdersWithFirestore(targetBakeryId, orderItems)

            // 8. Download Tasks
            val tasksSnapshot = bakeryDoc.collection("tasks").get().awaitTask()
            val taskItems = tasksSnapshot.documents.mapNotNull { documentToTask(it, targetBakeryId) }
            repository.syncTasksWithFirestore(targetBakeryId, taskItems)

            // 9. Download Packaging
            val pkgSnapshot = bakeryDoc.collection("packaging").get().awaitTask()
            val pkgItems = pkgSnapshot.documents.mapNotNull { documentToPackagingItem(it, targetBakeryId) }
            repository.syncPackagingWithFirestore(targetBakeryId, pkgItems)

            // Upload legitimate pending local changes (items with no firestoreId)
            val pendingPkg = repository.getPackagingByBakeryOnce(targetBakeryId).filter { it.firestoreId.isBlank() }
            for (p in pendingPkg) savePackagingToCloud(targetBakeryId, p)

            val pendingInv = repository.getInventoryByBakeryOnce(targetBakeryId).filter { it.firestoreId.isBlank() }
            for (i in pendingInv) saveInventoryToCloud(targetBakeryId, i)

            val pendingRecipes = repository.getRecipesByBakeryOnce(targetBakeryId).filter { it.firestoreId.isBlank() }
            for (r in pendingRecipes) saveRecipeToCloud(targetBakeryId, r)

            // Recalculate affected recipes and inventory totals
            repository.recalculateRecipeIngredientCosts(targetBakeryId)

            // Ensure listeners are active
            startAllRealtimeListeners(targetBakeryId, repository)

            Result.success("Sync complete")
        } catch (e: Throwable) {
            Log.e(TAG, "Manual sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Backward-compatible listener wrapper taking a raw callback.
     */
    fun listenToPackaging(bakeryId: String, onUpdate: (List<PackagingItemEntity>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        val targetBakeryId = bakeryId.ifBlank { currentBakeryId.ifBlank { getEffectiveUserId() } }
        return try {
            db.collection("bakeries").document(targetBakeryId).collection("packaging")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error listening to packaging collection: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            documentToPackagingItem(doc, targetBakeryId)
                        }
                        onUpdate(items)
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register packaging listener", e)
            null
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
