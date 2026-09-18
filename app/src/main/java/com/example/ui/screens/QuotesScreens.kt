package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.LineItem
import com.example.data.local.ProductServiceEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.UserProfileEntity
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

private fun formatZar(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
    return format.format(amount).replace("ZAR", "R").trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesListScreen(
    quotes: List<QuoteEntity>,
    products: List<ProductServiceEntity> = emptyList(),
    onBack: () -> Unit,
    onCreateQuote: (
        clientName: String,
        phone: String,
        eventType: String,
        eventDate: String,
        itemName: String,
        cost: Double,
        margin: Double,
        quotedPrice: Double,
        status: String,
        docType: String,
        subtotal: Double,
        discountAmount: Double,
        taxRatePercent: Double,
        taxAmount: Double,
        items: List<LineItem>
    ) -> Unit,
    onSaveProductService: ((name: String, description: String, category: String, costPrice: Double, sellingPrice: Double, unit: String) -> Unit)? = null,
    onNavigateToProducts: (() -> Unit)? = null,
    onUpdateStatus: (id: Long, status: String) -> Unit,
    onConvertToInvoice: (QuoteEntity) -> Unit,
    onDeleteQuote: (id: Long) -> Unit,
    profile: UserProfileEntity? = null,
    onUpdateBranding: ((logoUri: String, bakeryName: String, phone: String, email: String, address: String, bankName: String, accountNumber: String, branchCode: String, vatNumber: String, defaultHourlyRate: Double) -> Unit)? = null
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showBrandingDialog by remember { mutableStateOf(false) }
    var quoteToSend by remember { mutableStateOf<QuoteEntity?>(null) }

    val filteredQuotes = remember(quotes, selectedFilter, searchQuery) {
        quotes.filter { quote ->
            val matchesFilter = when (selectedFilter) {
                "Sent" -> quote.status == "Sent"
                "Accepted" -> quote.status == "Accepted"
                "Draft" -> quote.status == "Draft"
                "Declined" -> quote.status == "Declined"
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                quote.clientName.contains(searchQuery, ignoreCase = true) ||
                quote.quoteNumber.contains(searchQuery, ignoreCase = true) ||
                quote.recipeOrItemName.contains(searchQuery, ignoreCase = true) ||
                quote.eventType.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    val totalPipeline = remember(quotes) { quotes.sumOf { it.quotedPrice } }
    val totalAccepted = remember(quotes) { quotes.filter { it.status == "Accepted" }.sumOf { it.quotedPrice } }
    val activeCount = remember(quotes) { quotes.count { it.status == "Sent" || it.status == "Draft" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Quotes & Estimates",
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_quotes")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                    }
                },
                actions = {
                    if (onNavigateToProducts != null) {
                        IconButton(onClick = onNavigateToProducts, modifier = Modifier.testTag("btn_goto_products_quotes")) {
                            Icon(Icons.Outlined.LocalOffer, contentDescription = "Products & Services", tint = DarkText)
                        }
                    }
                    IconButton(onClick = { showBrandingDialog = true }, modifier = Modifier.testTag("btn_business_branding_quotes")) {
                        Icon(Icons.Outlined.Storefront, contentDescription = "Business Branding & Logo", tint = DarkText)
                    }
                    IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.testTag("btn_add_quote_top")) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "New Quote", tint = BatchPink)
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
                text = { Text("New Quote", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_new_quote")
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
                    .testTag("card_quotes_branding_header")
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
                                    .border(1.5.dp, WarmAmber, CircleShape)
                            )
                        } else {
                            Surface(shape = CircleShape, color = AmberLight, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Storefront, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(biz.bakeryName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                            Text("Branded Quotes • Tap to edit info & logo", fontSize = 11.sp, color = MediumText)
                        }
                    }

                    OutlinedButton(
                        onClick = { showBrandingDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Logo & Info", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                    }
                }
            }
            // Metrics row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuoteStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Pipeline",
                    amount = "R${String.format("%,.0f", totalPipeline)}",
                    accentColor = SoftBlue
                )
                QuoteStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Active",
                    amount = "$activeCount Quotes",
                    accentColor = WarmAmber
                )
                QuoteStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Won",
                    amount = "R${String.format("%,.0f", totalAccepted)}",
                    accentColor = MintGreen
                )
            }

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search client, cake type, quote #...") },
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
                    .testTag("input_search_quotes"),
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
                listOf("All", "Sent", "Accepted", "Draft", "Declined").forEach { filter ->
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

            if (filteredQuotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.RequestQuote,
                            contentDescription = null,
                            tint = LightText,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No quotes found",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create profitable custom quotes with margin protection.",
                            fontSize = 14.sp,
                            color = LightText
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Create Quote", color = Color.White)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredQuotes, key = { it.id }) { quote ->
                        QuoteItemCard(
                            quote = quote,
                            onUpdateStatus = { nextStatus -> onUpdateStatus(quote.id, nextStatus) },
                            onConvertToInvoice = { onConvertToInvoice(quote) },
                            onDelete = { onDeleteQuote(quote.id) },
                            onSend = { quoteToSend = quote }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateQuoteDialog(
            products = products,
            onDismiss = { showCreateDialog = false },
            onConfirm = { client, phone, event, date, item, cost, margin, price, status, docType, sub, disc, taxRate, taxAmt, lineItems ->
                onCreateQuote(client, phone, event, date, item, cost, margin, price, status, docType, sub, disc, taxRate, taxAmt, lineItems)
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

    quoteToSend?.let { q ->
        SendableQuoteDialog(
            quote = q,
            profile = profile,
            onDismiss = { quoteToSend = null },
            onConvertToInvoice = { onConvertToInvoice(q) }
        )
    }
}

@Composable
private fun QuoteStatCard(
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
private fun QuoteItemCard(
    quote: QuoteEntity,
    onUpdateStatus: (String) -> Unit,
    onConvertToInvoice: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit
) {
    val statusColor = when (quote.status) {
        "Accepted" -> MintGreen
        "Sent" -> SoftBlue
        "Declined" -> BatchPink
        else -> MediumText
    }
    val statusBg = when (quote.status) {
        "Accepted" -> MintLight
        "Sent" -> BlueLight
        "Declined" -> BatchPinkLight
        else -> DividerColor
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = quote.quoteNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BatchPink
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (quote.docType == "Estimate") AmberLight else PurpleLight
                        ) {
                            Text(
                                text = quote.docType,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (quote.docType == "Estimate") WarmAmber else PurpleAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BackgroundLight
                        ) {
                            Text(
                                text = quote.eventType,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MediumText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = quote.clientName,
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
                        text = quote.status,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = quote.recipeOrItemName,
                fontSize = 13.sp,
                color = MediumText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (quote.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundLight, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quote.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                            Text(
                                text = "${qtyStr}x ${item.itemName} (${item.unit})",
                                fontSize = 12.sp,
                                color = DarkText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatZar(item.lineTotal),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Costing & Profit Margin Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundLight, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Est. Cost: R${String.format("%,.0f", quote.estimatedCost)}",
                    fontSize = 12.sp,
                    color = LightText
                )
                Text(
                    text = "Margin: ${quote.profitMarginPercent.toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MintGreen
                )
                Text(
                    text = "Event: ${quote.eventDate}",
                    fontSize = 12.sp,
                    color = LightText
                )
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
                        text = if (quote.docType == "Estimate") "Estimated Price" else "Quoted Price",
                        fontSize = 11.sp,
                        color = LightText
                    )
                    Text(
                        text = "R${String.format("%,.2f", quote.quotedPrice)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Send / Share Quote Button
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AmberLight)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Send Quote",
                            tint = WarmAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Convert to Invoice Button
                    Button(
                        onClick = onConvertToInvoice,
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPinkLight),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Receipt,
                            contentDescription = null,
                            tint = BatchPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "To Invoice",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BatchPink
                        )
                    }

                    // Status cycle
                    IconButton(
                        onClick = {
                            val next = when (quote.status) {
                                "Draft" -> "Sent"
                                "Sent" -> "Accepted"
                                "Accepted" -> "Declined"
                                else -> "Draft"
                            }
                            onUpdateStatus(next)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DividerColor)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "Cycle Status",
                            tint = MediumText,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete
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
fun CreateQuoteDialog(
    products: List<ProductServiceEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (
        client: String,
        phone: String,
        eventType: String,
        date: String,
        item: String,
        cost: Double,
        margin: Double,
        price: Double,
        status: String,
        docType: String,
        subtotal: Double,
        discountAmount: Double,
        taxRatePercent: Double,
        taxAmount: Double,
        lineItems: List<LineItem>
    ) -> Unit,
    onSaveProductService: ((name: String, description: String, category: String, costPrice: Double, sellingPrice: Double, unit: String) -> Unit)? = null,
    onNavigateToProducts: (() -> Unit)? = null
) {
    var docType by remember { mutableStateOf("Quote") } // "Quote" or "Estimate"
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("Wedding") }
    var eventDate by remember { mutableStateOf("15 Oct 2026") }
    var manualItemName by remember { mutableStateOf("") }
    var manualCostText by remember { mutableStateOf("500") }
    var manualMarginText by remember { mutableStateOf("45") }
    var manualPriceText by remember { mutableStateOf("") }

    var lineItems by remember { mutableStateOf<List<LineItem>>(emptyList()) }
    var showProductSelector by remember { mutableStateOf(false) }
    var showCustomItemDialog by remember { mutableStateOf(false) }

    var discountText by remember { mutableStateOf("0") }
    var isVatEnabled by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    // Computations
    val lineSubtotal = lineItems.sumOf { it.lineTotal }
    val lineCost = lineItems.sumOf { it.costPrice * it.quantity }

    val discountAmount = discountText.toDoubleOrNull() ?: 0.0
    val taxRate = if (isVatEnabled) 15.0 else 0.0

    val subtotal = if (lineItems.isNotEmpty()) lineSubtotal else (manualPriceText.toDoubleOrNull() ?: 0.0)
    val discountedSubtotal = (subtotal - discountAmount).coerceAtLeast(0.0)
    val taxAmount = (discountedSubtotal * taxRate) / 100.0
    val finalTotal = discountedSubtotal + taxAmount
    val totalCost = if (lineItems.isNotEmpty()) lineCost else (manualCostText.toDoubleOrNull() ?: 0.0)
    val calculatedMargin = if (finalTotal > 0) {
        ((finalTotal - totalCost) / finalTotal * 100).coerceAtLeast(0.0)
    } else 0.0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.RequestQuote,
                            contentDescription = null,
                            tint = BatchPink
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New $docType",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = DarkText
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = LightText)
                    }
                }

                // Doc Type Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(BackgroundLight, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Quote", "Estimate").forEach { type ->
                        val isSelected = docType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) BatchPink else Color.Transparent)
                                .clickable { docType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MediumText,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Client Details
                    item {
                        Text(
                            text = "Client & Event Details",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = clientName,
                            onValueChange = { clientName = it; hasError = false },
                            label = { Text("Client Name *") },
                            placeholder = { Text("e.g. Lerato Mthembu") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_quote_client_name")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = clientPhone,
                            onValueChange = { clientPhone = it },
                            label = { Text("Client Phone / WhatsApp") },
                            placeholder = { Text("e.g. +27 82 123 4567") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = eventType,
                                onValueChange = { eventType = it },
                                label = { Text("Event Type") },
                                placeholder = { Text("Wedding, Birthday...") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = eventDate,
                                onValueChange = { eventDate = it },
                                label = { Text("Event Date") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Line items section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Line Items",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = DarkText
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { showProductSelector = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BatchPinkLight),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("btn_add_item_from_catalog_quote")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = BatchPink, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("From Catalog", color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showCustomItemDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("+ Custom", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (lineItems.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BackgroundLight,
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No line items selected yet.",
                                        fontSize = 13.sp,
                                        color = MediumText
                                    )
                                    Text(
                                        text = "Add products/services from your catalog for automatic pricing & line totals, or enter details manually below.",
                                        fontSize = 11.sp,
                                        color = LightText,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = manualItemName,
                                onValueChange = { manualItemName = it; hasError = false },
                                label = { Text("Cake / Order Details *") },
                                placeholder = { Text("e.g. 3-Tier Rustic Semi-Naked Wedding Cake (75 Servings)") },
                                shape = RoundedCornerShape(10.dp),
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = manualCostText,
                                    onValueChange = { manualCostText = it; hasError = false },
                                    label = { Text("Est. Cost (R)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = manualMarginText,
                                    onValueChange = { manualMarginText = it },
                                    label = { Text("Margin %") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualPriceText,
                                onValueChange = { manualPriceText = it },
                                label = { Text("Quoted Selling Price (R) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        items(lineItems.size) { index ->
                            val item = lineItems[index]
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.itemName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = DarkText
                                            )
                                            if (item.description.isNotBlank()) {
                                                Text(
                                                    text = item.description,
                                                    fontSize = 11.sp,
                                                    color = LightText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                lineItems = lineItems.toMutableList().also { it.removeAt(index) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = BatchPink, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Quantity stepper
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val newQty = (item.quantity - 1.0).coerceAtLeast(1.0)
                                                    lineItems = lineItems.toMutableList().also {
                                                        it[index] = item.copy(
                                                            quantity = newQty,
                                                            lineTotal = ((newQty * item.unitPrice) - item.discount).coerceAtLeast(0.0)
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Dec", modifier = Modifier.size(14.dp))
                                            }
                                            Text(
                                                text = "${if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity} ${item.unit}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val newQty = item.quantity + 1.0
                                                    lineItems = lineItems.toMutableList().also {
                                                        it[index] = item.copy(
                                                            quantity = newQty,
                                                            lineTotal = ((newQty * item.unitPrice) - item.discount).coerceAtLeast(0.0)
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Inc", modifier = Modifier.size(14.dp))
                                            }
                                        }

                                        // Editable Unit Price
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("@ R", fontSize = 11.sp, color = MediumText)
                                            var unitPriceText by remember(item.unitPrice) { mutableStateOf(String.format(Locale.US, "%.2f", item.unitPrice)) }
                                            BasicTextField(
                                                value = unitPriceText,
                                                onValueChange = { str ->
                                                    unitPriceText = str
                                                    val newP = str.toDoubleOrNull() ?: 0.0
                                                    lineItems = lineItems.toMutableList().also {
                                                        it[index] = item.copy(
                                                            unitPrice = newP,
                                                            lineTotal = ((item.quantity * newP) - item.discount).coerceAtLeast(0.0)
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .width(60.dp)
                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                                    .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.SemiBold)
                                            )
                                        }

                                        Text(
                                            text = formatZar(item.lineTotal),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = DarkText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Discounts & Tax Settings
                    item {
                        HorizontalDivider(color = DividerColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Discounts & Tax",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = discountText,
                                onValueChange = { discountText = it },
                                label = { Text("Discount (R)") },
                                placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isVatEnabled = !isVatEnabled }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = isVatEnabled,
                                    onCheckedChange = { isVatEnabled = it },
                                    colors = CheckboxDefaults.colors(checkedColor = BatchPink)
                                )
                                Text("Add 15% VAT", fontSize = 12.sp, color = DarkText)
                            }
                        }
                    }

                    // Summary Breakdown Box
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundLight,
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Subtotal", fontSize = 12.sp, color = MediumText)
                                    Text(formatZar(subtotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                                }
                                if (discountAmount > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Discount", fontSize = 12.sp, color = MintGreen)
                                        Text("- ${formatZar(discountAmount)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MintGreen)
                                    }
                                }
                                if (isVatEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("VAT (15%)", fontSize = 12.sp, color = MediumText)
                                        Text(formatZar(taxAmount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                                    }
                                }
                                HorizontalDivider(color = DividerColor)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Total $docType", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text(formatZar(finalTotal), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Est. Cost: ${formatZar(totalCost)}", fontSize = 11.sp, color = LightText)
                                    Text("Margin: ${calculatedMargin.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MintGreen)
                                    Text("50% Deposit: ${formatZar(finalTotal * 0.5)}", fontSize = 11.sp, color = MediumText)
                                }
                            }
                        }
                    }

                    if (hasError) {
                        item {
                            Text(
                                text = "Please enter Client Name and provide line items or cake details with price.",
                                color = BatchPink,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = MediumText)
                    }
                    Button(
                        onClick = {
                            val mainItem = when {
                                lineItems.isNotEmpty() -> lineItems.joinToString(", ") { "${if (it.quantity % 1.0 == 0.0) it.quantity.toInt() else it.quantity}x ${it.itemName}" }
                                manualItemName.isNotBlank() -> manualItemName
                                else -> ""
                            }
                            if (clientName.isBlank() || (lineItems.isEmpty() && (manualItemName.isBlank() || finalTotal <= 0.0))) {
                                hasError = true
                            } else {
                                onConfirm(
                                    clientName,
                                    clientPhone,
                                    eventType,
                                    eventDate,
                                    mainItem,
                                    totalCost,
                                    calculatedMargin,
                                    finalTotal,
                                    "Sent",
                                    docType,
                                    subtotal,
                                    discountAmount,
                                    taxRate,
                                    taxAmount,
                                    lineItems
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(2f).testTag("btn_confirm_create_quote")
                    ) {
                        Text("Generate $docType", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Product Selector / Custom Item Dialog
    if (showProductSelector || showCustomItemDialog) {
        ProductLineItemSelectorDialog(
            products = products,
            onDismiss = {
                showProductSelector = false
                showCustomItemDialog = false
            },
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
                showProductSelector = false
                showCustomItemDialog = false
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
                showProductSelector = false
                showCustomItemDialog = false
            },
            onQuickCreateMasterProduct = {
                showProductSelector = false
                showCustomItemDialog = false
                onNavigateToProducts?.invoke()
            }
        )
    }
}
