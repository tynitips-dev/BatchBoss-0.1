package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.remote.AiRecipeScannerService
import com.example.data.remote.BarcodeIngredientFetcher
import com.example.data.remote.ScannedProductInfo
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    onSaveIngredient: (
        name: String,
        currentStock: Double,
        minStock: Double,
        unit: String,
        packagePrice: Double,
        gramsPerUnit: Double,
        category: String,
        barcode: String
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var barcodeInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var scannedProduct by remember { mutableStateOf<ScannedProductInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var lastScannedCode by remember { mutableStateOf("") }

    // Camera permission tracking
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan barcodes.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-prompt camera permission on first entry to enable live scanning
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Form fields for editing/saving fetched product
    var editedName by remember { mutableStateOf("") }
    var editedCategory by remember { mutableStateOf("Flour & Grains") }
    var editedPackSize by remember { mutableStateOf("1000") }
    var editedUnit by remember { mutableStateOf("g") }
    var editedPrice by remember { mutableStateOf("45.00") }
    var editedCurrentStock by remember { mutableStateOf("2") }
    var editedMinStock by remember { mutableStateOf("1") }

    val categories = listOf(
        "Flour & Grains",
        "Dairy & Eggs",
        "Sugars & Sweeteners",
        "Chocolate & Cocoa",
        "Leaveners & Rising",
        "Flavorings & Extracts",
        "Baking Staples"
    )

    fun applyProduct(prod: ScannedProductInfo) {
        scannedProduct = prod
        editedName = prod.name
        editedCategory = prod.category
        editedPackSize = if (prod.quantity > 0) prod.quantity.toInt().toString() else "1000"
        editedUnit = prod.unit
        editedPrice = String.format("%.2f", if (prod.estimatedPriceZar > 0) prod.estimatedPriceZar else 35.00)
        editedCurrentStock = "2"
        editedMinStock = "1"
        errorMessage = null
    }

    fun executeScan(code: String) {
        val clean = code.trim().replace(Regex("[^0-9A-Za-z]"), "")
        if (clean.isBlank()) {
            errorMessage = "Please enter or scan a valid barcode"
            return
        }
        keyboardController?.hide()
        isLoading = true
        errorMessage = null
        lastScannedCode = clean

        coroutineScope.launch {
            val result = BarcodeIngredientFetcher.fetchProduct(clean)
            isLoading = false
            result.onSuccess { prod ->
                applyProduct(prod)
                Toast.makeText(context, "Scanned: ${prod.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                errorMessage = "Lookup error: ${err.message}. You can still enter details manually."
                // Provide editable fallback product so baker can enter details manually
                applyProduct(
                    ScannedProductInfo(
                        barcode = clean,
                        name = "Bakery Ingredient ($clean)",
                        brand = "",
                        quantity = 1000.0,
                        unit = "g",
                        category = "Flour & Grains",
                        estimatedPriceZar = 35.00
                    )
                )
            }
        }
    }

    // Gallery barcode picker launcher
    val barcodeImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                val bitmap = AiRecipeScannerService.loadBitmapFromUri(context, uri)
                if (bitmap != null) {
                    try {
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val scanner = BarcodeScanning.getClient()
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val found = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                if (found != null && !found.rawValue.isNullOrBlank()) {
                                    val code = found.rawValue!!
                                    coroutineScope.launch(Dispatchers.Main) {
                                        barcodeInput = code
                                        executeScan(code)
                                    }
                                } else {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        isLoading = false
                                        errorMessage = "No barcode detected in photo. Please ensure barcode is clear."
                                        Toast.makeText(context, "No barcode detected in selected image.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    isLoading = false
                                    errorMessage = "Error scanning image: ${e.message}"
                                }
                            }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            errorMessage = "Failed to process photo: ${e.message}"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Could not load selected image.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Viewfinder laser beam animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserBeam"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            tint = BatchPink,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Scan Ingredient Barcode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Camera Scanner & Open Food Facts Sync", fontSize = 11.sp, color = MediumText)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_barcode_scanner")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasCameraPermission) {
                        IconButton(
                            onClick = { isTorchOn = !isTorchOn },
                            modifier = Modifier.testTag("btn_toggle_torch")
                        ) {
                            Icon(
                                if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = "Flashlight",
                                tint = if (isTorchOn) Color(0xFFFFD700) else Color.DarkGray
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            barcodeImagePickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.testTag("btn_pick_barcode_photo")
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Scan from Photo", tint = BatchPink)
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
            // Live Camera Viewfinder or Permission Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2430)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission) {
                        // Live Camera Preview with CameraX
                        LiveCameraBarcodeScanner(
                            isTorchOn = isTorchOn,
                            onBarcodeScanned = { detectedCode ->
                                barcodeInput = detectedCode
                                executeScan(detectedCode)
                            }
                        )

                        // Viewfinder Overlay Reticle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(160.dp)
                                .align(Alignment.Center)
                                .border(2.dp, BatchPink.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                        ) {
                            // Animated Laser Beam
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .offset(y = (160 * laserOffset).dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color(0xFFFF2A6D), Color(0xFFFFD700), Color(0xFFFF2A6D), Color.Transparent)
                                        )
                                    )
                            )

                            // Center Focus Mark
                            Box(
                                modifier = Modifier.align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.CenterFocusWeak,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                        }

                        // Floating Guide / Status Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Looking up ingredient...", color = Color.White, fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = BatchPink, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (lastScannedCode.isNotBlank()) "Scanned: $lastScannedCode" else "Point camera at barcode on packaging",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // Camera Permission Request Screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BatchPinkLight,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.CameraAlt,
                                        contentDescription = null,
                                        tint = BatchPink,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Camera Barcode Scanning",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Allow camera access to instantly scan ingredient barcodes on flour, butter, sugar, and baking supplies.",
                                fontSize = 12.sp,
                                color = Color(0xFFCCCCCC),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("btn_enable_camera")
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enable Camera", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        barcodeImagePickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("From Photo", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Quick Bakery Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Popular Bakery Pantry Items",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DarkText
                    )
                    Text(
                        "Tap to test / quick add",
                        fontSize = 11.sp,
                        color = MediumText
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(BarcodeIngredientFetcher.SAMPLE_BAKERY_BARCODES) { sample ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (barcodeInput == sample.barcode) BatchPinkLight else Color.White,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (barcodeInput == sample.barcode) BatchPink else BorderLight
                            ),
                            modifier = Modifier.clickable {
                                barcodeInput = sample.barcode
                                executeScan(sample.barcode)
                            }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(sample.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                                Text("${sample.quantity.toInt()} ${sample.unit} • R${String.format("%.2f", sample.estimatedPriceZar)}", fontSize = 10.sp, color = MediumText)
                                Text("Code: ${sample.barcode}", fontSize = 9.sp, color = BatchPink)
                            }
                        }
                    }
                }
            }

            // Manual Barcode Input & Search
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Or Enter Barcode Number",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DarkText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = barcodeInput,
                            onValueChange = { barcodeInput = it },
                            placeholder = { Text("e.g. 6001007012345", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { executeScan(barcodeInput) }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_barcode_number"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (barcodeInput.isNotBlank()) {
                                    IconButton(onClick = { barcodeInput = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = Color.Gray)
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { executeScan(barcodeInput) },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("btn_lookup_barcode")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFD32F2F),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            // Scanned Product Details & Save to Pantry Form
            if (scannedProduct != null) {
                val prod = scannedProduct!!
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MintGreen.copy(alpha = 0.15f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MintGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Product Identified", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                                    Text("Brand: ${prod.brand.ifBlank { "Bakery Pantry" }}", fontSize = 11.sp, color = MediumText)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BorderLight
                            ) {
                                Text(
                                    text = prod.barcode,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Product Image + Brand Overview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!prod.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = prod.imageUrl,
                                    contentDescription = prod.name,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BorderLight),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prod.name,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = DarkText
                                )
                                if (!prod.brand.isNullOrBlank()) {
                                    Text(
                                        text = "Brand: ${prod.brand}",
                                        fontSize = 12.sp,
                                        color = MediumText
                                    )
                                }
                                Text(
                                    text = "Pack: ${prod.quantity.toInt()} ${prod.unit} • Est. R${String.format("%.2f", prod.estimatedPriceZar)}",
                                    fontSize = 12.sp,
                                    color = BatchPink,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        HorizontalDivider(color = BorderLight)

                        Text(
                            "Customize for Your Bakery Inventory",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DarkText
                        )

                        // Editable Ingredient Name
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("Ingredient Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_scanned_name"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Pack Size & Unit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = editedPackSize,
                                onValueChange = { editedPackSize = it },
                                label = { Text("Pack Size") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_scanned_pack_size"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = editedUnit,
                                onValueChange = { editedUnit = it },
                                label = { Text("Unit") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        // Price & Stock
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = editedPrice,
                                onValueChange = { editedPrice = it },
                                label = { Text("Package Price (R)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_scanned_price"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = editedCurrentStock,
                                onValueChange = { editedCurrentStock = it },
                                label = { Text("Stock In Pantry") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_scanned_stock"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        // Calculated Unit Cost Summary
                        val packSizeNum = editedPackSize.toDoubleOrNull() ?: 1000.0
                        val priceNum = editedPrice.toDoubleOrNull() ?: 0.0
                        val costPerUnit = if (packSizeNum > 0) priceNum / packSizeNum else 0.0

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BatchPinkLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Calculated Unit Cost:", fontSize = 12.sp, color = DarkText)
                                Text(
                                    text = "R${String.format("%.4f", costPerUnit)} / $editedUnit",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = BatchPink
                                )
                            }
                        }

                        // Save Button
                        Button(
                            onClick = {
                                if (editedName.isBlank()) {
                                    Toast.makeText(context, "Ingredient name cannot be blank", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val pSize = editedPackSize.toDoubleOrNull() ?: 1000.0
                                val pPrice = editedPrice.toDoubleOrNull() ?: 35.00
                                val stock = editedCurrentStock.toDoubleOrNull() ?: 2.0
                                val minStk = editedMinStock.toDoubleOrNull() ?: 1.0

                                onSaveIngredient(
                                    editedName.trim(),
                                    stock,
                                    minStk,
                                    editedUnit.trim(),
                                    pPrice,
                                    pSize,
                                    editedCategory,
                                    lastScannedCode.ifBlank { barcodeInput.trim() }
                                )
                                Toast.makeText(context, "'$editedName' added to Ingredient Pantry!", Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_save_scanned_ingredient")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add to Ingredient List", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * CameraX live preview composable with ML Kit Barcode Analyzer.
 */
@Composable
fun LiveCameraBarcodeScanner(
    modifier: Modifier = Modifier,
    isTorchOn: Boolean,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(isTorchOn, camera) {
        try {
            camera?.cameraControl?.enableTorch(isTorchOn)
        } catch (e: Exception) {
            Log.w("LiveCamera", "Torch toggle failed: ${e.message}")
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val barcodeScanner = BarcodeScanning.getClient()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    var lastScanTimestamp = 0L

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    val now = System.currentTimeMillis()
                                    for (barcode in barcodes) {
                                        val raw = barcode.rawValue
                                        if (!raw.isNullOrBlank() && (now - lastScanTimestamp > 1800)) {
                                            lastScanTimestamp = now
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onBarcodeScanned(raw)
                                            }
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        null
                    }

                    if (selector != null) {
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageAnalysis
                        )
                    }
                } catch (e: Exception) {
                    Log.e("LiveCamera", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
