package com.example.util

object UnitUtils {
    val RECIPE_UNITS = listOf("g", "kg", "ml", "tsp", "tbsp", "cup", "l", "unit")

    /**
     * Converts a given quantity and unit to base grams (or ml for liquids, where 1ml ~ 1g).
     * Supported: g, kg, ml, tsp, tbsp / tble, l, cup, unit / eggs
     */
    fun toBaseGrams(quantity: Double, unit: String): Double {
        return when (unit.lowercase().trim()) {
            "g", "gram", "grams" -> quantity
            "kg", "kilogram", "kilograms" -> quantity * 1000.0
            "ml", "milliliter", "milliliters" -> quantity
            "l", "liter", "litres", "liters" -> quantity * 1000.0
            "tsp", "teaspoon", "teaspoons" -> quantity * 5.0
            "tbsp", "tble", "tablespoon", "tablespoons" -> quantity * 15.0
            "cup", "cups" -> quantity * 240.0
            "pinch" -> quantity * 0.5
            else -> quantity // unit, egg, bottle, pcs
        }
    }

    /**
     * Calculates the ingredient cost given:
     * - quantity in recipe unit (e.g. 2.0 tbsp)
     * - unit (e.g. "tbsp")
     * - inventory unit price per base gram/unit (e.g. R0.022/g)
     */
    fun calculateCost(quantity: Double, unit: String, pricePerBaseGram: Double): Double {
        val baseGrams = toBaseGrams(quantity, unit)
        return baseGrams * pricePerBaseGram
    }

    /**
     * Estimates cost per gram/unit for common bakery ingredients for auto-cost calculation
     */
    fun getEstimatedRatePerGram(name: String): Double {
        val lower = name.lowercase().trim()
        return when {
            "vanilla" in lower -> 0.35 // R0.35 per ml (~R1.75 per tsp)
            "flour" in lower -> 0.022 // R22/kg (~R0.022/g)
            "sugar" in lower || "icing" in lower || "castor" in lower -> 0.024 // R24/kg (~R0.024/g)
            "butter" in lower || "margarine" in lower -> 0.14 // R140/kg (~R0.14/g)
            "egg" in lower -> 2.25 // R2.25 per egg unit
            "cocoa" in lower -> 0.18 // R180/kg
            "chocolate" in lower -> 0.16 // R160/kg
            "baking powder" in lower || "baking soda" in lower || "yeast" in lower -> 0.08
            "milk" in lower || "cream" in lower -> 0.018 // R18/L (~R0.018/ml)
            "oil" in lower -> 0.035
            "salt" in lower -> 0.005
            "cinnamon" in lower || "spice" in lower -> 0.15
            else -> 0.03
        }
    }

    /**
     * Formats ingredient display with readable unit and quantity
     */
    fun formatQuantity(quantity: Double, unit: String): String {
        val qtyStr = if (quantity == quantity.toInt().toDouble()) {
            quantity.toInt().toString()
        } else {
            String.format("%.2f", quantity).trimEnd('0').trimEnd('.')
        }
        return "$qtyStr $unit"
    }
}
