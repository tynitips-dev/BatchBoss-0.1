package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(
    tableName = "packaging_items",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["bakeryId"]),
        Index(value = ["category"]),
        Index(value = ["isLowStock"])
    ]
)
data class PackagingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val bakeryId: String = "bakery_1",
    val name: String,
    val category: String = "Cake boxes",
    val unit: String = "pcs",
    val packagePrice: Double = 0.0,
    val packageQuantity: Double = 1.0,
    val gramsPerUnit: Double = 0.0,
    val unitPrice: Double = 0.0, // Calculated cost per unit: packagePrice / packageQuantity
    val currentStock: Double = 0.0,
    val minStock: Double = 0.0,
    val isLowStock: Boolean = false,
    val alertEnabled: Boolean = true,
    val barcode: String = "",
    val supplier: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class InvoicePackagingItem(
    val id: String = UUID.randomUUID().toString(),
    val packagingId: Long = 0L,
    val name: String = "",
    val category: String = "Cake boxes",
    val unit: String = "pcs",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val lineTotal: Double = quantity * unitPrice
) {
    fun calculateLineTotal(): Double = quantity * unitPrice
}

object InvoicePackagingJsonUtil {
    fun toJson(items: List<InvoicePackagingItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("packagingId", item.packagingId)
            obj.put("name", item.name)
            obj.put("category", item.category)
            obj.put("unit", item.unit)
            obj.put("quantity", item.quantity)
            obj.put("unitPrice", item.unitPrice)
            obj.put("lineTotal", item.lineTotal)
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(json: String?): List<InvoicePackagingItem> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<InvoicePackagingItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val qty = obj.optDouble("quantity", 1.0)
                val price = obj.optDouble("unitPrice", 0.0)
                val total = if (obj.has("lineTotal")) obj.optDouble("lineTotal") else qty * price
                list.add(
                    InvoicePackagingItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        packagingId = obj.optLong("packagingId", 0L),
                        name = obj.optString("name", ""),
                        category = obj.optString("category", "Cake boxes"),
                        unit = obj.optString("unit", "pcs"),
                        quantity = qty,
                        unitPrice = price,
                        lineTotal = total
                    )
                )
            }
        } catch (_: Exception) {
            // gracefully fallback to empty list
        }
        return list
    }
}
