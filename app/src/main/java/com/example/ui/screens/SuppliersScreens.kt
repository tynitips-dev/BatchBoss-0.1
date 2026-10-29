package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.SpecialDealEntity
import com.example.data.local.SupplierEntity
import com.example.ui.theme.*

@Composable
fun SuppliersListScreen(
    suppliers: List<SupplierEntity>,
    onSupplierClick: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddSupplier: () -> Unit,
    isPremium: Boolean = true,
    onUnlockPremium: () -> Unit = {},
    onOpenStoreLocator: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: My Suppliers, 2: Favourites
    var searchQuery by remember { mutableStateOf("") }

    val filteredSuppliers = remember(suppliers, selectedTab, searchQuery) {
        suppliers.filter { s ->
            val matchesTab = when (selectedTab) {
                1 -> s.isMySupplier
                2 -> s.isFavorite
                else -> true
            }
            val matchesSearch = s.name.contains(searchQuery, ignoreCase = true) ||
                    s.categories.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Suppliers",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        if (!isPremium) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BatchPinkLight
                            ) {
                                Text(
                                    text = "PRO",
                                    color = BatchPink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BatchPinkLight,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenStoreLocator)
                                .testTag("btn_topbar_store_locator")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.NearMe, contentDescription = null, tint = BatchPink, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Store Locator", color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (!isPremium) onUnlockPremium() else onAddSupplier()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BatchPink)
                                .testTag("btn_add_supplier")
                        ) {
                            Icon(
                                imageVector = if (!isPremium) Icons.Filled.Lock else Icons.Filled.Add,
                                contentDescription = "Add Supplier",
                                tint = Color.White
                            )
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Store Locator Banner
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BatchPinkLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenStoreLocator)
                        .testTag("banner_store_locator")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(BatchPinkLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.NearMe, contentDescription = null, tint = BatchPink, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Baking Supply Store Locator", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(6.dp), color = MintGreen.copy(alpha = 0.2f)) {
                                    Text("MAP", color = Color(0xFF2E7D32), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Find local baking shops, cake boxes, flour & tools near you", fontSize = 11.sp, color = MediumText)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                    }
                }
            }
            // PRO Lock Banner if Free
            if (!isPremium) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = BatchPinkLight,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, BatchPink),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onUnlockPremium)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(BatchPink),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Suppliers is a PRO Feature", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text("Unlock wholesale accounts & discount specials", fontSize = 12.sp, color = MediumText)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onUnlockPremium,
                                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Upgrade to Pro to Unlock Suppliers", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // Search Input
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search suppliers...", color = LightText) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = LightText) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BatchPink,
                        unfocusedBorderColor = BorderLight,
                        unfocusedContainerColor = CardBackground,
                        focusedContainerColor = CardBackground
                    ),
                    singleLine = true
                )
            }

            // Tabs: All Suppliers, My Suppliers, Favourites
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All Suppliers", "My Suppliers", "Favourites").forEachIndexed { index, tabTitle ->
                        val isSelected = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) BatchPink else SurfaceWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BatchPink else BorderLight
                            ),
                            modifier = Modifier.clickable { selectedTab = index }
                        ) {
                            Text(
                                text = tabTitle,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DarkText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            items(filteredSuppliers) { supplier ->
                SupplierCard(
                    supplier = supplier,
                    onClick = { onSupplierClick(supplier.id) },
                    onToggleFavorite = { onToggleFavorite(supplier.id) }
                )
            }
        }
    }
}

@Composable
private fun SupplierCard(
    supplier: SupplierEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("card_supplier_${supplier.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BatchPinkContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = BatchPink, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = supplier.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supplier.categories,
                    fontSize = 12.sp,
                    color = LightText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${supplier.rating} (${supplier.reviewCount})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = supplier.openingHours,
                        fontSize = 11.sp,
                        color = MintGreen
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (supplier.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (supplier.isFavorite) BatchPink else LightText
                )
            }
        }
    }
}

