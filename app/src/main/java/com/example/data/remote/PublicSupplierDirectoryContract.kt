package com.example.data.remote

/**
 * Contract for the isolated BatchBoss Suppliers directory.
 * Never populate this model from supplier Firestore documents or a supplier login.
 * The server must filter approvals, moderation, suspension and paid entitlement first.
 */
data class PublicSupplierListing(
    val businessName: String,
    val category: String,
    val city: String,
    val province: String,
    val description: String = "",
    val delivery: String = ""
)

/** Rejects private fields even if a backend accidentally includes them. */
object PublicSupplierDirectoryContract {
    private val publicFields = setOf(
        "businessName", "category", "city", "province", "description", "delivery"
    )
    private val categories = setOf("Ingredients", "Packaging", "Equipment", "Decorating")

    fun parse(raw: Map<String, Any?>): PublicSupplierListing? {
        // Fail closed on identity, billing or private metadata; never silently pass it to UI.
        if (!publicFields.containsAll(raw.keys)) return null
        val name = raw["businessName"] as? String ?: return null
        val category = raw["category"] as? String ?: return null
        val city = raw["city"] as? String ?: return null
        val province = raw["province"] as? String ?: return null
        if (name.isBlank() || city.isBlank() || province.isBlank() || category !in categories) return null
        if (name.length > 120 || city.length > 100 || province.length > 100) return null
        if (raw.values.any { it != null && it !is String }) return null
        if ((raw["description"] as? String)?.length ?: 0 > 1000) return null
        if ((raw["delivery"] as? String)?.length ?: 0 > 150) return null
        return PublicSupplierListing(
            name.trim(), category, city.trim(), province.trim(),
            (raw["description"] as? String).orEmpty(),
            (raw["delivery"] as? String).orEmpty()
        )
    }
}
