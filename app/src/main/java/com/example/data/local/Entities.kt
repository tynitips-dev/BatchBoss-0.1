package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val overheadsCost: Double = 0.0, // Set to 0.0 ("no overheads" requested)
    val packagingCost: Double = 0.0,
    val utilitiesCost: Double = 0.0,
    val profitMarginPercent: Double,
    val isFavorite: Boolean = false,
    val customSellingPrice: Double = 0.0,
    val laborHours: Double = 1.5,
    val laborRatePerHour: Double = 120.0
)

@Entity(
    tableName = "recipe_ingredients",
    indices = [Index(value = ["recipeId"])]
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val name: String,
    val quantity: Double,
    val unit: String,
    val cost: Double
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val orderRef: String,
    val dueTime: String,
    val priority: String, // "High", "Medium", "Low"
    val status: String, // "Pending", "InProgress", "Completed"
    val dayOfWeek: String = "Wed",
    val dueDate: String = "" // "yyyy-MM-dd" e.g. "2026-09-14"
)

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currentStock: Double,
    val minStock: Double,
    val unit: String,
    val isLowStock: Boolean,
    val alertEnabled: Boolean = true,
    val unitPrice: Double = 0.0, // calculated cost per gram or per unit
    val packagePrice: Double = 0.0, // e.g. R55.00 for package
    val gramsPerUnit: Double = 1000.0, // e.g. 2500g in that package
    val category: String = "Baking Staples" // Flour, Sugar, Dairy, Chocolate, etc.
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val type: String, // "Alerts", "Orders", "System"
    val timeLabel: String,
    val isUnread: Boolean = true,
    val dateGroup: String = "Today" // "Today", "Yesterday"
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val clientName: String,
    val clientPhone: String,
    val orderDescription: String,
    val issueDate: String,
    val dueDate: String,
    val amount: Double,
    val status: String = "Pending" // "Paid", "Pending", "Overdue"
)

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quoteNumber: String,
    val clientName: String,
    val clientPhone: String,
    val eventType: String, // "Wedding", "Birthday", "Corporate", "Custom Cake"
    val eventDate: String,
    val recipeOrItemName: String,
    val estimatedCost: Double,
    val profitMarginPercent: Double,
    val quotedPrice: Double,
    val status: String = "Sent" // "Draft", "Sent", "Accepted", "Declined"
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: Long = 1,
    val fullName: String = "Tyne Jenkins",
    val bakeryName: String = "Tyne's Artisan Bakery",
    val specialty: String = "Cakes & Pastries",
    val phone: String = "+27 82 555 1234",
    val city: String = "Cape Town",
    val operatingModel: String = "Home Kitchen",
    val currency: String = "ZAR (R)",
    val email: String = "tyne@batchboss.com",
    val isPremium: Boolean = false,
    val subscriptionPlan: String = "Free",
    val logoUri: String = "",
    val bankName: String = "First National Bank (FNB)",
    val accountNumber: String = "62890123456",
    val branchCode: String = "250655",
    val vatNumber: String = "ZA48910293",
    val address: String = "12 Main Road, Cape Town",
    val defaultHourlyRate: Double = 120.0
)

