package com.example.data.remote

import org.junit.Assert.*
import org.junit.Test

class PublicSupplierDirectoryContractTest {
    private val safe = mapOf<String, Any?>(
        "businessName" to "Fixture Baking Supplies",
        "category" to "Packaging",
        "city" to "Johannesburg",
        "province" to "Gauteng",
        "description" to "",
        "delivery" to ""
    )

    @Test fun acceptsApprovedPublicShape() {
        val listing = PublicSupplierDirectoryContract.parse(safe)
        assertEquals("Fixture Baking Supplies", listing?.businessName)
        assertEquals("Packaging", listing?.category)
    }

    @Test fun rejectsInternalIdsAndPrivateFields() {
        for (field in listOf("id", "uid", "supplierId", "email", "contactEmail",
            "phoneNumber", "amountCents", "entitlement", "status", "paymentReference")) {
            assertNull(field, PublicSupplierDirectoryContract.parse(safe + (field to "secret")))
        }
    }

    @Test fun rejectsInvalidCategoryAndIncompleteProfiles() {
        assertNull(PublicSupplierDirectoryContract.parse(safe + ("category" to "Unmoderated")))
        assertNull(PublicSupplierDirectoryContract.parse(safe + ("businessName" to "")))
        assertNull(PublicSupplierDirectoryContract.parse(safe - "province"))
        assertNull(PublicSupplierDirectoryContract.parse(safe - "description"))
        assertNull(PublicSupplierDirectoryContract.parse(safe + ("delivery" to null)))
    }

    @Test fun rejectsOversizedPublicFields() {
        assertNull(PublicSupplierDirectoryContract.parse(safe + ("businessName" to "B".repeat(121))))
        assertNull(PublicSupplierDirectoryContract.parse(safe + ("city" to "C".repeat(101))))
        assertNull(PublicSupplierDirectoryContract.parse(safe + ("description" to "D".repeat(1001))))
        assertNull(PublicSupplierDirectoryContract.parse(safe + ("delivery" to "E".repeat(151))))
    }
}
