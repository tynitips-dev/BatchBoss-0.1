package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.RecipeEntity
import com.example.data.local.RecipeIngredientEntity
import com.example.ui.components.ProfitDonutChart
import com.example.ui.theme.*
import com.example.util.UnitUtils

@Composable
fun RecipesListScreen(
    recipes: List<RecipeEntity>,
    onRecipeClick: (Long) -> Unit,
    onCalculatePricingClick: (Long) -> Unit,
    onCreateRecipeClick: () -> Unit,
    isPremium: Boolean = false,
    onUnlockPremium: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Cakes", "Cupcakes", "Cookies", "Breads")

    val filteredRecipes = remember(recipes, searchQuery, selectedCategory) {
        recipes.filter { recipe ->
            val matchesCategory = selectedCategory == "All" || recipe.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = recipe.name.contains(searchQuery, ignoreCase = true) ||
                    recipe.category.contains(searchQuery, ignoreCase = true)
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recipes",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )

                    IconButton(
                        onClick = {
                            if (!isPremium && recipes.size >= 5) {
                                onUnlockPremium()
                            } else {
                                onCreateRecipeClick()
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BatchPink)
                            .testTag("btn_create_recipe")
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Create Recipe",
                            tint = Color.White
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Recipe Capacity / Subscription Status Banner
            item {
                if (!isPremium) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (recipes.size >= 5) AmberLight else SurfaceWhite,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (recipes.size >= 5) WarmAmber else BorderLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (recipes.size >= 5) WarmAmber else BatchPinkLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (recipes.size >= 5) Icons.Filled.Lock else Icons.Outlined.MenuBook,
                                    contentDescription = null,
                                    tint = if (recipes.size >= 5) Color.White else BatchPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (recipes.size >= 5) "Recipe Limit Reached (${recipes.size}/5)" else "Free Plan: ${recipes.size}/5 Recipes Used",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = if (recipes.size >= 5) "Upgrade to Pro to add more than 5 recipes" else "Upgrade for unlimited recipes & cloud backup",
                                    fontSize = 11.sp,
                                    color = MediumText
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BatchPink,
                                modifier = Modifier.clickable(onClick = onUnlockPremium)
                            ) {
                                Text(
                                    text = "Unlock",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BatchPinkLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BatchPink, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pro Plan: Unlimited Recipes Active (${recipes.size} Total)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BatchPink
                            )
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search recipes...", color = LightText) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = LightText)
                    },
                    trailingIcon = {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter", tint = LightText)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_recipe_search"),
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

            // Category Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) BatchPink else SurfaceWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BatchPink else BorderLight
                            ),
                            modifier = Modifier
                                .clickable { selectedCategory = category }
                                .testTag("chip_category_$category")
                        ) {
                            Text(
                                text = category,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DarkText,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Recipe Cards
            items(filteredRecipes) { recipe ->
                RecipeListItemCard(
                    recipe = recipe,
                    onViewClick = { onRecipeClick(recipe.id) },
                    onCalculateClick = { onCalculatePricingClick(recipe.id) }
                )
            }
        }
    }
}

@Composable
fun RecipeListItemCard(
    recipe: RecipeEntity,
    onViewClick: () -> Unit,
    onCalculateClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewClick)
            .testTag("recipe_card_${recipe.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Image(
                    painter = painterResource(
                        id = if (recipe.category == "Cupcakes") R.drawable.img_cupcake_hero else R.drawable.img_bakery_recipes
                    ),
                    contentDescription = recipe.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${recipe.rating} (${recipe.reviewCount})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recipe.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MintLight
                    ) {
                        Text(
                            text = "Profit ${recipe.profitMarginPercent.toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${recipe.servings} Servings • Batch of ${recipe.batchSize} • ${recipe.difficulty} Difficulty",
                    fontSize = 13.sp,
                    color = LightText
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons matching Figma screens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewClick,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("View Recipe", color = BatchPink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onCalculateClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Calculate Pricing", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeDetailScreen(
    recipe: RecipeEntity?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onViewRecipeIngredients: () -> Unit,
    onCalculatePricing: () -> Unit,
    onUpdatePricing: (id: Long, labour: Double, overheads: Double, packaging: Double, utilities: Double, profitMargin: Double, customSellingPrice: Double) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    if (recipe == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BatchPink)
        }
        return
    }

    var showEditPricingDialog by remember { mutableStateOf(false) }

    val batchSize = if (recipe.batchSize > 0) recipe.batchSize else 124
    val totalCosts = recipe.labourCost + recipe.overheadsCost + recipe.packagingCost + recipe.utilitiesCost + 34.01
    val costPerItem = totalCosts / batchSize
    val marginFrac = (recipe.profitMarginPercent / 100.0).coerceIn(0.05, 0.95)
    val sellingPricePerItem = if (recipe.customSellingPrice > 0.0) recipe.customSellingPrice else (costPerItem / (1.0 - marginFrac))

    Scaffold(
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewRecipeIngredients,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_view_ingredients")
                    ) {
                        Text("View Recipe", color = BatchPink, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onCalculatePricing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_calculate_pricing")
                    ) {
                        Text("Calculate Pricing", color = Color.White, fontWeight = FontWeight.Bold)
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
                .testTag("screen_recipe_detail")
        ) {
            // Hero Photo Header with overlay buttons
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    Image(
                        painter = painterResource(
                            id = if (recipe.category == "Cupcakes") R.drawable.img_cupcake_hero else R.drawable.img_bakery_recipes
                        ),
                        contentDescription = recipe.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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

                        Row {
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.85f))
                            ) {
                                Icon(
                                    imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (recipe.isFavorite) BatchPink else DarkText
                                )
                            }
                        }
                    }
                }
            }

            // Recipe Content
            item {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = recipe.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${recipe.rating} (${recipe.reviewCount})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BatchPinkLight
                        ) {
                            Text(
                                text = recipe.category,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BatchPink,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = recipe.description,
                        fontSize = 14.sp,
                        color = MediumText,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3-Stat Metric Row
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${recipe.servings}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Text(text = "Servings", fontSize = 12.sp, color = LightText)
                            }
                            Box(modifier = Modifier.width(1.dp).height(36.dp).background(BorderLight))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${recipe.batchSize}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Text(text = "Batch Size", fontSize = 12.sp, color = LightText)
                            }
                            Box(modifier = Modifier.width(1.dp).height(36.dp).background(BorderLight))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${recipe.profitMarginPercent.toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MintGreen)
                                Text(text = "Profit Margin", fontSize = 12.sp, color = LightText)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cost vs Selling Price Banner
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BatchPinkContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Cost per item", fontSize = 12.sp, color = LightText)
                                    Text(text = "R${String.format("%.2f", costPerItem)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "Selling price", fontSize = 12.sp, color = LightText)
                                    Text(text = "R${String.format("%.2f", sellingPricePerItem)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { showEditPricingDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("btn_edit_recipe_pricing_detail")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Recipe Prices & Overheads", color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditPricingDialog) {
        EditRecipePricingDialog(
            recipe = recipe,
            onDismiss = { showEditPricingDialog = false },
            onSave = { labour, overheads, packaging, utilities, profitMargin, customSellingPrice ->
                onUpdatePricing(recipe.id, labour, overheads, packaging, utilities, profitMargin, customSellingPrice)
            }
        )
    }
}

