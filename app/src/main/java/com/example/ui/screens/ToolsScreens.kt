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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RecipeEntity
import com.example.ui.theme.*

@Composable
fun QuickActionsScreen(
    onNewRecipeClick: () -> Unit,
    onViewRecipesClick: () -> Unit,
    onViewInventoryClick: () -> Unit,
    onViewSuppliersClick: () -> Unit,
    onOpenUnitConverter: () -> Unit,
    onOpenRecipeScaler: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenInvoices: () -> Unit = {},
    onOpenQuotes: () -> Unit = {},
    onOpenCustomers: () -> Unit = {},
    onOpenProductsServices: () -> Unit = {},
    onOpenPremium: () -> Unit = {},
    isPremium: Boolean = false,
    bakeryName: String = "My Bakery",
    onNavigateToLogin: () -> Unit = {},
    onNavigateToSignUp: () -> Unit = {},
    onNavigateToForgotPassword: () -> Unit = {},
    onOpenAiRecipeScanner: () -> Unit = {},
    onOpenBarcodeScanner: () -> Unit = {},
    onOpenStoreLocator: () -> Unit = {},
    onOpenAboutBatchBoss: () -> Unit = {},
    onOpenAccountDataDeletion: () -> Unit = {},
    onOpenMasterBackend: () -> Unit = {},
    onOpenFirebaseSync: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .testTag("screen_quick_actions"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Profile Header
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(BatchPinkLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = BatchPink, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = bakeryName.ifBlank { "My Bakery" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(
                            text = if (isPremium) "Pro Member • All Features Unlocked" else "Free Tier • 5 Recipes Cap",
                            fontSize = 12.sp,
                            color = if (isPremium) MintGreen else MediumText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!isPremium) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BatchPink,
                            modifier = Modifier.clickable(onClick = onOpenPremium)
                        ) {
                            Text(
                                text = "Upgrade",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BatchPinkLight,
                            modifier = Modifier.clickable(onClick = onOpenPremium)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "PRO", color = BatchPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Premium Promo Banner (if free)
        if (!isPremium) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = BatchPinkLight,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BatchPink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPremium)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BatchPink),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Unlock BatchBoss PRO", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text(text = "Invoices, Quotes, Unlimited Recipes & Suppliers", fontSize = 12.sp, color = MediumText)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = BatchPink)
                    }
                }
            }
        }

        // 1. Create New Section
        item {
            Text(text = "Create New", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionPill(icon = Icons.Outlined.MenuBook, label = "New Recipe", onClick = onNewRecipeClick, modifier = Modifier.weight(1f))
                QuickActionPill(icon = Icons.Outlined.Kitchen, label = "Add Ingredient", onClick = onViewInventoryClick, modifier = Modifier.weight(1f))
                QuickActionPill(icon = Icons.Outlined.ReceiptLong, label = "New Order", onClick = onOpenTasks, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionPill(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "AI Recipe Scan",
                    onClick = onOpenAiRecipeScanner,
                    modifier = Modifier.weight(1f)
                )
                QuickActionPill(
                    icon = Icons.Outlined.QrCodeScanner,
                    label = "Scan Barcode",
                    onClick = onOpenBarcodeScanner,
                    modifier = Modifier.weight(1f)
                )
                QuickActionPill(
                    icon = Icons.Outlined.WorkspacePremium,
                    label = "Manage Pro",
                    onClick = onOpenPremium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. Sales & Commercial Tools (PRO)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Sales & Commercial Tools", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                if (!isPremium) {
                    Text(text = "PRO FEATURES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isPremium) onOpenInvoices() else onOpenPremium()
                    }
                    .testTag("tool_invoices")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BatchPinkLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Receipt, contentDescription = null, tint = BatchPink, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Client Invoices", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            if (!isPremium) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = BatchPink, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(text = "Generate VAT/tax invoices, track payment status", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isPremium) onOpenQuotes() else onOpenPremium()
                    }
                    .testTag("tool_quotes")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AmberLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.RequestQuote, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Quotes & Estimates", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            if (!isPremium) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = BatchPink, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(text = "Quotes with expiry dates, acceptances & deposits", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCustomers)
                    .testTag("tool_customers")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BatchPinkLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.People, contentDescription = null, tint = BatchPink, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Customer Directory", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "Manage clients, order histories & contact details", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenProductsServices)
                    .testTag("tool_products_services")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MintLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.LocalOffer, contentDescription = null, tint = MintGreen, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Products & Services", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "Single source of truth: selling prices, units & catalog", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }
        }

        // 3. Bakery Tools Section
        item {
            Text(text = "Bakery Tools", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenUnitConverter)
                    .testTag("tool_unit_converter")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AmberLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Scale, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Baking Unit Converter", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "Accurate volume to weight for baking ingredients", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRecipeScaler)
                    .testTag("tool_recipe_scaler")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BatchPinkContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Calculate, contentDescription = null, tint = BatchPink, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Batch & Recipe Scaler", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "Scale batches and ingredients up or down easily", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenStoreLocator)
                    .testTag("tool_store_locator")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.NearMe, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Baking Supply Store Locator", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = MintGreen.copy(alpha = 0.2f)) {
                                Text("MAP", color = Color(0xFF2E7D32), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        Text(text = "Locate baking ingredients, cake boards & packaging nearby", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }
        }

        // 4. Shortcuts
        item {
            Text(text = "Shortcuts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutRow(icon = Icons.Outlined.MenuBook, title = "Recipe Catalog", onClick = onViewRecipesClick)
                    Divider(color = DividerColor)
                    ShortcutRow(icon = Icons.Outlined.Kitchen, title = "Ingredient Inventory", onClick = onViewInventoryClick)
                    Divider(color = DividerColor)
                    ShortcutRow(
                        icon = Icons.Outlined.Storefront,
                        title = if (isPremium) "Suppliers Directory" else "Suppliers Directory (PRO)",
                        onClick = {
                            if (isPremium) onViewSuppliersClick() else onOpenPremium()
                        }
                    )
                    Divider(color = DividerColor)
                    ShortcutRow(
                        icon = Icons.Outlined.NearMe,
                        title = "Baking Supply Locator",
                        onClick = onOpenStoreLocator
                    )
                    Divider(color = DividerColor)
                    ShortcutRow(icon = Icons.Outlined.Checklist, title = "Today's Tasks", onClick = onOpenTasks)
                }
            }
        }

        // 5. Account & Access Section
        item {
            Text(text = "Bakery Account & Access", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutRow(
                        icon = Icons.Outlined.PersonAdd,
                        title = "Sign Up / Create Account",
                        count = "New Bakery",
                        onClick = onNavigateToSignUp
                    )
                    Divider(color = DividerColor)
                    ShortcutRow(
                        icon = Icons.Outlined.Login,
                        title = "Log In to Account",
                        count = "Switch",
                        onClick = onNavigateToLogin
                    )
                    Divider(color = DividerColor)
                    ShortcutRow(
                        icon = Icons.Outlined.LockReset,
                        title = "Forgot Password / Reset",
                        count = "Recovery",
                        onClick = onNavigateToForgotPassword
                    )
                }
            }
        }

        // 6. About & Support
        item {
            Text(text = "About & Support", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAboutBatchBoss)
                    .testTag("tool_about_batchboss")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BatchPinkLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = BatchPink,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "About BatchBoss", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "Built for bakers • Contact, help & technical support", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAccountDataDeletion)
                    .testTag("tool_account_data_deletion")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Account & Data Deletion", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "Request data wipe or deletion link • Google Play compliance", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMasterBackend)
                    .testTag("tool_master_backend")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFF1E293B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Master Backend (Owner)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "Check logins, store data & execute data deletions", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFirebaseSync)
                    .testTag("tool_firebase_sync")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Firebase & Cloud Sync", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(text = "Publish readiness • Cloud Firestore backup & restore", fontSize = 12.sp, color = LightText)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText)
                }
            }
        }

        // 7. App Version & Copyright Footer
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BatchPinkLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BatchPink)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BatchBoss v10.0",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BatchPink
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Bakery Costing, Pricing & Operations Engine",
                    fontSize = 11.sp,
                    color = LightText
                )
                Spacer(modifier = Modifier.height(12.dp))
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
private fun QuickActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(BatchPinkLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = BatchPink, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DarkText, maxLines = 1)
        }
    }
}

