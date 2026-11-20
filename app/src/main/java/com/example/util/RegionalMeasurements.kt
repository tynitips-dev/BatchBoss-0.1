package com.example.util

/** Explicit measurement standards shared with the web foundation.
 * Legacy Android cup remains 240 ml and is never silently migrated.
 */
object RegionalMeasurements {
    private data class UnitSpec(val dimension: String, val factor: Double)
    private val units = mapOf(
        "g" to UnitSpec("mass", 1.0), "kg" to UnitSpec("mass", 1000.0),
        "oz" to UnitSpec("mass", 28.349523125), "lb" to UnitSpec("mass", 453.59237),
        "ml" to UnitSpec("volume", 1.0), "l" to UnitSpec("volume", 1000.0),
        "cup" to UnitSpec("volume", 240.0),
        "cup_us" to UnitSpec("volume", 236.5882365),
        "cup_metric" to UnitSpec("volume", 250.0),
        "fl_oz_us" to UnitSpec("volume", 29.5735295625),
        "fl_oz_uk" to UnitSpec("volume", 28.4130625),
        "tsp" to UnitSpec("volume", 5.0), "tbsp" to UnitSpec("volume", 15.0),
        "tsp_us" to UnitSpec("volume", 4.92892159375),
        "tbsp_us" to UnitSpec("volume", 14.78676478125),
        "tbsp_au" to UnitSpec("volume", 20.0)
    )
    fun convert(amount: Double, from: String, to: String): Double? {
        if (!amount.isFinite() || amount < 0) return null
        val source = units[from.trim().lowercase()] ?: return null
        val target = units[to.trim().lowercase()] ?: return null
        if (source.dimension != target.dimension) return null
        return (amount * source.factor / target.factor).takeIf { it.isFinite() }
    }

    fun convertTemperature(value: Double, from: String, to: String): Double? {
        if (!value.isFinite() || from !in setOf("C", "F") || to !in setOf("C", "F")) return null
        return (if (from == to) value else if (from == "C") value * 9 / 5 + 32
                else (value - 32) * 5 / 9).takeIf { it.isFinite() }
    }
}