@Composable
fun RecipeIngredientsScreen(
    recipe: RecipeEntity?,
    ingredients: List<RecipeIngredientEntity>,
    onBack: () -> Unit,
    onNavigateToCosting: () -> Unit,
    onUpdateIngredientCost: (id: Long, cost: Double, quantity: Double) -> Unit = { _, _, _ -> }
) {
    val totalCost = ingredients.sumOf { it.cost }
    var editingIngredient by remember { mutableStateOf<RecipeIngredientEntity?>(null) }

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
                            text = "Recipe Ingredients",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BatchPinkLight
                    ) {
                        Text(
                            text = "${ingredients.size} items",
                            color = BatchPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Total Ingredients Cost", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(
                            text = "R${String.format("%.2f", if (totalCost > 0) totalCost else 34.01)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BatchPink
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToCosting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_proceed_to_costing")
                    ) {
                        Text("View Recipe Costing", color = Color.White, fontWeight = FontWeight.Bold)
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${recipe?.servings ?: 16} Servings (${recipe?.batchSize ?: 124} units)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LightText
                    )
                    Text(
                        text = "Tap any ingredient to edit cost",
                        fontSize = 11.sp,
                        color = BatchPink,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            items(ingredients) { ing ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingIngredient = ing }
                        .testTag("card_ingredient_${ing.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(BatchPinkLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Kitchen, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = ing.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Text(
                                    text = if (ing.quantity == ing.quantity.toInt().toDouble())
                                        "${ing.quantity.toInt()} ${ing.unit}"
                                    else
                                        "${ing.quantity} ${ing.unit}",
                                    fontSize = 12.sp,
                                    color = LightText
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "R${String.format("%.2f", ing.cost)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = "Edit Cost",
                                    fontSize = 11.sp,
                                    color = BatchPink,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Cost", tint = BatchPink, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (editingIngredient != null) {
        EditIngredientCostDialog(
            ingredient = editingIngredient!!,
            onDismiss = { editingIngredient = null },
            onSave = { cost, qty ->
                onUpdateIngredientCost(editingIngredient!!.id, cost, qty)
            }
        )
    }
}

@Composable
fun RecipeCostingScreen(
    recipe: RecipeEntity?,
    ingredientsCost: Double,
    onBack: () -> Unit,
    onCalculatePricing: () -> Unit,
    onUpdatePricing: (id: Long, labour: Double, overheads: Double, packaging: Double, utilities: Double, profitMargin: Double, customSellingPrice: Double) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    val ingCost = if (ingredientsCost > 0) ingredientsCost else 34.01
    val labour = recipe?.labourCost ?: 15.00
    val overheads = recipe?.overheadsCost ?: 50.00
    val packaging = recipe?.packagingCost ?: 5.00
    val utilities = recipe?.utilitiesCost ?: 3.00

    val totalCost = ingCost + labour + overheads + packaging + utilities
    val batchSize = recipe?.batchSize ?: 124
    val costPerItem = totalCost / batchSize
    var showEditPricingDialog by remember { mutableStateOf(false) }

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
                            text = "Recipe Costing",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }

                    OutlinedButton(
                        onClick = { showEditPricingDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("btn_edit_costing_top")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Costs", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onCalculatePricing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_costing_calculate_pricing")
                    ) {
                        Text("Calculate Pricing", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(20.dp)
                .testTag("screen_recipe_costing"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Batch ($batchSize units)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Text(
                    text = "Tap any row to edit",
                    fontSize = 11.sp,
                    color = BatchPink,
                    fontWeight = FontWeight.Medium
                )
            }

            CostingRow(
                icon = Icons.Outlined.Receipt,
                label = "Ingredients Cost",
                value = "R${String.format("%.2f", ingCost)}",
                onClick = { showEditPricingDialog = true }
            )
            CostingRow(
                icon = Icons.Outlined.Person,
                label = "Operations & Labor",
                value = if (recipe != null && recipe.laborHours > 0) {
                    "R${String.format("%.2f", labour)} (${recipe.laborHours}h @ R${String.format("%.2f", recipe.laborRatePerHour)}/h)"
                } else {
                    "R${String.format("%.2f", labour)} (Per Hour)"
                },
                onClick = { showEditPricingDialog = true }
            )
            CostingRow(
                icon = Icons.Outlined.Store,
                label = "Overheads",
                value = "R0.00 (Zero Overheads)",
                onClick = { showEditPricingDialog = true }
            )
            CostingRow(
                icon = Icons.Outlined.Inventory2,
                label = "Packaging",
                value = "R${String.format("%.2f", packaging)}",
                onClick = { showEditPricingDialog = true }
            )
            CostingRow(
                icon = Icons.Outlined.Bolt,
                label = "Utilities",
                value = "R${String.format("%.2f", utilities)}",
                onClick = { showEditPricingDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BatchPinkContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Cost", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "R${String.format("%.2f", totalCost)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = BatchPink)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Cost per item", fontSize = 13.sp, color = MediumText)
                        Text(text = "R${String.format("%.2f", costPerItem)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }
                }
            }
        }
    }

    if (showEditPricingDialog && recipe != null) {
        EditRecipePricingDialog(
            recipe = recipe,
            ingredientsCost = ingCost,
            onDismiss = { showEditPricingDialog = false },
            onSave = { l, o, pk, u, m, sp ->
                onUpdatePricing(recipe.id, l, o, pk, u, m, sp)
            }
        )
    }
}

@Composable
private fun CostingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DarkText)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                if (onClick != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = LightText, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun PricingCalculatorScreen(
    recipe: RecipeEntity?,
    ingredientsCost: Double = 34.01,
    onBack: () -> Unit,
    onSavePricing: (Double) -> Unit,
    onViewAnalysis: () -> Unit,
    onUpdatePricing: (id: Long, labour: Double, overheads: Double, packaging: Double, utilities: Double, profitMargin: Double, customSellingPrice: Double) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    var isPerItem by remember { mutableStateOf(true) }
    var showEditCostsDialog by remember { mutableStateOf(false) }

    val ingCost = if (ingredientsCost > 0) ingredientsCost else 34.01
    val labour = recipe?.labourCost ?: 15.00
    val overheads = recipe?.overheadsCost ?: 50.00
    val packaging = recipe?.packagingCost ?: 5.00
    val utilities = recipe?.utilitiesCost ?: 3.00
    val totalCostBatch = ingCost + labour + overheads + packaging + utilities
    val batchSize = if ((recipe?.batchSize ?: 0) > 0) recipe!!.batchSize else 124
    val totalCostPerItem = totalCostBatch / batchSize

    var marginPercent by remember { mutableStateOf((recipe?.profitMarginPercent ?: 40.0).toFloat()) }
    var sellingPriceInputText by remember {
        val initialPrice = if ((recipe?.customSellingPrice ?: 0.0) > 0.0) {
            recipe!!.customSellingPrice
        } else {
            val margin = (recipe?.profitMarginPercent ?: 40.0) / 100.0
            totalCostPerItem / (1.0 - margin.coerceIn(0.05, 0.95))
        }
        mutableStateOf(String.format("%.2f", initialPrice))
    }

    val parsedPrice = sellingPriceInputText.toDoubleOrNull()
    val sellingPricePerItem = parsedPrice ?: (totalCostPerItem / (1.0 - (marginPercent / 100.0).coerceAtMost(0.95)))
    val profitPerItem = sellingPricePerItem - totalCostPerItem
    val sellingPriceBatch = sellingPricePerItem * batchSize
    val totalProfitBatch = profitPerItem * batchSize

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
                            text = "Pricing Calculator",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }

                    OutlinedButton(
                        onClick = { showEditCostsDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).testTag("btn_calculator_edit_costs")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = BatchPink, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Base Costs", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            if (recipe != null) {
                                onUpdatePricing(
                                    recipe.id,
                                    recipe.labourCost,
                                    recipe.overheadsCost,
                                    recipe.packagingCost,
                                    recipe.utilitiesCost,
                                    marginPercent.toDouble(),
                                    sellingPricePerItem
                                )
                            }
                            onSavePricing(marginPercent.toDouble())
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_save_pricing")
                    ) {
                        Text("Save Recipe Pricing", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onViewAnalysis,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Profit Analysis", color = BatchPink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("screen_pricing_calculator"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Per Item / Per Batch Segmented Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BorderLight)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isPerItem) BatchPink else Color.Transparent)
                        .clickable { isPerItem = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Per Item",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPerItem) Color.White else DarkText
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isPerItem) BatchPink else Color.Transparent)
                        .clickable { isPerItem = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Per Batch ($batchSize)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isPerItem) Color.White else DarkText
                    )
                }
            }

            // Base Cost Summary Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isPerItem) "Total Cost per Item" else "Total Cost per Batch ($batchSize)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = LightText
                        )
                        Text(
                            text = "Includes ingredients, labour & overheads",
                            fontSize = 11.sp,
                            color = MediumText
                        )
                    }
                    Text(
                        text = if (isPerItem) "R${String.format("%.2f", totalCostPerItem)}" else "R${String.format("%.2f", totalCostBatch)}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }
            }

            // Direct Selling Price Input Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isPerItem) "Direct Selling Price (Per Item)" else "Direct Selling Price (Batch of $batchSize)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type an exact price or slide margin below",
                        fontSize = 12.sp,
                        color = LightText
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = if (isPerItem) sellingPriceInputText else {
                            val p = sellingPriceInputText.toDoubleOrNull() ?: sellingPricePerItem
                            String.format("%.2f", p * batchSize)
                        },
                        onValueChange = { input ->
                            if (isPerItem) {
                                sellingPriceInputText = input
                                val p = input.toDoubleOrNull()
                                if (p != null && p > totalCostPerItem) {
                                    val impliedMargin = ((p - totalCostPerItem) / p) * 100.0
                                    marginPercent = impliedMargin.coerceIn(5.0, 90.0).toFloat()
                                }
                            } else {
                                val batchPrice = input.toDoubleOrNull()
                                if (batchPrice != null && batchPrice > 0) {
                                    val perItem = batchPrice / batchSize
                                    sellingPriceInputText = String.format("%.2f", perItem)
                                    if (perItem > totalCostPerItem) {
                                        val impliedMargin = ((perItem - totalCostPerItem) / perItem) * 100.0
                                        marginPercent = impliedMargin.coerceIn(5.0, 90.0).toFloat()
                                    }
                                }
                            }
                        },
                        prefix = { Text("R ", fontWeight = FontWeight.Bold, color = BatchPink) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_calculator_selling_price"),
                        singleLine = true
                    )
                }
            }

            // Profit Margin Slider Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Target Profit Margin", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "${marginPercent.toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = BatchPink)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = marginPercent,
                        onValueChange = {
                            marginPercent = it
                            val calculatedPrice = totalCostPerItem / (1.0 - (it / 100.0).coerceAtMost(0.95))
                            sellingPriceInputText = String.format("%.2f", calculatedPrice)
                        },
                        valueRange = 10f..80f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = BatchPink,
                            activeTrackColor = BatchPink,
                            inactiveTrackColor = BatchPinkLight
                        ),
                        modifier = Modifier.testTag("slider_profit_margin")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("10%", "25%", "40%", "55%", "70%", "80%").forEach { tick ->
                            Text(text = tick, fontSize = 11.sp, color = LightText)
                        }
                    }
                }
            }

            // Results List
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Selling Price per item", fontSize = 13.sp, color = MediumText)
                        Text("R${String.format("%.2f", sellingPricePerItem)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Profit per item", fontSize = 13.sp, color = MediumText)
                        Text(
                            "R${String.format("%.2f", profitPerItem)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (profitPerItem >= 0) MintGreen else Color.Red
                        )
                    }
                    Divider(color = DividerColor)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Selling Price per batch ($batchSize)", fontSize = 13.sp, color = MediumText)
                        Text("R${String.format("%.2f", sellingPriceBatch)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Profit per batch", fontSize = 13.sp, color = MediumText)
                        Text(
                            "R${String.format("%.2f", totalProfitBatch)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalProfitBatch >= 0) BatchPink else Color.Red
                        )
                    }
                }
            }
        }
    }

    if (showEditCostsDialog && recipe != null) {
        EditRecipePricingDialog(
            recipe = recipe,
            ingredientsCost = ingCost,
            onDismiss = { showEditCostsDialog = false },
            onSave = { l, o, pk, u, m, sp ->
                onUpdatePricing(recipe.id, l, o, pk, u, m, sp)
                sellingPriceInputText = String.format("%.2f", sp)
                marginPercent = m.toFloat()
            }
        )
    }
}

