package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow

class AppRepository(private val database: AppDatabase) {
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

    // Recipes
    val allRecipes: Flow<List<RecipeEntity>> = recipeDao.getAllRecipes()

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

    suspend fun insertTask(task: TaskEntity): Long = taskDao.insertTask(task)

    suspend fun updateTaskStatus(id: Long, status: String) = taskDao.updateTaskStatus(id, status)

    suspend fun deleteTask(id: Long) = taskDao.deleteTask(id)

    // Inventory
    val allInventory: Flow<List<InventoryItemEntity>> = inventoryDao.getAllInventory()
    val lowStockItems: Flow<List<InventoryItemEntity>> = inventoryDao.getLowStockItems()

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

    fun getSupplierById(id: Long): Flow<SupplierEntity?> = supplierDao.getSupplierById(id)

    suspend fun toggleFavoriteSupplier(id: Long) = supplierDao.toggleFavorite(id)

    suspend fun insertSupplier(supplier: SupplierEntity): Long = supplierDao.insertSupplier(supplier)

    // Specials
    val allSpecials: Flow<List<SpecialDealEntity>> = specialDealDao.getAllSpecials()

    fun getSpecialsForSupplier(supplierId: Long): Flow<List<SpecialDealEntity>> =
        specialDealDao.getSpecialsForSupplier(supplierId)

    fun getSpecialById(id: Long): Flow<SpecialDealEntity?> = specialDealDao.getSpecialById(id)

    // Notifications
    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()
    val unreadNotificationsCount: Flow<Int> = notificationDao.getUnreadCount()

    suspend fun markAllNotificationsAsRead() = notificationDao.markAllAsRead()

    // Invoices
    val allInvoices: Flow<List<InvoiceEntity>> = invoiceDao.getAllInvoices()

    suspend fun insertInvoice(invoice: InvoiceEntity): Long = invoiceDao.insertInvoice(invoice)

    suspend fun updateInvoice(invoice: InvoiceEntity) = invoiceDao.updateInvoice(invoice)

    suspend fun updateInvoiceStatus(id: Long, status: String) = invoiceDao.updateInvoiceStatus(id, status)

    suspend fun deleteInvoice(id: Long) = invoiceDao.deleteInvoice(id)

    // Quotes
    val allQuotes: Flow<List<QuoteEntity>> = quoteDao.getAllQuotes()

    suspend fun insertQuote(quote: QuoteEntity): Long = quoteDao.insertQuote(quote)

    suspend fun updateQuote(quote: QuoteEntity) = quoteDao.updateQuote(quote)

    suspend fun updateQuoteStatus(id: Long, status: String) = quoteDao.updateQuoteStatus(id, status)

    suspend fun deleteQuote(id: Long) = quoteDao.deleteQuote(id)

    // User Profile & Subscription
    val userProfile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()

    suspend fun saveUserProfile(profile: UserProfileEntity) = userProfileDao.insertOrUpdateProfile(profile)

    suspend fun updateSubscriptionStatus(isPremium: Boolean, plan: String) =
        userProfileDao.updatePremiumStatus(isPremium, plan)
}
