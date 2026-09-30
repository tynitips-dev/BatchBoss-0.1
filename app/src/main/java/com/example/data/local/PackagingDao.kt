package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PackagingDao {
    @Query("SELECT * FROM packaging_items ORDER BY isLowStock DESC, category ASC, name ASC")
    fun getAllPackaging(): Flow<List<PackagingItemEntity>>

    @Query("SELECT * FROM packaging_items WHERE (userId = :userId OR userId = 0) ORDER BY isLowStock DESC, category ASC, name ASC")
    fun getPackagingByUser(userId: Long): Flow<List<PackagingItemEntity>>

    @Query("SELECT * FROM packaging_items WHERE bakeryId = :bakeryId ORDER BY isLowStock DESC, category ASC, name ASC")
    fun getPackagingByBakery(bakeryId: String): Flow<List<PackagingItemEntity>>

    @Query("SELECT * FROM packaging_items WHERE isLowStock = 1 ORDER BY name ASC")
    fun getLowStockPackaging(): Flow<List<PackagingItemEntity>>

    @Query("SELECT * FROM packaging_items WHERE (userId = :userId OR userId = 0) AND isLowStock = 1 ORDER BY name ASC")
    fun getLowStockPackagingByUser(userId: Long): Flow<List<PackagingItemEntity>>

    @Query("SELECT * FROM packaging_items WHERE id = :id")
    fun getPackagingById(id: Long): Flow<PackagingItemEntity?>

    @Query("SELECT * FROM packaging_items WHERE id = :id")
    suspend fun getPackagingByIdOnce(id: Long): PackagingItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackaging(item: PackagingItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackagingList(items: List<PackagingItemEntity>)

    @Update
    suspend fun updatePackaging(item: PackagingItemEntity)

    @Query("""
        UPDATE packaging_items 
        SET packagePrice = :packagePrice, 
            packageQuantity = :packageQuantity, 
            unitPrice = :unitPrice, 
            currentStock = :currentStock, 
            minStock = :minStock, 
            isLowStock = :isLowStock,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateStockAndPrice(
        id: Long,
        packagePrice: Double,
        packageQuantity: Double,
        unitPrice: Double,
        currentStock: Double,
        minStock: Double,
        isLowStock: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE packaging_items SET alertEnabled = NOT alertEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleAlert(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM packaging_items WHERE id = :id")
    suspend fun deletePackaging(id: Long)

    @Query("DELETE FROM packaging_items WHERE bakeryId = :bakeryId")
    suspend fun deletePackagingByBakery(bakeryId: String)
}