@Composable
fun ProfitAnalysisScreen(
    recipe: RecipeEntity?,
    onBack: () -> Unit
) {
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
                            text = "Recipe Profit Analysis",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }

                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = DarkText)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Batch (${recipe?.batchSize ?: 124}) ⌵",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }
            }

            // 3 Summary Cards (Selling Price, Total Cost, Total Profit)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnalysisStatCard(title = "Selling Price", value = "R177.32", color = DarkText, modifier = Modifier.weight(1f))
                    AnalysisStatCard(title = "Total Cost", value = "R107.01", color = DarkText, modifier = Modifier.weight(1f))
                    AnalysisStatCard(title = "Total Profit", value = "R70.31", color = MintGreen, modifier = Modifier.weight(1f))
                }
            }

            // Donut Chart & Legend
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProfitDonutChart(
                            ingredientsPercent = 31.8f,
                            labourPercent = 14.0f,
                            overheadsPercent = 46.7f,
                            profitPercent = 39.7f,
                            modifier = Modifier.size(190.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DonutLegendRow(color = CoralOrange, label = "Ingredients", percent = "31.8%")
                            DonutLegendRow(color = WarmAmber, label = "Labour", percent = "14.0%")
                            DonutLegendRow(color = PurpleAccent, label = "Overheads", percent = "46.7%")
                            DonutLegendRow(color = MintGreen, label = "Profit", percent = "39.7%")
                        }
                    }
                }
            }

            // Performance Card Callout
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MintLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "This recipe is performing well! 🎉\nYour profit margin is 39.7%.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF065F46),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisStatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, fontSize = 11.sp, color = LightText)
        }
    }
}

