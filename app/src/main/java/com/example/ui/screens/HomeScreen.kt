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
import com.example.R
import com.example.data.local.InventoryItemEntity
import com.example.data.local.RecipeEntity
import com.example.ui.components.BatchBossBrandLogo
import com.example.ui.components.ProfitSparkline
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recipes: List<RecipeEntity>,
    lowStockItems: List<InventoryItemEntity>,
    unreadNotificationsCount: Int,
    timeFrame: String,
    onTimeFrameChanged: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenLowStock: () -> Unit,
    onOpenRecipesList: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onOpenTools: () -> Unit
) {
    var showTimeFrameMenu by remember { mutableStateOf(false) }

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
                    text = "Good morning,",
                    fontSize = 14.sp,
                    color = LightText
                )
                Text(
                    text = "Tyne 👋",
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
                                text = "Today's Profit",
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
                            text = "R2 450.00",
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
                                        Icons.Filled.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "18% vs yesterday",
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

        // 4. Sales Summary Grid (Revenue, Profit, Orders, Avg Order)
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
                        value = "R8 650",
                        trend = "↑ 15%",
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Profit",
                        value = "R2 450",
                        trend = "↑ 18%",
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Orders",
                        value = "24",
                        trend = "↑ 9%",
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Avg. Order",
                        value = "R360",
                        trend = "↑ 12%",
                        isPositive = true,
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
        items(recipes.take(3)) { recipe ->
            RecentRecipeCard(
                recipe = recipe,
                onClick = { onRecipeClick(recipe.id) }
            )
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
                Image(
                    painter = painterResource(
                        id = if (recipe.category == "Cupcakes") R.drawable.img_cupcake_hero else R.drawable.img_bakery_recipes
                    ),
                    contentDescription = recipe.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
