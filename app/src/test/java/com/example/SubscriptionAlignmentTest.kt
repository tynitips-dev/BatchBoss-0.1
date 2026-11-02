package com.example

import com.example.data.remote.BakerySubscriptionAccess
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SubscriptionAlignmentTest {
    @Test fun `Android follows the same subscription cases as the baker website`() {
        val text=javaClass.classLoader!!.getResourceAsStream("bakery-subscription-cases.json")!!.bufferedReader().use { it.readText() }
        val cases=JSONArray(text)
        for(index in 0 until cases.length()) {
            val row=cases.getJSONObject(index)
            fun optional(key:String)=if(row.isNull(key))null else row.getString(key)
            val expiry=if(row.isNull("expiry"))null else row.getLong("expiry")
            assertEquals(row.getString("name"),row.getBoolean("expected"),BakerySubscriptionAccess.active(optional("status"),optional("plan"),optional("source"),expiry,row.getLong("now")))
        }
    }
    @Test fun `expired access changes without a login or local profile rewrite`() {
        assertTrue(BakerySubscriptionAccess.active("active","monthly","paystack",2000,1999))
        assertFalse(BakerySubscriptionAccess.active("active","monthly","paystack",2000,2000))
    }
    @Test fun `a different auth account or bakery cannot inherit cached premium access`() {
        assertTrue(BakerySubscriptionAccess.matchesAccount(true,"first","first","owner@example.test","OWNER@example.test","bakery-a","bakery-a"))
        assertFalse(BakerySubscriptionAccess.matchesAccount(false,"first","first","owner@example.test","owner@example.test","bakery-a","bakery-a"))
        assertFalse(BakerySubscriptionAccess.matchesAccount(true,"first","second","owner@example.test","owner@example.test","bakery-a","bakery-a"))
        assertFalse(BakerySubscriptionAccess.matchesAccount(true,"first","first","other@example.test","owner@example.test","bakery-a","bakery-a"))
        assertFalse(BakerySubscriptionAccess.matchesAccount(true,"first","first","owner@example.test","owner@example.test","bakery-a","bakery-b"))
        assertFalse(BakerySubscriptionAccess.matchesAccount(true,null,null,"owner@example.test",null,"bakery-a","bakery-a"))
    }
    @Test fun `subscription actions open the baker website not a local unlock`() {
        assertEquals("https://app.batchboss.co.za/?payment=subscription",BakerySubscriptionAccess.MANAGEMENT_URL)
    }
}
