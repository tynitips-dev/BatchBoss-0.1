package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.InvoiceEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.UserProfileEntity
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 555f
    private const val CONTENT_WIDTH = 515f

    private val COLOR_PRIMARY = Color.rgb(216, 27, 96) // BatchPink
    private val COLOR_DARK = Color.rgb(45, 55, 72)
    private val COLOR_MUTED = Color.rgb(113, 128, 150)
    private val COLOR_LIGHT_BG = Color.rgb(254, 243, 246)
    private val COLOR_BORDER = Color.rgb(226, 232, 240)
    private val COLOR_AMBER = Color.rgb(217, 119, 6)

    fun generateInvoicePdf(context: Context, invoice: InvoiceEntity, profile: UserProfileEntity?): File {
        val biz = profile ?: UserProfileEntity()
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawInvoiceContent(context, canvas, invoice, biz)

        document.finishPage(page)

        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "Invoice_#${invoice.id}_${invoice.clientName.replace(" ", "_")}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    fun generateQuotePdf(context: Context, quote: QuoteEntity, profile: UserProfileEntity?): File {
        val biz = profile ?: UserProfileEntity()
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawQuoteContent(context, canvas, quote, biz)

        document.finishPage(page)

        val dir = File(context.cacheDir, "quotes").apply { mkdirs() }
        val file = File(dir, "Quote_#${quote.id}_${quote.clientName.replace(" ", "_")}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    private fun drawInvoiceContent(context: Context, canvas: Canvas, invoice: InvoiceEntity, biz: UserProfileEntity) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Accent Bar
        paint.color = COLOR_PRIMARY
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 10f, paint)

        // Logo Emblem
        val logoBmp: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_batchboss_emblem)
        } catch (e: Exception) {
            null
        }

        var textStartX = MARGIN_LEFT
        if (logoBmp != null) {
            val logoSize = 42f
            canvas.drawBitmap(logoBmp, null, RectF(MARGIN_LEFT, 26f, MARGIN_LEFT + logoSize, 26f + logoSize), paint)
            textStartX += logoSize + 12f
        }

        // Bakery Name / Business Header
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(biz.bakeryName.ifBlank { "Artisan Bakery" }, textStartX, 44f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = COLOR_MUTED
        var currentY = 58f
        if (biz.address.isNotBlank()) {
            canvas.drawText(biz.address, textStartX, currentY, paint)
            currentY += 14f
        }
        val contactLine = listOfNotNull(
            biz.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            biz.email.takeIf { it.isNotBlank() }?.let { "Email: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, textStartX, currentY, paint)
            currentY += 14f
        }
        if (biz.vatNumber.isNotBlank()) {
            canvas.drawText("VAT/Tax Reg: ${biz.vatNumber}", textStartX, currentY, paint)
            currentY += 14f
        }

        // Invoice Title & Details on Top Right
        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TAX INVOICE", MARGIN_RIGHT, 50f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("INVOICE #: INV-${invoice.id.toString().padStart(4, '0')}", MARGIN_RIGHT, 68f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = COLOR_MUTED
        canvas.drawText("Due Date: ${invoice.dueDate}", MARGIN_RIGHT, 82f, paint)
        canvas.drawText("Status: ${invoice.status.uppercase(Locale.ROOT)}", MARGIN_RIGHT, 96f, paint)

        // Reset alignment
        paint.textAlign = Paint.Align.LEFT

        // Divider
        paint.color = COLOR_BORDER
        canvas.drawLine(MARGIN_LEFT, 120f, MARGIN_RIGHT, 120f, paint)

        // Bill To Section Box
        paint.color = COLOR_LIGHT_BG
        val billToRect = RectF(MARGIN_LEFT, 135f, MARGIN_RIGHT, 185f)
        canvas.drawRoundRect(billToRect, 8f, 8f, paint)

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("BILLED TO CLIENT", MARGIN_LEFT + 14f, 153f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        canvas.drawText(invoice.clientName, MARGIN_LEFT + 14f, 172f, paint)

        if (invoice.clientPhone.isNotBlank()) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = COLOR_MUTED
            paint.textSize = 11f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Phone: ${invoice.clientPhone}", MARGIN_RIGHT - 14f, 172f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        // Table Header
        val tableTop = 205f
        paint.color = COLOR_DARK
        val headerRect = RectF(MARGIN_LEFT, tableTop, MARGIN_RIGHT, tableTop + 24f)
        canvas.drawRoundRect(headerRect, 4f, 4f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("ITEM DESCRIPTION", MARGIN_LEFT + 12f, tableTop + 16f, paint)
        canvas.drawText("QTY", MARGIN_LEFT + 250f, tableTop + 16f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("UNIT PRICE", MARGIN_RIGHT - 110f, tableTop + 16f, paint)
        canvas.drawText("LINE TOTAL", MARGIN_RIGHT - 12f, tableTop + 16f, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY = tableTop + 28f
        val lineItems = invoice.items

        if (lineItems.isNotEmpty()) {
            for (item in lineItems) {
                val rowHeight = if (item.description.isNotBlank()) 42f else 32f
                val rowRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + rowHeight)
                paint.style = Paint.Style.STROKE
                paint.color = COLOR_BORDER
                canvas.drawRoundRect(rowRect, 4f, 4f, paint)
                paint.style = Paint.Style.FILL

                paint.color = COLOR_DARK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 11f
                canvas.drawText(item.itemName, MARGIN_LEFT + 12f, currentY + 18f, paint)

                if (item.description.isNotBlank()) {
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.color = COLOR_MUTED
                    paint.textSize = 9f
                    canvas.drawText(item.description, MARGIN_LEFT + 12f, currentY + 32f, paint)
                }

                // Qty & Unit
                paint.color = COLOR_DARK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 10f
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                canvas.drawText("$qtyStr ${item.unit}", MARGIN_LEFT + 250f, currentY + 18f, paint)

                // Unit Price
                paint.textAlign = Paint.Align.RIGHT
                paint.color = COLOR_MUTED
                canvas.drawText(formatZar(item.unitPrice), MARGIN_RIGHT - 110f, currentY + 18f, paint)

                // Line Total
                paint.color = COLOR_PRIMARY
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 11f
                canvas.drawText(formatZar(item.lineTotal), MARGIN_RIGHT - 12f, currentY + 18f, paint)
                paint.textAlign = Paint.Align.LEFT

                currentY += rowHeight + 4f
            }
        } else {
            // Fallback single row
            val rowRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + 50f)
            paint.style = Paint.Style.STROKE
            paint.color = COLOR_BORDER
            canvas.drawRoundRect(rowRect, 4f, 4f, paint)
            paint.style = Paint.Style.FILL

            paint.color = COLOR_DARK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 12f
            canvas.drawText(invoice.orderDescription, MARGIN_LEFT + 12f, currentY + 24f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = COLOR_MUTED
            paint.textSize = 10f
            canvas.drawText("Artisan Baked Goods & Catering Service", MARGIN_LEFT + 12f, currentY + 38f, paint)

            val priceStr = formatZar(invoice.amount)
            paint.color = COLOR_PRIMARY
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 13f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(priceStr, MARGIN_RIGHT - 12f, currentY + 30f, paint)
            paint.textAlign = Paint.Align.LEFT
            currentY += 54f
        }

        // Summary Card with Subtotal, Discount, VAT, Total
        val summaryTop = currentY + 10f
        val summaryHeight = if (invoice.discountAmount > 0 || invoice.taxAmount > 0) 80f else 55f
        val summaryRect = RectF(MARGIN_RIGHT - 240f, summaryTop, MARGIN_RIGHT, summaryTop + summaryHeight)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(summaryRect, 8f, 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_PRIMARY
        canvas.drawRoundRect(summaryRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        var sumLineY = summaryTop + 22f
        if (invoice.discountAmount > 0 || invoice.taxAmount > 0) {
            paint.color = COLOR_MUTED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 10f
            canvas.drawText("Subtotal:", MARGIN_RIGHT - 226f, sumLineY, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatZar(invoice.subtotal.takeIf { it > 0 } ?: invoice.amount), MARGIN_RIGHT - 14f, sumLineY, paint)
            paint.textAlign = Paint.Align.LEFT
            sumLineY += 16f

            if (invoice.discountAmount > 0) {
                paint.color = COLOR_MUTED
                canvas.drawText("Discount:", MARGIN_RIGHT - 226f, sumLineY, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("-${formatZar(invoice.discountAmount)}", MARGIN_RIGHT - 14f, sumLineY, paint)
                paint.textAlign = Paint.Align.LEFT
                sumLineY += 16f
            }

            if (invoice.taxAmount > 0) {
                paint.color = COLOR_MUTED
                canvas.drawText("VAT (${invoice.taxRatePercent.toInt()}%):", MARGIN_RIGHT - 226f, sumLineY, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(formatZar(invoice.taxAmount), MARGIN_RIGHT - 14f, sumLineY, paint)
                paint.textAlign = Paint.Align.LEFT
                sumLineY += 16f
            }
        }

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("TOTAL DUE:", MARGIN_RIGHT - 226f, sumLineY, paint)

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatZar(invoice.amount), MARGIN_RIGHT - 14f, sumLineY + 2f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Banking Details Box
        val bankTop = summaryTop + summaryHeight + 20f
        val bankRect = RectF(MARGIN_LEFT, bankTop, MARGIN_RIGHT, bankTop + 105f)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(bankRect, 8f, 8f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(bankRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("BANKING & PAYMENT DETAILS", MARGIN_LEFT + 14f, bankTop + 24f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = COLOR_MUTED
        paint.textSize = 10f
        canvas.drawText("Bank: ${biz.bankName.ifBlank { "Standard Bank / FNB" }}", MARGIN_LEFT + 14f, bankTop + 44f, paint)
        canvas.drawText("Account Number: ${biz.accountNumber.ifBlank { "10192837465" }}", MARGIN_LEFT + 14f, bankTop + 60f, paint)
        canvas.drawText("Branch Code: ${biz.branchCode.ifBlank { "250655" }}", MARGIN_LEFT + 14f, bankTop + 76f, paint)
        canvas.drawText("Reference: INV-${invoice.id.toString().padStart(4, '0')} (${invoice.clientName})", MARGIN_LEFT + 14f, bankTop + 92f, paint)

        // Footer Note
        paint.color = COLOR_MUTED
        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Thank you for your business! Please email proof of payment.", PAGE_WIDTH / 2f, 790f, paint)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Powered by BatchBoss Bakery Suite  •  Smart Costing for Every Baker", PAGE_WIDTH / 2f, 806f, paint)
    }

    private fun drawQuoteContent(context: Context, canvas: Canvas, quote: QuoteEntity, biz: UserProfileEntity) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Accent Bar
        paint.color = COLOR_AMBER
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 10f, paint)

        // Logo Emblem
        val logoBmp: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_batchboss_emblem)
        } catch (e: Exception) {
            null
        }

        var textStartX = MARGIN_LEFT
        if (logoBmp != null) {
            val logoSize = 42f
            canvas.drawBitmap(logoBmp, null, RectF(MARGIN_LEFT, 26f, MARGIN_LEFT + logoSize, 26f + logoSize), paint)
            textStartX += logoSize + 12f
        }

        // Bakery Name / Business Header
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(biz.bakeryName.ifBlank { "Artisan Bakery" }, textStartX, 44f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = COLOR_MUTED
        var currentY = 58f
        if (biz.address.isNotBlank()) {
            canvas.drawText(biz.address, textStartX, currentY, paint)
            currentY += 14f
        }
        val contactLine = listOfNotNull(
            biz.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            biz.email.takeIf { it.isNotBlank() }?.let { "Email: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, textStartX, currentY, paint)
            currentY += 14f
        }

        // Quote Title & Details on Top Right
        paint.color = COLOR_AMBER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("OFFICIAL QUOTATION", MARGIN_RIGHT, 50f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("QUOTE #: QUO-${quote.id.toString().padStart(4, '0')}", MARGIN_RIGHT, 68f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = COLOR_MUTED
        canvas.drawText("Event Date: ${quote.eventDate}", MARGIN_RIGHT, 82f, paint)
        canvas.drawText("Status: ${quote.status.uppercase(Locale.ROOT)}", MARGIN_RIGHT, 96f, paint)

        // Reset alignment
        paint.textAlign = Paint.Align.LEFT

        // Divider
        paint.color = COLOR_BORDER
        canvas.drawLine(MARGIN_LEFT, 120f, MARGIN_RIGHT, 120f, paint)

        // Client & Event Details Box
        val clientRect = RectF(MARGIN_LEFT, 135f, MARGIN_RIGHT, 195f)
        paint.color = Color.rgb(254, 249, 238) // Warm Amber Light
        canvas.drawRoundRect(clientRect, 8f, 8f, paint)

        paint.color = COLOR_AMBER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("PREPARED FOR CLIENT", MARGIN_LEFT + 14f, 153f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        canvas.drawText(quote.clientName, MARGIN_LEFT + 14f, 172f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = COLOR_MUTED
        paint.textSize = 11f
        canvas.drawText("Event Type: ${quote.eventType}", MARGIN_LEFT + 14f, 188f, paint)

        if (quote.clientPhone.isNotBlank()) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Phone: ${quote.clientPhone}", MARGIN_RIGHT - 14f, 172f, paint)
            canvas.drawText("Event Date: ${quote.eventDate}", MARGIN_RIGHT - 14f, 188f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        // Items Table
        val tableTop = 215f
        paint.color = COLOR_DARK
        val headerRect = RectF(MARGIN_LEFT, tableTop, MARGIN_RIGHT, tableTop + 24f)
        canvas.drawRoundRect(headerRect, 4f, 4f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("ITEM DESCRIPTION", MARGIN_LEFT + 12f, tableTop + 16f, paint)
        canvas.drawText("QTY", MARGIN_LEFT + 250f, tableTop + 16f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("UNIT PRICE", MARGIN_RIGHT - 110f, tableTop + 16f, paint)
        canvas.drawText("LINE TOTAL", MARGIN_RIGHT - 12f, tableTop + 16f, paint)
        paint.textAlign = Paint.Align.LEFT

        var currentQuoteY = tableTop + 28f
        val qItems = quote.items

        if (qItems.isNotEmpty()) {
            for (item in qItems) {
                val rowHeight = if (item.description.isNotBlank()) 42f else 32f
                val rowRect = RectF(MARGIN_LEFT, currentQuoteY, MARGIN_RIGHT, currentQuoteY + rowHeight)
                paint.style = Paint.Style.STROKE
                paint.color = COLOR_BORDER
                canvas.drawRoundRect(rowRect, 4f, 4f, paint)
                paint.style = Paint.Style.FILL

                paint.color = COLOR_DARK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 11f
                canvas.drawText(item.itemName, MARGIN_LEFT + 12f, currentQuoteY + 18f, paint)

                if (item.description.isNotBlank()) {
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.color = COLOR_MUTED
                    paint.textSize = 9f
                    canvas.drawText(item.description, MARGIN_LEFT + 12f, currentQuoteY + 32f, paint)
                }

                // Qty & Unit
                paint.color = COLOR_DARK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 10f
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                canvas.drawText("$qtyStr ${item.unit}", MARGIN_LEFT + 250f, currentQuoteY + 18f, paint)

                // Unit Price
                paint.textAlign = Paint.Align.RIGHT
                paint.color = COLOR_MUTED
                canvas.drawText(formatZar(item.unitPrice), MARGIN_RIGHT - 110f, currentQuoteY + 18f, paint)

                // Line Total
                paint.color = COLOR_AMBER
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 11f
                canvas.drawText(formatZar(item.lineTotal), MARGIN_RIGHT - 12f, currentQuoteY + 18f, paint)
                paint.textAlign = Paint.Align.LEFT

                currentQuoteY += rowHeight + 4f
            }
        } else {
            // Fallback single row
            val rowRect = RectF(MARGIN_LEFT, currentQuoteY, MARGIN_RIGHT, currentQuoteY + 60f)
            paint.style = Paint.Style.STROKE
            paint.color = COLOR_BORDER
            canvas.drawRoundRect(rowRect, 4f, 4f, paint)
            paint.style = Paint.Style.FILL

            paint.color = COLOR_DARK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 12f
            canvas.drawText(quote.recipeOrItemName, MARGIN_LEFT + 12f, currentQuoteY + 24f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = COLOR_MUTED
            paint.textSize = 10f
            canvas.drawText("Includes custom ingredient preparation, baking & decoration labor", MARGIN_LEFT + 12f, currentQuoteY + 40f, paint)

            val priceStr = formatZar(quote.quotedPrice)
            paint.color = COLOR_AMBER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 13f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(priceStr, MARGIN_RIGHT - 12f, currentQuoteY + 32f, paint)
            paint.textAlign = Paint.Align.LEFT
            currentQuoteY += 64f
        }

        // Deposit & Grand Total Box
        val totalTop = currentQuoteY + 10f
        val summaryHeight = if (quote.discountAmount > 0 || quote.taxAmount > 0) 90f else 65f
        val totalRect = RectF(MARGIN_RIGHT - 240f, totalTop, MARGIN_RIGHT, totalTop + summaryHeight)
        paint.color = Color.rgb(254, 249, 238)
        canvas.drawRoundRect(totalRect, 8f, 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_AMBER
        canvas.drawRoundRect(totalRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        var qSumY = totalTop + 22f
        if (quote.discountAmount > 0 || quote.taxAmount > 0) {
            paint.color = COLOR_MUTED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 10f
            canvas.drawText("Subtotal:", MARGIN_RIGHT - 226f, qSumY, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatZar(quote.subtotal.takeIf { it > 0 } ?: quote.quotedPrice), MARGIN_RIGHT - 14f, qSumY, paint)
            paint.textAlign = Paint.Align.LEFT
            qSumY += 15f

            if (quote.discountAmount > 0) {
                paint.color = COLOR_MUTED
                canvas.drawText("Discount:", MARGIN_RIGHT - 226f, qSumY, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("-${formatZar(quote.discountAmount)}", MARGIN_RIGHT - 14f, qSumY, paint)
                paint.textAlign = Paint.Align.LEFT
                qSumY += 15f
            }

            if (quote.taxAmount > 0) {
                paint.color = COLOR_MUTED
                canvas.drawText("VAT (${quote.taxRatePercent.toInt()}%):", MARGIN_RIGHT - 226f, qSumY, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(formatZar(quote.taxAmount), MARGIN_RIGHT - 14f, qSumY, paint)
                paint.textAlign = Paint.Align.LEFT
                qSumY += 15f
            }
        }

        val deposit50 = quote.quotedPrice * 0.5
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        val docLabel = if (quote.docType.equals("Estimate", ignoreCase = true)) "ESTIMATE TOTAL:" else "TOTAL QUOTED:"
        canvas.drawText(docLabel, MARGIN_RIGHT - 226f, qSumY, paint)

        paint.color = COLOR_AMBER
        paint.textSize = 14f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatZar(quote.quotedPrice), MARGIN_RIGHT - 14f, qSumY, paint)

        paint.color = COLOR_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("50% Deposit to Confirm:", MARGIN_RIGHT - 226f, qSumY + 20f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatZar(deposit50), MARGIN_RIGHT - 14f, qSumY + 20f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Terms & Banking Box
        val termsTop = totalTop + summaryHeight + 20f
        val termsRect = RectF(MARGIN_LEFT, termsTop, MARGIN_RIGHT, termsTop + 115f)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(termsRect, 8f, 8f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(termsRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("TERMS & DEPOSIT PAYMENT DETAILS", MARGIN_LEFT + 14f, termsTop + 22f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = COLOR_MUTED
        paint.textSize = 9.5f
        canvas.drawText("• Quote valid for 14 days from issue date. 50% deposit secures the date.", MARGIN_LEFT + 14f, termsTop + 40f, paint)
        canvas.drawText("• Bank: ${biz.bankName.ifBlank { "Standard Bank / FNB" }}", MARGIN_LEFT + 14f, termsTop + 58f, paint)
        canvas.drawText("• Account: ${biz.accountNumber.ifBlank { "10192837465" }}  |  Branch: ${biz.branchCode.ifBlank { "250655" }}", MARGIN_LEFT + 14f, termsTop + 74f, paint)
        canvas.drawText("• Reference: QUO-${quote.id.toString().padStart(4, '0')} (${quote.clientName})", MARGIN_LEFT + 14f, termsTop + 90f, paint)
        canvas.drawText("• Please send proof of deposit to confirm order scheduling.", MARGIN_LEFT + 14f, termsTop + 104f, paint)

        // Footer
        paint.color = COLOR_MUTED
        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("We look forward to creating something delicious for your event!", PAGE_WIDTH / 2f, 790f, paint)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Powered by BatchBoss Bakery Suite  •  Smart Costing for Every Baker", PAGE_WIDTH / 2f, 806f, paint)
    }

    private fun formatZar(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        return format.format(amount).replace("ZAR", "R")
    }

    fun openPdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No PDF viewer app found. You can still share the file.", Toast.LENGTH_LONG).show()
            sharePdf(context, file, "Share PDF")
        }
    }

    fun sharePdf(context: Context, file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendPdfViaWhatsApp(context: Context, file: File, phone: String) {
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "").removePrefix("+")
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.whatsapp")
                if (cleanPhone.isNotBlank()) {
                    putExtra("jid", "$cleanPhone@s.whatsapp.net")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard share chooser
            sharePdf(context, file, "Send PDF Document")
        }
    }
}
