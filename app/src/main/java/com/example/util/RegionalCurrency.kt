package com.example.util

import java.util.Locale
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** Currency snapshots are immutable denominations; this helper performs no foreign exchange. */
object RegionalCurrency {
    const val LEGACY = "ZAR"
    const val UNKNOWN = "UNKNOWN"
    private val codePattern = Regex("^[A-Z]{3}$")
    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        decimalSeparator = '.'
        groupingSeparator = ' '
    }

    fun fromRecord(data: Map<String, Any?>): String {
        if (!data.containsKey("currency")) return LEGACY
        return (data["currency"] as? String)?.takeIf { codePattern.matches(it) } ?: UNKNOWN
    }

    fun format(amount: Double, currency: String = LEGACY): String {
        if (!amount.isFinite() || !codePattern.matches(currency)) return "Amount unavailable"
        val prefix = if (currency == LEGACY) "R" else "$currency "
        val number = DecimalFormat("#,##0.00", symbols).format(kotlin.math.abs(amount))
        return (if (amount < 0) "-" else "") + prefix + number
    }

    fun formatTotals(entries: List<Pair<String, Double>>): String {
        val groups = entries.filter { codePattern.matches(it.first) && it.second.isFinite() }
            .groupBy({ it.first }, { it.second })
        val totals = groups.toSortedMap().map { (currency, amounts) -> format(amounts.sum(), currency) }.toMutableList()
        if (entries.any { !codePattern.matches(it.first) || !it.second.isFinite() }) totals.add("Some amounts unavailable")
        return totals.joinToString(" / ").ifBlank { format(0.0) }
    }

    // Non-ZAR Android sign-in stays closed until the rest of the native pricing screens are migrated.
    fun nativeWorkspaceEnabled(bakery: Map<String, Any?>): Boolean =
        fromRecord(bakery) == LEGACY &&
            (!bakery.containsKey("countryCode") || bakery["countryCode"] == "ZA")
}
