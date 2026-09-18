package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * South African style number and currency formatting utilities.
 * Ensures:
 * - Full stop (.) as the decimal separator (NEVER commas).
 * - Space as thousands separator where appropriate (e.g. R1 250.00).
 * - Consistent formatting throughout the entire application.
 */
object AppFormatters {
    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        decimalSeparator = '.'
        groupingSeparator = ' '
    }

    private val currencyFormat = DecimalFormat("R#,##0.00", symbols)
    private val integerCurrencyFormat = DecimalFormat("R#,##0", symbols)
    private val decimalFormat = DecimalFormat("#,##0.00", symbols)
    private val singleDecimalFormat = DecimalFormat("#,##0.0", symbols)
    private val optionalDecimalFormat = DecimalFormat("#,##0.##", symbols)

    /**
     * Formats amount to South African Rand with 2 decimal places.
     * Example: 1250.5 -> "R1 250.50", 0.0 -> "R0.00"
     */
    fun formatCurrency(amount: Double): String {
        return synchronized(currencyFormat) {
            currencyFormat.format(amount)
        }
    }

    /**
     * Formats amount to Rand without decimals if zero, or with 2 decimals.
     */
    fun formatCurrencyCompact(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            synchronized(integerCurrencyFormat) {
                integerCurrencyFormat.format(amount)
            }
        } else {
            formatCurrency(amount)
        }
    }

    /**
     * Formats decimal number with 2 decimal places using full stop.
     * Example: 250.0 -> "250.00"
     */
    fun formatDecimal(value: Double): String {
        return synchronized(decimalFormat) {
            decimalFormat.format(value)
        }
    }

    /**
     * Formats decimal number with 1 decimal place using full stop.
     * Example: 1.5 -> "1.5"
     */
    fun formatSingleDecimal(value: Double): String {
        return synchronized(singleDecimalFormat) {
            singleDecimalFormat.format(value)
        }
    }

    /**
     * Formats decimal number with optional decimals using full stop.
     * Example: 2.0 -> "2", 2.75 -> "2.75"
     */
    fun formatOptionalDecimal(value: Double): String {
        return synchronized(optionalDecimalFormat) {
            optionalDecimalFormat.format(value)
        }
    }

    /**
     * Formats quantity with unit using full stop.
     * Example: (250.0, "g") -> "250.00 g", (1.5, "kg") -> "1.50 kg"
     */
    fun formatQuantityWithUnit(quantity: Double, unit: String): String {
        val formattedNumber = formatDecimal(quantity)
        return "$formattedNumber $unit".trim()
    }
}
