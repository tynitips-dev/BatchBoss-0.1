package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
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
        DataDeletionRequestEntity::class
    ],
    version = 11,
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

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "batchboss_database"
                )
                .addMigrations(MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_accounts ADD COLUMN bakeryId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN bakeryId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN bakeryId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN bakeryId TEXT NOT NULL DEFAULT 'bakery_1'")
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val storeDao = database.bakingSupplyStoreDao()
            // Only seed the public baking supply store directory if empty.
            // NO sample recipes, NO sample inventory, NO sample customers,
            // NO sample suppliers, NO sample invoices, NO sample quotes, NO fake profits!
            if (storeDao.getCount() == 0) {
                storeDao.insertStores(getSeedBakingSupplyStores())
            }
        }

        fun getSeedBakingSupplyStores(): List<BakingSupplyStoreEntity> = listOf(
            BakingSupplyStoreEntity(
                name = "The Baking Tin & Cake Emporium",
                category = "Cake Decorating",
                address = "42 Baker Street, Rosebank Central",
                city = "Johannesburg",
                distanceKm = 1.4,
                rating = 4.9,
                reviewCount = 248,
                openingHours = "Open • Closes 17:30",
                phone = "+27 11 447 9120",
                website = "https://thebakingtin.co.za",
                specialties = "Satin Ice Fondant, Callebaut Callets, AmeriColor Gel Colors, Silicone Flower Molds, PME Nozzles, Cake Scrapers & Turners",
                inStockHighlights = "Callebaut 811 54.5% Dark, AmeriColor 12-Pack, 10-inch Heavy Masonite Drums, Tylose Powder",
                lat = -26.1472,
                lng = 28.0413,
                hasDelivery = true,
                hasPickup = true,
                isFavorite = true
            ),
            BakingSupplyStoreEntity(
                name = "Pack & Bake Supply Depot",
                category = "Packaging & Boxes",
                address = "15 Commercial Rd, Strydom Park",
                city = "Randburg",
                distanceKm = 2.8,
                rating = 4.8,
                reviewCount = 175,
                openingHours = "Open • Closes 17:00",
                phone = "+27 11 792 5540",
                website = "https://packandbake.co.za",
                specialties = "Window Cake Boxes (6\", 8\", 10\", 12\"), Corrugated Heavy Cake Drums, Cupcake Inserts (6s & 12s), Macaron Display Boxes, Clear Gusset Bags",
                inStockHighlights = "12-inch Tall Cake Boxes with Window, White Cupcake Boxes x50, Heavy Foil Cake Boards, Satin Ribbons",
                lat = -26.0891,
                lng = 27.9734,
                hasDelivery = true,
                hasPickup = true,
                isFavorite = false
            ),
            BakingSupplyStoreEntity(
                name = "Golden Harvest Artisan Flour Mill",
                category = "Flour & Grains",
                address = "8 Millers Way, Industria West",
                city = "Johannesburg",
                distanceKm = 4.5,
                rating = 4.9,
                reviewCount = 132,
                openingHours = "Open • Closes 16:30",
                phone = "+27 11 839 2110",
                website = "https://goldenharvestmill.co.za",
                specialties = "Stoneground Unbleached Cake Flour, 00 Italian Pizza & Brioche Flour, Whole Wheat Stoneground, Rye Flour, Active Sourdough Cultures, French T55 Flour",
                inStockHighlights = "12.5kg Unbleached Cake Flour Bags, Rye Flour 5kg, Belgian Malted Flour, Organic Polenta",
                lat = -26.1950,
                lng = 27.9850,
                hasDelivery = true,
                hasPickup = true,
                isFavorite = true
            ),
            BakingSupplyStoreEntity(
                name = "Sweet Art Fondant & Sugar Studio",
                category = "Cake Decorating",
                address = "72 Oxford Rd, Parktown North",
                city = "Johannesburg",
                distanceKm = 5.6,
                rating = 4.7,
                reviewCount = 94,
                openingHours = "Open • Closes 17:00",
                phone = "+27 11 646 3390",
                website = "https://sweetartstudio.co.za",
                specialties = "Metallic Edible Lustre Dusts, Crystal Isomalt, Edible Wafer Paper, Chocolate Transfer Sheets, Petal Dust, Sugar Pearl Dragees",
                inStockHighlights = "Egyptian Gold Lustre Dust 10g, Rolkem Super Gold, Flexible Silicone Lace Mats, Airbrush Color Sets",
                lat = -26.1550,
                lng = 28.0320,
                hasDelivery = false,
                hasPickup = true,
                isFavorite = false
            ),
            BakingSupplyStoreEntity(
                name = "ChocCraft & Pastry Pro Wholesalers",
                category = "Dairy & Chocolate",
                address = "104 Enterprise Blvd, Midrand",
                city = "Midrand",
                distanceKm = 7.2,
                rating = 4.9,
                reviewCount = 310,
                openingHours = "Open • Closes 17:30",
                phone = "+27 11 315 8820",
                website = "https://choccraftpro.co.za",
                specialties = "Valrhona & Callebaut Chocolate Blocks & Drops, 82% Unsalted Cultured Butter (25kg bulk), Cocoa Butter Mycryo, Praline Pastes, Pure Vanilla Bean Paste",
                inStockHighlights = "Callebaut W2 White 2.5kg, Valrhona Guanaja 70%, 82% Fat Butter Blocks, 500g Nielsen-Massey Vanilla Paste",
                lat = -26.0120,
                lng = 28.1250,
                hasDelivery = true,
                hasPickup = true,
                isFavorite = true
            ),
            BakingSupplyStoreEntity(
                name = "Bakery Equipment & Pan World",
                category = "Tools & Bakeware",
                address = "23 Machine Street, Booysens",
                city = "Johannesburg South",
                distanceKm = 9.1,
                rating = 4.8,
                reviewCount = 88,
                openingHours = "Open • Closes 16:30",
                phone = "+27 11 493 6700",
                website = "https://bakeryequipmentworld.co.za",
                specialties = "USA Pan Non-Stick Bakeware, KitchenAid Heavy Duty Accessories, Perforated Silpat Baking Mats, Precision Gram Digital Scales, Multi-Tier Cooling Racks",
                inStockHighlights = "8-inch Deep Anodised Cake Pans (3-pack), Silform Tart Molds, Thermapen One Digital Food Thermometer",
                lat = -26.2310,
                lng = 28.0290,
                hasDelivery = true,
                hasPickup = true,
                isFavorite = false
            ),
            BakingSupplyStoreEntity(
                name = "Spice & Essence Boutique",
                category = "Specialty Flavors",
                address = "56 4th Avenue, Linden",
                city = "Johannesburg",
                distanceKm = 11.4,
                rating = 4.8,
                reviewCount = 115,
                openingHours = "Open • Closes 17:00",
                phone = "+27 11 782 1190",
                website = "https://spiceandessence.co.za",
                specialties = "Madagascar Bourbon Vanilla Pods, Almond Baking Emulsion, Freeze-Dried Berry Powders, Culinary Lavender, Natural Citrus Oils, Tonka Beans",
                inStockHighlights = "Grade A Vanilla Pods (10x vacuum pack), LorAnn Bakery Emulsions, Freeze-Dried Raspberry Crumbles",
                lat = -26.1360,
                lng = 27.9940,
                hasDelivery = true,
                hasPickup = true,
                isFavorite = false
            ),
            BakingSupplyStoreEntity(
                name = "Cape Sugar & Yeast Distributors",
                category = "Flour & Grains",
                address = "31 Sacks Circle, Bellville South",
                city = "Cape Town",
                distanceKm = 14.0,
                rating = 4.6,
                reviewCount = 64,
                openingHours = "Open • Closes 16:00",
                phone = "+27 21 951 4400",
                website = "https://capesugaryeast.co.za",
                specialties = "25kg Castor Sugar, Instant Dry Baker's Yeast, Liquid Invert Sugar, Golden Syrup Bulk Tins, Pure Glucose Syrup, Baking Soda Bulk",
                inStockHighlights = "25kg Fine Castor Sugar Sacks, SAF-Instant Red Yeast 500g, Liquid Glucose 5kg Tub",
                lat = -33.9249,
                lng = 18.6341,
                hasDelivery = true,
                hasPickup = true,
                isFavorite = false
            )
        )
    }
}
