package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.remote.BusinessDocumentCodec
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BusinessSyncRepairTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private val bakery = "bakery-fixture"

    @Before fun setup(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = AppRepository(db)
        repo.insertUserAccount(UserAccountEntity(id = 71, bakeryId = bakery, email = "fixture@example.test"))
        Unit
    }
    @After fun close() { db.close() }

    @Test fun `new records inherit the established workspace and receive stable cloud IDs`() = runBlocking {
        val customer = repo.insertCustomer(CustomerEntity(userId = 71, name = "Customer"))
        val product = repo.insertProduct(ProductServiceEntity(userId = 71, name = "Cake"))
        val stock = repo.insertInventoryItem(InventoryItemEntity(userId = 71, name = "Flour"))
        assertEquals(bakery, repo.customerDao.getCustomerByIdOnce(customer)!!.bakeryId)
        assertEquals(bakery, repo.productServiceDao.getProductByIdOnce(product)!!.bakeryId)
        assertEquals(bakery, repo.inventoryDao.getInventoryByIdOnce(stock)!!.bakeryId)
        assertEquals(3, repo.pendingCloudChanges.list(bakery).size)
        assertTrue(repo.customerDao.getCustomerByIdOnce(customer)!!.firestoreId.isNotBlank())
    }

    @Test fun `failure rolls back the record and its pending cloud write together`() = runBlocking {
        try {
            repo.atomic {
                repo.insertCustomer(CustomerEntity(userId = 71, name = "Must roll back"))
                error("simulated persistence failure")
            }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertTrue(repo.getCustomersByBakeryOnce(bakery).isEmpty())
        assertTrue(repo.pendingCloudChanges.list(bakery).isEmpty())
    }

    @Test fun `unknown account or different workspace cannot silently receive saved data`() = runBlocking {
        for (record in listOf(CustomerEntity(userId = 999, name = "Unknown"), CustomerEntity(userId = 71, bakeryId = "another-bakery", name = "Wrong"))) {
            try { repo.insertCustomer(record); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(repo.pendingCloudChanges.list(bakery).isEmpty())
    }

    @Test fun `snapshots cannot delete or overwrite pending local records`() = runBlocking {
        val id = repo.insertCustomer(CustomerEntity(userId = 71, name = "Local edit"))
        val row = repo.customerDao.getCustomerByIdOnce(id)!!
        repo.syncCustomersWithFirestore(bakery, listOf(row.copy(name = "Stale website", updatedAt = Long.MAX_VALUE)))
        assertEquals("Local edit", repo.customerDao.getCustomerByIdOnce(id)!!.name)
        repo.syncCustomersWithFirestore(bakery, emptyList())
        assertNotNull(repo.customerDao.getCustomerByIdOnce(id))
    }

    @Test fun `acknowledgement never clears a newer queued edit`() = runBlocking {
        val id = repo.insertCustomer(CustomerEntity(userId = 71, name = "First"))
        val first = repo.pendingCloudChanges.list(bakery).single()
        repo.updateCustomer(repo.customerDao.getCustomerByIdOnce(id)!!.copy(name = "Second"))
        repo.acknowledgeCloudChange(first, 123)
        val second = repo.pendingCloudChanges.list(bakery).single()
        assertNotEquals(first.revision, second.revision)
        assertTrue(second.ancestors.split(',').contains(first.revision))
        repo.acknowledgeCloudChange(second, 456)
        assertTrue(repo.pendingCloudChanges.list(bakery).isEmpty())
        assertEquals(456L, repo.customerDao.getCustomerByIdOnce(id)!!.updatedAt)
    }

    @Test fun `acknowledged timestamp is the next edit's cloud baseline`() = runBlocking {
        val id = repo.insertCustomer(CustomerEntity(userId = 71, name = "First"))
        repo.acknowledgeCloudChange(repo.pendingCloudChanges.list(bakery).single(), 456)
        repo.updateCustomer(repo.customerDao.getCustomerByIdOnce(id)!!.copy(name = "Next edit"))
        assertEquals(456L, repo.pendingCloudChanges.list(bakery).single().expectedUpdatedAt)
    }

    @Test fun `deleted local record is not resurrected by a stale cloud snapshot`() = runBlocking {
        val id = repo.insertCustomer(CustomerEntity(userId = 71, name = "Delete me"))
        val old = repo.customerDao.getCustomerByIdOnce(id)!!
        repo.deleteCustomer(id)
        repo.syncCustomersWithFirestore(bakery, listOf(old))
        assertNull(repo.customerDao.getCustomerByIdOnce(id))
        assertTrue(repo.pendingCloudChanges.list(bakery).single().deleted)
    }

    @Test fun `website lines preserve names quantities prices and totals without a fake product ID`() {
        val json = BusinessDocumentCodec.lines(listOf(mapOf("name" to "Cake", "productId" to "website-uuid", "quantity" to 2, "unitPrice" to 125, "lineTotal" to 250)))
        val line = LineItemJsonUtil.fromJson(json).single()
        assertEquals("Cake", line.itemName)
        assertEquals(2.0, line.quantity, 0.0)
        assertEquals(250.0, line.lineTotal, 0.0)
        assertNull(line.productId)
        assertEquals("website-uuid", line.productFirestoreId)
        assertEquals("Cake", BusinessDocumentCodec.webLines(json).single()["name"])
        assertEquals("website-uuid", BusinessDocumentCodec.webLines(json).single()["productId"])
    }

    @Test fun `website document status matches Android balance filters`() {
        assertEquals("Paid", BusinessDocumentCodec.androidStatus("paid"))
        assertEquals("Pending", BusinessDocumentCodec.androidStatus("pending"))
        assertEquals("Accepted", BusinessDocumentCodec.androidStatus("accepted"))
        assertEquals("Draft", BusinessDocumentCodec.androidStatus("draft"))
    }

    @Test fun `packaging exports as its own website line and is not reimported as a product`() {
        val products = LineItemJsonUtil.toJson(listOf(LineItem(itemName = "Cake", quantity = 1.0, unitPrice = 100.0, lineTotal = 100.0)))
        val packaging = InvoicePackagingJsonUtil.toJson(listOf(InvoicePackagingItem(name = "Box", quantity = 1.0, unitPrice = 10.0)))
        val web = BusinessDocumentCodec.webInvoiceLines(products, packaging)
        assertEquals(2, web.size)
        assertEquals("packaging", web.last()["kind"])
        val productLines = LineItemJsonUtil.fromJson(BusinessDocumentCodec.lines(web))
        assertEquals(1, productLines.size)
        assertEquals("Cake", productLines.single().itemName)
        assertNull(productLines.single().productFirestoreId)
    }

    @Test fun `invoice header JSON normalized rows and queue commit together`() = runBlocking {
        val json = LineItemJsonUtil.toJson(listOf(LineItem(itemName = "Cake", quantity = 2.0, unitPrice = 125.0, lineTotal = 250.0)))
        val id = repo.insertInvoice(InvoiceEntity(userId = 71, invoiceNumber = "INV-fixture", clientName = "Client", clientPhone = "", orderDescription = "Cake", issueDate = "Today", dueDate = "Tomorrow", amount = 250.0, subtotal = 250.0, lineItemsJson = json))
        assertEquals(250.0, repo.getInvoiceByIdOnce(id)!!.amount, 0.0)
        assertEquals("Cake", repo.documentLineItemDao.getLineItems("INVOICE", id).first().single().itemName)
        assertEquals("invoices", repo.pendingCloudChanges.list(bakery).single().collection)
        repo.updateInvoiceStatus(id, "Paid")
        assertEquals(1, repo.documentLineItemDao.getLineItems("INVOICE", id).first().size)
    }

    @Test fun `remote row ID collision cannot replace another bakery's records`() = runBlocking {
        val id = repo.insertCustomer(CustomerEntity(userId = 71, name = "Original"))
        repo.syncCustomersWithFirestore("other-bakery", listOf(CustomerEntity(id = id, firestoreId = "cloud-other", bakeryId = "other-bakery", name = "Other")))
        assertEquals("Original", repo.customerDao.getCustomerByIdOnce(id)!!.name)
        assertEquals("Other", repo.getCustomersByBakeryOnce("other-bakery").single().name)
    }

    @Test fun `recipe ingredient writes inherit the parent workspace and survive empty snapshots`() = runBlocking {
        val recipeId = repo.insertRecipe(RecipeEntity(userId = 71, name = "Cake", category = "Cakes", description = "", servings = 4, batchSize = 1, difficulty = "Easy", labourCost = 0.0, profitMarginPercent = 40.0, rating = 0.0, reviewCount = 0, imageResName = ""))
        val parent = repo.getRecipeByIdOnce(recipeId)!!
        val id = repo.insertIngredient(RecipeIngredientEntity(recipeId = recipeId, name = "Flour", quantity = 500.0, unit = "g", cost = 10.0))
        val ingredient = repo.ingredientDao.getIngredientByIdOnce(id)!!
        assertEquals(bakery, ingredient.bakeryId)
        assertEquals(parent.firestoreId, ingredient.recipeFirestoreId)
        val ingredientChange = repo.pendingCloudChanges.list(bakery).first { it.collection == "ingredients" }
        assertTrue(repo.parentIsPending(ingredientChange))
        repo.acknowledgeCloudChange(repo.pendingCloudChanges.list(bakery).first { it.collection == "recipes" }, 123)
        assertFalse(repo.parentIsPending(ingredientChange))
        repo.syncRecipeIngredientsWithFirestore(bakery, parent.firestoreId, recipeId, emptyList())
        assertNotNull(repo.ingredientDao.getIngredientByIdOnce(id))
    }

    @Test fun `schema15 migration adds the retry queue without losing saved business records`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "business-migration-" + java.util.UUID.randomUUID() + ".db"
        val first = AppDatabase.builder(context, name).allowMainThreadQueries().build()
        first.customerDao().insertCustomer(CustomerEntity(id = 1, userId = 71, bakeryId = bakery, name = "Preserve me"))
        first.openHelper.writableDatabase.execSQL("DROP TABLE pending_cloud_changes")
        first.openHelper.writableDatabase.version = 15
        first.close()
        val migrated = AppDatabase.builder(context, name).allowMainThreadQueries().build()
        try {
            assertEquals("Preserve me", migrated.customerDao().getCustomerByIdOnce(1)!!.name)
            assertTrue(migrated.pendingCloudChangeDao().list(bakery).isEmpty())
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun `recovery claims only rows owned by the authenticated local account`() = runBlocking {
        val owned = repo.customerDao.insertCustomer(CustomerEntity(userId = 71, name = "Previously hidden"))
        val unknown = repo.customerDao.insertCustomer(CustomerEntity(userId = 0, name = "Unknown ownership"))
        val other = repo.customerDao.insertCustomer(CustomerEntity(userId = 72, name = "Other account"))
        repo.recoverOwnedLocalRecords(71, bakery)
        assertEquals(bakery, repo.customerDao.getCustomerByIdOnce(owned)!!.bakeryId)
        assertEquals("", repo.customerDao.getCustomerByIdOnce(unknown)!!.bakeryId)
        assertEquals("", repo.customerDao.getCustomerByIdOnce(other)!!.bakeryId)
        assertEquals(1, repo.pendingCloudChanges.list(bakery).size)
        repo.recoverOwnedLocalRecords(71, bakery)
        assertEquals(1, repo.pendingCloudChanges.list(bakery).size)
    }

    @Test fun `legacy owned customers with a bakery but no cloud identity are queued on sync`() = runBlocking {
        val id = repo.customerDao.insertCustomer(CustomerEntity(userId = 71, bakeryId = bakery, name = "Never uploaded"))
        repo.recoverOwnedLocalRecords(71, bakery)
        assertEquals(id.toString(), repo.customerDao.getCustomerByIdOnce(id)!!.firestoreId)
        assertEquals("customers/$id", repo.pendingCloudChanges.list(bakery).single().path)
        repo.recoverOwnedLocalRecords(71, bakery)
        assertEquals(1, repo.pendingCloudChanges.list(bakery).size)
    }

    @Test fun `editing legacy recipe pricing assigns a cloud identity and queues the edit`() = runBlocking {
        val id = repo.recipeDao.insertRecipe(RecipeEntity(userId = 71, bakeryId = bakery, name = "Legacy cake",
            category = "Cakes", description = "", servings = 4, batchSize = 1, difficulty = "Easy",
            labourCost = 0.0, profitMarginPercent = 40.0, rating = 0.0, reviewCount = 0, imageResName = ""))
        repo.updateProfitMargin(id, 55.0)
        assertEquals(55.0, repo.getRecipeByIdOnce(id)!!.profitMarginPercent, 0.0)
        assertTrue(repo.getRecipeByIdOnce(id)!!.firestoreId.isNotBlank())
        assertEquals("recipes", repo.pendingCloudChanges.list(bakery).single().collection)
    }

    @Test fun `identical old recipe document IDs in two bakeries cannot cross link ingredients`() = runBlocking {
        val recipe = RecipeEntity(userId = 71, bakeryId = bakery, firestoreId = "1", name = "Recipe A",
            category = "Cakes", description = "", servings = 4, batchSize = 1, difficulty = "Easy",
            labourCost = 0.0, profitMarginPercent = 40.0, rating = 0.0, reviewCount = 0, imageResName = "")
        val a = repo.recipeDao.insertRecipe(recipe)
        val b = repo.recipeDao.insertRecipe(recipe.copy(userId = 72, bakeryId = "bakery-other", name = "Recipe B"))
        val ingredient = RecipeIngredientEntity(recipeId = a, bakeryId = bakery, recipeFirestoreId = "1",
            firestoreId = "same-ingredient-id", name = "A ingredient", quantity = 1.0, unit = "g", cost = 1.0)
        val firstId = repo.ingredientDao.insertIngredient(ingredient)
        repo.ingredientDao.insertIngredient(ingredient.copy(recipeId = b, bakeryId = "bakery-other", name = "B ingredient"))
        assertEquals(b, repo.getRecipeByFirestoreId("1", "bakery-other")!!.id)
        repo.syncRecipeIngredientsWithFirestore("bakery-other", "1", b,
            listOf(ingredient.copy(bakeryId = "bakery-other", name = "Updated B", updatedAt = Long.MAX_VALUE)))
        assertEquals("A ingredient", repo.ingredientDao.getIngredientByIdOnce(firstId)!!.name)
        assertEquals("Updated B", repo.getIngredientsForRecipeOnce(b).single().name)
    }
}
