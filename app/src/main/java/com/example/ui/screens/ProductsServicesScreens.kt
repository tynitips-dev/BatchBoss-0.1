package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import com.example.data.local.ProductPriceHistoryEntity
import com.example.data.local.ProductServiceEntity
import com.example.data.local.RecipeEntity
import com.example.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private fun formatZar(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
    return format.format(amount).replace("ZAR", "R").trim()
}

val STANDARD_UNITS = listOf(
    "Each", "Item", "Dozen", "Kg", "Gram", "Box", "Pack", "Hour", "Service", "Slice", "Batch"
)

val STANDARD_CATEGORIES = listOf(
    "All", "Cakes", "Cupcakes", "Cookies", "Muffins", "Cake Pops", "Platters", "Decorations", "Services", "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsServicesListScreen(
    products: List<ProductServiceEntity>,
    recipes: List<RecipeEntity> = emptyList(),
    onBack: () -> Unit,
    onAddProductClick: () -> Unit,
    onEditProductClick: (ProductServiceEntity) -> Unit,
    onDeleteProductClick: (Long) -> Unit,
    onViewPriceHistory: (ProductServiceEntity) -> Unit,
    onNavigateToInvoices: () -> Unit = {},
    onNavigateToQuotes: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showActiveOnly by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, searchQuery, selectedCategory, showActiveOnly) {
        products.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.sku.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true)
            val matchesActive = !showActiveOnly || item.isActive
            matchesCategory && matchesSearch && matchesActive
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Products & Services",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = DarkText
                        )
                        Text(
                            text = "Master Selling Prices • Single Source of Truth",
                            fontSize = 12.sp,
                            color = MediumText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_products")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onAddProductClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BatchPink,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_top_add_product")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Add Product/Service", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProductClick,
                containerColor = BatchPink,
                contentColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.testTag("fab_add_product")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ Add Product/Service", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
        ) {
            // Search Bar & Filter Strip
            Surface(
                color = CardBackground,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, SKU, category...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MediumText)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MediumText)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_products"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BatchPink,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(STANDARD_CATEGORIES) { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = category,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BatchPinkLight,
                                    selectedLabelColor = BatchPink
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) BatchPink else BorderLight
                                )
                            )
                        }
                    }
                }
            }

            // Products List or Empty State
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("empty_products_container"),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(BatchPinkLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.LocalOffer,
                                    contentDescription = null,
                                    tint = BatchPink,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No products or services yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add your first product or service to start creating quotes and invoices faster.",
                                fontSize = 13.sp,
                                color = MediumText,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onAddProductClick,
                                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("btn_empty_add_product")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Add Product/Service", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${filteredProducts.size} items in master catalog",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MediumText
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showActiveOnly = !showActiveOnly }
                            ) {
                                Checkbox(
                                    checked = showActiveOnly,
                                    onCheckedChange = { showActiveOnly = it },
                                    colors = CheckboxDefaults.colors(checkedColor = BatchPink)
                                )
                                Text("Active only", fontSize = 12.sp, color = DarkText)
                            }
                        }
                    }

                    items(filteredProducts, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            onEdit = { onEditProductClick(product) },
                            onDelete = { onDeleteProductClick(product.id) },
                            onPriceHistory = { onViewPriceHistory(product) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductItemCard(
    product: ProductServiceEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPriceHistory: () -> Unit
) {
    val estimatedProfit = (product.sellingPrice - product.costPrice).coerceAtLeast(0.0)
    val marginPercent = if (product.sellingPrice > 0) {
        ((product.sellingPrice - product.costPrice) / product.sellingPrice * 100).coerceAtLeast(0.0)
    } else 0.0

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, if (product.isActive) BorderLight else BorderLight.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_product_${product.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Header Row: Category Badge, Service/Product, Active pill, Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = BatchPinkLight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = product.category,
                            color = BatchPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (product.isService) {
                        Surface(
                            color = Color(0xFFEDE7F6),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Service",
                                color = Color(0xFF5E35B1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (!product.isActive) {
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Inactive",
                                color = Color(0xFFC62828),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPriceHistory,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_history_${product.id}")
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Price History",
                            tint = MediumText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_edit_${product.id}")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Product",
                            tint = MediumText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_delete_${product.id}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Product",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Product Name & SKU
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (product.sku.isNotBlank()) {
                        Text(
                            text = "SKU: ${product.sku}",
                            fontSize = 11.sp,
                            color = MediumText
                        )
                    }
                    if (product.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = product.description,
                            fontSize = 12.sp,
                            color = MediumText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Selling Price Display (The Single Source of Truth)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatZar(product.sellingPrice),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BatchPink
                    )
                    Text(
                        text = "per ${product.unit}",
                        fontSize = 11.sp,
                        color = MediumText
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Metric Row: Cost Price, Estimated Profit, Margin
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BackgroundLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Cost: ",
                            fontSize = 11.sp,
                            color = MediumText
                        )
                        Text(
                            text = formatZar(product.costPrice),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Profit: ",
                            fontSize = 11.sp,
                            color = MediumText
                        )
                        Text(
                            text = "${formatZar(estimatedProfit)} (${String.format(Locale.US, "%.0f%%", marginPercent)})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (estimatedProfit > 0) MintGreen else MediumText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditProductServiceDialog(
    existing: ProductServiceEntity? = null,
    recipes: List<RecipeEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        category: String,
        sku: String,
        costPrice: Double,
        sellingPrice: Double,
        unit: String,
        isActive: Boolean,
        isService: Boolean,
        linkedRecipeId: Long?,
        changeNotes: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Cakes") }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var costPriceText by remember { mutableStateOf(if (existing != null && existing.costPrice > 0) String.format(Locale.US, "%.2f", existing.costPrice) else "") }
    var sellingPriceText by remember { mutableStateOf(if (existing != null && existing.sellingPrice > 0) String.format(Locale.US, "%.2f", existing.sellingPrice) else "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "Each") }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var isService by remember { mutableStateOf(existing?.isService ?: false) }
    var selectedRecipeId by remember { mutableStateOf<Long?>(existing?.linkedRecipeId) }
    var changeNotes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val costVal = costPriceText.toDoubleOrNull() ?: 0.0
    val sellVal = sellingPriceText.toDoubleOrNull() ?: 0.0
    val profit = (sellVal - costVal).coerceAtLeast(0.0)
    val margin = if (sellVal > 0) ((sellVal - costVal) / sellVal * 100).coerceAtLeast(0.0) else 0.0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("dialog_add_edit_product")
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
                    Text(
                        text = if (existing == null) "New Product / Service" else "Edit Product / Service",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DarkText
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MediumText)
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = BatchPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Type Toggle (Product vs Service)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BackgroundLight)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { isService = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isService) BatchPink else Color.Transparent,
                                    contentColor = if (!isService) Color.White else DarkText
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Product (Cake, Bakes)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { isService = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isService) BatchPink else Color.Transparent,
                                    contentColor = if (isService) Color.White else DarkText
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Service (Delivery, Decor)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Optional Recipe Link
                    if (recipes.isNotEmpty() && !isService) {
                        item {
                            Text("Link to Recipe (Optional)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedRecipeId == null,
                                        onClick = { selectedRecipeId = null },
                                        label = { Text("None (Standalone)", fontSize = 11.sp) }
                                    )
                                }
                                items(recipes) { r ->
                                    val isLinked = selectedRecipeId == r.id
                                    FilterChip(
                                        selected = isLinked,
                                        onClick = {
                                            selectedRecipeId = r.id
                                            if (name.isBlank()) name = r.name
                                            // auto populate cost from recipe's calculated cost!
                                            val recipeTotalCost = r.labourCost + r.packagingCost + r.overheadsCost + r.utilitiesCost
                                            if (costPriceText.isBlank() || costPriceText == "0.00") {
                                                costPriceText = String.format(Locale.US, "%.2f", recipeTotalCost.coerceAtLeast(0.0))
                                            }
                                        },
                                        label = { Text(r.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    // Name
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; errorMessage = null },
                            label = { Text("Product/Service Name *") },
                            placeholder = { Text("e.g. Chocolate Cake, Wedding Tasting, Delivery Fee") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_product_name"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Category
                    item {
                        Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(STANDARD_CATEGORIES.filter { it != "All" }) { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // SKU & Unit
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = sku,
                                onValueChange = { sku = it },
                                label = { Text("SKU / Code") },
                                placeholder = { Text("e.g. CC-001") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // Unit Dropdown / Chips
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = { unit = it },
                                    label = { Text("Unit") },
                                    placeholder = { Text("Each, Box, Hour") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    // Unit quick select chips
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(STANDARD_UNITS) { u ->
                                FilterChip(
                                    selected = unit.equals(u, ignoreCase = true),
                                    onClick = { unit = u },
                                    label = { Text(u, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    // Cost Price & Master Selling Price
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = costPriceText,
                                onValueChange = { costPriceText = it },
                                label = { Text("Cost Price (R)") },
                                placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = sellingPriceText,
                                onValueChange = { sellingPriceText = it; errorMessage = null },
                                label = { Text("Selling Price (R) *") },
                                placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_product_selling_price"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                    }

                    // Profit Preview Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BatchPinkLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Estimated Profit", fontSize = 11.sp, color = DarkText)
                                    Text(
                                        text = formatZar(profit),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (profit > 0) MintGreen else DarkText
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Profit Margin", fontSize = 11.sp, color = DarkText)
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", margin),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (profit > 0) MintGreen else DarkText
                                    )
                                }
                            }
                        }
                    }

                    // Description
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (Optional)") },
                            placeholder = { Text("Ingredients, flavor profile, or package details...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2,
                            maxLines = 3
                        )
                    }

                    // Active Switch
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Active in Price List", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DarkText)
                                Text("Visible when creating quotes and invoices", fontSize = 12.sp, color = MediumText)
                            }
                            Switch(
                                checked = isActive,
                                onCheckedChange = { isActive = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = BatchPink, checkedTrackColor = BatchPinkLight)
                            )
                        }
                    }

                    if (existing != null && kotlin.math.abs(existing.sellingPrice - sellVal) > 0.001) {
                        item {
                            OutlinedTextField(
                                value = changeNotes,
                                onValueChange = { changeNotes = it },
                                label = { Text("Price Change Reason (For History)") },
                                placeholder = { Text("e.g. Butter price hike, annual rate revision") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                    }
                }

                // Save Action
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = "Please enter a product or service name"
                            return@Button
                        }
                        if (sellVal <= 0.0) {
                            errorMessage = "Please enter a valid selling price greater than R0.00"
                            return@Button
                        }
                        onSave(
                            name.trim(),
                            description.trim(),
                            category.trim(),
                            sku.trim(),
                            costVal,
                            sellVal,
                            unit.trim().ifBlank { "Each" },
                            isActive,
                            isService,
                            selectedRecipeId,
                            changeNotes.trim()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_save_product")
                ) {
                    Text(
                        text = if (existing == null) "Save to Products & Services" else "Update Product & Price",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PriceHistoryDialog(
    product: ProductServiceEntity,
    historyList: List<ProductPriceHistoryEntity>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .testTag("dialog_price_history")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Price History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkText)
                        Text(product.name, fontSize = 13.sp, color = MediumText)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MediumText)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Current Master Price banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BatchPinkLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Master Price", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                        Text(
                            text = "${formatZar(product.sellingPrice)} / ${product.unit}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BatchPink
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No previous price changes recorded yet.\nAny selling price updates will be logged here.",
                            fontSize = 13.sp,
                            color = MediumText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(historyList) { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BackgroundLight,
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.dateChanged.ifBlank { "Date not recorded" },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DarkText
                                        )
                                        if (entry.notes.isNotBlank()) {
                                            Text(
                                                text = entry.notes,
                                                fontSize = 11.sp,
                                                color = MediumText
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (entry.previousPrice > 0) {
                                                Text(
                                                    text = formatZar(entry.previousPrice),
                                                    fontSize = 12.sp,
                                                    color = MediumText
                                                )
                                                Text(
                                                    text = " → ",
                                                    fontSize = 12.sp,
                                                    color = MediumText
                                                )
                                            }
                                            Text(
                                                text = formatZar(entry.newPrice),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BatchPink
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fast, searchable Product / Service selector dialog for Quotes, Estimates, and Invoices.
 */
@Composable
fun ProductLineItemSelectorDialog(
    products: List<ProductServiceEntity>,
    onDismiss: () -> Unit,
    onSelectProduct: (ProductServiceEntity) -> Unit,
    onAddCustomItem: (
        name: String,
        description: String,
        quantity: Double,
        unit: String,
        unitPrice: Double,
        discount: Double,
        saveToMaster: Boolean,
        category: String
    ) -> Unit,
    onQuickCreateMasterProduct: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Products & Services Catalog, 1: + Add Custom Item
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Custom Item Form State
    var customName by remember { mutableStateOf("") }
    var customDesc by remember { mutableStateOf("") }
    var customQtyText by remember { mutableStateOf("1") }
    var customUnit by remember { mutableStateOf("Each") }
    var customPriceText by remember { mutableStateOf("") }
    var customDiscountText by remember { mutableStateOf("0") }
    var customCategory by remember { mutableStateOf("Cakes") }
    var saveToMaster by remember { mutableStateOf(false) }
    var customError by remember { mutableStateOf<String?>(null) }

    val filtered = remember(products, searchQuery, selectedCategory) {
        products.filter { it.isActive }.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.sku.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .testTag("dialog_product_selector")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Line Item",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DarkText
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MediumText)
                    }
                }

                // Tab Switcher: From Catalog vs Custom Item
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    contentColor = BatchPink,
                    divider = {}
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("From Price List", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("+ Custom Item", fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (activeTab == 0) {
                    // Catalog Search & Picker
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, SKU, category...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MediumText) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MediumText)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_picker_search"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(STANDARD_CATEGORIES) { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (products.none { it.isActive }) "No products saved in master catalog yet." else "No matching items found.",
                                    fontSize = 13.sp,
                                    color = MediumText,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onQuickCreateMasterProduct,
                                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Add to Products & Services", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.id }) { prod ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = BackgroundLight,
                                    border = BorderStroke(1.dp, BorderLight),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectProduct(prod) }
                                        .testTag("picker_item_${prod.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = prod.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DarkText
                                                )
                                                if (prod.sku.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "(${prod.sku})",
                                                        fontSize = 11.sp,
                                                        color = MediumText
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${prod.category} • Unit: ${prod.unit}",
                                                fontSize = 11.sp,
                                                color = MediumText
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = formatZar(prod.sellingPrice),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = BatchPink
                                            )
                                            Text(
                                                text = "Tap to add",
                                                fontSize = 10.sp,
                                                color = BatchPink,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Custom Line Item Form
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            if (customError != null) {
                                Text(customError!!, color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it; customError = null },
                                label = { Text("Item Name *") },
                                placeholder = { Text("e.g. Special Tiered Wedding Cake") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_custom_item_name"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = customDesc,
                                onValueChange = { customDesc = it },
                                label = { Text("Description") },
                                placeholder = { Text("Flavors, design details, notes...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 2
                            )
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customQtyText,
                                    onValueChange = { customQtyText = it },
                                    label = { Text("Qty") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_custom_qty"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = customUnit,
                                    onValueChange = { customUnit = it },
                                    label = { Text("Unit") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customPriceText,
                                    onValueChange = { customPriceText = it; customError = null },
                                    label = { Text("Unit Price (R) *") },
                                    placeholder = { Text("0.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_custom_price"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = customDiscountText,
                                    onValueChange = { customDiscountText = it },
                                    label = { Text("Discount (R)") },
                                    placeholder = { Text("0.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        item {
                            val q = customQtyText.toDoubleOrNull() ?: 1.0
                            val p = customPriceText.toDoubleOrNull() ?: 0.0
                            val d = customDiscountText.toDoubleOrNull() ?: 0.0
                            val lt = ((q * p) - d).coerceAtLeast(0.0)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BatchPinkLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Line Total Calculation", fontSize = 12.sp, color = DarkText)
                                    Text(
                                        text = formatZar(lt),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BatchPink
                                    )
                                }
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BackgroundLight,
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = saveToMaster,
                                        onCheckedChange = { saveToMaster = it },
                                        colors = CheckboxDefaults.colors(checkedColor = BatchPink)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("Save to Products & Services", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DarkText)
                                        Text("Add to master catalog for future reuse", fontSize = 11.sp, color = MediumText)
                                    }
                                }
                            }
                        }

                        if (saveToMaster) {
                            item {
                                Text("Category for Master Catalog", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(STANDARD_CATEGORIES.filter { it != "All" }) { cat ->
                                        FilterChip(
                                            selected = customCategory == cat,
                                            onClick = { customCategory = cat },
                                            label = { Text(cat, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val q = customQtyText.toDoubleOrNull() ?: 1.0
                            val p = customPriceText.toDoubleOrNull() ?: 0.0
                            val d = customDiscountText.toDoubleOrNull() ?: 0.0
                            if (customName.isBlank()) {
                                customError = "Please enter an item name"
                                return@Button
                            }
                            if (p <= 0.0) {
                                customError = "Please enter a unit price"
                                return@Button
                            }
                            onAddCustomItem(
                                customName.trim(),
                                customDesc.trim(),
                                q,
                                customUnit.trim().ifBlank { "Each" },
                                p,
                                d,
                                saveToMaster,
                                customCategory
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_confirm_custom_item")
                    ) {
                        Text("+ Add to Document", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
