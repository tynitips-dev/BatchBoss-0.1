package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts WHERE id = :id")
    fun getUserById(id: Long): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_accounts WHERE id = :id")
    suspend fun getUserByIdOnce(id: Long): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts ORDER BY id ASC")
    fun getAllUsers(): Flow<List<UserAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccountEntity): Long

    @Update
    suspend fun updateUser(user: UserAccountEntity)

    @Query("DELETE FROM user_accounts WHERE id = :id")
    suspend fun deleteUser(id: Long)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE userId = :userId ORDER BY name ASC")
    fun getCustomersByUser(userId: Long): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE bakeryId = :bakeryId ORDER BY name ASC")
    fun getCustomersByBakery(bakeryId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE bakeryId = :bakeryId ORDER BY name ASC")
    suspend fun getCustomersByBakeryOnce(bakeryId: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getCustomerByFirestoreId(firestoreId: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT COUNT(*) FROM customers WHERE userId = :userId")
    fun getCustomerCountByUser(userId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)

    @Query("DELETE FROM customers WHERE id IN (:ids)")
    suspend fun deleteCustomersByIds(ids: List<Long>)

    @Query("DELETE FROM customers WHERE bakeryId = :bakeryId")
    suspend fun deleteCustomersByBakery(bakeryId: String)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY id DESC")
    fun getOrdersByUser(userId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE bakeryId = :bakeryId ORDER BY id DESC")
    fun getOrdersByBakery(bakeryId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE bakeryId = :bakeryId ORDER BY id DESC")
    suspend fun getOrdersByBakeryOnce(bakeryId: String): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getOrderByFirestoreId(firestoreId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE bakeryId = :bakeryId AND orderNumber = :orderNumber LIMIT 1")
    suspend fun getOrderByOrderNumber(bakeryId: String, orderNumber: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: Long): OrderEntity?

    @Query("SELECT COUNT(*) FROM orders WHERE userId = :userId")
    fun getOrderCountByUser(userId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrderStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrder(id: Long)

    @Query("DELETE FROM orders WHERE id IN (:ids)")
    suspend fun deleteOrdersByIds(ids: List<Long>)

    @Query("DELETE FROM orders WHERE bakeryId = :bakeryId")
    suspend fun deleteOrdersByBakery(bakeryId: String)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE userId = :userId ORDER BY id DESC")
    fun getRecipesByUser(userId: Long): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE bakeryId = :bakeryId ORDER BY id DESC")
    fun getRecipesByBakery(bakeryId: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE bakeryId = :bakeryId ORDER BY id DESC")
    suspend fun getRecipesByBakeryOnce(bakeryId: String): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getRecipeByFirestoreId(firestoreId: String): RecipeEntity?

    @Query("SELECT * FROM recipes ORDER BY id ASC")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun getRecipeById(id: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeByIdOnce(id: Long): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: Long)

    @Query("DELETE FROM recipes WHERE id IN (:ids)")
    suspend fun deleteRecipesByIds(ids: List<Long>)

    @Query("DELETE FROM recipes WHERE bakeryId = :bakeryId")
    suspend fun deleteRecipesByBakery(bakeryId: String)

    @Query("UPDATE recipes SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE recipes SET profitMarginPercent = :margin WHERE id = :id")
    suspend fun updateProfitMargin(id: Long, margin: Double)

    @Query("UPDATE recipes SET labourCost = :labour, overheadsCost = :overheads, packagingCost = :packaging, utilitiesCost = :utilities, profitMarginPercent = :profitMargin, customSellingPrice = :sellingPrice WHERE id = :id")
    suspend fun updateRecipePricing(id: Long, labour: Double, overheads: Double, packaging: Double, utilities: Double, profitMargin: Double, sellingPrice: Double)

    @Query("UPDATE recipes SET photoUri = :photoUri WHERE id = :id")
    suspend fun updateRecipePhoto(id: Long, photoUri: String)
}

@Dao
interface RecipeIngredientDao {
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    fun getIngredientsForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun getIngredientsForRecipeOnce(recipeId: Long): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeFirestoreId = :recipeFirestoreId")
    fun getIngredientsForRecipeFirestoreId(recipeFirestoreId: String): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeFirestoreId = :recipeFirestoreId")
    suspend fun getIngredientsForRecipeFirestoreIdOnce(recipeFirestoreId: String): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE bakeryId = :bakeryId")
    suspend fun getIngredientsByBakeryOnce(bakeryId: String): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getIngredientByFirestoreId(firestoreId: String): RecipeIngredientEntity?

    @Query("SELECT * FROM recipe_ingredients WHERE id = :id LIMIT 1")
    suspend fun getIngredientByIdOnce(id: Long): RecipeIngredientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<RecipeIngredientEntity>)

    @Update
    suspend fun updateIngredient(ingredient: RecipeIngredientEntity)

    @Query("UPDATE recipe_ingredients SET cost = :cost, quantity = :quantity, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateIngredientCost(id: Long, cost: Double, quantity: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: Long)

    @Query("DELETE FROM recipe_ingredients WHERE recipeFirestoreId = :recipeFirestoreId")
    suspend fun deleteIngredientsForRecipeFirestoreId(recipeFirestoreId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: RecipeIngredientEntity): Long

    @Query("DELETE FROM recipe_ingredients WHERE id = :id")
    suspend fun deleteIngredient(id: Long)

    @Query("DELETE FROM recipe_ingredients WHERE id IN (:ids)")
    suspend fun deleteIngredientsByIds(ids: List<Long>)

    @Query("DELETE FROM recipe_ingredients WHERE bakeryId = :bakeryId")
    suspend fun deleteIngredientsByBakery(bakeryId: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY id ASC")
    fun getTasksByUser(userId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE bakeryId = :bakeryId ORDER BY id ASC")
    fun getTasksByBakery(bakeryId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE bakeryId = :bakeryId ORDER BY id ASC")
    suspend fun getTasksByBakeryOnce(bakeryId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getTaskByFirestoreId(firestoreId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE bakeryId = :bakeryId AND orderRef = :orderRef LIMIT 1")
    suspend fun getTaskByOrderRef(bakeryId: String, orderRef: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskByIdOnce(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteTasksByIds(ids: List<Long>)

    @Query("DELETE FROM tasks WHERE bakeryId = :bakeryId")
    suspend fun deleteTasksByBakery(bakeryId: String)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE (userId = :userId OR userId = 0) ORDER BY isLowStock DESC, name ASC")
    fun getInventoryByUser(userId: Long): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE bakeryId = :bakeryId ORDER BY isLowStock DESC, name ASC")
    fun getInventoryByBakery(bakeryId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE bakeryId = :bakeryId AND isLowStock = 1 ORDER BY name ASC")
    fun getLowStockByBakery(bakeryId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE bakeryId = :bakeryId ORDER BY isLowStock DESC, name ASC")
    suspend fun getInventoryByBakeryOnce(bakeryId: String): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getInventoryByFirestoreId(firestoreId: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getInventoryByIdOnce(id: Long): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE (userId = :userId OR userId = 0) AND isLowStock = 1")
    fun getLowStockByUser(userId: Long): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items ORDER BY isLowStock DESC, name ASC")
    fun getAllInventory(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE isLowStock = 1")
    fun getLowStockItems(): Flow<List<InventoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItemEntity): Long

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Query("UPDATE inventory_items SET alertEnabled = NOT alertEnabled WHERE id = :id")
    suspend fun toggleAlert(id: Long)

    @Query("UPDATE inventory_items SET unitPrice = :unitPrice, currentStock = :currentStock, minStock = :minStock, isLowStock = :isLowStock WHERE id = :id")
    suspend fun updateStockPriceAndQuantity(id: Long, unitPrice: Double, currentStock: Double, minStock: Double, isLowStock: Boolean)

    @Query("UPDATE inventory_items SET unitPrice = :unitPrice, packagePrice = :packagePrice, gramsPerUnit = :gramsPerUnit, currentStock = :currentStock, minStock = :minStock, isLowStock = :isLowStock WHERE id = :id")
    suspend fun updateStockItemFull(id: Long, unitPrice: Double, packagePrice: Double, gramsPerUnit: Double, currentStock: Double, minStock: Double, isLowStock: Boolean)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM inventory_items WHERE id IN (:ids)")
    suspend fun deleteInventoryByIds(ids: List<Long>)

    @Query("DELETE FROM inventory_items WHERE bakeryId = :bakeryId")
    suspend fun deleteInventoryByBakery(bakeryId: String)
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE userId = :userId ORDER BY id ASC")
    fun getSuppliersByUser(userId: Long): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers ORDER BY id ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    fun getSupplierById(id: Long): Flow<SupplierEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Query("UPDATE suppliers SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteSupplier(id: Long)
}

@Dao
interface SpecialDealDao {
    @Query("SELECT * FROM special_deals ORDER BY id ASC")
    fun getAllSpecials(): Flow<List<SpecialDealEntity>>

    @Query("SELECT * FROM special_deals WHERE supplierId = :supplierId")
    fun getSpecialsForSupplier(supplierId: Long): Flow<List<SpecialDealEntity>>

    @Query("SELECT * FROM special_deals WHERE id = :id")
    fun getSpecialById(id: Long): Flow<SpecialDealEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecials(specials: List<SpecialDealEntity>)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY id DESC")
    fun getNotificationsByUser(userId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY id ASC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isUnread = 1")
    fun getUnreadCountByUser(userId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE isUnread = 1")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE notifications SET isUnread = 0 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notifications SET isUnread = 0 WHERE userId = :userId")
    suspend fun markAllAsReadByUser(userId: Long)

    @Query("UPDATE notifications SET isUnread = 0")
    suspend fun markAllAsRead()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices WHERE userId = :userId ORDER BY id DESC")
    fun getInvoicesByUser(userId: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE bakeryId = :bakeryId ORDER BY id DESC")
    fun getInvoicesByBakery(bakeryId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE bakeryId = :bakeryId ORDER BY id DESC")
    suspend fun getInvoicesByBakeryOnce(bakeryId: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getInvoiceByFirestoreId(firestoreId: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceByIdOnce(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices ORDER BY id DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Query("UPDATE invoices SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateInvoiceStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoice(id: Long)

    @Query("DELETE FROM invoices WHERE id IN (:ids)")
    suspend fun deleteInvoicesByIds(ids: List<Long>)

    @Query("DELETE FROM invoices WHERE bakeryId = :bakeryId")
    suspend fun deleteInvoicesByBakery(bakeryId: String)
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes WHERE userId = :userId ORDER BY id DESC")
    fun getQuotesByUser(userId: Long): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE bakeryId = :bakeryId ORDER BY id DESC")
    fun getQuotesByBakery(bakeryId: String): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE bakeryId = :bakeryId ORDER BY id DESC")
    suspend fun getQuotesByBakeryOnce(bakeryId: String): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getQuoteByFirestoreId(firestoreId: String): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE id = :id LIMIT 1")
    suspend fun getQuoteByIdOnce(id: Long): QuoteEntity?

    @Query("SELECT * FROM quotes ORDER BY id DESC")
    fun getAllQuotes(): Flow<List<QuoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: QuoteEntity): Long

    @Update
    suspend fun updateQuote(quote: QuoteEntity)

    @Query("UPDATE quotes SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateQuoteStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuote(id: Long)

    @Query("DELETE FROM quotes WHERE id IN (:ids)")
    suspend fun deleteQuotesByIds(ids: List<Long>)

    @Query("DELETE FROM quotes WHERE bakeryId = :bakeryId")
    suspend fun deleteQuotesByBakery(bakeryId: String)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun getUserProfileByUser(userId: Long): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET isPremium = :isPremium, subscriptionPlan = :plan WHERE userId = :userId")
    suspend fun updatePremiumStatusByUser(userId: Long, isPremium: Boolean, plan: String)

    @Query("UPDATE user_profiles SET isPremium = :isPremium, subscriptionPlan = :plan WHERE id = 1")
    suspend fun updatePremiumStatus(isPremium: Boolean, plan: String)
}

@Dao
interface ProductServiceDao {
    @Query("SELECT * FROM products_services WHERE userId = :userId ORDER BY name ASC")
    fun getProductsByUser(userId: Long): Flow<List<ProductServiceEntity>>

    @Query("SELECT * FROM products_services WHERE bakeryId = :bakeryId ORDER BY name ASC")
    fun getProductsByBakery(bakeryId: String): Flow<List<ProductServiceEntity>>

    @Query("SELECT * FROM products_services WHERE bakeryId = :bakeryId ORDER BY name ASC")
    suspend fun getProductsByBakeryOnce(bakeryId: String): List<ProductServiceEntity>

    @Query("SELECT * FROM products_services WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getProductByFirestoreId(firestoreId: String): ProductServiceEntity?

    @Query("SELECT * FROM products_services WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
    fun getActiveProductsByUser(userId: Long): Flow<List<ProductServiceEntity>>

    @Query("SELECT * FROM products_services WHERE bakeryId = :bakeryId AND isActive = 1 ORDER BY name ASC")
    fun getActiveProductsByBakery(bakeryId: String): Flow<List<ProductServiceEntity>>

    @Query("SELECT * FROM products_services WHERE id = :id")
    fun getProductById(id: Long): Flow<ProductServiceEntity?>

    @Query("SELECT * FROM products_services WHERE id = :id")
    suspend fun getProductByIdOnce(id: Long): ProductServiceEntity?

    @Query("SELECT * FROM products_services WHERE userId = :userId AND (name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%') ORDER BY name ASC")
    fun searchProducts(userId: Long, query: String): Flow<List<ProductServiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductServiceEntity): Long

    @Update
    suspend fun updateProduct(product: ProductServiceEntity)

    @Query("DELETE FROM products_services WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Query("DELETE FROM products_services WHERE id IN (:ids)")
    suspend fun deleteProductsByIds(ids: List<Long>)

    @Query("DELETE FROM products_services WHERE bakeryId = :bakeryId")
    suspend fun deleteProductsByBakery(bakeryId: String)
}

@Dao
interface ProductPriceHistoryDao {
    @Query("SELECT * FROM product_price_history WHERE productId = :productId ORDER BY timestamp DESC")
    fun getPriceHistory(productId: Long): Flow<List<ProductPriceHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: ProductPriceHistoryEntity): Long

    @Query("DELETE FROM product_price_history WHERE productId = :productId")
    suspend fun deleteHistoryForProduct(productId: Long)
}

@Dao
interface DocumentLineItemDao {
    @Query("SELECT * FROM document_line_items WHERE documentType = :docType AND documentId = :docId ORDER BY id ASC")
    fun getLineItems(docType: String, docId: Long): Flow<List<DocumentLineItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineItems(items: List<DocumentLineItemEntity>)

    @Query("DELETE FROM document_line_items WHERE documentType = :docType AND documentId = :docId")
    suspend fun deleteLineItems(docType: String, docId: Long)
}

@Dao
interface BakingSupplyStoreDao {
    @Query("SELECT * FROM baking_supply_stores ORDER BY distanceKm ASC")
    fun getAllStores(): Flow<List<BakingSupplyStoreEntity>>

    @Query("SELECT * FROM baking_supply_stores WHERE id = :id")
    fun getStoreById(id: Long): Flow<BakingSupplyStoreEntity?>

    @Query("SELECT COUNT(*) FROM baking_supply_stores")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: BakingSupplyStoreEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStores(stores: List<BakingSupplyStoreEntity>)

    @Update
    suspend fun updateStore(store: BakingSupplyStoreEntity)

    @Query("UPDATE baking_supply_stores SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("DELETE FROM baking_supply_stores WHERE id = :id")
    suspend fun deleteStore(id: Long)
}

@Dao
interface UserLoginLogDao {
    @Query("SELECT * FROM user_login_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<UserLoginLogEntity>>

    @Query("SELECT * FROM user_login_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getLogsForUser(userId: Long): Flow<List<UserLoginLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: UserLoginLogEntity): Long

    @Query("DELETE FROM user_login_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM user_login_logs WHERE userId = :userId")
    suspend fun deleteLogsForUser(userId: Long)
}

@Dao
interface DataDeletionRequestDao {
    @Query("SELECT * FROM data_deletion_requests ORDER BY requestedAt DESC")
    fun getAllRequests(): Flow<List<DataDeletionRequestEntity>>

    @Query("SELECT * FROM data_deletion_requests WHERE status = 'PENDING' ORDER BY requestedAt ASC")
    fun getPendingRequests(): Flow<List<DataDeletionRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: DataDeletionRequestEntity): Long

    @Query("UPDATE data_deletion_requests SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, completedAt: Long?)

    @Query("DELETE FROM data_deletion_requests WHERE id = :id")
    suspend fun deleteRequest(id: Long)
}