@Composable
fun SupplierDetailScreen(
    supplier: SupplierEntity?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onViewSpecials: () -> Unit
) {
    if (supplier == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BatchPink)
        }
        return
    }

    Scaffold(
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onViewSpecials,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_view_specials")
                    ) {
                        Text("View Specials (12)", color = Color.White, fontWeight = FontWeight.Bold)
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
                .testTag("screen_supplier_detail")
        ) {
            // Header Image Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_bakery_recipes),
                        contentDescription = supplier.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                        }

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                imageVector = if (supplier.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (supplier.isFavorite) BatchPink else DarkText
                            )
                        }
                    }
                }
            }

            // Info Section
            item {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = supplier.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = supplier.categories,
                        fontSize = 13.sp,
                        color = LightText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${supplier.rating} (${supplier.reviewCount})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = supplier.openingHours,
                            fontSize = 12.sp,
                            color = MintGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4 Quick Contact Action Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SupplierActionButton(icon = Icons.Outlined.Phone, label = "Call")
                        SupplierActionButton(icon = Icons.Outlined.Email, label = "Email")
                        SupplierActionButton(icon = Icons.Outlined.Language, label = "Website")
                        SupplierActionButton(icon = Icons.Outlined.Directions, label = "Directions")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // About
                    Text(text = "About", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = supplier.about,
                        fontSize = 14.sp,
                        color = MediumText,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Contact Details
                    Text(text = "Contact Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ContactItemRow(icon = Icons.Outlined.Phone, text = supplier.phone)
                            Divider(color = DividerColor)
                            ContactItemRow(icon = Icons.Outlined.Email, text = supplier.email)
                            Divider(color = DividerColor)
                            ContactItemRow(icon = Icons.Outlined.Language, text = supplier.website)
                            Divider(color = DividerColor)
                            ContactItemRow(icon = Icons.Outlined.LocationOn, text = "124 Baker Street, Rosebank, Johannesburg")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplierActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(BatchPinkLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = BatchPink, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 12.sp, color = MediumText)
    }
}

@Composable
private fun ContactItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = LightText, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, fontSize = 13.sp, color = DarkText)
    }
}

@Composable
fun SpecialsListScreen(
    specials: List<SpecialDealEntity>,
    onBack: () -> Unit,
    onSpecialClick: (Long) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Current Specials, 1: Upcoming

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Specials",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Current Specials", "Upcoming").forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) BatchPink else SurfaceWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BatchPink else BorderLight
                            ),
                            modifier = Modifier.clickable { selectedTab = index }
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DarkText,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            items(specials) { special ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSpecialClick(special.id) }
                        .testTag("card_special_${special.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BatchPinkContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = BatchPink, modifier = Modifier.size(28.dp))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = special.productName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "R${String.format("%.2f", special.currentPrice)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BatchPink
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "R${String.format("%.2f", special.originalPrice)}",
                                    fontSize = 12.sp,
                                    color = LightText,
                                    textDecoration = TextDecoration.LineThrough
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = special.validUntil, fontSize = 11.sp, color = LightText)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MintLight
                        ) {
                            val saveAmount = (special.originalPrice - special.currentPrice).coerceAtLeast(0.0)
                            Text(
                                text = "Save R${String.format("%.0f", saveAmount)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpecialDetailScreen(
    special: SpecialDealEntity?,
    onBack: () -> Unit,
    onAddToCart: (Int) -> Unit
) {
    if (special == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BatchPink)
        }
        return
    }

    var quantity by remember { mutableIntStateOf(1) }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Special Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quantity stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BorderLight)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        IconButton(onClick = { if (quantity > 1) quantity-- }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Remove, contentDescription = "Minus", tint = DarkText)
                        }
                        Text(text = "$quantity", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { quantity++ }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Plus", tint = DarkText)
                        }
                    }

                    Button(
                        onClick = { onAddToCart(quantity) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_add_special_to_cart")
                    ) {
                        Text("Add to Shopping List", color = Color.White, fontWeight = FontWeight.Bold)
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BatchPinkContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = BatchPink, modifier = Modifier.size(72.dp))
                    }
                }
            }

            item {
                Text(text = special.productName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkText)

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "R${String.format("%.2f", special.currentPrice)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BatchPink
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "R${String.format("%.2f", special.originalPrice)}",
                        fontSize = 16.sp,
                        color = LightText,
                        textDecoration = TextDecoration.LineThrough
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MintLight) {
                        val saveAmount = (special.originalPrice - special.currentPrice).coerceAtLeast(0.0)
                        Text(
                            text = "Save R${String.format("%.0f", saveAmount)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(text = special.validUntil, fontSize = 13.sp, color = LightText)
            }

            item {
                Text(text = "Description", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = special.description,
                    fontSize = 14.sp,
                    color = MediumText,
                    lineHeight = 20.sp
                )
            }

            item {
                Text(text = "Terms & Conditions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Limited to stock availability.\n• Valid for registered bakery accounts only.\n• Cannot be combined with other promotional vouchers.",
                    fontSize = 13.sp,
                    color = LightText,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