@Composable
private fun ShortcutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DarkText)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!count.isNullOrBlank()) {
                Text(text = count, fontSize = 12.sp, color = LightText)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LightText, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(
    onBack: () -> Unit
) {
    var inputValue by remember { mutableStateOf("1") }
    var selectedIngredient by remember { mutableStateOf("Flour (All-Purpose)") }
    var fromUnit by remember { mutableStateOf("Cups") }
    var toUnit by remember { mutableStateOf("Grams") }

    val ingredientsDensity = mapOf(
        "Flour (All-Purpose)" to 120.0, // 1 cup = 120g
        "Granulated Sugar" to 200.0,    // 1 cup = 200g
        "Brown Sugar" to 220.0,         // 1 cup = 220g
        "Butter" to 227.0,              // 1 cup = 227g
        "Cocoa Powder" to 100.0,        // 1 cup = 100g
        "Milk / Water" to 240.0,        // 1 cup = 240g
        "Powdered Sugar" to 120.0       // 1 cup = 120g
    )

    // Calculate result
    val density = ingredientsDensity[selectedIngredient] ?: 120.0
    val inputNum = inputValue.toDoubleOrNull() ?: 1.0

    // Convert input to grams
    val inputInGrams = when (fromUnit) {
        "Grams" -> inputNum
        "Kilograms" -> inputNum * 1000.0
        "Ounces" -> inputNum * 28.3495
        "Cups" -> inputNum * density
        "Tablespoons" -> (inputNum / 16.0) * density
        "Teaspoons" -> (inputNum / 48.0) * density
        "Milliliters" -> inputNum * (density / 240.0)
        else -> inputNum
    }

    // Convert grams to target unit
    val result = when (toUnit) {
        "Grams" -> inputInGrams
        "Kilograms" -> inputInGrams / 1000.0
        "Ounces" -> inputInGrams / 28.3495
        "Cups" -> inputInGrams / density
        "Tablespoons" -> (inputInGrams / density) * 16.0
        "Teaspoons" -> (inputInGrams / density) * 48.0
        "Milliliters" -> inputInGrams / (density / 240.0)
        else -> inputInGrams
    }

    Scaffold(
        topBar = {
            Surface(color = SurfaceWhite, shadowElevation = 2.dp, modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Baking Unit Converter", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ingredient Picker
            Text("Select Ingredient", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
            LazyColumn(modifier = Modifier.height(130.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ingredientsDensity.keys.toList()) { ing ->
                    val isSelected = ing == selectedIngredient
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) BatchPinkContainer else CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BatchPink else BorderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIngredient = ing }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = { selectedIngredient = ing }, colors = RadioButtonDefaults.colors(selectedColor = BatchPink))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = ing, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            // Input quantity
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = { Text("Quantity") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // From / To Unit Pickers
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("From Unit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MediumText)
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf("Cups", "Grams", "Ounces", "Tablespoons").forEach { u ->
                        FilterChip(
                            selected = fromUnit == u,
                            onClick = { fromUnit = u },
                            label = { Text(u, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("To Unit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MediumText)
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf("Grams", "Cups", "Ounces", "Tablespoons").forEach { u ->
                        FilterChip(
                            selected = toUnit == u,
                            onClick = { toUnit = u },
                            label = { Text(u, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conversion Result Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BatchPinkContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$inputValue $fromUnit of $selectedIngredient =", fontSize = 13.sp, color = MediumText)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${String.format("%.2f", result)} $toUnit",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BatchPink
                    )
                }
            }
        }
    }
}

@Composable
fun RecipeScalerScreen(
    recipes: List<RecipeEntity>,
    onBack: () -> Unit
) {
    var selectedRecipeId by remember { mutableStateOf(recipes.firstOrNull()?.id ?: 1L) }
    var scaleMultiplier by remember { mutableFloatStateOf(1f) }

    val recipe = recipes.find { it.id == selectedRecipeId } ?: recipes.firstOrNull()

    Scaffold(
        topBar = {
            Surface(color = SurfaceWhite, shadowElevation = 2.dp, modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Batch & Recipe Scaler", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Scaling: ${recipe?.name ?: "Recipe"}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)

            // Scale buttons
            Text(text = "Batches (1, 2, 3, 4)", fontSize = 13.sp, color = MediumText)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 4).forEach { batches ->
                    val factor = batches.toFloat()
                    val isSelected = scaleMultiplier == factor
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) BatchPink else SurfaceWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BatchPink else BorderLight),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { scaleMultiplier = factor }
                    ) {
                        Text(
                            text = "$batches",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else DarkText,
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Results Card
            val baseServings = recipe?.servings ?: 16
            val scaledServings = (baseServings * scaleMultiplier).toInt()
            val baseBatch = recipe?.batchSize ?: 124
            val scaledBatch = (baseBatch * scaleMultiplier).toInt()

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
                        Text(text = "Scaled Servings", fontSize = 12.sp, color = LightText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "$scaledServings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(BorderLight))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Scaled Batch Size", fontSize = 12.sp, color = LightText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "$scaledBatch", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BatchPink)
                    }
                }
            }

            Text(text = "Scaled Ingredients Preview", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)

            val baseIngredients = listOf(
                Pair("Flour", 180.0 to "g"),
                Pair("Sugar", 250.0 to "g"),
                Pair("Cocoa", 25.0 to "g"),
                Pair("Baking Soda", 1.0 to "tsp"),
                Pair("Buttermilk", 120.0 to "ml"),
                Pair("Egg", 1.0 to "large")
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(baseIngredients) { (name, pair) ->
                    val (baseQty, unit) = pair
                    val scaledQty = baseQty * scaleMultiplier
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DarkText)
                            Text(
                                text = if (scaledQty == scaledQty.toInt().toDouble()) "${scaledQty.toInt()} $unit" else "${String.format("%.1f", scaledQty)} $unit",
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

@Composable
fun AddSupplierScreen(
    onBack: () -> Unit,
    onSaveSupplier: (name: String, category: String, phone: String, email: String, website: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Baking Ingredients & Packaging") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Surface(color = SurfaceWhite, shadowElevation = 2.dp, modifier = Modifier.statusBarsPadding()) {
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
                        Text("Add Supplier", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                    }

                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSaveSupplier(name, category, phone, email, website)
                            }
                        }
                    ) {
                        Text("Save", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Supplier Name (e.g. Flour Mills Direct)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category (e.g. Wholesale Flour & Dairy)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Website") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSaveSupplier(name, category, phone, email, website)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save Supplier", fontWeight = FontWeight.Bold)
            }
        }
    }
}
