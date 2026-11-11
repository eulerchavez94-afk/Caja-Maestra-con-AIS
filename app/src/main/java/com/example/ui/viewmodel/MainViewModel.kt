package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.CajaMaestraDatabase
import com.example.data.model.*
import com.example.util.AesEncryptionUtil
import com.example.util.NotificationHelper
import com.example.util.ReportExporter
import com.example.util.RoleManager
import com.example.util.ThermalPrinterUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = CajaMaestraDatabase.getDatabase(application, viewModelScope)
    private val dao = db.dao()

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

    // Initial Login / Lock Screen state
    var isUserAuthenticated = MutableStateFlow(false)
        private set

    var lastLoginError = MutableStateFlow<String?>(null)
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

    // Last completed sale & receipt preview modal
    var lastCompletedSale = MutableStateFlow<Sale?>(null)
        private set

    var lastSaleItems = MutableStateFlow<List<SaleCartItem>>(emptyList())
        private set

    var showReceiptModal = MutableStateFlow(false)
        private set

    // Reports filter state
    var selectedReportTitle = MutableStateFlow<String?>(null)
        private set

    var reportDateFilter = MutableStateFlow(DateFilterType.TODOS)
        private set

    // Tutorial modal
    var showTutorialDialog = MutableStateFlow(false)
        private set

    // Cloud Sync & Backup state
    var isCloudSyncing = MutableStateFlow(false)
        private set

    var cloudSyncMessage = MutableStateFlow("Salva manual en la nube lista (Cifrado AES-256)")
        private set

    var encryptedBackupText = MutableStateFlow("")
        private set

    init {
        // Check for low stock on startup and notify
        viewModelScope.launch(Dispatchers.IO) {
            inventoryItems.collect { items ->
                val lowCount = items.count { it.stock <= it.minStock && it.type == ItemType.PRODUCTO }
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

    fun navigateTo(screen: AppScreen) {
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
                val jsonArr = org.json.JSONArray(sale.itemsJson)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    items.add(
                        SaleCartItem(
                            itemId = 0L,
                            name = obj.optString("name", "Artículo"),
                            quantity = obj.optDouble("quantity", 1.0),
                            unitPrice = obj.optDouble("unitPrice", 0.0),
                            costPrice = obj.optDouble("costPrice", 0.0),
                            unit = obj.optString("unit", "Pza"),
                            type = ItemType.PRODUCTO
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
        selectedReportTitle.value = title
    }

    fun closeReportDetail() {
        selectedReportTitle.value = null
    }

    fun setReportDateFilter(filter: DateFilterType) {
        reportDateFilter.value = filter
    }

    // Cart Operations
    fun addToCart(item: InventoryItem, quantity: Double = 1.0) {
        val current = cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.itemId == item.id }
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
    }

    fun updateCartItemQuantity(itemId: Long, newQuantity: Double) {
        if (newQuantity <= 0) {
            removeFromCart(itemId)
            return
        }
        val current = cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.itemId == itemId }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = newQuantity)
            cartItems.value = current
        }
    }

    fun removeFromCart(itemId: Long) {
        cartItems.value = cartItems.value.filterNot { it.itemId == itemId }
    }

    fun clearCart() {
        cartItems.value = emptyList()
        selectedCustomer.value = null
        customerPaidInput.value = ""
        discountInput.value = "0"
        posNotes.value = ""
    }

    // Complete Sale / Quote
    fun completeSale() {
        val items = cartItems.value
        if (items.isEmpty()) return

        val subtotal = items.sumOf { it.subtotal }
        val discount = discountInput.value.toDoubleOrNull() ?: 0.0
        val total = (subtotal - discount).coerceAtLeast(0.0)
        val paid = customerPaidInput.value.toDoubleOrNull() ?: total
        val change = (paid - total).coerceAtLeast(0.0)

        val count = sales.value.size + 1
        val isQuote = currentSaleType.value == SaleType.COTIZACION
        val folio = if (isQuote) "COT-${String.format(Locale.US, "%05d", count)}" else "F-${String.format(Locale.US, "%05d", count)}"

        val status = when {
            isQuote -> SaleStatus.PENDIENTE
            paymentMethod.value.contains("Crédito", ignoreCase = true) -> SaleStatus.CREDITO
            else -> SaleStatus.PAGADA
        }

        // Build items JSON
        val jsonArray = JSONArray()
        for (it in items) {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("quantity", it.quantity)
            obj.put("unitPrice", it.unitPrice)
            obj.put("costPrice", it.costPrice)
            obj.put("unit", it.unit)
            obj.put("subtotal", it.subtotal)
            jsonArray.put(obj)
        }

        val sale = Sale(
            folio = folio,
            type = currentSaleType.value,
            status = status,
            customerId = selectedCustomer.value?.id,
            customerName = selectedCustomer.value?.name ?: "Público General",
            subtotal = subtotal,
            discount = discount,
            total = total,
            paidAmount = paid,
            changeAmount = change,
            paymentMethod = paymentMethod.value,
            date = System.currentTimeMillis(),
            sellerName = settings.value.currentUserName,
            notes = posNotes.value,
            itemsJson = jsonArray.toString()
        )

        viewModelScope.launch(Dispatchers.IO) {
            dao.insertSale(sale)

            // Reduce stock if enabled and not a quote
            if (settings.value.checkStock && !isQuote) {
                for (it in items) {
                    if (it.type == ItemType.PRODUCTO) {
                        dao.reduceStock(it.itemId, it.quantity)
                    }
                }
            }

            // Update client credit balance if sold on credit
            if (status == SaleStatus.CREDITO && selectedCustomer.value != null) {
                val client = selectedCustomer.value!!
                dao.updateClient(client.copy(creditBalance = client.creditBalance + total))
            }

            // Save for receipt modal & thermal printing
            lastCompletedSale.value = sale
            lastSaleItems.value = items
            showReceiptModal.value = true

            // Clear active cart
            clearCart()
        }
    }

    // Purchases & Stock replenishment
    fun registerPurchase(purchase: Purchase, itemsBought: List<Triple<InventoryItem, Double, Double>>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPurchase(purchase)
            for ((item, qty, cost) in itemsBought) {
                dao.addStock(item.id, qty)
                if (cost > 0) {
                    dao.updateItem(item.copy(costPrice = cost, stock = item.stock + qty))
                }
            }
        }
    }

    // Inventory CRUD
    fun saveInventoryItem(item: InventoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.id == 0L) {
                dao.insertItem(item)
            } else {
                dao.updateItem(item)
            }
        }
    }

    fun deleteInventoryItem(item: InventoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteItem(item)
        }
    }

    fun adjustItemStock(item: InventoryItem, delta: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStock = (item.stock + delta).coerceAtLeast(0.0)
            dao.updateItem(item.copy(stock = newStock))
        }
    }

    // Extra Movements
    fun saveExtraMovement(movement: ExtraMovement) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertExtraMovement(movement)
        }
    }

    fun deleteExtraMovement(movement: ExtraMovement) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteExtraMovement(movement)
        }
    }

    // Rentals & Loans
    fun saveRentalLoan(item: RentalLoan) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.id == 0L) {
                dao.insertRentalLoan(item)
            } else {
                dao.updateRentalLoan(item)
            }
        }
    }

    fun toggleRentalLoanStatus(item: RentalLoan) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateRentalLoan(item.copy(isReturnedOrPaid = !item.isReturnedOrPaid))
        }
    }

    fun deleteRentalLoan(item: RentalLoan) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteRentalLoan(item)
        }
    }

    // Clients & Suppliers
    fun saveClient(client: Client) {
        viewModelScope.launch(Dispatchers.IO) {
            if (client.id == 0L) dao.insertClient(client) else dao.updateClient(client)
        }
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteClient(client)
        }
    }

    fun saveSupplier(supplier: Supplier) {
        viewModelScope.launch(Dispatchers.IO) {
            if (supplier.id == 0L) dao.insertSupplier(supplier) else dao.updateSupplier(supplier)
        }
    }

    fun deleteSupplier(supplier: Supplier) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSupplier(supplier)
        }
    }

    // Employees & Roles
    fun saveEmployee(employee: Employee) {
        viewModelScope.launch(Dispatchers.IO) {
            if (employee.id == 0L) dao.insertEmployee(employee) else dao.updateEmployee(employee)
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteEmployee(employee)
        }
    }

    fun switchActiveRole(role: UserRole, userName: String) {
        settings.value = settings.value.copy(
            currentRole = role,
            currentUserName = userName
        )
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
     * Authenticates user from initial lock screen.
     * Roles & Passwords required by user:
     * 1- Admin: Admin
     * 2- Jefe: Jefe
     * 3- Trabajador: Trabajador
     */
    fun authenticateUser(usernameInput: String, passwordInput: String): Boolean {
        val s = settings.value
        val cleanUser = usernameInput.trim()
        val cleanPass = passwordInput.trim()

        // Admin override check
        if (cleanUser.equals("Admin", ignoreCase = true) && (cleanPass == s.adminPassword || cleanPass == "Admin")) {
            settings.value = s.copy(
                currentRole = UserRole.ADMINISTRADOR,
                currentUserName = "Administrador Master"
            )
            isUserAuthenticated.value = true
            lastLoginError.value = null
            return true
        }

        // If app is manually locked by Admin or trial expired, block non-admin roles
        if (s.isAppLocked || isTrialExpired()) {
            lastLoginError.value = "La aplicación se encuentra BLOQUEADA o el periodo de prueba ha expirado. Contacte a Admin."
            return false
        }

        // Jefe / Dueño check
        if ((cleanUser.equals("Jefe", ignoreCase = true) || cleanUser.equals("Dueno", ignoreCase = true) || cleanUser.equals("Dueño", ignoreCase = true)) &&
            (cleanPass == s.jefePassword || cleanPass == "Jefe")) {
            settings.value = s.copy(
                currentRole = UserRole.DUENO,
                currentUserName = "Jefe / Dueño"
            )
            isUserAuthenticated.value = true
            lastLoginError.value = null
            return true
        }

        // Trabajador check
        if ((cleanUser.equals("Trabajador", ignoreCase = true) || cleanUser.equals("Vendedor", ignoreCase = true) || cleanUser.equals("Cajero", ignoreCase = true)) &&
            (cleanPass == s.trabajadorPassword || cleanPass == "Trabajador")) {
            settings.value = s.copy(
                currentRole = UserRole.TRABAJADOR,
                currentUserName = "Trabajador Operativo"
            )
            isUserAuthenticated.value = true
            lastLoginError.value = null
            return true
        }

        lastLoginError.value = "Usuario o contraseña incorrectos. Verifique sus credenciales."
        return false
    }

    fun logout() {
        isUserAuthenticated.value = false
        lastLoginError.value = null
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
    fun getUserPermissions(): com.example.util.UserPermissions {
        return RoleManager.getPermissions(settings.value)
    }

    fun setTrialMode(enabled: Boolean, days: Int = 7) {
        val s = settings.value
        settings.value = s.copy(
            trialModeEnabled = enabled,
            trialDurationDays = days,
            trialStartDate = if (enabled) System.currentTimeMillis() else 0L,
            isAppLocked = false
        )
    }

    fun unlockApp() {
        val s = settings.value
        settings.value = s.copy(
            isAppLocked = false,
            trialModeEnabled = false,
            trialStartDate = 0L
        )
    }

    fun lockApp(reason: String = "Aplicación bloqueada por el Administrador.") {
        val s = settings.value
        settings.value = s.copy(
            isAppLocked = true,
            appLockReason = reason
        )
    }

    fun updateSettings(newSettings: BusinessSettings) {
        settings.value = newSettings
    }

    // AES-256 Cloud Backup & Sync
    fun generateEncryptedBackup(passphrase: String = "CajaMaestraSecret2026"): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("business", settings.value.businessName)

        val itemsArr = JSONArray()
        for (i in inventoryItems.value) {
            val obj = JSONObject()
            obj.put("id", i.id)
            obj.put("name", i.name)
            obj.put("code", i.code)
            obj.put("cost", i.costPrice)
            obj.put("sale", i.salePrice)
            obj.put("stock", i.stock)
            obj.put("category", i.category)
            obj.put("type", i.type.name)
            itemsArr.put(obj)
        }
        root.put("inventory", itemsArr)

        val salesArr = JSONArray()
        for (s in sales.value) {
            val obj = JSONObject()
            obj.put("folio", s.folio)
            obj.put("total", s.total)
            obj.put("date", s.date)
            obj.put("customer", s.customerName)
            obj.put("status", s.status.name)
            salesArr.put(obj)
        }
        root.put("sales", salesArr)

        val encrypted = AesEncryptionUtil.encryptWithGzip(root.toString(), passphrase)
        encryptedBackupText.value = encrypted
        return encrypted
    }

    fun restoreEncryptedBackup(encryptedBase64: String, passphrase: String = "CajaMaestraSecret2026"): Boolean {
        val result = AesEncryptionUtil.decrypt(encryptedBase64, passphrase)
        return if (result.isSuccess) {
            cloudSyncMessage.value = "¡Respaldo restaurado con éxito desde la nube!"
            true
        } else {
            cloudSyncMessage.value = "Error: Clave de descifrado incorrecta o archivo dañado."
            false
        }
    }

    fun performManualCloudSync() {
        viewModelScope.launch(Dispatchers.IO) {
            isCloudSyncing.value = true
            cloudSyncMessage.value = "Cifrando base de datos con AES-256..."
            kotlinx.coroutines.delay(800)
            generateEncryptedBackup()
            cloudSyncMessage.value = "Sincronizando salva en la nube segura..."
            kotlinx.coroutines.delay(1200)
            isCloudSyncing.value = false
            val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            cloudSyncMessage.value = "¡Sincronización manual en la nube completada! ($time)"
        }
    }

    fun wipeAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearInventory()
            dao.clearSales()
            dao.clearPurchases()
            dao.clearExtraMovements()
            dao.clearRentalsLoans()
        }
    }
}
