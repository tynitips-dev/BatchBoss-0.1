package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import java.util.UUID

class AppRepository(private val database: AppDatabase) {
    val userAccountDao = database.userAccountDao()
    val customerDao = database.customerDao()
    val orderDao = database.orderDao()
    val recipeDao = database.recipeDao()
    val ingredientDao = database.recipeIngredientDao()
    val taskDao = database.taskDao()
    val inventoryDao = database.inventoryDao()
    val packagingDao = database.packagingDao()
    val supplierDao = database.supplierDao()
    val specialDealDao = database.specialDealDao()
    val notificationDao = database.notificationDao()
    val invoiceDao = database.invoiceDao()
    val quoteDao = database.quoteDao()
    val userProfileDao = database.userProfileDao()
    val bakingSupplyStoreDao = database.bakingSupplyStoreDao()
    val productServiceDao = database.productServiceDao()
    val productPriceHistoryDao = database.productPriceHistoryDao()
    val documentLineItemDao = database.documentLineItemDao()
    val userLoginLogDao = database.userLoginLogDao()
    val dataDeletionRequestDao = database.dataDeletionRequestDao()

    suspend fun <T> atomic(block: suspend () -> T): T = database.withTransaction { block() }

    val pendingCloudChanges = database.pendingCloudChangeDao()

    suspend fun acknowledgeCloudChange(change: PendingCloudChange, serverUpdatedAt: Long) = database.withTransaction {
        if (pendingCloudChanges.get(change.bakeryId, change.path)?.revision != change.revision) return@withTransaction
        if (!change.deleted) when (change.collection) {
            "customers" -> customerDao.getCustomerByIdOnce(change.localId)?.let { customerDao.updateCustomer(it.copy(updatedAt = serverUpdatedAt)) }
            "products" -> productServiceDao.getProductByIdOnce(change.localId)?.let { productServiceDao.updateProduct(it.copy(updatedAt = serverUpdatedAt)) }
            "invoices" -> invoiceDao.getInvoiceByIdOnce(change.localId)?.let { invoiceDao.updateInvoice(it.copy(updatedAt = serverUpdatedAt)) }
            "quotes" -> quoteDao.getQuoteByIdOnce(change.localId)?.let { quoteDao.updateQuote(it.copy(updatedAt = serverUpdatedAt)) }
            "inventory" -> inventoryDao.getInventoryByIdOnce(change.localId)?.let { inventoryDao.updateItem(it.copy(updatedAt = serverUpdatedAt)) }
            "recipes" -> recipeDao.getRecipeByIdOnce(change.localId)?.let { recipeDao.updateRecipe(it.copy(updatedAt = serverUpdatedAt)) }
            "ingredients" -> ingredientDao.getIngredientByIdOnce(change.localId)?.let { ingredientDao.updateIngredient(it.copy(updatedAt = serverUpdatedAt)) }
        }
        pendingCloudChanges.acknowledge(change.bakeryId, change.path, change.revision)
    }

    /** Recover only rows explicitly owned by this established local account; never claim userId=0 legacy data. */
    suspend fun recoverOwnedLocalRecords(userId: Long, bakeryId: String) = database.withTransaction {
        require(userId > 0 && userAccountDao.getUserByIdOnce(userId)?.bakeryId == bakeryId && bakeryId.isNotBlank())
        for (row in customerDao.getCustomersByUser(userId).first().filter { it.userId == userId && it.bakeryId.isBlank() }) insertCustomer(row.copy(bakeryId = bakeryId))
        for (row in productServiceDao.getProductsByUser(userId).first().filter { it.userId == userId && it.bakeryId.isBlank() }) insertProduct(row.copy(bakeryId = bakeryId))
        for (row in invoiceDao.getInvoicesByUser(userId).first().filter { it.userId == userId && it.bakeryId.isBlank() }) insertInvoice(row.copy(bakeryId = bakeryId))
        for (row in quoteDao.getQuotesByUser(userId).first().filter { it.userId == userId && it.bakeryId.isBlank() }) insertQuote(row.copy(bakeryId = bakeryId))
        for (row in inventoryDao.getInventoryByUser(userId).first().filter { it.userId == userId && it.bakeryId.isBlank() }) insertInventoryItem(row.copy(bakeryId = bakeryId))
        for (row in recipeDao.getRecipesByUser(userId).first().filter { it.userId == userId && it.bakeryId.isBlank() }) {
            insertRecipe(row.copy(bakeryId = bakeryId))
            for (ingredient in ingredientDao.getIngredientsForRecipeOnce(row.id)) insertIngredient(ingredient)
        }
    }

    private suspend fun workspace(userId: Long, supplied: String): String {
        val accountBakery = userAccountDao.getUserByIdOnce(userId)?.bakeryId.orEmpty()
        require(supplied.isBlank() || accountBakery.isBlank() || supplied == accountBakery) { "The record belongs to another bakery." }
        return supplied.ifBlank { accountBakery }.also {
            require(it.isNotBlank()) { "Sign in to your bakery workspace before saving." }
        }
    }

    private suspend fun queue(bakeryId: String, collection: String, localId: Long, documentId: String,
                              previousUpdatedAt: Long?, deleted: Boolean = false, parent: String = "") {
        val path = if (parent.isBlank()) "$collection/$documentId" else "recipes/$parent/ingredients/$documentId"
        val previous = pendingCloudChanges.get(bakeryId, path)
        pendingCloudChanges.put(PendingCloudChange(bakeryId, path, collection, localId, UUID.randomUUID().toString(),
            previous?.let { (it.ancestors.split(',').filter(String::isNotBlank) + it.revision).joinToString(",") }.orEmpty(),
            if (previous != null) previous.expectedUpdatedAt else previousUpdatedAt, deleted))
    }

    private fun LineItem.toDocumentRow(type: String, id: Long, uid: Long) = DocumentLineItemEntity(
        documentType = type, documentId = id, userId = uid, productId = productId, itemName = itemName,
        description = description, quantity = quantity, unit = unit, unitPrice = unitPrice,
        discount = discount, costPrice = costPrice, lineTotal = lineTotal)

    // User Accounts
    val allUsers: Flow<List<UserAccountEntity>> = userAccountDao.getAllUsers()
    fun getUserById(id: Long): Flow<UserAccountEntity?> = userAccountDao.getUserById(id)
    suspend fun getUserByIdOnce(id: Long): UserAccountEntity? = userAccountDao.getUserByIdOnce(id)
    suspend fun getUserByEmail(email: String): UserAccountEntity? = userAccountDao.getUserByEmail(email)
    suspend fun insertUserAccount(user: UserAccountEntity): Long = userAccountDao.insertUser(user)
    suspend fun updateUserAccount(user: UserAccountEntity) = userAccountDao.updateUser(user)

    // Recipes
    val allRecipes: Flow<List<RecipeEntity>> = recipeDao.getAllRecipes()
    fun getRecipesByUser(userId: Long): Flow<List<RecipeEntity>> = recipeDao.getRecipesByUser(userId)
    fun getRecipesByBakery(bakeryId: String): Flow<List<RecipeEntity>> = recipeDao.getRecipesByBakery(bakeryId)
    suspend fun getRecipesByBakeryOnce(bakeryId: String): List<RecipeEntity> = recipeDao.getRecipesByBakeryOnce(bakeryId)

