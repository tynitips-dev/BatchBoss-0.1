package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow

class AppRepository(private val database: AppDatabase) {
    val userAccountDao = database.userAccountDao()
    val customerDao = database.customerDao()
    val orderDao = database.orderDao()
    val recipeDao = database.recipeDao()
    val ingredientDao = database.recipeIngredientDao()
    val taskDao = database.taskDao()
    val inventoryDao = database.inventoryDao()
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

    fun getRecipeById(id: Long): Flow<RecipeEntity?> = recipeDao.getRecipeById(id)

    suspend fun insertRecipe(recipe: RecipeEntity): Long = recipeDao.insertRecipe(recipe)

    suspend fun updateRecipe(recipe: RecipeEntity) = recipeDao.updateRecipe(recipe)

    suspend fun deleteRecipe(id: Long) {
        ingredientDao.deleteIngredientsForRecipe(id)
        recipeDao.deleteRecipe(id)
    }

    suspend fun toggleFavoriteRecipe(id: Long) = recipeDao.toggleFavorite(id)

    suspend fun updateProfitMargin(id: Long, margin: Double) = recipeDao.updateProfitMargin(id, margin)

    suspend fun updateRecipePricing(
        id: Long,
        labour: Double,
        overheads: Double,
        packaging: Double,
        utilities: Double,
        profitMargin: Double,
        sellingPrice: Double
    ) = recipeDao.updateRecipePricing(id, labour, overheads, packaging, utilities, profitMargin, sellingPrice)

    suspend fun updateRecipePhoto(id: Long, photoUri: String) = recipeDao.updateRecipePhoto(id, photoUri)

    // Ingredients
    fun getIngredientsForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>> =
        ingredientDao.getIngredientsForRecipe(recipeId)

    suspend fun updateIngredientCost(id: Long, cost: Double, quantity: Double) =
        ingredientDao.updateIngredientCost(id, cost, quantity)

    suspend fun saveIngredients(recipeId: Long, ingredients: List<RecipeIngredientEntity>) {
        ingredientDao.deleteIngredientsForRecipe(recipeId)
        ingredientDao.insertIngredients(ingredients)
    }

    suspend fun insertIngredient(ingredient: RecipeIngredientEntity): Long = ingredientDao.insertIngredient(ingredient)

    suspend fun deleteIngredient(id: Long) = ingredientDao.deleteIngredient(id)

    // Tasks
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    fun getTasksByUser(userId: Long): Flow<List<TaskEntity>> = taskDao.getTasksByUser(userId)

    suspend fun insertTask(task: TaskEntity): Long = taskDao.insertTask(task)

    suspend fun updateTaskStatus(id: Long, status: String) = taskDao.updateTaskStatus(id, status)

    suspend fun deleteTask(id: Long) = taskDao.deleteTask(id)

    // Inventory
    val allInventory: Flow<List<InventoryItemEntity>> = inventoryDao.getAllInventory()
    val lowStockItems: Flow<List<InventoryItemEntity>> = inventoryDao.getLowStockItems()
    fun getInventoryByUser(userId: Long): Flow<List<InventoryItemEntity>> = inventoryDao.getInventoryByUser(userId)
    fun getLowStockByUser(userId: Long): Flow<List<InventoryItemEntity>> = inventoryDao.getLowStockByUser(userId)

    suspend fun toggleInventoryAlert(id: Long) = inventoryDao.toggleAlert(id)

    suspend fun updateStockPrice(id: Long, unitPrice: Double, currentStock: Double, minStock: Double) {
        val isLow = currentStock <= minStock
        inventoryDao.updateStockPriceAndQuantity(id, unitPrice, currentStock, minStock, isLow)
    }

    suspend fun updateStockItemFull(id: Long, unitPrice: Double, packagePrice: Double, gramsPerUnit: Double, currentStock: Double, minStock: Double) {
        val isLow = currentStock <= minStock
        inventoryDao.updateStockItemFull(id, unitPrice, packagePrice, gramsPerUnit, currentStock, minStock, isLow)
    }

