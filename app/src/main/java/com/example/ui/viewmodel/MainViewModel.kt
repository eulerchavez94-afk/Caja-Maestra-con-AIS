package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupCounts
import com.example.data.backup.BackupInspectionResult
import com.example.data.backup.BackupOperationResult
import com.example.data.backup.OfflineBackupManager
import com.example.data.backup.RestoreStage
import com.example.data.db.CajaMaestraDatabase
import com.example.data.model.*
import com.example.data.prefs.SecurityVault
import com.example.data.prefs.SettingsStorage
import com.example.data.repository.BusinessOperationsRepository
import com.example.data.repository.PurchaseLineInput
import com.example.data.repository.RepoPurchaseResult
import com.example.data.repository.RepoSaleResult
import com.example.util.CashShiftCalculator
import com.example.util.NotificationHelper
import com.example.util.PasswordHasher
import com.example.util.PosSearchEngine
import com.example.util.ReportExporter
import com.example.util.RoleManager
import com.example.util.ThermalPrinterUtil
import com.example.util.UserPermissions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class AppScreen {
    INVENTARIO,
    VENTAS,
    COMPRAS,
    INGRESOS_GASTOS,
    RENTAS,
    REPORTES,
    EMPLEADOS,
    CLIENTES,
    PROVEEDORES,
    VINCULAR_WEB,
    RECIBO_TICKET,
    CONFIGURACION,
    AGENDA
}

enum class DateFilterType(val label: String) {
    HOY("Hoy"),
    ULTIMA_SEMANA("Últimos 7 días"),
    ESTE_MES("Este Mes"),
    TODOS("Todo el Historial")
}

sealed class SaleCheckoutResult {
    data object Success : SaleCheckoutResult()
    data object AlreadyProcessing : SaleCheckoutResult()
    data object EmptyCart : SaleCheckoutResult()
    data object ShiftNotOpen : SaleCheckoutResult()
    data object ClientRequiredForCredit : SaleCheckoutResult()
    data class InsufficientCash(val missing: Double) : SaleCheckoutResult()
    data class InsufficientStock(
        val itemName: String,
        val available: Double,
        val requested: Double
    ) : SaleCheckoutResult()
    data object Unauthorized : SaleCheckoutResult()
}

sealed class PurchaseRegistrationResult {
    data class Success(val purchase: Purchase) : PurchaseRegistrationResult()
    data object AlreadyProcessing : PurchaseRegistrationResult()
    data object Unauthorized : PurchaseRegistrationResult()
    data class Error(val message: String) : PurchaseRegistrationResult()
}

data class SecurityUiState(
    val isInitialized: Boolean = false,
    val hasMasterPin: Boolean = false,
    val recoveryQuestion: String = "",
    val failedAttempts: Int = 0,
    val lockoutUntilMs: Long = 0L
)

sealed class InitialSecuritySetupResult {
    data class Success(val ownerRecoveryCode: String, val adminRecoveryCode: String) : InitialSecuritySetupResult()
    data class Error(val message: String) : InitialSecuritySetupResult()
}

sealed class SecurityActionResult {
    data object Success : SecurityActionResult()
    data class Error(val message: String) : SecurityActionResult()
}

sealed class RecoveryResult {
    data class Success(val newRecoveryCode: String? = null) : RecoveryResult()
    data class Error(val message: String) : RecoveryResult()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = CajaMaestraDatabase.getDatabase(application, viewModelScope)
    private val dao = db.dao()
    private val settingsStorage = SettingsStorage.getInstance(application)
    internal val backupManager = OfflineBackupManager(application, db, settingsStorage)
    private var securityVault = SecurityVault()
    private val initCompleted = CompletableDeferred<Unit>()
    @Volatile
    private var lastPersistJob: Job? = null
    private val persistMutex = Mutex()
    private val saleOperationInFlight = AtomicBoolean(false)
    private val purchaseOperationInFlight = AtomicBoolean(false)
    private val cashShiftOperationInFlight = AtomicBoolean(false)
    private val wipeOperationInFlight = AtomicBoolean(false)

    val inventoryItems = dao.getAllInventoryItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales = dao.getAllSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases = dao.getAllPurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val extraMovements = dao.getAllExtraMovements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rentalsLoans = dao.getAllRentalsLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clients = dao.getAllClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppliers = dao.getAllSuppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val employees = dao.getAllEmployees()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cashShifts = dao.getAllCashShifts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allShiftMovements = dao.getAllShiftMovements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openCashShift = dao.getOpenCashShift()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeShiftMovements: StateFlow<List<CashShiftMovement>> = openCashShift
        .flatMapLatest { shift ->
            if (shift == null) flowOf(emptyList()) else dao.getMovementsForShift(shift.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryMovements = dao.getAllInventoryMovements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Navigation & UI state
    var currentScreen = MutableStateFlow(AppScreen.INVENTARIO)
        private set

    var searchQuery = MutableStateFlow("")
        private set

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val debouncedSearchQuery = searchQuery
        .debounce(300L)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    var settings = MutableStateFlow(BusinessSettings())
        private set

    var isSettingsLoaded = MutableStateFlow(false)
        private set

    var securityState = MutableStateFlow(SecurityUiState())
        private set

    // Initial Login / Lock Screen state
    var isUserAuthenticated = MutableStateFlow(false)
        private set

    var lastLoginError = MutableStateFlow<String?>(null)
        private set

    var lastSecurityError = MutableStateFlow<String?>(null)
        private set

    // POS & Sales state
    var currentPOSInterface = MutableStateFlow(1) // 1 = Interfaz I (Grid/List Cards), 2 = Interfaz II (Table POS)
        private set

    var currentSaleType = MutableStateFlow(SaleType.VENTA)
        private set

    var cartItems = MutableStateFlow<List<SaleCartItem>>(emptyList())
        private set

    var selectedCustomer = MutableStateFlow<Client?>(null)
        private set

    var paymentMethod = MutableStateFlow("Efectivo")
        private set

    var customerPaidInput = MutableStateFlow("")
        private set

    var discountInput = MutableStateFlow("0")
        private set

    var posNotes = MutableStateFlow("")
        private set

    // Processing locks & idempotency operation keys
    var isProcessingSale = MutableStateFlow(false)
        private set

    var activeCheckoutOperationId = MutableStateFlow(UUID.randomUUID().toString())
        private set

    var isProcessingPurchase = MutableStateFlow(false)
        private set

    var isProcessingCashShift = MutableStateFlow(false)
        private set

    var lastPurchaseError = MutableStateFlow<String?>(null)
        private set

    // Last completed sale & receipt preview modal
    var lastCompletedSale = MutableStateFlow<Sale?>(null)
        private set

    var lastSaleItems = MutableStateFlow<List<SaleCartItem>>(emptyList())
        private set

    var showReceiptModal = MutableStateFlow(false)
        private set

    internal val operationsRepo = BusinessOperationsRepository(
        db = db,
        settingsProvider = { settings.value }
    )

    // Cash Shift Audit / Corte de Caja modal
    var lastClosedShift = MutableStateFlow<CashRegisterShift?>(null)
        private set

    var showShiftCutModal = MutableStateFlow(false)
        private set

    // Reports filter state
    var selectedReportTitle = MutableStateFlow<String?>(null)
        private set

    var reportDateFilter = MutableStateFlow(DateFilterType.TODOS)
        private set

    // Tutorial modal
    var showTutorialDialog = MutableStateFlow(false)
        private set

    // Offline Encrypted Backup & Restore state
    var isCloudSyncing = MutableStateFlow(false)
        private set

    var cloudSyncMessage = MutableStateFlow("Respaldo local cifrado AES-256-GCM listo (100% Offline)")
        private set

    var encryptedBackupText = MutableStateFlow("")
        private set

    var selectedBackupInspection = MutableStateFlow<BackupInspectionResult?>(null)
        private set

    var selectedBackupBytes = MutableStateFlow<ByteArray?>(null)
        private set

    var lastRestoreResult = MutableStateFlow<BackupOperationResult?>(null)
        private set

    var lastExportedFilePath = MutableStateFlow<String?>(null)
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recoveredPending = backupManager.recoverPendingInterruptedRestoreIfNeeded()
                val loaded = settingsStorage.loadState()
                securityVault = loaded.vault
                settings.value = loaded.settings
                refreshSecurityUiState()
                migrateLegacyEmployeePins()
                currentScreen.value = RoleManager.getFirstPermittedScreen(loaded.settings)
                if (recoveredPending) {
                    cloudSyncMessage.value =
                        "Se recuperó automáticamente el respaldo preventivo tras una restauración interrumpida."
                }
            } finally {
                isSettingsLoaded.value = true
                initCompleted.complete(Unit)
            }
        }

