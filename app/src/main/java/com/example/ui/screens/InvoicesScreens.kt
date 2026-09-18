package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.InvoiceEntity
import com.example.data.local.LineItem
import com.example.data.local.ProductServiceEntity
import com.example.data.local.UserProfileEntity
import com.example.ui.theme.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import java.text.NumberFormat
import java.util.Locale

private fun formatZar(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
    return format.format(amount).replace("ZAR", "R").trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesListScreen(
    invoices: List<InvoiceEntity>,
    products: List<ProductServiceEntity> = emptyList(),
    onBack: () -> Unit,
    onCreateInvoice: (
        clientName: String,
        phone: String,
        desc: String,
        amount: Double,
        dueDate: String,
        status: String,
        subtotal: Double,
        discountAmount: Double,
        taxRatePercent: Double,
        taxAmount: Double,
        items: List<LineItem>,
        totalCost: Double
    ) -> Unit,
    onSaveProductService: ((name: String, description: String, category: String, costPrice: Double, sellingPrice: Double, unit: String) -> Unit)? = null,
    onNavigateToProducts: (() -> Unit)? = null,
    onUpdateStatus: (id: Long, status: String) -> Unit,
    onDeleteInvoice: (id: Long) -> Unit,
    onShareInvoice: (InvoiceEntity) -> Unit = {},
    profile: UserProfileEntity? = null,
    onUpdateBranding: ((logoUri: String, bakeryName: String, phone: String, email: String, address: String, bankName: String, accountNumber: String, branchCode: String, vatNumber: String, defaultHourlyRate: Double) -> Unit)? = null
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showBrandingDialog by remember { mutableStateOf(false) }
    var invoiceToSend by remember { mutableStateOf<InvoiceEntity?>(null) }

    val filteredInvoices = remember(invoices, selectedFilter, searchQuery) {
        invoices.filter { invoice ->
            val matchesFilter = when (selectedFilter) {
                "Paid" -> invoice.status == "Paid"
                "Pending" -> invoice.status == "Pending"
                "Overdue" -> invoice.status == "Overdue"
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                invoice.clientName.contains(searchQuery, ignoreCase = true) ||
                invoice.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                invoice.orderDescription.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    val totalBilled = remember(invoices) { invoices.sumOf { it.amount } }
    val totalPending = remember(invoices) { invoices.filter { it.status == "Pending" }.sumOf { it.amount } }
    val totalPaid = remember(invoices) { invoices.filter { it.status == "Paid" }.sumOf { it.amount } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Invoicing",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AmberLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = WarmAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "PRO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarmAmber
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_invoices")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                    }
                },
                actions = {
                    if (onNavigateToProducts != null) {
                        IconButton(onClick = onNavigateToProducts, modifier = Modifier.testTag("btn_goto_products")) {
                            Icon(Icons.Outlined.LocalOffer, contentDescription = "Products & Services", tint = DarkText)
                        }
                    }
                    IconButton(onClick = { showBrandingDialog = true }, modifier = Modifier.testTag("btn_business_branding")) {
                        Icon(Icons.Outlined.Storefront, contentDescription = "Business Branding & Logo", tint = DarkText)
                    }
                    IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.testTag("btn_add_invoice_top")) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "New Invoice", tint = BatchPink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = BatchPink,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New Invoice", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_new_invoice")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
        ) {
            // Business Branding Header Banner
            val biz = profile ?: UserProfileEntity()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { showBrandingDialog = true }
                    .testTag("card_business_branding_header")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (biz.logoUri.isNotBlank() && !biz.logoUri.startsWith("emblem_")) {
                            AsyncImage(
                                model = biz.logoUri,
                                contentDescription = "Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, BatchPink, CircleShape)
                            )
                        } else {
                            Surface(shape = CircleShape, color = BatchPinkContainer, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Storefront, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(biz.bakeryName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                            Text("Tap to edit logo, info & bank details", fontSize = 11.sp, color = MediumText)
                        }
                    }

                    OutlinedButton(
                        onClick = { showBrandingDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Logo & Info", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                    }
                }
            }
            // Metrics cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InvoiceStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Billed",
                    amount = "R${String.format("%,.0f", totalBilled)}",
                    accentColor = SoftBlue
                )
                InvoiceStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Pending",
                    amount = "R${String.format("%,.0f", totalPending)}",
                    accentColor = WarmAmber
                )
                InvoiceStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Collected",
                    amount = "R${String.format("%,.0f", totalPaid)}",
                    accentColor = MintGreen
                )
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search client, invoice # or order...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = LightText) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = LightText)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("input_search_invoices"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BatchPink,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Pending", "Paid", "Overdue").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BatchPink,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = MediumText
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) BatchPink else BorderLight
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredInvoices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = LightText,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No invoices found",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create your first professional bakery invoice.",
                            fontSize = 14.sp,
                            color = LightText
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Create Invoice", color = Color.White)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredInvoices, key = { it.id }) { invoice ->
                        InvoiceItemCard(
                            invoice = invoice,
                            onTogglePaid = {
                                val next = if (invoice.status == "Paid") "Pending" else "Paid"
                                onUpdateStatus(invoice.id, next)
                            },
                            onDelete = { onDeleteInvoice(invoice.id) },
                            onShare = {
                                invoiceToSend = invoice
                                onShareInvoice(invoice)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateInvoiceDialog(
            products = products,
            onDismiss = { showCreateDialog = false },
            onConfirm = { client, phone, desc, amt, due, st, sub, disc, taxRate, taxAmt, lineItems, cost ->
                onCreateInvoice(client, phone, desc, amt, due, st, sub, disc, taxRate, taxAmt, lineItems, cost)
                showCreateDialog = false
            },
            onSaveProductService = onSaveProductService,
            onNavigateToProducts = onNavigateToProducts
        )
    }

    if (showBrandingDialog && onUpdateBranding != null) {
        EditBusinessBrandingDialog(
            profile = profile,
            onDismiss = { showBrandingDialog = false },
            onSave = onUpdateBranding
        )
    }

    invoiceToSend?.let { inv ->
        SendableInvoiceDialog(
            invoice = inv,
            profile = profile,
            onDismiss = { invoiceToSend = null }
        )
    }
}

@Composable
private fun InvoiceStatCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = LightText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InvoiceItemCard(
    invoice: InvoiceEntity,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val statusColor = when (invoice.status) {
        "Paid" -> MintGreen
        "Overdue" -> BatchPink
        else -> WarmAmber
    }
    val statusBg = when (invoice.status) {
        "Paid" -> MintLight
        "Overdue" -> BatchPinkLight
        else -> AmberLight
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = invoice.invoiceNumber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BatchPink
                    )
                    Text(
                        text = invoice.clientName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg
                ) {
                    Text(
                        text = invoice.status,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = invoice.orderDescription,
                fontSize = 13.sp,
                color = MediumText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val invoiceLineItems = invoice.items
            if (invoiceLineItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BackgroundLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = "${invoiceLineItems.size} Line Item${if (invoiceLineItems.size > 1) "s" else ""}:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        invoiceLineItems.take(3).forEach { itm ->
                            val qStr = if (itm.quantity % 1.0 == 0.0) itm.quantity.toInt().toString() else itm.quantity.toString()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• $qStr ${itm.unit} × ${itm.itemName}",
                                    fontSize = 11.sp,
                                    color = MediumText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatZar(itm.lineTotal),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                            }
                        }
                        if (invoiceLineItems.size > 3) {
                            Text(
                                text = "+ ${invoiceLineItems.size - 3} more items...",
                                fontSize = 10.sp,
                                color = BatchPink,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Due: ${invoice.dueDate}",
                        fontSize = 12.sp,
                        color = LightText
                    )
                    Text(
                        text = "R${String.format("%,.2f", invoice.amount)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Toggle Paid button
                    OutlinedButton(
                        onClick = onTogglePaid,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (invoice.status == "Paid") MediumText else MintGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (invoice.status == "Paid") BorderLight else MintGreen
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (invoice.status == "Paid") Icons.Filled.Close else Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (invoice.status == "Paid") "Mark Pending" else "Mark Paid",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Share button
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BatchPinkLight)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = BatchPink,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DividerColor)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = LightText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateInvoiceDialog(
    products: List<ProductServiceEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (
        client: String,
        phone: String,
        desc: String,
        amount: Double,
        due: String,
        status: String,
        subtotal: Double,
        discountAmount: Double,
        taxRatePercent: Double,
        taxAmount: Double,
        items: List<LineItem>,
        totalCost: Double
    ) -> Unit,
    onSaveProductService: ((name: String, description: String, category: String, costPrice: Double, sellingPrice: Double, unit: String) -> Unit)? = null,
    onNavigateToProducts: (() -> Unit)? = null
) {
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var orderDesc by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("25 Sep 2026") }
    var status by remember { mutableStateOf("Pending") }

    // Line items list
    var lineItems by remember { mutableStateOf<List<LineItem>>(emptyList()) }
    var discountText by remember { mutableStateOf("") }
    var taxRatePercent by remember { mutableStateOf(0.0) } // 0% or 15% VAT
    var manualAmountText by remember { mutableStateOf("") }

    var showSelectorDialog by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    // Automatic calculation logic
    val subtotal = remember(lineItems, manualAmountText) {
        if (lineItems.isNotEmpty()) {
            lineItems.sumOf { it.lineTotal }
        } else {
            manualAmountText.toDoubleOrNull() ?: 0.0
        }
    }

    val discountAmount = remember(discountText, subtotal) {
        val d = discountText.toDoubleOrNull() ?: 0.0
        d.coerceAtMost(subtotal)
    }

    val taxableAmount = remember(subtotal, discountAmount) {
        (subtotal - discountAmount).coerceAtLeast(0.0)
    }

    val taxAmount = remember(taxableAmount, taxRatePercent) {
        if (taxRatePercent > 0) taxableAmount * (taxRatePercent / 100.0) else 0.0
    }

    val grandTotal = remember(taxableAmount, taxAmount) {
        taxableAmount + taxAmount
    }

    val totalCost = remember(lineItems) {
        lineItems.sumOf { it.costPrice * it.quantity }
    }

    val estimatedProfit = remember(grandTotal, totalCost) {
        if (grandTotal > 0 && totalCost > 0) grandTotal - totalCost else null
    }

    // Auto-update order description if blank
    val effectiveDescription = remember(orderDesc, lineItems) {
        if (orderDesc.isNotBlank()) orderDesc
        else if (lineItems.isNotEmpty()) {
            lineItems.joinToString(", ") { "${if (it.quantity % 1.0 == 0.0) it.quantity.toInt() else it.quantity}x ${it.itemName}" }
        } else ""
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BatchPinkLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.ReceiptLong,
                                    contentDescription = null,
                                    tint = BatchPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Create Invoice", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkText)
                            Text("Automatic line items & totals", fontSize = 11.sp, color = LightText)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = LightText)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Client Details Section
                    item {
                        Text(
                            text = "CLIENT & INVOICE DETAILS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BatchPink,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = clientName,
                            onValueChange = { clientName = it; hasError = false },
                            label = { Text("Client Name *") },
                            placeholder = { Text("e.g. Lerato Mthembu") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_invoice_client_name")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = clientPhone,
                                onValueChange = { clientPhone = it },
                                label = { Text("Phone / WhatsApp") },
                                placeholder = { Text("082 123 4567") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("input_invoice_phone")
                            )

                            OutlinedTextField(
                                value = dueDate,
                                onValueChange = { dueDate = it },
                                label = { Text("Due Date") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("input_invoice_due_date")
                            )
                        }
                    }

                    // Line Items Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "LINE ITEMS (${lineItems.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BatchPink,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "From Products & Services catalog",
                                    fontSize = 10.sp,
                                    color = MediumText
                                )
                            }

                            Button(
                                onClick = { showSelectorDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_add_line_item_dialog")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add Item", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (lineItems.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundLight,
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Outlined.ShoppingCart,
                                        contentDescription = null,
                                        tint = LightText,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "No line items added yet",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = DarkText
                                    )
                                    Text(
                                        text = "Tap '+ Add Item' to select from your central Products & Services database or add a custom bespoke item.",
                                        fontSize = 11.sp,
                                        color = LightText,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Direct fast button
                                    OutlinedButton(
                                        onClick = { showSelectorDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BatchPink),
                                        border = BorderStroke(1.dp, BatchPink)
                                    ) {
                                        Text("Select Product / Service", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = DividerColor)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Fallback single amount option if user prefers manual entry
                                    Text(
                                        text = "Or enter single manual amount:",
                                        fontSize = 11.sp,
                                        color = LightText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = manualAmountText,
                                        onValueChange = { manualAmountText = it },
                                        label = { Text("Manual Amount (R)") },
                                        placeholder = { Text("e.g. 1500") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(0.8f).testTag("input_manual_amount")
                                    )
                                }
                            }
                        }
                    }

                    // Render Line Items
                    items(lineItems) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.itemName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = DarkText
                                        )
                                        if (item.description.isNotBlank()) {
                                            Text(
                                                text = item.description,
                                                fontSize = 11.sp,
                                                color = MediumText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            lineItems = lineItems.filterNot { it.id == item.id }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = LightText, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = DividerColor)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Quantity Stepper
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(BackgroundLight, RoundedCornerShape(8.dp))
                                            .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (item.quantity > 1) {
                                                    val newQty = item.quantity - 1
                                                    lineItems = lineItems.map {
                                                        if (it.id == item.id) it.copy(quantity = newQty, lineTotal = ((newQty * it.unitPrice) - it.discount).coerceAtLeast(0.0))
                                                        else it
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp), tint = DarkText)
                                        }

                                        val qtyDisplay = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.1f", item.quantity)
                                        Text(
                                            text = "$qtyDisplay ${item.unit}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkText,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                val newQty = item.quantity + 1
                                                lineItems = lineItems.map {
                                                    if (it.id == item.id) it.copy(quantity = newQty, lineTotal = ((newQty * it.unitPrice) - it.discount).coerceAtLeast(0.0))
                                                    else it
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp), tint = DarkText)
                                        }
                                    }

                                    // Editable Unit price for this invoice & line total
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("@ R", fontSize = 11.sp, color = MediumText)
                                            var unitPriceText by remember(item.unitPrice) { mutableStateOf(String.format(Locale.US, "%.2f", item.unitPrice)) }
                                            BasicTextField(
                                                value = unitPriceText,
                                                onValueChange = { str ->
                                                    unitPriceText = str
                                                    val newP = str.toDoubleOrNull() ?: 0.0
                                                    lineItems = lineItems.map {
                                                        if (it.id == item.id) it.copy(unitPrice = newP, lineTotal = ((it.quantity * newP) - it.discount).coerceAtLeast(0.0))
                                                        else it
                                                    }
                                                },
                                                modifier = Modifier
                                                    .width(68.dp)
                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                                    .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    .testTag("input_line_price_${item.id}"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                        if (item.discount > 0) {
                                            Text(
                                                text = "Disc: -${formatZar(item.discount)}",
                                                fontSize = 10.sp,
                                                color = WarmAmber
                                            )
                                        }
                                        Text(
                                            text = formatZar(item.lineTotal),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BatchPink
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Order Notes / Description
                    item {
                        OutlinedTextField(
                            value = orderDesc,
                            onValueChange = { orderDesc = it },
                            label = { Text("Invoice Description / Notes (Optional)") },
                            placeholder = { Text(effectiveDescription.ifBlank { "e.g. Wedding Catering Services" }) },
                            shape = RoundedCornerShape(10.dp),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("input_invoice_desc")
                        )
                    }

                    // Totals and Tax Calculation Section
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BackgroundLight,
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "CALCULATIONS & SUMMARY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BatchPink,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Subtotal
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Subtotal:", fontSize = 13.sp, color = MediumText)
                                    Text(formatZar(subtotal), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Discount Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Discount (R):", fontSize = 13.sp, color = MediumText)
                                    OutlinedTextField(
                                        value = discountText,
                                        onValueChange = { discountText = it },
                                        placeholder = { Text("0.00") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.width(100.dp).height(48.dp).testTag("input_invoice_discount")
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // VAT / Tax Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("VAT Tax:", fontSize = 13.sp, color = MediumText)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (taxRatePercent > 0) BatchPinkLight else BorderLight,
                                            modifier = Modifier.clickable {
                                                taxRatePercent = if (taxRatePercent == 0.0) 15.0 else 0.0
                                            }
                                        ) {
                                            Text(
                                                text = if (taxRatePercent > 0) "15% Standard" else "0% None",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (taxRatePercent > 0) BatchPink else MediumText,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = formatZar(taxAmount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = DividerColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                // Grand Total
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("TOTAL DUE:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text(
                                        text = formatZar(grandTotal),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BatchPink
                                    )
                                }

                                // Profit indicator if costs exist
                                if (estimatedProfit != null && totalCost > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val margin = ((estimatedProfit / grandTotal) * 100).toInt()
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MintLight,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Estimated Profit:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MintGreen)
                                            }
                                            Text(
                                                text = "${formatZar(estimatedProfit)} ($margin% margin)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MintGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (hasError) {
                        item {
                            Text(
                                text = "Please fill in Client Name and add at least one line item or enter an amount.",
                                color = BatchPink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MediumText)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (clientName.isBlank() || grandTotal <= 0.0) {
                                hasError = true
                            } else {
                                onConfirm(
                                    clientName.trim(),
                                    clientPhone.trim(),
                                    effectiveDescription.ifBlank { "Bakery Order" },
                                    grandTotal,
                                    dueDate,
                                    status,
                                    subtotal,
                                    discountAmount,
                                    taxRatePercent,
                                    taxAmount,
                                    lineItems,
                                    totalCost
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_confirm_generate_invoice")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Invoice", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    if (showSelectorDialog) {
        ProductLineItemSelectorDialog(
            products = products,
            onDismiss = { showSelectorDialog = false },
            onSelectProduct = { prod ->
                lineItems = lineItems + LineItem(
                    productId = prod.id,
                    itemName = prod.name,
                    description = prod.description,
                    quantity = 1.0,
                    unit = prod.unit,
                    unitPrice = prod.sellingPrice,
                    discount = 0.0,
                    costPrice = prod.costPrice,
                    lineTotal = prod.sellingPrice
                )
                showSelectorDialog = false
            },
            onAddCustomItem = { name, desc, qty, unit, unitPrice, discount, saveToMaster, category ->
                val lineTot = ((qty * unitPrice) - discount).coerceAtLeast(0.0)
                if (saveToMaster && onSaveProductService != null) {
                    onSaveProductService(name, desc, category, 0.0, unitPrice, unit)
                }
                lineItems = lineItems + LineItem(
                    itemName = name,
                    description = desc,
                    quantity = qty,
                    unit = unit,
                    unitPrice = unitPrice,
                    discount = discount,
                    costPrice = 0.0,
                    lineTotal = lineTot
                )
                showSelectorDialog = false
            },
            onQuickCreateMasterProduct = {
                showSelectorDialog = false
                onNavigateToProducts?.invoke()
            }
        )
    }
}
