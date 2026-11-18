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
}
