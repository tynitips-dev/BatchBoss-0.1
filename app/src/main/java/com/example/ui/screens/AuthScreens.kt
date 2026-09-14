package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.Screen
import com.example.ui.components.BatchBossBrandLogo
import com.example.ui.theme.*

@Composable
fun SplashScreen(
    onNavigateToSignUp: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateNext: () -> Unit = onNavigateToHome
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val stepTime = 25L
        for (i in 1..100) {
            kotlinx.coroutines.delay(stepTime)
            progress = i / 100f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("screen_splash"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(BatchPinkContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Cake,
                    contentDescription = "BatchBoss Logo",
                    tint = BatchPink,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "BatchBoss",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BatchPinkLight
            ) {
                Text(
                    text = "Bakery Costing & Pricing Suite",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BatchPink,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Cost it. Price it. Profit.\nBake with complete confidence.",
                fontSize = 15.sp,
                color = MediumText,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(44.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(200.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BatchPink,
                trackColor = BatchPinkLight
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (progress < 1f) "Preparing your bakery kitchen..." else "Ready to bake!",
                fontSize = 13.sp,
                color = LightText
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Primary Sign Up CTA
            Button(
                onClick = onNavigateToSignUp,
                colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp)
                    .testTag("btn_splash_signup")
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get Started / Sign Up", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log In Button
            OutlinedButton(
                onClick = onNavigateToLogin,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BatchPink),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
                    .testTag("btn_splash_login")
            ) {
                Icon(Icons.Outlined.Login, contentDescription = null, tint = BatchPink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log In to Account", color = BatchPink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direct Open / Demo CTA
            TextButton(
                onClick = onNavigateToHome,
                modifier = Modifier.testTag("btn_enter_app")
            ) {
                Text("Explore Bakery Demo", color = LightText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Skip",
                    color = BatchPink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSkip)
                        .padding(8.dp)
                        .testTag("btn_skip_onboarding")
                )
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pager dots
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BatchPink)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BorderLight)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BorderLight)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onGetStarted,
                        colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_get_started")
                    ) {
                        Text(
                            text = "Get Started",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .testTag("screen_onboarding")
        ) {
            Text(
                text = "Welcome to\nBatchBoss",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "The all-in-one app to calculate costs, set prices and grow your baking business.",
                fontSize = 15.sp,
                color = MediumText,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Hero Cupcake image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cupcake_hero),
                    contentDescription = "Gourmet Cupcake",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3 feature items
            OnboardingFeatureCard(
                icon = Icons.Outlined.ReceiptLong,
                title = "Calculate ingredient costs with ease",
                accentColor = BatchPink
            )

            Spacer(modifier = Modifier.height(12.dp))

            OnboardingFeatureCard(
                icon = Icons.Outlined.TrendingUp,
                title = "Set the right prices and increase profit",
                accentColor = WarmAmber
            )

            Spacer(modifier = Modifier.height(12.dp))

            OnboardingFeatureCard(
                icon = Icons.Outlined.Assignment,
                title = "Manage quotes, invoices and orders",
                accentColor = MintGreen
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = DarkText
            )
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgot: () -> Unit,
    onLoginWithDetails: ((emailOrPhone: String, branch: String) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    var loginMode by remember { mutableStateOf("Owner") } // "Owner" or "Staff PIN"
    var emailOrPhone by remember { mutableStateOf("tyne@batchboss.com") }
    var password by remember { mutableStateOf("password123") }
    var staffPin by remember { mutableStateOf("1234") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var selectedBranch by remember { mutableStateOf("Main Flagship Bakery") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("screen_login"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onBack != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("btn_back_login")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        BatchBossBrandLogo(size = 48)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome back!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Text(
            text = "Sign in to manage recipes, costing & invoices",
            fontSize = 14.sp,
            color = MediumText
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Role / Login Mode Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundLight, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (loginMode == "Owner") BatchPink else Color.Transparent)
                    .clickable { loginMode = "Owner"; errorMessage = null }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = if (loginMode == "Owner") Color.White else MediumText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Owner / Baker",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (loginMode == "Owner") Color.White else MediumText
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (loginMode == "Staff PIN") BatchPink else Color.Transparent)
                    .clickable { loginMode = "Staff PIN"; errorMessage = null }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Pin,
                        contentDescription = null,
                        tint = if (loginMode == "Staff PIN") Color.White else MediumText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kitchen Staff PIN",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (loginMode == "Staff PIN") Color.White else MediumText
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AmberLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage!!, fontSize = 13.sp, color = DarkText, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (loginMode == "Owner") {
            // Email or Phone
            OutlinedTextField(
                value = emailOrPhone,
                onValueChange = { emailOrPhone = it; errorMessage = null },
                label = { Text("Email or Mobile Number") },
                placeholder = { Text("tyne@batchboss.com or +27 82...") },
                leadingIcon = {
                    Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = LightText)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_login_email"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BatchPink,
                    unfocusedBorderColor = BorderLight
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = LightText)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = LightText
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_login_password"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BatchPink,
                    unfocusedBorderColor = BorderLight
                ),
                singleLine = true
            )
        } else {
            // Staff PIN input
            OutlinedTextField(
                value = staffPin,
                onValueChange = { if (it.length <= 6) staffPin = it; errorMessage = null },
                label = { Text("4-Digit Kitchen PIN") },
                placeholder = { Text("Enter 4-digit PIN") },
                leadingIcon = {
                    Icon(Icons.Outlined.Pin, contentDescription = null, tint = LightText)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle PIN visibility",
                            tint = LightText
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_staff_pin"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BatchPink,
                    unfocusedBorderColor = BorderLight
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bakery Branch selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Bakery Branch / Workspace",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MediumText,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Main Flagship", "Studio 2", "Home Kitchen").forEach { branch ->
                    val isSelected = selectedBranch.startsWith(branch.split(" ")[0])
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) BatchPinkLight else BackgroundLight,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) BatchPink else BorderLight
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedBranch = branch }
                    ) {
                        Text(
                            text = branch,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) BatchPink else DarkText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    colors = CheckboxDefaults.colors(checkedColor = BatchPink)
                )
                Text("Remember me", fontSize = 13.sp, color = MediumText)
            }

            Text(
                text = "Forgot Password?",
                fontSize = 13.sp,
                color = BatchPink,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onNavigateToForgot)
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (loginMode == "Owner") {
                    if (emailOrPhone.isBlank()) {
                        errorMessage = "Please enter your email or mobile number"
                        return@Button
                    }
                    if (password.length < 4) {
                        errorMessage = "Please enter your password"
                        return@Button
                    }
                } else {
                    if (staffPin.length < 4) {
                        errorMessage = "Please enter your 4-digit Kitchen PIN"
                        return@Button
                    }
                }
                errorMessage = null
                onLoginWithDetails?.invoke(emailOrPhone, selectedBranch)
                onLoginSuccess()
            },
            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_login_submit")
        ) {
            Text("Sign In to Bakery", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Biometric / Quick Sign In
        OutlinedButton(
            onClick = {
                onLoginWithDetails?.invoke("Biometric User", selectedBranch)
                onLoginSuccess()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
        ) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = BatchPink)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign in with Biometrics / Face ID", color = DarkText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("or continue with", fontSize = 13.sp, color = LightText)

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onLoginSuccess,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
            ) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = BatchPink, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Google", color = DarkText, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onLoginSuccess,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
            ) {
                Icon(Icons.Filled.PhoneIphone, contentDescription = null, tint = DarkText, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apple", color = DarkText, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account? ", color = MediumText, fontSize = 14.sp)
            Text(
                text = "Sign up",
                color = BatchPink,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onNavigateToSignUp)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun CreateAccountScreen(
    onAccountCreated: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onAccountCreatedWithData: ((fullName: String, bakeryName: String, specialty: String, phone: String, city: String, operatingModel: String, currency: String, email: String) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    var fullName by remember { mutableStateOf("") }
    var bakeryName by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("Cakes & Cupcakes") }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Cape Town") }
    var operatingModel by remember { mutableStateOf("Home Kitchen") }
    var currency by remember { mutableStateOf("ZAR (R)") }
    var taxNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(true) }
    var newsletterOptIn by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val passwordStrength = remember(password) {
        when {
            password.isEmpty() -> 0
            password.length < 6 -> 1
            password.any { it.isDigit() } && password.any { it.isLetter() } && password.length >= 8 -> 3
            password.length >= 6 -> 2
            else -> 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("screen_create_account")
    ) {
        IconButton(
            onClick = { onBack?.invoke() ?: onNavigateToLogin() },
            modifier = Modifier.testTag("btn_back_create_account")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Create Bakery Account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Set up your bakery profile and master your recipe costing",
            fontSize = 14.sp,
            color = MediumText
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AmberLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage!!, fontSize = 13.sp, color = DarkText, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Baker Full Name
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it; errorMessage = null },
            label = { Text("Baker / Owner Full Name *") },
            placeholder = { Text("e.g. Tyne Jenkins") },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = LightText) },
            modifier = Modifier.fillMaxWidth().testTag("input_signup_fullname"),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Bakery Name
        OutlinedTextField(
            value = bakeryName,
            onValueChange = { bakeryName = it; errorMessage = null },
            label = { Text("Bakery / Business Name *") },
            placeholder = { Text("e.g. Tyne's Artisan Bakery") },
            leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = LightText) },
            modifier = Modifier.fillMaxWidth().testTag("input_signup_bakeryname"),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Bakery Specialty Focus
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Bakery Specialty Focus",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MediumText,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            val specialties = listOf("Cakes & Cupcakes", "Artisan Breads", "Home Bakery", "Pastries", "Weddings", "Cookies")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                specialties.take(3).forEach { spec ->
                    val isSelected = specialty == spec
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) BatchPinkLight else BackgroundLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BatchPink else BorderLight),
                        modifier = Modifier.weight(1f).clickable { specialty = spec }
                    ) {
                        Text(
                            text = spec,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) BatchPink else DarkText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                specialties.drop(3).forEach { spec ->
                    val isSelected = specialty == spec
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) BatchPinkLight else BackgroundLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BatchPink else BorderLight),
                        modifier = Modifier.weight(1f).clickable { specialty = spec }
                    ) {
                        Text(
                            text = spec,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) BatchPink else DarkText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Phone / WhatsApp & City
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; errorMessage = null },
                label = { Text("Phone / WhatsApp *") },
                placeholder = { Text("082 555 1234") },
                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = LightText) },
                modifier = Modifier.weight(1.2f).testTag("input_signup_phone"),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it; errorMessage = null },
                label = { Text("City / Region") },
                placeholder = { Text("Cape Town") },
                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = LightText) },
                modifier = Modifier.weight(1f).testTag("input_signup_city"),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Operating Setup & Preferred Currency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(
                    text = "Kitchen Setup",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MediumText,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Home Kitchen", "Storefront").forEach { model ->
                        val isSelected = operatingModel == model
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BatchPinkLight else BackgroundLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BatchPink else BorderLight),
                            modifier = Modifier.weight(1f).clickable { operatingModel = model }
                        ) {
                            Text(
                                text = model,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BatchPink else DarkText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Currency",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MediumText,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ZAR (R)", "USD ($)").forEach { curr ->
                        val isSelected = currency == curr
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BatchPinkLight else BackgroundLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BatchPink else BorderLight),
                            modifier = Modifier.weight(1f).clickable { currency = curr }
                        ) {
                            Text(
                                text = curr,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BatchPink else DarkText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tax / VAT Number (Optional)
        OutlinedTextField(
            value = taxNumber,
            onValueChange = { taxNumber = it },
            label = { Text("Tax / VAT Number (Optional)") },
            placeholder = { Text("e.g. 4120289192") },
            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null, tint = LightText) },
            modifier = Modifier.fillMaxWidth().testTag("input_signup_tax"),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Email Address
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email Address *") },
            placeholder = { Text("tyne@batchboss.com") },
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = LightText) },
            modifier = Modifier.fillMaxWidth().testTag("input_signup_email"),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password *") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = LightText) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = LightText
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("input_signup_password"),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        if (password.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val strengthLabel = when (passwordStrength) {
                    3 -> "Strong"
                    2 -> "Medium"
                    else -> "Weak"
                }
                val strengthColor = when (passwordStrength) {
                    3 -> MintGreen
                    2 -> WarmAmber
                    else -> BatchPink
                }
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index < passwordStrength) strengthColor else BorderLight)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strengthLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = strengthColor
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Confirm Password
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorMessage = null },
            label = { Text("Confirm Password *") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = LightText) },
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = LightText
                    )
                }
            },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("input_signup_confirm_password"),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        if (confirmPassword.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            val isMatch = password == confirmPassword
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isMatch) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (isMatch) MintGreen else BatchPink,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isMatch) "Passwords match" else "Passwords do not match",
                    fontSize = 12.sp,
                    color = if (isMatch) MintGreen else BatchPink
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Terms of Service Checkbox
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = agreedToTerms,
                onCheckedChange = { agreedToTerms = it },
                colors = CheckboxDefaults.colors(checkedColor = BatchPink)
            )
            Text(
                text = "I agree to the Terms of Service & Privacy Policy",
                fontSize = 12.sp,
                color = MediumText
            )
        }

        // Newsletter Checkbox
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = newsletterOptIn,
                onCheckedChange = { newsletterOptIn = it },
                colors = CheckboxDefaults.colors(checkedColor = BatchPink)
            )
            Text(
                text = "Send me weekly bakery costing & margin tips",
                fontSize = 12.sp,
                color = MediumText
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                when {
                    fullName.trim().isEmpty() -> errorMessage = "Please enter your name"
                    bakeryName.trim().isEmpty() -> errorMessage = "Please enter your bakery name"
                    phone.trim().isEmpty() -> errorMessage = "Please enter your phone or WhatsApp number"
                    email.trim().isEmpty() || !email.contains("@") -> errorMessage = "Please enter a valid email address"
                    password.length < 6 -> errorMessage = "Password must be at least 6 characters"
                    password != confirmPassword -> errorMessage = "Passwords do not match"
                    !agreedToTerms -> errorMessage = "Please agree to the Terms of Service"
                    else -> {
                        errorMessage = null
                        onAccountCreatedWithData?.invoke(
                            fullName.trim(),
                            bakeryName.trim(),
                            specialty,
                            phone.trim(),
                            city.trim(),
                            operatingModel,
                            currency,
                            email.trim()
                        )
                        onAccountCreated()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_create_account_submit")
        ) {
            Text("Create Bakery Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Social Sign-up Dividers
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
            Text("  or sign up with  ", color = LightText, fontSize = 12.sp)
            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onAccountCreated,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
        ) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = DarkText)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign up with Google", color = DarkText, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Already have an account? ", color = MediumText, fontSize = 14.sp)
            Text(
                text = "Login",
                color = BatchPink,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onNavigateToLogin)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    onResetSent: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var sentNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("screen_forgot_password"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(BatchPinkContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MarkEmailRead,
                contentDescription = null,
                tint = BatchPink,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Forgot Password?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "No worries! Enter your email address and we'll send you a link to reset your password.",
            fontSize = 14.sp,
            color = MediumText,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = LightText) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                sentNotice = true
                onResetSent()
            },
            colors = ButtonDefaults.buttonColors(containerColor = BatchPink),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_send_reset_link")
        ) {
            Text("Send Reset Link", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Back to Login",
            color = MediumText,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onNavigateBack)
                .padding(8.dp)
        )
    }
}