    fun getRecipeById(id: Long): Flow<RecipeEntity?> = recipeDao.getRecipeById(id)
    suspend fun getRecipeByIdOnce(id: Long): RecipeEntity? = recipeDao.getRecipeByIdOnce(id)
    suspend fun getRecipeByFirestoreId(firestoreId: String): RecipeEntity? = recipeDao.getRecipeByFirestoreId(firestoreId)

    suspend fun insertRecipe(recipe: RecipeEntity): Long = database.withTransaction {
        val old = if (recipe.id > 0) recipeDao.getRecipeByIdOnce(recipe.id) else null
        val bId = workspace(recipe.userId, recipe.bakeryId)
        require(old == null || old.bakeryId == bId || old.bakeryId.isBlank() && old.userId == recipe.userId && userAccountDao.getUserByIdOnce(recipe.userId)?.bakeryId == bId) { "Cannot replace another bakery's record." }
        val record = recipe.copy(bakeryId = bId, firestoreId = recipe.firestoreId.ifBlank { old?.firestoreId.orEmpty().ifBlank { if (old != null) old.id.toString() else UUID.randomUUID().toString() } })
        val id = recipeDao.insertRecipe(record)
        queue(bId, "recipes", id, record.firestoreId, old?.takeIf { it.firestoreId.isNotBlank() }?.updatedAt)
        id
    }

    suspend fun updateRecipe(recipe: RecipeEntity) { insertRecipe(recipe) }

