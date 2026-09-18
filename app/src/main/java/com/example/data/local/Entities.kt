package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String = "",
    val surname: String = "",
    val email: String = "",
    val passwordHash: String = "",
    val bakeryName: String = "",
    val phone: String = "",
    val city: String = "Cape Town",
    val operatingModel: String = "Home Kitchen",
    val currency: String = "ZAR (R)",
    val specialty: String = "Cakes & Pastries",
    val isPremium: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val fullName: String
        get() = "$firstName $surname".trim().ifBlank {
            firstName.ifBlank { surname.ifBlank { "Baker" } }
        }
}

@Entity(
    tableName = "customers",
    indices = [Index(value = ["userId"])]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val totalOrders: Int = 0,
    val totalSpend: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "orders",
    indices = [Index(value = ["userId"])]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val orderNumber: String,
    val customerName: String,
    val customerPhone: String = "",
    val description: String,
    val dueDate: String,
    val totalAmount: Double,
    val totalCost: Double = 0.0,
    val status: String = "Pending", // "Pending", "In Progress", "Completed", "Cancelled"
    val recipeOrItemName: String = "",
    val quantity: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recipes",
    indices = [Index(value = ["userId"])]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val name: String,
    val category: String,
    val description: String,
    val servings: Int,
    val batchSize: Int,
    val difficulty: String,
    val rating: Double,
    val reviewCount: Int,
    val imageResName: String,
    val labourCost: Double,
    val overheadsCost: Double = 0.0,
    val packagingCost: Double = 0.0,
    val utilitiesCost: Double = 0.0,
    val profitMarginPercent: Double,
    val isFavorite: Boolean = false,
    val customSellingPrice: Double = 0.0,
    val laborHours: Double = 1.5,
    val laborRatePerHour: Double = 120.0,
    val instructions: String = "",
    val photoUri: String = ""
)

@Entity(
    tableName = "recipe_ingredients",
    indices = [Index(value = ["recipeId"]), Index(value = ["userId"])]
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val userId: Long = 0,
    val name: String,
    val quantity: Double,
    val unit: String,
    val cost: Double
)

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["userId"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val title: String,
    val orderRef: String,
    val dueTime: String,
    val priority: String, // "High", "Medium", "Low"
    val status: String, // "Pending", "InProgress", "Completed"
    val dayOfWeek: String = "Wed",
    val dueDate: String = "" // "yyyy-MM-dd"
)

@Entity(
    tableName = "inventory_items",
    indices = [Index(value = ["userId"])]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val name: String,
    val currentStock: Double,
    val minStock: Double,
    val unit: String,
    val isLowStock: Boolean = false,
    val alertEnabled: Boolean = true,
    val unitPrice: Double = 0.0,
    val packagePrice: Double = 0.0,
    val gramsPerUnit: Double = 1000.0,
    val category: String = "Baking Staples",
    val barcode: String = ""
)

@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["userId"])]
)
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val name: String,
    val categories: String,
    val rating: Double,
    val reviewCount: Int,
    val openingHours: String,
    val phone: String,
    val email: String,
    val website: String,
    val about: String,
    val isFavorite: Boolean = false,
    val isMySupplier: Boolean = true
)

@Entity(tableName = "special_deals")
data class SpecialDealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val productName: String,
    val currentPrice: Double,
    val originalPrice: Double,
    val discountPercent: Int,
    val validUntil: String,
    val description: String,
    val terms: String,
    val isUpcoming: Boolean = false
)

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["userId"])]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val title: String,
    val message: String,
    val type: String, // "Alerts", "Orders", "System"
    val timeLabel: String,
    val isUnread: Boolean = true,
    val dateGroup: String = "Today"
)

@Entity(
    tableName = "products_services",
    indices = [Index(value = ["userId"])]
)
data class ProductServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val name: String,
    val description: String = "",
    val category: String = "Cakes",
    val imageUrl: String = "",
    val sku: String = "",
    val costPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val unit: String = "Each",
    val isActive: Boolean = true,
    val isService: Boolean = false,
    val linkedRecipeId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "product_price_history",
    indices = [Index(value = ["productId"]), Index(value = ["userId"])]
)
data class ProductPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val userId: Long = 0,
    val previousPrice: Double,
    val newPrice: Double,
    val dateChanged: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "document_line_items",
    indices = [Index(value = ["documentType", "documentId"]), Index(value = ["userId"])]
)
data class DocumentLineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentType: String, // "INVOICE", "QUOTE", "ESTIMATE"
    val documentId: Long,
    val userId: Long = 0,
    val productId: Long? = null,
    val itemName: String,
    val description: String = "",
    val quantity: Double = 1.0,
    val unit: String = "Each",
    val unitPrice: Double = 0.0,
    val discount: Double = 0.0,
    val costPrice: Double = 0.0,
    val lineTotal: Double = 0.0
)

