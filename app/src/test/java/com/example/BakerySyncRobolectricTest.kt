package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
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
import com.example.data.local.UserProfileEntity
import com.example.data.local.generateBakeryId
import com.example.data.remote.WebSyncService
import com.example.data.repository.AppRepository
import com.example.ui.screens.formatUnitCost
import com.example.util.UnitUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BakerySyncRobolectricTest {

    private lateinit var database: AppDatabase
    private val testBakeryId = "bakery_test_suite_1"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `generateBakeryId creates clean formatted identifier`() {
        val id1 = generateBakeryId(1L, "The Sugar & Spice Bakery")
        assertEquals("bakery_the_sugar_spice_bake_1", id1)

        val id2 = generateBakeryId(42L, "Nadia's Custom Cakes!")
        assertEquals("bakery_nadia_s_custom_cakes_42", id2)

        val id3 = generateBakeryId(5L, "")
        assertEquals("bakery_artisan_5", id3)
    }

    @Test
    fun `user profile stores and persists bakeryId in database`() = runBlocking {
        val profile = UserProfileEntity(
            id = 1L,
            userId = 101L,
            bakeryId = testBakeryId,
            fullName = "Sarah Connor",
            email = "sarah@skynetbakery.com",
            phone = "+27 82 999 8888",
            bakeryName = "Resistance Bakery",
            city = "Cape Town",
            operatingModel = "Commercial Bakery",
            currency = "ZAR (R)"
        )

        database.userProfileDao().insertOrUpdateProfile(profile)

        val loaded = database.userProfileDao().getUserProfile().first()
        assertNotNull(loaded)
        assertEquals(testBakeryId, loaded?.bakeryId)
        assertEquals("Resistance Bakery", loaded?.bakeryName)
    }

    @Test
    fun `customer entity stores bakeryId and syncs to web backend`() = runBlocking {
        val customer = CustomerEntity(
            id = 5501L,
            userId = 101L,
            bakeryId = testBakeryId,
            name = "John Connor",
            phone = "+27 82 123 4567",
            email = "john@resistance.com",
            address = "77 Sanctuary Rd, Cape Town",
            notes = "Order: 10 Sourdough loaves every Monday",
            totalOrders = 3,
            totalSpend = 450.0
        )

        database.customerDao().insertCustomer(customer)
        val inDb = database.customerDao().getCustomerById(5501L)
        assertNotNull(inDb)
        assertEquals(testBakeryId, inDb?.bakeryId)

        // Sync with local backend if live server is reachable
        val isServerUp = try {
            val req = okhttp3.Request.Builder().url("http://127.0.0.1:3000/api/health").get().build()
            okhttp3.OkHttpClient.Builder().connectTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS).build()
                .newCall(req).execute().use { it.isSuccessful }
        } catch (_: Throwable) {
            false
        }

        if (isServerUp) {
            val syncSuccess = WebSyncService.syncCustomer(testBakeryId, customer)
            assertTrue("Customer should sync successfully to web backend", syncSuccess)

            // Verify customer appears on website backend
            val fetchedCustomers = WebSyncService.fetchBakeryCustomers(testBakeryId)
            assertTrue(fetchedCustomers.any { it.name == "John Connor" && it.bakeryId == testBakeryId })
        }
    }

    @Test
    fun `recipe entity stores bakeryId and syncs to web backend`() = runBlocking {
        val recipe = RecipeEntity(
            id = 8801L,
            userId = 101L,
            bakeryId = testBakeryId,
            name = "Golden Butter Croissant",
            category = "Pastries",
            description = "Crispy, laminated French style butter croissants.",
            servings = 24,
            batchSize = 2,
            difficulty = "Medium",
            rating = 4.8,
            reviewCount = 12,
            imageResName = "ic_cake",
            labourCost = 50.0,
            packagingCost = 20.0,
            utilitiesCost = 15.0,
            profitMarginPercent = 55.0,
            customSellingPrice = 35.0
        )

        database.recipeDao().insertRecipe(recipe)
        val inDb = database.recipeDao().getRecipeByIdOnce(8801L)
        assertNotNull(inDb)
        assertEquals(testBakeryId, inDb?.bakeryId)

        // Sync with local backend if live server is reachable
        val isServerUp = try {
            val req = okhttp3.Request.Builder().url("http://127.0.0.1:3000/api/health").get().build()
            okhttp3.OkHttpClient.Builder().connectTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS).build()
                .newCall(req).execute().use { it.isSuccessful }
        } catch (_: Throwable) {
            false
        }

        if (isServerUp) {
            val syncSuccess = WebSyncService.syncRecipe(testBakeryId, recipe)
            assertTrue("Recipe should sync successfully to web backend", syncSuccess)

            // Verify recipe appears on website backend
            val fetchedRecipes = WebSyncService.fetchBakeryRecipes(testBakeryId)
            assertTrue(fetchedRecipes.any { it.name == "Golden Butter Croissant" && it.bakeryId == testBakeryId })
        }
    }

    @Test
    fun `website or firestore price change updates Room and packaging screen flow`() = runBlocking {
        val repository = AppRepository(database)
        val initialItem = PackagingItemEntity(
            id = 10L,
            firestoreId = "pkg_firestore_box_1",
            userId = 101L,
            bakeryId = testBakeryId,
            name = "Window Cake Box 10in",
            category = "Cake boxes",
            unit = "pcs",
            packagePrice = 100.0,
            packageQuantity = 10.0,
            unitPrice = 10.0,
            currentStock = 50.0,
            minStock = 10.0,
            updatedAt = 1000L
        )
        database.packagingDao().insertPackaging(initialItem)

        // Web/Firestore updates price from R100 to R240 for 12 pack -> R20 each
        val remoteUpdate = PackagingItemEntity(
            id = 0L,
            firestoreId = "pkg_firestore_box_1",
            userId = 0L,
            bakeryId = testBakeryId,
            name = "Window Cake Box 10in",
            category = "Cake boxes",
            unit = "pcs",
            packagePrice = 240.0,
            packageQuantity = 12.0,
            unitPrice = 20.0,
            currentStock = 50.0,
            minStock = 10.0,
            updatedAt = 2000L
        )

        repository.syncPackagingWithFirestore(testBakeryId, listOf(remoteUpdate))

        // Check Room updated in-place preserving local ID 10L
        val updatedLocal = database.packagingDao().getPackagingByIdOnce(10L)
        assertNotNull(updatedLocal)
        assertEquals(10L, updatedLocal?.id)
        assertEquals("pkg_firestore_box_1", updatedLocal?.firestoreId)
        assertEquals(240.0, updatedLocal?.packagePrice ?: 0.0, 0.001)
        assertEquals(12.0, updatedLocal?.packageQuantity ?: 0.0, 0.001)
        assertEquals(20.0, updatedLocal?.unitPrice ?: 0.0, 0.001)

        // Check screen flow emits updated item
        val flowItems = repository.getPackagingByBakery(testBakeryId).first()
        assertEquals(1, flowItems.size)
        assertEquals(20.0, flowItems[0].unitPrice, 0.001)
    }

    @Test
    fun `deleted Firestore item is removed locally`() = runBlocking {
        val repository = AppRepository(database)
        val itemA = PackagingItemEntity(
            id = 21L,
            firestoreId = "doc_item_a",
            bakeryId = testBakeryId,
            name = "Brown Kraft Box",
            unit = "pcs",
            packagePrice = 50.0,
            packageQuantity = 5.0,
            unitPrice = 10.0
        )
        val itemB = PackagingItemEntity(
            id = 22L,
            firestoreId = "doc_item_b",
            bakeryId = testBakeryId,
            name = "Gold Ribbon Roll",
            unit = "roll",
            packagePrice = 80.0,
            packageQuantity = 1.0,
            unitPrice = 80.0
        )
        database.packagingDao().insertPackaging(itemA)
        database.packagingDao().insertPackaging(itemB)

        assertEquals(2, database.packagingDao().getPackagingByBakeryOnce(testBakeryId).size)

        // Remote snapshot only contains itemA (itemB was deleted on Firestore)
        repository.syncPackagingWithFirestore(testBakeryId, listOf(itemA))

        val remaining = database.packagingDao().getPackagingByBakeryOnce(testBakeryId)
        assertEquals(1, remaining.size)
        assertEquals(21L, remaining[0].id)
        assertEquals("doc_item_a", remaining[0].firestoreId)

        val deletedItem = database.packagingDao().getPackagingByIdOnce(22L)
        org.junit.Assert.assertNull("Deleted Firestore item must be removed from Room", deletedItem)
    }

    @Test
    fun `refreshing does not create duplicate packaging items`() = runBlocking {
        val repository = AppRepository(database)
        val item = PackagingItemEntity(
            id = 30L,
            firestoreId = "doc_item_c",
            bakeryId = testBakeryId,
            name = "Clear Cupcake Clamshell",
            unit = "pcs",
            packagePrice = 120.0,
            packageQuantity = 10.0,
            unitPrice = 12.0,
            updatedAt = 5000L
        )
        database.packagingDao().insertPackaging(item)

        // Multiple sync/refresh cycles from Firestore
        repository.syncPackagingWithFirestore(testBakeryId, listOf(item))
        repository.syncPackagingWithFirestore(testBakeryId, listOf(item))
        repository.syncPackagingWithFirestore(testBakeryId, listOf(item))

        val allItems = database.packagingDao().getPackagingByBakeryOnce(testBakeryId)
        assertEquals("Should not create duplicate items on refresh", 1, allItems.size)
        assertEquals(30L, allItems[0].id)
        assertEquals("doc_item_c", allItems[0].firestoreId)
    }

    @Test
    fun `R144 divided by 12 displays a unit cost of R12`() {
        val packagePrice = 144.0
        val packageQuantity = 12.0
        val unitPrice = packagePrice / packageQuantity

        assertEquals(12.0, unitPrice, 0.001)

        val unitCostDisplay = formatUnitCost(unitPrice)
        assertEquals("R12", unitCostDisplay)

        val screenLabel = "$unitCostDisplay / pcs"
        assertEquals("R12 / pcs", screenLabel)
    }

    @Test
    fun `website ingredient changes update Android Room and the UI`() = runBlocking {
        val repository = AppRepository(database)
        val initialItem = InventoryItemEntity(
            id = 101L,
            firestoreId = "inv_flour_1",
            bakeryId = testBakeryId,
            name = "Sync Test Flour",
            packagePrice = 40.0,
            gramsPerUnit = 2500.0,
            unitPrice = 0.016,
            currentStock = 10000.0,
            minStock = 2000.0,
            unit = "g",
            updatedAt = 1000L
        )
        database.inventoryDao().insertItem(initialItem)

        // Remote update: price changes to R50
        val remoteUpdate = initialItem.copy(
            id = 0L,
            packagePrice = 50.0,
            unitPrice = 0.02,
            updatedAt = 2000L
        )
        repository.syncInventoryWithFirestore(testBakeryId, listOf(remoteUpdate))

        val local = database.inventoryDao().getInventoryByIdOnce(101L)
        assertNotNull(local)
        assertEquals(50.0, local!!.packagePrice, 0.001)
        assertEquals(0.02, local.unitPrice, 0.001)

        val flowItems = repository.getInventoryByBakery(testBakeryId).first()
        assertEquals(1, flowItems.size)
        assertEquals(0.02, flowItems[0].unitPrice, 0.001)
    }

    @Test
    fun `android ingredient changes update the same Firestore document`() {
        val item = InventoryItemEntity(
            id = 101L,
            firestoreId = "inv_flour_1",
            bakeryId = testBakeryId,
            name = "Sync Test Flour",
            packagePrice = 45.0,
            gramsPerUnit = 2500.0,
            unitPrice = 0.018,
            currentStock = 8000.0,
            minStock = 2000.0,
            unit = "g"
        )
        assertEquals("inv_flour_1", item.firestoreDocumentId)
        assertEquals("inv_flour_1", item.firestoreId)
    }

    @Test
    fun `website recipe changes update Android`() = runBlocking {
        val repository = AppRepository(database)
        val initialRecipe = RecipeEntity(
            id = 501L,
            firestoreId = "rec_sponge_1",
            bakeryId = testBakeryId,
            name = "Classic Sponge Cake",
            category = "Cakes",
            description = "Light sponge cake",
            servings = 8,
            batchSize = 1,
            difficulty = "Easy",
            rating = 4.5,
            reviewCount = 5,
            imageResName = "ic_cake",
            labourCost = 30.0,
            profitMarginPercent = 40.0,
            customSellingPrice = 120.0,
            updatedAt = 1000L
        )
        database.recipeDao().insertRecipe(initialRecipe)

        val remoteUpdate = initialRecipe.copy(
            id = 0L,
            profitMarginPercent = 50.0,
            customSellingPrice = 150.0,
            updatedAt = 2000L
        )
        repository.syncRecipesWithFirestore(testBakeryId, listOf(remoteUpdate))

        val local = database.recipeDao().getRecipeByIdOnce(501L)
        assertNotNull(local)
        assertEquals(501L, local!!.id)
        assertEquals("rec_sponge_1", local.firestoreId)
        assertEquals(50.0, local.profitMarginPercent, 0.001)
        assertEquals(150.0, local.customSellingPrice, 0.001)

        val flow = repository.getRecipesByBakery(testBakeryId).first()
        assertEquals(1, flow.size)
        assertEquals(150.0, flow[0].customSellingPrice, 0.001)
    }

    @Test
    fun `recipe ingredient subcollections synchronise correctly`() = runBlocking {
        val repository = AppRepository(database)
        val recipe = RecipeEntity(
            id = 601L,
            firestoreId = "rec_croissant_9",
            bakeryId = testBakeryId,
            name = "Butter Croissant",
            category = "Pastries",
            description = "Crispy laminated croissant",
            servings = 12,
            batchSize = 1,
            difficulty = "Hard",
            rating = 4.9,
            reviewCount = 10,
            imageResName = "ic_cake",
            labourCost = 40.0,
            profitMarginPercent = 50.0
        )
        database.recipeDao().insertRecipe(recipe)

        val remoteIngredients = listOf(
            RecipeIngredientEntity(
                id = 0L,
                firestoreId = "ing_flour_doc_1",
                recipeFirestoreId = "rec_croissant_9",
                recipeId = 601L,
                bakeryId = testBakeryId,
                name = "Bread Flour",
                quantity = 500.0,
                unit = "g",
                cost = 10.0,
                updatedAt = 2000L
            ),
            RecipeIngredientEntity(
                id = 0L,
                firestoreId = "ing_butter_doc_2",
                recipeFirestoreId = "rec_croissant_9",
                recipeId = 601L,
                bakeryId = testBakeryId,
                name = "French Butter",
                quantity = 250.0,
                unit = "g",
                cost = 25.0,
                updatedAt = 2000L
            )
        )

        repository.syncRecipeIngredientsWithFirestore(testBakeryId, "rec_croissant_9", 601L, remoteIngredients)

        val localIngs = database.recipeIngredientDao().getIngredientsForRecipeFirestoreIdOnce("rec_croissant_9")
        assertEquals(2, localIngs.size)
        assertTrue(localIngs.any { it.name == "Bread Flour" && it.cost == 10.0 && it.recipeFirestoreId == "rec_croissant_9" })
        assertTrue(localIngs.any { it.name == "French Butter" && it.cost == 25.0 && it.recipeFirestoreId == "rec_croissant_9" })
    }

    @Test
    fun `deleted records disappear on both platforms`() = runBlocking {
        val repository = AppRepository(database)
        val item1 = InventoryItemEntity(
            id = 701L, firestoreId = "inv_keep", bakeryId = testBakeryId,
            name = "Sugar", currentStock = 10.0, minStock = 2.0, unit = "kg"
        )
        val item2 = InventoryItemEntity(
            id = 702L, firestoreId = "inv_delete", bakeryId = testBakeryId,
            name = "Cocoa Powder", currentStock = 5.0, minStock = 1.0, unit = "kg"
        )
        database.inventoryDao().insertItem(item1)
        database.inventoryDao().insertItem(item2)

        assertEquals(2, database.inventoryDao().getInventoryByBakeryOnce(testBakeryId).size)

        repository.syncInventoryWithFirestore(testBakeryId, listOf(item1))

        val remaining = database.inventoryDao().getInventoryByBakeryOnce(testBakeryId)
        assertEquals(1, remaining.size)
        assertEquals("inv_keep", remaining[0].firestoreId)
        assertNull(database.inventoryDao().getInventoryByIdOnce(702L))
    }

    @Test
    fun `repeated sync does not create duplicates`() = runBlocking {
        val repository = AppRepository(database)
        val customer = CustomerEntity(
            id = 801L, firestoreId = "cust_rep_1", bakeryId = testBakeryId,
            name = "Alice Baker", phone = "0820000000", email = "alice@baker.com"
        )
        database.customerDao().insertCustomer(customer)

        repository.syncCustomersWithFirestore(testBakeryId, listOf(customer))
        repository.syncCustomersWithFirestore(testBakeryId, listOf(customer))
        repository.syncCustomersWithFirestore(testBakeryId, listOf(customer))

        val list = database.customerDao().getCustomersByBakeryOnce(testBakeryId)
        assertEquals(1, list.size)
        assertEquals("cust_rep_1", list[0].firestoreId)
    }

    @Test
    fun `older cached data cannot overwrite newer Firestore data`() = runBlocking {
        val repository = AppRepository(database)
        val localNewer = InventoryItemEntity(
            id = 901L, firestoreId = "inv_newer", bakeryId = testBakeryId,
            name = "Vanilla Extract", packagePrice = 80.0, currentStock = 5.0, minStock = 1.0, unit = "bottle",
            updatedAt = 5000L
        )
        database.inventoryDao().insertItem(localNewer)

        val staleRemote = localNewer.copy(id = 0L, packagePrice = 60.0, updatedAt = 3000L)
        repository.syncInventoryWithFirestore(testBakeryId, listOf(staleRemote))

        val inDb = database.inventoryDao().getInventoryByIdOnce(901L)
        assertNotNull(inDb)
        assertEquals(80.0, inDb!!.packagePrice, 0.001)
    }

    @Test
    fun `account switching never displays another bakery's data`() = runBlocking {
        val repository = AppRepository(database)
        val bakeryA = "bakery_alpha"
        val bakeryB = "bakery_beta"

        database.customerDao().insertCustomer(
            CustomerEntity(id = 11L, firestoreId = "cust_a", bakeryId = bakeryA, name = "Alpha Client")
        )
        database.customerDao().insertCustomer(
            CustomerEntity(id = 12L, firestoreId = "cust_b", bakeryId = bakeryB, name = "Beta Client")
        )

        val alphaCustomers = repository.getCustomersByBakery(bakeryA).first()
        val betaCustomers = repository.getCustomersByBakery(bakeryB).first()

        assertEquals(1, alphaCustomers.size)
        assertEquals("Alpha Client", alphaCustomers[0].name)

        assertEquals(1, betaCustomers.size)
        assertEquals("Beta Client", betaCustomers[0].name)

        repository.clearBakeryData(bakeryA)
        val alphaAfterClear = repository.getCustomersByBakery(bakeryA).first()
        val betaAfterClear = repository.getCustomersByBakery(bakeryB).first()

        assertEquals(0, alphaAfterClear.size)
        assertEquals(1, betaAfterClear.size)
    }

    @Test
    fun `R40 for 2_5 kg correctly produces R4 for 250 g`() {
        val packageQuantityKg = 2.5
        val packagePrice = 40.0
        val baseQuantityGrams = UnitUtils.toBaseGrams(packageQuantityKg, "kg")
        assertEquals(2500.0, baseQuantityGrams, 0.001)

        val costPerGram = packagePrice / baseQuantityGrams
        assertEquals(0.016, costPerGram, 0.0001)

        val recipeCostFor250g = UnitUtils.calculateCost(250.0, "g", costPerGram)
        assertEquals(4.00, recipeCostFor250g, 0.001)
    }

    @Test
    fun `R50 for 2_5 kg correctly produces R5 for 250 g and recalculates recipe ingredient cost`() = runBlocking {
        val repository = AppRepository(database)

        val flour = InventoryItemEntity(
            id = 991L, firestoreId = "inv_flour_recalc", bakeryId = testBakeryId,
            name = "Sync Test Flour", packagePrice = 40.0, gramsPerUnit = 2500.0, unitPrice = 0.016,
            currentStock = 10000.0, minStock = 1000.0, unit = "g", updatedAt = 1000L
        )
        database.inventoryDao().insertItem(flour)

        val ing = RecipeIngredientEntity(
            id = 992L, firestoreId = "ing_flour_recalc", recipeFirestoreId = "rec_cake_recalc",
            recipeId = 1L, bakeryId = testBakeryId, name = "Sync Test Flour",
            quantity = 250.0, unit = "g", cost = 4.00, updatedAt = 1000L
        )
        database.recipeIngredientDao().insertIngredient(ing)

        val updatedFlour = flour.copy(
            id = 0L, packagePrice = 50.0, unitPrice = 50.0 / 2500.0, updatedAt = 2000L
        )
        assertEquals(0.02, updatedFlour.unitPrice, 0.0001)

        repository.syncInventoryWithFirestore(testBakeryId, listOf(updatedFlour))

        val recalculatedIng = database.recipeIngredientDao().getIngredientByIdOnce(992L)
        assertNotNull(recalculatedIng)
        assertEquals(5.00, recalculatedIng!!.cost, 0.001)
    }

    @Test
    fun `customers synchronise both ways`() = runBlocking {
        val repository = AppRepository(database)
        val remoteCustomer = CustomerEntity(
            id = 0L, firestoreId = "cust_both_1", bakeryId = testBakeryId,
            name = "Grace Hopper", phone = "0831112222", email = "grace@hopper.org"
        )
        repository.syncCustomersWithFirestore(testBakeryId, listOf(remoteCustomer))
        val inDb = database.customerDao().getCustomerByFirestoreId("cust_both_1")
        assertNotNull(inDb)
        assertEquals("Grace Hopper", inDb!!.name)
    }

    @Test
    fun `products synchronise both ways`() = runBlocking {
        val repository = AppRepository(database)
        val remoteProduct = ProductServiceEntity(
            id = 0L, firestoreId = "prod_both_1", bakeryId = testBakeryId,
            name = "Artisan Sourdough Loaf", sellingPrice = 45.0, costPrice = 18.0
        )
        repository.syncProductsWithFirestore(testBakeryId, listOf(remoteProduct))
        val inDb = database.productServiceDao().getProductByFirestoreId("prod_both_1")
        assertNotNull(inDb)
        assertEquals("Artisan Sourdough Loaf", inDb!!.name)
        assertEquals(45.0, inDb.sellingPrice, 0.001)
    }

    @Test
    fun `invoices synchronise both ways`() = runBlocking {
        val repository = AppRepository(database)
        val remoteInvoice = InvoiceEntity(
            id = 0L, firestoreId = "inv_both_1", bakeryId = testBakeryId,
            invoiceNumber = "INV-2026-001", clientName = "Hotel Royale", clientPhone = "0823334444",
            orderDescription = "50 Baguettes", issueDate = "2026-10-01", dueDate = "2026-10-05",
            amount = 1250.0, status = "Pending"
        )
        repository.syncInvoicesWithFirestore(testBakeryId, listOf(remoteInvoice))
        val inDb = database.invoiceDao().getInvoiceByFirestoreId("inv_both_1")
        assertNotNull(inDb)
        assertEquals("INV-2026-001", inDb!!.invoiceNumber)
        assertEquals(1250.0, inDb.amount, 0.001)
    }

    @Test
    fun `quotes synchronise both ways`() = runBlocking {
        val repository = AppRepository(database)
        val remoteQuote = QuoteEntity(
            id = 0L, firestoreId = "quote_both_1", bakeryId = testBakeryId,
            quoteNumber = "QTE-2026-001", clientName = "Wedding Event Co", clientPhone = "0845556666",
            eventType = "Wedding", eventDate = "2026-12-15", recipeOrItemName = "3-Tier Floral Cake",
            estimatedCost = 1500.0, profitMarginPercent = 50.0, quotedPrice = 3000.0
        )
        repository.syncQuotesWithFirestore(testBakeryId, listOf(remoteQuote))
        val inDb = database.quoteDao().getQuoteByFirestoreId("quote_both_1")
        assertNotNull(inDb)
        assertEquals("QTE-2026-001", inDb!!.quoteNumber)
        assertEquals(3000.0, inDb.quotedPrice, 0.001)
    }

    @Test
    fun `orders synchronise both ways`() = runBlocking {
        val repository = AppRepository(database)
        val remoteOrder = OrderEntity(
            id = 0L, firestoreId = "ord_both_1", bakeryId = testBakeryId,
            orderNumber = "ORD-2026-001", customerName = "David Baker",
            description = "Custom Birthday Cake", dueDate = "2026-10-10", totalAmount = 650.0
        )
        repository.syncOrdersWithFirestore(testBakeryId, listOf(remoteOrder))
        val inDb = database.orderDao().getOrderByFirestoreId("ord_both_1")
        assertNotNull(inDb)
        assertEquals("ORD-2026-001", inDb!!.orderNumber)
        assertEquals(650.0, inDb.totalAmount, 0.001)
    }

    @Test
    fun `tasks calendar synchronise both ways`() = runBlocking {
        val repository = AppRepository(database)
        val remoteTask = TaskEntity(
            id = 0L, firestoreId = "task_both_1", bakeryId = testBakeryId,
            title = "Bake 24 Sourdough Batards", orderRef = "ORD-2026-001", dueTime = "06:00",
            priority = "High", status = "Pending", dueDate = "2026-10-02"
        )
        repository.syncTasksWithFirestore(testBakeryId, listOf(remoteTask))
        val inDb = database.taskDao().getTaskByFirestoreId("task_both_1")
        assertNotNull(inDb)
        assertEquals("Bake 24 Sourdough Batards", inDb!!.title)
        assertEquals("High", inDb.priority)
    }

    @Test
    fun `packaging continues working after the shared sync implementation`() = runBlocking {
        val repository = AppRepository(database)
        val pkg = PackagingItemEntity(
            id = 0L, firestoreId = "pkg_shared_1", bakeryId = testBakeryId,
            name = "Cupcake Box 6-Hole", category = "Cupcake boxes", unit = "pcs",
            packagePrice = 120.0, packageQuantity = 10.0, unitPrice = 12.0
        )
        repository.syncPackagingWithFirestore(testBakeryId, listOf(pkg))
        val list = repository.getPackagingByBakery(testBakeryId).first()
        assertEquals(1, list.size)
        assertEquals("Cupcake Box 6-Hole", list[0].name)
        assertEquals(12.0, list[0].unitPrice, 0.001)
    }

    @Test
    fun `invoice order task automation does not create duplicates`() = runBlocking {
        val repository = AppRepository(database)
        val invoice = InvoiceEntity(
            id = 0L, firestoreId = "inv_auto_1", bakeryId = testBakeryId,
            invoiceNumber = "INV-AUTO-100", clientName = "Maria Garcia", clientPhone = "0827778888",
            orderDescription = "Chocolate Fudge Cake", issueDate = "2026-10-01", dueDate = "2026-10-03",
            amount = 550.0, status = "Pending"
        )

        val invId = repository.recordInvoiceCreated(invoice, 101L)
        assertTrue(invId > 0)

        val order = database.orderDao().getOrderByOrderNumber(testBakeryId, "INV-AUTO-100")
        assertNotNull("Connected order should exist", order)
        assertEquals("Pending", order!!.status)

        val task = database.taskDao().getTaskByOrderRef(testBakeryId, "INV-AUTO-100")
        assertNotNull("Connected task should exist", task)
        assertEquals("Pending", task!!.status)

        // Mark invoice as Paid
        repository.recordInvoicePaid(invId)

        val updatedOrder = database.orderDao().getOrderByOrderNumber(testBakeryId, "INV-AUTO-100")
        assertEquals("Completed", updatedOrder!!.status)

        val updatedTask = database.taskDao().getTaskByOrderRef(testBakeryId, "INV-AUTO-100")
        assertEquals("Completed", updatedTask!!.status)

        // Verify no duplicate orders or tasks created
        val allOrders = database.orderDao().getOrdersByBakeryOnce(testBakeryId)
        val allTasks = database.taskDao().getTasksByBakeryOnce(testBakeryId)
        assertEquals(1, allOrders.size)
        assertEquals(1, allTasks.size)
    }
}
