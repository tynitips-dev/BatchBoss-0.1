package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceEntity
import com.example.data.local.UserProfileEntity
import com.example.ui.theme.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesListScreen(
    invoices: List<InvoiceEntity>,
    onBack: () -> Unit,
    onCreateInvoice: (clientName: String, phone: String, desc: String, amount: Double, dueDate: String, status: String) -> Unit,
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
            onDismiss = { showCreateDialog = false },
            onConfirm = { client, phone, desc, amt, due, st ->
                onCreateInvoice(client, phone, desc, amt, due, st)
                showCreateDialog = false
            }
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
    onDismiss: () -> Unit,
    onConfirm: (client: String, phone: String, desc: String, amount: Double, due: String, status: String) -> Unit
) {
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var orderDesc by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("25 Sep 2026") }
    var status by remember { mutableStateOf("Pending") }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Bakery Invoice", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it; hasError = false },
                    label = { Text("Client Name *") },
                    placeholder = { Text("e.g. Lerato Mthembu") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = clientPhone,
                    onValueChange = { clientPhone = it },
                    label = { Text("Phone / WhatsApp") },
                    placeholder = { Text("e.g. 082 123 4567") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = orderDesc,
                    onValueChange = { orderDesc = it; hasError = false },
                    label = { Text("Order / Cake Description *") },
                    placeholder = { Text("e.g. 3-Tier Chocolate Drip Wedding Cake") },
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; hasError = false },
                        label = { Text("Amount (R) *") },
                        placeholder = { Text("1850") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (hasError) {
                    Text(
                        text = "Please fill in Client Name, Description, and valid Amount.",
                        color = BatchPink,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (clientName.isBlank() || orderDesc.isBlank() || amt == null || amt <= 0.0) {
                        hasError = true
                    } else {
                        onConfirm(clientName, clientPhone, orderDesc, amt, dueDate, status)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Generate Invoice", color = Color.White, fontWeight = FontWeight.Bold)
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
