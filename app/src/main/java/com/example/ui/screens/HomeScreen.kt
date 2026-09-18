package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.InventoryItemEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.RecipeEntity
import com.example.data.local.UserAccountEntity
import com.example.ui.components.BatchBossBrandLogo
import com.example.ui.components.ProfitSparkline
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recipes: List<RecipeEntity>,
    lowStockItems: List<InventoryItemEntity>,
    invoices: List<InvoiceEntity> = emptyList(),
    userAccount: UserAccountEntity? = null,
    customersCount: Int = 0,
    unreadNotificationsCount: Int,
    timeFrame: String,
    onTimeFrameChanged: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenLowStock: () -> Unit,
    onOpenRecipesList: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onOpenTools: () -> Unit,
    onOpenCustomers: () -> Unit = {},
    onOpenInvoices: () -> Unit = {},
    onOpenScanner: () -> Unit = {}
) {
    var showTimeFrameMenu by remember { mutableStateOf(false) }

    // Dynamic metrics calculated purely from actual data
    val userName = userAccount?.firstName?.ifBlank { userAccount?.fullName }?.takeIf { it.isNotBlank() } ?: "Baker"
    val totalRevenue = invoices.sumOf { it.amount }
    val paidInvoices = invoices.filter { it.status.equals("Paid", ignoreCase = true) }
    val paidRevenue = paidInvoices.sumOf { it.amount }
    val totalOrdersCount = invoices.size
    val avgOrder = if (totalOrdersCount > 0) totalRevenue / totalOrdersCount else 0.0

    val averageRecipeMargin = if (recipes.isNotEmpty()) {
        recipes.map { it.profitMarginPercent }.average()
    } else 0.0

    val estimatedProfit = if (totalRevenue > 0.0 && averageRecipeMargin > 0.0) {
        totalRevenue * (averageRecipeMargin / 100.0)
    } else if (totalRevenue > 0.0) {
        totalRevenue * 0.30
    } else 0.0

    val profitDisplay = if (estimatedProfit > 0.0) "R${String.format("%.2f", estimatedProfit)}" else "R0.00"
    val revenueDisplay = if (totalRevenue > 0.0) "R${String.format("%.2f", totalRevenue)}" else "R0.00"
    val avgOrderDisplay = if (avgOrder > 0.0) "R${String.format("%.2f", avgOrder)}" else "R0.00"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .testTag("screen_home"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // 1. Top Bar Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BatchBossBrandLogo(size = 38)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Task button shortcut
                    IconButton(
                        onClick = onOpenTasks,
                        modifier = Modifier.testTag("btn_home_tasks")
                    ) {
                        Icon(
                            Icons.Outlined.Checklist,
                            contentDescription = "Today's Tasks",
                            tint = DarkText
                        )
                    }

                    // Notification Bell with badge
                    Box {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.testTag("btn_home_notifications")
                        ) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = DarkText
                            )
                        }
                        if (unreadNotificationsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(BatchPink),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unreadNotificationsCount.toString(),
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BatchPinkLight)
                            .clickable(onClick = onOpenTools),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Profile",
                            tint = BatchPink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 2. Greeting
        item {
            Column {
                Text(
                    text = "Good day,",
                    fontSize = 14.sp,
                    color = LightText
                )
                Text(
                    text = "$userName 👋",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
            }
        }

        // 3. "Today's Profit" Highlight Card (Pink Gradient)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_today_profit")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(BatchPink, Color(0xFFFF5278))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Estimated Profit ($timeFrame)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            // Time frame dropdown pill
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.White.copy(alpha = 0.22f),
                                    modifier = Modifier.clickable { showTimeFrameMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = timeFrame,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Select Timeframe",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showTimeFrameMenu,
                                    onDismissRequest = { showTimeFrameMenu = false }
                                ) {
                                    listOf("Today", "This Week", "This Month").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                onTimeFrameChanged(option)
                                                showTimeFrameMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = profitDisplay,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.25f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (estimatedProfit > 0) Icons.Filled.TrendingUp else Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (estimatedProfit > 0) "${paidInvoices.size} paid invoices" else "Based on active orders & margins",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Sparkline chart
                            ProfitSparkline(
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(36.dp),
                                lineColor = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Quick Hub Shortcuts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenCustomers)
                        .testTag("btn_home_customers")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.People, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Customers", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenInvoices)
                        .testTag("btn_home_invoices")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.Receipt, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Invoices", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenScanner)
                        .testTag("btn_home_scanner")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }
                }
            }
        }

        // 4. Sales Summary Grid (Revenue, Profit, Orders, Customers)
        item {
            Column {
                Text(
                    text = "Sales Summary ($timeFrame)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Revenue",
                        value = revenueDisplay,
                        trend = if (totalRevenue > 0) "${invoices.size} recorded" else "R0.00 base",
                        isPositive = totalRevenue > 0,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Est. Profit",
                        value = profitDisplay,
                        trend = if (estimatedProfit > 0) "Dynamic margin" else "R0.00 base",
                        isPositive = estimatedProfit > 0,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Orders / Invoices",
                        value = totalOrdersCount.toString(),
                        trend = if (totalOrdersCount > 0) "$paidRevenue paid" else "0 orders",
                        isPositive = totalOrdersCount > 0,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Customers",
                        value = customersCount.toString(),
                        trend = if (customersCount > 0) "$customersCount active" else "0 registered",
                        isPositive = customersCount > 0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 5. Recent Recipes Header & List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Recipes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Text(
                    text = "View all",
                    fontSize = 13.sp,
                    color = BatchPink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenRecipesList)
                        .padding(4.dp)
                )
            }
        }

        // Recent recipe cards (3 items)
        if (recipes.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenRecipesList)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = LightText, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No recipes created yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(
                            text = "Add your recipes to automatically track ingredient costs and profit margins",
                            fontSize = 12.sp,
                            color = LightText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(recipes.take(3)) { recipe ->
                RecentRecipeCard(
                    recipe = recipe,
                    onClick = { onRecipeClick(recipe.id) }
                )
            }
        }

        // 6. Low Stock Alerts Header & Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Low Stock Alerts",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Text(
                    text = "View all",
                    fontSize = 13.sp,
                    color = BatchPink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenLowStock)
                        .padding(4.dp)
                )
            }
        }

        if (lowStockItems.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MintLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MintGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Inventory healthy! No items below minimum threshold.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkText
                        )
                    }
                }
            }
        } else {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(lowStockItems) { item ->
                        LowStockChip(
                            name = item.name,
                            detail = "Low (${item.currentStock.toInt()}${item.unit} left)",
                            onClick = onOpenLowStock
                        )
                    }
                }
            }
        }

        // Bottom Copyright
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "© 2026 Tada Innovations (Pty) Ltd. All rights reserved. BatchBoss™",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MediumText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    trend: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, fontSize = 12.sp, color = LightText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = trend,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPositive) MintGreen else BatchPink
            )
        }
    }
}

@Composable
private fun RecentRecipeCard(
    recipe: RecipeEntity,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("card_recipe_${recipe.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BatchPinkContainer),
                contentAlignment = Alignment.Center
            ) {
                if (recipe.photoUri.isNotBlank()) {
                    AsyncImage(
                        model = recipe.photoUri,
                        contentDescription = recipe.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(
                            id = if (recipe.category == "Cupcakes") R.drawable.img_cupcake_hero else R.drawable.img_bakery_recipes
                        ),
                        contentDescription = recipe.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Batch: ${recipe.batchSize} • Servings: ${recipe.servings}",
                    fontSize = 12.sp,
                    color = MediumText
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Profit", fontSize = 11.sp, color = LightText)
                Text(
                    text = "${recipe.profitMarginPercent.toInt()}%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BatchPink
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View Details",
                tint = LightText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LowStockChip(
    name: String,
    detail: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(WarmAmber)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Text(text = detail, fontSize = 11.sp, color = LightText)
            }
        }
    }
}
