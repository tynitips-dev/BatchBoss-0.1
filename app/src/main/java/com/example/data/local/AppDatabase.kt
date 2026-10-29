package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserAccountEntity::class,
        CustomerEntity::class,
        OrderEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        TaskEntity::class,
        InventoryItemEntity::class,
        SupplierEntity::class,
        SpecialDealEntity::class,
        NotificationEntity::class,
        InvoiceEntity::class,
        QuoteEntity::class,
        UserProfileEntity::class,
        BakingSupplyStoreEntity::class,
        ProductServiceEntity::class,
        ProductPriceHistoryEntity::class,
        DocumentLineItemEntity::class,
        UserLoginLogEntity::class,
        DataDeletionRequestEntity::class,
        PackagingItemEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userAccountDao(): UserAccountDao
    abstract fun customerDao(): CustomerDao
    abstract fun orderDao(): OrderDao
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao
    abstract fun taskDao(): TaskDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun packagingDao(): PackagingDao
    abstract fun supplierDao(): SupplierDao
    abstract fun specialDealDao(): SpecialDealDao
    abstract fun notificationDao(): NotificationDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun quoteDao(): QuoteDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bakingSupplyStoreDao(): BakingSupplyStoreDao
    abstract fun productServiceDao(): ProductServiceDao
    abstract fun productPriceHistoryDao(): ProductPriceHistoryDao
    abstract fun documentLineItemDao(): DocumentLineItemDao
    abstract fun userLoginLogDao(): UserLoginLogDao
    abstract fun dataDeletionRequestDao(): DataDeletionRequestDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, definition: String) {
            val exists = db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) if (cursor.getString(name) == column) found = true
                found
            }
            if (!exists) db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
        }

        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "suppliers", "bakeryId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "suppliers", "cloudDocumentId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "suppliers", "publicListingId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "suppliers", "city", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "suppliers", "province", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "suppliers", "cloudConfirmed", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "suppliers", "pendingSync", "INTEGER NOT NULL DEFAULT 0")
                // Preserve unknown legacy ownership; recovery requires explicit confirmation.
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `packagingTotal` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `packagingItemsJson` TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `packaging_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `bakeryId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `packagePrice` REAL NOT NULL,
                        `packageQuantity` REAL NOT NULL,
                        `gramsPerUnit` REAL NOT NULL,
                        `unitPrice` REAL NOT NULL,
                        `currentStock` REAL NOT NULL,
                        `minStock` REAL NOT NULL,
                        `isLowStock` INTEGER NOT NULL,
                        `alertEnabled` INTEGER NOT NULL,
                        `barcode` TEXT NOT NULL,
                        `supplier` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_packaging_items_userId` ON `packaging_items` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_packaging_items_bakeryId` ON `packaging_items` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_packaging_items_category` ON `packaging_items` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_packaging_items_isLowStock` ON `packaging_items` (`isLowStock`)")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "packaging_items", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_packaging_items_firestoreId` ON `packaging_items` (`firestoreId`)")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_12_13.migrate(db) // Repair the alternate supplier-only schema13 before adding schema14 fields.
                // Customers
                addColumnIfMissing(db, "customers", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "customers", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_bakeryId` ON `customers` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_firestoreId` ON `customers` (`firestoreId`)")

                // Orders
                addColumnIfMissing(db, "orders", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "orders", "bakeryId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "orders", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_bakeryId` ON `orders` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_firestoreId` ON `orders` (`firestoreId`)")

                // Recipes
                addColumnIfMissing(db, "recipes", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "recipes", "createdAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "recipes", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_bakeryId` ON `recipes` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_firestoreId` ON `recipes` (`firestoreId`)")

                // Recipe Ingredients
                addColumnIfMissing(db, "recipe_ingredients", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "recipe_ingredients", "recipeFirestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "recipe_ingredients", "bakeryId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "recipe_ingredients", "createdAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "recipe_ingredients", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_recipeFirestoreId` ON `recipe_ingredients` (`recipeFirestoreId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_firestoreId` ON `recipe_ingredients` (`firestoreId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_bakeryId` ON `recipe_ingredients` (`bakeryId`)")

                // Tasks
                addColumnIfMissing(db, "tasks", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "tasks", "bakeryId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "tasks", "createdAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "tasks", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_bakeryId` ON `tasks` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_firestoreId` ON `tasks` (`firestoreId`)")

                // Inventory Items
                addColumnIfMissing(db, "inventory_items", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "inventory_items", "bakeryId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "inventory_items", "createdAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "inventory_items", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_bakeryId` ON `inventory_items` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_firestoreId` ON `inventory_items` (`firestoreId`)")

                // Products & Services
                addColumnIfMissing(db, "products_services", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "products_services", "bakeryId", "TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_services_bakeryId` ON `products_services` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_services_firestoreId` ON `products_services` (`firestoreId`)")

                // Invoices
                addColumnIfMissing(db, "invoices", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "invoices", "bakeryId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "invoices", "createdAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "invoices", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_bakeryId` ON `invoices` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_firestoreId` ON `invoices` (`firestoreId`)")

                // Quotes
                addColumnIfMissing(db, "quotes", "firestoreId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "quotes", "bakeryId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "quotes", "createdAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "quotes", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quotes_bakeryId` ON `quotes` (`bakeryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quotes_firestoreId` ON `quotes` (`firestoreId`)")
            }
        }

        internal fun builder(context: Context, name: String): RoomDatabase.Builder<AppDatabase> =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = builder(context, "batchboss_database")
                .build()
                INSTANCE = instance
                instance
            }
        }

        // Legacy local locator records remain stored. Public discovery uses approved listings.
    }
}
