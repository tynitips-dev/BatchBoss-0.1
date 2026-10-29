package com.example.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

data class LegacySupplierRecoveryState(val unassignedCount: Int, val canRecover: Boolean)

/** No supplier names are exposed before ownership is established. Shared-device cases require review. */
object LegacySupplierRecovery {
    private suspend fun state(database: AppDatabase, userId: Long, bakeryId: String): LegacySupplierRecoveryState {
        val count = database.supplierDao().countUnassignedLegacy()
        val accounts = database.userAccountDao().getAllUsers().first()
        val sequence = database.openHelper.readableDatabase.query("SELECT seq FROM sqlite_sequence WHERE name = 'user_accounts'").use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        // A deleted second account must still disqualify automatic single-owner recovery.
        val soleOriginalAccount = userId == 1L && sequence == 1L && accounts.size == 1
            && accounts.single().id == userId && bakeryId.isNotBlank() && accounts.single().bakeryId == bakeryId
        return LegacySupplierRecoveryState(count, count > 0 && soleOriginalAccount)
    }
    suspend fun inspect(database: AppDatabase, userId: Long, bakeryId: String): LegacySupplierRecoveryState =
        database.withTransaction { state(database, userId, bakeryId) }

    suspend fun recover(database: AppDatabase, userId: Long, bakeryId: String, confirmedOwnership: Boolean): Int {
        require(confirmedOwnership) { "Confirm ownership before recovering older suppliers." }
        return database.withTransaction {
            check(state(database, userId, bakeryId).canRecover) { "These records need an ownership review before recovery." }
            database.supplierDao().recoverUnassignedLegacy(userId, bakeryId)
        }
    }
}
