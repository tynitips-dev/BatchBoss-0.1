package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PackagingItemEntity
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

val SUGGESTED_PACKAGING_CATEGORIES = listOf(
    "Cake boxes",
    "Cupcake boxes",
    "Bento boxes",
    "Cake boards",
    "Ribbon",
    "Stickers and labels",
    "Bags",
    "Containers",
    "Other"
)

private fun formatZar(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
    return format.format(amount).replace("ZAR", "R").trim()
}

fun formatUnitCost(amount: Double): String {
    return if (amount == 0.0) "R0"
    else if (amount % 1.0 == 0.0) "R${amount.toInt()}"
    else String.format(Locale.US, "R%.2f", amount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagingListScreen(
    allPackaging: List<PackagingItemEntity>,
    lowStockPackaging: List<PackagingItemEntity>,
    initialTab: Int = 0, // 0: All, 1: Low Stock, 2: In Stock
    onBack: () -> Unit,
    onToggleAlert: (Long) -> Unit,
    onAddNewPackaging: (
        name: String,
        category: String,
        unit: String,
        packagePrice: Double,
        packageQuantity: Double,
        currentStock: Double,
        minStock: Double,
        supplier: String,
        notes: String
    ) -> Unit,
    onUpdatePackaging: (PackagingItemEntity) -> Unit,
    onUpdateStockAndPrice: (
        id: Long,
        packagePrice: Double,
        packageQuantity: Double,
        currentStock: Double,
        minStock: Double
    ) -> Unit,
    onDeletePackaging: (Long) -> Unit,
    onNavigateToInventory: (() -> Unit)? = null,
    onRefreshSync: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<PackagingItemEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<PackagingItemEntity?>(null) }
    var showRestockDialog by remember { mutableStateOf(false) }

    val totalValuation = remember(allPackaging) {
        allPackaging.sumOf { it.currentStock * it.unitPrice }
    }

    val filteredPackaging = remember(allPackaging, lowStockPackaging, selectedTab, selectedCategoryFilter, searchQuery) {
        val baseList = when (selectedTab) {
            1 -> lowStockPackaging
            2 -> allPackaging.filter { !it.isLowStock }
            else -> allPackaging
        }

        baseList.filter { item ->
            val matchesCategory = selectedCategoryFilter == "All" || item.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.supplier.contains(searchQuery, ignoreCase = true) ||
                item.notes.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("btn_packaging_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column {
                            Text(
                                text = "Packaging & Supplies",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Text(
                                text = "Boxes, boards, ribbons & labels",
                                fontSize = 11.sp,
                                color = LightText
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onRefreshSync != null) {
                            OutlinedButton(
                                onClick = onRefreshSync,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BatchPink),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("btn_sync_packaging")
                            ) {
                                Icon(Icons.Filled.Sync, contentDescription = "Sync", tint = BatchPink, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BatchPink)
                                .testTag("btn_add_packaging_top")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Packaging", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (lowStockPackaging.isNotEmpty()) {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRestockDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, BatchPink),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_packaging_purchase_list")
                        ) {
                            Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restock List (${lowStockPackaging.size})", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_new_packaging_bottom")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Packaging", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
        ) {
            // Valuation & Metrics Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BatchPinkContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = "Packaging Valuation", fontSize = 12.sp, color = MediumText)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatZar(totalValuation),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BatchPink
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = "Low Stock Alerts", fontSize = 12.sp, color = MediumText)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${lowStockPackaging.size} item${if (lowStockPackaging.size == 1) "" else "s"}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lowStockPackaging.isNotEmpty()) WarmAmber else DarkText
                            )
                        }
                    }
                }
            }

            // Quick Jump to Ingredients Inventory if available
            if (onNavigateToInventory != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToInventory)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BatchPinkLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Kitchen, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Looking for Food Ingredients?", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkText)
                                Text("Tap to view baking pantry stock, flours & dairy", fontSize = 11.sp, color = LightText)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Refined Tabs (All Items / Low Stock / In Stock)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            Triple(0, "All Items", allPackaging.size),
                            Triple(1, "Low Stock", lowStockPackaging.size),
                            Triple(2, "In Stock", (allPackaging.size - lowStockPackaging.size).coerceAtLeast(0))
                        )
                        tabs.forEach { (index, title, count) ->
                            val isSelected = selectedTab == index
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) BatchPink else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = index }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else DarkText
                                    )
                                    if (index == 1 && count > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else WarmAmber)
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "$count",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) BatchPink else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == "All",
                            onClick = { selectedCategoryFilter = "All" },
                            label = { Text("All Categories") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BatchPink,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(SUGGESTED_PACKAGING_CATEGORIES) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter.equals(cat, ignoreCase = true),
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BatchPink,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search boxes, ribbons, boards...", color = LightText, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = LightText, modifier = Modifier.size(20.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_packaging_search"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedBorderColor = BatchPink,
                        unfocusedBorderColor = BorderLight
                    ),
                    singleLine = true
                )
            }

            // Low Stock Warning Banner if Low Stock tab is active or low stock items exist
            if (selectedTab == 1 && lowStockPackaging.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BatchPinkContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Warning, contentDescription = null, tint = BatchPink, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${lowStockPackaging.size} packaging item${if (lowStockPackaging.size == 1) "" else "s"} need restocking",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = "Tap 'Edit Price / Stock' to restock your packaging units.",
                                    fontSize = 11.sp,
                                    color = MediumText
                                )
                            }
                        }
                    }
                }
            }

            // Empty state (starts clean for new user per requirement 9)
            if (filteredPackaging.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = LightText, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedCategoryFilter != "All") "No matching packaging found" else "No packaging items yet",
                                fontWeight = FontWeight.Bold,
                                color = DarkText,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add your cake boxes, ribbons, boards and stickers to track stock and calculate invoice costs.",
                                color = LightText,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("btn_empty_add_packaging")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Add First Packaging Item", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Items List
            items(filteredPackaging, key = { it.id }) { item ->
                PackagingItemCard(
                    item = item,
                    onEditClick = { editingItem = item },
                    onToggleAlert = { onToggleAlert(item.id) },
                    onDeleteClick = { itemToDelete = item }
                )
            }
        }
    }

    // Dialog: Add New Packaging
    if (showAddDialog) {
        AddPackagingDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, category, unit, pPrice, pQty, cStock, mStock, supplier, notes ->
                onAddNewPackaging(name, category, unit, pPrice, pQty, cStock, mStock, supplier, notes)
                showAddDialog = false
            }
        )
    }

    // Dialog: Edit Packaging
    if (editingItem != null) {
        EditPackagingDialog(
            item = editingItem!!,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                onUpdatePackaging(updated)
                editingItem = null
            },
            onDelete = { id ->
                onDeletePackaging(id)
                editingItem = null
            }
        )
    }

    // Dialog: Delete Confirmation
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Delete Packaging Item?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text("Are you sure you want to remove \"${itemToDelete?.name}\"? This packaging item will be deleted locally and from the cloud workspace.") },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = itemToDelete
                        itemToDelete = null
                        toDelete?.let { onDeletePackaging(it.id) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.testTag("btn_confirm_delete_packaging")
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Restock Purchase List
    if (showRestockDialog) {
        AlertDialog(
            onDismissRequest = { showRestockDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = BatchPink)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Packaging Restock List", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("The following packaging items are below minimum threshold:", fontSize = 12.sp, color = MediumText)
                    lowStockPackaging.forEach { item ->
                        val needed = (item.minStock - item.currentStock).coerceAtLeast(0.0)
                        val estCost = needed * item.unitPrice
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("• ${item.name}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkText)
                                Text(
                                    text = "Need +${if (needed % 1.0 == 0.0) needed.toInt().toString() else String.format("%.1f", needed)} ${item.unit} (${item.category})",
                                    fontSize = 11.sp,
                                    color = BatchPink,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (estCost > 0) {
                                Text(formatZar(estCost), fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRestockDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun PackagingItemCard(
    item: PackagingItemEntity,
    onEditClick: () -> Unit,
    onToggleAlert: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, if (item.isLowStock) BatchPinkLight else BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_packaging_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Category badge & Low Stock / In Stock badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (item.isLowStock) AmberLight else MintLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.category.lowercase()) {
                                "ribbon" -> Icons.Outlined.ContentCut
                                "stickers and labels" -> Icons.Outlined.Label
                                "bags" -> Icons.Outlined.ShoppingBag
                                else -> Icons.Outlined.Inventory2
                            },
                            contentDescription = null,
                            tint = if (item.isLowStock) WarmAmber else MintGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BackgroundLight
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MediumText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isLowStock) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberLight
                        ) {
                            Text(
                                text = "Low Stock",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarmAmber,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MintLight
                        ) {
                            Text(
                                text = "In Stock",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintGreen,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onToggleAlert, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (item.alertEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                            contentDescription = "Alert toggle",
                            tint = if (item.alertEnabled) BatchPink else LightText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = BorderLight)
            Spacer(modifier = Modifier.height(8.dp))

            // Body Details: Stock, Package Price & Calculated Unit Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Stock: ${if (item.currentStock % 1.0 == 0.0) item.currentStock.toInt().toString() else item.currentStock} ${item.unit}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isLowStock) WarmAmber else DarkText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• Min: ${if (item.minStock % 1.0 == 0.0) item.minStock.toInt().toString() else item.minStock}",
                            fontSize = 11.sp,
                            color = LightText
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pack: ${formatZar(item.packagePrice)} for ${if (item.packageQuantity % 1.0 == 0.0) item.packageQuantity.toInt().toString() else item.packageQuantity} ${item.unit}",
                        fontSize = 11.sp,
                        color = MediumText
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Cost per unit",
                        fontSize = 10.sp,
                        color = LightText
                    )
                    Text(
                        text = "${formatUnitCost(item.unitPrice)} / ${item.unit}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BatchPink,
                        modifier = Modifier.testTag("txt_unit_cost_${item.id}")
                    )
                }
            }

            if (item.supplier.isNotBlank() || item.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (item.supplier.isNotBlank()) {
                        Text(
                            text = "Supplier: ${item.supplier}",
                            fontSize = 10.5.sp,
                            color = MediumText
                        )
                    }
                    if (item.notes.isNotBlank()) {
                        Text(
                            text = item.notes,
                            fontSize = 10.5.sp,
                            color = LightText,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Edit Price & Stock, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = LightText, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BatchPink),
                    border = BorderStroke(1.dp, BatchPink),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp).testTag("btn_edit_packaging_${item.id}")
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Price & Stock", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPackagingDialog(
    onDismiss: () -> Unit,
    onAdd: (
        name: String,
        category: String,
        unit: String,
        packagePrice: Double,
        packageQuantity: Double,
        currentStock: Double,
        minStock: Double,
        supplier: String,
        notes: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SUGGESTED_PACKAGING_CATEGORIES.first()) }
    var unit by remember { mutableStateOf("pcs") }
    var packagePriceText by remember { mutableStateOf("") }
    var packageQuantityText by remember { mutableStateOf("1") }
    var currentStockText by remember { mutableStateOf("0") }
    var minStockText by remember { mutableStateOf("10") }
    var supplier by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    // Live calculated cost per unit
    val packagePrice = packagePriceText.toDoubleOrNull() ?: 0.0
    val packageQuantity = packageQuantityText.toDoubleOrNull() ?: 1.0
    val calculatedCostPerUnit = if (packageQuantity > 0) packagePrice / packageQuantity else 0.0

    val currentStock = currentStockText.toDoubleOrNull() ?: 0.0
    val minStock = minStockText.toDoubleOrNull() ?: 0.0
    val isLowStock = currentStock <= minStock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BatchPinkLight,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Add Packaging Item", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkText)
                    Text("Track boxes, boards, ribbons & cost", fontSize = 11.sp, color = LightText)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; hasError = false },
                    label = { Text("Item Name *") },
                    placeholder = { Text("e.g. 10x10 White Cake Box") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_packaging_name"),
                    singleLine = true
                )

                // Category selector
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                var expandedCategory by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        SUGGESTED_PACKAGING_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        placeholder = { Text("pcs / m / roll") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = packageQuantityText,
                        onValueChange = { packageQuantityText = it },
                        label = { Text("Pack Qty *") },
                        placeholder = { Text("e.g. 50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("input_packaging_pack_qty"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = packagePriceText,
                    onValueChange = { packagePriceText = it },
                    label = { Text("Package Price (R) *") },
                    placeholder = { Text("e.g. 150.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_packaging_pack_price"),
                    singleLine = true
                )

                // Calculated Cost Per Unit Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BatchPinkContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Calculated Cost / Unit:", fontSize = 12.sp, color = MediumText)
                        Text(
                            text = "${formatZar(calculatedCostPerUnit)} / $unit",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BatchPink
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentStockText,
                        onValueChange = { currentStockText = it },
                        label = { Text("Current Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("input_packaging_current_stock"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it },
                        label = { Text("Min Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("input_packaging_min_stock"),
                        singleLine = true
                    )
                }

                // Low stock indicator preview
                if (isLowStock) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmberLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Current stock is at or below minimum warning threshold", fontSize = 10.5.sp, color = WarmAmber, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier (Optional)") },
                    placeholder = { Text("e.g. Cape Packaging Supplies") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("e.g. Dimensions 25x25x15cm with window") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                if (hasError) {
                    Text("Please provide an item name.", color = BatchPink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        hasError = true
                    } else {
                        onAdd(
                            name.trim(),
                            selectedCategory,
                            unit.trim().ifBlank { "pcs" },
                            packagePrice,
                            packageQuantity.coerceAtLeast(0.001),
                            currentStock,
                            minStock,
                            supplier.trim(),
                            notes.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_add_packaging")
            ) {
                Text("Add Packaging", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MediumText)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPackagingDialog(
    item: PackagingItemEntity,
    onDismiss: () -> Unit,
    onSave: (PackagingItemEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var selectedCategory by remember { mutableStateOf(item.category) }
    var unit by remember { mutableStateOf(item.unit) }
    var packagePriceText by remember { mutableStateOf(if (item.packagePrice > 0) item.packagePrice.toString() else "") }
    var packageQuantityText by remember { mutableStateOf(item.packageQuantity.toString()) }
    var currentStockText by remember { mutableStateOf(if (item.currentStock % 1.0 == 0.0) item.currentStock.toInt().toString() else item.currentStock.toString()) }
    var minStockText by remember { mutableStateOf(if (item.minStock % 1.0 == 0.0) item.minStock.toInt().toString() else item.minStock.toString()) }
    var supplier by remember { mutableStateOf(item.supplier) }
    var notes by remember { mutableStateOf(item.notes) }
    var alertEnabled by remember { mutableStateOf(item.alertEnabled) }

    val packagePrice = packagePriceText.toDoubleOrNull() ?: 0.0
    val packageQuantity = packageQuantityText.toDoubleOrNull() ?: 1.0
    val calculatedCostPerUnit = if (packageQuantity > 0) packagePrice / packageQuantity else 0.0

    val currentStock = currentStockText.toDoubleOrNull() ?: 0.0
    val minStock = minStockText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Packaging", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkText)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name *") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_packaging_name"),
                    singleLine = true
                )

                // Category selector
                var expandedCategory by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        SUGGESTED_PACKAGING_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = packageQuantityText,
                        onValueChange = { packageQuantityText = it },
                        label = { Text("Pack Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = packagePriceText,
                    onValueChange = { packagePriceText = it },
                    label = { Text("Package Price (R)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_packaging_price"),
                    singleLine = true
                )

                // Calculated Cost Per Unit Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BatchPinkContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Calculated Cost / Unit:", fontSize = 12.sp, color = MediumText)
                        Text(
                            text = "${formatZar(calculatedCostPerUnit)} / $unit",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BatchPink
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentStockText,
                        onValueChange = { currentStockText = it },
                        label = { Text("Current Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("input_edit_packaging_current_stock"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it },
                        label = { Text("Min Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            name = name.trim().ifBlank { item.name },
                            category = selectedCategory,
                            unit = unit.trim().ifBlank { "pcs" },
                            packagePrice = packagePrice,
                            packageQuantity = packageQuantity.coerceAtLeast(0.001),
                            unitPrice = calculatedCostPerUnit,
                            currentStock = currentStock,
                            minStock = minStock,
                            isLowStock = currentStock <= minStock,
                            supplier = supplier.trim(),
                            notes = notes.trim(),
                            alertEnabled = alertEnabled,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_save_packaging_edit")
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MediumText)
            }
        }
    )
}
