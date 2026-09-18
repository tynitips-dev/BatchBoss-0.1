package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BakingSupplyStoreEntity
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class StoreViewMode {
    LIST, MAP
}

enum class StoreSortOption(val label: String) {
    NEAREST("Nearest First"),
    RATING("Highest Rated"),
    NAME("Name (A - Z)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BakingSupplyStoreLocatorScreen(
    stores: List<BakingSupplyStoreEntity>,
    onBack: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddCustomStore: (
        name: String,
        category: String,
        address: String,
        city: String,
        phone: String,
        website: String,
        specialties: String,
        inStockHighlights: String
    ) -> Unit,
    onDeleteStore: (Long) -> Unit = {}
) {
    val context = LocalContext.current

    var viewMode by remember { mutableStateOf(StoreViewMode.LIST) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedMaxRadiusKm by remember { mutableStateOf(50.0) } // 5, 15, 30, 50, 100
    var sortOption by remember { mutableStateOf(StoreSortOption.NEAREST) }
    var showOnlyFavorites by remember { mutableStateOf(false) }

    // Dialog & BottomSheet state
    var showAddStoreDialog by remember { mutableStateOf(false) }
    var selectedStoreForDetails by remember { mutableStateOf<BakingSupplyStoreEntity?>(null) }
    var activeMapSelectedStoreId by remember { mutableStateOf<Long?>(null) }

    // Kitchen base location
    var kitchenArea by remember { mutableStateOf("Rosebank, Johannesburg") }

    val categories = listOf(
        "All",
        "Cake Decorating",
        "Packaging & Boxes",
        "Flour & Grains",
        "Dairy & Chocolate",
        "Tools & Bakeware",
        "Specialty Flavors"
    )

    val radiusOptions = listOf(5.0 to "5 km", 15.0 to "15 km", 30.0 to "30 km", 50.0 to "50 km", 150.0 to "All")

    // Filtered and sorted stores
    val filteredStores = remember(
        stores,
        searchQuery,
        selectedCategory,
        selectedMaxRadiusKm,
        showOnlyFavorites,
        sortOption
    ) {
        stores.filter { store ->
            val matchesCategory = (selectedCategory == "All" || store.category.equals(selectedCategory, ignoreCase = true))
            val matchesFav = (!showOnlyFavorites || store.isFavorite)
            val matchesRadius = (selectedMaxRadiusKm >= 100.0 || store.distanceKm <= selectedMaxRadiusKm)
            val matchesSearch = searchQuery.isBlank() ||
                    store.name.contains(searchQuery, ignoreCase = true) ||
                    store.address.contains(searchQuery, ignoreCase = true) ||
                    store.specialties.contains(searchQuery, ignoreCase = true) ||
                    store.inStockHighlights.contains(searchQuery, ignoreCase = true) ||
                    store.category.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesFav && matchesRadius && matchesSearch
        }.sortedWith { a, b ->
            when (sortOption) {
                StoreSortOption.NEAREST -> a.distanceKm.compareTo(b.distanceKm)
                StoreSortOption.RATING -> b.rating.compareTo(a.rating)
                StoreSortOption.NAME -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .testTag("screen_baking_supply_store_locator"),
        topBar = {
            Surface(
                color = SurfaceWhite,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("btn_store_locator_back")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Baking Supply Locator",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = "Find ingredients, boxes & tools nearby",
                                    fontSize = 12.sp,
                                    color = MediumText
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // View Switcher (List vs Map)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BatchPinkLight,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewMode = if (viewMode == StoreViewMode.LIST) StoreViewMode.MAP else StoreViewMode.LIST
                                    }
                                    .testTag("btn_toggle_store_view_mode")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (viewMode == StoreViewMode.LIST) Icons.Filled.Map else Icons.Filled.FormatListBulleted,
                                        contentDescription = null,
                                        tint = BatchPink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (viewMode == StoreViewMode.LIST) "Map View" else "List View",
                                        color = BatchPink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Add custom local store button
                            IconButton(
                                onClick = { showAddStoreDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(BatchPink)
                                    .testTag("btn_add_custom_store")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add Store",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text("Search stores, fondant, cake boxes, flour...", color = LightText, fontSize = 13.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = BatchPink)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = LightText)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BatchPink,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("input_search_supply_stores")
                    )

                    // Bakery kitchen origin banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = MintGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Base: $kitchenArea",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MediumText
                            )
                        }

                        // Radius pills
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            radiusOptions.forEach { (km, label) ->
                                val isSelected = selectedMaxRadiusKm == km
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) BatchPink else SurfaceWhite,
                                    border = BorderStroke(1.dp, if (isSelected) BatchPink else BorderLight),
                                    modifier = Modifier.clickable { selectedMaxRadiusKm = km }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else DarkText,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Category horizontal chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BatchPink,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceWhite,
                                    labelColor = DarkText
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) BatchPink else BorderLight
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }

                        item {
                            FilterChip(
                                selected = showOnlyFavorites,
                                onClick = { showOnlyFavorites = !showOnlyFavorites },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (showOnlyFavorites) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (showOnlyFavorites) Color.White else BatchPink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Favorites", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BatchPink,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceWhite,
                                    labelColor = DarkText
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = showOnlyFavorites,
                                    borderColor = if (showOnlyFavorites) BatchPink else BorderLight
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (viewMode) {
                StoreViewMode.LIST -> {
                    StoreListView(
                        stores = filteredStores,
                        sortOption = sortOption,
                        onSortChange = { sortOption = it },
                        onStoreClick = { store -> selectedStoreForDetails = store },
                        onToggleFavorite = onToggleFavorite,
                        onCallStore = { phone ->
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            try {
                                context.startActivity(dialIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDirections = { store ->
                            val uri = Uri.parse("geo:${store.lat},${store.lng}?q=${Uri.encode("${store.name}, ${store.address}")}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("${store.name}, ${store.address}")}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                            }
                        },
                        onOpenWebsite = { url ->
                            if (url.isNotBlank()) {
                                val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open website", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                StoreViewMode.MAP -> {
                    StoreMapRadarView(
                        stores = filteredStores,
                        selectedStoreId = activeMapSelectedStoreId,
                        onSelectStoreId = { id -> activeMapSelectedStoreId = id },
                        onOpenDetails = { store -> selectedStoreForDetails = store },
                        onCallStore = { phone ->
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            try {
                                context.startActivity(dialIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDirections = { store ->
                            val uri = Uri.parse("geo:${store.lat},${store.lng}?q=${Uri.encode("${store.name}, ${store.address}")}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("${store.name}, ${store.address}")}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                            }
                        }
                    )
                }
            }
        }
    }

    // Store Details BottomSheet
    if (selectedStoreForDetails != null) {
        val store = selectedStoreForDetails!!
        ModalBottomSheet(
            onDismissRequest = { selectedStoreForDetails = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            StoreDetailSheetContent(
                store = store,
                onClose = { selectedStoreForDetails = null },
                onToggleFavorite = { onToggleFavorite(store.id) },
                onCall = {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${store.phone}"))
                    try {
                        context.startActivity(dialIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDirections = {
                    val uri = Uri.parse("geo:${store.lat},${store.lng}?q=${Uri.encode("${store.name}, ${store.address}")}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(mapIntent)
                    } catch (e: Exception) {
                        val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("${store.name}, ${store.address}")}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                    }
                },
                onOpenWebsite = {
                    if (store.website.isNotBlank()) {
                        val fullUrl = if (!store.website.startsWith("http://") && !store.website.startsWith("https://")) "https://${store.website}" else store.website
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open website", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }

    // Add Custom Local Store Dialog
    if (showAddStoreDialog) {
        AddCustomStoreDialog(
            onDismiss = { showAddStoreDialog = false },
            onSave = { name, category, address, city, phone, website, specialties, highlights ->
                onAddCustomStore(name, category, address, city, phone, website, specialties, highlights)
                showAddStoreDialog = false
                Toast.makeText(context, "Saved $name to your local store locator!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun StoreListView(
    stores: List<BakingSupplyStoreEntity>,
    sortOption: StoreSortOption,
    onSortChange: (StoreSortOption) -> Unit,
    onStoreClick: (BakingSupplyStoreEntity) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onCallStore: (String) -> Unit,
    onDirections: (BakingSupplyStoreEntity) -> Unit,
    onOpenWebsite: (String) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stores.size} baking supply stores found",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MediumText
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.clickable { sortExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Sort, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(sortOption.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = DarkText)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = LightText, modifier = Modifier.size(16.dp))
                        }
                    }

                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        StoreSortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label, fontSize = 13.sp) },
                                onClick = {
                                    onSortChange(opt)
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (stores.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(BatchPinkLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Storefront, contentDescription = null, tint = BatchPink, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No baking supply stores found", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Try expanding your radius, clearing your search query, or tapping '+ Add Store' to add a local baking shop you know.",
                            fontSize = 12.sp,
                            color = MediumText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(stores, key = { it.id }) { store ->
            BakingStoreCard(
                store = store,
                onClick = { onStoreClick(store) },
                onToggleFavorite = { onToggleFavorite(store.id) },
                onCall = { onCallStore(store.phone) },
                onDirections = { onDirections(store) },
                onWebsite = { onOpenWebsite(store.website) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun BakingStoreCard(
    store: BakingSupplyStoreEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCall: () -> Unit,
    onDirections: () -> Unit,
    onWebsite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("card_store_${store.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Category tag, Distance, and Favorite button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (catColor, catBg) = getCategoryColors(store.category)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = catBg
                    ) {
                        Text(
                            text = store.category,
                            color = catColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F8E9)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.NearMe, contentDescription = null, tint = MintGreen, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${String.format("%.1f", store.distanceKm)} km",
                                color = Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (store.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (store.isFavorite) BatchPink else LightText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Store Name & Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = store.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${store.rating}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = " (${store.reviewCount})",
                        fontSize = 11.sp,
                        color = LightText
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Address & Open hours
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = LightText, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${store.address}, ${store.city}",
                    fontSize = 12.sp,
                    color = MediumText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (store.openingHours.contains("Open", ignoreCase = true)) MintGreen else Color.Red)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = store.openingHours,
                    fontSize = 12.sp,
                    color = if (store.openingHours.contains("Open", ignoreCase = true)) Color(0xFF2E7D32) else MediumText,
                    fontWeight = FontWeight.Medium
                )

                if (store.hasDelivery) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("• 🚚 Delivery Available", fontSize = 11.sp, color = MediumText)
                }
            }

            // Specialties / Key items pill summary
            if (store.specialties.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Specialties:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
                Spacer(modifier = Modifier.height(4.dp))
                val specialtyItems = store.specialties.split(",").map { it.trim() }.take(4)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    specialtyItems.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BackgroundLight,
                            border = BorderStroke(1.dp, BorderLight)
                        ) {
                            Text(
                                text = item,
                                fontSize = 10.sp,
                                color = DarkText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // In-stock highlights
            if (store.inStockHighlights.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BatchPinkLight.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BatchPink, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "In Stock: ${store.inStockHighlights}",
                            fontSize = 11.sp,
                            color = DarkText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Row: Directions, Call, Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Directions button
                Button(
                    onClick = onDirections,
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_directions_${store.id}")
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Directions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Call button
                OutlinedButton(
                    onClick = onCall,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BatchPink),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_call_${store.id}")
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = BatchPink, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Store", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                }

                // Details arrow icon
                IconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BackgroundLight)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "View Details", tint = DarkText, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun StoreMapRadarView(
    stores: List<BakingSupplyStoreEntity>,
    selectedStoreId: Long?,
    onSelectStoreId: (Long) -> Unit,
    onOpenDetails: (BakingSupplyStoreEntity) -> Unit,
    onCallStore: (String) -> Unit,
    onDirections: (BakingSupplyStoreEntity) -> Unit
) {
    val selectedStore = remember(stores, selectedStoreId) {
        stores.firstOrNull { it.id == selectedStoreId } ?: stores.firstOrNull()
    }

    // Animation for radar sweep
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Radar Map Canvas
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2430)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(4.dp, RoundedCornerShape(20.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(stores) {
                                detectTapGestures { offset ->
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val maxRadius = minOf(centerX, centerY) * 0.85f

                                    // Find tapped store pin
                                    stores.forEach { store ->
                                        // Map distance into concentric coordinates
                                        val angleRad = Math.toRadians((store.id * 53 % 360).toDouble())
                                        val normalizedDist = (store.distanceKm / 16.0).coerceIn(0.15, 0.95)
                                        val pinX = centerX + (maxRadius * normalizedDist * cos(angleRad)).toFloat()
                                        val pinY = centerY + (maxRadius * normalizedDist * sin(angleRad)).toFloat()

                                        val clickRadius = 40f
                                        val distFromTap = Math.hypot((offset.x - pinX).toDouble(), (offset.y - pinY).toDouble())
                                        if (distFromTap <= clickRadius) {
                                            onSelectStoreId(store.id)
                                            return@detectTapGestures
                                        }
                                    }
                                }
                            }
                    ) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val maxRadius = minOf(centerX, centerY) * 0.85f

                        // Draw background grid lines
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, size.height),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 1f
                        )

                        // Draw concentric distance rings (5km, 10km, 15km)
                        val rings = listOf(0.33f to "5 km", 0.66f to "10 km", 0.95f to "15+ km")
                        rings.forEach { (fraction, _) ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.12f),
                                radius = maxRadius * fraction,
                                center = Offset(centerX, centerY),
                                style = Stroke(
                                    width = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                        }

                        // Animated pulse wave around center
                        drawCircle(
                            color = BatchPink.copy(alpha = (1.3f - pulseScale).coerceIn(0f, 0.4f)),
                            radius = maxRadius * 0.25f * pulseScale,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 2.5f)
                        )

                        // Center: Baker's Kitchen
                        drawCircle(
                            color = BatchPink,
                            radius = 16f,
                            center = Offset(centerX, centerY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6f,
                            center = Offset(centerX, centerY)
                        )

                        // Draw Store Pins on Map
                        stores.forEach { store ->
                            val isSelected = store.id == selectedStore?.id
                            val angleRad = Math.toRadians((store.id * 53 % 360).toDouble())
                            val normalizedDist = (store.distanceKm / 16.0).coerceIn(0.15, 0.95)
                            val pinX = centerX + (maxRadius * normalizedDist * cos(angleRad)).toFloat()
                            val pinY = centerY + (maxRadius * normalizedDist * sin(angleRad)).toFloat()

                            val (catColor, _) = getCategoryColors(store.category)

                            if (isSelected) {
                                // Highlight ring
                                drawCircle(
                                    color = Color.White,
                                    radius = 24f,
                                    center = Offset(pinX, pinY),
                                    style = Stroke(width = 3f)
                                )
                                drawCircle(
                                    color = catColor.copy(alpha = 0.4f),
                                    radius = 28f,
                                    center = Offset(pinX, pinY)
                                )
                            }

                            // Pin body
                            drawCircle(
                                color = if (isSelected) catColor else catColor.copy(alpha = 0.9f),
                                radius = if (isSelected) 16f else 12f,
                                center = Offset(pinX, pinY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = if (isSelected) 6f else 4f,
                                center = Offset(pinX, pinY)
                            )
                        }
                    }

                    // Legend Overlay
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BatchPink))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Your Bakery Kitchen", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Tap pin to view store", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }

                    // Distance label badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Radar Scale: 15 km Radius",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Selected Store Card
            if (selectedStore != null) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetails(selectedStore) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (catColor, catBg) = getCategoryColors(selectedStore.category)
                            Surface(shape = RoundedCornerShape(6.dp), color = catBg) {
                                Text(
                                    text = selectedStore.category,
                                    color = catColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.NearMe, contentDescription = null, tint = MintGreen, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${String.format("%.1f", selectedStore.distanceKm)} km away",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedStore.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = DarkText,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("${selectedStore.rating}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                            }
                        }

                        Text(
                            text = "${selectedStore.address}, ${selectedStore.city}",
                            fontSize = 11.sp,
                            color = MediumText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onDirections(selectedStore) },
                                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Icon(Icons.Filled.Directions, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Directions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { onCallStore(selectedStore.phone) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BatchPink),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Icon(Icons.Filled.Phone, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                            }

                            Button(
                                onClick = { onOpenDetails(selectedStore) },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundLight),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Details", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreDetailSheetContent(
    store: BakingSupplyStoreEntity,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCall: () -> Unit,
    onDirections: () -> Unit,
    onOpenWebsite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (catColor, catBg) = getCategoryColors(store.category)
            Surface(shape = RoundedCornerShape(8.dp), color = catBg) {
                Text(
                    text = store.category,
                    color = catColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (store.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (store.isFavorite) BatchPink else LightText
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = LightText)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = store.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${store.rating} Rating (${store.reviewCount} reviews)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkText
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "•  ${String.format("%.1f", store.distanceKm)} km from bakery",
                fontSize = 13.sp,
                color = MintGreen,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onDirections,
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Filled.Directions, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Get Directions", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
            }

            OutlinedButton(
                onClick = onCall,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BatchPink),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Call Store", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BatchPink)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Store Information Details
        Text("Store Details & Hours", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(store.address, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkText)
                        Text(store.city, fontSize = 12.sp, color = MediumText)
                    }
                }

                HorizontalDivider(color = BorderLight)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MintGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(store.openingHours, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkText)
                }

                HorizontalDivider(color = BorderLight)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(store.phone, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkText)
                }

                if (store.website.isNotBlank()) {
                    HorizontalDivider(color = BorderLight)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onOpenWebsite)
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = store.website,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = BatchPink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Specialties & Ingredients
        if (store.specialties.isNotBlank()) {
            Text("Baking Supplies & Stock Carried", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
            Spacer(modifier = Modifier.height(8.dp))

            val items = store.specialties.split(",").map { it.trim() }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item, fontSize = 13.sp, color = DarkText)
                    }
                }
            }
        }

        if (store.inStockHighlights.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BatchPinkLight.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = BatchPink, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Popular Items In Stock Today:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(store.inStockHighlights, fontSize = 12.sp, color = DarkText, lineHeight = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun AddCustomStoreDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: String,
        address: String,
        city: String,
        phone: String,
        website: String,
        specialties: String,
        highlights: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Cake Decorating") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Johannesburg") }
    var phone by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var specialties by remember { mutableStateOf("") }
    var highlights by remember { mutableStateOf("") }

    var categoryExpanded by remember { mutableStateOf(false) }

    val categoryList = listOf(
        "Cake Decorating",
        "Packaging & Boxes",
        "Flour & Grains",
        "Dairy & Chocolate",
        "Tools & Bakeware",
        "Specialty Flavors"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AddBusiness, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Local Baking Store", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DarkText)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Store Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Store Category") },
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryExpanded = true }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categoryList.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Suburb *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = specialties,
                    onValueChange = { specialties = it },
                    label = { Text("Key Supplies / Specialties (comma separated)") },
                    placeholder = { Text("e.g. Fondant, Cake Boxes, Callets") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = highlights,
                    onValueChange = { highlights = it },
                    label = { Text("In-Stock Highlights / Notes") },
                    placeholder = { Text("e.g. Best price on 10-inch drums") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && address.isNotBlank()) {
                        onSave(name, category, address, city, phone, website, specialties, highlights)
                    }
                },
                enabled = name.isNotBlank() && address.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
            ) {
                Text("Save Store", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MediumText)
            }
        }
    )
}

fun getCategoryColors(category: String): Pair<Color, Color> {
    return when {
        category.contains("Cake", ignoreCase = true) -> Pair(BatchPink, BatchPinkLight)
        category.contains("Packaging", ignoreCase = true) || category.contains("Box", ignoreCase = true) -> Pair(CoralOrange, Color(0xFFFFE0B2))
        category.contains("Flour", ignoreCase = true) || category.contains("Grain", ignoreCase = true) -> Pair(Color(0xFF8D6E63), Color(0xFFD7CCC8))
        category.contains("Dairy", ignoreCase = true) || category.contains("Chocolate", ignoreCase = true) -> Pair(Color(0xFF4E342E), Color(0xFFEFEBE9))
        category.contains("Tool", ignoreCase = true) || category.contains("Bakeware", ignoreCase = true) -> Pair(Color(0xFF1976D2), Color(0xFFBBDEFB))
        category.contains("Flavor", ignoreCase = true) || category.contains("Extract", ignoreCase = true) -> Pair(Color(0xFF7B1FA2), Color(0xFFE1BEE7))
        else -> Pair(BatchPink, BatchPinkLight)
    }
}
