package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.AppRepository
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
    data object PremiumSubscription : Screen()
}

class BatchBossViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val repository = AppRepository(database)

    // Navigation stack / current screen
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Home, 1: Recipes, 2: Ingredients, 3: Suppliers, 4: More
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Dashboard time frame filter
    private val _selectedTimeFrame = MutableStateFlow("This Week")
    val selectedTimeFrame: StateFlow<String> = _selectedTimeFrame.asStateFlow()

    // Data streams
    val allRecipes: StateFlow<List<RecipeEntity>> = repository.allRecipes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInventory: StateFlow<List<InventoryItemEntity>> = repository.allInventory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockItems: StateFlow<List<InventoryItemEntity>> = repository.lowStockItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<SupplierEntity>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSpecials: StateFlow<List<SpecialDealEntity>> = repository.allSpecials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = repository.unreadNotificationsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    // Invoices & Quotes
    val allInvoices: StateFlow<List<InvoiceEntity>> = repository.allInvoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allQuotes: StateFlow<List<QuoteEntity>> = repository.allQuotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User Profile & Premium Subscription
    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            is Screen.QuickActions, is Screen.UnitConverter, is Screen.RecipeScaler -> _selectedTab.value = 4
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
        defaultHourlyRate: Double = 120.0
    ) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfileEntity()
            val updated = current.copy(
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
        ingredients: List<RecipeIngredientEntity>
    ) {
        viewModelScope.launch {
            val recipeId = repository.insertRecipe(
                RecipeEntity(
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
                    profitMarginPercent = profitMargin
                )
            )
            val updatedIngredients = ingredients.map { it.copy(recipeId = recipeId) }
            repository.saveIngredients(recipeId, updatedIngredients)
            navigateTo(Screen.RecipeDetail(recipeId))
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
        email: String
    ) {
        viewModelScope.launch {
            val currentPlan = if (_isPremiumUser.value) "Pro Monthly" else "Free"
            val profile = UserProfileEntity(
                id = 1,
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
        }
    }

    // Invoices
    fun createInvoice(
        clientName: String,
        clientPhone: String,
        orderDescription: String,
        amount: Double,
        dueDate: String,
        status: String = "Pending"
    ) {
        viewModelScope.launch {
            val count = (allInvoices.value.size + 1008)
            val invNumber = "INV-2024-$count"
            repository.insertInvoice(
                InvoiceEntity(
                    invoiceNumber = invNumber,
                    clientName = clientName,
                    clientPhone = clientPhone,
                    orderDescription = orderDescription,
                    issueDate = "Today",
                    dueDate = dueDate,
                    amount = amount,
                    status = status
                )
            )
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
        }
    }

    // Quotes
    fun createQuote(
        clientName: String,
        clientPhone: String,
        eventType: String,
        eventDate: String,
        recipeOrItemName: String,
        estimatedCost: Double,
        profitMarginPercent: Double,
        quotedPrice: Double,
        status: String = "Sent"
    ) {
        viewModelScope.launch {
            val count = (allQuotes.value.size + 1008)
            val quoteNumber = "QT-2024-$count"
            repository.insertQuote(
                QuoteEntity(
                    quoteNumber = quoteNumber,
                    clientName = clientName,
                    clientPhone = clientPhone,
                    eventType = eventType,
                    eventDate = eventDate,
                    recipeOrItemName = recipeOrItemName,
                    estimatedCost = estimatedCost,
                    profitMarginPercent = profitMarginPercent,
                    quotedPrice = quotedPrice,
                    status = status
                )
            )
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
            repository.insertInvoice(
                InvoiceEntity(
                    invoiceNumber = "INV-2024-$invCount",
                    clientName = quote.clientName,
                    clientPhone = quote.clientPhone,
                    orderDescription = "${quote.eventType} Cake: ${quote.recipeOrItemName}",
                    issueDate = "Today",
                    dueDate = quote.eventDate,
                    amount = quote.quotedPrice,
                    status = "Pending"
                )
            )
        }
    }

    fun deleteQuote(id: Long) {
        viewModelScope.launch {
            repository.deleteQuote(id)
        }
    }
}
