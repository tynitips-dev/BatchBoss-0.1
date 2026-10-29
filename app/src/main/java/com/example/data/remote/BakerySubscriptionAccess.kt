package com.example.data.remote

/** Same access rule as the baker website. Historical local Pro flags are not entitlements. */
object BakerySubscriptionAccess {
    const val MANAGEMENT_URL = "https://app.batchboss.co.za/?payment=subscription"
    fun active(status: String?, plan: String?, source: String?, expiresAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (status !in listOf("active", "trialing")) return false
        return if (expiresAtMillis != null) expiresAtMillis > nowMillis else plan != "promo" && source != "paystack"
    }
    fun matchesAccount(loggedIn: Boolean, expectedUid: String?, currentUid: String?, localEmail: String, authEmail: String?, localBakery: String, remoteBakery: String): Boolean =
        loggedIn && !expectedUid.isNullOrBlank() && expectedUid == currentUid &&
            localEmail.isNotBlank() && localEmail.equals(authEmail, ignoreCase = true) &&
            localBakery.isNotBlank() && localBakery == remoteBakery
}
