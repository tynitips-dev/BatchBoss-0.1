package com.example.data.remote

import com.example.data.local.SupplierEntity
import java.util.UUID

data class SupplierSyncPlan(val imports: List<SupplierEntity>, val deletes: List<Long>, val uploads: List<SupplierEntity>)

/** Plans an authoritative server refresh without crossing accounts or reviving a deleted supplier. */
object SupplierSyncPlanner {
    fun plan(local: List<SupplierEntity>, remote: List<SupplierEntity>, userId: Long, bakeryId: String,
        newDocumentId: () -> String = { UUID.randomUUID().toString() }): SupplierSyncPlan {
        require(userId > 0 && bakeryId.isNotBlank())
        require(local.all { it.userId == userId && (it.bakeryId.isBlank() || it.bakeryId == bakeryId) })
        require(remote.all { it.cloudDocumentId.isNotBlank() && '/' !in it.cloudDocumentId })
        require(remote.map { it.cloudDocumentId }.distinct().size == remote.size)
        val byCloudId = remote.associateBy { it.cloudDocumentId }
        val imports = remote.mapNotNull { record ->
            val existing = local.firstOrNull { it.cloudDocumentId == record.cloudDocumentId && it.bakeryId == bakeryId }
            if (existing?.pendingSync == true) null else record.copy(id = existing?.id ?: 0, userId = userId, bakeryId = bakeryId)
        }
        val deleted = local.filter { it.cloudConfirmed && it.cloudDocumentId.isNotBlank() && it.cloudDocumentId !in byCloudId }.map { it.id }
        val uploads = local.filter { it.id !in deleted && (it.pendingSync || it.cloudDocumentId.isBlank()) }.map { record ->
            val fresh = byCloudId[record.cloudDocumentId]
            val content = fresh?.copy(id = record.id, userId = userId, isFavorite = record.isFavorite) ?: record
            content.copy(bakeryId = bakeryId, cloudDocumentId = record.cloudDocumentId.ifBlank { newDocumentId() }, pendingSync = true)
        }
        return SupplierSyncPlan(imports, deleted, uploads)
    }
}
