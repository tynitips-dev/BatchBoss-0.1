package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.remote.FirebaseService
import com.example.data.remote.WebSyncService
import com.example.data.repository.AppRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class Screen {
    data object Splash : Screen()
    data object Onboarding : Screen()
    data object Login : Screen()
    data object CreateAccount : Screen()
    data object ForgotPassword : Screen()

    data object Home : Screen()
    data object Notifications : Screen()
    data object Tasks : Screen()
    data object LowStock : Screen()
    data object QuickActions : Screen()

    data object RecipesList : Screen()
    data class RecipeDetail(val recipeId: Long) : Screen()
    data class RecipeIngredients(val recipeId: Long) : Screen()
    data class RecipeCosting(val recipeId: Long) : Screen()
    data class PricingCalculator(val recipeId: Long) : Screen()
    data class ProfitAnalysis(val recipeId: Long) : Screen()
    data class CreateEditRecipe(val recipeId: Long? = null) : Screen()

    data object SuppliersList : Screen()
    data class SupplierDetail(val supplierId: Long) : Screen()
    data object SpecialsList : Screen()
    data class SpecialDetail(val specialId: Long) : Screen()
    data object AddSupplier : Screen()

    data object UnitConverter : Screen()
    data object RecipeScaler : Screen()
    data object InventoryList : Screen()

    data object InvoicesList : Screen()
    data object QuotesList : Screen()
    data object CustomersList : Screen()
    data object ProductsServicesList : Screen()
    data class CreateEditProductService(val productId: Long? = null) : Screen()
    data object PremiumSubscription : Screen()
    data object AiRecipeScanner : Screen()
    data object BarcodeScanner : Screen()
    data object BakingSupplyStoreLocator : Screen()
    data object AboutBatchBoss : Screen()
    data object AccountDataDeletion : Screen()
    data object MasterBackend : Screen()
    data object FirebaseSync : Screen()
    data class BakingSupplyStoreDetail(val storeId: Long) : Screen()
    data class WelcomeEmail(
        val fullName: String,
        val email: String,
        val bakeryName: String,
        val city: String = "Cape Town",
        val operatingModel: String = "Home Kitchen",
        val currency: String = "ZAR (R)"
    ) : Screen()
}

class BatchBossViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val repository = AppRepository(database)

    // Auth Session Management
    private val authPrefs = application.getSharedPreferences("batchboss_auth_prefs", android.content.Context.MODE_PRIVATE)
    private val initialSavedUserId: Long = authPrefs.getLong("active_user_id", -1L)

    private val _isUserLoggedIn = MutableStateFlow(initialSavedUserId > 0)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    // Navigation stack / current screen
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Home, 1: Recipes, 2: Ingredients, 3: Suppliers, 4: More
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Dashboard time frame filter
    private val _selectedTimeFrame = MutableStateFlow("This Week")
    val selectedTimeFrame: StateFlow<String> = _selectedTimeFrame.asStateFlow()

    // Active User
    private val _currentUserId = MutableStateFlow<Long>(if (initialSavedUserId > 0) initialSavedUserId else 1L)
    val currentUserId: StateFlow<Long> = _currentUserId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserAccount: StateFlow<UserAccountEntity?> = _currentUserId
        .flatMapLatest { id -> repository.getUserById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Master Backend Streams
    val allRegisteredUsers: StateFlow<List<UserAccountEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLoginLogs: StateFlow<List<UserLoginLogEntity>> = repository.allLoginLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDeletionRequests: StateFlow<List<DataDeletionRequestEntity>> = repository.allDeletionRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Data streams scoped to active user account
    @OptIn(ExperimentalCoroutinesApi::class)
    val allRecipes: StateFlow<List<RecipeEntity>> = _currentUserId
        .flatMapLatest { id -> repository.getRecipesByUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allTasks: StateFlow<List<TaskEntity>> = _currentUserId
        .flatMapLatest { id -> repository.getTasksByUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allInventory: StateFlow<List<InventoryItemEntity>> = _currentUserId
        .flatMapLatest { id -> repository.getInventoryByUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val lowStockItems: StateFlow<List<InventoryItemEntity>> = _currentUserId
        .flatMapLatest { id -> repository.getLowStockByUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Customers scoped to active user
    @OptIn(ExperimentalCoroutinesApi::class)
    val allCustomers: StateFlow<List<CustomerEntity>> = _currentUserId
        .flatMapLatest { id -> repository.getCustomersByUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<SupplierEntity>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSpecials: StateFlow<List<SpecialDealEntity>> = repository.allSpecials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = repository.unreadNotificationsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Invoices & Quotes scoped to active user
    @OptIn(ExperimentalCoroutinesApi::class)
    val allInvoices: StateFlow<List<InvoiceEntity>> = _currentUserId
        .flatMapLatest { id -> repository.getInvoicesByUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allQuotes: StateFlow<List<QuoteEntity>> = _currentUserId
        .flatMapLatest { id -> repository.getQuotesByUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allProductsServices: StateFlow<List<ProductServiceEntity>> = _currentUserId
        .flatMapLatest { id -> repository.getProductsByUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User Profile & Premium Subscription
    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Baking Supply Stores
    val allBakingSupplyStores: StateFlow<List<BakingSupplyStoreEntity>> = repository.allBakingSupplyStores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedStoreId = MutableStateFlow<Long?>(1L)
    val selectedBakingSupplyStore: StateFlow<BakingSupplyStoreEntity?> = _selectedStoreId
        .flatMapLatest { id ->
            if (id != null) repository.getBakingSupplyStoreById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectBakingSupplyStore(id: Long) {
        _selectedStoreId.value = id
    }

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser: StateFlow<Boolean> = _isPremiumUser.asStateFlow()

    // Premium Paywall Modal State
    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall.asStateFlow()

    private val _paywallFeatureName = MutableStateFlow("BatchBoss Premium")
    val paywallFeatureName: StateFlow<String> = _paywallFeatureName.asStateFlow()

    private val _paywallDescription = MutableStateFlow("Upgrade to unlock all bakery tools.")
    val paywallDescription: StateFlow<String> = _paywallDescription.asStateFlow()

    init {
        // Observe user profile to sync premium status
        viewModelScope.launch {
            repository.userProfile.collect { profile ->
                if (profile != null) {
                    _isPremiumUser.value = profile.isPremium
                }
            }
        }
        // Ensure baking supply stores are seeded
        viewModelScope.launch {
            repository.ensureInitialBakingStores()
        }
    }

    // Selected recipe detail stream
    private val _selectedRecipeId = MutableStateFlow<Long?>(1L)
    val selectedRecipe: StateFlow<RecipeEntity?> = _selectedRecipeId
        .flatMapLatest { id ->
            if (id != null) repository.getRecipeById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedRecipeIngredients: StateFlow<List<RecipeIngredientEntity>> = _selectedRecipeId
        .flatMapLatest { id ->
            if (id != null) repository.getIngredientsForRecipe(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected supplier stream
    private val _selectedSupplierId = MutableStateFlow<Long?>(1L)
    val selectedSupplier: StateFlow<SupplierEntity?> = _selectedSupplierId
        .flatMapLatest { id ->
            if (id != null) repository.getSupplierById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedSupplierSpecials: StateFlow<List<SpecialDealEntity>> = _selectedSupplierId
        .flatMapLatest { id ->
            if (id != null) repository.getSpecialsForSupplier(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected special deal stream
    private val _selectedSpecialId = MutableStateFlow<Long?>(1L)
    val selectedSpecial: StateFlow<SpecialDealEntity?> = _selectedSpecialId
        .flatMapLatest { id ->
            if (id != null) repository.getSpecialById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Navigation functions
    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        when (screen) {
            is Screen.Home -> _selectedTab.value = 0
            is Screen.RecipesList -> _selectedTab.value = 1
            is Screen.InventoryList, is Screen.LowStock -> _selectedTab.value = 2
            is Screen.SuppliersList -> _selectedTab.value = 3
            is Screen.QuickActions, is Screen.UnitConverter, is Screen.RecipeScaler, is Screen.AboutBatchBoss, is Screen.AccountDataDeletion, is Screen.MasterBackend, is Screen.FirebaseSync -> _selectedTab.value = 4
            is Screen.RecipeDetail -> {
                _selectedRecipeId.value = screen.recipeId
                _selectedTab.value = 1
            }
            is Screen.RecipeIngredients -> {
                _selectedRecipeId.value = screen.recipeId
                _selectedTab.value = 1
            }
            is Screen.RecipeCosting -> {
                _selectedRecipeId.value = screen.recipeId
                _selectedTab.value = 1
            }
            is Screen.PricingCalculator -> {
                _selectedRecipeId.value = screen.recipeId
                _selectedTab.value = 1
            }
            is Screen.ProfitAnalysis -> {
                _selectedRecipeId.value = screen.recipeId
                _selectedTab.value = 1
            }
            is Screen.SupplierDetail -> {
                _selectedSupplierId.value = screen.supplierId
                _selectedTab.value = 3
            }
            is Screen.SpecialDetail -> {
                _selectedSpecialId.value = screen.specialId
                _selectedTab.value = 3
            }
            is Screen.BakingSupplyStoreLocator -> {
                _selectedTab.value = 3
            }
            is Screen.BakingSupplyStoreDetail -> {
                _selectedStoreId.value = screen.storeId
                _selectedTab.value = 3
            }
            else -> {}
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        when (index) {
            0 -> _currentScreen.value = Screen.Home
            1 -> _currentScreen.value = Screen.RecipesList
            2 -> _currentScreen.value = Screen.InventoryList
            3 -> _currentScreen.value = Screen.SuppliersList
            4 -> _currentScreen.value = Screen.QuickActions
        }
    }

    fun setTimeFrame(timeFrame: String) {
        _selectedTimeFrame.value = timeFrame
    }

    // Actions
    fun toggleFavoriteRecipe(id: Long) {
        viewModelScope.launch {
            repository.toggleFavoriteRecipe(id)
        }
    }

    fun updateRecipeProfitMargin(id: Long, marginPercent: Double) {
        viewModelScope.launch {
            repository.updateProfitMargin(id, marginPercent)
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            val newStatus = if (task.status == "Completed") "Pending" else "Completed"
            repository.updateTaskStatus(task.id, newStatus)
        }
    }

    fun addTask(
        title: String,
        orderRef: String,
        dueTime: String,
        priority: String,
        dueDate: String = "",
        dayOfWeek: String = "Mon"
    ) {
        viewModelScope.launch {
            repository.insertTask(
                TaskEntity(
                    userId = _currentUserId.value,
                    title = title,
                    orderRef = orderRef,
                    dueTime = dueTime,
                    priority = priority,
                    status = "Pending",
                    dueDate = dueDate,
                    dayOfWeek = dayOfWeek
                )
            )
        }
    }

    fun toggleInventoryAlert(id: Long) {
        viewModelScope.launch {
            repository.toggleInventoryAlert(id)
        }
    }

    fun updateStockPrice(id: Long, unitPrice: Double, currentStock: Double, minStock: Double) {
        viewModelScope.launch {
            repository.updateStockPrice(id, unitPrice, currentStock, minStock)
        }
    }

    fun addNewStockItem(name: String, unitPrice: Double, currentStock: Double, minStock: Double, unit: String) {
        viewModelScope.launch {
            val isLow = currentStock <= minStock
            repository.insertInventoryItem(
                InventoryItemEntity(
                    userId = _currentUserId.value,
                    name = name,
                    unitPrice = unitPrice,
                    currentStock = currentStock,
                    minStock = minStock,
                    unit = unit,
                    isLowStock = isLow,
                    alertEnabled = true
                )
            )
        }
    }

    fun deleteStockItem(id: Long) {
        viewModelScope.launch {
            repository.deleteInventoryItem(id)
        }
    }

    fun addNewStockItemDetailed(
        name: String,
        packagePrice: Double,
        gramsPerUnit: Double,
        currentStock: Double,
        minStock: Double,
        unit: String,
        category: String
    ) {
        viewModelScope.launch {
            val calcUnitPrice = if (gramsPerUnit > 0) packagePrice / gramsPerUnit else packagePrice
            val isLow = currentStock <= minStock
            repository.insertInventoryItem(
                InventoryItemEntity(
                    userId = _currentUserId.value,
                    name = name,
                    unitPrice = calcUnitPrice,
                    packagePrice = packagePrice,
                    gramsPerUnit = gramsPerUnit,
                    currentStock = currentStock,
                    minStock = minStock,
                    unit = unit,
                    category = category,
                    isLowStock = isLow,
                    alertEnabled = true
                )
            )
        }
    }

    fun updateStockItemDetailed(
        id: Long,
        packagePrice: Double,
        gramsPerUnit: Double,
        currentStock: Double,
        minStock: Double
    ) {
        viewModelScope.launch {
            val calcUnitPrice = if (gramsPerUnit > 0) packagePrice / gramsPerUnit else packagePrice
            repository.updateStockItemFull(id, calcUnitPrice, packagePrice, gramsPerUnit, currentStock, minStock)
        }
    }

    fun addIngredientToRecipe(
        recipeId: Long,
        name: String,
        quantity: Double,
        unit: String,
        cost: Double
    ) {
        viewModelScope.launch {
            repository.insertIngredient(
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    cost = cost
                )
            )
        }
    }

    fun deleteIngredient(id: Long) {
        viewModelScope.launch {
            repository.deleteIngredient(id)
        }
    }

    fun deleteRecipe(id: Long) {
        viewModelScope.launch {
            repository.deleteRecipe(id)
            if (_selectedRecipeId.value == id) {
                _selectedRecipeId.value = null
            }
        }
    }

    fun updateBusinessBranding(
        logoUri: String,
        bakeryName: String,
        phone: String,
        email: String,
        address: String,
        bankName: String,
        accountNumber: String,
        branchCode: String,
        vatNumber: String,
        defaultHourlyRate: Double = 120.0,
        bakeryId: String = ""
    ) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfileEntity()
            val finalBakeryId = bakeryId.ifBlank {
                current.bakeryId.ifBlank {
                    generateBakeryId(_currentUserId.value, bakeryName)
                }
            }
            val updated = current.copy(
                bakeryId = finalBakeryId,
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
            repository.saveUserProfile(updated)
            try {
                WebSyncService.syncProfile(finalBakeryId, updated)
            } catch (_: Throwable) {}
        }
    }

    fun updateRecipePricing(
        id: Long,
        labour: Double,
        overheads: Double,
        packaging: Double,
        utilities: Double,
        profitMargin: Double,
        sellingPrice: Double
    ) {
        viewModelScope.launch {
            repository.updateRecipePricing(id, labour, overheads, packaging, utilities, profitMargin, sellingPrice)
        }
    }

    fun updateRecipePhoto(id: Long, photoUri: String) {
        viewModelScope.launch {
            repository.updateRecipePhoto(id, photoUri)
        }
    }

    fun updateIngredientCost(id: Long, cost: Double, quantity: Double) {
        viewModelScope.launch {
            repository.updateIngredientCost(id, cost, quantity)
        }
    }

    fun toggleFavoriteSupplier(id: Long) {
        viewModelScope.launch {
            repository.toggleFavoriteSupplier(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun saveNewRecipe(
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
        instructions: String = "",
        photoUri: String = ""
    ) {
        viewModelScope.launch {
            val uid = _currentUserId.value
            val currentProfile = userProfile.value
            val bId = currentProfile?.bakeryId?.ifBlank {
                generateBakeryId(uid, currentProfile.bakeryName)
            } ?: generateBakeryId(uid, "artisan")

            val newRecipe = RecipeEntity(
                userId = uid,
                bakeryId = bId,
                name = name,
                category = category,
                description = description,
                servings = servings,
                batchSize = batchSize,
                difficulty = "Medium",
                rating = 5.0,
                reviewCount = 1,
                imageResName = "cupcake",
                labourCost = labourCost,
                overheadsCost = overheadsCost,
                packagingCost = packagingCost,
                utilitiesCost = utilitiesCost,
                profitMarginPercent = profitMargin,
                instructions = instructions,
                photoUri = photoUri
            )
            val recipeId = repository.insertRecipe(newRecipe)
            val updatedIngredients = ingredients.map { it.copy(recipeId = recipeId, userId = uid) }
            repository.saveIngredients(recipeId, updatedIngredients)

            // Auto-sync extracted ingredients into inventory pantry
            for (ing in ingredients) {
                if (ing.name.isNotBlank()) {
                    repository.insertInventoryItem(
                        InventoryItemEntity(
                            userId = uid,
                            name = ing.name,
                            currentStock = maxOf(ing.quantity * 2, 500.0),
                            minStock = maxOf(ing.quantity, 200.0),
                            unit = ing.unit.ifBlank { "g" },
                            unitPrice = if (ing.quantity > 0) ing.cost / ing.quantity else 0.05,
                            packagePrice = maxOf(ing.cost * 1.5, 25.0),
                            gramsPerUnit = 1000.0,
                            category = "Baking Staples",
                            isLowStock = false
                        )
                    )
                }
            }

            // Sync newly created recipe to web backend
            try {
                WebSyncService.syncRecipe(bId, newRecipe.copy(id = recipeId), updatedIngredients)
            } catch (_: Throwable) {}

            navigateTo(Screen.RecipeDetail(recipeId))
        }
    }

    fun saveScannedIngredient(
        name: String,
        currentStock: Double,
        minStock: Double,
        unit: String,
        packagePrice: Double,
        gramsPerUnit: Double,
        category: String,
        barcode: String = ""
    ) {
        val calculatedUnitPrice = if (gramsPerUnit > 0) packagePrice / gramsPerUnit else packagePrice
        val effectiveUserId = if (_currentUserId.value > 0) _currentUserId.value else 1L
        viewModelScope.launch {
            repository.insertInventoryItem(
                InventoryItemEntity(
                    userId = effectiveUserId,
                    name = name,
                    currentStock = currentStock,
                    minStock = minStock,
                    unit = unit,
                    unitPrice = calculatedUnitPrice,
                    packagePrice = packagePrice,
                    gramsPerUnit = gramsPerUnit,
                    category = category,
                    barcode = barcode,
                    isLowStock = currentStock <= minStock
                )
            )
        }
    }

    fun saveNewSupplier(
        name: String,
        category: String,
        phone: String,
        email: String,
        website: String
    ) {
        viewModelScope.launch {
            val id = repository.insertSupplier(
                SupplierEntity(
                    name = name,
                    categories = category,
                    rating = 4.5,
                    reviewCount = 10,
                    openingHours = "Open • Closes 17:00",
                    phone = phone,
                    email = email,
                    website = website,
                    about = "Bakery supplier providing wholesale baking items and equipment.",
                    isFavorite = true,
                    isMySupplier = true
                )
            )
            navigateTo(Screen.SupplierDetail(id))
        }
    }

    // Paywall Controls
    fun openPaywall(featureName: String, description: String) {
        _paywallFeatureName.value = featureName
        _paywallDescription.value = description
        _showPaywall.value = true
    }

    fun closePaywall() {
        _showPaywall.value = false
    }

    fun triggerPaywall(featureName: String, description: String) = openPaywall(featureName, description)
    fun dismissPaywall() = closePaywall()

    // Baking Supply Store Locator
    fun toggleFavoriteBakingSupplyStore(id: Long) {
        viewModelScope.launch {
            repository.toggleFavoriteStore(id)
        }
    }

    fun addCustomBakingSupplyStore(
        name: String,
        category: String,
        address: String,
        city: String,
        phone: String,
        website: String,
        specialties: String,
        inStockHighlights: String = "",
        distanceKm: Double = 3.2
    ) {
        viewModelScope.launch {
            val id = repository.insertBakingSupplyStore(
                BakingSupplyStoreEntity(
                    name = name,
                    category = category,
                    address = address,
                    city = city,
                    distanceKm = distanceKm,
                    rating = 5.0,
                    reviewCount = 1,
                    openingHours = "Open • Closes 17:00",
                    phone = phone,
                    website = website,
                    specialties = specialties,
                    inStockHighlights = inStockHighlights,
                    lat = -26.1450 + (Math.random() - 0.5) * 0.04,
                    lng = 28.0350 + (Math.random() - 0.5) * 0.04,
                    hasDelivery = true,
                    hasPickup = true,
                    isFavorite = true,
                    isCustomAdded = true
                )
            )
            selectBakingSupplyStore(id)
        }
    }

    fun deleteBakingSupplyStore(id: Long) {
        viewModelScope.launch {
            repository.deleteBakingSupplyStore(id)
        }
    }

    fun upgradeToPremium(plan: String = "Pro Monthly") {
        viewModelScope.launch {
            _isPremiumUser.value = true
            repository.updateSubscriptionStatus(true, plan)
            _showPaywall.value = false
        }
    }

    fun cancelPremium() {
        viewModelScope.launch {
            _isPremiumUser.value = false
            repository.updateSubscriptionStatus(false, "Free")
        }
    }

    fun togglePremium() {
        if (_isPremiumUser.value) {
            cancelPremium()
        } else {
            upgradeToPremium("Pro Monthly")
        }
    }

    fun updateUserProfile(
        fullName: String,
        bakeryName: String,
        specialty: String,
        phone: String,
        city: String,
        operatingModel: String,
        currency: String,
        email: String,
        bakeryId: String = ""
    ) {
        viewModelScope.launch {
            val currentPlan = if (_isPremiumUser.value) "Pro Monthly" else "Free"
            val uid = _currentUserId.value
            val existing = userProfile.value
            val finalBakeryId = bakeryId.ifBlank {
                existing?.bakeryId?.ifBlank {
                    generateBakeryId(uid, bakeryName)
                } ?: generateBakeryId(uid, bakeryName)
            }
            val profile = (existing ?: UserProfileEntity()).copy(
                id = 1,
                userId = uid,
                bakeryId = finalBakeryId,
                fullName = fullName,
                bakeryName = bakeryName,
                specialty = specialty,
                phone = phone,
                city = city,
                operatingModel = operatingModel,
                currency = currency,
                email = email,
                isPremium = _isPremiumUser.value,
                subscriptionPlan = currentPlan
            )
            repository.saveUserProfile(profile)
            try {
                WebSyncService.syncProfile(finalBakeryId, profile)
            } catch (_: Throwable) {}
        }
    }

    // Products & Services (Central Single Source of Truth)
    fun saveProductService(
        name: String,
        description: String = "",
        category: String = "Cakes",
        imageUrl: String = "",
        sku: String = "",
        costPrice: Double = 0.0,
        sellingPrice: Double = 0.0,
        unit: String = "Each",
        isActive: Boolean = true,
        isService: Boolean = false,
        linkedRecipeId: Long? = null,
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val product = ProductServiceEntity(
                userId = _currentUserId.value,
                name = name.trim(),
                description = description.trim(),
                category = category.trim().ifBlank { "Cakes" },
                imageUrl = imageUrl,
                sku = sku.trim(),
                costPrice = costPrice,
                sellingPrice = sellingPrice,
                unit = unit.ifBlank { "Each" },
                isActive = isActive,
                isService = isService,
                linkedRecipeId = linkedRecipeId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.insertProduct(product)
            val dateStr = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            repository.insertPriceHistory(
                ProductPriceHistoryEntity(
                    productId = newId,
                    userId = _currentUserId.value,
                    previousPrice = 0.0,
                    newPrice = sellingPrice,
                    dateChanged = dateStr,
                    notes = "Initial price set"
                )
            )
            onSuccess(newId)
        }
    }

    fun updateProductService(
        existing: ProductServiceEntity,
        name: String,
        description: String,
        category: String,
        imageUrl: String,
        sku: String,
        costPrice: Double,
        sellingPrice: Double,
        unit: String,
        isActive: Boolean,
        isService: Boolean,
        linkedRecipeId: Long?,
        changeNotes: String = ""
    ) {
        viewModelScope.launch {
            val oldPrice = existing.sellingPrice
            val priceChanged = kotlin.math.abs(oldPrice - sellingPrice) > 0.001
            val updated = existing.copy(
                name = name.trim(),
                description = description.trim(),
                category = category.trim().ifBlank { "Cakes" },
                imageUrl = imageUrl,
                sku = sku.trim(),
                costPrice = costPrice,
                sellingPrice = sellingPrice,
                unit = unit.ifBlank { "Each" },
                isActive = isActive,
                isService = isService,
                linkedRecipeId = linkedRecipeId,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateProduct(updated)

            if (priceChanged) {
                val dateStr = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                repository.insertPriceHistory(
                    ProductPriceHistoryEntity(
                        productId = existing.id,
                        userId = _currentUserId.value,
                        previousPrice = oldPrice,
                        newPrice = sellingPrice,
                        dateChanged = dateStr,
                        notes = changeNotes.ifBlank { "Master price updated" }
                    )
                )
            }
        }
    }

    fun updateProductService(updated: ProductServiceEntity) {
        viewModelScope.launch {
            repository.updateProduct(updated.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleProductServiceActive(id: Long, active: Boolean) {
        viewModelScope.launch {
            val existing = allProductsServices.value.find { it.id == id } ?: return@launch
            repository.updateProduct(existing.copy(isActive = active, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteProductService(id: Long) {
        viewModelScope.launch {
            repository.deleteProduct(id)
        }
    }

    fun getPriceHistory(productId: Long): Flow<List<ProductPriceHistoryEntity>> =
        repository.getPriceHistory(productId)

    // Invoices
    fun createInvoice(
        clientName: String,
        clientPhone: String,
        orderDescription: String,
        amount: Double,
        dueDate: String,
        status: String = "Pending",
        subtotal: Double = amount,
        discountAmount: Double = 0.0,
        taxRatePercent: Double = 0.0,
        taxAmount: Double = 0.0,
        items: List<LineItem> = emptyList(),
        totalCost: Double = 0.0
    ) {
        viewModelScope.launch {
            val count = (allInvoices.value.size + 1008)
            val invNumber = "INV-2024-$count"
            val lineItemsJson = LineItemJsonUtil.toJson(items)
            val finalDescription = if (orderDescription.isNotBlank()) {
                orderDescription
            } else if (items.isNotEmpty()) {
                items.joinToString(", ") { "${it.quantity.let { q -> if (q % 1.0 == 0.0) q.toInt().toString() else q.toString() }}x ${it.itemName}" }
            } else {
                "Bakery Order"
            }
            val invId = repository.insertInvoice(
                InvoiceEntity(
                    userId = _currentUserId.value,
                    invoiceNumber = invNumber,
                    clientName = clientName,
                    clientPhone = clientPhone,
                    orderDescription = finalDescription,
                    issueDate = "Today",
                    dueDate = dueDate,
                    amount = amount,
                    status = status,
                    subtotal = subtotal,
                    discountAmount = discountAmount,
                    taxRatePercent = taxRatePercent,
                    taxAmount = taxAmount,
                    lineItemsJson = lineItemsJson,
                    totalCost = totalCost
                )
            )
            if (items.isNotEmpty()) {
                val dbEntities = items.map {
                    DocumentLineItemEntity(
                        documentType = "INVOICE",
                        documentId = invId,
                        userId = _currentUserId.value,
                        productId = it.productId,
                        itemName = it.itemName,
                        description = it.description,
                        quantity = it.quantity,
                        unit = it.unit,
                        unitPrice = it.unitPrice,
                        discount = it.discount,
                        costPrice = it.costPrice,
                        lineTotal = it.lineTotal
                    )
                }
                repository.insertLineItems(dbEntities)
            }
        }
    }

    fun updateInvoiceStatus(id: Long, status: String) {
        viewModelScope.launch {
            repository.updateInvoiceStatus(id, status)
        }
    }

    fun deleteInvoice(id: Long) {
        viewModelScope.launch {
            repository.deleteInvoice(id)
            repository.deleteLineItems("INVOICE", id)
        }
    }

    // Quotes & Estimates
    fun createQuote(
        clientName: String,
        clientPhone: String,
        eventType: String,
        eventDate: String,
        recipeOrItemName: String,
        estimatedCost: Double,
        profitMarginPercent: Double,
        quotedPrice: Double,
        status: String = "Sent",
        docType: String = "Quote",
        subtotal: Double = quotedPrice,
        discountAmount: Double = 0.0,
        taxRatePercent: Double = 0.0,
        taxAmount: Double = 0.0,
        items: List<LineItem> = emptyList(),
        totalCost: Double = estimatedCost
    ) {
        viewModelScope.launch {
            val count = (allQuotes.value.size + 1008)
            val prefix = if (docType.equals("Estimate", ignoreCase = true)) "EST" else "QT"
            val quoteNumber = "$prefix-2024-$count"
            val lineItemsJson = LineItemJsonUtil.toJson(items)
            val finalItemName = if (recipeOrItemName.isNotBlank()) {
                recipeOrItemName
            } else if (items.isNotEmpty()) {
                items.joinToString(", ") { "${it.quantity.let { q -> if (q % 1.0 == 0.0) q.toInt().toString() else q.toString() }}x ${it.itemName}" }
            } else {
                "Custom Cake & Treats"
            }
            val quoteId = repository.insertQuote(
                QuoteEntity(
                    userId = _currentUserId.value,
                    quoteNumber = quoteNumber,
                    clientName = clientName,
                    clientPhone = clientPhone,
                    eventType = eventType,
                    eventDate = eventDate,
                    recipeOrItemName = finalItemName,
                    estimatedCost = estimatedCost,
                    profitMarginPercent = profitMarginPercent,
                    quotedPrice = quotedPrice,
                    status = status,
                    docType = docType,
                    subtotal = subtotal,
                    discountAmount = discountAmount,
                    taxRatePercent = taxRatePercent,
                    taxAmount = taxAmount,
                    lineItemsJson = lineItemsJson,
                    totalCost = totalCost
                )
            )
            if (items.isNotEmpty()) {
                val dbEntities = items.map {
                    DocumentLineItemEntity(
                        documentType = docType.uppercase(),
                        documentId = quoteId,
                        userId = _currentUserId.value,
                        productId = it.productId,
                        itemName = it.itemName,
                        description = it.description,
                        quantity = it.quantity,
                        unit = it.unit,
                        unitPrice = it.unitPrice,
                        discount = it.discount,
                        costPrice = it.costPrice,
                        lineTotal = it.lineTotal
                    )
                }
                repository.insertLineItems(dbEntities)
            }
        }
    }

    fun updateQuoteStatus(id: Long, status: String) {
        viewModelScope.launch {
            repository.updateQuoteStatus(id, status)
        }
    }

    fun convertQuoteToInvoice(quote: QuoteEntity) {
        viewModelScope.launch {
            repository.updateQuoteStatus(quote.id, "Accepted")
            val invCount = (allInvoices.value.size + 1008)
            val invId = repository.insertInvoice(
                InvoiceEntity(
                    userId = _currentUserId.value,
                    invoiceNumber = "INV-2024-$invCount",
                    clientName = quote.clientName,
                    clientPhone = quote.clientPhone,
                    orderDescription = "${quote.eventType}: ${quote.recipeOrItemName}",
                    issueDate = "Today",
                    dueDate = quote.eventDate,
                    amount = quote.quotedPrice,
                    status = "Pending",
                    subtotal = quote.subtotal,
                    discountAmount = quote.discountAmount,
                    taxRatePercent = quote.taxRatePercent,
                    taxAmount = quote.taxAmount,
                    lineItemsJson = quote.lineItemsJson,
                    totalCost = quote.totalCost
                )
            )
            val items = quote.items
            if (items.isNotEmpty()) {
                val dbEntities = items.map {
                    DocumentLineItemEntity(
                        documentType = "INVOICE",
                        documentId = invId,
                        userId = _currentUserId.value,
                        productId = it.productId,
                        itemName = it.itemName,
                        description = it.description,
                        quantity = it.quantity,
                        unit = it.unit,
                        unitPrice = it.unitPrice,
                        discount = it.discount,
                        costPrice = it.costPrice,
                        lineTotal = it.lineTotal
                    )
                }
                repository.insertLineItems(dbEntities)
            }
        }
    }

    fun deleteQuote(id: Long) {
        viewModelScope.launch {
            repository.deleteQuote(id)
            repository.deleteLineItems("QUOTE", id)
            repository.deleteLineItems("ESTIMATE", id)
        }
    }

    // Customers
    fun addCustomer(
        name: String,
        phone: String = "",
        email: String = "",
        address: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            val uid = _currentUserId.value
            val currentProfile = userProfile.value
            val bId = currentProfile?.bakeryId?.ifBlank {
                generateBakeryId(uid, currentProfile.bakeryName)
            } ?: generateBakeryId(uid, "artisan")

            val customer = CustomerEntity(
                userId = uid,
                bakeryId = bId,
                name = name.trim(),
                phone = phone.trim(),
                email = email.trim(),
                address = address.trim(),
                notes = notes.trim()
            )
            val newId = repository.insertCustomer(customer)
            val savedCustomer = customer.copy(id = newId)

            // Real-time synchronization with website backend
            try {
                WebSyncService.syncCustomer(bId, savedCustomer)
            } catch (_: Throwable) {}
        }
    }

    fun updateCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            val currentProfile = userProfile.value
            val bId = customer.bakeryId.ifBlank {
                currentProfile?.bakeryId?.ifBlank {
                    generateBakeryId(_currentUserId.value, currentProfile.bakeryName)
                } ?: generateBakeryId(_currentUserId.value, "artisan")
            }
            val updated = customer.copy(userId = _currentUserId.value, bakeryId = bId)
            repository.updateCustomer(updated)
            try {
                WebSyncService.syncCustomer(bId, updated)
            } catch (_: Throwable) {}
        }
    }

    fun deleteCustomer(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomer(id)
        }
    }

    // Notifications
    fun markNotificationAsRead(id: Long) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    // User Authentication & Account Isolation
    fun loginUser(emailOrPhone: String, branch: String = "Main Flagship", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val cleanInput = emailOrPhone.trim().lowercase()
            val user = repository.getUserByEmail(cleanInput)
            if (user != null) {
                _currentUserId.value = user.id
                _isUserLoggedIn.value = true
                authPrefs.edit().putLong("active_user_id", user.id).apply()
                val userBakeryId = user.bakeryId.ifBlank { generateBakeryId(user.id, user.bakeryName) }
                if (user.bakeryId.isBlank()) {
                    repository.updateUserAccount(user.copy(bakeryId = userBakeryId))
                }
                updateUserProfile(
                    fullName = user.fullName,
                    bakeryName = user.bakeryName,
                    specialty = user.specialty,
                    phone = user.phone,
                    city = user.city,
                    operatingModel = user.operatingModel,
                    currency = user.currency,
                    email = user.email,
                    bakeryId = userBakeryId
                )
                repository.recordLoginLog(
                    userId = user.id,
                    email = user.email,
                    bakeryName = user.bakeryName,
                    action = "LOGIN",
                    branch = branch,
                    notes = "Signed in successfully"
                )
                onResult(true, "Welcome back, ${user.firstName.ifBlank { "Baker" }}!")
            } else {
                val firstName = if (cleanInput.contains("@")) {
                    cleanInput.substringBefore("@").replaceFirstChar { it.uppercase() }
                } else {
                    cleanInput.ifBlank { "Baker" }
                }
                val bakeryName = "$firstName's Bakery"
                val initialBakeryId = generateBakeryId(0, bakeryName)
                val newId = repository.insertUserAccount(
                    UserAccountEntity(
                        bakeryId = initialBakeryId,
                        firstName = firstName,
                        surname = "",
                        email = if (cleanInput.contains("@")) cleanInput else "$cleanInput@bakery.com",
                        bakeryName = bakeryName,
                        phone = if (cleanInput.contains("@")) "" else cleanInput,
                        city = "Cape Town",
                        operatingModel = "Home Kitchen",
                        currency = "ZAR (R)",
                        specialty = "Cakes & Pastries"
                    )
                )
                val finalBakeryId = generateBakeryId(newId, bakeryName)
                repository.updateUserAccount(
                    UserAccountEntity(
                        id = newId,
                        bakeryId = finalBakeryId,
                        firstName = firstName,
                        surname = "",
                        email = if (cleanInput.contains("@")) cleanInput else "$cleanInput@bakery.com",
                        bakeryName = bakeryName,
                        phone = if (cleanInput.contains("@")) "" else cleanInput,
                        city = "Cape Town",
                        operatingModel = "Home Kitchen",
                        currency = "ZAR (R)",
                        specialty = "Cakes & Pastries"
                    )
                )
                _currentUserId.value = newId
                _isUserLoggedIn.value = true
                authPrefs.edit().putLong("active_user_id", newId).apply()
                val userEmail = if (cleanInput.contains("@")) cleanInput else "$cleanInput@bakery.com"
                updateUserProfile(
                    fullName = firstName,
                    bakeryName = bakeryName,
                    specialty = "Cakes & Pastries",
                    phone = if (cleanInput.contains("@")) "" else cleanInput,
                    city = "Cape Town",
                    operatingModel = "Home Kitchen",
                    currency = "ZAR (R)",
                    email = userEmail,
                    bakeryId = finalBakeryId
                )
                repository.recordLoginLog(
                    userId = newId,
                    email = userEmail,
                    bakeryName = bakeryName,
                    action = "REGISTER",
                    branch = branch,
                    notes = "Account auto-provisioned on login"
                )
                onResult(true, "Welcome to BatchBoss, $firstName!")
            }
        }
    }

    fun logoutUser() {
        val uid = _currentUserId.value
        if (uid != null && uid > 0) {
            viewModelScope.launch {
                val user = repository.getUserByIdOnce(uid)
                if (user != null) {
                    repository.recordLoginLog(
                        userId = uid,
                        email = user.email,
                        bakeryName = user.bakeryName,
                        action = "LOGOUT",
                        notes = "User logged out"
                    )
                }
            }
        }
        authPrefs.edit().remove("active_user_id").apply()
        _isUserLoggedIn.value = false
        _currentScreen.value = Screen.Login
    }

    fun createAccountWithDetails(
        firstName: String,
        surname: String,
        email: String,
        password: String,
        bakeryName: String,
        phone: String,
        city: String,
        operatingModel: String,
        currency: String,
        specialty: String,
        onComplete: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val cleanEmail = email.trim().lowercase()
            val bName = bakeryName.trim().ifBlank { "$firstName's Bakery" }
            val initialBakeryId = generateBakeryId(0, bName)
            val user = UserAccountEntity(
                bakeryId = initialBakeryId,
                firstName = firstName.trim(),
                surname = surname.trim(),
                email = cleanEmail,
                passwordHash = password,
                bakeryName = bName,
                phone = phone.trim(),
                city = city.trim(),
                operatingModel = operatingModel,
                currency = currency,
                specialty = specialty
            )
            val newUserId = repository.insertUserAccount(user)
            val finalBakeryId = generateBakeryId(newUserId, bName)
            repository.updateUserAccount(user.copy(id = newUserId, bakeryId = finalBakeryId))

            _currentUserId.value = newUserId
            _isUserLoggedIn.value = true
            authPrefs.edit().putLong("active_user_id", newUserId).apply()

            updateUserProfile(
                fullName = "$firstName $surname".trim(),
                bakeryName = bName,
                specialty = specialty,
                phone = phone.trim(),
                city = city.trim(),
                operatingModel = operatingModel,
                currency = currency,
                email = cleanEmail,
                bakeryId = finalBakeryId
            )

            repository.recordLoginLog(
                userId = newUserId,
                email = cleanEmail,
                bakeryName = bakeryName.trim(),
                action = "REGISTER",
                notes = "New bakery account created"
            )

            // Insert official Welcome Email notification from BatchBoss
            repository.insertNotification(
                NotificationEntity(
                    userId = newUserId,
                    title = "Welcome Email from BatchBoss 🧁",
                    message = "Hi $firstName! Welcome to BatchBoss. We have dispatched your official welcome letter & starter guide to $cleanEmail. Tap to open and read.",
                    type = "System",
                    timeLabel = "Just now",
                    isUnread = true,
                    dateGroup = "Today"
                )
            )

            onComplete(newUserId)
        }
    }

    // Google Play Account & Data Deletion
    fun submitDataDeletionRequest(reason: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val uid = _currentUserId.value ?: 0L
            val user = if (uid > 0) repository.getUserByIdOnce(uid) else null
            val email = user?.email ?: "user@batchboss.app"
            val bakery = user?.bakeryName ?: "Bakery"
            repository.recordDeletionRequest(uid, email, bakery, reason)
            repository.recordLoginLog(
                userId = uid,
                email = email,
                bakeryName = bakery,
                action = "DELETION_REQUESTED",
                notes = reason
            )
            onComplete()
        }
    }

    fun instantDeleteCurrentAccountAndData(onComplete: () -> Unit) {
        viewModelScope.launch {
            val uid = _currentUserId.value
            if (uid != null && uid > 0) {
                val user = repository.getUserByIdOnce(uid)
                if (user != null) {
                    repository.recordLoginLog(
                        userId = uid,
                        email = user.email,
                        bakeryName = user.bakeryName,
                        action = "ACCOUNT_DELETED",
                        notes = "User triggered instant account & data wipe"
                    )
                }
                repository.deleteUserAndAllData(uid)
            }
            authPrefs.edit().remove("active_user_id").apply()
            _currentUserId.value = 1L
            _isUserLoggedIn.value = false
            _currentScreen.value = Screen.Login
            onComplete()
        }
    }

    // Master Personal Backend & Admin Console Operations
    fun adminDeleteUserData(targetUserId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserByIdOnce(targetUserId)
            if (user != null) {
                repository.recordLoginLog(
                    userId = targetUserId,
                    email = user.email,
                    bakeryName = user.bakeryName,
                    action = "ADMIN_DELETED_USER",
                    notes = "Admin executed full user & data purge"
                )
            }
            repository.deleteUserAndAllData(targetUserId)
            if (_currentUserId.value == targetUserId) {
                authPrefs.edit().remove("active_user_id").apply()
                _currentUserId.value = 1L
                _isUserLoggedIn.value = false
            }
            onComplete()
        }
    }

    fun adminProcessDeletionRequest(requestId: Long, targetUserId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (targetUserId > 0) {
                repository.deleteUserAndAllData(targetUserId)
            }
            repository.updateDeletionRequestStatus(requestId, "COMPLETED")
            onComplete()
        }
    }

    fun adminRejectDeletionRequest(requestId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.updateDeletionRequestStatus(requestId, "REJECTED")
            onComplete()
        }
    }

    fun adminClearLoginLogs() {
        viewModelScope.launch {
            repository.clearAllLoginLogs()
        }
    }

    fun adminFactoryResetDatabase(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.factoryResetDatabase()
            authPrefs.edit().clear().apply()
            _currentUserId.value = 1L
            _isUserLoggedIn.value = false
            _currentScreen.value = Screen.Login
            onComplete()
        }
    }

    fun adminCreateUserAccount(
        firstName: String,
        surname: String,
        email: String,
        bakeryName: String,
        phone: String,
        city: String,
        operatingModel: String,
        currency: String,
        isPro: Boolean,
        onComplete: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.insertUserAccount(
                UserAccountEntity(
                    firstName = firstName.trim(),
                    surname = surname.trim(),
                    email = email.trim().lowercase(),
                    bakeryName = bakeryName.trim(),
                    phone = phone.trim(),
                    city = city.trim(),
                    operatingModel = operatingModel,
                    currency = currency,
                    isPremium = isPro,
                    specialty = "Cakes & Bakes"
                )
            )
            repository.recordLoginLog(
                userId = id,
                email = email.trim().lowercase(),
                bakeryName = bakeryName.trim(),
                action = "ADMIN_CREATED",
                notes = "Provisioned via Master Backend"
            )
            onComplete(id)
        }
    }

    fun adminToggleUserPro(userId: Long, isPro: Boolean) {
        viewModelScope.launch {
            val user = repository.getUserByIdOnce(userId)
            if (user != null) {
                repository.updateUserAccount(user.copy(isPremium = isPro))
                if (_currentUserId.value == userId) {
                    _isPremiumUser.value = isPro
                }
            }
        }
    }

    fun adminPurgeUserInvoices(userId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.purgeAllInvoices(userId)
            onComplete()
        }
    }

    fun adminPurgeUserQuotes(userId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.purgeAllQuotes(userId)
            onComplete()
        }
    }

    fun restoreCloudData(
        recipes: List<com.example.data.local.RecipeEntity>,
        ingredients: List<com.example.data.local.RecipeIngredientEntity>,
        inventory: List<com.example.data.local.InventoryItemEntity>,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val uid = _currentUserId.value
            for (r in recipes) {
                val newRecipeId = repository.insertRecipe(r.copy(id = 0, userId = uid))
                val matchingIngs = ingredients.filter { it.recipeId == r.id }
                for (ing in matchingIngs) {
                    repository.insertIngredient(ing.copy(id = 0, recipeId = newRecipeId, userId = uid))
                }
            }
            for (item in inventory) {
                repository.insertInventoryItem(item.copy(id = 0, userId = uid))
            }
            onComplete()
        }
    }

    fun syncWithWebBackend(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val uid = _currentUserId.value
            val profile = userProfile.value ?: UserProfileEntity()
            val bId = profile.bakeryId.ifBlank { generateBakeryId(uid, profile.bakeryName) }

            val customersList = repository.getCustomersByUser(uid).first()
            val recipesList = repository.getRecipesByUser(uid).first()

            var syncedItems = 0
            if (WebSyncService.syncProfile(bId, profile)) syncedItems++

            for (cust in customersList) {
                if (WebSyncService.syncCustomer(bId, cust)) syncedItems++
            }

            for (rec in recipesList) {
                val ings = repository.getIngredientsForRecipeOnce(rec.id)
                if (WebSyncService.syncRecipe(bId, rec, ings)) syncedItems++
            }

            try {
                FirebaseService.backupDataToCloud(
                    recipes = recipesList,
                    ingredients = emptyList(),
                    inventory = emptyList(),
                    customers = customersList,
                    bakeryId = bId
                )
            } catch (_: Throwable) {}

            onComplete(true, "Synchronized with Website for Bakery ID: $bId ($syncedItems entities updated)")
        }
    }
}
