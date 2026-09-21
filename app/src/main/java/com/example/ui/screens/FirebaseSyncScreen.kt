package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InventoryItemEntity
import com.example.data.local.RecipeEntity
import com.example.data.local.RecipeIngredientEntity
import com.example.data.remote.FirebaseService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseSyncScreen(
    recipes: List<RecipeEntity>,
    ingredients: List<RecipeIngredientEntity>,
    inventory: List<InventoryItemEntity>,
    onBack: () -> Unit,
    onRestoreData: (List<RecipeEntity>, List<RecipeIngredientEntity>, List<InventoryItemEntity>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isSyncing by remember { mutableStateOf(false) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var syncSuccess by remember { mutableStateOf<Boolean?>(null) }

    val isFirebaseReady = FirebaseService.isConfigured
    val currentUser = FirebaseService.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Firebase & Cloud Sync",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = DarkText
                        )
                        Text(
                            text = if (isFirebaseReady) "Cloud Firestore Connected" else "Local Mode • Ready for Setup",
                            fontSize = 12.sp,
                            color = if (isFirebaseReady) MintGreen else MediumText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_firebase_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 1. Connection Status Banner
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isFirebaseReady) Color(0xFFF0FDF4) else Color(0xFFEFF6FF),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isFirebaseReady) Color(0xFF86EFAC) else Color(0xFF93C5FD)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("card_firebase_status")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isFirebaseReady) MintGreen else Color(0xFF3B82F6),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isFirebaseReady) Icons.Filled.CloudDone else Icons.Outlined.CloudQueue,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isFirebaseReady) "Firebase Cloud Connected" else "Running in Local Offline Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isFirebaseReady) Color(0xFF15803D) else Color(0xFF1D4ED8)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isFirebaseReady) {
                                    "User: ${currentUser?.email ?: currentUser?.uid ?: "Anonymous Active"}"
                                } else {
                                    "All Firebase libraries & plugins are fully configured. Add google-services.json to app/ to activate live cloud sync."
                                },
                                fontSize = 12.sp,
                                color = DarkText,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 2. Publishing & Configuration Checklist Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth().testTag("card_publish_readiness")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publishing & Setup Readiness", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                            }

                            Surface(shape = RoundedCornerShape(8.dp), color = MintGreen.copy(alpha = 0.15f)) {
                                Text(
                                    text = "PRE-CONFIGURED",
                                    color = MintGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "When registering this application in your Firebase Console or Google Play Console, use this exact Application Package ID:",
                            fontSize = 12.sp,
                            color = MediumText
                        )

                        // Application ID display with copy button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundLight,
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Application ID (Package Name)", fontSize = 10.sp, color = LightText)
                                    Text(
                                        text = FirebaseService.APPLICATION_ID,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = DarkText
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Application ID", FirebaseService.APPLICATION_ID))
                                        Toast.makeText(context, "Copied Application ID to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("btn_copy_application_id")
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy ID", tint = BatchPink, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Divider(color = DividerColor)

                        // Setup Steps Checklist
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReadinessItem(
                                title = "Google Services Plugin",
                                subtitle = "com.google.gms.google-services (v4.5.0) active in build.gradle.kts",
                                isComplete = true
                            )
                            ReadinessItem(
                                title = "Firebase BOM & Firestore SDK",
                                subtitle = "firebase-bom (v34.17.0) & firebase-firestore enabled",
                                isComplete = true
                            )
                            ReadinessItem(
                                title = "Firebase Auth & Credential Manager",
                                subtitle = "firebase-auth, androidx.credentials & googleid integrated",
                                isComplete = true
                            )
                            ReadinessItem(
                                title = "Local SQLite Room Database",
                                subtitle = "Active offline storage holding ${recipes.size} recipes & ${inventory.size} items",
                                isComplete = true
                            )
                            ReadinessItem(
                                title = "google-services.json Integration",
                                subtitle = if (isFirebaseReady) "Configured & Active" else "Download from Firebase Console and place in app/ folder",
                                isComplete = isFirebaseReady
                            )
                        }
                    }
                }
            }

            // 3. Cloud Actions (Backup / Restore)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth().testTag("card_cloud_actions")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cloud Sync Operations", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                        }

                        Text(
                            text = "Safely upload your local recipes and inventory to Cloud Firestore, or restore from a previous backup.",
                            fontSize = 12.sp,
                            color = MediumText
                        )

                        if (syncStatusMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (syncSuccess == true) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, if (syncSuccess == true) Color(0xFF86EFAC) else Color(0xFFFCA5A5)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = syncStatusMessage!!,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (syncSuccess == true) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Backup Button
                        Button(
                            onClick = {
                                isSyncing = true
                                syncStatusMessage = null
                                coroutineScope.launch {
                                    val result = FirebaseService.backupDataToCloud(recipes, ingredients, inventory)
                                    isSyncing = false
                                    syncSuccess = result.success
                                    syncStatusMessage = result.message
                                }
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_cloud_backup")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing with Firestore...")
                            } else {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Backup ${recipes.size} Recipes & ${inventory.size} Items to Cloud")
                            }
                        }

                        // Restore Button
                        OutlinedButton(
                            onClick = {
                                isSyncing = true
                                syncStatusMessage = null
                                coroutineScope.launch {
                                    val result = FirebaseService.restoreDataFromCloud()
                                    isSyncing = false
                                    syncSuccess = result.success
                                    syncStatusMessage = result.message
                                    if (result.success && result.data != null) {
                                        onRestoreData(result.data.recipes, result.data.ingredients, result.data.inventory)
                                    }
                                }
                            },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_cloud_restore")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Data from Firestore", color = BatchPink)
                        }

                        // Write Test Recipe POJO
                        OutlinedButton(
                            onClick = {
                                isSyncing = true
                                syncStatusMessage = null
                                coroutineScope.launch {
                                    val testRecipe = com.example.data.model.Recipe(
                                        title = "Classic Chocolate Chip Cookies",
                                        prepTimeMinutes = 20,
                                        ingredients = listOf("250g Flour", "150g Butter", "100g Brown Sugar", "150g Chocolate Chips"),
                                        instructions = "Cream butter and sugar, fold in dry ingredients and chocolate chips, bake at 180°C for 10-12 minutes."
                                    )
                                    val result = FirebaseService.addOnlineRecipe(testRecipe)
                                    isSyncing = false
                                    if (result.isSuccess) {
                                        syncSuccess = true
                                        syncStatusMessage = "Successfully wrote Recipe POJO to Firestore! Document ID: ${result.getOrNull()}"
                                    } else {
                                        syncSuccess = false
                                        syncStatusMessage = "Failed to write recipe: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                                    }
                                }
                            },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_write_sample_recipe")
                        ) {
                            Icon(Icons.Filled.PostAdd, contentDescription = null, tint = DarkText, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Write Sample Recipe (POJO) to Firestore", color = DarkText)
                        }
                    }
                }
            }

            // 4. Firebase Quick Guide
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = MediumText, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("How to link your Firebase project", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
                        }

                        Text(
                            text = "1. Open https://console.firebase.google.com\n" +
                                   "2. Select your Firebase project (or create a new one)\n" +
                                   "3. Click 'Add app' -> Android\n" +
                                   "4. Enter Package name: com.aistudio.batchboss.kqwxrv\n" +
                                   "5. Download google-services.json\n" +
                                   "6. Upload or place google-services.json into the app/ directory\n" +
                                   "7. Build or push your app — Firebase will activate immediately without any build errors!",
                            fontSize = 12.sp,
                            color = MediumText,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessItem(
    title: String,
    subtitle: String,
    isComplete: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isComplete) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isComplete) MintGreen else LightText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DarkText)
            Text(subtitle, fontSize = 11.sp, color = MediumText)
        }
    }
}
