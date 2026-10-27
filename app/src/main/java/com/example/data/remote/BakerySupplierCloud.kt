package com.example.data.remote

import com.example.data.local.SupplierEntity
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source

internal enum class SupplierWrite { UPDATE_FAVOURITE, CREATE, DELETED }
internal fun supplierWrite(exists: Boolean, confirmed: Boolean): SupplierWrite = when {
    exists -> SupplierWrite.UPDATE_FAVOURITE
    confirmed -> SupplierWrite.DELETED
    else -> SupplierWrite.CREATE
}

/** Baker-owned saved suppliers use the main Firebase project, never the supplier project's private records. */
object BakerySupplierCloud {
    fun directoryDocumentId(id: String): String {
        require(PublicSupplierDirectory.validId(id)) { "Invalid public supplier." }
        return "directory_" + id
    }
    private fun suppliers(bakeryId: String) = run {
        require(bakeryId.isNotBlank() && '/' !in bakeryId) { "A bakery workspace is required." }
        check(FirebaseService.auth?.currentUser != null) { "Sign in before syncing suppliers." }
        val db = FirebaseService.firestore ?: error("Firebase is not configured.")
        db.collection("bakeries").document(bakeryId).collection("suppliers")
    }
    internal fun decode(id: String, data: Map<String, Any>): SupplierEntity {
        fun text(key: String, fallback: String = "") = (data[key] as? String ?: fallback).take(2000)
        return SupplierEntity(
            name = text("name"), categories = text("categories", text("category")),
            rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
            reviewCount = (data["reviewCount"] as? Number)?.toInt() ?: 0,
            openingHours = text("openingHours"), phone = text("phone"), email = text("email"), website = text("website"),
            about = text("description", text("about")), isFavorite = data["isFavorite"] as? Boolean ?: false,
            isMySupplier = data["isMySupplier"] as? Boolean ?: true, cloudDocumentId = id,
            publicListingId = text("publicListingId"), city = text("city"), province = text("province"), cloudConfirmed = true, pendingSync = false
        )
    }
    suspend fun fetch(bakeryId: String): List<SupplierEntity> = suppliers(bakeryId).get(Source.SERVER).awaitTask().documents.mapNotNull { doc ->
        doc.data?.let { decode(doc.id, it) }
    }
    suspend fun save(bakeryId: String, supplier: SupplierEntity): Boolean {
        require(supplier.cloudDocumentId.isNotBlank() && '/' !in supplier.cloudDocumentId)
        return writeSupplier(suppliers(bakeryId).document(supplier.cloudDocumentId), supplier).awaitTask()
    }
    /** The same transaction is exercised against the local Firestore emulator in CI. */
    internal fun writeSupplier(target: DocumentReference, supplier: SupplierEntity): Task<Boolean> {
        val data = mapOf(
            "name" to supplier.name, "description" to supplier.about, "about" to supplier.about,
            "categories" to supplier.categories, "phone" to supplier.phone, "email" to supplier.email,
            "website" to supplier.website, "openingHours" to supplier.openingHours, "rating" to supplier.rating,
            "reviewCount" to supplier.reviewCount, "isFavorite" to supplier.isFavorite, "isMySupplier" to supplier.isMySupplier,
            "publicListingId" to supplier.publicListingId, "city" to supplier.city, "province" to supplier.province,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return target.firestore.runTransaction { transaction ->
            val existing = transaction.get(target)
            when (supplierWrite(existing.exists(), supplier.cloudConfirmed)) {
                // The phone currently edits only the favourite flag for existing suppliers.
                // Never overwrite details that may have changed since the initial fetch.
                SupplierWrite.UPDATE_FAVOURITE -> {
                    transaction.update(target, mapOf("isFavorite" to supplier.isFavorite, "updatedAt" to FieldValue.serverTimestamp()))
                    true
                }
                // A website deletion must not be undone by a stale phone write.
                SupplierWrite.DELETED -> false
                SupplierWrite.CREATE -> { transaction.set(target, data); true }
            }
        }
    }
    suspend fun saveDirectory(bakeryId: String, supplier: PublicSupplier): SupplierEntity {
        require(PublicSupplierDirectory.validId(supplier.id)) { "Invalid public supplier." }
        val target = suppliers(bakeryId).document(directoryDocumentId(supplier.id))
        val db = FirebaseService.firestore ?: error("Firebase is not configured.")
        return db.runTransaction { transaction ->
            val existing = transaction.get(target)
            if (existing.exists()) decode(existing.id, existing.data.orEmpty()) else {
                val data = mapOf(
                    "name" to supplier.businessName, "description" to supplier.description, "about" to supplier.description,
                    "categories" to supplier.category, "city" to supplier.city, "province" to supplier.province,
                    "delivery" to supplier.delivery, "publicListingId" to supplier.id, "isMySupplier" to true,
                    "isFavorite" to false, "phone" to "", "email" to "", "website" to "", "openingHours" to "",
                    "rating" to 0.0, "reviewCount" to 0, "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(target, data)
                decode(target.id, data)
            }
        }.awaitTask()
    }
}
