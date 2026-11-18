package com.example.util

object UnitUtils {
    val RECIPE_UNITS = listOf("g", "kg", "ml", "tsp", "tbsp", "cup", "l", "unit", "oz", "lb", "cup_us", "cup_metric", "fl_oz_us", "fl_oz_uk", "tsp_us", "tbsp_us", "tbsp_au")

    // Legacy cup stays 240 ml on Android. New recipes can choose explicit standards.
    fun unitLabel(unit: String): String = when(unit.lowercase()) {
        "cup" -> "Legacy cup (240 ml)"
        "cup_us" -> "US cup"
        "cup_metric" -> "Metric cup (250 ml)"
        "oz" -> "oz (weight)"
        "fl_oz_us" -> "US fluid oz"
        "fl_oz_uk" -> "UK fluid oz"
        "tsp_us" -> "US tsp"
        "tbsp_us" -> "US tbsp"
        "tbsp_au" -> "AU tbsp (20 ml)"
        else -> unit
    }

    fun baseDimension(unit: String): String? = when(unit.trim().lowercase()) {
        "g","gram","grams","kg","kilogram","kilograms","oz","ounce","ounces","lb","lbs","pound","pounds" -> "g"
        "ml","milliliter","milliliters","millilitre","l","liter","liters","litre","litres",
        "cup","cups","cup_us","cup_metric","tsp","teaspoon","teaspoons","tsp_us","tbsp","tble","tablespoon","tablespoons","tbsp_us","tbsp_au","fl_oz_us","fl_oz_uk" -> "ml"
        "unit","units","egg","eggs","each","pcs","piece","pieces" -> "unit"
        "bottle","bottles" -> "bottle"
        else -> null
    }

    /** Exact costing only. Mass and volume need a known ingredient density, not a guessed ratio. */
    fun exactIngredientCost(recipeQuantity: Double, recipeUnit: String, packagePrice: Double, packageQuantity: Double, packageUnit: String): Double? {
        val dimension=baseDimension(recipeUnit) ?: return null
        if(dimension!=baseDimension(packageUnit)) return null
        if(listOf(recipeQuantity,packagePrice,packageQuantity).any { !it.isFinite() } ||
            recipeQuantity<=0.0 || packagePrice<0.0 || packageQuantity<=0.0) return null
        val basePackage=toBaseUnits(packageQuantity,packageUnit)
        val baseRecipe=toBaseUnits(recipeQuantity,recipeUnit)
        if(basePackage<=0.0 || !basePackage.isFinite() || !baseRecipe.isFinite()) return null
        return (baseRecipe * (packagePrice/basePackage)).takeIf { it.isFinite() }
    }

    /**
     * Normalises ingredient package quantities to base units:
     * - 1 kg = 1000 g
     * - 500 g = 500 g
     * - 1 litre / 1 l = 1000 ml
     * - 500 ml = 500 ml
     * - units such as eggs remain individual units
     *
     * Supports compatible unit conversions:
     * - kg to g, g to kg
     * - litre to ml, ml to litre
     */
    fun toBaseUnits(quantity: Double, unit: String): Double {
        return when (unit.lowercase().trim()) {
            "kg", "kilogram", "kilograms" -> quantity * 1000.0
            "g", "gram", "grams" -> quantity
            "l", "liter", "litres", "liters", "litre" -> quantity * 1000.0
            "ml", "milliliter", "milliliters", "millilitre" -> quantity
            "tsp", "teaspoon", "teaspoons" -> quantity * 5.0
            "tbsp", "tble", "tablespoon", "tablespoons" -> quantity * 15.0
            "cup", "cups" -> quantity * 240.0
            "oz", "ounce", "ounces" -> quantity * 28.349523125
            "lb", "lbs", "pound", "pounds" -> quantity * 453.59237
            "cup_us" -> quantity * 236.5882365
            "cup_metric" -> quantity * 250.0
            "fl_oz_us" -> quantity * 29.5735295625
            "fl_oz_uk" -> quantity * 28.4130625
            "tsp_us" -> quantity * 4.92892159375
            "tbsp_us" -> quantity * 14.78676478125
            "tbsp_au" -> quantity * 20.0
            "pinch" -> quantity * 0.5
            else -> quantity // unit, egg, eggs, bottle, pcs
        }
    }

    /**
     * Converts a given quantity and unit to base grams (or ml for liquids, where 1ml ~ 1g).
     * Supported: g, kg, ml, tsp, tbsp / tble, l, cup, unit / eggs
     */
    fun toBaseGrams(quantity: Double, unit: String): Double = toBaseUnits(quantity, unit)

    /**
     * Calculates the ingredient cost per gram or millilitre (or per individual unit):
     * costPerBaseUnit = packagePrice / packageQuantityInBaseUnits
     */
    fun calculateCostPerBaseUnit(packagePrice: Double, packageQuantity: Double, packageUnit: String): Double {
        if (packagePrice <= 0.0) return 0.0
        val baseQuantity = toBaseUnits(packageQuantity, packageUnit)
        return if (baseQuantity > 0.0) packagePrice / baseQuantity else 0.0
    }

    /**
     * Calculates the recipe ingredient cost automatically:
     * ingredientCost = costPerBaseUnit * recipeQuantityUsed
     *
     * Example:
     * Package price = R50, Package size = 1 kg
     * Recipe amount = 240 g
     * Cost per gram = R50 / 1000 = R0.05
     * Ingredient cost = R0.05 * 240 = R12.00
     */
    fun calculateIngredientCost(
        recipeQuantity: Double,
        recipeUnit: String,
        packagePrice: Double,
        packageQuantity: Double,
        packageUnit: String
    ): Double {
        if (recipeQuantity <= 0.0 || packagePrice <= 0.0) return 0.0
        val costPerBase = calculateCostPerBaseUnit(packagePrice, packageQuantity, packageUnit)
        val recipeBaseUnits = toBaseUnits(recipeQuantity, recipeUnit)
        return costPerBase * recipeBaseUnits
    }

    /**
     * Formats money display with full-stop decimal to two decimal places (e.g. R12.00).
     */
    fun formatMoney(amount: Double): String {
        return String.format(java.util.Locale.US, "R%.2f", amount)
    }

    /**
     * Formats decimal with full-stop to two decimal places (e.g. 12.00).
     */
    fun formatDecimal(amount: Double): String {
        return String.format(java.util.Locale.US, "%.2f", amount)
    }

    /**
     * Calculates the ingredient cost given:
     * - quantity in recipe unit (e.g. 2.0 tbsp)
     * - unit (e.g. "tbsp")
     * - inventory unit price per base gram/unit (e.g. R0.022/g)
     */
    fun calculateCost(quantity: Double, unit: String, pricePerBaseGram: Double): Double {
        val baseGrams = toBaseUnits(quantity, unit)
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
            String.format(java.util.Locale.US, "%.2f", quantity).trimEnd('0').trimEnd('.')
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
