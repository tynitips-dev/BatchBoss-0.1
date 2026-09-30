package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.RecipeEntity
import com.example.data.local.UserProfileEntity
import com.example.data.local.generateBakeryId
import com.example.data.remote.WebSyncService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        // Sync with local backend
        val syncSuccess = WebSyncService.syncCustomer(testBakeryId, customer)
        assertTrue("Customer should sync successfully to web backend", syncSuccess)

        // Verify customer appears on website backend
        val fetchedCustomers = WebSyncService.fetchBakeryCustomers(testBakeryId)
        assertTrue(fetchedCustomers.any { it.name == "John Connor" && it.bakeryId == testBakeryId })
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

        // Sync with local backend
        val syncSuccess = WebSyncService.syncRecipe(testBakeryId, recipe)
        assertTrue("Recipe should sync successfully to web backend", syncSuccess)

        // Verify recipe appears on website backend
        val fetchedRecipes = WebSyncService.fetchBakeryRecipes(testBakeryId)
        assertTrue(fetchedRecipes.any { it.name == "Golden Butter Croissant" && it.bakeryId == testBakeryId })
    }
}
