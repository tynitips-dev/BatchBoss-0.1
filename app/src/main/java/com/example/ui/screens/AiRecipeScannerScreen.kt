package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RecipeIngredientEntity
import com.example.data.remote.AiRecipeScannerService
import com.example.data.remote.ParsedIngredient
import com.example.data.remote.ParsedRecipeResult
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRecipeScannerScreen(
    isPremium: Boolean,
    onBack: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onSaveRecipe: (
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
        ingredients: List<RecipeIngredientEntity>,
        instructions: String,
        photoUri: String
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var parsedResult by remember { mutableStateOf<ParsedRecipeResult?>(null) }
    var showAddIngredientDialog by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }

    // Editable fields
    var recipeName by remember { mutableStateOf("") }
    var recipeCategory by remember { mutableStateOf("Cakes") }
    var recipeDescription by remember { mutableStateOf("") }
    var servings by remember { mutableIntStateOf(12) }
    var batchSize by remember { mutableIntStateOf(1) }
    var ingredientsList by remember { mutableStateOf<List<ParsedIngredient>>(emptyList()) }
    var stepsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var newStepText by remember { mutableStateOf("") }

    val categories = listOf("Cakes", "Cupcakes", "Bread", "Pastries", "Cookies", "Desserts")

    fun applyParsedRecipe(result: ParsedRecipeResult, bmp: Bitmap? = null) {
        parsedResult = result
        capturedBitmap = bmp
        recipeName = result.name
        recipeCategory = result.category
        recipeDescription = result.description
        servings = result.servings
        batchSize = result.batchSize
        ingredientsList = result.ingredients
        stepsList = result.steps
        isAnalyzing = false
    }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var pastedRecipeText by remember { mutableStateOf("") }
    var showRawTextSheet by remember { mutableStateOf(false) }

    // Full-resolution camera launcher using FileProvider
    val fullResCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = tempPhotoUri
        if (success && uri != null) {
            isAnalyzing = true
            coroutineScope.launch {
                val bmp = AiRecipeScannerService.loadBitmapFromUri(context, uri)
                if (bmp != null) {
                    val scanResult = AiRecipeScannerService.scanRecipeImage(bmp)
                    scanResult.onSuccess { parsed ->
                        applyParsedRecipe(parsed, bmp)
                        Toast.makeText(context, "Recipe '${parsed.name}' extracted successfully!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        isAnalyzing = false
                        Toast.makeText(context, "Scanning error: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    isAnalyzing = false
                    Toast.makeText(context, "Could not load captured photo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Thumbnail Camera launcher fallback
    val cameraThumbnailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            capturedBitmap = bmp
            isAnalyzing = true
            coroutineScope.launch {
                val scanResult = AiRecipeScannerService.scanRecipeImage(bmp)
                scanResult.onSuccess { parsed ->
                    applyParsedRecipe(parsed, bmp)
                    Toast.makeText(context, "Recipe '${parsed.name}' extracted successfully!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    isAnalyzing = false
                    Toast.makeText(context, "Scanning error: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun launchCameraCapture() {
        try {
            val photoFile = java.io.File(context.cacheDir, "recipe_capture_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempPhotoUri = uri
            fullResCameraLauncher.launch(uri)
        } catch (e: Exception) {
            cameraThumbnailLauncher.launch(null)
        }
    }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, "Camera permission is required to photograph recipe cards.", Toast.LENGTH_LONG).show()
        }
    }

    // Image Picker launcher (Photo library)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isAnalyzing = true
            coroutineScope.launch {
                val bmp = AiRecipeScannerService.loadBitmapFromUri(context, uri)
                if (bmp != null) {
                    val scanResult = AiRecipeScannerService.scanRecipeImage(bmp)
                    scanResult.onSuccess { parsed ->
                        applyParsedRecipe(parsed, bmp)
                        Toast.makeText(context, "Recipe '${parsed.name}' extracted from photo!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        isAnalyzing = false
                        Toast.makeText(context, "Error extracting recipe: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    isAnalyzing = false
                    Toast.makeText(context, "Could not load selected photo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Radar scanning animation
    val infiniteTransition = rememberInfiniteTransition(label = "scanRadar")
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BatchPinkLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = BatchPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("AI Recipe Scanner", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isPremium) MintGreen.copy(alpha = 0.15f) else BatchPinkLight
                                ) {
                                    Text(
                                        text = if (isPremium) "PRO ACTIVE" else "PRO FEATURE",
                                        color = if (isPremium) MintGreen else BatchPink,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("Extract ingredients & steps from photos", fontSize = 11.sp, color = MediumText)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_ai_recipe_scanner")) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Subscription status callout if not premium
            if (!isPremium) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BatchPinkContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("card_pro_scanner_trial_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Stars, contentDescription = null, tint = BatchPink, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("BatchBoss Pro Vision", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                                Text("Trial Mode active. Try scanning a recipe or tap below to upgrade.", fontSize = 11.sp, color = MediumText)
                            }
                        }
                        TextButton(onClick = onNavigateToSubscription) {
                            Text("Upgrade", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Input Selection Box (Camera, Photo, Preset)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Capture or Select Recipe",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Camera Button
                        Button(
                            onClick = {
                                val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (hasCam) {
                                    launchCameraCapture()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("btn_take_recipe_photo")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take Photo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Upload Image Button
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BatchPink),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("btn_pick_recipe_image")
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = BatchPink)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Image", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Paste / Enter Recipe Text Button
                    OutlinedButton(
                        onClick = {
                            pastedRecipeText = ""
                            showTextInputDialog = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_paste_recipe_text")
                    ) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = BatchPink)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste or Type Recipe Text Directly", color = DarkText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    // Sample Recipes Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "OR TEST WITH POPULAR RECIPE CARDS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AiRecipeScannerService.SAMPLE_RECIPES) { sample ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (parsedResult?.name == sample.name) BatchPinkLight else CardBackground,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (parsedResult?.name == sample.name) BatchPink else BorderLight
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            applyParsedRecipe(sample, null)
                                            Toast.makeText(context, "Loaded ${sample.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        .testTag("btn_sample_recipe_${sample.category}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.ReceiptLong,
                                            contentDescription = null,
                                            tint = if (parsedResult?.name == sample.name) BatchPink else DarkText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(sample.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                            Text("${sample.ingredients.size} ingredients • ${sample.steps.size} steps", fontSize = 10.sp, color = MediumText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Scanning Progress State
            AnimatedVisibility(visible = isAnalyzing) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2430)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(BatchPink.copy(alpha = radarPulse))
                                .border(2.dp, BatchPink, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "BatchBoss AI Vision Analyzing Recipe...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reading handwriting, extracting ingredients, and structuring baking steps.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Parsed Recipe Content & Form
            if (parsedResult != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BatchPink.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("card_ai_parsed_recipe_container")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Header with status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MintGreen.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MintGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "AI EXTRACTION COMPLETE",
                                        color = MintGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            if (capturedBitmap != null) {
                                Image(
                                    bitmap = capturedBitmap!!.asImageBitmap(),
                                    contentDescription = "Captured Recipe",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                                )
                            }
                        }

                        // Recipe Name
                        OutlinedTextField(
                            value = recipeName,
                            onValueChange = { recipeName = it },
                            label = { Text("Recipe Title") },
                            modifier = Modifier.fillMaxWidth().testTag("input_ai_recipe_name"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Category Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(categories) { cat ->
                                    FilterChip(
                                        selected = recipeCategory == cat,
                                        onClick = { recipeCategory = cat },
                                        label = { Text(cat, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Servings and Batch Size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Servings Stepper
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { if (servings > 1) servings-- },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease Servings")
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$servings", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                                        Text("Servings", fontSize = 10.sp, color = MediumText)
                                    }
                                    IconButton(
                                        onClick = { servings++ },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase Servings")
                                    }
                                }
                            }

                            // Batch Size Stepper
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { if (batchSize > 1) batchSize-- },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease Batch")
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$batchSize", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                                        Text("Batch Units", fontSize = 10.sp, color = MediumText)
                                    }
                                    IconButton(
                                        onClick = { batchSize++ },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase Batch")
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = BorderLight)

                        // Extracted Ingredients Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Kitchen, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Extracted Ingredients (${ingredientsList.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DarkText
                                    )
                                }

                                TextButton(onClick = { showAddIngredientDialog = true }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = BatchPink)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", color = BatchPink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            ingredientsList.forEachIndexed { index, ing ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CardBackground,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                    modifier = Modifier.fillMaxWidth().testTag("card_ai_ingredient_$index")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(ing.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                                            Text(
                                                text = "${if (ing.quantity % 1.0 == 0.0) ing.quantity.toInt().toString() else ing.quantity} ${ing.unit}",
                                                fontSize = 11.sp,
                                                color = MediumText
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "R${String.format("%.2f", ing.cost)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = BatchPink
                                            )
                                            IconButton(
                                                onClick = {
                                                    ingredientsList = ingredientsList.toMutableList().also { it.removeAt(index) }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = BorderLight)

                        // Extracted Steps & Method Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Baking Method & Steps (${stepsList.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DarkText
                                    )
                                }
                            }

                            stepsList.forEachIndexed { index, step ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CardBackground,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                                    modifier = Modifier.fillMaxWidth().testTag("card_ai_step_$index")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(BatchPink),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = step,
                                            fontSize = 12.sp,
                                            color = DarkText,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                stepsList = stepsList.toMutableList().also { it.removeAt(index) }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Remove Step", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            // Quick Add Step Field
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newStepText,
                                    onValueChange = { newStepText = it },
                                    placeholder = { Text("Add custom step...") },
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (newStepText.isNotBlank()) {
                                            stepsList = stepsList + newStepText.trim()
                                            newStepText = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                                ) {
                                    Text("Add")
                                }
                            }
                        }

                        // Summary Cost Banner
                        val totalIngredientsCost = ingredientsList.sumOf { it.cost }
                        val laborCost = 1.5 * 120.0
                        val packagingCost = 12.00
                        val totalRecipeCost = totalIngredientsCost + laborCost + packagingCost

                        // Raw OCR Text Inspection (if available)
                        if (!parsedResult?.rawExtractedText.isNullOrBlank()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = BorderLight.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showRawText = !showRawText },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Outlined.DocumentScanner,
                                                contentDescription = null,
                                                tint = BatchPink,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Raw OCR Text Extracted from Image",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkText
                                            )
                                        }
                                        Icon(
                                            if (showRawText) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = null,
                                            tint = DarkText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (showRawText) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = parsedResult!!.rawExtractedText,
                                            fontSize = 11.sp,
                                            color = DarkText,
                                            lineHeight = 15.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White, RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BatchPinkLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Ingredients Cost:", fontSize = 12.sp, color = DarkText)
                                    Text("R${String.format("%.2f", totalIngredientsCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Estimated Labor & Packaging:", fontSize = 12.sp, color = DarkText)
                                    Text("R${String.format("%.2f", laborCost + packagingCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                }
                                HorizontalDivider(color = BatchPink.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Estimated Total Batch Cost:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text("R${String.format("%.2f", totalRecipeCost)}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = BatchPink)
                                }
                            }
                        }

                        // Save Recipe Button
                        Button(
                            onClick = {
                                if (recipeName.isBlank()) {
                                    Toast.makeText(context, "Please enter a recipe title", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val formattedSteps = stepsList.joinToString("\n")
                                val entities = ingredientsList.map { ing ->
                                    RecipeIngredientEntity(
                                        recipeId = 0,
                                        name = ing.name,
                                        quantity = ing.quantity,
                                        unit = ing.unit,
                                        cost = ing.cost
                                    )
                                }

                                onSaveRecipe(
                                    recipeName.trim(),
                                    recipeCategory,
                                    recipeDescription.ifBlank { "Handcrafted recipe scanned with BatchBoss AI Vision." },
                                    servings,
                                    batchSize,
                                    laborCost,
                                    0.0, // Zero overheads policy
                                    packagingCost,
                                    0.0,
                                    60.0, // Default 60% profit margin
                                    entities,
                                    formattedSteps,
                                    ""
                                )
                                Toast.makeText(context, "Recipe '$recipeName' created & opening details...", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_save_scanned_recipe")
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Recipe & Start Baking", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    // Add Ingredient Dialog
    if (showAddIngredientDialog) {
        var newName by remember { mutableStateOf("") }
        var newQty by remember { mutableStateOf("100") }
        var newUnit by remember { mutableStateOf("g") }
        var newCost by remember { mutableStateOf("15.00") }

        AlertDialog(
            onDismissRequest = { showAddIngredientDialog = false },
            title = { Text("Add Ingredient to Recipe", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Ingredient Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newQty,
                            onValueChange = { newQty = it },
                            label = { Text("Qty") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newUnit,
                            onValueChange = { newUnit = it },
                            label = { Text("Unit") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = newCost,
                        onValueChange = { newCost = it },
                        label = { Text("Cost (R)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            ingredientsList = ingredientsList + ParsedIngredient(
                                name = newName.trim(),
                                quantity = newQty.toDoubleOrNull() ?: 100.0,
                                unit = newUnit.trim(),
                                cost = newCost.toDoubleOrNull() ?: 15.00
                            )
                            showAddIngredientDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddIngredientDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Direct Text Input / Paste Dialog
    if (showTextInputDialog) {
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, tint = BatchPink)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Paste or Type Recipe", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste ingredients, quantities, and instructions from a website, email, or notes. BatchBoss AI will parse them into structured ingredients and baking steps.",
                        fontSize = 12.sp,
                        color = MediumText
                    )
                    OutlinedTextField(
                        value = pastedRecipeText,
                        onValueChange = { pastedRecipeText = it },
                        placeholder = {
                            Text(
                                "Example:\nBanana Bread\nIngredients:\n2 cups flour\n1/2 cup butter\n3 ripe bananas\n1 cup sugar\n\nInstructions:\n1. Mash bananas.\n2. Mix dry ingredients.\n3. Bake at 180°C for 50 mins.",
                                fontSize = 11.sp,
                                color = LightText
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        maxLines = 15
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pastedRecipeText.isNotBlank()) {
                            isAnalyzing = true
                            showTextInputDialog = false
                            coroutineScope.launch {
                                val parsed = AiRecipeScannerService.parseRecipeFromText(pastedRecipeText)
                                applyParsedRecipe(parsed, null)
                                isAnalyzing = false
                                Toast.makeText(context, "Parsed '${parsed.name}' successfully!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink)
                ) {
                    Text("Parse Recipe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