        // Check for low stock on startup and notify
        viewModelScope.launch(Dispatchers.IO) {
            inventoryItems.collect { items ->
                val lowCount = items.count { !it.isWaste && it.stock <= it.minStock && it.type == ItemType.PRODUCTO }
                if (lowCount > 0) {
                    NotificationHelper.showCriticalAlert(
                        getApplication(),
                        101,
                        "¡Alerta de Inventario!",
                        "Tiene $lowCount artículo(s) con existencias bajas o agotadas."
                    )
                }
            }
        }
    }

    suspend fun awaitInitialized() {
        initCompleted.await()
    }

    suspend fun awaitPersistence() {
        lastPersistJob?.join()
    }

    private fun refreshSecurityUiState() {
        securityState.value = SecurityUiState(
            isInitialized = securityVault.isInitialized,
            hasMasterPin = securityVault.masterPinHash.isNotBlank(),
            recoveryQuestion = securityVault.recoveryQuestion,
            failedAttempts = securityVault.failedAttempts,
            lockoutUntilMs = securityVault.lockoutUntilMs
        )
    }

    private fun launchSerializedPersist(block: suspend () -> Unit) {
        val previousJob = lastPersistJob
        lastPersistJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { previousJob?.join() }
            persistMutex.withLock {
                block()
            }
        }
    }

    private fun persistSettingsAsync(updatedSettings: BusinessSettings) {
        launchSerializedPersist {
            settingsStorage.saveBusinessSettings(updatedSettings)
        }
    }

    private fun persistVaultAsync(updatedVault: SecurityVault) {
        launchSerializedPersist {
            settingsStorage.saveSecurityVault(updatedVault)
        }
    }

    private fun persistAllAsync(updatedSettings: BusinessSettings, updatedVault: SecurityVault) {
        launchSerializedPersist {
            settingsStorage.saveAll(updatedSettings, updatedVault)
        }
    }

    private suspend fun migrateLegacyEmployeePins() {
        val all = dao.getAllEmployeesSync()
        for (emp in all) {
            if (emp.pin.isNotBlank() && !PasswordHasher.isHashed(emp.pin)) {
                val migratedPin = if (PasswordHasher.isLegacyDefaultCredential(emp.pin)) {
                    ""
                } else {
                    PasswordHasher.hash(emp.pin.trim())
                }
                dao.updateEmployee(emp.copy(pin = migratedPin))
            }
        }
    }

    private fun requirePermitted(
        actionLabel: String,
        predicate: (UserPermissions) -> Boolean
    ): Boolean {
        if (!isUserAuthenticated.value) {
            lastSecurityError.value = "Debe iniciar sesión para $actionLabel."
            return false
        }
        val perms = RoleManager.getPermissions(settings.value)
        if (!predicate(perms)) {
            lastSecurityError.value = "Permiso denegado (${settings.value.currentRole.name}): $actionLabel."
            return false
        }
        lastSecurityError.value = null
        return true
    }

    fun clearSecurityError() {
        lastSecurityError.value = null
    }

    fun navigateTo(screen: AppScreen) {
        if (!isUserAuthenticated.value) return
        if (!RoleManager.canAccessScreen(screen, settings.value)) {
            lastSecurityError.value = "Módulo no autorizado para su rol actual."
            currentScreen.value = RoleManager.getFirstPermittedScreen(settings.value)
            return
        }
        currentScreen.value = screen
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setPOSInterface(interfaceNum: Int) {
        currentPOSInterface.value = interfaceNum
    }

    fun setSaleType(type: SaleType) {
        currentSaleType.value = type
    }

    fun setSelectedCustomer(client: Client?) {
        selectedCustomer.value = client
    }

    fun setPaymentMethod(method: String) {
        paymentMethod.value = method
    }

    fun setCustomerPaidInput(amount: String) {
        customerPaidInput.value = amount
    }

    fun setDiscountInput(discount: String) {
        discountInput.value = discount
    }

    fun setPosNotes(notes: String) {
        posNotes.value = notes
    }

    fun setShowTutorialDialog(show: Boolean) {
        showTutorialDialog.value = show
    }

    fun closeReceiptModal() {
        showReceiptModal.value = false
    }

    fun openSaleReceipt(sale: Sale) {
        val items = mutableListOf<SaleCartItem>()
        try {
            if (sale.itemsJson.isNotBlank()) {
                val jsonArr = JSONArray(sale.itemsJson)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val parsedType = runCatching {
                        ItemType.valueOf(obj.optString("type", ItemType.PRODUCTO.name))
                    }.getOrDefault(ItemType.PRODUCTO)
                    items.add(
                        SaleCartItem(
                            itemId = obj.optLong("itemId", 0L),
                            name = obj.optString("name", "Artículo"),
                            quantity = obj.optDouble("quantity", 1.0),
                            unitPrice = obj.optDouble("unitPrice", 0.0),
                            costPrice = obj.optDouble("costPrice", 0.0),
                            unit = obj.optString("unit", "Pza"),
                            type = parsedType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (items.isEmpty()) {
            items.add(
                SaleCartItem(
                    itemId = 0L,
                    name = "Venta General (${sale.folio})",
                    quantity = 1.0,
                    unitPrice = sale.total,
                    costPrice = 0.0,
                    unit = "Serv",
                    type = ItemType.PRODUCTO
                )
            )
        }
        lastCompletedSale.value = sale
        lastSaleItems.value = items
        showReceiptModal.value = true
    }

    fun openReportDetail(title: String) {
        if (!requirePermitted("ver reportes financieros") { it.canViewFinancialReports }) return
        selectedReportTitle.value = title
    }

    fun closeReportDetail() {
        selectedReportTitle.value = null
    }

    fun setReportDateFilter(filter: DateFilterType) {
        reportDateFilter.value = filter
    }

    fun openShiftCutReport(shift: CashRegisterShift) {
        if (!requirePermitted("consultar corte de caja") { it.canOpenCloseCashRegister }) return
        lastClosedShift.value = shift
        showShiftCutModal.value = true
    }

    fun closeShiftCutModal() {
        showShiftCutModal.value = false
    }

    // Cash Register Shift Operations (Abrir Caja, Entradas/Retiros, Corte/Cerrar Caja)
    suspend fun openCashRegisterAsync(openingFloat: Double, notes: String = ""): CashRegisterShift? {
        if (!requirePermitted("abrir caja") { it.canOpenCloseCashRegister }) return null
        if (!cashShiftOperationInFlight.compareAndSet(false, true)) return null
        isProcessingCashShift.value = true
        return try {
            withContext(Dispatchers.IO) {
                operationsRepo.openCashRegister(openingFloat, notes)
            }
        } finally {
            isProcessingCashShift.value = false
            cashShiftOperationInFlight.set(false)
        }
    }

    fun openCashRegister(openingFloat: Double, notes: String = "") {
        if (!requirePermitted("abrir caja") { it.canOpenCloseCashRegister }) return
        viewModelScope.launch(Dispatchers.IO) {
            openCashRegisterAsync(openingFloat, notes)
        }
    }

    suspend fun registerCashShiftMovementAsync(
        type: CashShiftMovementType,
        amount: Double,
        reason: String
    ): Boolean {
        if (!requirePermitted("registrar movimiento de caja") { it.canOpenCloseCashRegister }) return false
        if (amount <= 0.0 || !amount.isFinite() || reason.isBlank()) return false
        if (!cashShiftOperationInFlight.compareAndSet(false, true)) return false
        isProcessingCashShift.value = true
        return try {
            withContext(Dispatchers.IO) {
                operationsRepo.registerCashShiftMovement(type, amount, reason) != null
            }
        } finally {
            isProcessingCashShift.value = false
            cashShiftOperationInFlight.set(false)
        }
    }

    fun registerCashShiftMovement(
        type: CashShiftMovementType,
        amount: Double,
        reason: String
    ): Boolean {
        if (!requirePermitted("registrar movimiento de caja") { it.canOpenCloseCashRegister }) return false
        if (amount <= 0.0 || !amount.isFinite() || reason.isBlank()) return false
        viewModelScope.launch(Dispatchers.IO) {
            registerCashShiftMovementAsync(type, amount, reason)
        }
        return true
    }

    suspend fun closeCashRegisterAsync(
        countedCash: Double,
        nextTurnFloat: Double = 0.0,
        closingNotes: String = ""
    ): CashRegisterShift? {
        if (!requirePermitted("cerrar caja") { it.canOpenCloseCashRegister }) return null
        if (!cashShiftOperationInFlight.compareAndSet(false, true)) return null
        isProcessingCashShift.value = true
        return try {
            val closedShift = withContext(Dispatchers.IO) {
                operationsRepo.closeCashRegister(countedCash, nextTurnFloat, closingNotes)
            }
            if (closedShift != null) {
                lastClosedShift.value = closedShift
                showShiftCutModal.value = true
            }
            closedShift
        } finally {
            isProcessingCashShift.value = false
            cashShiftOperationInFlight.set(false)
        }
    }

    fun closeCashRegister(
        countedCash: Double,
        nextTurnFloat: Double = 0.0,
        closingNotes: String = ""
    ) {
        if (!requirePermitted("cerrar caja") { it.canOpenCloseCashRegister }) return
        viewModelScope.launch(Dispatchers.IO) {
            closeCashRegisterAsync(countedCash, nextTurnFloat, closingNotes)
        }
    }

    // Cart Operations with real stock checking
    fun addToCart(item: InventoryItem, quantity: Double = 1.0): Boolean {
        if (!requirePermitted("agregar artículos al carrito") { it.canCreateSale }) return false
        if (quantity <= 0.0 || !quantity.isFinite() || item.isWaste) return false
        val enforceStock = settings.value.checkStock && item.type != ItemType.SERVICIO
        val current = cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.itemId == item.id }
        val existingQty = if (index >= 0) current[index].quantity else 0.0

        if (enforceStock && (existingQty + quantity) > item.stock + 0.0001) {
            return false
        }

        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            current.add(
                SaleCartItem(
                    itemId = item.id,
                    name = item.name,
                    quantity = quantity,
                    unitPrice = item.salePrice,
                    costPrice = item.costPrice,
                    unit = item.unit,
                    type = item.type
                )
            )
        }
        cartItems.value = current
        return true
    }

    fun updateCartItemQuantity(itemId: Long, newQuantity: Double): Boolean {
        if (!requirePermitted("modificar cantidades del carrito") { it.canCreateSale }) return false
        if (!newQuantity.isFinite()) return false
        if (newQuantity <= 0.0) {
            removeFromCart(itemId)
            return true
        }
        val catalogItem = inventoryItems.value.firstOrNull { it.id == itemId }
        if (catalogItem != null && settings.value.checkStock && catalogItem.type != ItemType.SERVICIO) {
            if (newQuantity > catalogItem.stock + 0.0001) {
                return false
            }
        }
        val current = cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.itemId == itemId }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = newQuantity)
            cartItems.value = current
            return true
        }
        return false
    }

    fun removeFromCart(itemId: Long) {
        if (!requirePermitted("quitar artículos del carrito") { it.canCreateSale }) return
        cartItems.value = cartItems.value.filterNot { it.itemId == itemId }
    }

    fun clearCart() {
        cartItems.value = emptyList()
        selectedCustomer.value = null
        customerPaidInput.value = ""
        discountInput.value = "0"
        posNotes.value = ""
        activeCheckoutOperationId.value = UUID.randomUUID().toString()
    }

    // Complete Sale / Quote with ACID Room Transaction, Mutex Lock & Duplicate Protection
    suspend fun completeSaleAsync(
        operationId: String? = activeCheckoutOperationId.value,
        faultHookAfterSaleInsert: (() -> Unit)? = null
    ): SaleCheckoutResult {
        if (!requirePermitted("registrar ventas") { it.canCreateSale }) {
            return SaleCheckoutResult.Unauthorized
        }
        val items = cartItems.value
        if (items.isEmpty()) return SaleCheckoutResult.EmptyCart

        if (!saleOperationInFlight.compareAndSet(false, true)) {
            return SaleCheckoutResult.AlreadyProcessing
        }
        isProcessingSale.value = true
        return try {
            val outcome = withContext(Dispatchers.IO) {
                operationsRepo.completeSale(
                    items = items,
                    saleType = currentSaleType.value,
                    selectedCustomerId = selectedCustomer.value?.id,
                    paymentMethod = paymentMethod.value,
                    customerPaidInput = customerPaidInput.value,
                    discountInput = discountInput.value,
                    notes = posNotes.value,
                    operationId = operationId,
                    faultHookAfterSaleInsert = faultHookAfterSaleInsert
                )
            }

            when (outcome) {
                is RepoSaleResult.Committed -> {
                    lastCompletedSale.value = outcome.sale
                    lastSaleItems.value = outcome.items
                    showReceiptModal.value = true
                    clearCart()
                    SaleCheckoutResult.Success
                }
                RepoSaleResult.AlreadyProcessed -> SaleCheckoutResult.AlreadyProcessing
                RepoSaleResult.EmptyCart -> SaleCheckoutResult.EmptyCart
                RepoSaleResult.ShiftNotOpen -> SaleCheckoutResult.ShiftNotOpen
                RepoSaleResult.ClientRequiredForCredit -> SaleCheckoutResult.ClientRequiredForCredit
                is RepoSaleResult.InsufficientCash -> SaleCheckoutResult.InsufficientCash(outcome.missing)
                is RepoSaleResult.InsufficientStock -> SaleCheckoutResult.InsufficientStock(
                    itemName = outcome.itemName,
                    available = outcome.available,
                    requested = outcome.requested
                )
            }
        } finally {
            isProcessingSale.value = false
            saleOperationInFlight.set(false)
        }
    }

    @Deprecated("Usa completeSaleAsync() desde coroutines para evitar bloqueo en UI.")
    fun completeSale(): SaleCheckoutResult {
        return runBlocking(Dispatchers.IO) {
            completeSaleAsync(activeCheckoutOperationId.value)
        }
    }

    /**
     * Voids/Cancels a completed sale transactionally, restoring product stock once,
     * reversing client credit balance from live Room state, and updating closed shift audit if applicable.
     */
    suspend fun cancelSaleAsync(sale: Sale): Boolean {
        if (!requirePermitted("anular ventas") { it.canCancelSales }) return false
        val updated = withContext(Dispatchers.IO) {
            operationsRepo.cancelSale(sale)
        } ?: return false

        if (lastCompletedSale.value?.id == updated.id || lastCompletedSale.value?.folio == updated.folio) {
            lastCompletedSale.value = updated
        }
        return true
    }

    fun cancelSale(sale: Sale) {
        if (!requirePermitted("anular ventas") { it.canCancelSales }) return
        viewModelScope.launch(Dispatchers.IO) {
            cancelSaleAsync(sale)
        }
    }

    // Purchases & Stock replenishment with ACID Transaction, Weighted Average Cost & full itemsJson
    suspend fun registerPurchaseAsync(
        supplierId: Long?,
        fallbackSupplierName: String,
        itemsBought: List<Triple<InventoryItem, Double, Double>>,
        isCredit: Boolean,
        notes: String,
        operationId: String? = null,
        faultHookAfterPurchaseInsert: (() -> Unit)? = null
    ): PurchaseRegistrationResult {
        if (!requirePermitted("registrar compras") { it.canCreatePurchase }) {
            return PurchaseRegistrationResult.Unauthorized
        }
        if (!purchaseOperationInFlight.compareAndSet(false, true)) {
            return PurchaseRegistrationResult.AlreadyProcessing
        }
        isProcessingPurchase.value = true
        lastPurchaseError.value = null
        return try {
            val lines = itemsBought.map { (item, qty, cost) ->
                PurchaseLineInput(itemId = item.id, quantity = qty, unitCost = cost)
            }
            val res = withContext(Dispatchers.IO) {
                operationsRepo.registerPurchase(
                    supplierId = supplierId,
                    fallbackSupplierName = fallbackSupplierName,
                    lines = lines,
                    isCredit = isCredit,
                    notes = notes,
                    operationId = operationId,
                    faultHookAfterPurchaseInsert = faultHookAfterPurchaseInsert
                )
            }
            when (res) {
                is RepoPurchaseResult.Committed -> PurchaseRegistrationResult.Success(res.purchase)
                RepoPurchaseResult.AlreadyProcessed -> PurchaseRegistrationResult.AlreadyProcessing
                is RepoPurchaseResult.Invalid -> {
                    lastPurchaseError.value = res.reason
                    PurchaseRegistrationResult.Error(res.reason)
                }
            }
        } finally {
            isProcessingPurchase.value = false
            purchaseOperationInFlight.set(false)
        }
    }

    fun registerPurchase(purchase: Purchase, itemsBought: List<Triple<InventoryItem, Double, Double>>) {
        if (!requirePermitted("registrar compras") { it.canCreatePurchase }) return
        viewModelScope.launch(Dispatchers.IO) {
            registerPurchaseAsync(
                supplierId = purchase.supplierId,
                fallbackSupplierName = purchase.supplierName,
                itemsBought = itemsBought,
                isCredit = purchase.isCredit,
                notes = purchase.notes
            )
        }
    }

    // Inventory CRUD (Transactional & Audited in inventory_movements)
    suspend fun saveInventoryItemAsync(item: InventoryItem, reason: String = ""): InventoryItem? {
        if (!requirePermitted("guardar artículos de inventario") { it.canAccessInventory }) return null
        if (item.name.isBlank()) return null
        val canEditCost = RoleManager.getPermissions(settings.value).canViewCostPrice
        return withContext(Dispatchers.IO) {
            operationsRepo.saveInventoryItem(
                item = item,
                canEditCostPrice = canEditCost,
                reason = reason
            )
        }
    }

    fun saveInventoryItem(item: InventoryItem, reason: String = "") {
        if (!requirePermitted("guardar artículos de inventario") { it.canAccessInventory }) return
        if (item.name.isBlank()) return
        val canEditCost = RoleManager.getPermissions(settings.value).canViewCostPrice
        viewModelScope.launch(Dispatchers.IO) {
            operationsRepo.saveInventoryItem(
                item = item,
                canEditCostPrice = canEditCost,
                reason = reason
            )
        }
    }

    fun deleteInventoryItem(item: InventoryItem) {
        if (!requirePermitted("eliminar artículos del inventario") { it.canDeleteInventoryItem }) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteItem(item)
        }
    }

    suspend fun adjustItemStockAsync(
        item: InventoryItem,
        delta: Double,
        reason: String = "Ajuste manual en inventario"
    ): InventoryItem? {
        if (!requirePermitted("ajustar existencias") { it.canAccessInventory }) return null
        return withContext(Dispatchers.IO) {
            operationsRepo.adjustItemStock(item.id, delta, reason)
        }
    }

    fun adjustItemStock(
        item: InventoryItem,
        delta: Double,
        reason: String = "Ajuste manual en inventario"
    ) {
        if (!requirePermitted("ajustar existencias") { it.canAccessInventory }) return
        viewModelScope.launch(Dispatchers.IO) {
            operationsRepo.adjustItemStock(item.id, delta, reason)
        }
    }

    // Extra Movements
    fun saveExtraMovement(movement: ExtraMovement) {
        if (!requirePermitted("registrar movimientos de caja extra") { it.canCreateMovement }) return
        if (movement.concept.isBlank() || !movement.amount.isFinite() || movement.amount <= 0.0) return
        val sanitized = movement.copy(
            concept = movement.concept.trim(),
            category = movement.category.trim().ifEmpty { "Otros" },
            notes = movement.notes.trim()
        )
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertExtraMovement(sanitized)
        }
    }

    fun deleteExtraMovement(movement: ExtraMovement) {
        if (!requirePermitted("eliminar movimientos extra") { it.canDeleteExtraMovement }) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteExtraMovement(movement)
        }
    }

    // Rentals & Loans
    fun saveRentalLoan(item: RentalLoan) {
        if (!requirePermitted("registrar rentas o préstamos") { it.canCreateRental }) return
        if (item.contactName.isBlank() || item.itemOrReason.isBlank()) return
        if (!item.amount.isFinite() || item.amount < 0.0 || !item.depositAmount.isFinite() || item.depositAmount < 0.0) return
        val sanitized = item.copy(
            contactName = item.contactName.trim(),
            itemOrReason = item.itemOrReason.trim(),
            notes = item.notes.trim()
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (sanitized.id == 0L) {
                dao.insertRentalLoan(sanitized)
            } else {
                dao.updateRentalLoan(sanitized)
            }
        }
    }

    fun toggleRentalLoanStatus(item: RentalLoan) {
        if (!requirePermitted("actualizar estado de renta o préstamo") { it.canCreateRental }) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateRentalLoan(item.copy(isReturnedOrPaid = !item.isReturnedOrPaid))
        }
    }

    fun deleteRentalLoan(item: RentalLoan) {
        if (!requirePermitted("eliminar rentas o préstamos") { it.canDeleteRental }) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteRentalLoan(item)
        }
    }

    // Clients & Suppliers
    fun saveClient(client: Client) {
        if (!requirePermitted("guardar clientes") { it.canCreateClient }) return
        if (client.name.isBlank()) return
        val safeCredit = client.creditBalance.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val sanitized = client.copy(
            name = client.name.trim(),
            company = client.company.trim(),
            phone = client.phone.trim(),
            email = client.email.trim(),
            address = client.address.trim(),
            creditBalance = safeCredit,
            notes = client.notes.trim()
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (sanitized.id == 0L) dao.insertClient(sanitized) else dao.updateClient(sanitized)
        }
    }

    fun deleteClient(client: Client) {
        if (!requirePermitted("eliminar clientes") { it.canDeleteClient }) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteClient(client)
        }
    }

    fun saveSupplier(supplier: Supplier) {
        if (!requirePermitted("guardar proveedores") { it.canCreateSupplier }) return
        if (supplier.name.isBlank()) return
        val sanitized = supplier.copy(
            name = supplier.name.trim(),
            company = supplier.company.trim(),
            contactName = supplier.contactName.trim(),
            phone = supplier.phone.trim(),
            email = supplier.email.trim(),
            address = supplier.address.trim(),
            suppliedProducts = supplier.suppliedProducts.trim(),
            paymentTerms = supplier.paymentTerms.trim().ifEmpty { "Contado" },
            notes = supplier.notes.trim()
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (sanitized.id == 0L) dao.insertSupplier(sanitized) else dao.updateSupplier(sanitized)
        }
    }

    fun deleteSupplier(supplier: Supplier) {
        if (!requirePermitted("eliminar proveedores") { it.canDeleteSupplier }) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSupplier(supplier)
        }
    }

    // Employees & Roles (Strict privilege hierarchy & hashed PIN storage)
    fun saveEmployee(employee: Employee) {
        if (!requirePermitted("administrar empleados") { it.canCreateEmployee }) return
        val actorRole = settings.value.currentRole
        if (!RoleManager.canManageEmployeeRole(actorRole, employee.role)) {
            lastSecurityError.value = "Solo un Administrador puede asignar o modificar el rol ${employee.role.name}."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val existing = if (employee.id != 0L) {
                dao.getAllEmployeesSync().firstOrNull { it.id == employee.id }
            } else null

            if (existing != null && !RoleManager.canManageEmployeeRole(actorRole, existing.role)) {
                lastSecurityError.value = "No tienes autoridad para modificar a un usuario ${existing.role.name}."
                return@launch
            }

            val cleanPin = employee.pin.trim()
            val finalPin = when {
                cleanPin.isEmpty() -> existing?.pin ?: ""
                PasswordHasher.isHashed(cleanPin) -> cleanPin
                PasswordHasher.isLegacyDefaultCredential(cleanPin) -> {
                    lastSecurityError.value = "El PIN $cleanPin no está permitido por seguridad."
                    return@launch
                }
                cleanPin.length < 4 -> {
                    lastSecurityError.value = "El PIN del empleado debe tener al menos 4 dígitos."
                    return@launch
                }
                else -> PasswordHasher.hash(cleanPin)
            }

            val toSave = employee.copy(pin = finalPin)
            if (toSave.id == 0L) dao.insertEmployee(toSave) else dao.updateEmployee(toSave)
        }
    }

    fun deleteEmployee(employee: Employee) {
        if (!requirePermitted("eliminar empleados") { it.canDeleteEmployee }) return
        if (!RoleManager.canManageEmployeeRole(settings.value.currentRole, employee.role)) {
            lastSecurityError.value = "Solo un Administrador puede eliminar a otro Administrador."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteEmployee(employee)
        }
    }

    /**
     * Kept for signature compatibility only: NEVER switches roles without credentials.
     * Use switchActiveRoleWithCredential(...) for authenticated role changes.
     */
    @Deprecated("Direct unauthorized role switching is disabled. Use switchActiveRoleWithCredential().")
    fun switchActiveRole(role: UserRole, userName: String) {
        if (role == settings.value.currentRole && userName == settings.value.currentUserName) return
        lastSecurityError.value = "Cambio de rol bloqueado: se requiere autorización con contraseña o PIN."
    }

    fun switchActiveRoleWithCredential(
        targetRole: UserRole,
        credential: String,
        targetEmployee: Employee? = null
    ): SecurityActionResult {
        if (!isUserAuthenticated.value) {
            return SecurityActionResult.Error("Debe iniciar sesión primero.")
        }
        val cleanCred = credential.trim()
        if (cleanCred.isEmpty()) {
            return SecurityActionResult.Error("Ingrese la contraseña o PIN para autorizar el cambio de rol.")
        }
        val s = settings.value
        if (targetRole != UserRole.ADMINISTRADOR && (s.isAppLocked || isTrialExpired())) {
            return SecurityActionResult.Error("La aplicación se encuentra bloqueada o en prueba expirada. Solo Administrador puede acceder.")
        }

        val verifiedName: String? = when {
            targetEmployee != null -> {
                if (targetEmployee.role != targetRole) {
                    return SecurityActionResult.Error("El empleado seleccionado no corresponde al rol ${targetRole.name}.")
                }
                if (targetEmployee.pin.isNotBlank() && PasswordHasher.verify(cleanCred, targetEmployee.pin)) {
                    targetEmployee.name
                } else {
                    null
                }
            }
            else -> {
                val roleHash = when (targetRole) {
                    UserRole.ADMINISTRADOR -> securityVault.adminPasswordHash
                    UserRole.DUENO -> securityVault.jefePasswordHash
                    UserRole.TRABAJADOR -> securityVault.trabajadorPasswordHash
                }
                if (PasswordHasher.verify(cleanCred, roleHash)) {
                    defaultDisplayNameForRole(targetRole)
                } else {
                    employees.value.firstOrNull {
                        it.role == targetRole && it.pin.isNotBlank() && PasswordHasher.verify(cleanCred, it.pin)
                    }?.name
                }
            }
        }

        if (verifiedName == null) {
            lastSecurityError.value = "Credencial incorrecta para cambiar al rol ${targetRole.name}."
            return SecurityActionResult.Error("Contraseña o PIN incorrecto para el rol ${targetRole.name}.")
        }

        val updated = s.copy(
            currentRole = targetRole,
            currentUserName = verifiedName
        )
        settings.value = updated
        lastSecurityError.value = null
        persistSettingsAsync(updated)
        if (!RoleManager.canAccessScreen(currentScreen.value, updated)) {
            currentScreen.value = RoleManager.getFirstPermittedScreen(updated)
        }
        return SecurityActionResult.Success
    }

    private fun defaultDisplayNameForRole(role: UserRole): String {
        return when (role) {
            UserRole.ADMINISTRADOR -> "Administrador Master"
            UserRole.DUENO -> "Jefe / Dueño"
            UserRole.TRABAJADOR -> "Trabajador Operativo"
        }
    }

    /**
     * First-time offline security provisioning. Generates unique random recovery codes
     * and stores all passwords, master PIN, recovery answer and recovery codes strictly hashed
     * inside the AES-256-GCM encrypted vault.
     */
    fun initializeSecuritySetup(
        adminPassword: String,
        jefePassword: String,
        trabajadorPassword: String,
        masterPin: String,
        recoveryQuestion: String,
        recoveryAnswer: String
    ): InitialSecuritySetupResult {
        if (securityVault.isInitialized) {
            return InitialSecuritySetupResult.Error("La seguridad inicial ya fue configurada en este dispositivo.")
        }
        val cleanAdmin = adminPassword.trim()
        val cleanJefe = jefePassword.trim()
        val cleanTrab = trabajadorPassword.trim()
        val cleanPin = masterPin.trim()
        val cleanQuestion = recoveryQuestion.trim()
        val cleanAnswer = PasswordHasher.normalizeAnswer(recoveryAnswer)

        if (cleanAdmin.length < 6) {
            return InitialSecuritySetupResult.Error("La contraseña de Administrador debe tener al menos 6 caracteres.")
        }
        if (cleanJefe.length < 6) {
            return InitialSecuritySetupResult.Error("La contraseña de Dueño/Jefe debe tener al menos 6 caracteres.")
        }
        if (cleanTrab.length < 4) {
            return InitialSecuritySetupResult.Error("La contraseña de Trabajador debe tener al menos 4 caracteres.")
        }
        if (PasswordHasher.isLegacyDefaultCredential(cleanAdmin) ||
            PasswordHasher.isLegacyDefaultCredential(cleanJefe) ||
            PasswordHasher.isLegacyDefaultCredential(cleanTrab)
        ) {
            return InitialSecuritySetupResult.Error("Por seguridad no uses contraseñas predeterminadas (Admin, Jefe, Trabajador, 1234, 0000).")
        }
        if (cleanPin.length !in 4..8 || !cleanPin.all { it.isDigit() } || PasswordHasher.isLegacyDefaultCredential(cleanPin)) {
            return InitialSecuritySetupResult.Error("El PIN maestro debe tener entre 4 y 8 dígitos y no puede ser 1234 ni 0000.")
        }
        if (cleanQuestion.length < 5) {
            return InitialSecuritySetupResult.Error("Ingresa una pregunta de seguridad válida (mínimo 5 caracteres).")
        }
        if (cleanAnswer.length < 3) {
            return InitialSecuritySetupResult.Error("La respuesta de recuperación debe tener al menos 3 caracteres.")
        }

        val ownerRecoveryCode = PasswordHasher.generateRecoveryCode()
        val adminRecoveryCode = PasswordHasher.generateRecoveryCode()

        val newVault = SecurityVault(
            isInitialized = true,
            adminPasswordHash = PasswordHasher.hash(cleanAdmin),
            jefePasswordHash = PasswordHasher.hash(cleanJefe),
            trabajadorPasswordHash = PasswordHasher.hash(cleanTrab),
            masterPinHash = PasswordHasher.hash(cleanPin),
            ownerRecoveryCodeHash = PasswordHasher.hash(PasswordHasher.normalizeRecoveryCode(ownerRecoveryCode)),
            adminRecoveryCodeHash = PasswordHasher.hash(PasswordHasher.normalizeRecoveryCode(adminRecoveryCode)),
            recoveryQuestion = cleanQuestion,
            ownerRecoveryAnswerHash = PasswordHasher.hash(cleanAnswer),
            failedAttempts = 0,
            lockoutUntilMs = 0L
        )

        securityVault = newVault
        refreshSecurityUiState()
        lastLoginError.value = null
        persistAllAsync(settings.value, newVault)
        return InitialSecuritySetupResult.Success(
            ownerRecoveryCode = ownerRecoveryCode,
            adminRecoveryCode = adminRecoveryCode
        )
    }

    /**
     * Verifies the Master PIN against its PBKDF2 hash inside the encrypted vault.
     * No universal "1234" bypass exists.
     */
    fun verifyMasterPin(pinAttempt: String): Boolean {
        val cleanPin = pinAttempt.trim()
        if (cleanPin.isEmpty() || !securityVault.isInitialized || securityVault.masterPinHash.isBlank()) {
            return false
        }
        return PasswordHasher.verify(cleanPin, securityVault.masterPinHash)
    }

    fun enablePinRestriction(newPin: String): SecurityActionResult {
        if (!requirePermitted("configurar PIN de acceso") { it.canAccessJefePanel || it.canAccessAdminPanel }) {
            return SecurityActionResult.Error("Solo Dueño o Administrador pueden configurar el PIN maestro.")
        }
        val cleanPin = newPin.trim()
        if (cleanPin.length !in 4..8 || !cleanPin.all { it.isDigit() }) {
            return SecurityActionResult.Error("El PIN debe contener entre 4 y 8 dígitos numéricos.")
        }
        if (PasswordHasher.isLegacyDefaultCredential(cleanPin)) {
            return SecurityActionResult.Error("Por seguridad, elige un PIN distinto de 1234 o 0000.")
        }
        val updatedVault = securityVault.copy(masterPinHash = PasswordHasher.hash(cleanPin))
        val updatedSettings = settings.value.copy(restrictWithPin = true)
        securityVault = updatedVault
        settings.value = updatedSettings
        refreshSecurityUiState()
        persistAllAsync(updatedSettings, updatedVault)
        return SecurityActionResult.Success
    }

    fun disablePinRestriction(): SecurityActionResult {
        if (!requirePermitted("desactivar PIN de acceso") { it.canAccessJefePanel || it.canAccessAdminPanel }) {
            return SecurityActionResult.Error("Solo Dueño o Administrador pueden desactivar el PIN maestro.")
        }
        val updatedSettings = settings.value.copy(restrictWithPin = false)
        settings.value = updatedSettings
        persistSettingsAsync(updatedSettings)
        return SecurityActionResult.Success
    }

    fun updateRolePassword(
        targetRole: UserRole,
        currentActorPassword: String,
        newPassword: String
    ): SecurityActionResult {
        if (!requirePermitted("cambiar contraseñas de acceso") { it.canChangePasswords }) {
            return SecurityActionResult.Error("No tienes permiso para cambiar contraseñas.")
        }
        val actorRole = settings.value.currentRole
        if (targetRole == UserRole.ADMINISTRADOR && actorRole != UserRole.ADMINISTRADOR) {
            return SecurityActionResult.Error("Solo el Administrador puede cambiar su propia contraseña.")
        }

        val actorHash = when (actorRole) {
            UserRole.ADMINISTRADOR -> securityVault.adminPasswordHash
            UserRole.DUENO -> securityVault.jefePasswordHash
            UserRole.TRABAJADOR -> return SecurityActionResult.Error("Acceso denegado.")
        }
        if (!PasswordHasher.verify(currentActorPassword.trim(), actorHash)) {
            return SecurityActionResult.Error("Tu contraseña actual es incorrecta.")
        }

        val cleanNew = newPassword.trim()
        val minLength = if (targetRole == UserRole.TRABAJADOR) 4 else 6
        if (cleanNew.length < minLength) {
            return SecurityActionResult.Error("La nueva contraseña debe tener al menos $minLength caracteres.")
        }
        if (PasswordHasher.isLegacyDefaultCredential(cleanNew)) {
            return SecurityActionResult.Error("Por seguridad no uses contraseñas predeterminadas (Admin, Jefe, Trabajador, 1234, 0000).")
        }

        val newHash = PasswordHasher.hash(cleanNew)
        val updatedVault = when (targetRole) {
            UserRole.ADMINISTRADOR -> securityVault.copy(adminPasswordHash = newHash)
            UserRole.DUENO -> securityVault.copy(jefePasswordHash = newHash)
            UserRole.TRABAJADOR -> securityVault.copy(trabajadorPasswordHash = newHash)
        }
        securityVault = updatedVault
        refreshSecurityUiState()
        persistVaultAsync(updatedVault)
        return SecurityActionResult.Success
    }

    fun recoverOwnerWithSecurityAnswer(
        answerInput: String,
        newPassword: String
    ): RecoveryResult {
        if (!securityVault.isInitialized) {
            return RecoveryResult.Error("La configuración inicial aún no se ha completado.")
        }
        val now = System.currentTimeMillis()
        if (now < securityVault.lockoutUntilMs) {
            val waitSecs = ((securityVault.lockoutUntilMs - now) / 1000L).coerceAtLeast(1L)
            return RecoveryResult.Error("Demasiados intentos fallidos. Espera ${waitSecs}s para reintentar.")
        }
        val cleanAnswer = PasswordHasher.normalizeAnswer(answerInput)
        if (cleanAnswer.isEmpty() || !PasswordHasher.verify(cleanAnswer, securityVault.ownerRecoveryAnswerHash)) {
            recordFailedAuthAttempt()
            return RecoveryResult.Error("Respuesta de seguridad incorrecta.")
        }
        val cleanNew = newPassword.trim()
        if (cleanNew.length < 6) {
            return RecoveryResult.Error("La nueva contraseña de Dueño debe tener al menos 6 caracteres.")
        }
        if (PasswordHasher.isLegacyDefaultCredential(cleanNew)) {
            return RecoveryResult.Error("No uses contraseñas predeterminadas (Admin, Jefe, Trabajador, 1234, 0000).")
        }
        val updatedVault = securityVault.copy(
            jefePasswordHash = PasswordHasher.hash(cleanNew),
            failedAttempts = 0,
            lockoutUntilMs = 0L
        )
        securityVault = updatedVault
        refreshSecurityUiState()
        lastLoginError.value = null
        persistVaultAsync(updatedVault)
        return RecoveryResult.Success()
    }

    fun recoverRoleWithCode(
        targetRole: UserRole,
        recoveryCodeInput: String,
        newPassword: String
    ): RecoveryResult {
        if (!securityVault.isInitialized) {
            return RecoveryResult.Error("La configuración inicial aún no se ha completado.")
        }
        if (targetRole == UserRole.TRABAJADOR) {
            return RecoveryResult.Error("El Dueño o Administrador debe restablecer la clave del Trabajador desde Configuración.")
        }
        val now = System.currentTimeMillis()
        if (now < securityVault.lockoutUntilMs) {
            val waitSecs = ((securityVault.lockoutUntilMs - now) / 1000L).coerceAtLeast(1L)
            return RecoveryResult.Error("Demasiados intentos fallidos. Espera ${waitSecs}s.")
        }
        val normalizedCode = PasswordHasher.normalizeRecoveryCode(recoveryCodeInput)
        val expectedHash = when (targetRole) {
            UserRole.DUENO -> securityVault.ownerRecoveryCodeHash
            UserRole.ADMINISTRADOR -> securityVault.adminRecoveryCodeHash
            UserRole.TRABAJADOR -> ""
        }
        if (normalizedCode.isEmpty() || !PasswordHasher.verify(normalizedCode, expectedHash)) {
            recordFailedAuthAttempt()
            return RecoveryResult.Error("Código de recuperación inválido para ${targetRole.name}.")
        }
        val cleanNew = newPassword.trim()
        if (cleanNew.length < 6) {
            return RecoveryResult.Error("La nueva contraseña debe tener al menos 6 caracteres.")
        }
        if (PasswordHasher.isLegacyDefaultCredential(cleanNew)) {
            return RecoveryResult.Error("No uses contraseñas predeterminadas (Admin, Jefe, Trabajador, 1234, 0000).")
        }

        // Rotate single-use recovery code immediately so old code cannot be reused
        val freshCode = PasswordHasher.generateRecoveryCode()
        val freshCodeHash = PasswordHasher.hash(PasswordHasher.normalizeRecoveryCode(freshCode))
        val newPassHash = PasswordHasher.hash(cleanNew)

        val updatedVault = when (targetRole) {
            UserRole.DUENO -> securityVault.copy(
                jefePasswordHash = newPassHash,
                ownerRecoveryCodeHash = freshCodeHash,
                failedAttempts = 0,
                lockoutUntilMs = 0L
            )
            UserRole.ADMINISTRADOR -> securityVault.copy(
                adminPasswordHash = newPassHash,
                adminRecoveryCodeHash = freshCodeHash,
                failedAttempts = 0,
                lockoutUntilMs = 0L
            )
            UserRole.TRABAJADOR -> securityVault
        }
        securityVault = updatedVault
        refreshSecurityUiState()
        lastLoginError.value = null
        persistVaultAsync(updatedVault)
        return RecoveryResult.Success(newRecoveryCode = freshCode)
    }

    fun regenerateRecoveryMaterial(
        currentActorPassword: String,
        newQuestion: String? = null,
        newAnswer: String? = null
    ): RecoveryResult {
        if (!requirePermitted("gestionar recuperación offline") { it.canAccessJefePanel || it.canAccessAdminPanel }) {
            return RecoveryResult.Error("Acceso no autorizado.")
        }
        val actorRole = settings.value.currentRole
        val actorHash = if (actorRole == UserRole.ADMINISTRADOR) {
            securityVault.adminPasswordHash
        } else {
            securityVault.jefePasswordHash
        }
        if (!PasswordHasher.verify(currentActorPassword.trim(), actorHash)) {
            return RecoveryResult.Error("Contraseña actual incorrecta.")
        }

        val freshCode = PasswordHasher.generateRecoveryCode()
        val freshCodeHash = PasswordHasher.hash(PasswordHasher.normalizeRecoveryCode(freshCode))

        var updatedVault = if (actorRole == UserRole.ADMINISTRADOR) {
            securityVault.copy(adminRecoveryCodeHash = freshCodeHash)
        } else {
            securityVault.copy(ownerRecoveryCodeHash = freshCodeHash)
        }

        if (!newQuestion.isNullOrBlank() && !newAnswer.isNullOrBlank()) {
            val cleanQ = newQuestion.trim()
            val cleanA = PasswordHasher.normalizeAnswer(newAnswer)
            if (cleanQ.length < 5 || cleanA.length < 3) {
                return RecoveryResult.Error("Pregunta (mín. 5 chars) y respuesta (mín. 3 chars) requeridas.")
            }
            updatedVault = updatedVault.copy(
                recoveryQuestion = cleanQ,
                ownerRecoveryAnswerHash = PasswordHasher.hash(cleanA)
            )
        }

        securityVault = updatedVault
        refreshSecurityUiState()
        persistVaultAsync(updatedVault)
        return RecoveryResult.Success(newRecoveryCode = freshCode)
    }

    private fun recordFailedAuthAttempt() {
        val now = System.currentTimeMillis()
        val nextCount = securityVault.failedAttempts + 1
        val lockout = if (nextCount >= 5 && nextCount % 5 == 0) now + 30_000L else 0L
        securityVault = securityVault.copy(failedAttempts = nextCount, lockoutUntilMs = lockout)
        refreshSecurityUiState()
        persistVaultAsync(securityVault)
    }

    /**
     * Checks if trial period has expired.
     */
    fun isTrialExpired(): Boolean {
        val s = settings.value
        if (!s.trialModeEnabled || s.trialStartDate <= 0L) return false
        val elapsedMs = System.currentTimeMillis() - s.trialStartDate
        val durationMs = s.trialDurationDays.toLong() * 24 * 3600 * 1000L
        return elapsedMs > durationMs
    }

    /**
     * Calculates days and hours remaining in trial mode.
     */
    fun getTrialRemainingTime(): String {
        val s = settings.value
        if (!s.trialModeEnabled || s.trialStartDate <= 0L) return "Prueba Ilimitada / Desbloqueado"
        val elapsedMs = System.currentTimeMillis() - s.trialStartDate
        val totalMs = s.trialDurationDays.toLong() * 24 * 3600 * 1000L
        val remainingMs = totalMs - elapsedMs
        if (remainingMs <= 0L) return "PERIODO DE PRUEBA EXPIRADO"
        val days = remainingMs / (24 * 3600 * 1000L)
        val hours = (remainingMs % (24 * 3600 * 1000L)) / (3600 * 1000L)
        return "$days días y $hours horas restantes"
    }

    /**
     * Authenticates user against hashed role credentials or Room employee hashed PINs.
     * Universal hardcoded fallback passwords ("Admin", "Jefe", "Trabajador", "1234") are completely removed.
     */
    fun authenticateUser(usernameInput: String, passwordInput: String): Boolean {
        if (!securityVault.isInitialized) {
            lastLoginError.value = "Complete primero la configuración inicial de seguridad."
            return false
        }
        val now = System.currentTimeMillis()
        if (now < securityVault.lockoutUntilMs) {
            val secs = ((securityVault.lockoutUntilMs - now) / 1000L).coerceAtLeast(1L)
            lastLoginError.value = "Acceso temporalmente bloqueado por intentos fallidos. Espera ${secs}s."
            return false
        }

        val s = settings.value
        val cleanUser = usernameInput.trim()
        val cleanPass = passwordInput.trim()

        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            lastLoginError.value = "Ingresa tu usuario/empleado y contraseña o PIN."
            return false
        }

        // 1. Master role lookup
        val masterRoleMatch: Pair<UserRole, String>? = when {
            cleanUser.equals("Admin", ignoreCase = true) || cleanUser.equals("Administrador", ignoreCase = true) -> {
                if (PasswordHasher.verify(cleanPass, securityVault.adminPasswordHash)) {
                    UserRole.ADMINISTRADOR to "Administrador Master"
                } else null
            }
            cleanUser.equals("Jefe", ignoreCase = true) ||
                cleanUser.equals("Dueno", ignoreCase = true) ||
                cleanUser.equals("Dueño", ignoreCase = true) -> {
                if (PasswordHasher.verify(cleanPass, securityVault.jefePasswordHash)) {
                    UserRole.DUENO to "Jefe / Dueño"
                } else null
            }
            cleanUser.equals("Trabajador", ignoreCase = true) ||
                cleanUser.equals("Vendedor", ignoreCase = true) ||
                cleanUser.equals("Cajero", ignoreCase = true) -> {
                if (PasswordHasher.verify(cleanPass, securityVault.trabajadorPasswordHash)) {
                    UserRole.TRABAJADOR to "Trabajador Operativo"
                } else null
            }
            else -> null
        }

        // 2. Registered Room Employee lookup (Name or Email + hashed personal PIN)
        val matchedEmployee = if (masterRoleMatch == null) {
            employees.value.firstOrNull { emp ->
                (emp.name.equals(cleanUser, ignoreCase = true) ||
                    (emp.email.isNotBlank() && emp.email.equals(cleanUser, ignoreCase = true))) &&
                    emp.pin.isNotBlank() &&
                    PasswordHasher.verify(cleanPass, emp.pin)
            }
        } else null

        val resolvedRole = masterRoleMatch?.first ?: matchedEmployee?.role
        val resolvedName = masterRoleMatch?.second ?: matchedEmployee?.name

        if (resolvedRole == null || resolvedName == null) {
            recordFailedAuthAttempt()
            val updatedLock = securityVault.lockoutUntilMs
            lastLoginError.value = if (updatedLock > System.currentTimeMillis()) {
                "Demasiados intentos fallidos. Acceso bloqueado por 30 segundos."
            } else {
                "Usuario o contraseña/PIN incorrectos."
            }
            return false
        }

        // Enforce Admin app lock / trial expiration on all non-Admin accounts (roles & employees)
        if (resolvedRole != UserRole.ADMINISTRADOR && (s.isAppLocked || isTrialExpired())) {
            lastLoginError.value = "La aplicación se encuentra BLOQUEADA o el periodo de prueba ha expirado. Contacte al Administrador."
            return false
        }

        val updatedSettings = s.copy(
            currentRole = resolvedRole,
            currentUserName = resolvedName
        )
        val clearedVault = securityVault.copy(failedAttempts = 0, lockoutUntilMs = 0L)

        settings.value = updatedSettings
        securityVault = clearedVault
        refreshSecurityUiState()
        currentScreen.value = RoleManager.getFirstPermittedScreen(updatedSettings)
        isUserAuthenticated.value = true
        lastLoginError.value = null
        lastSecurityError.value = null
        persistAllAsync(updatedSettings, clearedVault)
        return true
    }

    fun logout() {
        isUserAuthenticated.value = false
        lastLoginError.value = null
        lastSecurityError.value = null
    }

    /**
     * Checks if a screen/module is permitted for the active session,
     * centralizing permission evaluation through RoleManager.
     */
    fun isModulePermitted(screen: AppScreen): Boolean {
        return RoleManager.canAccessScreen(screen, settings.value)
    }

    /**
     * Returns the full permission set for the active user session.
     */
    fun getUserPermissions(): UserPermissions {
        return RoleManager.getPermissions(settings.value)
    }

    fun setTrialMode(enabled: Boolean, days: Int = 7) {
        if (!requirePermitted("configurar modo de prueba") { it.canAccessAdminPanel }) return
        val s = settings.value
        val updated = s.copy(
            trialModeEnabled = enabled,
            trialDurationDays = days,
            trialStartDate = if (enabled) System.currentTimeMillis() else 0L,
            isAppLocked = false
        )
        settings.value = updated
        persistSettingsAsync(updated)
    }

    fun unlockApp() {
        if (!requirePermitted("desbloquear la aplicación") { it.canAccessAdminPanel }) return
        val s = settings.value
        val updated = s.copy(
            isAppLocked = false,
            trialModeEnabled = false,
            trialStartDate = 0L
        )
        settings.value = updated
        persistSettingsAsync(updated)
    }

    fun lockApp(reason: String = "Aplicación bloqueada por el Administrador.") {
        if (!requirePermitted("bloquear la aplicación") { it.canAccessAdminPanel }) return
        val s = settings.value
        val updated = s.copy(
            isAppLocked = true,
            appLockReason = reason
        )
        settings.value = updated
        persistSettingsAsync(updated)
    }

    fun updateSettings(newSettings: BusinessSettings) {
        if (!isUserAuthenticated.value) {
            lastSecurityError.value = "Debe iniciar sesión para modificar la configuración."
            return
        }
        val perms = RoleManager.getPermissions(settings.value)
        val hasAnyConfigPrivilege = perms.canAccessSettings ||
            perms.canConfigurePrinter ||
            perms.canEditReceiptHeaders ||
            perms.canAccessAdminPanel ||
            perms.canAccessJefePanel
        if (!hasAnyConfigPrivilege) {
            lastSecurityError.value = "Permiso denegado para modificar la configuración."
            return
        }
        val sanitized = RoleManager.sanitizeSettingsUpdate(settings.value, newSettings)
        settings.value = sanitized
        persistSettingsAsync(sanitized)
    }

    // Offline AES-256-GCM Backup, Export, Verification & Coordinated Restore
    fun buildDefaultBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "CajaMaestra_Respaldo_$stamp.cmbak"
    }

    suspend fun createFullBackupBytes(passphrase: String): Result<Pair<ByteArray, BackupCounts>> {
        if (!requirePermitted("generar respaldo cifrado") { it.canAccessCloudBackup }) {
            return Result.failure(SecurityException(lastSecurityError.value ?: "Permiso denegado."))
        }
        return withContext(Dispatchers.IO) {
            isCloudSyncing.value = true
            try {
                val result = backupManager.createEncryptedBackupBytes(passphrase)
                result.onSuccess { (bytes, counts) ->
                    encryptedBackupText.value = String(bytes, Charsets.UTF_8)
                    val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    cloudSyncMessage.value =
                        "Respaldo completo cifrado generado ($time): ${counts.totalRoomRecords} registros, configuración y bóveda."
                }.onFailure { err ->
                    cloudSyncMessage.value = err.message ?: "No se pudo generar el respaldo."
                }
                result
            } finally {
                isCloudSyncing.value = false
            }
        }
    }

    suspend fun saveLocalPrivateBackup(passphrase: String): BackupOperationResult {
        val creation = createFullBackupBytes(passphrase)
        val (bytes, counts) = creation.getOrElse { err ->
            return BackupOperationResult.Error(err.message ?: "Error al generar respaldo.")
        }
        val fileName = buildDefaultBackupFileName()
        return withContext(Dispatchers.IO) {
            val savedFile = backupManager.writeLocalArchiveFile(fileName, bytes).getOrElse { err ->
                cloudSyncMessage.value = "Error al guardar respaldo en almacenamiento local: ${err.message}"
                return@withContext BackupOperationResult.Error(cloudSyncMessage.value)
            }
            if (!savedFile.exists() || savedFile.length() != bytes.size.toLong()) {
                val msg = "No se pudo verificar la escritura del archivo en dispositivo."
                cloudSyncMessage.value = msg
                return@withContext BackupOperationResult.Error(msg)
            }
            lastExportedFilePath.value = savedFile.absolutePath
            val msg = "Respaldo guardado localmente (${savedFile.name}, ${bytes.size} bytes, ${counts.totalRoomRecords} registros)."
            cloudSyncMessage.value = msg
            BackupOperationResult.ExportSuccess(
                fileName = savedFile.name,
                sizeBytes = bytes.size,
                counts = counts,
                message = msg
            )
        }
    }

    suspend fun prepareShareableBackupFile(passphrase: String): Result<Pair<File, BackupCounts>> {
        val creation = createFullBackupBytes(passphrase)
        val (bytes, counts) = creation.getOrElse { return Result.failure(it) }
        val fileName = buildDefaultBackupFileName()
        return withContext(Dispatchers.IO) {
            backupManager.writeShareableBackupFile(fileName, bytes).map { file ->
                lastExportedFilePath.value = file.absolutePath
                cloudSyncMessage.value =
                    "Archivo cifrado listo para compartir (${file.name}, ${counts.totalRoomRecords} registros)."
                file to counts
            }
        }
    }

    fun recordExternalBackupExport(fileName: String, byteCount: Int, counts: BackupCounts) {
        lastExportedFilePath.value = fileName
        cloudSyncMessage.value =
            "Respaldo guardado en '$fileName' ($byteCount bytes, ${counts.totalRoomRecords} registros + configuración y bóveda)."
    }

    fun inspectSelectedBackupBytes(bytes: ByteArray, fileName: String = ""): BackupInspectionResult {
        val inspection = backupManager.inspectBackupBytes(bytes, fileName)
        selectedBackupInspection.value = inspection
        selectedBackupBytes.value = if (inspection.isValidForRestore) bytes else null
        return inspection
    }

    fun clearSelectedBackup() {
        selectedBackupInspection.value = null
        selectedBackupBytes.value = null
    }

    suspend fun executeFullRestore(
        bytes: ByteArray,
        passphrase: String,
        faultInjector: ((RestoreStage) -> Unit)? = null
    ): BackupOperationResult {
        val wasUninitializedInstall = !securityVault.isInitialized
        if (!wasUninitializedInstall) {
            if (!requirePermitted("restaurar respaldos") { it.canRestoreBackup }) {
                val err = BackupOperationResult.Error(
                    lastSecurityError.value ?: "Solo Dueño o Administrador pueden restaurar respaldos."
                )
                lastRestoreResult.value = err
                return err
            }
        }

        return withContext(Dispatchers.IO) {
            isCloudSyncing.value = true
            try {
                val res = backupManager.restoreFromBackupBytes(bytes, passphrase, faultInjector)
                lastRestoreResult.value = res
                when (res) {
                    is BackupOperationResult.RestoreSuccess -> {
                        val reloaded = settingsStorage.loadState()
                        securityVault = reloaded.vault
                        settings.value = reloaded.settings
                        refreshSecurityUiState()
                        migrateLegacyEmployeePins()
                        lastLoginError.value = null
                        lastSecurityError.value = null
                        currentScreen.value = RoleManager.getFirstPermittedScreen(reloaded.settings)
                        if (wasUninitializedInstall) {
                            // Never bypass authentication when restoring on a fresh device
                            isUserAuthenticated.value = false
                        }
                        clearSelectedBackup()
                        cloudSyncMessage.value = res.message
                    }
                    is BackupOperationResult.Error -> {
                        val reloaded = settingsStorage.loadState()
                        securityVault = reloaded.vault
                        settings.value = reloaded.settings
                        refreshSecurityUiState()
                        cloudSyncMessage.value = res.message
                    }
                    is BackupOperationResult.ExportSuccess -> Unit
                }
                res
            } finally {
                isCloudSyncing.value = false
            }
        }
    }

    fun generateEncryptedBackup(passphrase: String = ""): String {
        if (!requirePermitted("generar respaldo cifrado") { it.canAccessCloudBackup }) return ""
        val validationError = backupManager.validateExportPassphrase(passphrase)
        if (validationError != null) {
            lastSecurityError.value = validationError
            cloudSyncMessage.value = validationError
            return ""
        }
        val res = runBlocking(Dispatchers.IO) {
            backupManager.createEncryptedBackupBytes(passphrase)
        }
        return res.fold(
            onSuccess = { (bytes, counts) ->
                val text = String(bytes, Charsets.UTF_8)
                encryptedBackupText.value = text
                cloudSyncMessage.value =
                    "Respaldo completo cifrado generado (${counts.totalRoomRecords} registros)."
                text
            },
            onFailure = { err ->
                cloudSyncMessage.value = err.message ?: "Error al generar respaldo."
                ""
            }
        )
    }

    fun restoreEncryptedBackup(encryptedPayloadOrBase64: String, passphrase: String = ""): Boolean {
        val raw = encryptedPayloadOrBase64.trim()
        if (raw.isEmpty()) {
            cloudSyncMessage.value = "El respaldo está vacío."
            return false
        }
        val res = runBlocking(Dispatchers.IO) {
            executeFullRestore(raw.toByteArray(Charsets.UTF_8), passphrase)
        }
        return res is BackupOperationResult.RestoreSuccess
    }

    fun performManualCloudSync() {
        if (!requirePermitted("acceder a respaldos") { it.canAccessCloudBackup }) return
        cloudSyncMessage.value =
            "Caja Maestra opera 100% offline. Usa Respaldo Local para guardar o compartir archivos .cmbak cifrados."
        navigateTo(AppScreen.VINCULAR_WEB)
    }

    suspend fun wipeAllDataAsync(): Boolean {
        if (!requirePermitted("borrar toda la información") { it.canWipeAllData }) return false
        if (!wipeOperationInFlight.compareAndSet(false, true)) return false
        return try {
            withContext(Dispatchers.IO) {
                operationsRepo.wipeAllBusinessData()
            }
            clearCart()
            lastCompletedSale.value = null
            lastSaleItems.value = emptyList()
            showReceiptModal.value = false
            lastClosedShift.value = null
            showShiftCutModal.value = false
            selectedReportTitle.value = null
            true
        } finally {
            wipeOperationInFlight.set(false)
        }
    }

    fun wipeAllData() {
        if (!requirePermitted("borrar toda la información") { it.canWipeAllData }) return
        viewModelScope.launch(Dispatchers.IO) {
            wipeAllDataAsync()
        }
    }
}
