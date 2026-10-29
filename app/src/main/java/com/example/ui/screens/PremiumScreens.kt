package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSubscriptionScreen(
    isPremium: Boolean,
    onBack: () -> Unit,
    onUpgrade: (plan: String) -> Unit,
    onCancelSubscription: () -> Unit,
    onOpenAiScanner: () -> Unit = {},
    onOpenBarcodeScanner: () -> Unit = {}
) {
    var selectedPlan by remember { mutableStateOf("annual") } // "monthly" or "annual"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscription", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_premium")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Crown Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BatchPinkDark,
                                BatchPink,
                                CoralOrange
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = "Premium Crown",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "BatchBoss Premium",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isPremium) "Your Premium membership is ACTIVE" else "Scale your bakery with complete commercial tools",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )

                    if (isPremium) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MintGreen
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pro Status: Unlocked", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // What's Unlocked List
            Text(
                text = "EVERYTHING UNLOCKED IN PREMIUM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LightText,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FeatureRowItem(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "AI Recipe Photo & Image Scanner",
                        subtitle = "Snap a photo of your recipe card, book, or screenshot. AI automatically extracts all ingredients, measurements, and baking steps into your recipe book.",
                        iconBg = BatchPinkLight,
                        iconTint = BatchPink
                    )
                    HorizontalDivider(color = DividerColor)
                    FeatureRowItem(
                        icon = Icons.Outlined.QrCodeScanner,
                        title = "Barcode Ingredient Scanner",
                        subtitle = "Scan grocery barcode packaging to fetch ingredient weights, units, package prices, and instantly log to your pantry stock.",
                        iconBg = MintLight,
                        iconTint = MintGreen
                    )
                    HorizontalDivider(color = DividerColor)
                    FeatureRowItem(
                        icon = Icons.Outlined.AllInclusive,
                        title = "Add More Than 5 Recipes",
                        subtitle = "Free plan limited to 5 recipes. Premium unlocks unlimited recipe costing, bulk scaling & ingredient sync.",
                        iconBg = BatchPinkLight,
                        iconTint = BatchPink
                    )
                    HorizontalDivider(color = DividerColor)
                    FeatureRowItem(
                        icon = Icons.Outlined.Description,
                        title = "Professional Invoicing",
                        subtitle = "Create branded invoices, track payment receipts (Paid / Pending / Overdue), and share directly to WhatsApp.",
                        iconBg = BlueLight,
                        iconTint = SoftBlue
                    )
                    HorizontalDivider(color = DividerColor)
                    FeatureRowItem(
                        icon = Icons.Outlined.RequestQuote,
                        title = "Custom Quotes & Estimates",
                        subtitle = "Quote custom wedding & event cakes with automated profit margin locks. 1-click convert quotes to invoices.",
                        iconBg = PurpleLight,
                        iconTint = PurpleAccent
                    )
                    HorizontalDivider(color = DividerColor)
                    FeatureRowItem(
                        icon = Icons.Outlined.Storefront,
                        title = "Wholesale Suppliers & Specials",
                        subtitle = "Unlock wholesale ingredient suppliers, direct phone contacts, website ordering & exclusive supplier deals.",
                        iconBg = AmberLight,
                        iconTint = WarmAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Launch Pro Scanners
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "QUICK LAUNCH SCAN TOOLS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BatchPink,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onOpenAiScanner,
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_launch_ai_recipe_scanner")
                    ) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take / Upload Recipe Photo (AI)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onOpenBarcodeScanner,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MintGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_launch_barcode_scanner")
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MintGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Ingredient Barcode", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isPremium) {
                // Plan Selector
                Text(
                    text = "CHOOSE YOUR PLAN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Annual
                    val isAnnual = selectedPlan == "annual"
                    PlanCard(
                        modifier = Modifier.weight(1f),
                        title = "Annual",
                        badge = "SAVE 33%",
                        price = "R1 199",
                        period = "per year (~R99/mo)",
                        isSelected = isAnnual,
                        onClick = { selectedPlan = "annual" }
                    )

                    // Monthly
                    val isMonthly = selectedPlan == "monthly"
                    PlanCard(
                        modifier = Modifier.weight(1f),
                        title = "Monthly",
                        badge = "FLEXIBLE",
                        price = "R149",
                        period = "per month",
                        isSelected = isMonthly,
                        onClick = { selectedPlan = "monthly" }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val planName = if (selectedPlan == "annual") "Pro Annual" else "Pro Monthly"
                        onUpgrade(planName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("btn_upgrade_premium")
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unlock BatchBoss Premium",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Includes 7-day free trial. Cancel anytime with no penalty.",
                    fontSize = 12.sp,
                    color = LightText,
                    textAlign = TextAlign.Center
                )
            } else {
                // Subscription is active
                Button(
                    onClick = onCancelSubscription,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MediumText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel Subscription (Switch to Free)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlanCard(
    modifier: Modifier = Modifier,
    title: String,
    badge: String,
    price: String,
    period: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BatchPinkContainer else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) BatchPink else BorderLight
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) BatchPink else AmberLight
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else WarmAmber,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(price, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = BatchPink)
            Spacer(modifier = Modifier.height(2.dp))
            Text(period, fontSize = 11.sp, color = MediumText, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FeatureRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconBg: Color,
    iconTint: Color
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 12.sp, color = MediumText, lineHeight = 16.sp)
        }
    }
}

@Composable
fun PremiumPaywallDialog(
    featureName: String,
    description: String,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AmberLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = "Premium Crown",
                        tint = WarmAmber,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = featureName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MediumText,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BackgroundLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaywallBullet(text = "Unlimited Recipes (More than 5)")
                        PaywallBullet(text = "Professional Invoicing with PDF / WhatsApp")
                        PaywallBullet(text = "Custom Cake Quotes with Profit Protection")
                        PaywallBullet(text = "Wholesale Suppliers Directory & Deals")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "From only R149/month • Cancel anytime",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BatchPink
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_paywall_upgrade")
            ) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Unlock Premium Now", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Maybe Later", color = MediumText)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
private fun PaywallBullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MintGreen,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 12.sp, color = DarkText, fontWeight = FontWeight.Medium)
    }
}
