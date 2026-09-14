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
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        TaskEntity::class,
        InventoryItemEntity::class,
        SupplierEntity::class,
        SpecialDealEntity::class,
        NotificationEntity::class,
        InvoiceEntity::class,
        QuoteEntity::class,
        UserProfileEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
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
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
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

            // 1. Recipes from Figma designs
            val r1Id = recipeDao.insertRecipe(
                RecipeEntity(
                    name = "Chocolate Cupcakes",
                    category = "Cupcakes",
                    description = "Rich and moist chocolate cupcakes perfect for any occasion.",
                    servings = 16,
                    batchSize = 124,
                    difficulty = "Medium",
                    rating = 4.9,
                    reviewCount = 128,
                    imageResName = "cupcake",
                    labourCost = 15.00,
                    overheadsCost = 50.00,
                    packagingCost = 5.00,
                    utilitiesCost = 3.00,
                    profitMarginPercent = 40.0,
                    isFavorite = true
                )
            )

            val r2Id = recipeDao.insertRecipe(
                RecipeEntity(
                    name = "Carrot Cake",
                    category = "Cakes",
                    description = "Moist and flavorful carrot cake with rich cream cheese frosting.",
                    servings = 12,
                    batchSize = 96,
                    difficulty = "Medium",
                    rating = 4.9,
                    reviewCount = 152,
                    imageResName = "carrot_cake",
                    labourCost = 18.00,
                    overheadsCost = 45.00,
                    packagingCost = 8.00,
                    utilitiesCost = 4.00,
                    profitMarginPercent = 42.0,
                    isFavorite = true
                )
            )

            val r3Id = recipeDao.insertRecipe(
                RecipeEntity(
                    name = "Red Velvet Cake",
                    category = "Cakes",
                    description = "Classic red velvet cake with smooth cream cheese frosting.",
                    servings = 14,
                    batchSize = 112,
                    difficulty = "Medium",
                    rating = 4.8,
                    reviewCount = 98,
                    imageResName = "red_velvet",
                    labourCost = 20.00,
                    overheadsCost = 52.00,
                    packagingCost = 8.00,
                    utilitiesCost = 4.50,
                    profitMarginPercent = 38.0,
                    isFavorite = false
                )
            )

            val r4Id = recipeDao.insertRecipe(
                RecipeEntity(
                    name = "Chocolate Cookies",
                    category = "Cookies",
                    description = "Crispy edges, soft center chocolate cookies. Everyone's favorite!",
                    servings = 24,
                    batchSize = 48,
                    difficulty = "Easy",
                    rating = 4.9,
                    reviewCount = 210,
                    imageResName = "cookies",
                    labourCost = 10.00,
                    overheadsCost = 25.00,
                    packagingCost = 4.00,
                    utilitiesCost = 2.50,
                    profitMarginPercent = 46.0,
                    isFavorite = false
                )
            )

            val r5Id = recipeDao.insertRecipe(
                RecipeEntity(
                    name = "Chocolate Cake",
                    category = "Cakes",
                    description = "Decadent chocolate cake with a rich chocolate frosting.",
                    servings = 14,
                    batchSize = 112,
                    difficulty = "Medium",
                    rating = 4.9,
                    reviewCount = 176,
                    imageResName = "chocolate_cake",
                    labourCost = 22.00,
                    overheadsCost = 55.00,
                    packagingCost = 9.00,
                    utilitiesCost = 5.00,
                    profitMarginPercent = 40.0,
                    isFavorite = false
                )
            )

            val r6Id = recipeDao.insertRecipe(
                RecipeEntity(
                    name = "Vanilla Cake",
                    category = "Cakes",
                    description = "Light and fluffy vanilla cake perfect for any celebration.",
                    servings = 14,
                    batchSize = 112,
                    difficulty = "Easy",
                    rating = 4.8,
                    reviewCount = 134,
                    imageResName = "vanilla_cake",
                    labourCost = 16.00,
                    overheadsCost = 42.00,
                    packagingCost = 7.00,
                    utilitiesCost = 3.50,
                    profitMarginPercent = 37.0,
                    isFavorite = false
                )
            )

            // 2. Ingredients for Chocolate Cupcakes (matches Figma screen exactly!)
            ingredientDao.insertIngredients(
                listOf(
                    RecipeIngredientEntity(recipeId = r1Id, name = "Flour", quantity = 180.0, unit = "g", cost = 0.17),
                    RecipeIngredientEntity(recipeId = r1Id, name = "Sugar", quantity = 250.0, unit = "g", cost = 0.21),
                    RecipeIngredientEntity(recipeId = r1Id, name = "Cocoa", quantity = 25.0, unit = "g", cost = 0.28),
                    RecipeIngredientEntity(recipeId = r1Id, name = "Baking Soda", quantity = 0.75, unit = "tsp", cost = 0.22),
                    RecipeIngredientEntity(recipeId = r1Id, name = "Salt", quantity = 0.25, unit = "tsp", cost = 0.02),
                    RecipeIngredientEntity(recipeId = r1Id, name = "Buttermilk", quantity = 240.0, unit = "ml", cost = 28.00),
                    RecipeIngredientEntity(recipeId = r1Id, name = "Vegetable Oil", quantity = 80.0, unit = "ml", cost = 0.43),
                    RecipeIngredientEntity(recipeId = r1Id, name = "Large Egg", quantity = 1.0, unit = "egg", cost = 3.30),
                    RecipeIngredientEntity(recipeId = r1Id, name = "Vanilla Extract", quantity = 2.0, unit = "tsp", cost = 1.18)
                )
            )

            // Ingredients for Carrot Cake
            ingredientDao.insertIngredients(
                listOf(
                    RecipeIngredientEntity(recipeId = r2Id, name = "Cake Flour", quantity = 300.0, unit = "g", cost = 4.50),
                    RecipeIngredientEntity(recipeId = r2Id, name = "Fresh Carrots Grated", quantity = 400.0, unit = "g", cost = 8.00),
                    RecipeIngredientEntity(recipeId = r2Id, name = "Cream Cheese", quantity = 250.0, unit = "g", cost = 24.50),
                    RecipeIngredientEntity(recipeId = r2Id, name = "Brown Sugar", quantity = 200.0, unit = "g", cost = 3.20),
                    RecipeIngredientEntity(recipeId = r2Id, name = "Vegetable Oil", quantity = 150.0, unit = "ml", cost = 2.80)
                )
            )

            // 3. Tasks from Figma "Today's Tasks" with calendar due dates
            taskDao.insertTask(
                TaskEntity(
                    title = "Bake Chocolate Cupcakes",
                    orderRef = "Order #ORD-1024",
                    dueTime = "Due 12:00 PM",
                    priority = "High",
                    status = "Pending",
                    dayOfWeek = "Mon",
                    dueDate = "2026-09-14"
                )
            )
            taskDao.insertTask(
                TaskEntity(
                    title = "Prepare Carrot Cake",
                    orderRef = "Order #ORD-1025",
                    dueTime = "Due 2:00 PM",
                    priority = "High",
                    status = "Pending",
                    dayOfWeek = "Mon",
                    dueDate = "2026-09-14"
                )
            )
            taskDao.insertTask(
                TaskEntity(
                    title = "Decorate Wedding Cake",
                    orderRef = "Order #ORD-1023",
                    dueTime = "Due 10:00 AM",
                    priority = "Medium",
                    status = "InProgress",
                    dayOfWeek = "Tue",
                    dueDate = "2026-09-15"
                )
            )
            taskDao.insertTask(
                TaskEntity(
                    title = "Make Cookie Dough",
                    orderRef = "For tomorrow's orders",
                    dueTime = "Due 4:30 PM",
                    priority = "Medium",
                    status = "InProgress",
                    dayOfWeek = "Tue",
                    dueDate = "2026-09-15"
                )
            )
            taskDao.insertTask(
                TaskEntity(
                    title = "Confirm Order #ORD-1022",
                    orderRef = "Customer: Thandeka M.",
                    dueTime = "Completed",
                    priority = "Low",
                    status = "Completed",
                    dayOfWeek = "Wed",
                    dueDate = "2026-09-16"
                )
            )
            taskDao.insertTask(
                TaskEntity(
                    title = "Macaron Shells Batch",
                    orderRef = "Weekend Event Booking",
                    dueTime = "Due 11:00 AM",
                    priority = "High",
                    status = "Pending",
                    dayOfWeek = "Sat",
                    dueDate = "2026-09-19"
                )
            )
            taskDao.insertTask(
                TaskEntity(
                    title = "Shopping Done",
                    orderRef = "Ingredients for today",
                    dueTime = "Completed",
                    priority = "Low",
                    status = "Completed"
                )
            )

            // 4. Inventory items (from Figma Low Stock screen)
            inventoryDao.insertItem(
                InventoryItemEntity(name = "Butter", currentStock = 130.0, minStock = 250.0, unit = "g", isLowStock = true, unitPrice = 0.13, packagePrice = 65.00, gramsPerUnit = 500.0, category = "Dairy & Butter")
            )
            inventoryDao.insertItem(
                InventoryItemEntity(name = "Eggs", currentStock = 6.0, minStock = 12.0, unit = "eggs", isLowStock = true, unitPrice = 3.50, packagePrice = 42.00, gramsPerUnit = 12.0, category = "Eggs")
            )
            inventoryDao.insertItem(
                InventoryItemEntity(name = "Vanilla Extract", currentStock = 1.0, minStock = 2.0, unit = "bottle", isLowStock = true, unitPrice = 45.00, packagePrice = 45.00, gramsPerUnit = 100.0, category = "Extracts & Flavors")
            )
            inventoryDao.insertItem(
                InventoryItemEntity(name = "Cocoa Powder", currentStock = 80.0, minStock = 200.0, unit = "g", isLowStock = true, unitPrice = 0.28, packagePrice = 70.00, gramsPerUnit = 250.0, category = "Chocolates & Cocoa")
            )
            inventoryDao.insertItem(
                InventoryItemEntity(name = "Baking Powder", currentStock = 20.0, minStock = 50.0, unit = "g", isLowStock = true, unitPrice = 0.15, packagePrice = 15.00, gramsPerUnit = 100.0, category = "Leaveners & Salts")
            )
            inventoryDao.insertItem(
                InventoryItemEntity(name = "Milk", currentStock = 200.0, minStock = 500.0, unit = "ml", isLowStock = true, unitPrice = 0.02, packagePrice = 20.00, gramsPerUnit = 1000.0, category = "Dairy & Butter")
            )
            inventoryDao.insertItem(
                InventoryItemEntity(name = "Cake Flour", currentStock = 5000.0, minStock = 2000.0, unit = "g", isLowStock = false, unitPrice = 0.015, packagePrice = 37.50, gramsPerUnit = 2500.0, category = "Flour & Grains")
            )
            inventoryDao.insertItem(
                InventoryItemEntity(name = "White Sugar", currentStock = 4500.0, minStock = 2000.0, unit = "g", isLowStock = false, unitPrice = 0.018, packagePrice = 45.00, gramsPerUnit = 2500.0, category = "Sugars & Sweeteners")
            )

            // 5. Suppliers from Figma
            val s1 = supplierDao.insertSupplier(
                SupplierEntity(
                    name = "Bidvest Waltons",
                    categories = "Packaging • Baking • Cleaning",
                    rating = 4.8,
                    reviewCount = 152,
                    openingHours = "Open • Closes 17:00",
                    phone = "011 111 1234",
                    email = "info@waltons.co.za",
                    website = "www.waltons.co.za",
                    about = "Bidvest Waltons is a leading supplier of quality packaging, baking ingredients, cleaning products and more for businesses of all sizes. Established supplier with competitive prices and excellent service.",
                    isFavorite = true,
                    isMySupplier = true
                )
            )
            val s2 = supplierDao.insertSupplier(
                SupplierEntity(
                    name = "Food Lover's Market",
                    categories = "Ingredients • Fresh Produce",
                    rating = 4.6,
                    reviewCount = 98,
                    openingHours = "Open • Closes 18:00",
                    phone = "021 111 2222",
                    email = "info@foodloversmarket.co.za",
                    website = "www.foodloversmarket.co.za",
                    about = "Direct from farms fresh dairy, fruits, nuts, flours and bakery supplies at retail and wholesale discounts.",
                    isFavorite = false,
                    isMySupplier = true
                )
            )
            val s3 = supplierDao.insertSupplier(
                SupplierEntity(
                    name = "Bakels",
                    categories = "Baking Ingredients • Mixes",
                    rating = 4.9,
                    reviewCount = 76,
                    openingHours = "Open • Closes 16:30",
                    phone = "011 450 1200",
                    email = "orders@bakels.co.za",
                    website = "www.bakels.co.za",
                    about = "Premier bakery ingredients supplier specialising in high-performance fondants, icings, bread mixes, and cake concentrates.",
                    isFavorite = true,
                    isMySupplier = true
                )
            )
            val s4 = supplierDao.insertSupplier(
                SupplierEntity(
                    name = "Metro Cash & Carry",
                    categories = "Bulk • Ingredients • Packaging",
                    rating = 4.3,
                    reviewCount = 64,
                    openingHours = "Open • Closes 17:00",
                    phone = "011 888 3400",
                    email = "sales@metrocash.co.za",
                    website = "www.metrocash.co.za",
                    about = "Wholesale cash & carry distributor offering bulk flour, sugar, dairy, and heavy duty bakery packaging.",
                    isFavorite = false,
                    isMySupplier = false
                )
            )
            val s5 = supplierDao.insertSupplier(
                SupplierEntity(
                    name = "Chef's Warehouse",
                    categories = "Equipment • Tools • Packaging",
                    rating = 4.7,
                    reviewCount = 112,
                    openingHours = "Open • Closes 16:00",
                    phone = "021 555 7890",
                    email = "orders@chefswarehouse.co.za",
                    website = "www.chefswarehouse.co.za",
                    about = "Specialist culinary store with professional silicone molds, cake rings, mixers, turntables and display boxes.",
                    isFavorite = false,
                    isMySupplier = false
                )
            )

            // 6. Specials from Figma
            specialDealDao.insertSpecials(
                listOf(
                    SpecialDealEntity(
                        supplierId = s1,
                        productName = "Golden Cloud Cake Flour 12.5kg",
                        currentPrice = 149.99,
                        originalPrice = 189.99,
                        discountPercent = 21,
                        validUntil = "Valid until 31 May 2024",
                        description = "Premium cake flour ideal for cakes, cupcakes and all your baking needs. Gives perfect results every time.",
                        terms = "• While stocks last\n• Valid for cash and card payments\n• For business customers only\n• No further discounts apply",
                        isUpcoming = false
                    ),
                    SpecialDealEntity(
                        supplierId = s1,
                        productName = "Selati White Sugar 10kg",
                        currentPrice = 159.99,
                        originalPrice = 189.99,
                        discountPercent = 16,
                        validUntil = "Valid until 31 May 2024",
                        description = "Pure refined granulated cane sugar suitable for all sweet bakery applications, syrups, and icings.",
                        terms = "• Limit 5 per customer\n• Valid until end of promotional month",
                        isUpcoming = false
                    ),
                    SpecialDealEntity(
                        supplierId = s2,
                        productName = "Clover Butter 500g",
                        currentPrice = 42.99,
                        originalPrice = 52.99,
                        discountPercent = 19,
                        validUntil = "Valid until 31 May 2024",
                        description = "Full cream salted farm butter with minimum 82% butterfat for rich crusts and smooth buttercreams.",
                        terms = "• Subject to availability",
                        isUpcoming = false
                    ),
                    SpecialDealEntity(
                        supplierId = s3,
                        productName = "Moirs Baking Powder 1kg",
                        currentPrice = 49.99,
                        originalPrice = 64.99,
                        discountPercent = 23,
                        validUntil = "Valid until 31 May 2024",
                        description = "Double-acting leavening agent for consistent high rise in cakes and quick breads.",
                        terms = "• Valid while stocks last",
                        isUpcoming = false
                    )
                )
            )

            // 7. Notifications from Figma
            notificationDao.insertNotification(
                NotificationEntity(
                    title = "Great job! 🎉",
                    message = "Your profit today is 18% higher than yesterday.",
                    type = "Alerts",
                    timeLabel = "9:30 AM",
                    isUnread = true,
                    dateGroup = "Today"
                )
            )
            notificationDao.insertNotification(
                NotificationEntity(
                    title = "Low Stock Alert",
                    message = "Butter is running low. 130g remaining.",
                    type = "Alerts",
                    timeLabel = "8:15 AM",
                    isUnread = true,
                    dateGroup = "Today"
                )
            )
            notificationDao.insertNotification(
                NotificationEntity(
                    title = "New Order Received",
                    message = "Order #ORD-1024 has been placed by Sarah Johnson.",
                    type = "Orders",
                    timeLabel = "7:45 AM",
                    isUnread = true,
                    dateGroup = "Today"
                )
            )
            notificationDao.insertNotification(
                NotificationEntity(
                    title = "Quote Accepted",
                    message = "Quote #QT-1005 has been accepted by Lerato Events.",
                    type = "Orders",
                    timeLabel = "Yesterday",
                    isUnread = true,
                    dateGroup = "Yesterday"
                )
            )
            notificationDao.insertNotification(
                NotificationEntity(
                    title = "Payment Received",
                    message = "Payment of R1 250.00 received for Invoice #INV-1007.",
                    type = "Orders",
                    timeLabel = "Yesterday",
                    isUnread = false,
                    dateGroup = "Yesterday"
                )
            )
            notificationDao.insertNotification(
                NotificationEntity(
                    title = "System Update",
                    message = "BatchBoss has been updated to version 2.3.1.",
                    type = "System",
                    timeLabel = "Yesterday",
                    isUnread = false,
                    dateGroup = "Yesterday"
                )
            )

            // User Profile
            userProfileDao.insertOrUpdateProfile(
                UserProfileEntity(
                    id = 1,
                    fullName = "Tyne Jenkins",
                    bakeryName = "Tyne's Artisan Bakery",
                    specialty = "Cakes & Pastries",
                    phone = "+27 82 555 1234",
                    city = "Cape Town",
                    operatingModel = "Home Kitchen",
                    currency = "ZAR (R)",
                    email = "tyne@batchboss.com",
                    isPremium = false,
                    subscriptionPlan = "Free"
                )
            )

            // Initial Invoices
            invoiceDao.insertInvoice(
                InvoiceEntity(
                    invoiceNumber = "INV-2024-1007",
                    clientName = "Lerato Mthembu",
                    clientPhone = "+27 72 345 6789",
                    orderDescription = "3-Tier Rustic Wedding Cake (Vanilla & Salted Caramel)",
                    issueDate = "10 Sep 2026",
                    dueDate = "15 Sep 2026",
                    amount = 1850.00,
                    status = "Paid"
                )
            )
            invoiceDao.insertInvoice(
                InvoiceEntity(
                    invoiceNumber = "INV-2024-1008",
                    clientName = "Sipho Dlamini",
                    clientPhone = "+27 83 234 5678",
                    orderDescription = "48x Red Velvet Birthday Cupcakes with Cream Cheese Frosting",
                    issueDate = "12 Sep 2026",
                    dueDate = "16 Sep 2026",
                    amount = 950.00,
                    status = "Pending"
                )
            )
            invoiceDao.insertInvoice(
                InvoiceEntity(
                    invoiceNumber = "INV-2024-1009",
                    clientName = "Oakridge Corporate Bistro",
                    clientPhone = "+27 11 987 6543",
                    orderDescription = "25x Sourdough Artisan Loaves & 40x Butter Croissants",
                    issueDate = "13 Sep 2026",
                    dueDate = "18 Sep 2026",
                    amount = 1420.00,
                    status = "Pending"
                )
            )

            // Initial Quotes
            quoteDao.insertQuote(
                QuoteEntity(
                    quoteNumber = "QT-2024-1005",
                    clientName = "Lerato Mthembu",
                    clientPhone = "+27 72 345 6789",
                    eventType = "Wedding",
                    eventDate = "24 Oct 2026",
                    recipeOrItemName = "3-Tier Rustic Semi-Naked Wedding Cake (75 Servings)",
                    estimatedCost = 620.00,
                    profitMarginPercent = 45.0,
                    quotedPrice = 1850.00,
                    status = "Accepted"
                )
            )
            quoteDao.insertQuote(
                QuoteEntity(
                    quoteNumber = "QT-2024-1006",
                    clientName = "Chloe Petersen",
                    clientPhone = "+27 82 876 5432",
                    eventType = "Birthday",
                    eventDate = "28 Sep 2026",
                    recipeOrItemName = "Pastel Pink 1st Birthday Dessert Table & Cupcake Tower",
                    estimatedCost = 480.00,
                    profitMarginPercent = 40.0,
                    quotedPrice = 1400.00,
                    status = "Sent"
                )
            )
            quoteDao.insertQuote(
                QuoteEntity(
                    quoteNumber = "QT-2024-1007",
                    clientName = "Kagiso Events Co.",
                    clientPhone = "+27 79 123 9988",
                    eventType = "Corporate",
                    eventDate = "05 Oct 2026",
                    recipeOrItemName = "60x Artisan Brioche Rolls & 30x Carrot Cake Mini Bites",
                    estimatedCost = 350.00,
                    profitMarginPercent = 42.0,
                    quotedPrice = 980.00,
                    status = "Draft"
                )
            )
        }
    }
}
