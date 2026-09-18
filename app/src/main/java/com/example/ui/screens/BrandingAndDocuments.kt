package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.InvoiceEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.UserProfileEntity
import com.example.ui.components.BatchBossEmblem
import com.example.ui.theme.*
import com.example.ui.util.PdfGenerator

object DocumentShareUtils {
    fun sendViaWhatsApp(context: Context, phone: String, message: String) {
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "").removePrefix("+")
        try {
            val url = if (cleanPhone.isNotBlank()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
            } else {
                "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard share sheet
            shareText(context, "Send to Client", message)
        }
    }

    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(intent, title).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun EditBusinessBrandingDialog(
    profile: UserProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (
        logoUri: String,
        bakeryName: String,
        phone: String,
        email: String,
        address: String,
        bankName: String,
        accountNumber: String,
        branchCode: String,
        vatNumber: String,
        defaultHourlyRate: Double
    ) -> Unit
) {
    val current = profile ?: UserProfileEntity()
    var logoUri by remember { mutableStateOf(current.logoUri) }
    var bakeryName by remember { mutableStateOf(current.bakeryName) }
    var phone by remember { mutableStateOf(current.phone) }
    var email by remember { mutableStateOf(current.email) }
    var address by remember { mutableStateOf(current.address) }
    var bankName by remember { mutableStateOf(current.bankName) }
    var accountNumber by remember { mutableStateOf(current.accountNumber) }
    var branchCode by remember { mutableStateOf(current.branchCode) }
    var vatNumber by remember { mutableStateOf(current.vatNumber) }
    var hourlyRateText by remember { mutableStateOf(String.format("%.2f", current.defaultHourlyRate)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Google Play compliant zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            logoUri = uri.toString()
        }
    }

    val presetLogoEmblems = listOf(
        "BatchBoss Emblem" to "emblem_batchboss",
        "Chef Toque" to "emblem_chef",
        "Artisan Cake" to "emblem_cake",
        "Golden Whisk" to "emblem_whisk",
        "Cupcake Delight" to "emblem_cupcake",
        "Artisan Loaf" to "emblem_bread"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Storefront, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Business Logo & Info", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(errorMessage!!, color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Business Logo Upload Section
                Text("Business Logo", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BatchPinkContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (logoUri.isNotBlank() && !logoUri.startsWith("emblem_")) {
                                AsyncImage(
                                    model = logoUri,
                                    contentDescription = "Business Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, BatchPink, CircleShape)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(54.dp),
                                    border = androidx.compose.foundation.BorderStroke(2.dp, BatchPink)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val icon = when (logoUri) {
                                            "emblem_cake" -> Icons.Outlined.Cake
                                            "emblem_whisk" -> Icons.Outlined.AutoAwesome
                                            "emblem_cupcake" -> Icons.Outlined.Cookie
                                            "emblem_bread" -> Icons.Outlined.BakeryDining
                                            "emblem_chef" -> Icons.Outlined.Storefront
                                            else -> null
                                        }
                                        if (icon != null) {
                                            Icon(icon, contentDescription = null, tint = BatchPink, modifier = Modifier.size(28.dp))
                                        } else {
                                            BatchBossEmblem(size = 46)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Bakery Logo", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                                Text(
                                    text = if (logoUri.isNotBlank()) "Logo set" else "No logo uploaded",
                                    fontSize = 11.sp,
                                    color = MediumText
                                )
                            }
                        }

                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Preset Logo Emblems
                Text("Or Select Emblem:", fontSize = 11.sp, color = MediumText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetLogoEmblems) { (label, key) ->
                        FilterChip(
                            selected = logoUri == key,
                            onClick = { logoUri = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                // Bakery Details
                Text("Bakery & Contact Information", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                OutlinedTextField(
                    value = bakeryName,
                    onValueChange = { bakeryName = it; errorMessage = null },
                    label = { Text("Bakery / Business Name *") },
                    placeholder = { Text("e.g. Example Bakery Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_business_name")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone / WhatsApp") },
                        placeholder = { Text("+27 82 555 1234") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        placeholder = { Text("orders@bakery.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Bakery Physical Address") },
                    placeholder = { Text("12 Main Road, Cape Town") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Banking & Tax Details
                Text("Banking & Invoice Details", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name") },
                    placeholder = { Text("e.g. First National Bank (FNB)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number") },
                        placeholder = { Text("62890123456") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = branchCode,
                        onValueChange = { branchCode = it },
                        label = { Text("Branch Code") },
                        placeholder = { Text("250655") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vatNumber,
                        onValueChange = { vatNumber = it },
                        label = { Text("VAT / Reg Number") },
                        placeholder = { Text("ZA48910293") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hourlyRateText,
                        onValueChange = { hourlyRateText = it },
                        label = { Text("Hourly Labor Rate (R)") },
                        placeholder = { Text("120.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = hourlyRateText.toDoubleOrNull() ?: 120.0
                    if (bakeryName.isBlank()) {
                        errorMessage = "Please enter your Bakery / Business Name"
                    } else {
                        onSave(
                            logoUri,
                            bakeryName.trim(),
                            phone.trim(),
                            email.trim(),
                            address.trim(),
                            bankName.trim(),
                            accountNumber.trim(),
                            branchCode.trim(),
                            vatNumber.trim(),
                            rate
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_save_business_branding")
            ) {
                Text("Save Business Info", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MediumText)
            }
        },
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White
    )
}

@Composable
fun SendableInvoiceDialog(
    invoice: InvoiceEntity,
    profile: UserProfileEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val biz = profile ?: UserProfileEntity()

    val formattedShareText = remember(invoice, biz) {
        buildString {
            appendLine("📄 TAX INVOICE - ${biz.bakeryName.uppercase()}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Invoice No: ${invoice.invoiceNumber}")
            appendLine("Issue Date: ${invoice.issueDate}")
            appendLine("Due Date: ${invoice.dueDate}")
            appendLine("Status: ${invoice.status.uppercase()}")
            appendLine()
            appendLine("👤 BILLED TO:")
            appendLine("${invoice.clientName}")
            if (invoice.clientPhone.isNotBlank()) appendLine("Phone: ${invoice.clientPhone}")
            appendLine()
            appendLine("🎂 ORDER DETAILS:")
            appendLine("${invoice.orderDescription}")
            appendLine()
            appendLine("💵 TOTAL AMOUNT DUE: R${String.format("%,.2f", invoice.amount)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🏦 BANKING DETAILS FOR PAYMENT:")
            appendLine("Bank: ${biz.bankName}")
            appendLine("Account: ${biz.accountNumber}")
            appendLine("Branch Code: ${biz.branchCode}")
            appendLine("Reference: ${invoice.invoiceNumber}")
            appendLine()
            if (biz.phone.isNotBlank()) appendLine("Contact: ${biz.phone}")
            if (biz.email.isNotBlank()) appendLine("Email: ${biz.email}")
            appendLine("Thank you for supporting ${biz.bakeryName}!")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Branding & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (biz.logoUri.isNotBlank() && !biz.logoUri.startsWith("emblem_")) {
                            AsyncImage(
                                model = biz.logoUri,
                                contentDescription = "Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, BatchPink, CircleShape)
                            )
                        } else {
                            BatchBossEmblem(size = 42)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = biz.bakeryName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                            Text(text = biz.phone, fontSize = 11.sp, color = LightText)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = LightText)
                    }
                }

                // Printable Invoice Card Preview
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BackgroundLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Title & Invoice Number
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TAX INVOICE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DarkText)
                                Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BatchPink)
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (invoice.status == "Paid") MintLight else AmberLight
                            ) {
                                Text(
                                    text = invoice.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (invoice.status == "Paid") MintGreen else WarmAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = DividerColor)

                        // Client details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("BILLED TO:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Text(invoice.clientName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                if (invoice.clientPhone.isNotBlank()) {
                                    Text(invoice.clientPhone, fontSize = 11.sp, color = MediumText)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("DUE DATE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Text(invoice.dueDate, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Text("Issued: ${invoice.issueDate}", fontSize = 10.sp, color = LightText)
                            }
                        }

                        // Order description
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Item Description", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(invoice.orderDescription, fontSize = 12.sp, color = DarkText)
                            }
                        }

                        // Amount Due
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Amount Due:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text(
                                text = "R${String.format("%,.2f", invoice.amount)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BatchPink
                            )
                        }

                        // Banking Details
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BatchPinkLight.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Payment Instructions:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                                Text("Bank: ${biz.bankName} • Acc: ${biz.accountNumber}", fontSize = 11.sp, color = DarkText)
                                Text("Branch: ${biz.branchCode} • Ref: ${invoice.invoiceNumber}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                            }
                        }
                    }
                }

                // Official PDF Generation Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BatchPinkContainer.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BatchPinkLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                            Text("Official PDF Invoice", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                        }

                        Text(
                            "Generate a high-res printable PDF with business logo, tax breakdown, and banking details.",
                            fontSize = 11.sp,
                            color = MediumText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val file = PdfGenerator.generateInvoicePdf(context, invoice, profile)
                                        PdfGenerator.openPdf(context, file)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("btn_view_invoice_pdf")
                            ) {
                                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val file = PdfGenerator.generateInvoicePdf(context, invoice, profile)
                                        PdfGenerator.sharePdf(context, file, "Invoice ${invoice.invoiceNumber} - ${biz.bakeryName}")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("btn_share_invoice_pdf")
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val file = PdfGenerator.generateInvoicePdf(context, invoice, profile)
                                    PdfGenerator.sendPdfViaWhatsApp(context, file, invoice.clientPhone)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error sending PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E7E34)),
                            modifier = Modifier.fillMaxWidth().height(38.dp).testTag("btn_whatsapp_invoice_pdf")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send PDF Document via WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Send and Share Actions (Text & Link)
                Text("Quick Text Message to Client", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)

                // WhatsApp Send Button
                Button(
                    onClick = {
                        DocumentShareUtils.sendViaWhatsApp(context, invoice.clientPhone, formattedShareText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_send_whatsapp_invoice")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Text via WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            DocumentShareUtils.shareText(
                                context,
                                "Invoice ${invoice.invoiceNumber} - ${biz.bakeryName}",
                                formattedShareText
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            DocumentShareUtils.copyToClipboard(context, "Invoice ${invoice.invoiceNumber}", formattedShareText)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SendableQuoteDialog(
    quote: QuoteEntity,
    profile: UserProfileEntity?,
    onDismiss: () -> Unit,
    onConvertToInvoice: () -> Unit
) {
    val context = LocalContext.current
    val biz = profile ?: UserProfileEntity()

    val formattedShareText = remember(quote, biz) {
        buildString {
            appendLine("📋 OFFICIAL ${quote.docType.uppercase()} - ${biz.bakeryName.uppercase()}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("${quote.docType} No: ${quote.quoteNumber}")
            appendLine("Event Type: ${quote.eventType}")
            appendLine("Event Date: ${quote.eventDate}")
            appendLine("Status: ${quote.status.uppercase()}")
            appendLine()
            appendLine("👤 PREPARED FOR:")
            appendLine(quote.clientName)
            if (quote.clientPhone.isNotBlank()) appendLine("Phone: ${quote.clientPhone}")
            appendLine()
            if (quote.items.isNotEmpty()) {
                appendLine("📦 LINE ITEMS:")
                quote.items.forEach { item ->
                    val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                    appendLine("• ${item.itemName} x$qtyStr (${item.unit}) @ R${String.format("%,.2f", item.unitPrice)} = R${String.format("%,.2f", item.lineTotal)}")
                }
                appendLine()
                if (quote.subtotal > 0) appendLine("Subtotal: R${String.format("%,.2f", quote.subtotal)}")
                if (quote.discountAmount > 0) appendLine("Discount: -R${String.format("%,.2f", quote.discountAmount)}")
                if (quote.taxAmount > 0) appendLine("VAT (15%): R${String.format("%,.2f", quote.taxAmount)}")
            } else {
                appendLine("🎂 ITEM & SPECIFICATIONS:")
                appendLine(quote.recipeOrItemName)
            }
            appendLine()
            appendLine("💵 TOTAL ${quote.docType.uppercase()}: R${String.format("%,.2f", quote.quotedPrice)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📌 ACCEPTANCE & DEPOSIT INSTRUCTIONS:")
            appendLine("A 50% deposit (R${String.format("%,.2f", quote.quotedPrice * 0.5)}) secures your order.")
            appendLine("Bank: ${biz.bankName}")
            appendLine("Account: ${biz.accountNumber}")
            appendLine("Branch Code: ${biz.branchCode}")
            appendLine("Reference: ${quote.quoteNumber}")
            appendLine()
            if (biz.phone.isNotBlank()) appendLine("Contact: ${biz.phone}")
            if (biz.email.isNotBlank()) appendLine("Email: ${biz.email}")
            appendLine("Thank you for choosing ${biz.bakeryName}!")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (biz.logoUri.isNotBlank() && !biz.logoUri.startsWith("emblem_")) {
                            AsyncImage(
                                model = biz.logoUri,
                                contentDescription = "Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, BatchPink, CircleShape)
                            )
                        } else {
                            BatchBossEmblem(size = 42)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = biz.bakeryName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                            Text(text = biz.phone, fontSize = 11.sp, color = LightText)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = LightText)
                    }
                }

                // Quote Card Preview
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BackgroundLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("OFFICIAL QUOTE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DarkText)
                                Text(quote.quoteNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WarmAmber)
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (quote.status) {
                                    "Accepted" -> MintLight
                                    "Sent" -> AmberLight
                                    else -> BatchPinkLight
                                }
                            ) {
                                Text(
                                    text = quote.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (quote.status) {
                                        "Accepted" -> MintGreen
                                        "Sent" -> WarmAmber
                                        else -> BatchPink
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = DividerColor)

                        // Client & Event
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("PREPARED FOR:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Text(quote.clientName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                if (quote.clientPhone.isNotBlank()) {
                                    Text(quote.clientPhone, fontSize = 11.sp, color = MediumText)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("EVENT DATE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Text(quote.eventDate, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Text(quote.eventType, fontSize = 11.sp, color = MediumText)
                            }
                        }

                        // Item details & line items
                        if (quote.items.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Line Items", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightText)
                                    quote.items.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                                            Text(
                                                text = "${item.itemName} x$qtyStr (${item.unit})",
                                                fontSize = 12.sp,
                                                color = DarkText,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "R${String.format("%,.2f", item.lineTotal)}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = DarkText
                                            )
                                        }
                                    }
                                }
                            }

                            if (quote.subtotal > 0 || quote.discountAmount > 0 || quote.taxAmount > 0) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (quote.subtotal > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Subtotal", fontSize = 11.sp, color = MediumText)
                                            Text("R${String.format("%,.2f", quote.subtotal)}", fontSize = 11.sp, color = DarkText)
                                        }
                                    }
                                    if (quote.discountAmount > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Discount", fontSize = 11.sp, color = MintGreen)
                                            Text("-R${String.format("%,.2f", quote.discountAmount)}", fontSize = 11.sp, color = MintGreen)
                                        }
                                    }
                                    if (quote.taxAmount > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("VAT (15%)", fontSize = 11.sp, color = MediumText)
                                            Text("R${String.format("%,.2f", quote.taxAmount)}", fontSize = 11.sp, color = DarkText)
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Cake / Baked Item Specifications", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightText)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(quote.recipeOrItemName, fontSize = 12.sp, color = DarkText)
                                }
                            }
                        }

                        // Quoted Price
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total ${quote.docType}:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text(
                                text = "R${String.format("%,.2f", quote.quotedPrice)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WarmAmber
                            )
                        }

                        // Deposit note
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberLight.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("50% Deposit to Confirm Order:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                                Text("Bank: ${biz.bankName} • Acc: ${biz.accountNumber}", fontSize = 11.sp, color = DarkText)
                                Text("Branch: ${biz.branchCode} • Ref: ${quote.quoteNumber}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                            }
                        }
                    }
                }

                // Official PDF Quote Generation Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberLight.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarmAmber.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(20.dp))
                            Text("Official PDF Quotation", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                        }

                        Text(
                            "Generate a professional quote document with deposit breakdown, event date, and terms.",
                            fontSize = 11.sp,
                            color = MediumText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val file = PdfGenerator.generateQuotePdf(context, quote, profile)
                                        PdfGenerator.openPdf(context, file)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WarmAmber),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("btn_view_quote_pdf")
                            ) {
                                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val file = PdfGenerator.generateQuotePdf(context, quote, profile)
                                        PdfGenerator.sharePdf(context, file, "Quote ${quote.quoteNumber} - ${biz.bakeryName}")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("btn_share_quote_pdf")
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val file = PdfGenerator.generateQuotePdf(context, quote, profile)
                                    PdfGenerator.sendPdfViaWhatsApp(context, file, quote.clientPhone)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error sending PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E7E34)),
                            modifier = Modifier.fillMaxWidth().height(38.dp).testTag("btn_whatsapp_quote_pdf")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send PDF Document via WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Send & Actions (Quick Text Message)
                Text("Quick Text Message to Client", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)

                // WhatsApp Send Button
                Button(
                    onClick = {
                        DocumentShareUtils.sendViaWhatsApp(context, quote.clientPhone, formattedShareText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_send_whatsapp_quote")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Text via WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            DocumentShareUtils.shareText(
                                context,
                                "Quote ${quote.quoteNumber} - ${biz.bakeryName}",
                                formattedShareText
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onConvertToInvoice()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Outlined.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("To Invoice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
