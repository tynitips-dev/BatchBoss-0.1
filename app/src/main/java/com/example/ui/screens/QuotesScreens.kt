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
import com.example.data.local.QuoteEntity
import com.example.data.local.UserProfileEntity
import com.example.ui.theme.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesListScreen(
    quotes: List<QuoteEntity>,
    onBack: () -> Unit,
    onCreateQuote: (clientName: String, phone: String, eventType: String, eventDate: String, itemName: String, cost: Double, margin: Double, quotedPrice: Double, status: String) -> Unit,
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
            onDismiss = { showCreateDialog = false },
            onConfirm = { client, phone, event, date, item, cost, margin, price, status ->
                onCreateQuote(client, phone, event, date, item, cost, margin, price, status)
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
                            color = PurpleLight
                        ) {
                            Text(
                                text = quote.eventType,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PurpleAccent,
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
                        text = "Quoted Price",
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
    onDismiss: () -> Unit,
    onConfirm: (client: String, phone: String, eventType: String, date: String, item: String, cost: Double, margin: Double, price: Double, status: String) -> Unit
) {
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("Wedding") }
    var eventDate by remember { mutableStateOf("15 Oct 2026") }
    var itemName by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("500") }
    var marginText by remember { mutableStateOf("45") }
    var priceText by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    val calculatedPrice = remember(costText, marginText) {
        val cost = costText.toDoubleOrNull() ?: 0.0
        val margin = marginText.toDoubleOrNull() ?: 0.0
        if (margin in 0.0..99.0 && cost > 0.0) {
            cost / (1.0 - (margin / 100.0))
        } else {
            cost * 1.5
        }
    }

    LaunchedEffect(calculatedPrice) {
        if (priceText.isBlank() || priceText.toDoubleOrNull() != null) {
            priceText = String.format("%.0f", calculatedPrice)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.RequestQuote, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Quote & Estimate", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it; hasError = false },
                    label = { Text("Cake / Order Details *") },
                    placeholder = { Text("e.g. 3-Tier Rustic Semi-Naked Wedding Cake (75 Servings)") },
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it; hasError = false },
                        label = { Text("Est. Cost (R) *") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = marginText,
                        onValueChange = { marginText = it },
                        label = { Text("Margin %") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Quoted Selling Price (R)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasError) {
                    Text(
                        text = "Please fill in Client Name, Details, and Cost.",
                        color = BatchPink,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cost = costText.toDoubleOrNull()
                    val margin = marginText.toDoubleOrNull() ?: 40.0
                    val price = priceText.toDoubleOrNull() ?: calculatedPrice
                    if (clientName.isBlank() || itemName.isBlank() || cost == null || cost <= 0.0) {
                        hasError = true
                    } else {
                        onConfirm(clientName, clientPhone, eventType, eventDate, itemName, cost, margin, price, "Sent")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Generate Quote", color = Color.White, fontWeight = FontWeight.Bold)
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
