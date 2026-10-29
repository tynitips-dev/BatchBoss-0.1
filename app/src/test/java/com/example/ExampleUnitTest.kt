package com.example

import com.example.data.remote.FirebaseLoginResult
import com.example.data.remote.FirebaseService
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BatchBoss authentication and Firebase workspace mapping.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testFirebasePathsUseExactBakeryId() {
        val testBakeryId = "bakery_cape_artisan_2"
        FirebaseService.currentBakeryId = testBakeryId

        assertEquals("bakeries/bakery_cape_artisan_2/packaging", FirebaseService.getPackagingPath())
        assertEquals("bakeries/bakery_cape_artisan_2/inventory", FirebaseService.getInventoryPath())
        assertEquals("bakeries/bakery_cape_artisan_2/recipes", FirebaseService.getRecipesPath())
        assertEquals("bakeries/bakery_cape_artisan_2/customers", FirebaseService.getCustomersPath())
        assertEquals("bakeries/bakery_cape_artisan_2/products", FirebaseService.getProductsPath())
        assertEquals("bakeries/bakery_cape_artisan_2/invoices", FirebaseService.getInvoicesPath())
        assertEquals("bakeries/bakery_cape_artisan_2/quotes", FirebaseService.getQuotesPath())
        assertEquals("bakeries/bakery_cape_artisan_2/orders", FirebaseService.getOrdersPath())
        assertEquals("bakeries/bakery_cape_artisan_2/tasks", FirebaseService.getTasksPath())
    }

    @Test
    fun testFirebaseLoginResultProEvaluation() {
        val proResult = FirebaseLoginResult(
            success = true,
            bakeryId = "bakery_pro_1",
            subscriptionStatus = "active",
            subscriptionPlan = "Pro Commercial",
            isPro = true
        )
        assertTrue(proResult.isPro)
        assertEquals("bakery_pro_1", proResult.bakeryId)

        val freeResult = FirebaseLoginResult(
            success = true,
            bakeryId = "bakery_free_1",
            subscriptionStatus = "free",
            subscriptionPlan = "Free Tier",
            isPro = false
        )
        assertFalse(freeResult.isPro)
    }
}