    suspend fun deleteRecipe(id: Long) = database.withTransaction {
        val old = recipeDao.getRecipeByIdOnce(id) ?: return@withTransaction
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "recipes", id, old.firestoreId, old.updatedAt, deleted = true)
        recipeDao.deleteRecipe(id)
        for (ingredient in ingredientDao.getIngredientsForRecipeOnce(id)) deleteIngredient(ingredient.id)
    }

    suspend fun toggleFavoriteRecipe(id: Long) = recipeDao.toggleFavorite(id)

    suspend fun updateProfitMargin(id: Long, margin: Double) = database.withTransaction {
        val old = recipeDao.getRecipeByIdOnce(id) ?: return@withTransaction
        recipeDao.updateProfitMargin(id, margin)
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "recipes", id, old.firestoreId, old.updatedAt)
    }

    suspend fun updateRecipePricing(
        id: Long,
        labour: Double,
        overheads: Double,
        packaging: Double,
        utilities: Double,
        profitMargin: Double,
        sellingPrice: Double
    ) = database.withTransaction {
        val old = recipeDao.getRecipeByIdOnce(id) ?: return@withTransaction
        recipeDao.updateRecipePricing(id, labour, overheads, packaging, utilities, profitMargin, sellingPrice)
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "recipes", id, old.firestoreId, old.updatedAt)
    }

    suspend fun updateRecipePhoto(id: Long, photoUri: String) = database.withTransaction {
        val old = recipeDao.getRecipeByIdOnce(id) ?: return@withTransaction
        recipeDao.updateRecipePhoto(id, photoUri)
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "recipes", id, old.firestoreId, old.updatedAt)
    }

    // Ingredients
    fun getIngredientsForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>> =
        ingredientDao.getIngredientsForRecipe(recipeId)

    suspend fun getIngredientsForRecipeOnce(recipeId: Long): List<RecipeIngredientEntity> =
        ingredientDao.getIngredientsForRecipeOnce(recipeId)

    fun getIngredientsForRecipeFirestoreId(recipeFirestoreId: String): Flow<List<RecipeIngredientEntity>> =
        ingredientDao.getIngredientsForRecipeFirestoreId(recipeFirestoreId)

    suspend fun getIngredientsForRecipeFirestoreIdOnce(recipeFirestoreId: String): List<RecipeIngredientEntity> =
        ingredientDao.getIngredientsForRecipeFirestoreIdOnce(recipeFirestoreId)

    suspend fun getIngredientsByBakeryOnce(bakeryId: String): List<RecipeIngredientEntity> =
        ingredientDao.getIngredientsByBakeryOnce(bakeryId)

    suspend fun updateIngredientCost(id: Long, cost: Double, quantity: Double) = database.withTransaction {
        val old = ingredientDao.getIngredientByIdOnce(id) ?: return@withTransaction
        insertIngredient(old.copy(cost = cost, quantity = quantity, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveIngredients(recipeId: Long, ingredients: List<RecipeIngredientEntity>) = database.withTransaction {
        for (old in ingredientDao.getIngredientsForRecipeOnce(recipeId)) deleteIngredient(old.id)
        for (ingredient in ingredients) insertIngredient(ingredient.copy(id = 0, recipeId = recipeId))
    }

    suspend fun insertIngredient(ingredient: RecipeIngredientEntity): Long = database.withTransaction {
        val parent = requireNotNull(recipeDao.getRecipeByIdOnce(ingredient.recipeId)) { "Save the recipe before its ingredients." }
        require(parent.bakeryId.isNotBlank() && parent.firestoreId.isNotBlank()) { "The recipe has no cloud identity." }
        val old = if (ingredient.id > 0) ingredientDao.getIngredientByIdOnce(ingredient.id) else null
        require(old == null || old.bakeryId == parent.bakeryId || old.bakeryId.isBlank() && old.recipeId == parent.id) { "Cannot replace another bakery's ingredient." }
        val record = ingredient.copy(bakeryId = parent.bakeryId, userId = parent.userId,
            recipeFirestoreId = parent.firestoreId, firestoreId = ingredient.firestoreId.ifBlank { old?.firestoreId.orEmpty().ifBlank { UUID.randomUUID().toString() } })
        val id = ingredientDao.insertIngredient(record)
        queue(parent.bakeryId, "ingredients", id, record.firestoreId, old?.takeIf { it.firestoreId.isNotBlank() }?.updatedAt, parent = parent.firestoreId)
        id
    }

    suspend fun deleteIngredient(id: Long) = database.withTransaction {
        val old = ingredientDao.getIngredientByIdOnce(id) ?: return@withTransaction
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "ingredients", id, old.firestoreId, old.updatedAt, deleted = true, parent = old.recipeFirestoreId)
        ingredientDao.deleteIngredient(id)
    }

    // Tasks
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    fun getTasksByUser(userId: Long): Flow<List<TaskEntity>> = taskDao.getTasksByUser(userId)
    fun getTasksByBakery(bakeryId: String): Flow<List<TaskEntity>> = taskDao.getTasksByBakery(bakeryId)
    suspend fun getTasksByBakeryOnce(bakeryId: String): List<TaskEntity> = taskDao.getTasksByBakeryOnce(bakeryId)

    suspend fun insertTask(task: TaskEntity): Long = taskDao.insertTask(task)

    suspend fun updateTaskStatus(id: Long, status: String) = taskDao.updateTaskStatus(id, status)

    suspend fun deleteTask(id: Long) = taskDao.deleteTask(id)

    // Inventory
    val allInventory: Flow<List<InventoryItemEntity>> = inventoryDao.getAllInventory()
    val lowStockItems: Flow<List<InventoryItemEntity>> = inventoryDao.getLowStockItems()
    fun getInventoryByUser(userId: Long): Flow<List<InventoryItemEntity>> = inventoryDao.getInventoryByUser(userId)
    fun getLowStockByUser(userId: Long): Flow<List<InventoryItemEntity>> = inventoryDao.getLowStockByUser(userId)
    fun getInventoryByBakery(bakeryId: String): Flow<List<InventoryItemEntity>> = inventoryDao.getInventoryByBakery(bakeryId)
    fun getLowStockByBakery(bakeryId: String): Flow<List<InventoryItemEntity>> = inventoryDao.getLowStockByBakery(bakeryId)
    suspend fun getInventoryByBakeryOnce(bakeryId: String): List<InventoryItemEntity> = inventoryDao.getInventoryByBakeryOnce(bakeryId)

    suspend fun toggleInventoryAlert(id: Long) = inventoryDao.toggleAlert(id)

    suspend fun updateStockPrice(id: Long, unitPrice: Double, currentStock: Double, minStock: Double) = database.withTransaction {
        val old = inventoryDao.getInventoryByIdOnce(id) ?: return@withTransaction
        insertInventoryItem(old.copy(unitPrice = unitPrice, currentStock = currentStock, minStock = minStock, isLowStock = currentStock <= minStock, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateStockItemFull(id: Long, unitPrice: Double, packagePrice: Double, gramsPerUnit: Double, currentStock: Double, minStock: Double) = database.withTransaction {
        val old = inventoryDao.getInventoryByIdOnce(id) ?: return@withTransaction
        insertInventoryItem(old.copy(unitPrice = unitPrice, currentStock = currentStock, minStock = minStock, isLowStock = currentStock <= minStock, updatedAt = System.currentTimeMillis(), packagePrice = packagePrice, gramsPerUnit = gramsPerUnit))
    }

    suspend fun insertInventoryItem(item: InventoryItemEntity): Long = database.withTransaction {
        val old = if (item.id > 0) inventoryDao.getInventoryByIdOnce(item.id) else null
        val bId = workspace(item.userId, item.bakeryId)
        require(old == null || old.bakeryId == bId || old.bakeryId.isBlank() && old.userId == item.userId && userAccountDao.getUserByIdOnce(item.userId)?.bakeryId == bId) { "Cannot replace another bakery's record." }
        val record = item.copy(bakeryId = bId, firestoreId = item.firestoreId.ifBlank { old?.firestoreId.orEmpty().ifBlank { if (old != null) old.id.toString() else UUID.randomUUID().toString() } }).normalized()
        val id = inventoryDao.insertItem(record)
        queue(bId, "inventory", id, record.firestoreId, old?.takeIf { it.firestoreId.isNotBlank() }?.updatedAt)
        id
    }

    suspend fun deleteInventoryItem(id: Long) = database.withTransaction {
        val old = inventoryDao.getInventoryByIdOnce(id) ?: return@withTransaction
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "inventory", id, old.firestoreId, old.updatedAt, deleted = true)
        inventoryDao.deleteItem(id)
    }

    // Packaging
    val allPackaging: Flow<List<PackagingItemEntity>> = packagingDao.getAllPackaging()
    val lowStockPackaging: Flow<List<PackagingItemEntity>> = packagingDao.getLowStockPackaging()
    fun getPackagingByUser(userId: Long): Flow<List<PackagingItemEntity>> = packagingDao.getPackagingByUser(userId)
    fun getPackagingByBakery(bakeryId: String): Flow<List<PackagingItemEntity>> = packagingDao.getPackagingByBakery(bakeryId)
    fun getLowStockPackagingByBakery(bakeryId: String): Flow<List<PackagingItemEntity>> = packagingDao.getLowStockPackagingByBakery(bakeryId)
    fun getLowStockPackagingByUser(userId: Long): Flow<List<PackagingItemEntity>> = packagingDao.getLowStockPackagingByUser(userId)
    fun getPackagingById(id: Long): Flow<PackagingItemEntity?> = packagingDao.getPackagingById(id)
    suspend fun getPackagingByIdOnce(id: Long): PackagingItemEntity? = packagingDao.getPackagingByIdOnce(id)
    suspend fun getPackagingByBakeryOnce(bakeryId: String): List<PackagingItemEntity> = packagingDao.getPackagingByBakeryOnce(bakeryId)
    suspend fun insertPackaging(item: PackagingItemEntity): Long = packagingDao.insertPackaging(item)
    suspend fun insertPackagingList(items: List<PackagingItemEntity>) = packagingDao.insertPackagingList(items)
    suspend fun updatePackaging(item: PackagingItemEntity) = packagingDao.updatePackaging(item)
    suspend fun updatePackagingStockAndPrice(
        id: Long,
        packagePrice: Double,
        packageQuantity: Double,
        unitPrice: Double,
        currentStock: Double,
        minStock: Double
    ) {
        val isLow = currentStock <= minStock
        packagingDao.updateStockAndPrice(id, packagePrice, packageQuantity, unitPrice, currentStock, minStock, isLow)
    }
    suspend fun togglePackagingAlert(id: Long) = packagingDao.toggleAlert(id)
    suspend fun deletePackaging(id: Long) = packagingDao.deletePackaging(id)
    suspend fun deletePackagingByBakery(bakeryId: String) = packagingDao.deletePackagingByBakery(bakeryId)

    /**
     * Reconciles Firestore packaging documents into Room for the specified bakery.
     * - Upserts added and modified items into local Room.
     * - Deletes local Room items that no longer exist in Firestore.
     * - Preserves Firestore doc ID as the permanent sync ID (firestoreId).
     * - Preserves existing local ID so duplicate rows are never created.
     * - Conflict-safe: newest updatedAt wins.
     */
    suspend fun syncPackagingWithFirestore(bakeryId: String, remoteItems: List<PackagingItemEntity>): Int {
        val localItems = packagingDao.getPackagingByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }
        val localById = localItems.associateBy { it.id }
        val localByName = localItems.associateBy { it.name.trim().lowercase() }

        val remoteDocIds = mutableSetOf<String>()

        for (remote in remoteItems) {
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) {
                remoteDocIds.add(docId)
            }

            // Find existing local item: by firestoreId, or by numeric ID, or by matching name
            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)
                ?: (if (remote.id > 0) localById[remote.id] else null)
                ?: localByName[remote.name.trim().lowercase()]

            if (existing != null) {
                if (docId.isNotBlank()) {
                    remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                }
                // Conflict resolution: newest updatedAt wins
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id, // Preserve existing local ID!
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    packagingDao.updatePackaging(updated)
                }
            } else {
                // Insert new item without colliding with local primary keys
                val newLocalId = if (remote.id > 0 && !localById.containsKey(remote.id)) remote.id else 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    bakeryId = bakeryId
                )
                val insertedId = packagingDao.insertPackaging(newItem)
                if (docId.isBlank()) {
                    remoteDocIds.add(insertedId.toString())
                }
            }
        }

        // Delete local items that were deleted on Firestore
        val toDelete = localItems.filter { localItem ->
            val localKey = localItem.firestoreId.ifBlank { localItem.id.toString() }
            !remoteDocIds.contains(localKey)
        }
        if (toDelete.isNotEmpty()) {
            packagingDao.deletePackagingByIds(toDelete.map { it.id })
        }

        return remoteItems.size
    }

    /**
     * Reconciles Firestore inventory documents into Room for the specified bakery.
     * Newest updatedAt wins. Preserves local primary key ID.
     * Automatically recalculates recipe ingredient costs affected by inventory changes.
     */
    suspend fun syncInventoryWithFirestore(bakeryId: String, remoteItems: List<InventoryItemEntity>): Int = database.withTransaction {
        val localItems = inventoryDao.getInventoryByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }

        val remoteDocIds = mutableSetOf<String>()

        val pending = pendingCloudChanges.list(bakeryId).filter { it.collection == "inventory" }
        val dirtyIds = pending.map { it.localId }.toSet()
        val dirtyDocuments = pending.map { it.path.substringAfterLast('/') }.toSet()
        for (remote in remoteItems) {
            if (remote.firestoreId in dirtyDocuments) continue
            val norm = remote.normalized()
            val docId = norm.firestoreId.ifBlank { if (norm.id > 0) norm.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

                val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)

                if (existing?.id in dirtyIds) continue
            if (existing != null) {
                    if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                    if (norm.updatedAt >= existing.updatedAt) {
                        val updated = norm.copy(
                            id = existing.id,
                            firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                            userId = if (norm.userId != 0L) norm.userId else existing.userId,
                            bakeryId = bakeryId
                        ).normalized()
                        inventoryDao.updateItem(updated)
                    }
                } else {
                    val newLocalId = 0L
                    val newItem = norm.copy(
                        id = newLocalId,
                        firestoreId = docId,
                        bakeryId = bakeryId
                    ).normalized()
                    val insertedId = inventoryDao.insertItem(newItem)
                    if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
                }
        }

        val toDelete = localItems.filter { localItem ->
            if (localItem.id in dirtyIds) return@filter false
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            inventoryDao.deleteInventoryByIds(toDelete.map { it.id })
        }

        // Recalculate recipe ingredient costs based on new inventory prices
        recalculateRecipeIngredientCosts(bakeryId)

        remoteItems.size
    }

    /**
     * Recalculates cost for all recipe ingredients matching inventory items in this bakery.
     * E.g. Package price R50 for 1 kg -> 240g in recipe becomes R12.00.
     */
    suspend fun recalculateRecipeIngredientCosts(bakeryId: String) {
        val inventory = inventoryDao.getInventoryByBakeryOnce(bakeryId)
        val inventoryByName = inventory.associateBy { it.name.trim().lowercase() }
        val allIngredients = ingredientDao.getIngredientsByBakeryOnce(bakeryId)
        for (ing in allIngredients) {
            val inv = inventoryByName[ing.name.trim().lowercase()]
            if (inv != null && (inv.packagePrice > 0.0 || inv.unitPrice > 0.0)) {
                val newCost = if (inv.packagePrice > 0.0 && inv.packageQuantity > 0.0) {
                    com.example.util.UnitUtils.calculateIngredientCost(
                        recipeQuantity = ing.quantity,
                        recipeUnit = ing.unit,
                        packagePrice = inv.packagePrice,
                        packageQuantity = inv.packageQuantity,
                        packageUnit = inv.unit
                    )
                } else {
                    com.example.util.UnitUtils.calculateCost(ing.quantity, ing.unit, inv.unitPrice)
                }
                if (newCost > 0.0 && Math.abs(newCost - ing.cost) > 0.0001) {
                    ingredientDao.updateIngredientCost(ing.id, newCost, ing.quantity, System.currentTimeMillis())
                }
            }
        }
    }

    /**
     * Reconciles Firestore recipe documents into Room for the specified bakery.
     */
    suspend fun syncRecipesWithFirestore(bakeryId: String, remoteItems: List<RecipeEntity>): Int = database.withTransaction {
        val localItems = recipeDao.getRecipesByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }

        val remoteDocIds = mutableSetOf<String>()

        val pending = pendingCloudChanges.list(bakeryId).filter { it.collection == "recipes" }
        val dirtyIds = pending.map { it.localId }.toSet()
        val dirtyDocuments = pending.map { it.path.substringAfterLast('/') }.toSet()
        for (remote in remoteItems) {
            if (remote.firestoreId in dirtyDocuments) continue
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)

            if (existing?.id in dirtyIds) continue
            if (existing != null) {
                if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id,
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    recipeDao.updateRecipe(updated)
                }
            } else {
                val newLocalId = 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    bakeryId = bakeryId
                )
                val insertedId = recipeDao.insertRecipe(newItem)
                if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
            }
        }

        val toDelete = localItems.filter { localItem ->
            if (localItem.id in dirtyIds) return@filter false
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            recipeDao.deleteRecipesByIds(toDelete.map { it.id })
            for (del in toDelete) {
                ingredientDao.deleteIngredientsForRecipe(del.id)
                if (del.firestoreId.isNotBlank()) {
                    ingredientDao.deleteIngredientsForRecipeFirestoreId(del.firestoreId)
                }
            }
        }

        remoteItems.size
    }

    /**
     * Reconciles recipe ingredients subcollection:
     * bakeries/{bakeryId}/recipes/{recipeFirestoreId}/ingredients
     */
    suspend fun syncRecipeIngredientsWithFirestore(
        bakeryId: String,
        recipeFirestoreId: String,
        recipeLocalId: Long,
        remoteItems: List<RecipeIngredientEntity>
    ): Int = database.withTransaction {
        val localIngredients = (if (recipeFirestoreId.isNotBlank()) {
            ingredientDao.getIngredientsForRecipeFirestoreIdOnce(recipeFirestoreId)
        } else emptyList()).ifEmpty {
            if (recipeLocalId > 0) ingredientDao.getIngredientsForRecipeOnce(recipeLocalId) else emptyList()
        }

        val localByFirestoreId = localIngredients.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }

        val remoteDocIds = mutableSetOf<String>()

        val pending = pendingCloudChanges.list(bakeryId).filter { it.collection == "ingredients" }
        val dirtyIds = pending.map { it.localId }.toSet()
        val dirtyDocuments = pending.map { it.path.substringAfterLast('/') }.toSet()
        for (remote in remoteItems) {
            if (remote.firestoreId in dirtyDocuments) continue
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)

            if (existing?.id in dirtyIds) continue
            if (existing != null) {
                if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id,
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        recipeId = if (recipeLocalId > 0) recipeLocalId else existing.recipeId,
                        recipeFirestoreId = if (recipeFirestoreId.isNotBlank()) recipeFirestoreId else existing.recipeFirestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    ingredientDao.updateIngredient(updated)
                }
            } else {
                val newLocalId = 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    recipeId = recipeLocalId,
                    recipeFirestoreId = recipeFirestoreId,
                    bakeryId = bakeryId
                )
                val insertedId = ingredientDao.insertIngredient(newItem)
                if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
            }
        }

        val toDelete = localIngredients.filter { localItem ->
            if (localItem.id in dirtyIds) return@filter false
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            ingredientDao.deleteIngredientsByIds(toDelete.map { it.id })
        }

        remoteItems.size
    }

    /**
     * Reconciles Firestore customers documents into Room.
     */
    suspend fun syncCustomersWithFirestore(bakeryId: String, remoteItems: List<CustomerEntity>): Int = database.withTransaction {
        val localItems = customerDao.getCustomersByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }

        val remoteDocIds = mutableSetOf<String>()

        val pending = pendingCloudChanges.list(bakeryId).filter { it.collection == "customers" }
        val dirtyIds = pending.map { it.localId }.toSet()
        val dirtyDocuments = pending.map { it.path.substringAfterLast('/') }.toSet()
        for (remote in remoteItems) {
            if (remote.firestoreId in dirtyDocuments) continue
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)

            if (existing?.id in dirtyIds) continue
            if (existing != null) {
                if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id,
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    customerDao.updateCustomer(updated)
                }
            } else {
                val newLocalId = 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    bakeryId = bakeryId
                )
                val insertedId = customerDao.insertCustomer(newItem)
                if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
            }
        }

        val toDelete = localItems.filter { localItem ->
            if (localItem.id in dirtyIds) return@filter false
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            customerDao.deleteCustomersByIds(toDelete.map { it.id })
        }

        remoteItems.size
    }

    /**
     * Reconciles Firestore products documents into Room.
     */
    suspend fun syncProductsWithFirestore(bakeryId: String, remoteItems: List<ProductServiceEntity>): Int = database.withTransaction {
        val localItems = productServiceDao.getProductsByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }

        val remoteDocIds = mutableSetOf<String>()

        val pending = pendingCloudChanges.list(bakeryId).filter { it.collection == "products" }
        val dirtyIds = pending.map { it.localId }.toSet()
        val dirtyDocuments = pending.map { it.path.substringAfterLast('/') }.toSet()
        for (remote in remoteItems) {
            if (remote.firestoreId in dirtyDocuments) continue
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)

            if (existing?.id in dirtyIds) continue
            if (existing != null) {
                if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id,
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    productServiceDao.updateProduct(updated)
                }
            } else {
                val newLocalId = 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    bakeryId = bakeryId
                )
                val insertedId = productServiceDao.insertProduct(newItem)
                if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
            }
        }

        val toDelete = localItems.filter { localItem ->
            if (localItem.id in dirtyIds) return@filter false
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            productServiceDao.deleteProductsByIds(toDelete.map { it.id })
        }

        remoteItems.size
    }

    /**
     * Reconciles Firestore invoices documents into Room.
     */
    suspend fun syncInvoicesWithFirestore(bakeryId: String, remoteItems: List<InvoiceEntity>): Int = database.withTransaction {
        val localItems = invoiceDao.getInvoicesByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }

        val remoteDocIds = mutableSetOf<String>()

        val pending = pendingCloudChanges.list(bakeryId).filter { it.collection == "invoices" }
        val dirtyIds = pending.map { it.localId }.toSet()
        val dirtyDocuments = pending.map { it.path.substringAfterLast('/') }.toSet()
        for (remote in remoteItems) {
            if (remote.firestoreId in dirtyDocuments) continue
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)

            if (existing?.id in dirtyIds) continue
            if (existing != null) {
                if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id,
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    invoiceDao.updateInvoice(updated)
                    documentLineItemDao.deleteLineItems("INVOICE", updated.id)
                    documentLineItemDao.insertLineItems(updated.items.map { it.toDocumentRow("INVOICE", updated.id, updated.userId) })
                }
            } else {
                val newLocalId = 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    bakeryId = bakeryId
                )
                val insertedId = invoiceDao.insertInvoice(newItem)
                documentLineItemDao.insertLineItems(newItem.items.map { it.toDocumentRow("INVOICE", insertedId, newItem.userId) })
                if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
            }
        }

        val toDelete = localItems.filter { localItem ->
            if (localItem.id in dirtyIds) return@filter false
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            invoiceDao.deleteInvoicesByIds(toDelete.map { it.id })
        }

        remoteItems.size
    }

    /**
     * Reconciles Firestore quotes documents into Room.
     */
    suspend fun syncQuotesWithFirestore(bakeryId: String, remoteItems: List<QuoteEntity>): Int = database.withTransaction {
        val localItems = quoteDao.getQuotesByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }

        val remoteDocIds = mutableSetOf<String>()

        val pending = pendingCloudChanges.list(bakeryId).filter { it.collection == "quotes" }
        val dirtyIds = pending.map { it.localId }.toSet()
        val dirtyDocuments = pending.map { it.path.substringAfterLast('/') }.toSet()
        for (remote in remoteItems) {
            if (remote.firestoreId in dirtyDocuments) continue
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)

            if (existing?.id in dirtyIds) continue
            if (existing != null) {
                if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id,
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    quoteDao.updateQuote(updated)
                    documentLineItemDao.deleteLineItems(updated.docType.uppercase(), updated.id)
                    documentLineItemDao.insertLineItems(updated.items.map { it.toDocumentRow(updated.docType.uppercase(), updated.id, updated.userId) })
                }
            } else {
                val newLocalId = 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    bakeryId = bakeryId
                )
                val insertedId = quoteDao.insertQuote(newItem)
                documentLineItemDao.insertLineItems(newItem.items.map { it.toDocumentRow(newItem.docType.uppercase(), insertedId, newItem.userId) })
                if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
            }
        }

        val toDelete = localItems.filter { localItem ->
            if (localItem.id in dirtyIds) return@filter false
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            quoteDao.deleteQuotesByIds(toDelete.map { it.id })
        }

        remoteItems.size
    }

    /**
     * Reconciles Firestore orders documents into Room.
     */
    suspend fun syncOrdersWithFirestore(bakeryId: String, remoteItems: List<OrderEntity>): Int {
        val localItems = orderDao.getOrdersByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }
        val localById = localItems.associateBy { it.id }
        val localByNumber = localItems.associateBy { it.orderNumber.trim() }

        val remoteDocIds = mutableSetOf<String>()

        for (remote in remoteItems) {
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)
                ?: (if (remote.id > 0) localById[remote.id] else null)
                ?: (if (remote.orderNumber.isNotBlank()) localByNumber[remote.orderNumber.trim()] else null)

            if (existing != null) {
                if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id,
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    orderDao.updateOrder(updated)
                }
            } else {
                val newLocalId = if (remote.id > 0 && !localById.containsKey(remote.id)) remote.id else 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    bakeryId = bakeryId
                )
                val insertedId = orderDao.insertOrder(newItem)
                if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
            }
        }

        val toDelete = localItems.filter { localItem ->
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            orderDao.deleteOrdersByIds(toDelete.map { it.id })
        }

        return remoteItems.size
    }

    /**
     * Reconciles Firestore tasks documents into Room.
     */
    suspend fun syncTasksWithFirestore(bakeryId: String, remoteItems: List<TaskEntity>): Int {
        val localItems = taskDao.getTasksByBakeryOnce(bakeryId)
        val localByFirestoreId = localItems.filter { it.firestoreId.isNotBlank() }.associateBy { it.firestoreId }
        val localById = localItems.associateBy { it.id }
        val localByRef = localItems.filter { it.orderRef.isNotBlank() }.associateBy { it.orderRef.trim() }

        val remoteDocIds = mutableSetOf<String>()

        for (remote in remoteItems) {
            val docId = remote.firestoreId.ifBlank { if (remote.id > 0) remote.id.toString() else "" }
            if (docId.isNotBlank()) remoteDocIds.add(docId)

            val existing = (if (docId.isNotBlank()) localByFirestoreId[docId] else null)
                ?: (if (remote.id > 0) localById[remote.id] else null)
                ?: (if (remote.orderRef.isNotBlank()) localByRef[remote.orderRef.trim()] else null)

            if (existing != null) {
                if (docId.isNotBlank()) remoteDocIds.add(existing.firestoreId.ifBlank { existing.id.toString() })
                if (remote.updatedAt >= existing.updatedAt) {
                    val updated = remote.copy(
                        id = existing.id,
                        firestoreId = if (docId.isNotBlank()) docId else existing.firestoreId,
                        userId = if (remote.userId != 0L) remote.userId else existing.userId,
                        bakeryId = bakeryId
                    )
                    taskDao.updateTask(updated)
                }
            } else {
                val newLocalId = if (remote.id > 0 && !localById.containsKey(remote.id)) remote.id else 0L
                val newItem = remote.copy(
                    id = newLocalId,
                    firestoreId = docId,
                    bakeryId = bakeryId
                )
                val insertedId = taskDao.insertTask(newItem)
                if (docId.isBlank()) remoteDocIds.add(insertedId.toString())
            }
        }

        val toDelete = localItems.filter { localItem ->
            localItem.firestoreId.isNotBlank() && !remoteDocIds.contains(localItem.firestoreId)
        }
        if (toDelete.isNotEmpty()) {
            taskDao.deleteTasksByIds(toDelete.map { it.id })
        }

        return remoteItems.size
    }

    /**
     * Invoice Automation:
     * Creating an invoice creates/connects the appropriate order and task entry.
     */
    suspend fun recordInvoiceCreated(invoice: InvoiceEntity, uid: Long): Long {
        val invId = invoiceDao.insertInvoice(invoice)
        val bId = invoice.bakeryId
        val invNumber = invoice.invoiceNumber
        if (invNumber.isNotBlank()) {
            val existingOrder = orderDao.getOrderByOrderNumber(bId, invNumber)
            if (existingOrder == null) {
                val order = OrderEntity(
                    userId = uid,
                    bakeryId = bId,
                    firestoreId = "order_$invNumber",
                    orderNumber = invNumber,
                    customerName = invoice.clientName,
                    customerPhone = invoice.clientPhone,
                    description = invoice.orderDescription,
                    dueDate = invoice.dueDate,
                    totalAmount = invoice.amount,
                    totalCost = invoice.totalCost,
                    status = if (invoice.status.equals("Paid", ignoreCase = true)) "Completed" else "Pending"
                )
                orderDao.insertOrder(order)
            }
            val existingTask = taskDao.getTaskByOrderRef(bId, invNumber)
            if (existingTask == null) {
                val task = TaskEntity(
                    userId = uid,
                    bakeryId = bId,
                    firestoreId = "task_$invNumber",
                    title = "Order: ${invoice.clientName} ($invNumber)",
                    orderRef = invNumber,
                    dueTime = "09:00",
                    priority = "High",
                    status = if (invoice.status.equals("Paid", ignoreCase = true)) "Completed" else "Pending",
                    dueDate = invoice.dueDate
                )
                taskDao.insertTask(task)
            }
        }
        return invId
    }

    /**
     * Invoice Automation:
     * Marking an invoice as paid updates its order and task without duplicates.
     */
    suspend fun recordInvoicePaid(invoiceId: Long) {
        val invoice = invoiceDao.getInvoiceByIdOnce(invoiceId) ?: return
        invoiceDao.updateInvoiceStatus(invoiceId, "Paid", System.currentTimeMillis())
        val invNumber = invoice.invoiceNumber
        val bId = invoice.bakeryId
        if (invNumber.isNotBlank()) {
            val existingOrder = orderDao.getOrderByOrderNumber(bId, invNumber)
            if (existingOrder != null && existingOrder.status != "Completed") {
                orderDao.updateOrderStatus(existingOrder.id, "Completed", System.currentTimeMillis())
            }
            val existingTask = taskDao.getTaskByOrderRef(bId, invNumber)
            if (existingTask != null && existingTask.status != "Completed") {
                taskDao.updateTaskStatus(existingTask.id, "Completed", System.currentTimeMillis())
            }
        }
    }

    /**
     * Clears cached records for a specific bakery so switching accounts never displays another bakery's data.
     */
    suspend fun clearBakeryData(bakeryId: String) {
        if (bakeryId.isBlank()) return
        packagingDao.deletePackagingByBakery(bakeryId)
        inventoryDao.deleteInventoryByBakery(bakeryId)
        recipeDao.deleteRecipesByBakery(bakeryId)
        ingredientDao.deleteIngredientsByBakery(bakeryId)
        customerDao.deleteCustomersByBakery(bakeryId)
        productServiceDao.deleteProductsByBakery(bakeryId)
        invoiceDao.deleteInvoicesByBakery(bakeryId)
        quoteDao.deleteQuotesByBakery(bakeryId)
        orderDao.deleteOrdersByBakery(bakeryId)
        taskDao.deleteTasksByBakery(bakeryId)
    }

    // Suppliers
    val allSuppliers: Flow<List<SupplierEntity>> = supplierDao.getAllSuppliers()
    fun getSuppliersByUser(userId: Long): Flow<List<SupplierEntity>> = supplierDao.getSuppliersByUser(userId)

    fun getSupplierById(id: Long): Flow<SupplierEntity?> = supplierDao.getSupplierById(id)

    suspend fun toggleFavoriteSupplier(id: Long) = supplierDao.toggleFavorite(id)

    suspend fun insertSupplier(supplier: SupplierEntity): Long = supplierDao.insertSupplier(supplier)

    suspend fun updateSupplier(supplier: SupplierEntity) = supplierDao.updateSupplier(supplier)

    suspend fun deleteSupplier(id: Long) = supplierDao.deleteSupplier(id)

    // Specials
    val allSpecials: Flow<List<SpecialDealEntity>> = specialDealDao.getAllSpecials()

    fun getSpecialsForSupplier(supplierId: Long): Flow<List<SpecialDealEntity>> =
        specialDealDao.getSpecialsForSupplier(supplierId)

    fun getSpecialById(id: Long): Flow<SpecialDealEntity?> = specialDealDao.getSpecialById(id)

    // Notifications
    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()
    val unreadNotificationsCount: Flow<Int> = notificationDao.getUnreadCount()
    fun getNotificationsByUser(userId: Long): Flow<List<NotificationEntity>> = notificationDao.getNotificationsByUser(userId)
    fun getUnreadNotificationsCountByUser(userId: Long): Flow<Int> = notificationDao.getUnreadCountByUser(userId)

    suspend fun markAllNotificationsAsRead() = notificationDao.markAllAsRead()
    suspend fun markAllNotificationsAsReadByUser(userId: Long) = notificationDao.markAllAsReadByUser(userId)
    suspend fun markNotificationAsRead(id: Long) = notificationDao.markAsRead(id)

    suspend fun insertNotification(notification: NotificationEntity) = notificationDao.insertNotification(notification)

    // Invoices
    val allInvoices: Flow<List<InvoiceEntity>> = invoiceDao.getAllInvoices()
    fun getInvoicesByUser(userId: Long): Flow<List<InvoiceEntity>> = invoiceDao.getInvoicesByUser(userId)
    fun getInvoicesByBakery(bakeryId: String): Flow<List<InvoiceEntity>> = invoiceDao.getInvoicesByBakery(bakeryId)
    suspend fun getInvoicesByBakeryOnce(bakeryId: String): List<InvoiceEntity> = invoiceDao.getInvoicesByBakeryOnce(bakeryId)
    suspend fun getInvoiceByFirestoreId(firestoreId: String): InvoiceEntity? = invoiceDao.getInvoiceByFirestoreId(firestoreId)
    suspend fun getInvoiceByIdOnce(id: Long): InvoiceEntity? = invoiceDao.getInvoiceByIdOnce(id)

    suspend fun insertInvoice(invoice: InvoiceEntity): Long = database.withTransaction {
        val old = if (invoice.id > 0) invoiceDao.getInvoiceByIdOnce(invoice.id) else null
        val bId = workspace(invoice.userId, invoice.bakeryId)
        require(old == null || old.bakeryId == bId || old.bakeryId.isBlank() && old.userId == invoice.userId && userAccountDao.getUserByIdOnce(invoice.userId)?.bakeryId == bId) { "Cannot replace another bakery's record." }
        val record = invoice.copy(bakeryId = bId, firestoreId = invoice.firestoreId.ifBlank { old?.firestoreId.orEmpty().ifBlank { if (old != null) old.id.toString() else UUID.randomUUID().toString() } })
        val id = invoiceDao.insertInvoice(record)
        documentLineItemDao.deleteLineItems("INVOICE", id)
        documentLineItemDao.insertLineItems(record.items.map { it.toDocumentRow("INVOICE", id, record.userId) })
        queue(bId, "invoices", id, record.firestoreId, old?.takeIf { it.firestoreId.isNotBlank() }?.updatedAt)
        id
    }

    suspend fun updateInvoice(invoice: InvoiceEntity) { insertInvoice(invoice) }

    suspend fun updateInvoiceStatus(id: Long, status: String) = database.withTransaction {
        val old = invoiceDao.getInvoiceByIdOnce(id) ?: return@withTransaction
        updateInvoice(old.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteInvoice(id: Long) = database.withTransaction {
        val old = invoiceDao.getInvoiceByIdOnce(id) ?: return@withTransaction
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "invoices", id, old.firestoreId, old.updatedAt, deleted = true)
        invoiceDao.deleteInvoice(id)
        documentLineItemDao.deleteLineItems("INVOICE", id)
    }

    // Quotes
    val allQuotes: Flow<List<QuoteEntity>> = quoteDao.getAllQuotes()
    fun getQuotesByUser(userId: Long): Flow<List<QuoteEntity>> = quoteDao.getQuotesByUser(userId)
    fun getQuotesByBakery(bakeryId: String): Flow<List<QuoteEntity>> = quoteDao.getQuotesByBakery(bakeryId)
    suspend fun getQuotesByBakeryOnce(bakeryId: String): List<QuoteEntity> = quoteDao.getQuotesByBakeryOnce(bakeryId)
    suspend fun getQuoteByFirestoreId(firestoreId: String): QuoteEntity? = quoteDao.getQuoteByFirestoreId(firestoreId)
    suspend fun getQuoteByIdOnce(id: Long): QuoteEntity? = quoteDao.getQuoteByIdOnce(id)

    suspend fun insertQuote(quote: QuoteEntity): Long = database.withTransaction {
        val old = if (quote.id > 0) quoteDao.getQuoteByIdOnce(quote.id) else null
        val bId = workspace(quote.userId, quote.bakeryId)
        require(old == null || old.bakeryId == bId || old.bakeryId.isBlank() && old.userId == quote.userId && userAccountDao.getUserByIdOnce(quote.userId)?.bakeryId == bId) { "Cannot replace another bakery's record." }
        val record = quote.copy(bakeryId = bId, firestoreId = quote.firestoreId.ifBlank { old?.firestoreId.orEmpty().ifBlank { if (old != null) old.id.toString() else UUID.randomUUID().toString() } })
        val id = quoteDao.insertQuote(record)
        documentLineItemDao.deleteLineItems("QUOTE", id)
        documentLineItemDao.deleteLineItems("ESTIMATE", id)
        documentLineItemDao.insertLineItems(record.items.map { it.toDocumentRow(record.docType.uppercase(), id, record.userId) })
        queue(bId, "quotes", id, record.firestoreId, old?.takeIf { it.firestoreId.isNotBlank() }?.updatedAt)
        id
    }

    suspend fun updateQuote(quote: QuoteEntity) { insertQuote(quote) }

    suspend fun updateQuoteStatus(id: Long, status: String) = database.withTransaction {
        val old = quoteDao.getQuoteByIdOnce(id) ?: return@withTransaction
        updateQuote(old.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteQuote(id: Long) = database.withTransaction {
        val old = quoteDao.getQuoteByIdOnce(id) ?: return@withTransaction
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "quotes", id, old.firestoreId, old.updatedAt, deleted = true)
        quoteDao.deleteQuote(id)
        documentLineItemDao.deleteLineItems("QUOTE", id)
        documentLineItemDao.deleteLineItems("ESTIMATE", id)
    }

    // Customers
    fun getCustomersByUser(userId: Long): Flow<List<CustomerEntity>> = customerDao.getCustomersByUser(userId)
    fun getCustomersByBakery(bakeryId: String): Flow<List<CustomerEntity>> = customerDao.getCustomersByBakery(bakeryId)
    suspend fun getCustomersByBakeryOnce(bakeryId: String): List<CustomerEntity> = customerDao.getCustomersByBakeryOnce(bakeryId)
    suspend fun getCustomerByFirestoreId(firestoreId: String): CustomerEntity? = customerDao.getCustomerByFirestoreId(firestoreId)
    fun getCustomerCountByUser(userId: Long): Flow<Int> = customerDao.getCustomerCountByUser(userId)
    suspend fun insertCustomer(customer: CustomerEntity): Long = database.withTransaction {
        val old = if (customer.id > 0) customerDao.getCustomerByIdOnce(customer.id) else null
        val bId = workspace(customer.userId, customer.bakeryId)
        require(old == null || old.bakeryId == bId || old.bakeryId.isBlank() && old.userId == customer.userId && userAccountDao.getUserByIdOnce(customer.userId)?.bakeryId == bId) { "Cannot replace another bakery's record." }
        val record = customer.copy(bakeryId = bId, firestoreId = customer.firestoreId.ifBlank { old?.firestoreId.orEmpty().ifBlank { if (old != null) old.id.toString() else UUID.randomUUID().toString() } })
        val id = customerDao.insertCustomer(record)
        queue(bId, "customers", id, record.firestoreId, old?.takeIf { it.firestoreId.isNotBlank() }?.updatedAt)
        id
    }
    suspend fun updateCustomer(customer: CustomerEntity) { insertCustomer(customer) }
    suspend fun deleteCustomer(id: Long) = database.withTransaction {
        val old = customerDao.getCustomerByIdOnce(id) ?: return@withTransaction
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "customers", id, old.firestoreId, old.updatedAt, deleted = true)
        customerDao.deleteCustomer(id)
    }

    // Orders
    val allOrders: Flow<List<OrderEntity>> = orderDao.getOrdersByBakery("")
    fun getOrdersByUser(userId: Long): Flow<List<OrderEntity>> = orderDao.getOrdersByUser(userId)
    fun getOrdersByBakery(bakeryId: String): Flow<List<OrderEntity>> = orderDao.getOrdersByBakery(bakeryId)
    suspend fun getOrdersByBakeryOnce(bakeryId: String): List<OrderEntity> = orderDao.getOrdersByBakeryOnce(bakeryId)
    suspend fun getOrderByFirestoreId(firestoreId: String): OrderEntity? = orderDao.getOrderByFirestoreId(firestoreId)
    suspend fun getOrderByOrderNumber(bakeryId: String, orderNumber: String): OrderEntity? = orderDao.getOrderByOrderNumber(bakeryId, orderNumber)
    fun getOrderCountByUser(userId: Long): Flow<Int> = orderDao.getOrderCountByUser(userId)
    suspend fun insertOrder(order: OrderEntity): Long = orderDao.insertOrder(order)
    suspend fun updateOrderStatus(id: Long, status: String) = orderDao.updateOrderStatus(id, status)
    suspend fun deleteOrder(id: Long) = orderDao.deleteOrder(id)

    // User Profile & Subscription
    val userProfile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()
    fun getUserProfileByUser(userId: Long): Flow<UserProfileEntity?> = userProfileDao.getUserProfileByUser(userId)

    suspend fun saveUserProfile(profile: UserProfileEntity) = userProfileDao.insertOrUpdateProfile(profile)

    suspend fun updateSubscriptionStatus(isPremium: Boolean, plan: String) =
        userProfileDao.updatePremiumStatus(isPremium, plan)

    // Baking Supply Stores
    val allBakingSupplyStores: Flow<List<BakingSupplyStoreEntity>> = bakingSupplyStoreDao.getAllStores()

    fun getBakingSupplyStoreById(id: Long): Flow<BakingSupplyStoreEntity?> =
        bakingSupplyStoreDao.getStoreById(id)

    suspend fun insertBakingSupplyStore(store: BakingSupplyStoreEntity): Long =
        bakingSupplyStoreDao.insertStore(store)

    suspend fun toggleFavoriteStore(id: Long) =
        bakingSupplyStoreDao.toggleFavorite(id)

    suspend fun deleteBakingSupplyStore(id: Long) =
        bakingSupplyStoreDao.deleteStore(id)

    // Products & Services
    fun getProductsByUser(userId: Long): Flow<List<ProductServiceEntity>> =
        productServiceDao.getProductsByUser(userId)

    fun getProductsByBakery(bakeryId: String): Flow<List<ProductServiceEntity>> =
        productServiceDao.getProductsByBakery(bakeryId)

    fun getActiveProductsByUser(userId: Long): Flow<List<ProductServiceEntity>> =
        productServiceDao.getActiveProductsByUser(userId)

    fun getActiveProductsByBakery(bakeryId: String): Flow<List<ProductServiceEntity>> =
        productServiceDao.getActiveProductsByBakery(bakeryId)

    suspend fun getProductsByBakeryOnce(bakeryId: String): List<ProductServiceEntity> =
        productServiceDao.getProductsByBakeryOnce(bakeryId)

    suspend fun getProductByFirestoreId(firestoreId: String): ProductServiceEntity? =
        productServiceDao.getProductByFirestoreId(firestoreId)

    fun getProductById(id: Long): Flow<ProductServiceEntity?> =
        productServiceDao.getProductById(id)

    suspend fun getProductByIdOnce(id: Long): ProductServiceEntity? =
        productServiceDao.getProductByIdOnce(id)

    fun searchProducts(userId: Long, query: String): Flow<List<ProductServiceEntity>> =
        productServiceDao.searchProducts(userId, query)

    suspend fun insertProduct(product: ProductServiceEntity): Long = database.withTransaction {
        val old = if (product.id > 0) productServiceDao.getProductByIdOnce(product.id) else null
        val bId = workspace(product.userId, product.bakeryId)
        require(old == null || old.bakeryId == bId || old.bakeryId.isBlank() && old.userId == product.userId && userAccountDao.getUserByIdOnce(product.userId)?.bakeryId == bId) { "Cannot replace another bakery's record." }
        val record = product.copy(bakeryId = bId, firestoreId = product.firestoreId.ifBlank { old?.firestoreId.orEmpty().ifBlank { if (old != null) old.id.toString() else UUID.randomUUID().toString() } })
        val id = productServiceDao.insertProduct(record)
        queue(bId, "products", id, record.firestoreId, old?.takeIf { it.firestoreId.isNotBlank() }?.updatedAt)
        id
    }

    suspend fun updateProduct(product: ProductServiceEntity) { insertProduct(product) }

    suspend fun deleteProduct(id: Long) = database.withTransaction {
        val old = productServiceDao.getProductByIdOnce(id) ?: return@withTransaction
        if (old.firestoreId.isNotBlank()) queue(old.bakeryId, "products", id, old.firestoreId, old.updatedAt, deleted = true)
        productServiceDao.deleteProduct(id)
    }

    // Product Price History
    fun getPriceHistory(productId: Long): Flow<List<ProductPriceHistoryEntity>> =
        productPriceHistoryDao.getPriceHistory(productId)

    suspend fun insertPriceHistory(history: ProductPriceHistoryEntity): Long =
        productPriceHistoryDao.insertPriceHistory(history)

    // Document Line Items
    fun getLineItems(docType: String, docId: Long): Flow<List<DocumentLineItemEntity>> =
        documentLineItemDao.getLineItems(docType, docId)

    suspend fun insertLineItems(items: List<DocumentLineItemEntity>) =
        documentLineItemDao.insertLineItems(items)

    suspend fun deleteLineItems(docType: String, docId: Long) =
        documentLineItemDao.deleteLineItems(docType, docId)

    // User Login Logs & Activity Auditing
    val allLoginLogs: Flow<List<UserLoginLogEntity>> = userLoginLogDao.getAllLogs()
    fun getLoginLogsForUser(userId: Long): Flow<List<UserLoginLogEntity>> = userLoginLogDao.getLogsForUser(userId)

    suspend fun recordLoginLog(
        userId: Long,
        email: String,
        bakeryName: String,
        action: String,
        branch: String = "Main Flagship",
        notes: String = ""
    ): Long {
        return userLoginLogDao.insertLog(
            UserLoginLogEntity(
                userId = userId,
                email = email,
                bakeryName = bakeryName,
                action = action,
                branch = branch,
                notes = notes
            )
        )
    }

    suspend fun clearAllLoginLogs() = userLoginLogDao.clearAllLogs()

    // Data Deletion Requests
    val allDeletionRequests: Flow<List<DataDeletionRequestEntity>> = dataDeletionRequestDao.getAllRequests()
    val pendingDeletionRequests: Flow<List<DataDeletionRequestEntity>> = dataDeletionRequestDao.getPendingRequests()

    suspend fun recordDeletionRequest(
        userId: Long,
        email: String,
        bakeryName: String,
        reason: String
    ): Long {
        return dataDeletionRequestDao.insertRequest(
            DataDeletionRequestEntity(
                userId = userId,
                email = email,
                bakeryName = bakeryName,
                reason = reason
            )
        )
    }

    suspend fun updateDeletionRequestStatus(id: Long, status: String) {
        val completedAt = if (status == "COMPLETED") System.currentTimeMillis() else null
        dataDeletionRequestDao.updateStatus(id, status, completedAt)
    }

    suspend fun deleteDeletionRequest(id: Long) = dataDeletionRequestDao.deleteRequest(id)

    // Comprehensive User Data Deletion (Google Play & Backend)
    suspend fun deleteUserAndAllData(userId: Long) {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM customers WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM orders WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM recipes WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM tasks WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM inventory_items WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM invoices WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM quotes WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM products_services WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM notifications WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM user_profiles WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM user_login_logs WHERE userId = ?", arrayOf(userId))
            db.execSQL("DELETE FROM user_accounts WHERE id = ?", arrayOf(userId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Granular Purges
    suspend fun purgeAllInvoices(userId: Long) {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM invoices WHERE userId = ?", arrayOf(userId))
    }

    suspend fun purgeAllQuotes(userId: Long) {
        val db = database.openHelper.writableDatabase
        db.execSQL("DELETE FROM quotes WHERE userId = ?", arrayOf(userId))
    }

    // Complete Database Factory Reset
    suspend fun factoryResetDatabase() {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM customers")
            db.execSQL("DELETE FROM orders")
            db.execSQL("DELETE FROM recipe_ingredients")
            db.execSQL("DELETE FROM recipes")
            db.execSQL("DELETE FROM tasks")
            db.execSQL("DELETE FROM inventory_items")
            db.execSQL("DELETE FROM invoices")
            db.execSQL("DELETE FROM quotes")
            db.execSQL("DELETE FROM products_services")
            db.execSQL("DELETE FROM product_price_history")
            db.execSQL("DELETE FROM document_line_items")
            db.execSQL("DELETE FROM notifications")
            db.execSQL("DELETE FROM user_profiles")
            db.execSQL("DELETE FROM user_login_logs")
            db.execSQL("DELETE FROM data_deletion_requests")
            db.execSQL("DELETE FROM user_accounts")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