data class LineItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val productId: Long? = null,
    val itemName: String = "",
    val description: String = "",
    val quantity: Double = 1.0,
    val unit: String = "Each",
    val unitPrice: Double = 0.0,
    val discount: Double = 0.0,
    val costPrice: Double = 0.0,
    val lineTotal: Double = 0.0
) {
    fun calculateLineTotal(): Double {
        val gross = quantity * unitPrice
        return (gross - discount).coerceAtLeast(0.0)
    }
}

object LineItemJsonUtil {
    fun toJson(items: List<LineItem>): String {
        val array = org.json.JSONArray()
        for (item in items) {
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            if (item.productId != null) obj.put("productId", item.productId)
            obj.put("itemName", item.itemName)
            obj.put("description", item.description)
            obj.put("quantity", item.quantity)
            obj.put("unit", item.unit)
            obj.put("unitPrice", item.unitPrice)
            obj.put("discount", item.discount)
            obj.put("costPrice", item.costPrice)
            obj.put("lineTotal", item.lineTotal)
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(json: String?): List<LineItem> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<LineItem>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LineItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        productId = if (obj.has("productId")) obj.optLong("productId") else null,
                        itemName = obj.optString("itemName", ""),
                        description = obj.optString("description", ""),
                        quantity = obj.optDouble("quantity", 1.0),
                        unit = obj.optString("unit", "Each"),
                        unitPrice = obj.optDouble("unitPrice", 0.0),
                        discount = obj.optDouble("discount", 0.0),
                        costPrice = obj.optDouble("costPrice", 0.0),
                        lineTotal = obj.optDouble("lineTotal", 0.0)
                    )
                )
            }
        } catch (e: Exception) {
            // fallback gracefully
        }
        return list
    }
}

@Entity(
    tableName = "invoices",
    indices = [Index(value = ["userId"])]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val invoiceNumber: String,
    val clientName: String,
    val clientPhone: String,
    val orderDescription: String,
    val issueDate: String,
    val dueDate: String,
    val amount: Double,
    val status: String = "Pending", // "Paid", "Pending", "Overdue"
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxRatePercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val lineItemsJson: String = "",
    val totalCost: Double = 0.0
) {
    val items: List<LineItem>
        get() = LineItemJsonUtil.fromJson(lineItemsJson)
}

@Entity(
    tableName = "quotes",
    indices = [Index(value = ["userId"])]
)
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val quoteNumber: String,
    val clientName: String,
    val clientPhone: String,
    val eventType: String,
    val eventDate: String,
    val recipeOrItemName: String,
    val estimatedCost: Double,
    val profitMarginPercent: Double,
    val quotedPrice: Double,
    val status: String = "Sent", // "Draft", "Sent", "Accepted", "Declined"
    val docType: String = "Quote", // "Quote" or "Estimate"
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxRatePercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val lineItemsJson: String = "",
    val totalCost: Double = 0.0
) {
    val items: List<LineItem>
        get() = LineItemJsonUtil.fromJson(lineItemsJson)
}

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: Long = 1,
    val userId: Long = 0,
    val fullName: String = "",
    val bakeryName: String = "",
    val specialty: String = "Cakes & Pastries",
    val phone: String = "",
    val city: String = "Cape Town",
    val operatingModel: String = "Home Kitchen",
    val currency: String = "ZAR (R)",
    val email: String = "",
    val isPremium: Boolean = false,
    val subscriptionPlan: String = "Free",
    val logoUri: String = "",
    val bankName: String = "First National Bank (FNB)",
    val accountNumber: String = "",
    val branchCode: String = "",
    val vatNumber: String = "",
    val address: String = "",
    val defaultHourlyRate: Double = 120.0
)

@Entity(tableName = "baking_supply_stores")
data class BakingSupplyStoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val name: String,
    val category: String,
    val address: String,
    val city: String,
    val distanceKm: Double,
    val rating: Double,
    val reviewCount: Int,
    val openingHours: String,
    val phone: String,
    val website: String = "",
    val specialties: String,
    val inStockHighlights: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val hasDelivery: Boolean = true,
    val hasPickup: Boolean = true,
    val isFavorite: Boolean = false,
    val isCustomAdded: Boolean = false
)

@Entity(
    tableName = "user_login_logs",
    indices = [Index(value = ["userId"]), Index(value = ["timestamp"])]
)
data class UserLoginLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val email: String = "",
    val bakeryName: String = "",
    val action: String = "LOGIN", // LOGIN, REGISTER, LOGOUT, DATA_DELETION_REQUEST
    val branch: String = "Main Flagship",
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "data_deletion_requests",
    indices = [Index(value = ["userId"]), Index(value = ["status"])]
)
data class DataDeletionRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 0,
    val email: String = "",
    val bakeryName: String = "",
    val reason: String = "User requested account & data wipe",
    val requestedAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // PENDING, COMPLETED, REJECTED
    val completedAt: Long? = null
)
