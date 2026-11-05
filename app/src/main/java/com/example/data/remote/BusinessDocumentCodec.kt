package com.example.data.remote

import com.example.data.local.LineItemJsonUtil
import org.json.JSONArray
import org.json.JSONObject

/** Accept both existing Android and website document shapes without recalculating stored totals. */
object BusinessDocumentCodec {
    fun lines(raw: Any?): String {
        val input = when (raw) {
            is String -> if (raw.isBlank()) JSONArray() else JSONArray(raw)
            is List<*> -> JSONArray(raw.map { if (it is Map<*, *>) JSONObject(it) else it })
            else -> JSONArray()
        }
        val output = JSONArray()
        for (i in 0 until input.length()) {
            val row = input.getJSONObject(i)
            if (!row.has("itemName")) row.put("itemName", row.optString("name"))
            if (!row.has("lineTotal")) row.put("lineTotal", row.optDouble("quantity", 1.0) * row.optDouble("unitPrice", 0.0) - row.optDouble("discount", 0.0))
            // Website IDs are strings, often UUIDs. They must not become local product ID zero.
            if (row.has("productId")) {
                val id = row.optString("productId").toLongOrNull()
                if (id == null) row.remove("productId") else row.put("productId", id)
            }
            output.put(row)
        }
        return output.toString()
    }

    fun webLines(json: String): List<Map<String, Any?>> = LineItemJsonUtil.fromJson(json).map {
        mapOf("name" to it.itemName, "itemName" to it.itemName, "productId" to it.productId?.toString(),
            "description" to it.description, "quantity" to it.quantity, "unit" to it.unit,
            "unitPrice" to it.unitPrice, "discount" to it.discount, "costPrice" to it.costPrice,
            "lineTotal" to it.lineTotal)
    }
}
