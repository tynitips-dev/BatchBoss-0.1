package com.example

import com.example.util.RegionalCurrency
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class RegionalCurrencyTest {
    @Test fun recordDenominationIsNotInferredFromLocale() {
        assertEquals("ZAR", RegionalCurrency.fromRecord(emptyMap()))
        assertEquals("GBP", RegionalCurrency.fromRecord(mapOf("currency" to "GBP")))
        assertEquals("UNKNOWN", RegionalCurrency.fromRecord(mapOf("currency" to null)))
        assertEquals("UNKNOWN", RegionalCurrency.fromRecord(mapOf("currency" to "usd")))
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("R4.99", RegionalCurrency.format(4.99))
            assertEquals("USD 0.59", RegionalCurrency.format(0.59, "USD"))
            assertEquals("GBP 1 250.50", RegionalCurrency.format(1250.5, "GBP"))
        } finally { Locale.setDefault(original) }
    }
    @Test fun totalsRemainSeparateAndForeignNativeLoginIsGated() {
        assertEquals("USD 3.00 / R2.00", RegionalCurrency.formatTotals(listOf("USD" to 3.0, "ZAR" to 2.0)))
        assertTrue(RegionalCurrency.nativeWorkspaceEnabled(emptyMap()))
        assertTrue(RegionalCurrency.nativeWorkspaceEnabled(mapOf("currency" to "ZAR", "countryCode" to "ZA")))
        assertFalse(RegionalCurrency.nativeWorkspaceEnabled(mapOf("currency" to "USD", "countryCode" to "US")))
    }
    @Test fun nativeExactCostingAndAverageDoNotMixDimensionsOrDenominations() {
        val u=com.example.util.UnitUtils
        assertEquals(4.0,u.exactIngredientCost(8.0,"oz",8.0,1.0,"lb")!!,0.000001)
        assertEquals(5.0,u.exactIngredientCost(1.0,"cup_metric",20.0,1.0,"l")!!,0.000001)
        assertEquals(4.8,u.exactIngredientCost(1.0,"cup",20.0,1.0,"l")!!,0.000001)
        assertNull(u.exactIngredientCost(1.0,"cup_us",20.0,1.0,"kg"))
        assertNull(u.exactIngredientCost(Double.NaN,"g",20.0,1.0,"kg"))
        assertNull(u.exactIngredientCost(1.0,"bottle",20.0,1.0,"kg"))
        assertEquals("USD 4.00 / R3.00",RegionalCurrency.formatAverages(listOf("USD" to 2.0,"USD" to 6.0,"ZAR" to 3.0)))
        assertEquals("R0.0140",RegionalCurrency.format(0.014,decimals=4))
    }
}
