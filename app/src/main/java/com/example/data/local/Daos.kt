package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
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

    @Query("UPDATE recipes SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE recipes SET profitMarginPercent = :margin WHERE id = :id")
    suspend fun updateProfitMargin(id: Long, margin: Double)

    @Query("UPDATE recipes SET labourCost = :labour, overheadsCost = :overheads, packagingCost = :packaging, utilitiesCost = :utilities, profitMarginPercent = :profitMargin, customSellingPrice = :sellingPrice WHERE id = :id")
    suspend fun updateRecipePricing(id: Long, labour: Double, overheads: Double, packaging: Double, utilities: Double, profitMargin: Double, sellingPrice: Double)
}

@Dao
interface RecipeIngredientDao {
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    fun getIngredientsForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun getIngredientsForRecipeOnce(recipeId: Long): List<RecipeIngredientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<RecipeIngredientEntity>)

    @Update
    suspend fun updateIngredient(ingredient: RecipeIngredientEntity)

    @Query("UPDATE recipe_ingredients SET cost = :cost, quantity = :quantity WHERE id = :id")
    suspend fun updateIngredientCost(id: Long, cost: Double, quantity: Double)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: RecipeIngredientEntity): Long

    @Query("DELETE FROM recipe_ingredients WHERE id = :id")
    suspend fun deleteIngredient(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, status: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)
}

@Dao
interface InventoryDao {
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
}

@Dao
interface SupplierDao {
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
    @Query("SELECT * FROM notifications ORDER BY id ASC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isUnread = 1")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE notifications SET isUnread = 0")
    suspend fun markAllAsRead()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY id DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Query("UPDATE invoices SET status = :status WHERE id = :id")
    suspend fun updateInvoiceStatus(id: Long, status: String)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoice(id: Long)
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY id DESC")
    fun getAllQuotes(): Flow<List<QuoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: QuoteEntity): Long

    @Update
    suspend fun updateQuote(quote: QuoteEntity)

    @Query("UPDATE quotes SET status = :status WHERE id = :id")
    suspend fun updateQuoteStatus(id: Long, status: String)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuote(id: Long)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET isPremium = :isPremium, subscriptionPlan = :plan WHERE id = 1")
    suspend fun updatePremiumStatus(isPremium: Boolean, plan: String)
}

