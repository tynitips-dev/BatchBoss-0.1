package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
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

        drawInvoiceContent(canvas, invoice, biz)

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

        drawQuoteContent(canvas, quote, biz)

        document.finishPage(page)

        val dir = File(context.cacheDir, "quotes").apply { mkdirs() }
        val file = File(dir, "Quote_#${quote.id}_${quote.clientName.replace(" ", "_")}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    private fun drawInvoiceContent(canvas: Canvas, invoice: InvoiceEntity, biz: UserProfileEntity) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Accent Bar
        paint.color = COLOR_PRIMARY
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 10f, paint)

        // Bakery Name / Business Header
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(biz.bakeryName.ifBlank { "Artisan Bakery" }, MARGIN_LEFT, 50f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = COLOR_MUTED
        var currentY = 65f
        if (biz.address.isNotBlank()) {
            canvas.drawText(biz.address, MARGIN_LEFT, currentY, paint)
            currentY += 14f
        }
        val contactLine = listOfNotNull(
            biz.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            biz.email.takeIf { it.isNotBlank() }?.let { "Email: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, MARGIN_LEFT, currentY, paint)
            currentY += 14f
        }
        if (biz.vatNumber.isNotBlank()) {
            canvas.drawText("VAT/Tax Reg: ${biz.vatNumber}", MARGIN_LEFT, currentY, paint)
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
        canvas.drawText("DESCRIPTION / SERVICE", MARGIN_LEFT + 12f, tableTop + 16f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL AMOUNT", MARGIN_RIGHT - 12f, tableTop + 16f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Table Row (Invoice item)
        val rowTop = tableTop + 28f
        paint.color = Color.WHITE
        val rowRect = RectF(MARGIN_LEFT, rowTop, MARGIN_RIGHT, rowTop + 50f)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        canvas.drawRoundRect(rowRect, 4f, 4f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText(invoice.orderDescription, MARGIN_LEFT + 12f, rowTop + 24f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = COLOR_MUTED
        paint.textSize = 10f
        canvas.drawText("Artisan Baked Goods & Catering Service", MARGIN_LEFT + 12f, rowTop + 38f, paint)

        val priceStr = formatZar(invoice.amount)
        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(priceStr, MARGIN_RIGHT - 12f, rowTop + 30f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Total Summary Card
        val summaryTop = rowTop + 65f
        val summaryRect = RectF(MARGIN_RIGHT - 220f, summaryTop, MARGIN_RIGHT, summaryTop + 55f)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(summaryRect, 8f, 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_PRIMARY
        canvas.drawRoundRect(summaryRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("TOTAL DUE:", MARGIN_RIGHT - 206f, summaryTop + 32f, paint)

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(priceStr, MARGIN_RIGHT - 14f, summaryTop + 34f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Banking Details Box
        val bankTop = summaryTop + 75f
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
        canvas.drawText("Generated by BatchBoss Bakery Suite", PAGE_WIDTH / 2f, 806f, paint)
    }

    private fun drawQuoteContent(canvas: Canvas, quote: QuoteEntity, biz: UserProfileEntity) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Accent Bar
        paint.color = COLOR_AMBER
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 10f, paint)

        // Bakery Name / Business Header
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(biz.bakeryName.ifBlank { "Artisan Bakery" }, MARGIN_LEFT, 50f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.color = COLOR_MUTED
        var currentY = 65f
        if (biz.address.isNotBlank()) {
            canvas.drawText(biz.address, MARGIN_LEFT, currentY, paint)
            currentY += 14f
        }
        val contactLine = listOfNotNull(
            biz.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            biz.email.takeIf { it.isNotBlank() }?.let { "Email: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, MARGIN_LEFT, currentY, paint)
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
        canvas.drawText("DESCRIPTION / BESPOKE ITEM", MARGIN_LEFT + 12f, tableTop + 16f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("QUOTED TOTAL", MARGIN_RIGHT - 12f, tableTop + 16f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Row
        val rowTop = tableTop + 28f
        val rowRect = RectF(MARGIN_LEFT, rowTop, MARGIN_RIGHT, rowTop + 60f)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_BORDER
        canvas.drawRoundRect(rowRect, 4f, 4f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText(quote.recipeOrItemName, MARGIN_LEFT + 12f, rowTop + 24f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = COLOR_MUTED
        paint.textSize = 10f
        canvas.drawText("Includes custom ingredient preparation, baking & decoration labor", MARGIN_LEFT + 12f, rowTop + 40f, paint)

        val priceStr = formatZar(quote.quotedPrice)
        paint.color = COLOR_AMBER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(priceStr, MARGIN_RIGHT - 12f, rowTop + 32f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Deposit & Grand Total Box
        val totalTop = rowTop + 75f
        val totalRect = RectF(MARGIN_RIGHT - 240f, totalTop, MARGIN_RIGHT, totalTop + 65f)
        paint.color = Color.rgb(254, 249, 238)
        canvas.drawRoundRect(totalRect, 8f, 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = COLOR_AMBER
        canvas.drawRoundRect(totalRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        val deposit50 = quote.quotedPrice * 0.5
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("TOTAL QUOTED:", MARGIN_RIGHT - 226f, totalTop + 26f, paint)

        paint.color = COLOR_AMBER
        paint.textSize = 14f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(priceStr, MARGIN_RIGHT - 14f, totalTop + 26f, paint)

        paint.color = COLOR_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("50% Deposit to Confirm:", MARGIN_RIGHT - 226f, totalTop + 48f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatZar(deposit50), MARGIN_RIGHT - 14f, totalTop + 48f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Terms & Banking Box
        val termsTop = totalTop + 85f
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
        canvas.drawText("Generated by BatchBoss Bakery Suite", PAGE_WIDTH / 2f, 806f, paint)
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