@Composable
private fun DonutLegendRow(color: Color, label: String, percent: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 13.sp, color = MediumText)
        }
        Text(text = percent, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
    }
}

@Composable
fun CreateEditRecipeScreen(
    onBack: () -> Unit,
    onSave: (
        name: String,
        category: String,
        description: String,
        servings: Int,
        batchSize: Int,
        labourCost: Double,
        overheadsCost: Double,
        packagingCost: Double,
        utilitiesCost: Double,
        profitMargin: Double,
        ingredients: List<RecipeIngredientEntity>
    ) -> Unit,
    onSaveDetailed: ((
        name: String,
        category: String,
        description: String,
        servings: Int,
        batchSize: Int,
        laborHours: Double,
        laborRatePerHour: Double,
        packagingCost: Double,
        profitMargin: Double,
        ingredients: List<RecipeIngredientEntity>
    ) -> Unit)? = null
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Cupcakes") }
    var description by remember { mutableStateOf("") }
    var servings by remember { mutableIntStateOf(16) }
    var batchSize by remember { mutableIntStateOf(124) }

    var laborHoursText by remember { mutableStateOf("1.5") }
    var laborRatePerHourText by remember { mutableStateOf("120.00") }
    var packagingCostText by remember { mutableStateOf("5.00") }
    var profitMarginText by remember { mutableStateOf("40.0") }
    var showAddIngredientDialog by remember { mutableStateOf(false) }

    var ingredientsList by remember {
        mutableStateOf(
            listOf(
                RecipeIngredientEntity(recipeId = 0, name = "Cake Flour", quantity = 180.0, unit = "g", cost = 3.96),
                RecipeIngredientEntity(recipeId = 0, name = "Castor Sugar", quantity = 250.0, unit = "g", cost = 6.00),
                RecipeIngredientEntity(recipeId = 0, name = "Unsalted Butter", quantity = 120.0, unit = "g", cost = 16.80),
                RecipeIngredientEntity(recipeId = 0, name = "Vanilla Extract", quantity = 2.0, unit = "tsp", cost = 3.50),
                RecipeIngredientEntity(recipeId = 0, name = "Baking Powder", quantity = 1.0, unit = "tble", cost = 1.20)
            )
        )
    }

    val parsedHours = laborHoursText.toDoubleOrNull() ?: 1.5
    val parsedRate = laborRatePerHourText.toDoubleOrNull() ?: 120.0
    val calculatedLabour = parsedHours * parsedRate
    val parsedPackaging = packagingCostText.toDoubleOrNull() ?: 5.0
    val parsedMargin = profitMarginText.toDoubleOrNull() ?: 40.0
    val totalIngredientsCost = ingredientsList.sumOf { it.cost }
    val totalBatchCost = totalIngredientsCost + calculatedLabour + parsedPackaging
    val costPerItem = totalBatchCost / (if (batchSize > 0) batchSize else 1)

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
                            text = "Create New Recipe",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }

                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                if (onSaveDetailed != null) {
                                    onSaveDetailed(
                                        name.trim(), category, description.trim(), servings, batchSize,
                                        parsedHours, parsedRate, parsedPackaging, parsedMargin, ingredientsList
                                    )
                                } else {
                                    onSave(
                                        name.trim(), category, description.trim(), servings, batchSize,
                                        calculatedLabour, 0.0, parsedPackaging, 0.0, parsedMargin, ingredientsList
                                    )
                                }
                            }
                        },
                        modifier = Modifier.testTag("btn_save_recipe")
                    ) {
                        Text("Save", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Photo Upload Placeholder
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BatchPinkContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = BatchPink, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add recipe photo", color = BatchPink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Recipe Name (e.g. Vanilla Cupcakes)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            item {
                Column {
                    Text("Category", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MediumText)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Cupcakes", "Cakes", "Cookies", "Breads", "Pastries")) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Tell us about your recipe...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Servings Stepper
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Servings", fontSize = 12.sp, color = LightText)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (servings > 1) servings-- }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, tint = BatchPink)
                                }
                                Text(text = "$servings", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { servings++ }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = BatchPink)
                                }
                            }
                        }
                    }

                    // Batch Size Stepper
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Batch Size", fontSize = 12.sp, color = LightText)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (batchSize > 1) batchSize -= 10 }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, tint = BatchPink)
                                }
                                Text(text = "$batchSize", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { batchSize += 10 }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = BatchPink)
                                }
                            }
                        }
                    }
                }
            }

            // Operations & Labor Section (Hourly rate, no overheads)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Operations & Labor", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MintLight
                            ) {
                                Text(
                                    text = "No Overheads Policy",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Operations cost labor is calculated strictly per hour with zero overhead charges.",
                            fontSize = 11.sp,
                            color = MediumText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = laborHoursText,
                                onValueChange = { laborHoursText = it },
                                label = { Text("Labor Hours") },
                                suffix = { Text("hrs", fontSize = 11.sp, color = MediumText) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = laborRatePerHourText,
                                onValueChange = { laborRatePerHourText = it },
                                label = { Text("Rate / Hour") },
                                prefix = { Text("R ", color = BatchPink, fontWeight = FontWeight.Bold) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Calculated Labor Cost:", fontSize = 12.sp, color = DarkText)
                            Text(
                                text = "R${String.format("%.2f", calculatedLabour)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BatchPink
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = packagingCostText,
                                onValueChange = { packagingCostText = it },
                                label = { Text("Packaging Cost") },
                                prefix = { Text("R ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = profitMarginText,
                                onValueChange = { profitMarginText = it },
                                label = { Text("Profit Margin") },
                                suffix = { Text("%", color = MintGreen, fontWeight = FontWeight.Bold) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Ingredients Header & List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Recipe Ingredients", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(
                            text = "Add per g / kg / ml / tsp / tble",
                            fontSize = 11.sp,
                            color = MediumText
                        )
                    }

                    Text(
                        text = "Total: R${String.format("%.2f", totalIngredientsCost)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BatchPink
                    )
                }
            }

            // Ingredient items
            items(ingredientsList.size) { idx ->
                val ing = ingredientsList[idx]
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = BatchPinkContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Cookie, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = ing.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Text(
                                    text = "${UnitUtils.formatQuantity(ing.quantity, ing.unit)} • Cost: R${String.format("%.2f", ing.cost)}",
                                    fontSize = 12.sp,
                                    color = MediumText
                                )
                            }
                        }

                        IconButton(
                            onClick = { ingredientsList = ingredientsList.filterIndexed { i, _ -> i != idx } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = LightText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Add Ingredient Button
            item {
                OutlinedButton(
                    onClick = { showAddIngredientDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BatchPink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_add_recipe_ingredient")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Ingredient (g / kg / ml / tsp / tble)", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Summary Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BatchPinkContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ingredients Cost:", fontSize = 12.sp, color = DarkText)
                            Text("R${String.format("%.2f", totalIngredientsCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Operations & Labor ($parsedHours hrs @ R$parsedRate/hr):", fontSize = 12.sp, color = DarkText)
                            Text("R${String.format("%.2f", calculatedLabour)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Overheads (Zero Policy):", fontSize = 12.sp, color = DarkText)
                            Text("R0.00", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MintGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Packaging Cost:", fontSize = 12.sp, color = DarkText)
                            Text("R${String.format("%.2f", parsedPackaging)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        }
                        HorizontalDivider(color = BatchPink.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Batch Cost:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text("R${String.format("%.2f", totalBatchCost)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = BatchPink)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cost Per Unit ($batchSize units):", fontSize = 12.sp, color = MediumText)
                            Text("R${String.format("%.2f", costPerItem)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        }
                    }
                }
            }
        }
    }

    if (showAddIngredientDialog) {
        AddRecipeIngredientDialog(
            onDismiss = { showAddIngredientDialog = false },
            onAdd = { ingName, qty, unit, cost ->
                ingredientsList = ingredientsList + RecipeIngredientEntity(
                    recipeId = 0,
                    name = ingName,
                    quantity = qty,
                    unit = unit,
                    cost = cost
                )
                showAddIngredientDialog = false
            }
        )
    }
}

@Composable
fun AddRecipeIngredientDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, quantity: Double, unit: String, cost: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("g") }
    var costText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val presetIngredients = listOf(
        "Cake Flour" to "g",
        "Castor Sugar" to "g",
        "Unsalted Butter" to "g",
        "Vanilla Extract" to "tsp",
        "Baking Powder" to "tsp",
        "Large Eggs" to "unit",
        "Cocoa Powder" to "g",
        "Whole Milk" to "ml",
        "Salt" to "pinch",
        "Cinnamon" to "tsp",
        "Icing Sugar" to "g"
    )

    val supportedUnits = listOf("g", "kg", "ml", "tsp", "tble", "cup", "unit")

    fun updateEstimatedCost(n: String, q: Double, u: String) {
        val rate = UnitUtils.getEstimatedRatePerGram(n)
        val estimated = UnitUtils.calculateCost(q, u, rate)
        costText = String.format("%.2f", estimated)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Ingredient", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Text(errorMessage!!, color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Quick presets
                Text("Quick Presets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetIngredients) { (presetName, defUnit) ->
                        FilterChip(
                            selected = name == presetName,
                            onClick = {
                                name = presetName
                                selectedUnit = defUnit
                                val q = quantityText.toDoubleOrNull() ?: 0.0
                                if (q > 0) updateEstimatedCost(presetName, q, defUnit)
                            },
                            label = { Text(presetName, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                        val q = quantityText.toDoubleOrNull() ?: 0.0
                        if (q > 0) updateEstimatedCost(it, q, selectedUnit)
                    },
                    label = { Text("Ingredient Name") },
                    placeholder = { Text("e.g. Vanilla Extract or Cake Flour") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Unit selection chips (g, kg, ml, tsp, tble, etc.)
                Text("Unit (g, kg, ml, tsp, tble, etc.)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(supportedUnits) { u ->
                        FilterChip(
                            selected = selectedUnit == u,
                            onClick = {
                                selectedUnit = u
                                val q = quantityText.toDoubleOrNull() ?: 0.0
                                if (name.isNotBlank() && q > 0) updateEstimatedCost(name, q, u)
                            },
                            label = { Text(u, fontSize = 11.sp, fontWeight = if (selectedUnit == u) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = {
                            quantityText = it
                            errorMessage = null
                            val q = it.toDoubleOrNull() ?: 0.0
                            if (name.isNotBlank() && q > 0) updateEstimatedCost(name, q, selectedUnit)
                        },
                        label = { Text("Quantity") },
                        suffix = { Text(selectedUnit, color = MediumText, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = costText,
                        onValueChange = {
                            costText = it
                            errorMessage = null
                        },
                        label = { Text("Cost") },
                        prefix = { Text("R ", color = BatchPink, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

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
                        Text("Estimated Unit Conversion:", fontSize = 11.sp, color = DarkText)
                        val q = quantityText.toDoubleOrNull() ?: 0.0
                        val baseGrams = UnitUtils.toBaseGrams(q, selectedUnit)
                        Text(
                            text = if (baseGrams > 0) "~${String.format("%.1f", baseGrams)} base g/ml" else "-",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BatchPink
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = quantityText.toDoubleOrNull()
                    val c = costText.toDoubleOrNull()
                    when {
                        name.isBlank() -> errorMessage = "Please enter an ingredient name"
                        q == null || q <= 0 -> errorMessage = "Please enter a valid quantity"
                        c == null || c < 0 -> errorMessage = "Please enter ingredient cost"
                        else -> onAdd(name.trim(), q, selectedUnit, c)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
            ) {
                Text("Add to Recipe")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditRecipePricingDialog(
    recipe: RecipeEntity,
    ingredientsCost: Double = 34.01,
    onDismiss: () -> Unit,
    onSave: (labour: Double, overheads: Double, packaging: Double, utilities: Double, profitMargin: Double, customSellingPrice: Double) -> Unit,
    onSaveDetailedPricing: ((laborHours: Double, laborRatePerHour: Double, packaging: Double, profitMargin: Double, customSellingPrice: Double) -> Unit)? = null
) {
    var laborHoursText by remember {
        mutableStateOf(if (recipe.laborHours > 0) recipe.laborHours.toString() else "1.5")
    }
    var laborRatePerHourText by remember {
        mutableStateOf(if (recipe.laborRatePerHour > 0) String.format("%.2f", recipe.laborRatePerHour) else "120.00")
    }
    var packagingText by remember { mutableStateOf(String.format("%.2f", recipe.packagingCost)) }
    var profitMarginText by remember { mutableStateOf(String.format("%.1f", recipe.profitMarginPercent)) }

    val batchSize = if (recipe.batchSize > 0) recipe.batchSize else 1
    val parsedHours = laborHoursText.toDoubleOrNull() ?: 0.0
    val parsedRate = laborRatePerHourText.toDoubleOrNull() ?: 0.0
    val calculatedLabour = parsedHours * parsedRate
    val parsedPackaging = packagingText.toDoubleOrNull() ?: 0.0
    val parsedMargin = profitMarginText.toDoubleOrNull() ?: 40.0

    val totalCost = ingredientsCost + calculatedLabour + parsedPackaging
    val costPerItem = totalCost / batchSize

    var sellingPriceText by remember {
        val initialSellingPrice = if (recipe.customSellingPrice > 0.0) {
            recipe.customSellingPrice
        } else {
            val marginFrac = (recipe.profitMarginPercent / 100.0).coerceIn(0.05, 0.95)
            costPerItem / (1.0 - marginFrac)
        }
        mutableStateOf(String.format("%.2f", initialSellingPrice))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsedSellingPrice = sellingPriceText.toDoubleOrNull() ?: 0.0
    val totalRevenue = parsedSellingPrice * batchSize
    val totalProfit = totalRevenue - totalCost
    val profitPerItem = parsedSellingPrice - costPerItem

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PriceChange, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit ${recipe.name} Pricing", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(errorMessage!!, color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Zero Overheads policy banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MintLight
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Operations labor is billed per hour • Zero Overheads (R0.00)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintGreen
                        )
                    }
                }

                // Operations & Labor (Hours & Rate per hour)
                Text("Operations & Labor (Per Hour)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = laborHoursText,
                        onValueChange = { laborHoursText = it; errorMessage = null },
                        label = { Text("Hours") },
                        suffix = { Text("hrs", fontSize = 11.sp, color = MediumText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = laborRatePerHourText,
                        onValueChange = { laborRatePerHourText = it; errorMessage = null },
                        label = { Text("Rate / Hour") },
                        prefix = { Text("R ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Calculated Labor Cost:", fontSize = 12.sp, color = DarkText)
                    Text("R${String.format("%.2f", calculatedLabour)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                }

                // Packaging Cost
                OutlinedTextField(
                    value = packagingText,
                    onValueChange = { packagingText = it; errorMessage = null },
                    label = { Text("Packaging Cost") },
                    prefix = { Text("R ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Target Selling Price & Margin
                Text("Selling Price & Profit Margin", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sellingPriceText,
                        onValueChange = {
                            sellingPriceText = it
                            errorMessage = null
                            val newPrice = it.toDoubleOrNull()
                            if (newPrice != null && costPerItem > 0 && newPrice > costPerItem) {
                                val impliedMargin = ((newPrice - costPerItem) / newPrice) * 100.0
                                profitMarginText = String.format("%.1f", impliedMargin.coerceIn(0.0, 99.0))
                            }
                        },
                        label = { Text("Price / Item") },
                        prefix = { Text("R ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_edit_recipe_selling_price"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = profitMarginText,
                        onValueChange = {
                            profitMarginText = it
                            errorMessage = null
                            val newMargin = it.toDoubleOrNull()
                            if (newMargin != null && newMargin in 1.0..95.0 && costPerItem > 0) {
                                val impliedPrice = costPerItem / (1.0 - (newMargin / 100.0))
                                sellingPriceText = String.format("%.2f", impliedPrice)
                            }
                        },
                        label = { Text("Margin %") },
                        suffix = { Text("%", fontWeight = FontWeight.Bold, color = MintGreen) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_edit_recipe_margin"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Summary Calculation Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BatchPinkContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ingredients Cost:", fontSize = 12.sp, color = DarkText)
                            Text("R${String.format("%.2f", ingredientsCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Labor ($parsedHours hrs @ R$parsedRate/hr):", fontSize = 12.sp, color = DarkText)
                            Text("R${String.format("%.2f", calculatedLabour)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Overheads:", fontSize = 12.sp, color = DarkText)
                            Text("R0.00 (Zero Overheads)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MintGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Batch Cost:", fontSize = 12.sp, color = DarkText)
                            Text("R${String.format("%.2f", totalCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cost Per Item:", fontSize = 12.sp, color = DarkText)
                            Text("R${String.format("%.2f", costPerItem)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Selling Price / Item:", fontSize = 12.sp, color = DarkText)
                            Text("R${String.format("%.2f", parsedSellingPrice)}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = BatchPink)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Profit Per Item:", fontSize = 12.sp, color = DarkText)
                            Text(
                                "R${String.format("%.2f", profitPerItem)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (profitPerItem >= 0) MintGreen else Color.Red
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pk = packagingText.toDoubleOrNull()
                    val m = profitMarginText.toDoubleOrNull()
                    val sp = sellingPriceText.toDoubleOrNull()
                    when {
                        parsedHours < 0 -> errorMessage = "Please enter valid labor hours"
                        parsedRate < 0 -> errorMessage = "Please enter valid hourly rate"
                        pk == null || pk < 0 -> errorMessage = "Please enter valid packaging cost"
                        m == null || m <= 0 -> errorMessage = "Please enter valid profit margin"
                        sp == null || sp <= 0 -> errorMessage = "Please enter valid selling price"
                        else -> {
                            if (onSaveDetailedPricing != null) {
                                onSaveDetailedPricing(parsedHours, parsedRate, pk, m, sp)
                            } else {
                                onSave(calculatedLabour, 0.0, pk, 0.0, m, sp)
                            }
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                modifier = Modifier.testTag("btn_save_recipe_pricing")
            ) {
                Text("Save Pricing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditIngredientCostDialog(
    ingredient: RecipeIngredientEntity,
    onDismiss: () -> Unit,
    onSave: (cost: Double, quantity: Double) -> Unit
) {
    var costText by remember { mutableStateOf(String.format("%.2f", ingredient.cost)) }
    var quantityText by remember { mutableStateOf(if (ingredient.quantity == ingredient.quantity.toInt().toDouble()) ingredient.quantity.toInt().toString() else ingredient.quantity.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = BatchPink)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit ${ingredient.name} Cost", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (errorMessage != null) {
                    Text(errorMessage!!, color = BatchPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Column {
                    Text("Total Ingredient Cost in Recipe (R)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it; errorMessage = null },
                        prefix = { Text("R ", fontWeight = FontWeight.Bold, color = BatchPink) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_ingredient_cost"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Quick percentage adjustments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-10, -5, 5, 10).forEach { pct ->
                        AssistChip(
                            onClick = {
                                val curr = costText.toDoubleOrNull() ?: ingredient.cost
                                val updated = (curr * (1.0 + pct / 100.0)).coerceAtLeast(0.01)
                                costText = String.format("%.2f", updated)
                            },
                            label = { Text("${if (pct > 0) "+" else ""}$pct%", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column {
                    Text("Recipe Quantity (${ingredient.unit})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it; errorMessage = null },
                        suffix = { Text(ingredient.unit, color = MediumText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_ingredient_quantity"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val c = costText.toDoubleOrNull()
                    val q = quantityText.toDoubleOrNull()
                    when {
                        c == null || c < 0 -> errorMessage = "Please enter valid cost"
                        q == null || q <= 0 -> errorMessage = "Please enter valid quantity"
                        else -> {
                            onSave(c, q)
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

