package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.BatchBossViewModel
import com.example.ui.Screen
import com.example.ui.components.BatchBossBottomNav
import com.example.ui.screens.*
import com.example.ui.theme.BatchBossTheme
import com.example.data.local.ProductServiceEntity
import com.example.data.local.ProductPriceHistoryEntity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BatchBossTheme {
                BatchBossApp()
            }
        }
    }
}

@Composable
fun BatchBossApp(
    viewModel: BatchBossViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedTimeFrame by viewModel.selectedTimeFrame.collectAsStateWithLifecycle()

    val recipes by viewModel.allRecipes.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val allInventory by viewModel.allInventory.collectAsStateWithLifecycle()
    val lowStockItems by viewModel.lowStockItems.collectAsStateWithLifecycle()
    val suppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val specials by viewModel.allSpecials.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()

    val selectedRecipe by viewModel.selectedRecipe.collectAsStateWithLifecycle()
    val selectedRecipeIngredients by viewModel.selectedRecipeIngredients.collectAsStateWithLifecycle()
    val selectedSupplier by viewModel.selectedSupplier.collectAsStateWithLifecycle()
    val selectedSpecial by viewModel.selectedSpecial.collectAsStateWithLifecycle()

    val isPremiumUser by viewModel.isPremiumUser.collectAsStateWithLifecycle()
    val allInvoices by viewModel.allInvoices.collectAsStateWithLifecycle()
    val allQuotes by viewModel.allQuotes.collectAsStateWithLifecycle()
    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val currentUserAccount by viewModel.currentUserAccount.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProductsServices.collectAsStateWithLifecycle()
    val showPaywall by viewModel.showPaywall.collectAsStateWithLifecycle()
    val paywallFeature by viewModel.paywallFeatureName.collectAsStateWithLifecycle()
    val paywallDesc by viewModel.paywallDescription.collectAsStateWithLifecycle()
    val bakingSupplyStores by viewModel.allBakingSupplyStores.collectAsStateWithLifecycle()
    val allRegisteredUsers by viewModel.allRegisteredUsers.collectAsStateWithLifecycle()
    val allLoginLogs by viewModel.allLoginLogs.collectAsStateWithLifecycle()
    val allDeletionRequests by viewModel.allDeletionRequests.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Determine if bottom navigation bar should be visible
    val showBottomBar = when (currentScreen) {
        is Screen.Home,
        is Screen.RecipesList,
        is Screen.InventoryList,
        is Screen.SuppliersList,
        is Screen.BakingSupplyStoreLocator,
        is Screen.QuickActions -> true
        else -> false
    }

    // Back handling
    BackHandler(enabled = currentScreen != Screen.Home) {
        when (currentScreen) {
            is Screen.RecipeDetail, is Screen.CreateEditRecipe -> viewModel.navigateTo(Screen.RecipesList)
            is Screen.RecipeIngredients -> {
                val rId = (currentScreen as Screen.RecipeIngredients).recipeId
                viewModel.navigateTo(Screen.RecipeDetail(rId))
            }
            is Screen.RecipeCosting -> {
                val rId = (currentScreen as Screen.RecipeCosting).recipeId
                viewModel.navigateTo(Screen.RecipeIngredients(rId))
            }
            is Screen.PricingCalculator -> {
                val rId = (currentScreen as Screen.PricingCalculator).recipeId
                viewModel.navigateTo(Screen.RecipeCosting(rId))
            }
            is Screen.ProfitAnalysis -> {
                val rId = (currentScreen as Screen.ProfitAnalysis).recipeId
                viewModel.navigateTo(Screen.PricingCalculator(rId))
            }
            is Screen.SupplierDetail -> viewModel.navigateTo(Screen.SuppliersList)
            is Screen.BakingSupplyStoreLocator, is Screen.BakingSupplyStoreDetail -> viewModel.navigateTo(Screen.SuppliersList)
            is Screen.SpecialsList -> {
                val sId = selectedSupplier?.id ?: 1L
                viewModel.navigateTo(Screen.SupplierDetail(sId))
            }
            is Screen.SpecialDetail -> viewModel.navigateTo(Screen.SpecialsList)
            is Screen.UnitConverter, is Screen.RecipeScaler, is Screen.AddSupplier,
            is Screen.InvoicesList, is Screen.QuotesList, is Screen.CustomersList,
            is Screen.ProductsServicesList, is Screen.PremiumSubscription, is Screen.AboutBatchBoss,
            is Screen.AccountDataDeletion, is Screen.MasterBackend -> viewModel.navigateTo(Screen.QuickActions)
            is Screen.Notifications, is Screen.Tasks, is Screen.LowStock -> viewModel.navigateTo(Screen.Home)
            is Screen.Login, is Screen.CreateAccount, is Screen.ForgotPassword -> viewModel.navigateTo(Screen.Home)
            else -> viewModel.navigateTo(Screen.Home)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                BatchBossBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { index -> viewModel.selectTab(index) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            when (val screen = currentScreen) {
                is Screen.Splash -> {
                    SplashScreen(
                        onNavigateToSignUp = { viewModel.navigateTo(Screen.CreateAccount) },
                        onNavigateToLogin = { viewModel.navigateTo(Screen.Login) },
                        onNavigateToHome = { viewModel.navigateTo(Screen.Home) },
                        onNavigateNext = { viewModel.navigateTo(Screen.Login) }
                    )
                }

                is Screen.Onboarding -> {
                    OnboardingScreen(
                        onGetStarted = { viewModel.navigateTo(Screen.CreateAccount) },
                        onSkip = { viewModel.navigateTo(Screen.Home) }
                    )
                }

                is Screen.Login -> {
                    LoginScreen(
                        onLoginSuccess = {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Logged in successfully!") }
                            viewModel.navigateTo(Screen.Home)
                        },
                        onNavigateToSignUp = { viewModel.navigateTo(Screen.CreateAccount) },
                        onNavigateToForgot = { viewModel.navigateTo(Screen.ForgotPassword) },
                        onBack = { viewModel.navigateTo(Screen.Home) },
                        onLoginWithDetails = { emailOrPhone, branch ->
                            viewModel.loginUser(emailOrPhone) { success, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    )
                }

                is Screen.CreateAccount -> {
                    CreateAccountScreen(
                        onAccountCreated = {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Account created! Welcome to BatchBoss.") }
                            viewModel.navigateTo(Screen.Home)
                        },
                        onNavigateToLogin = { viewModel.navigateTo(Screen.Login) },
                        onBack = { viewModel.navigateTo(Screen.Home) },
                        onAccountCreatedWithData = { fullName, bakeryName, specialty, phone, city, operatingModel, currency, email ->
                            viewModel.createAccountWithDetails(
                                firstName = fullName.substringBefore(" "),
                                surname = fullName.substringAfter(" ", ""),
                                email = email,
                                password = "",
                                bakeryName = bakeryName,
                                phone = phone,
                                city = city,
                                operatingModel = operatingModel,
                                currency = currency,
                                specialty = specialty
                            ) {
                                coroutineScope.launch { snackbarHostState.showSnackbar("Welcome to BatchBoss, $fullName!") }
                                viewModel.navigateTo(
                                    Screen.WelcomeEmail(
                                        fullName = fullName,
                                        email = email,
                                        bakeryName = bakeryName,
                                        city = city,
                                        operatingModel = operatingModel,
                                        currency = currency
                                    )
                                )
                            }
                        }
                    )
                }

                is Screen.WelcomeEmail -> {
                    val welcomeScreen = currentScreen as Screen.WelcomeEmail
                    WelcomeEmailScreen(
                        fullName = welcomeScreen.fullName,
                        email = welcomeScreen.email,
                        bakeryName = welcomeScreen.bakeryName,
                        city = welcomeScreen.city,
                        operatingModel = welcomeScreen.operatingModel,
                        currency = welcomeScreen.currency,
                        onProceedToHome = { viewModel.navigateTo(Screen.Home) }
                    )
                }

                is Screen.ForgotPassword -> {
                    ForgotPasswordScreen(
                        onNavigateBack = { viewModel.navigateTo(Screen.Login) },
                        onResetSent = {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Password reset link sent to your email.") }
                            viewModel.navigateTo(Screen.Login)
                        }
                    )
                }

                is Screen.Home -> {
                    HomeScreen(
                        recipes = recipes,
                        lowStockItems = lowStockItems,
                        invoices = allInvoices,
                        userAccount = currentUserAccount,
                        customersCount = customers.size,
                        unreadNotificationsCount = unreadCount,
                        timeFrame = selectedTimeFrame,
                        onTimeFrameChanged = { viewModel.setTimeFrame(it) },
                        onOpenNotifications = { viewModel.navigateTo(Screen.Notifications) },
                        onOpenTasks = { viewModel.navigateTo(Screen.Tasks) },
                        onOpenLowStock = { viewModel.navigateTo(Screen.LowStock) },
                        onOpenRecipesList = { viewModel.navigateTo(Screen.RecipesList) },
                        onRecipeClick = { id -> viewModel.navigateTo(Screen.RecipeDetail(id)) },
                        onOpenTools = { viewModel.navigateTo(Screen.QuickActions) },
                        onOpenCustomers = { viewModel.navigateTo(Screen.CustomersList) },
                        onOpenInvoices = { viewModel.navigateTo(Screen.InvoicesList) },
                        onOpenScanner = { viewModel.navigateTo(Screen.AiRecipeScanner) }
                    )
                }

                is Screen.Notifications -> {
                    NotificationsScreen(
                        notifications = notifications,
                        onBack = { viewModel.navigateTo(Screen.Home) },
                        onMarkAllRead = {
                            viewModel.markAllNotificationsRead()
                            coroutineScope.launch { snackbarHostState.showSnackbar("All notifications marked as read") }
                        },
                        onNotificationClick = { notif ->
                            viewModel.markNotificationAsRead(notif.id)
                            if (notif.title.contains("Welcome", ignoreCase = true) || notif.type.equals("welcome", ignoreCase = true)) {
                                val acc = currentUserAccount
                                viewModel.navigateTo(
                                    Screen.WelcomeEmail(
                                        fullName = if (acc != null) "${acc.firstName} ${acc.surname}".trim() else "Head Baker",
                                        email = acc?.email ?: "baker@batchboss.com",
                                        bakeryName = acc?.bakeryName ?: "Artisan Bakery",
                                        city = acc?.city ?: "Johannesburg",
                                        operatingModel = acc?.operatingModel ?: "Home-Based Bakery",
                                        currency = acc?.currency ?: "ZAR (R)"
                                    )
                                )
                            }
                        }
                    )
                }

                is Screen.Tasks -> {
                    TasksScreen(
                        tasks = tasks,
                        onBack = { viewModel.navigateTo(Screen.Home) },
                        onToggleTask = { task -> viewModel.toggleTaskCompletion(task) },
                        onAddTask = { title, orderRef, dueTime, priority, dueDate, dayOfWeek ->
                            viewModel.addTask(title, orderRef, dueTime, priority, dueDate, dayOfWeek)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Task added successfully") }
                        }
                    )
                }

                is Screen.LowStock -> {
                    InventoryListScreen(
                        allInventory = allInventory,
                        lowStockItems = lowStockItems,
                        initialTab = 1,
                        onBack = { viewModel.navigateTo(Screen.Home) },
                        onToggleAlert = { id -> viewModel.toggleInventoryAlert(id) },
                        onUpdateStockPrice = { id, unitPrice, currentStock, minStock ->
                            viewModel.updateStockPrice(id, unitPrice, currentStock, minStock)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Stock price and quantity updated!") }
                        },
                        onAddNewStockItem = { name, unitPrice, currentStock, minStock, unit ->
                            viewModel.addNewStockItem(name, unitPrice, currentStock, minStock, unit)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Added $name to stock!") }
                        },
                        onDeleteStockItem = { id ->
                            viewModel.deleteStockItem(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Stock item removed") }
                        }
                    )
                }

                is Screen.InventoryList -> {
                    InventoryListScreen(
                        allInventory = allInventory,
                        lowStockItems = lowStockItems,
                        initialTab = 0,
                        onBack = { viewModel.navigateTo(Screen.Home) },
                        onToggleAlert = { id -> viewModel.toggleInventoryAlert(id) },
                        onUpdateStockPrice = { id, unitPrice, currentStock, minStock ->
                            viewModel.updateStockPrice(id, unitPrice, currentStock, minStock)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Stock price and quantity updated!") }
                        },
                        onAddNewStockItem = { name, unitPrice, currentStock, minStock, unit ->
                            viewModel.addNewStockItem(name, unitPrice, currentStock, minStock, unit)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Added $name to stock!") }
                        },
                        onDeleteStockItem = { id ->
                            viewModel.deleteStockItem(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Stock item removed") }
                        },
                        onScanBarcode = { viewModel.navigateTo(Screen.BarcodeScanner) }
                    )
                }

                is Screen.RecipesList -> {
                    RecipesListScreen(
                        recipes = recipes,
                        onRecipeClick = { id -> viewModel.navigateTo(Screen.RecipeDetail(id)) },
                        onCalculatePricingClick = { id -> viewModel.navigateTo(Screen.PricingCalculator(id)) },
                        onCreateRecipeClick = { viewModel.navigateTo(Screen.CreateEditRecipe()) },
                        isPremium = isPremiumUser,
                        onUnlockPremium = {
                            viewModel.triggerPaywall(
                                "Unlimited Recipes",
                                "Upgrade to BatchBoss Pro to store more than 5 recipes with complete pricing and batch scaling."
                            )
                        },
                        onScanRecipeAi = { viewModel.navigateTo(Screen.AiRecipeScanner) },
                        onDeleteRecipe = { id ->
                            viewModel.deleteRecipe(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Recipe deleted successfully") }
                        }
                    )
                }

                is Screen.RecipeDetail -> {
                    RecipeDetailScreen(
                        recipe = selectedRecipe,
                        ingredients = selectedRecipeIngredients,
                        onBack = { viewModel.navigateTo(Screen.RecipesList) },
                        onToggleFavorite = { selectedRecipe?.let { viewModel.toggleFavoriteRecipe(it.id) } },
                        onViewRecipeIngredients = { selectedRecipe?.let { viewModel.navigateTo(Screen.RecipeIngredients(it.id)) } },
                        onCalculatePricing = { selectedRecipe?.let { viewModel.navigateTo(Screen.RecipeCosting(it.id)) } },
                        onUpdatePricing = { id, labour, overheads, pkg, utils, profit, customSellingPrice ->
                            viewModel.updateRecipePricing(id, labour, overheads, pkg, utils, profit, customSellingPrice)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Recipe pricing saved!") }
                        },
                        onDeleteRecipe = { id ->
                            viewModel.deleteRecipe(id)
                            viewModel.navigateTo(Screen.RecipesList)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Recipe deleted successfully") }
                        },
                        onUpdatePhoto = { photoUri ->
                            selectedRecipe?.let {
                                viewModel.updateRecipePhoto(it.id, photoUri)
                                coroutineScope.launch { snackbarHostState.showSnackbar("Recipe photo updated!") }
                            }
                        }
                    )
                }

                is Screen.RecipeIngredients -> {
                    RecipeIngredientsScreen(
                        recipe = selectedRecipe,
                        ingredients = selectedRecipeIngredients,
                        onBack = { selectedRecipe?.let { viewModel.navigateTo(Screen.RecipeDetail(it.id)) } },
                        onNavigateToCosting = { selectedRecipe?.let { viewModel.navigateTo(Screen.RecipeCosting(it.id)) } },
                        onUpdateIngredientCost = { id, cost, qty ->
                            viewModel.updateIngredientCost(id, cost, qty)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Ingredient cost updated!") }
                        },
                        onDeleteIngredient = { id ->
                            viewModel.deleteIngredient(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Ingredient removed from recipe") }
                        }
                    )
                }

                is Screen.RecipeCosting -> {
                    val ingredientsCost = selectedRecipeIngredients.sumOf { it.cost }
                    RecipeCostingScreen(
                        recipe = selectedRecipe,
                        ingredientsCost = ingredientsCost,
                        onBack = { selectedRecipe?.let { viewModel.navigateTo(Screen.RecipeIngredients(it.id)) } },
                        onCalculatePricing = { selectedRecipe?.let { viewModel.navigateTo(Screen.PricingCalculator(it.id)) } },
                        onUpdatePricing = { id, labour, overheads, pkg, utils, profit, customSellingPrice ->
                            viewModel.updateRecipePricing(id, labour, overheads, pkg, utils, profit, customSellingPrice)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Recipe costing updated!") }
                        }
                    )
                }

                is Screen.PricingCalculator -> {
                    val ingredientsCost = selectedRecipeIngredients.sumOf { it.cost }
                    PricingCalculatorScreen(
                        recipe = selectedRecipe,
                        ingredientsCost = ingredientsCost,
                        onBack = { selectedRecipe?.let { viewModel.navigateTo(Screen.RecipeCosting(it.id)) } },
                        onSavePricing = { margin ->
                            selectedRecipe?.let { viewModel.updateRecipeProfitMargin(it.id, margin) }
                            coroutineScope.launch { snackbarHostState.showSnackbar("Profit margin saved!") }
                        },
                        onViewAnalysis = { selectedRecipe?.let { viewModel.navigateTo(Screen.ProfitAnalysis(it.id)) } },
                        onUpdatePricing = { id, labour, overheads, pkg, utils, profit, customSellingPrice ->
                            viewModel.updateRecipePricing(id, labour, overheads, pkg, utils, profit, customSellingPrice)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Recipe pricing saved!") }
                        }
                    )
                }

                is Screen.ProfitAnalysis -> {
                    ProfitAnalysisScreen(
                        recipe = selectedRecipe,
                        onBack = { selectedRecipe?.let { viewModel.navigateTo(Screen.PricingCalculator(it.id)) } }
                    )
                }

                is Screen.CreateEditRecipe -> {
                    CreateEditRecipeScreen(
                        onBack = { viewModel.navigateTo(Screen.RecipesList) },
                        onSave = { name, category, desc, servings, batchSize, labour, overheads, pkg, utils, profit, ings ->
                            viewModel.saveNewRecipe(name, category, desc, servings, batchSize, labour, overheads, pkg, utils, profit, ings)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Recipe created successfully!") }
                        },
                        onSaveWithPhoto = { name, category, desc, servings, batchSize, labour, overheads, pkg, utils, profit, ings, photoUri ->
                            viewModel.saveNewRecipe(name, category, desc, servings, batchSize, labour, overheads, pkg, utils, profit, ings, photoUri = photoUri)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Recipe created successfully!") }
                        }
                    )
                }

                is Screen.SuppliersList -> {
                    SuppliersListScreen(
                        suppliers = suppliers,
                        onSupplierClick = { id -> viewModel.navigateTo(Screen.SupplierDetail(id)) },
                        onToggleFavorite = { id -> viewModel.toggleFavoriteSupplier(id) },
                        onAddSupplier = { viewModel.navigateTo(Screen.AddSupplier) },
                        isPremium = isPremiumUser,
                        onUnlockPremium = {
                            viewModel.triggerPaywall(
                                "Suppliers Directory",
                                "Unlock verified supplier directory, wholesale contact lines, and exclusive supplier discounts."
                            )
                        },
                        onOpenStoreLocator = { viewModel.navigateTo(Screen.BakingSupplyStoreLocator) }
                    )
                }

                is Screen.SupplierDetail -> {
                    SupplierDetailScreen(
                        supplier = selectedSupplier,
                        onBack = { viewModel.navigateTo(Screen.SuppliersList) },
                        onToggleFavorite = { selectedSupplier?.let { viewModel.toggleFavoriteSupplier(it.id) } },
                        onViewSpecials = { viewModel.navigateTo(Screen.SpecialsList) }
                    )
                }

                is Screen.SpecialsList -> {
                    SpecialsListScreen(
                        specials = specials,
                        onBack = { selectedSupplier?.let { viewModel.navigateTo(Screen.SupplierDetail(it.id)) } ?: viewModel.navigateTo(Screen.SuppliersList) },
                        onSpecialClick = { id -> viewModel.navigateTo(Screen.SpecialDetail(id)) }
                    )
                }

                is Screen.SpecialDetail -> {
                    SpecialDetailScreen(
                        special = selectedSpecial,
                        onBack = { viewModel.navigateTo(Screen.SpecialsList) },
                        onAddToCart = { qty ->
                            coroutineScope.launch { snackbarHostState.showSnackbar("Added $qty item(s) to shopping list!") }
                            viewModel.navigateTo(Screen.SpecialsList)
                        }
                    )
                }

                is Screen.AddSupplier -> {
                    AddSupplierScreen(
                        onBack = { viewModel.navigateTo(Screen.SuppliersList) },
                        onSaveSupplier = { name, cat, phone, email, web ->
                            viewModel.saveNewSupplier(name, cat, phone, email, web)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Supplier added successfully!") }
                        }
                    )
                }

                is Screen.QuickActions -> {
                    QuickActionsScreen(
                        onNewRecipeClick = {
                            if (!isPremiumUser && recipes.size >= 5) {
                                viewModel.triggerPaywall("Unlimited Recipes", "You have reached the 5-recipe limit on the free tier. Upgrade to Pro for unlimited recipes.")
                            } else {
                                viewModel.navigateTo(Screen.CreateEditRecipe())
                            }
                        },
                        onViewRecipesClick = { viewModel.navigateTo(Screen.RecipesList) },
                        onViewInventoryClick = { viewModel.navigateTo(Screen.InventoryList) },
                        onViewSuppliersClick = { viewModel.navigateTo(Screen.SuppliersList) },
                        onOpenUnitConverter = { viewModel.navigateTo(Screen.UnitConverter) },
                        onOpenRecipeScaler = { viewModel.navigateTo(Screen.RecipeScaler) },
                        onOpenTasks = { viewModel.navigateTo(Screen.Tasks) },
                        onOpenInvoices = {
                            if (isPremiumUser) {
                                viewModel.navigateTo(Screen.InvoicesList)
                            } else {
                                viewModel.triggerPaywall("Invoicing Suite", "Create professional bakery invoices, track client payments, and manage due dates.")
                            }
                        },
                        onOpenQuotes = {
                            if (isPremiumUser) {
                                viewModel.navigateTo(Screen.QuotesList)
                            } else {
                                viewModel.triggerPaywall("Custom Cake Quotes", "Generate bespoke cake and event quotes with cost calculation and profit margins.")
                            }
                        },
                        onOpenPremium = { viewModel.navigateTo(Screen.PremiumSubscription) },
                        isPremium = isPremiumUser,
                        onNavigateToLogin = { viewModel.navigateTo(Screen.Login) },
                        onNavigateToSignUp = { viewModel.navigateTo(Screen.CreateAccount) },
                        onNavigateToForgotPassword = { viewModel.navigateTo(Screen.ForgotPassword) },
                        onOpenAiRecipeScanner = { viewModel.navigateTo(Screen.AiRecipeScanner) },
                        onOpenBarcodeScanner = { viewModel.navigateTo(Screen.BarcodeScanner) },
                        onOpenStoreLocator = { viewModel.navigateTo(Screen.BakingSupplyStoreLocator) },
                        onOpenCustomers = { viewModel.navigateTo(Screen.CustomersList) },
                        onOpenProductsServices = { viewModel.navigateTo(Screen.ProductsServicesList) },
                        onOpenAboutBatchBoss = { viewModel.navigateTo(Screen.AboutBatchBoss) },
                        onOpenAccountDataDeletion = { viewModel.navigateTo(Screen.AccountDataDeletion) },
                        onOpenMasterBackend = { viewModel.navigateTo(Screen.MasterBackend) }
                    )
                }

                is Screen.CustomersList -> {
                    CustomersScreen(
                        customers = customers,
                        onBack = { viewModel.navigateTo(Screen.QuickActions) },
                        onAddCustomer = { name, phone, email, address, notes ->
                            viewModel.addCustomer(name, phone, email, address, notes)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Customer added successfully!") }
                        },
                        onUpdateCustomer = { updated ->
                            viewModel.updateCustomer(updated)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Customer updated!") }
                        },
                        onDeleteCustomer = { id ->
                            viewModel.deleteCustomer(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Customer removed") }
                        }
                    )
                }

                is Screen.ProductsServicesList, is Screen.CreateEditProductService -> {
                    var showAddEditDialog by remember { mutableStateOf(screen is Screen.CreateEditProductService) }
                    var editingProduct by remember {
                        mutableStateOf(
                            if (screen is Screen.CreateEditProductService && screen.productId != null) {
                                allProducts.find { it.id == screen.productId }
                            } else null
                        )
                    }
                    var viewingHistoryProduct by remember { mutableStateOf<ProductServiceEntity?>(null) }

                    ProductsServicesListScreen(
                        products = allProducts,
                        recipes = recipes,
                        onBack = { viewModel.navigateTo(Screen.QuickActions) },
                        onAddProductClick = {
                            editingProduct = null
                            showAddEditDialog = true
                        },
                        onEditProductClick = { product ->
                            editingProduct = product
                            showAddEditDialog = true
                        },
                        onDeleteProductClick = { id ->
                            viewModel.deleteProductService(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Item removed from catalog") }
                        },
                        onViewPriceHistory = { product ->
                            viewingHistoryProduct = product
                        },
                        onNavigateToInvoices = { viewModel.navigateTo(Screen.InvoicesList) },
                        onNavigateToQuotes = { viewModel.navigateTo(Screen.QuotesList) }
                    )

                    if (showAddEditDialog) {
                        AddEditProductServiceDialog(
                            existing = editingProduct,
                            recipes = recipes,
                            onDismiss = {
                                showAddEditDialog = false
                                editingProduct = null
                            },
                            onSave = { name, desc, cat, sku, cost, price, unit, active, service, recipeId, notes ->
                                if (editingProduct != null) {
                                    viewModel.updateProductService(
                                        existing = editingProduct!!,
                                        name = name,
                                        description = desc,
                                        category = cat,
                                        imageUrl = editingProduct!!.imageUrl,
                                        sku = sku,
                                        costPrice = cost,
                                        sellingPrice = price,
                                        unit = unit,
                                        isActive = active,
                                        isService = service,
                                        linkedRecipeId = recipeId,
                                        changeNotes = notes
                                    )
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Updated '$name'") }
                                } else {
                                    viewModel.saveProductService(
                                        name = name,
                                        description = desc,
                                        category = cat,
                                        imageUrl = "",
                                        sku = sku,
                                        costPrice = cost,
                                        sellingPrice = price,
                                        unit = unit,
                                        isActive = active,
                                        isService = service,
                                        linkedRecipeId = recipeId
                                    )
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Added '$name' to catalog") }
                                }
                                showAddEditDialog = false
                                editingProduct = null
                            }
                        )
                    }

                    val viewingProd = viewingHistoryProduct
                    if (viewingProd != null) {
                        val historyList by viewModel.getPriceHistory(viewingProd.id).collectAsStateWithLifecycle(initialValue = emptyList())
                        PriceHistoryDialog(
                            product = viewingProd,
                            historyList = historyList,
                            onDismiss = { viewingHistoryProduct = null }
                        )
                    }
                }

                is Screen.InvoicesList -> {
                    InvoicesListScreen(
                        invoices = allInvoices,
                        products = allProducts,
                        profile = userProfile,
                        onBack = { viewModel.navigateTo(Screen.QuickActions) },
                        onNavigateToProducts = { viewModel.navigateTo(Screen.ProductsServicesList) },
                        onSaveProductService = { name, desc, cat, cost, price, unit ->
                            viewModel.saveProductService(
                                name = name,
                                description = desc,
                                category = cat,
                                costPrice = cost,
                                sellingPrice = price,
                                unit = unit
                            )
                        },
                        onCreateInvoice = { clientName, phone, desc, amount, dueDate, status, subtotal, discount, taxRate, taxAmount, items, totalCost ->
                            viewModel.createInvoice(clientName, phone, desc, amount, dueDate, status, subtotal, discount, taxRate, taxAmount, items, totalCost)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Invoice created!") }
                        },
                        onUpdateStatus = { id, status ->
                            viewModel.updateInvoiceStatus(id, status)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Invoice status updated to $status") }
                        },
                        onDeleteInvoice = { id ->
                            viewModel.deleteInvoice(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Invoice deleted") }
                        },
                        onUpdateBranding = { logoUri, bakeryName, phone, email, address, bankName, accountNumber, branchCode, vatNumber, defaultHourlyRate ->
                            viewModel.updateBusinessBranding(
                                logoUri = logoUri,
                                bakeryName = bakeryName,
                                phone = phone,
                                email = email,
                                address = address,
                                bankName = bankName,
                                accountNumber = accountNumber,
                                branchCode = branchCode,
                                vatNumber = vatNumber,
                                defaultHourlyRate = defaultHourlyRate
                            )
                            coroutineScope.launch { snackbarHostState.showSnackbar("Business branding updated!") }
                        }
                    )
                }

                is Screen.QuotesList -> {
                    QuotesListScreen(
                        quotes = allQuotes,
                        products = allProducts,
                        profile = userProfile,
                        onBack = { viewModel.navigateTo(Screen.QuickActions) },
                        onNavigateToProducts = { viewModel.navigateTo(Screen.ProductsServicesList) },
                        onSaveProductService = { name, desc, cat, cost, price, unit ->
                            viewModel.saveProductService(
                                name = name,
                                description = desc,
                                category = cat,
                                costPrice = cost,
                                sellingPrice = price,
                                unit = unit
                            )
                        },
                        onCreateQuote = { clientName, phone, eventType, eventDate, itemName, cost, margin, price, status, docType, subtotal, discount, taxRate, taxAmount, lineItems ->
                            viewModel.createQuote(clientName, phone, eventType, eventDate, itemName, cost, margin, price, status, docType, subtotal, discount, taxRate, taxAmount, lineItems)
                            coroutineScope.launch { snackbarHostState.showSnackbar("$docType created!") }
                        },
                        onUpdateStatus = { id, status ->
                            viewModel.updateQuoteStatus(id, status)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Status updated to $status") }
                        },
                        onConvertToInvoice = { quote ->
                            viewModel.convertQuoteToInvoice(quote)
                            coroutineScope.launch { snackbarHostState.showSnackbar("${quote.docType} converted to Invoice!") }
                        },
                        onDeleteQuote = { id ->
                            viewModel.deleteQuote(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Deleted") }
                        },
                        onUpdateBranding = { logoUri, bakeryName, phone, email, address, bankName, accountNumber, branchCode, vatNumber, defaultHourlyRate ->
                            viewModel.updateBusinessBranding(
                                logoUri = logoUri,
                                bakeryName = bakeryName,
                                phone = phone,
                                email = email,
                                address = address,
                                bankName = bankName,
                                accountNumber = accountNumber,
                                branchCode = branchCode,
                                vatNumber = vatNumber,
                                defaultHourlyRate = defaultHourlyRate
                            )
                            coroutineScope.launch { snackbarHostState.showSnackbar("Business branding updated!") }
                        }
                    )
                }

                is Screen.PremiumSubscription -> {
                    PremiumSubscriptionScreen(
                        isPremium = isPremiumUser,
                        onBack = { viewModel.navigateTo(Screen.QuickActions) },
                        onUpgrade = { plan ->
                            viewModel.upgradeToPremium(plan)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Upgraded to BatchBoss Pro! All features unlocked.") }
                        },
                        onCancelSubscription = {
                            viewModel.cancelPremium()
                            coroutineScope.launch { snackbarHostState.showSnackbar("Subscription reverted to Free tier.") }
                        },
                        onOpenAiScanner = { viewModel.navigateTo(Screen.AiRecipeScanner) },
                        onOpenBarcodeScanner = { viewModel.navigateTo(Screen.BarcodeScanner) }
                    )
                }

                is Screen.UnitConverter -> {
                    UnitConverterScreen(
                        onBack = { viewModel.navigateTo(Screen.QuickActions) }
                    )
                }

                is Screen.RecipeScaler -> {
                    RecipeScalerScreen(
                        recipes = recipes,
                        onBack = { viewModel.navigateTo(Screen.QuickActions) }
                    )
                }

                is Screen.AiRecipeScanner -> {
                    AiRecipeScannerScreen(
                        isPremium = isPremiumUser,
                        onBack = { viewModel.navigateTo(Screen.RecipesList) },
                        onNavigateToSubscription = { viewModel.navigateTo(Screen.PremiumSubscription) },
                        onSaveRecipe = { name, category, desc, servings, batchSize, labour, overheads, pkg, utils, profit, ings, instructions, photoUri ->
                            viewModel.saveNewRecipe(
                                name = name,
                                category = category,
                                description = desc,
                                servings = servings,
                                batchSize = batchSize,
                                labourCost = labour,
                                overheadsCost = overheads,
                                packagingCost = pkg,
                                utilitiesCost = utils,
                                profitMargin = profit,
                                ingredients = ings,
                                instructions = instructions,
                                photoUri = photoUri
                            )
                            coroutineScope.launch { snackbarHostState.showSnackbar("Recipe '$name' imported & created successfully!") }
                        }
                    )
                }

                is Screen.BarcodeScanner -> {
                    BarcodeScannerScreen(
                        onBack = { viewModel.navigateTo(Screen.InventoryList) },
                        onSaveIngredient = { name, currentStock, minStock, unit, packagePrice, gramsPerUnit, category, barcode ->
                            viewModel.saveScannedIngredient(
                                name = name,
                                currentStock = currentStock,
                                minStock = minStock,
                                unit = unit,
                                packagePrice = packagePrice,
                                gramsPerUnit = gramsPerUnit,
                                category = category,
                                barcode = barcode
                            )
                            coroutineScope.launch { snackbarHostState.showSnackbar("Added $name to stock & ingredient list!") }
                            viewModel.navigateTo(Screen.InventoryList)
                        }
                    )
                }

                is Screen.BakingSupplyStoreLocator, is Screen.BakingSupplyStoreDetail -> {
                    BakingSupplyStoreLocatorScreen(
                        stores = bakingSupplyStores,
                        onBack = { viewModel.navigateTo(Screen.SuppliersList) },
                        onToggleFavorite = { id -> viewModel.toggleFavoriteBakingSupplyStore(id) },
                        onAddCustomStore = { name, category, address, city, phone, website, specialties, highlights ->
                            viewModel.addCustomBakingSupplyStore(
                                name = name,
                                category = category,
                                address = address,
                                city = city,
                                phone = phone,
                                website = website,
                                specialties = specialties,
                                inStockHighlights = highlights
                            )
                            coroutineScope.launch { snackbarHostState.showSnackbar("Added $name to store locator!") }
                        },
                        onDeleteStore = { id ->
                            viewModel.deleteBakingSupplyStore(id)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Store removed") }
                        }
                    )
                }

                is Screen.AboutBatchBoss -> {
                    AboutBatchBossScreen(
                        onBack = { viewModel.navigateTo(Screen.QuickActions) }
                    )
                }

                is Screen.AccountDataDeletion -> {
                    AccountDataDeletionScreen(
                        userAccount = currentUserAccount,
                        onBack = { viewModel.navigateTo(Screen.QuickActions) },
                        onSubmitRequest = { reason ->
                            viewModel.submitDataDeletionRequest(reason) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Deletion request submitted for processing.")
                                }
                            }
                        },
                        onInstantDelete = {
                            viewModel.instantDeleteCurrentAccountAndData {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Account and data permanently deleted.")
                                }
                            }
                        }
                    )
                }

                is Screen.MasterBackend -> {
                    MasterBackendScreen(
                        users = allRegisteredUsers,
                        loginLogs = allLoginLogs,
                        deletionRequests = allDeletionRequests,
                        onBack = { viewModel.navigateTo(Screen.QuickActions) },
                        onDeleteUser = { uid ->
                            viewModel.adminDeleteUserData(uid) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("User account and data wiped.")
                                }
                            }
                        },
                        onProcessDeletionRequest = { reqId, uid ->
                            viewModel.adminProcessDeletionRequest(reqId, uid) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Deletion request approved and executed.")
                                }
                            }
                        },
                        onRejectDeletionRequest = { reqId ->
                            viewModel.adminRejectDeletionRequest(reqId) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Deletion request dismissed.")
                                }
                            }
                        },
                        onClearLogs = {
                            viewModel.adminClearLoginLogs()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Login audit logs cleared.")
                            }
                        },
                        onFactoryReset = {
                            viewModel.adminFactoryResetDatabase {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Database reset to zero state.")
                                }
                            }
                        },
                        onCreateUser = { first, last, email, bakery, phone, city, opModel, curr, isPro ->
                            viewModel.adminCreateUserAccount(first, last, email, bakery, phone, city, opModel, curr, isPro) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Bakery account stored successfully!")
                                }
                            }
                        },
                        onToggleUserPro = { uid, isPro ->
                            viewModel.adminToggleUserPro(uid, isPro)
                        },
                        onPurgeInvoices = { uid ->
                            viewModel.adminPurgeUserInvoices(uid) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Invoices purged for user.")
                                }
                            }
                        },
                        onPurgeQuotes = { uid ->
                            viewModel.adminPurgeUserQuotes(uid) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Quotes purged for user.")
                                }
                            }
                        }
                    )
                }
            }

            // Paywall Dialog
            if (showPaywall) {
                PremiumPaywallDialog(
                    featureName = paywallFeature,
                    description = paywallDesc,
                    onDismiss = { viewModel.dismissPaywall() },
                    onUpgrade = {
                        viewModel.dismissPaywall()
                        viewModel.navigateTo(Screen.PremiumSubscription)
                    }
                )
            }
        }
    }
}
