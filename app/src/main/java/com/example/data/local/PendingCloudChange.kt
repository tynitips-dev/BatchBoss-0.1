package com.example.data.local

import androidx.room.*

/** Stored in the same Room transaction as the business record; cleared only after acknowledgement. */
@Entity(tableName = "pending_cloud_changes", primaryKeys = ["bakeryId", "path"])
data class PendingCloudChange(
    val bakeryId: String,
    val path: String,
    val collection: String,
    val localId: Long,
    val revision: String,
    val ancestors: String = "",
    val expectedUpdatedAt: Long?,
    val deleted: Boolean = false
)

@Dao
interface PendingCloudChangeDao {
    @Query("SELECT * FROM pending_cloud_changes WHERE bakeryId = :bakeryId ORDER BY length(path), path")
    suspend fun list(bakeryId: String): List<PendingCloudChange>

    @Query("SELECT * FROM pending_cloud_changes WHERE bakeryId = :bakeryId AND path = :path")
    suspend fun get(bakeryId: String, path: String): PendingCloudChange?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(change: PendingCloudChange)

    @Query("DELETE FROM pending_cloud_changes WHERE bakeryId = :bakeryId AND path = :path AND revision = :revision")
    suspend fun acknowledge(bakeryId: String, path: String, revision: String)
}
