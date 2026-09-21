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
            "flour" in lower -> 35.00 / 2500.0 // Checkers Housebrand Cake Flour R35.00 for 2.5kg (~R0.014/g)
            "sugar" in lower || "castor" in lower || "icing" in lower -> 38.50 / 2000.0 // Checkers White Sugar R38.50 for 2kg (~R0.019/g)
            "chocolate" in lower || "cocoa" in lower -> 42.00 / 200.0 // Checkers Baking Chocolate R42.00 for 200g (~R0.21/g)
            "baking soda" in lower -> 18.00 / 200.0 // Checkers Baking Soda R18.00 for 200g (~R0.09/g)
            "bicarbonate" in lower -> 16.50 / 200.0 // Checkers Bicarbonate of Soda R16.50 for 200g (~R0.0825/g)
            "salt" in lower -> 12.00 / 500.0 // Checkers Cerebos Table Salt R12.00 for 500g (~R0.024/g)
            "egg" in lower -> 68.00 / 30.0 // Checkers Farm Fresh Eggs R68.00 for 30-pack (~R2.27 / egg)
            "vanilla" in lower -> 0.35 // R0.35 per ml (~R1.75 per tsp)
            "butter" in lower || "margarine" in lower -> 0.14 // R140/kg (~R0.14/g)
            "baking powder" in lower || "yeast" in lower -> 0.08
            "milk" in lower || "cream" in lower -> 0.018 // R18/L (~R0.018/ml)
            "oil" in lower -> 0.035
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

    /**
     * Converts an ingredient to Cups & Spoons volume measurement
     */
    fun convertToCups(quantity: Double, unit: String, ingredientName: String): Pair<Double, String> {
        val lowerUnit = unit.lowercase().trim()
        val lowerName = ingredientName.lowercase().trim()

        // Non-convertible units (e.g. whole eggs, bottles)
        if (lowerUnit in listOf("unit", "egg", "eggs", "pinch", "item", "pcs")) {
            return Pair(quantity, unit)
        }

        // Already in cups or spoons
        if (lowerUnit in listOf("cup", "cups")) {
            return Pair(quantity, "cup")
        }
        if (lowerUnit in listOf("tsp", "teaspoon", "teaspoons")) {
            return Pair(quantity, "tsp")
        }
        if (lowerUnit in listOf("tbsp", "tble", "tablespoon", "tablespoons")) {
            return Pair(quantity, "tbsp")
        }

        val baseGrams = toBaseGrams(quantity, unit)

        // For small seasonings/leaveners (salt, baking soda, baking powder, vanilla), use spoons
        if (baseGrams in 0.1..15.0 || "baking powder" in lowerName || "baking soda" in lowerName || "bicarbonate" in lowerName || "salt" in lowerName || "vanilla" in lowerName) {
            return if (baseGrams <= 5.0) {
                Pair(maxOf(0.25, Math.round(baseGrams / 5.0 * 4.0) / 4.0), "tsp")
            } else if (baseGrams <= 15.0) {
                Pair(maxOf(0.5, Math.round(baseGrams / 15.0 * 2.0) / 2.0), "tbsp")
            } else {
                Pair(Math.round(baseGrams / 15.0 * 2.0) / 2.0, "tbsp")
            }
        }

        // Density per cup based on ingredient
        val gramsPerCup = when {
            "flour" in lowerName -> 125.0
            "sugar" in lowerName || "castor" in lowerName -> 200.0
            "icing" in lowerName -> 120.0
            "butter" in lowerName || "margarine" in lowerName -> 227.0
            "chocolate" in lowerName || "cocoa" in lowerName -> 150.0
            "milk" in lowerName || "water" in lowerName || "oil" in lowerName || "cream" in lowerName -> 240.0
            else -> 200.0
        }

        val rawCups = baseGrams / gramsPerCup
        // Round to nearest quarter cup e.g. 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, etc.
        val roundedCups = Math.round(rawCups * 4.0) / 4.0
        return Pair(maxOf(0.25, roundedCups), "cup")
    }

    /**
     * Converts an ingredient to Grams (metric weight) measurement
     */
    fun convertToGrams(quantity: Double, unit: String, ingredientName: String): Pair<Double, String> {
        val lowerUnit = unit.lowercase().trim()
        val lowerName = ingredientName.lowercase().trim()

        if (lowerUnit in listOf("unit", "egg", "eggs", "pinch", "item", "pcs")) {
            return Pair(quantity, unit)
        }

        if (lowerUnit in listOf("g", "gram", "grams")) {
            return Pair(quantity, "g")
        }

        val gramsPerCup = when {
            "flour" in lowerName -> 125.0
            "sugar" in lowerName || "castor" in lowerName -> 200.0
            "icing" in lowerName -> 120.0
            "butter" in lowerName || "margarine" in lowerName -> 227.0
            "chocolate" in lowerName || "cocoa" in lowerName -> 150.0
            "milk" in lowerName || "water" in lowerName || "oil" in lowerName || "cream" in lowerName -> 240.0
            else -> 200.0
        }

        val convertedGrams = when (lowerUnit) {
            "cup", "cups" -> quantity * gramsPerCup
            "tsp", "teaspoon", "teaspoons" -> quantity * 5.0
            "tbsp", "tble", "tablespoon", "tablespoons" -> quantity * 15.0
            "kg", "kilogram", "kilograms" -> quantity * 1000.0
            "ml", "milliliter", "milliliters" -> quantity
            "l", "liter", "litres", "liters" -> quantity * 1000.0
            else -> quantity
        }

        val roundedGrams = Math.round(convertedGrams).toDouble()
        return Pair(maxOf(1.0, roundedGrams), "g")
    }
}