    suspend fun insertInventoryItem(item: InventoryItemEntity): Long = inventoryDao.insertItem(item)

    suspend fun deleteInventoryItem(id: Long) = inventoryDao.deleteItem(id)

    // Suppliers
    val allSuppliers: Flow<List<SupplierEntity>> = supplierDao.getAllSuppliers()
    fun getSuppliersByUser(userId: Long): Flow<List<SupplierEntity>> = supplierDao.getSuppliersByUser(userId)

    fun getSupplierById(id: Long): Flow<SupplierEntity?> = supplierDao.getSupplierById(id)

    suspend fun toggleFavoriteSupplier(id: Long) = supplierDao.toggleFavorite(id)

    suspend fun insertSupplier(supplier: SupplierEntity): Long = supplierDao.insertSupplier(supplier)

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

    suspend fun insertInvoice(invoice: InvoiceEntity): Long = invoiceDao.insertInvoice(invoice)

    suspend fun updateInvoice(invoice: InvoiceEntity) = invoiceDao.updateInvoice(invoice)

    suspend fun updateInvoiceStatus(id: Long, status: String) = invoiceDao.updateInvoiceStatus(id, status)

    suspend fun deleteInvoice(id: Long) = invoiceDao.deleteInvoice(id)

    // Quotes
    val allQuotes: Flow<List<QuoteEntity>> = quoteDao.getAllQuotes()
    fun getQuotesByUser(userId: Long): Flow<List<QuoteEntity>> = quoteDao.getQuotesByUser(userId)

    suspend fun insertQuote(quote: QuoteEntity): Long = quoteDao.insertQuote(quote)

    suspend fun updateQuote(quote: QuoteEntity) = quoteDao.updateQuote(quote)

    suspend fun updateQuoteStatus(id: Long, status: String) = quoteDao.updateQuoteStatus(id, status)

    suspend fun deleteQuote(id: Long) = quoteDao.deleteQuote(id)

    // Customers
    fun getCustomersByUser(userId: Long): Flow<List<CustomerEntity>> = customerDao.getCustomersByUser(userId)
    fun getCustomerCountByUser(userId: Long): Flow<Int> = customerDao.getCustomerCountByUser(userId)
    suspend fun insertCustomer(customer: CustomerEntity): Long = customerDao.insertCustomer(customer)
    suspend fun updateCustomer(customer: CustomerEntity) = customerDao.updateCustomer(customer)
    suspend fun deleteCustomer(id: Long) = customerDao.deleteCustomer(id)

    // Orders
    fun getOrdersByUser(userId: Long): Flow<List<OrderEntity>> = orderDao.getOrdersByUser(userId)
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

    suspend fun ensureInitialBakingStores() {
        val count = bakingSupplyStoreDao.getCount()
        if (count == 0) {
            bakingSupplyStoreDao.insertStores(AppDatabase.getSeedBakingSupplyStores())
        }
    }

    // Products & Services
    fun getProductsByUser(userId: Long): Flow<List<ProductServiceEntity>> =
        productServiceDao.getProductsByUser(userId)

    fun getActiveProductsByUser(userId: Long): Flow<List<ProductServiceEntity>> =
        productServiceDao.getActiveProductsByUser(userId)

    fun getProductById(id: Long): Flow<ProductServiceEntity?> =
        productServiceDao.getProductById(id)

    suspend fun getProductByIdOnce(id: Long): ProductServiceEntity? =
        productServiceDao.getProductByIdOnce(id)

    fun searchProducts(userId: Long, query: String): Flow<List<ProductServiceEntity>> =
        productServiceDao.searchProducts(userId, query)

    suspend fun insertProduct(product: ProductServiceEntity): Long =
        productServiceDao.insertProduct(product)

    suspend fun updateProduct(product: ProductServiceEntity) =
        productServiceDao.updateProduct(product)

    suspend fun deleteProduct(id: Long) {
        productPriceHistoryDao.deleteHistoryForProduct(id)
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
