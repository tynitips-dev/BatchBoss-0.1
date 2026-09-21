package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.data.local.InventoryItemEntity
import com.example.data.remote.AiProductPricingService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun InventoryListScreen(
    allInventory: List<InventoryItemEntity>,
    lowStockItems: List<InventoryItemEntity>,
    initialTab: Int = 0, // 0: All, 1: Low Stock
    onBack: () -> Unit,
    onToggleAlert: (Long) -> Unit,
    onUpdateStockPrice: (Long, Double, Double, Double) -> Unit,
    onAddNewStockItem: (String, Double, Double, Double, String) -> Unit,
    onDeleteStockItem: (Long) -> Unit,
    onUpdateStockPriceDetailed: ((Long, Double, Double, Double, Double, Double) -> Unit)? = null,
    onAddNewStockItemDetailed: ((String, Double, Double, Double, Double, Double, String, String) -> Unit)? = null,
    onScanBarcode: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    val displayedItems = remember(allInventory, lowStockItems, selectedTab, searchQuery) {
        val baseList = when (selectedTab) {
            1 -> lowStockItems
            2 -> allInventory.filter { !it.isLowStock }
            else -> allInventory
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val totalStockValuation = remember(allInventory) {
        allInventory.sumOf { it.currentStock * it.unitPrice }
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
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = if (selectedTab == 1) "Low Stock Alerts" else "Stock & Inventory",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onScanBarcode,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BatchPinkLight)
                                .testTag("btn_scan_barcode_top")
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan Barcode", tint = BatchPink, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { showAddItemDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BatchPink)
                                .testTag("btn_add_stock_item")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Stock Item", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (lowStockItems.isNotEmpty()) {
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
                            onClick = { showPurchaseDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, BatchPink),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_view_purchase_list")
                        ) {
                            Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Purchase List (${lowStockItems.size})", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { showAddItemDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_new_ingredient")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Stock Item", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // Valuation and Metrics Cards
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
                            Text(text = "Total Stock Value", fontSize = 12.sp, color = MediumText)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "R${String.format("%.2f", totalStockValuation)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BatchPink
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = "Items Low", fontSize = 12.sp, color = MediumText)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${lowStockItems.size} alert${if (lowStockItems.size == 1) "" else "s"}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lowStockItems.isNotEmpty()) WarmAmber else DarkText
                            )
                        }
                    }
                }
            }

            // Slim Refined Tabs (All Items / Low Stock / In Stock)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            Triple(0, "All Items", allInventory.size),
                            Triple(1, "Low Stock", lowStockItems.size),
                            Triple(2, "In Stock", (allInventory.size - lowStockItems.size).coerceAtLeast(0))
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
                                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else DarkText
                                    )
                                    if (index == 1 && count > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else WarmAmber)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
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

            // Quick Barcode Ingredient Scanner
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onScanBarcode)
                        .testTag("card_scan_barcode_quick")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BatchPinkLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = BatchPink, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Barcode Ingredient Scanner", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                            Text("Scan package barcodes to auto-fetch pantry ingredients & costs", fontSize = 11.sp, color = MediumText)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = BatchPink) {
                            Text("Scan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search stock & ingredients...", color = LightText, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = LightText, modifier = Modifier.size(20.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_stock_search"),
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

            // Low Stock Warning Banner if on Low Stock tab or low items exist
            if (selectedTab == 1 && lowStockItems.isNotEmpty()) {
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = BatchPink,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "${lowStockItems.size} ingredient${if (lowStockItems.size == 1) "" else "s"} need restocking",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = "Tap 'Edit Price / Stock' to update on-hand quantities or prices.",
                                    fontSize = 11.sp,
                                    color = MediumText
                                )
                            }
                        }
                    }
                }
            }

            // Empty state
            if (displayedItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = LightText, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No stock items found", fontWeight = FontWeight.Bold, color = DarkText, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Add your ingredients or adjust your filter.", color = MediumText, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Items List
            items(displayedItems, key = { it.id }) { item ->
                StockItemCard(
                    item = item,
                    onEditClick = { editingItem = item },
                    onToggleAlert = { onToggleAlert(item.id) },
                    onDeleteClick = { itemToDelete = item }
                )
            }
        }
    }

    // Confirmation Dialog: Delete Stock Item
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
            title = { Text("Delete Ingredient?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text("Are you sure you want to delete \"${itemToDelete?.name}\" from your inventory?") },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = itemToDelete
                        itemToDelete = null
                        toDelete?.let { onDeleteStockItem(it.id) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.testTag("btn_confirm_delete_ingredient")
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

    // Dialog: Edit Stock & Price
    if (editingItem != null) {
        EditStockPriceDialog(
            item = editingItem!!,
            onDismiss = { editingItem = null },
            onSave = { id, unitPrice, currentStock, minStock ->
                onUpdateStockPrice(id, unitPrice, currentStock, minStock)
                editingItem = null
            },
            onDelete = { id ->
                onDeleteStockItem(id)
                editingItem = null
            },
            onSaveDetailed = { id, unitPrice, packagePrice, gramsPerUnit, currentStock, minStock ->
                if (onUpdateStockPriceDetailed != null) {
                    onUpdateStockPriceDetailed(id, unitPrice, packagePrice, gramsPerUnit, currentStock, minStock)
                } else {
                    onUpdateStockPrice(id, unitPrice, currentStock, minStock)
                }
                editingItem = null
            }
        )
    }

    // Dialog: Add New Stock Item
    if (showAddItemDialog) {
        AddStockItemDialog(
            onDismiss = { showAddItemDialog = false },
            onAdd = { name, unitPrice, currentStock, minStock, unit ->
                onAddNewStockItem(name, unitPrice, currentStock, minStock, unit)
                showAddItemDialog = false
            },
            onAddDetailed = { name, unitPrice, packagePrice, gramsPerUnit, currentStock, minStock, unit, cat ->
                if (onAddNewStockItemDetailed != null) {
                    onAddNewStockItemDetailed(name, unitPrice, packagePrice, gramsPerUnit, currentStock, minStock, unit, cat)
                } else {
                    onAddNewStockItem(name, unitPrice, currentStock, minStock, unit)
                }
                showAddItemDialog = false
            }
        )
    }

    // Dialog: Purchase List
    if (showPurchaseDialog) {
        AlertDialog(
            onDismissRequest = { showPurchaseDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = BatchPink)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restock Purchase List", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The following items are below minimum required stock:", fontSize = 13.sp, color = MediumText)
                    lowStockItems.forEach { item ->
                        val needed = (item.minStock - item.currentStock).coerceAtLeast(0.0)
                        val estCost = needed * item.unitPrice
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("• ${item.name}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Need +${if (needed == needed.toInt().toDouble()) needed.toInt().toString() else String.format("%.1f", needed)} ${item.unit}",
                                    fontSize = 11.sp,
                                    color = BatchPink,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (estCost > 0) {
                                Text("~R${String.format("%.2f", estCost)}", fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPurchaseDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurchaseDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun StockItemCard(
    item: InventoryItemEntity,
    onEditClick: () -> Unit,
    onToggleAlert: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    val totalItemValue = item.currentStock * item.unitPrice

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.isLowStock) BatchPinkLight else BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_stock_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (item.isLowStock) AmberLight else MintLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Kitchen,
                            contentDescription = null,
                            tint = if (item.isLowStock) WarmAmber else MintGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Stock: ${if (item.currentStock == item.currentStock.toInt().toDouble()) item.currentStock.toInt().toString() else String.format("%.1f", item.currentStock)} ${item.unit}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.isLowStock) WarmAmber else MediumText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Min: ${if (item.minStock == item.minStock.toInt().toDouble()) item.minStock.toInt().toString() else String.format("%.1f", item.minStock)}",
                                fontSize = 11.sp,
                                color = LightText
                            )
                        }
                        if (item.packagePrice > 0.0 && item.gramsPerUnit > 0.0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pack: R${String.format("%.2f", item.packagePrice)} (${if (item.gramsPerUnit == item.gramsPerUnit.toInt().toDouble()) item.gramsPerUnit.toInt().toString() else item.gramsPerUnit} ${item.unit})",
                                fontSize = 11.sp,
                                color = MediumText
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
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

                    Spacer(modifier = Modifier.height(4.dp))

                    IconButton(
                        onClick = onToggleAlert,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (item.alertEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                            contentDescription = "Alert toggle",
                            tint = if (item.alertEnabled) BatchPink else LightText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = BorderLight)
            Spacer(modifier = Modifier.height(4.dp))

            // Pricing and Valuation Row with Direct Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Sell, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Price: R${String.format("%.2f", item.unitPrice)} / ${item.unit}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }
                    Text(
                        text = "Total Value: R${String.format("%.2f", totalItemValue)}",
                        fontSize = 11.sp,
                        color = MediumText
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("btn_delete_stock_item_${item.id}")
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete Ingredient",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Edit Price Button
                    OutlinedButton(
                        onClick = onEditClick,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_edit_price_${item.id}")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Price", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EditStockPriceDialog(
    item: InventoryItemEntity,
    onDismiss: () -> Unit,
    onSave: (id: Long, unitPrice: Double, currentStock: Double, minStock: Double) -> Unit,
    onDelete: (id: Long) -> Unit,
    onSaveDetailed: ((id: Long, unitPrice: Double, packagePrice: Double, gramsPerUnit: Double, currentStock: Double, minStock: Double) -> Unit)? = null
) {
    var packagePriceText by remember {
        mutableStateOf(if (item.packagePrice > 0.0) String.format("%.2f", item.packagePrice) else "")
    }
    var gramsPerUnitText by remember {
        mutableStateOf(if (item.gramsPerUnit > 0.0) {
            if (item.gramsPerUnit == item.gramsPerUnit.toInt().toDouble()) item.gramsPerUnit.toInt().toString() else item.gramsPerUnit.toString()
        } else "")
    }
    var unitPriceText by remember { mutableStateOf(String.format("%.4f", item.unitPrice)) }
    var currentStockText by remember { mutableStateOf(if (item.currentStock == item.currentStock.toInt().toDouble()) item.currentStock.toInt().toString() else item.currentStock.toString()) }
    var minStockText by remember { mutableStateOf(if (item.minStock == item.minStock.toInt().toDouble()) item.minStock.toInt().toString() else item.minStock.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val parsedPkgPrice = packagePriceText.toDoubleOrNull() ?: 0.0
    val parsedGrams = gramsPerUnitText.toDoubleOrNull() ?: 0.0
    val calculatedCostPerGram = if (parsedGrams > 0) parsedPkgPrice / parsedGrams else (unitPriceText.toDoubleOrNull() ?: item.unitPrice)
    val parsedStock = currentStockText.toDoubleOrNull() ?: 0.0
    val liveValuation = calculatedCostPerGram * parsedStock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditCalendar, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit ${item.name} Stock & Price", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Package Price & Grams in Unit
                Text("Package / Unit Purchase Details", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = packagePriceText,
                        onValueChange = {
                            packagePriceText = it
                            errorMessage = null
                            val p = it.toDoubleOrNull() ?: 0.0
                            val g = gramsPerUnitText.toDoubleOrNull() ?: 0.0
                            if (g > 0) unitPriceText = String.format("%.4f", p / g)
                        },
                        label = { Text("Package Price") },
                        prefix = { Text("R ", fontWeight = FontWeight.Bold, color = BatchPink) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = gramsPerUnitText,
                        onValueChange = {
                            gramsPerUnitText = it
                            errorMessage = null
                            val g = it.toDoubleOrNull() ?: 0.0
                            val p = packagePriceText.toDoubleOrNull() ?: 0.0
                            if (g > 0) unitPriceText = String.format("%.4f", p / g)
                        },
                        label = { Text("Grams in Unit") },
                        suffix = { Text(item.unit, color = MediumText, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Calculated Cost Per Gram / Unit
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BatchPinkContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cost per ${item.unit}:", fontSize = 12.sp, color = DarkText)
                            Text(
                                text = "R${String.format("%.4f", calculatedCostPerGram)} / ${item.unit}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BatchPink
                            )
                        }
                        if (item.unit == "g" && calculatedCostPerGram > 0) {
                            Text(
                                text = "Equivalent to R${String.format("%.2f", calculatedCostPerGram * 1000)} / kg",
                                fontSize = 11.sp,
                                color = MediumText
                            )
                        }
                    }
                }

                // Current stock quantity
                Column {
                    Text("Current Stock (${item.unit})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = currentStockText,
                        onValueChange = { currentStockText = it; errorMessage = null },
                        suffix = { Text(item.unit, color = MediumText, fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_current_stock"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Min stock alert threshold
                Column {
                    Text("Minimum Stock Alert Threshold (${item.unit})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it; errorMessage = null },
                        suffix = { Text(item.unit, color = MediumText, fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_min_stock"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Live valuation
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BackgroundLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Stock Value:", fontSize = 12.sp, color = DarkText)
                        Text(
                            text = "R${String.format("%.2f", liveValuation)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BatchPink
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = unitPriceText.toDoubleOrNull() ?: calculatedCostPerGram
                    val s = currentStockText.toDoubleOrNull()
                    val m = minStockText.toDoubleOrNull()
                    val pkg = packagePriceText.toDoubleOrNull() ?: 0.0
                    val g = gramsPerUnitText.toDoubleOrNull() ?: 0.0

                    if (p < 0) {
                        errorMessage = "Please enter a valid price"
                    } else if (s == null || s < 0) {
                        errorMessage = "Please enter a valid stock quantity"
                    } else if (m == null || m < 0) {
                        errorMessage = "Please enter a valid minimum threshold"
                    } else {
                        if (onSaveDetailed != null) {
                            onSaveDetailed(item.id, p, pkg, g, s, m)
                        } else {
                            onSave(item.id, p, s, m)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                modifier = Modifier.testTag("btn_save_stock_price")
            ) {
                Text("Save Price & Stock")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F)),
                    modifier = Modifier.testTag("btn_delete_from_dialog_${item.id}")
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(32.dp))
            },
            title = { Text("Delete Ingredient?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text("Are you sure you want to permanently delete \"${item.name}\" from your inventory?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(item.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Keep Item")
                }
            }
        )
    }
}

@Composable
fun AddStockItemDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, unitPrice: Double, currentStock: Double, minStock: Double, unit: String) -> Unit,
    onAddDetailed: ((name: String, unitPrice: Double, packagePrice: Double, gramsPerUnit: Double, currentStock: Double, minStock: Double, unit: String, category: String) -> Unit)? = null
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Flour & Grains") }
    var packagePriceText by remember { mutableStateOf("") }
    var gramsPerUnitText by remember { mutableStateOf("") }
    var unitPriceText by remember { mutableStateOf("") }
    var currentStockText by remember { mutableStateOf("") }
    var minStockText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isEstimatingPrice by remember { mutableStateOf(false) }
    var priceNote by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Flour & Grains", "Sugars", "Dairy & Butter", "Eggs", "Chocolates", "Extracts & Flavors", "Leaveners", "Packaging", "Other")
    // Only the products: baking soda, flour, chocolate, bicarbonate of soda, salt, eggs
    val ingredientPresets = listOf(
        "Flour" to ("Flour & Grains" to "g"),
        "Sugar" to ("Sugars" to "g"),
        "Chocolate" to ("Chocolates" to "g"),
        "Baking Soda" to ("Leaveners" to "g"),
        "Bicarbonate of Soda" to ("Leaveners" to "g"),
        "Salt" to ("Other" to "g"),
        "Eggs" to ("Eggs" to "unit")
    )
    val commonUnits = listOf("g", "kg", "ml", "L", "unit", "bottle")

    val parsedPkg = packagePriceText.toDoubleOrNull() ?: 0.0
    val parsedGrams = gramsPerUnitText.toDoubleOrNull() ?: 0.0
    val calculatedCostPerUnit = if (parsedGrams > 0) parsedPkg / parsedGrams else (unitPriceText.toDoubleOrNull() ?: 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Stock & Ingredient", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Quick Presets - Only the 7 key products
                Text("Ingredient Presets (Filtered)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ingredientPresets.size) { idx ->
                        val (presetName, meta) = ingredientPresets[idx]
                        FilterChip(
                            selected = name == presetName,
                            onClick = {
                                name = presetName
                                category = meta.first
                                unit = meta.second
                                when (presetName) {
                                    "Flour" -> {
                                        // Checkers Cake Flour 2.5kg for R35.00
                                        packagePriceText = "35.00"
                                        gramsPerUnitText = "2500"
                                        unitPriceText = String.format("%.4f", 35.0 / 2500.0)
                                        priceNote = "Checkers Retail Benchmark: R35.00 for 2.5kg (R0.014/g)"
                                    }
                                    "Sugar" -> {
                                        packagePriceText = "38.50"
                                        gramsPerUnitText = "2000"
                                        unitPriceText = String.format("%.4f", 38.5 / 2000.0)
                                        priceNote = "Checkers Retail Benchmark: R38.50 for 2kg (R0.019/g)"
                                    }
                                    "Chocolate" -> {
                                        packagePriceText = "42.00"
                                        gramsPerUnitText = "200"
                                        unitPriceText = String.format("%.4f", 42.0 / 200.0)
                                        priceNote = "Checkers Retail Benchmark: R42.00 for 200g (R0.21/g)"
                                    }
                                    "Baking Soda" -> {
                                        packagePriceText = "18.00"
                                        gramsPerUnitText = "200"
                                        unitPriceText = String.format("%.4f", 18.0 / 200.0)
                                        priceNote = "Checkers Retail Benchmark: R18.00 for 200g (R0.09/g)"
                                    }
                                    "Bicarbonate of Soda" -> {
                                        packagePriceText = "16.50"
                                        gramsPerUnitText = "200"
                                        unitPriceText = String.format("%.4f", 16.5 / 200.0)
                                        priceNote = "Checkers Retail Benchmark: R16.50 for 200g (R0.0825/g)"
                                    }
                                    "Salt" -> {
                                        packagePriceText = "12.00"
                                        gramsPerUnitText = "500"
                                        unitPriceText = String.format("%.4f", 12.0 / 500.0)
                                        priceNote = "Checkers Retail Benchmark: R12.00 for 500g (R0.024/g)"
                                    }
                                    "Eggs" -> {
                                        packagePriceText = "68.00"
                                        gramsPerUnitText = "30"
                                        unitPriceText = String.format("%.2f", 68.0 / 30.0)
                                        priceNote = "Checkers Retail Benchmark: R68.00 for 30 eggs (R2.27/egg)"
                                    }
                                }
                            },
                            label = { Text(presetName, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Ingredient / Item Name") },
                    placeholder = { Text("e.g. Flour, Sugar, Chocolate, Eggs") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Checkers AI Price Estimate Button
                OutlinedButton(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = "Please enter or pick an ingredient name first"
                            return@OutlinedButton
                        }
                        coroutineScope.launch {
                            isEstimatingPrice = true
                            try {
                                val estimate = AiProductPricingService.estimateProductPrice(name, "Checkers")
                                packagePriceText = String.format("%.2f", estimate.packagePriceZar)
                                gramsPerUnitText = if (estimate.packageSize == estimate.packageSize.toInt().toDouble()) {
                                    estimate.packageSize.toInt().toString()
                                } else {
                                    String.format("%.1f", estimate.packageSize)
                                }
                                unit = estimate.packageUnit
                                unitPriceText = String.format("%.4f", estimate.unitPricePerBaseGram)
                                priceNote = "Checkers Price: ${estimate.brand} - R${String.format("%.2f", estimate.packagePriceZar)} for ${estimate.packageSize}${estimate.packageUnit} (${estimate.note})"
                            } catch (e: Exception) {
                                errorMessage = "Could not estimate price: ${e.message}"
                            } finally {
                                isEstimatingPrice = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BatchPink)
                ) {
                    if (isEstimatingPrice) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BatchPink)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching Checkers AI Price...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = BatchPink)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Estimate Price with Checkers AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (priceNote != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MintLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MintGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = priceNote!!, fontSize = 11.sp, color = MintGreen, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Unit selection chips
                Text("Unit of Measurement", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonUnits.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u, fontSize = 11.sp) }
                        )
                    }
                }

                // Package Price & Grams in Unit
                Text("Package Price & Grams in Unit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = packagePriceText,
                        onValueChange = {
                            packagePriceText = it
                            errorMessage = null
                            val p = it.toDoubleOrNull() ?: 0.0
                            val g = gramsPerUnitText.toDoubleOrNull() ?: 0.0
                            if (g > 0) unitPriceText = String.format("%.4f", p / g)
                        },
                        label = { Text("Pack Price") },
                        prefix = { Text("R ", fontWeight = FontWeight.Bold, color = BatchPink) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = gramsPerUnitText,
                        onValueChange = {
                            gramsPerUnitText = it
                            errorMessage = null
                            val g = it.toDoubleOrNull() ?: 0.0
                            val p = packagePriceText.toDoubleOrNull() ?: 0.0
                            if (g > 0) unitPriceText = String.format("%.4f", p / g)
                        },
                        label = { Text("Grams in Unit") },
                        suffix = { Text(unit, color = MediumText, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Unit cost calculation preview
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BatchPinkContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Calculated Cost / $unit:", fontSize = 12.sp, color = DarkText)
                            Text(
                                text = "R${String.format("%.4f", calculatedCostPerUnit)} / $unit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BatchPink
                            )
                        }
                        if (unit == "g" && calculatedCostPerUnit > 0) {
                            Text(
                                text = "R${String.format("%.2f", calculatedCostPerUnit * 1000)} / kg",
                                fontSize = 11.sp,
                                color = MediumText
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentStockText,
                        onValueChange = { currentStockText = it; errorMessage = null },
                        label = { Text("Current Stock") },
                        suffix = { Text(unit, color = MediumText, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it; errorMessage = null },
                        label = { Text("Min Stock") },
                        suffix = { Text(unit, color = MediumText, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = if (calculatedCostPerUnit > 0) calculatedCostPerUnit else (unitPriceText.toDoubleOrNull() ?: 0.0)
                    val s = currentStockText.toDoubleOrNull()
                    val m = minStockText.toDoubleOrNull()
                    val pkg = packagePriceText.toDoubleOrNull() ?: (p * (gramsPerUnitText.toDoubleOrNull() ?: 1.0))
                    val g = gramsPerUnitText.toDoubleOrNull() ?: 1.0

                    when {
                        name.isBlank() -> errorMessage = "Please enter an item name"
                        p <= 0 -> errorMessage = "Please enter package price & grams or unit price"
                        s == null || s < 0 -> errorMessage = "Please enter stock quantity"
                        m == null || m < 0 -> errorMessage = "Please enter min threshold"
                        else -> {
                            if (onAddDetailed != null) {
                                onAddDetailed(name.trim(), p, pkg, g, s, m, unit, category)
                            } else {
                                onAdd(name.trim(), p, s, m, unit)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
            ) {
                Text("Add Item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// LowStockScreen forwards to InventoryListScreen with low-stock tab selected
@Composable
fun LowStockScreen(
    lowStockItems: List<InventoryItemEntity>,
    allInventory: List<InventoryItemEntity> = lowStockItems,
    onBack: () -> Unit,
    onToggleAlert: (Long) -> Unit,
    onUpdateStockPrice: (Long, Double, Double, Double) -> Unit = { _, _, _, _ -> },
    onAddNewStockItem: (String, Double, Double, Double, String) -> Unit = { _, _, _, _, _ -> },
    onDeleteStockItem: (Long) -> Unit = {}
) {
    InventoryListScreen(
        allInventory = if (allInventory.isNotEmpty()) allInventory else lowStockItems,
        lowStockItems = lowStockItems,
        initialTab = 1,
        onBack = onBack,
        onToggleAlert = onToggleAlert,
        onUpdateStockPrice = onUpdateStockPrice,
        onAddNewStockItem = onAddNewStockItem,
        onDeleteStockItem = onDeleteStockItem
    )
}
