package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupDataset
import com.example.data.backup.BackupOperationResult
import com.example.data.backup.BackupSerializer
import com.example.data.db.CajaMaestraDatabase
import com.example.data.model.*
import com.example.data.prefs.SettingsStorage
import com.example.data.repository.BusinessOperationsRepository
import com.example.data.repository.PurchaseLineInput
import com.example.data.repository.RepoPurchaseResult
import com.example.data.repository.RepoSaleResult
import com.example.ui.viewmodel.InitialSecuritySetupResult
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PurchaseRegistrationResult
import com.example.ui.viewmodel.SaleCheckoutResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseIntegrityPhase3Test {

    private lateinit var app: Application

    @Before
    fun setUpClean() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            SettingsStorage.getInstance(app).clearAllForTest()
            clearAllTablesOnSingletonDb()
            File(app.filesDir, "backup_journal").deleteRecursively()
            File(app.filesDir, "backups").deleteRecursively()
            File(app.filesDir, "db_safety_backup").deleteRecursively()
            File(app.cacheDir, "backups").deleteRecursively()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            SettingsStorage.getInstance(app).clearAllForTest()
            clearAllTablesOnSingletonDb()
        }
    }

    private suspend fun clearAllTablesOnSingletonDb() {
        val db = CajaMaestraDatabase.getDatabase(app, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
        val dao = db.dao()
        dao.clearCashShiftMovements()
        dao.clearCashShifts()
        dao.clearSales()
        dao.clearPurchases()
        dao.clearExtraMovements()
        dao.clearRentalsLoans()
        dao.clearClients()
        dao.clearSuppliers()
        dao.clearEmployees()
        dao.clearInventory()
        dao.clearFolioSequences()
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun createInitializedOwnerViewModel(): MainViewModel {
        val vm = MainViewModel(app)
        vm.awaitInitialized()
        if (!vm.securityState.value.isInitialized) {
            val res = vm.initializeSecuritySetup(
                adminPassword = "AdminPhase3#2026",
                jefePassword = "DuenoPhase3#2026",
                trabajadorPassword = "CajaPhase3#2026",
                masterPin = "8294",
                recoveryQuestion = "Ciudad de origen?",
                recoveryAnswer = "Monterrey"
            )
            assertTrue(res is InitialSecuritySetupResult.Success)
            vm.awaitPersistence()
        }
        assertTrue(vm.authenticateUser("Dueño", "DuenoPhase3#2026"))
        vm.awaitPersistence()
        idleMainLooper()
        return vm
    }

    private suspend fun getDao() =
        CajaMaestraDatabase.getDatabase(app, kotlinx.coroutines.CoroutineScope(Dispatchers.IO)).dao()

    // 1. Venta normal + parsing real en openSaleReceipt
    @Test
    fun `01 normal sale completes atomically, reduces stock, assigns monotonic folio, and openSaleReceipt preserves itemId and type`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val manId = dao.insertItem(
            InventoryItem(
                name = "Combo Emprendedor",
                code = "MAN-301",
                costPrice = 300.0,
                salePrice = 550.0,
                stock = 10.0,
                type = ItemType.MANUFACTURA
            )
        )
        assertNotNull(vm.openCashRegisterAsync(500.0, "Turno mañana"))

        val item = dao.getInventoryItemByIdSync(manId)!!
        assertTrue(vm.addToCart(item, 2.0))
        vm.setPaymentMethod("Efectivo")
        vm.setCustomerPaidInput("1200")

        val result = vm.completeSaleAsync()
        assertTrue(result is SaleCheckoutResult.Success)

        val sale = dao.getAllSalesSync().single()
        assertEquals("F-00001", sale.folio)
        assertEquals(1100.0, sale.total, 0.001)
        assertEquals(1200.0, sale.paidAmount, 0.001)
        assertEquals(100.0, sale.changeAmount, 0.001)
        assertEquals(SaleStatus.PAGADA, sale.status)

        // Stock decremented atomically
        assertEquals(8.0, dao.getInventoryItemByIdSync(manId)!!.stock, 0.001)

        // openSaleReceipt must preserve real itemId & ItemType.MANUFACTURA (not hardcoded 0L / PRODUCTO)
        vm.openSaleReceipt(sale)
        val parsed = vm.lastSaleItems.value.single()
        assertEquals(manId, parsed.itemId)
        assertEquals(ItemType.MANUFACTURA, parsed.type)
        assertEquals(2.0, parsed.quantity, 0.001)
    }

    // 2. Venta con múltiples productos (PRODUCTO + MANUFACTURA + SERVICIO)
    @Test
    fun `02 multi-product sale updates physical stock only and skips service stock`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(
            InventoryItem(name = "Café 500g", code = "P-1", costPrice = 80.0, salePrice = 150.0, stock = 20.0, type = ItemType.PRODUCTO)
        )
        val kitId = dao.insertItem(
            InventoryItem(name = "Kit Barista", code = "M-1", costPrice = 200.0, salePrice = 400.0, stock = 5.0, type = ItemType.MANUFACTURA)
        )
        val srvId = dao.insertItem(
            InventoryItem(name = "Calibración", code = "S-1", costPrice = 0.0, salePrice = 250.0, stock = 0.0, type = ItemType.SERVICIO)
        )

        assertNotNull(vm.openCashRegisterAsync(300.0))

        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 3.0))
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(kitId)!!, 2.0))
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(srvId)!!, 1.0))
        vm.setPaymentMethod("Tarjeta")

        val res = vm.completeSaleAsync()
        assertTrue(res is SaleCheckoutResult.Success)

        val sale = dao.getAllSalesSync().single()
        assertEquals(1500.0, sale.total, 0.001)
        assertEquals(17.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
        assertEquals(3.0, dao.getInventoryItemByIdSync(kitId)!!.stock, 0.001)
        assertEquals(0.0, dao.getInventoryItemByIdSync(srvId)!!.stock, 0.001)

        vm.openSaleReceipt(sale)
        val receiptItems = vm.lastSaleItems.value
        assertEquals(3, receiptItems.size)
        assertEquals(ItemType.PRODUCTO, receiptItems.first { it.itemId == prodId }.type)
        assertEquals(ItemType.MANUFACTURA, receiptItems.first { it.itemId == kitId }.type)
        assertEquals(ItemType.SERVICIO, receiptItems.first { it.itemId == srvId }.type)
    }

    // 3. Doble pulsación en Cobrar Venta
    @Test
    fun `03 rapid double-tap on completeSaleAsync with same operationId creates strictly one sale and one stock deduction`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(
            InventoryItem(name = "Termo Acero", code = "T-1", costPrice = 100.0, salePrice = 220.0, stock = 10.0)
        )
        assertNotNull(vm.openCashRegisterAsync(200.0))
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 2.0))

        val opId = vm.activeCheckoutOperationId.value
        val results = listOf(
            async(Dispatchers.Default) { vm.completeSaleAsync(opId) },
            async(Dispatchers.Default) { vm.completeSaleAsync(opId) },
            async(Dispatchers.Default) { vm.completeSaleAsync(opId) }
        ).awaitAll()

        assertEquals(1, results.count { it is SaleCheckoutResult.Success })
        assertEquals(1, dao.getAllSalesSync().size)
        assertEquals(8.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
    }

    // 4. Múltiples ventas consecutivas concurrentes + validación de stock límite
    @Test
    fun `04 concurrent sales serialize atomically with unique folios and reject oversell`() = runBlocking {
        val inMemDb = Room.inMemoryDatabaseBuilder(app, CajaMaestraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = inMemDb.dao()
            dao.insertCashShift(CashRegisterShift(cashierName = "Cajero", openingFloat = 500.0, isOpen = true))
            val prodId = dao.insertItem(
                InventoryItem(name = "Producto Escaso", code = "ESC-1", costPrice = 10.0, salePrice = 25.0, stock = 5.0)
            )
            val repo = BusinessOperationsRepository(inMemDb) { BusinessSettings(checkStock = true) }
            val line = listOf(SaleCartItem(prodId, "Producto Escaso", 2.0, 25.0, 10.0, "Pza", ItemType.PRODUCTO))

            // 4 concurrent buyers each wanting 2 units from stock=5 -> max 2 succeed (4 units sold), 2 fail cleanly!
            val outcomes = (1..4).map { idx ->
                async(Dispatchers.IO) {
                    repo.completeSale(
                        items = line,
                        saleType = SaleType.VENTA,
                        selectedCustomerId = null,
                        paymentMethod = "Efectivo",
                        customerPaidInput = "50",
                        discountInput = "0",
                        notes = "Concurrente $idx",
                        operationId = "concurrent-sale-$idx"
                    )
                }
            }.awaitAll()

            val successes = outcomes.filterIsInstance<RepoSaleResult.Committed>()
            val rejected = outcomes.filterIsInstance<RepoSaleResult.InsufficientStock>()
            assertEquals(2, successes.size)
            assertEquals(2, rejected.size)

            val persistedSales = dao.getAllSalesSync()
            assertEquals(2, persistedSales.size)
            val folios = persistedSales.map { it.folio }.toSet()
            assertEquals(setOf("F-00001", "F-00002"), folios)
            assertEquals(1.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
        } finally {
            inMemDb.close()
        }
    }

    // 5. Registro de compra con varios productos + itemsJson completo
    @Test
    fun `05 multi-product purchase persists full itemsJson, atomic OC folio, and adds stock once without double increment`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val supId = dao.insertSupplier(Supplier(name = "Importadora del Valle"))
        val p1Id = dao.insertItem(InventoryItem(name = "Grano Arábica", code = "G-1", costPrice = 100.0, salePrice = 180.0, stock = 10.0))
        val p2Id = dao.insertItem(InventoryItem(name = "Filtro V60", code = "F-1", costPrice = 40.0, salePrice = 90.0, stock = 5.0))

        val item1 = dao.getInventoryItemByIdSync(p1Id)!!
        val item2 = dao.getInventoryItemByIdSync(p2Id)!!

        val res = vm.registerPurchaseAsync(
            supplierId = supId,
            fallbackSupplierName = "Importadora del Valle",
            itemsBought = listOf(
                Triple(item1, 10.0, 120.0),
                Triple(item2, 15.0, 60.0)
            ),
            isCredit = true,
            notes = "Factura A-992",
            operationId = "purchase-op-multi-1"
        )

        assertTrue(res is PurchaseRegistrationResult.Success)
        val savedPurchase = (res as PurchaseRegistrationResult.Success).purchase
        assertEquals("OC-00001", savedPurchase.folio)
        assertEquals(10.0 * 120.0 + 15.0 * 60.0, savedPurchase.total, 0.001)
        assertTrue(savedPurchase.itemsJson.isNotBlank())

        val parsedLines = JSONArray(savedPurchase.itemsJson)
        assertEquals(2, parsedLines.length())
        assertEquals(p1Id, parsedLines.getJSONObject(0).getLong("itemId"))
        assertEquals(10.0, parsedLines.getJSONObject(0).getDouble("quantity"), 0.001)
        assertEquals(p2Id, parsedLines.getJSONObject(1).getLong("itemId"))

        // Crucial regression check: stock incremented ONCE (10+10=20, 5+15=20), NOT doubled (30, 35)!
        assertEquals(20.0, dao.getInventoryItemByIdSync(p1Id)!!.stock, 0.001)
        assertEquals(20.0, dao.getInventoryItemByIdSync(p2Id)!!.stock, 0.001)
    }

    // 6. Cálculo de costo promedio ponderado vs último costo
    @Test
    fun `06 purchase respects useAverageCost weighted formula vs last cost overwrite`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        // Existing: 10 units @ $100 -> new batch: 10 units @ $200 -> weighted average = $150
        val avgProdId = dao.insertItem(InventoryItem(name = "Molino", code = "M-10", costPrice = 100.0, salePrice = 300.0, stock = 10.0))
        vm.updateSettings(vm.settings.value.copy(useAverageCost = true))
        val avgRes = vm.registerPurchaseAsync(
            supplierId = null,
            fallbackSupplierName = "Proveedor",
            itemsBought = listOf(Triple(dao.getInventoryItemByIdSync(avgProdId)!!, 10.0, 200.0)),
            isCredit = false,
            notes = "Avg cost test"
        )
        assertTrue(avgRes is PurchaseRegistrationResult.Success)
        val afterAvg = dao.getInventoryItemByIdSync(avgProdId)!!
        assertEquals(20.0, afterAvg.stock, 0.001)
        assertEquals(150.0, afterAvg.costPrice, 0.001)

        // When useAverageCost = false -> costPrice becomes last purchase cost ($200)
        val lastProdId = dao.insertItem(InventoryItem(name = "Prensa", code = "P-20", costPrice = 100.0, salePrice = 300.0, stock = 10.0))
        vm.updateSettings(vm.settings.value.copy(useAverageCost = false))
        val lastRes = vm.registerPurchaseAsync(
            supplierId = null,
            fallbackSupplierName = "Proveedor",
            itemsBought = listOf(Triple(dao.getInventoryItemByIdSync(lastProdId)!!, 10.0, 200.0)),
            isCredit = false,
            notes = "Last cost test"
        )
        assertTrue(lastRes is PurchaseRegistrationResult.Success)
        val afterLast = dao.getInventoryItemByIdSync(lastProdId)!!
        assertEquals(20.0, afterLast.stock, 0.001)
        assertEquals(200.0, afterLast.costPrice, 0.001)
    }

    // 7. Rechazo de compra de servicios o mermas
    @Test
    fun `07 purchase rejects SERVICIO and waste items cleanly without mutating DB`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val srvId = dao.insertItem(InventoryItem(name = "Instalación", code = "S-9", costPrice = 50.0, salePrice = 300.0, stock = 0.0, type = ItemType.SERVICIO))
        val wasteId = dao.insertItem(InventoryItem(name = "Taza Rota", code = "W-9", costPrice = 30.0, salePrice = 0.0, stock = 2.0, type = ItemType.PRODUCTO, isWaste = true))

        val srvRes = vm.registerPurchaseAsync(
            supplierId = null,
            fallbackSupplierName = "Prov",
            itemsBought = listOf(Triple(dao.getInventoryItemByIdSync(srvId)!!, 5.0, 50.0)),
            isCredit = false,
            notes = ""
        )
        assertTrue(srvRes is PurchaseRegistrationResult.Error)

        val wasteRes = vm.registerPurchaseAsync(
            supplierId = null,
            fallbackSupplierName = "Prov",
            itemsBought = listOf(Triple(dao.getInventoryItemByIdSync(wasteId)!!, 3.0, 30.0)),
            isCredit = false,
            notes = ""
        )
        assertTrue(wasteRes is PurchaseRegistrationResult.Error)

        assertTrue(dao.getAllPurchasesSync().isEmpty())
        assertEquals(0.0, dao.getInventoryItemByIdSync(srvId)!!.stock, 0.001)
        assertEquals(2.0, dao.getInventoryItemByIdSync(wasteId)!!.stock, 0.001)
    }

    // 8 & 9. Anulación de venta y anulación repetida (idempotencia + actualización de corte cerrado)
    @Test
    fun `08 and 09 cancelSaleAsync restores stock and recalculates closed shift totals strictly once even on repeated calls`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(InventoryItem(name = "Filtro Papel", code = "FP-1", costPrice = 20.0, salePrice = 50.0, stock = 10.0))
        assertNotNull(vm.openCashRegisterAsync(100.0))
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 4.0))
        vm.setPaymentMethod("Efectivo")
        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)

        val sale = dao.getAllSalesSync().single()
        assertEquals(6.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)

        // Close shift before voiding sale -> expectedCash = 100 + 200 = 300
        val closedShift = vm.closeCashRegisterAsync(countedCash = 300.0)!!
        assertEquals(300.0, closedShift.expectedCash, 0.001)
        assertEquals(1, closedShift.salesCount)

        // Repeated / concurrent void calls must restore stock strictly ONCE and recalculate closed shift
        val cancelResults = listOf(
            async(Dispatchers.IO) { vm.cancelSaleAsync(sale) },
            async(Dispatchers.IO) { vm.cancelSaleAsync(sale) }
        ).awaitAll()

        assertEquals(1, cancelResults.count { it })
        assertEquals(SaleStatus.CANCELADA, dao.getSaleByIdSync(sale.id)!!.status)
        assertEquals(10.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)

        val recalculatedShift = dao.getCashShiftByIdSync(closedShift.id)!!
        assertEquals(0, recalculatedShift.salesCount)
        assertEquals(0.0, recalculatedShift.cashSalesTotal, 0.001)
        assertEquals(100.0, recalculatedShift.expectedCash, 0.001)
    }

    // 10 & 11. Apertura, movimientos de efectivo y cierre de caja
    @Test
    fun `10 and 11 cash register open prevents duplicate shift and tracks entries, withdrawals, and close audit`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val s1 = vm.openCashRegisterAsync(500.0, "Apertura")
        val s2 = vm.openCashRegisterAsync(999.0, "Duplicada")
        assertNotNull(s1)
        assertNull(s2)

        assertTrue(vm.registerCashShiftMovementAsync(CashShiftMovementType.ENTRADA, 150.0, "Cambio en monedas"))
        assertTrue(vm.registerCashShiftMovementAsync(CashShiftMovementType.RETIRO, 80.0, "Pago mensajería"))
        assertFalse(vm.registerCashShiftMovementAsync(CashShiftMovementType.RETIRO, -10.0, "Negativo"))

        // Expected cash = 500 + 150 - 80 = 570
        val closed = vm.closeCashRegisterAsync(countedCash = 565.0, nextTurnFloat = 300.0, closingNotes = "Cierre OK")!!
        assertFalse(closed.isOpen)
        assertEquals(570.0, closed.expectedCash, 0.001)
        assertEquals(565.0, closed.countedCash, 0.001)
        assertEquals(-5.0, closed.difference, 0.001)
        assertNull(dao.getOpenCashShiftSync())
    }

    // 12. Venta a crédito y anulación con clientes consultados directo en Room (sin StateFlow activo)
    @Test
    fun `12 credit sale updates client balance and void reverses it even with zero StateFlow observers`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val clientId = dao.insertClient(Client(name = "Panadería La Espiga", creditBalance = 100.0))
        val prodId = dao.insertItem(InventoryItem(name = "Harina 10kg", code = "H-10", costPrice = 120.0, salePrice = 200.0, stock = 15.0))
        assertNotNull(vm.openCashRegisterAsync(200.0))

        // Note: vm.clients.value is empty because SharingStarted.WhileSubscribed has no collectors!
        assertTrue(vm.clients.value.isEmpty())

        val client = dao.getClientByIdSync(clientId)!!
        val prod = dao.getInventoryItemByIdSync(prodId)!!
        vm.setSelectedCustomer(client)
        vm.setPaymentMethod("Crédito")
        assertTrue(vm.addToCart(prod, 2.0))

        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)
        assertEquals(500.0, dao.getClientByIdSync(clientId)!!.creditBalance, 0.001)

        val creditSale = dao.getAllSalesSync().single()
        assertTrue(vm.cancelSaleAsync(creditSale))
        assertEquals(100.0, dao.getClientByIdSync(clientId)!!.creditBalance, 0.001)
        // Repeating void cannot subtract credit a second time
        assertFalse(vm.cancelSaleAsync(creditSale))
        assertEquals(100.0, dao.getClientByIdSync(clientId)!!.creditBalance, 0.001)
    }

    // 13. Prevención de duplicados mediante operationId en ventas y compras
    @Test
    fun `13 duplicate operationId is rejected idempotently for sequential sale and purchase replays`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(InventoryItem(name = "Vaso", code = "V-1", costPrice = 10.0, salePrice = 30.0, stock = 20.0))
        assertNotNull(vm.openCashRegisterAsync(100.0))

        // Replay sale with exact same operationId
        val saleOp = "fixed-sale-op-${UUID.randomUUID()}"
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 2.0))
        assertTrue(vm.completeSaleAsync(operationId = saleOp) is SaleCheckoutResult.Success)

        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 2.0))
        val replaySale = vm.completeSaleAsync(operationId = saleOp)
        assertTrue(replaySale is SaleCheckoutResult.AlreadyProcessing)
        assertEquals(1, dao.getAllSalesSync().size)
        assertEquals(18.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)

        // Replay purchase with exact same operationId
        val purchaseOp = "fixed-purchase-op-${UUID.randomUUID()}"
        val prod = dao.getInventoryItemByIdSync(prodId)!!
        assertTrue(vm.registerPurchaseAsync(null, "Prov", listOf(Triple(prod, 5.0, 12.0)), false, "", operationId = purchaseOp) is PurchaseRegistrationResult.Success)
        val replayPurchase = vm.registerPurchaseAsync(null, "Prov", listOf(Triple(prod, 5.0, 12.0)), false, "", operationId = purchaseOp)
        assertTrue(replayPurchase is PurchaseRegistrationResult.AlreadyProcessing)
        assertEquals(1, dao.getAllPurchasesSync().size)
        assertEquals(23.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
    }

    // 14. Transacción interrumpida a la mitad / rollback completo
    @Test
    fun `14 mid-transaction failure rolls back sale, stock reduction, and folio sequence atomically`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val clientId = dao.insertClient(Client(name = "Cliente Rollback", creditBalance = 50.0))
        val prodId = dao.insertItem(InventoryItem(name = "Báscula", code = "B-1", costPrice = 400.0, salePrice = 800.0, stock = 6.0))
        assertNotNull(vm.openCashRegisterAsync(100.0))

        vm.setSelectedCustomer(dao.getClientByIdSync(clientId)!!)
        vm.setPaymentMethod("Crédito")
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 2.0))

        var rolledBack = false
        try {
            vm.completeSaleAsync(
                operationId = "tx-fail-1",
                faultHookAfterSaleInsert = { throw IllegalStateException("Fallo simulado a mitad de transacción") }
            )
        } catch (_: IllegalStateException) {
            rolledBack = true
        }
        assertTrue(rolledBack)

        // Nothing persisted partially!
        assertTrue(dao.getAllSalesSync().isEmpty())
        assertEquals(6.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
        assertEquals(50.0, dao.getClientByIdSync(clientId)!!.creditBalance, 0.001)
        assertNull(dao.getFolioSequenceSync(CajaMaestraDatabase.KEY_VENTA))

        // Retrying without fault with same operationId succeeds cleanly and gets F-00001
        val retry = vm.completeSaleAsync(operationId = "tx-fail-1")
        assertTrue(retry is SaleCheckoutResult.Success)
        assertEquals("F-00001", dao.getAllSalesSync().single().folio)
        assertEquals(4.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
    }

    // 15. Borrado completo de las 10 tablas + conservación de settings, vault y folios
    @Test
    fun `15 wipeAllDataAsync clears all 10 tables including clients, suppliers, employees while keeping settings, vault and folio sequences`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(InventoryItem(name = "Producto Wipe", code = "W-1", costPrice = 10.0, salePrice = 20.0, stock = 5.0))
        val clientId = dao.insertClient(Client(name = "Cliente Wipe"))
        dao.insertSupplier(Supplier(name = "Proveedor Wipe"))
        dao.insertEmployee(Employee(name = "Empleado Wipe", role = UserRole.TRABAJADOR, pin = ""))
        dao.insertExtraMovement(ExtraMovement(type = MovementType.GASTO, concept = "Renta", category = "Local", amount = 500.0))
        dao.insertRentalLoan(RentalLoan(isRental = true, contactName = "Ana", itemOrReason = "Silla", amount = 100.0))
        val shift = vm.openCashRegisterAsync(100.0)!!
        assertTrue(vm.registerCashShiftMovementAsync(CashShiftMovementType.ENTRADA, 50.0, "Cambio"))
        vm.setSelectedCustomer(dao.getClientByIdSync(clientId))
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 1.0))
        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)
        assertTrue(vm.registerPurchaseAsync(null, "Prov", listOf(Triple(dao.getInventoryItemByIdSync(prodId)!!, 2.0, 10.0)), false, "") is PurchaseRegistrationResult.Success)

        vm.updateSettings(vm.settings.value.copy(businessName = "Negocio Persistente"))
        vm.awaitPersistence()

        assertTrue(vm.wipeAllDataAsync())

        // All 10 operational tables must be empty (0 rows)
        assertEquals(0, dao.getAllInventoryItemsSync().size)
        assertEquals(0, dao.getAllSalesSync().size)
        assertEquals(0, dao.getAllPurchasesSync().size)
        assertEquals(0, dao.getAllExtraMovementsSync().size)
        assertEquals(0, dao.getAllRentalsLoansSync().size)
        assertEquals(0, dao.getAllClientsSync().size)
        assertEquals(0, dao.getAllSuppliersSync().size)
        assertEquals(0, dao.getAllEmployeesSync().size)
        assertEquals(0, dao.getAllCashShiftsSync().size)
        assertEquals(0, dao.getAllShiftMovementsSync().size)

        // Transient UI state cleared
        assertNull(vm.lastCompletedSale.value)
        assertFalse(vm.showReceiptModal.value)
        assertTrue(vm.cartItems.value.isEmpty())

        // BusinessSettings & SecurityVault preserved
        assertEquals("Negocio Persistente", vm.settings.value.businessName)
        assertTrue(vm.securityState.value.isInitialized)
        assertTrue(vm.verifyMasterPin("8294"))

        // Historical folio sequence preserved so wiped folios are NOT reused!
        val seqVenta = dao.getFolioSequenceSync(CajaMaestraDatabase.KEY_VENTA)
        assertEquals(1L, seqVenta?.lastNumber)
        val newProdId = dao.insertItem(InventoryItem(name = "Nuevo Post Wipe", code = "NP-1", costPrice = 10.0, salePrice = 30.0, stock = 5.0))
        assertNotNull(vm.openCashRegisterAsync(100.0))
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(newProdId)!!, 1.0))
        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)
        assertEquals("F-00002", dao.getAllSalesSync().single().folio)
    }

    // 16. Persistencia tras reinicio de ViewModel
    @Test
    fun `16 sales, purchases, stocks, and folio sequences persist across ViewModel restart`() = runBlocking {
        val vm1 = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(InventoryItem(name = "Leche 1L", code = "L-1", costPrice = 18.0, salePrice = 28.0, stock = 10.0))
        assertNotNull(vm1.openCashRegisterAsync(250.0))
        assertTrue(vm1.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 3.0))
        assertTrue(vm1.completeSaleAsync() is SaleCheckoutResult.Success) // stock=7, folio F-00001
        assertTrue(
            vm1.registerPurchaseAsync(
                null,
                "Lechera",
                listOf(Triple(dao.getInventoryItemByIdSync(prodId)!!, 5.0, 20.0)),
                false,
                ""
            ) is PurchaseRegistrationResult.Success
        ) // stock=12, folio OC-00001

        // Recreate MainViewModel simulating cold process restart
        val vm2 = MainViewModel(app)
        vm2.awaitInitialized()
        assertTrue(vm2.authenticateUser("Dueño", "DuenoPhase3#2026"))
        vm2.awaitPersistence()

        assertEquals(12.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
        assertEquals("F-00001", dao.getAllSalesSync().single().folio)
        assertEquals("OC-00001", dao.getAllPurchasesSync().single().folio)

        assertTrue(vm2.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 1.0))
        assertTrue(vm2.completeSaleAsync() is SaleCheckoutResult.Success)
        assertEquals("F-00002", dao.getAllSalesSync().maxByOrNull { it.id }!!.folio)
    }

    // 17. Compatibilidad con respaldos de Fase 2 (v4 + restauración de respaldo v3 anterior)
    @Test
    fun `17 Phase 2 backups preserve folio sequences in v4 and safely restore older v3 backups`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(InventoryItem(name = "Taza", code = "TZ-1", costPrice = 30.0, salePrice = 70.0, stock = 10.0))
        assertNotNull(vm.openCashRegisterAsync(100.0))
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 1.0))
        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)
        val firstSale = dao.getAllSalesSync().single()
        assertTrue(vm.cancelSaleAsync(firstSale))
        // Delete sale manually from DB while folio_sequences still remembers lastNumber=1
        dao.deleteSale(dao.getSaleByIdSync(firstSale.id)!!)

        val pass = "BackupPhase3#2026"
        val (v4Bytes, _) = vm.createFullBackupBytes(pass).getOrThrow()

        // Wipe and restore v4 backup -> folio sequence = 1 MUST be restored!
        SettingsStorage.getInstance(app).clearAllForTest()
        clearAllTablesOnSingletonDb()
        val vmRestored = MainViewModel(app)
        vmRestored.awaitInitialized()
        val restoreV4 = vmRestored.executeFullRestore(v4Bytes, pass)
        assertTrue(restoreV4 is BackupOperationResult.RestoreSuccess)
        assertEquals(1L, dao.getFolioSequenceSync(CajaMaestraDatabase.KEY_VENTA)?.lastNumber)

        // Also test backward compatibility: restoring an older Room v3 backup (no folioSequences key)
        val storedState = SettingsStorage.getInstance(app).loadState()
        val v3Dataset = BackupDataset(
            formatVersion = 2,
            roomSchemaVersion = 3,
            createdAt = System.currentTimeMillis(),
            inventory = listOf(InventoryItem(id = 10L, name = "Pan", code = "P10", costPrice = 5.0, salePrice = 12.0, stock = 8.0)),
            sales = listOf(
                Sale(id = 10L, folio = "F-00088", type = SaleType.VENTA, status = SaleStatus.PAGADA, subtotal = 12.0, total = 12.0)
            ),
            purchases = listOf(
                Purchase(id = 10L, folio = "OC-00042", total = 50.0)
            ),
            extraMovements = emptyList(),
            rentalsLoans = emptyList(),
            clients = emptyList(),
            suppliers = emptyList(),
            employees = emptyList(),
            cashShifts = emptyList(),
            cashShiftMovements = emptyList(),
            businessSettings = storedState.settings,
            securityVault = storedState.vault,
            folioSequences = emptyList()
        )
        // Serialize as JSON v2 packet with roomSchemaVersion=3
        val v3DataObj = BackupSerializer.serializeDataObject(v3Dataset)
        assertFalse(v3DataObj.has("folioSequences"))
        assertTrue(vmRestored.authenticateUser("Dueño", "DuenoPhase3#2026"))
        val v3Bytes = BackupSerializer.serializeEncryptedV2(v3Dataset, pass)
        val restoreV3 = vmRestored.executeFullRestore(v3Bytes, pass)
        assertTrue(restoreV3 is BackupOperationResult.RestoreSuccess)

        // Derived folio sequences must prevent F-00088 / OC-00042 collision
        assertEquals(88L, dao.getFolioSequenceSync(CajaMaestraDatabase.KEY_VENTA)?.lastNumber)
        assertEquals(42L, dao.getFolioSequenceSync(CajaMaestraDatabase.KEY_COMPRA)?.lastNumber)
    }

    // 18. Migraciones reales SQLite v2 -> v3 -> v4 + protección de archivo v1 sin borrado destructivo
    @Test
    fun `18 real SQLite migrations v2 to v3 to v4 preserve data and v1 salvage guard prevents silent wipe`() = runBlocking {
        val dbName = "migration_test_caja_maestra.db"
        app.deleteDatabase(dbName)

        // Create authentic SQLite v2 database matching CajaMaestraDatabase v2 schema
        val callbackV2 = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE inventory_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, code TEXT NOT NULL, costPrice REAL NOT NULL, salePrice REAL NOT NULL, stock REAL NOT NULL, minStock REAL NOT NULL, category TEXT NOT NULL, unit TEXT NOT NULL, type TEXT NOT NULL, isWaste INTEGER NOT NULL, notes TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_inventory_items_name ON inventory_items(name)""")
                db.execSQL("""CREATE INDEX index_inventory_items_code ON inventory_items(code)""")
                db.execSQL("""CREATE INDEX index_inventory_items_type ON inventory_items(type)""")
                db.execSQL("""CREATE INDEX index_inventory_items_category ON inventory_items(category)""")

                db.execSQL("""CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, folio TEXT NOT NULL, type TEXT NOT NULL, status TEXT NOT NULL, customerId INTEGER, customerName TEXT NOT NULL, subtotal REAL NOT NULL, discount REAL NOT NULL, total REAL NOT NULL, paidAmount REAL NOT NULL, changeAmount REAL NOT NULL, paymentMethod TEXT NOT NULL, date INTEGER NOT NULL, sellerName TEXT NOT NULL, notes TEXT NOT NULL, itemsJson TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_sales_date ON sales(date)""")
                db.execSQL("""CREATE INDEX index_sales_folio ON sales(folio)""")
                db.execSQL("""CREATE INDEX index_sales_type ON sales(type)""")
                db.execSQL("""CREATE INDEX index_sales_status ON sales(status)""")
                db.execSQL("""CREATE INDEX index_sales_customerId ON sales(customerId)""")

                db.execSQL("""CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, folio TEXT NOT NULL, supplierId INTEGER, supplierName TEXT NOT NULL, total REAL NOT NULL, date INTEGER NOT NULL, isCredit INTEGER NOT NULL, notes TEXT NOT NULL, itemsJson TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_purchases_date ON purchases(date)""")
                db.execSQL("""CREATE INDEX index_purchases_folio ON purchases(folio)""")
                db.execSQL("""CREATE INDEX index_purchases_supplierId ON purchases(supplierId)""")

                db.execSQL("""CREATE TABLE extra_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, concept TEXT NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, isCredit INTEGER NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_extra_movements_date ON extra_movements(date)""")
                db.execSQL("""CREATE INDEX index_extra_movements_type ON extra_movements(type)""")
                db.execSQL("""CREATE INDEX index_extra_movements_category ON extra_movements(category)""")

                db.execSQL("""CREATE TABLE rentals_loans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, isRental INTEGER NOT NULL, contactName TEXT NOT NULL, itemOrReason TEXT NOT NULL, amount REAL NOT NULL, depositAmount REAL NOT NULL, startDate INTEGER NOT NULL, dueDate INTEGER NOT NULL, isReturnedOrPaid INTEGER NOT NULL, notes TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_rentals_loans_startDate ON rentals_loans(startDate)""")
                db.execSQL("""CREATE INDEX index_rentals_loans_isRental ON rentals_loans(isRental)""")
                db.execSQL("""CREATE INDEX index_rentals_loans_isReturnedOrPaid ON rentals_loans(isReturnedOrPaid)""")

                db.execSQL("""CREATE TABLE clients (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, company TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, address TEXT NOT NULL, creditBalance REAL NOT NULL, notes TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_clients_name ON clients(name)""")
                db.execSQL("""CREATE INDEX index_clients_phone ON clients(phone)""")

                db.execSQL("""CREATE TABLE suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, company TEXT NOT NULL, contactName TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, address TEXT NOT NULL, suppliedProducts TEXT NOT NULL, paymentTerms TEXT NOT NULL, notes TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_suppliers_name ON suppliers(name)""")
                db.execSQL("""CREATE INDEX index_suppliers_company ON suppliers(company)""")

                db.execSQL("""CREATE TABLE employees (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, position TEXT NOT NULL, salary REAL NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, address TEXT NOT NULL, role TEXT NOT NULL, pin TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_employees_name ON employees(name)""")
                db.execSQL("""CREATE INDEX index_employees_role ON employees(role)""")

                db.execSQL("INSERT INTO inventory_items VALUES (7, 'Producto V2', 'V2-07', 25.0, 50.0, 14.0, 3.0, 'General', 'Pza', 'PRODUCTO', 0, 'Migrado')")
                db.execSQL("INSERT INTO sales VALUES (11, 'F-00250', 'VENTA', 'PAGADA', NULL, 'Cliente V2', 100.0, 0.0, 100.0, 100.0, 0.0, 'Efectivo', 1700000000000, 'Admin', '', '[]')")
                db.execSQL("INSERT INTO sales VALUES (12, 'COT-00019', 'COTIZACION', 'PENDIENTE', NULL, 'Cliente Cot', 200.0, 0.0, 200.0, 0.0, 0.0, 'Cotización', 1700000001000, 'Admin', '', '[]')")
                db.execSQL("INSERT INTO purchases VALUES (3, 'OC-00077', NULL, 'Prov V2', 500.0, 1700000002000, 0, '', '[]')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val helperV2 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(dbName)
                .callback(callbackV2)
                .build()
        )
        helperV2.writableDatabase.close()
        helperV2.close()

        // Open with Room v5 using real MIGRATION_2_3, MIGRATION_3_4 and MIGRATION_4_5
        val migratedDb = Room.databaseBuilder(app, CajaMaestraDatabase::class.java, dbName)
            .addMigrations(
                CajaMaestraDatabase.MIGRATION_2_3,
                CajaMaestraDatabase.MIGRATION_3_4,
                CajaMaestraDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()
        try {
            val mDao = migratedDb.dao()
            val migratedItem = mDao.getInventoryItemByIdSync(7L)
            assertNotNull(migratedItem)
            assertEquals("Producto V2", migratedItem!!.name)
            assertEquals(250L, mDao.getFolioSequenceSync(CajaMaestraDatabase.KEY_VENTA)?.lastNumber)
            assertEquals(19L, mDao.getFolioSequenceSync(CajaMaestraDatabase.KEY_COTIZACION)?.lastNumber)
            assertEquals(77L, mDao.getFolioSequenceSync(CajaMaestraDatabase.KEY_COMPRA)?.lastNumber)

            // Verify new sale continues cleanly at F-00251
            val repo = BusinessOperationsRepository(migratedDb) { BusinessSettings() }
            mDao.insertCashShift(CashRegisterShift(cashierName = "Owner", openingFloat = 100.0, isOpen = true))
            val res = repo.completeSale(
                items = listOf(SaleCartItem(7L, "Producto V2", 1.0, 50.0, 25.0, "Pza", ItemType.PRODUCTO)),
                saleType = SaleType.VENTA,
                selectedCustomerId = null,
                paymentMethod = "Efectivo",
                customerPaidInput = "50",
                discountInput = "0",
                notes = ""
            )
            assertTrue(res is RepoSaleResult.Committed)
            assertEquals("F-00251", (res as RepoSaleResult.Committed).sale.folio)
        } finally {
            migratedDb.close()
            app.deleteDatabase(dbName)
        }

        // Verify legacy v1 SQLite salvage protection refuses destructive wipe and creates backup copy
        val v1DbName = "legacy_v1_test.db"
        app.deleteDatabase(v1DbName)
        val helperV1 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(v1DbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE legacy_data (id INTEGER PRIMARY KEY, val TEXT)")
                        db.execSQL("INSERT INTO legacy_data VALUES (1, 'no_perder')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        helperV1.writableDatabase.close()
        helperV1.close()

        var caughtV1Guard = false
        try {
            CajaMaestraDatabase.salvageLegacyV1DatabaseIfPresent(app, v1DbName)
        } catch (e: IllegalStateException) {
            caughtV1Guard = true
            assertTrue(e.message!!.contains("db_safety_backup"))
        } finally {
            app.deleteDatabase(v1DbName)
        }
        assertTrue(caughtV1Guard)
        val backupFiles = File(app.filesDir, "db_safety_backup").listFiles().orEmpty()
        assertTrue(backupFiles.any { it.name.startsWith(v1DbName) })
    }

    // 19. Foliación sin colisiones tras borrar/anular ventas y compras + cotizaciones independientes
    @Test
    fun `19 monotonic folios never repeat after deletion or cancellation and isolate VENTA, COTIZACION, and COMPRA sequences`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(InventoryItem(name = "Cable USB", code = "C-1", costPrice = 20.0, salePrice = 60.0, stock = 20.0))
        assertNotNull(vm.openCashRegisterAsync(100.0))

        // Create two sales: F-00001 and F-00002
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 1.0))
        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 1.0))
        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)

        // Delete F-00002 -> next sale MUST be F-00003 (not F-00002!)
        val sale2 = dao.getSaleByFolioSync("F-00002")!!
        dao.deleteSale(sale2)
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 1.0))
        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)
        assertNotNull(dao.getSaleByFolioSync("F-00003"))

        // Switch to Quote -> independent COT-00001 sequence without reducing stock
        vm.setSaleType(SaleType.COTIZACION)
        assertTrue(vm.addToCart(dao.getInventoryItemByIdSync(prodId)!!, 5.0))
        assertTrue(vm.completeSaleAsync() is SaleCheckoutResult.Success)
        assertNotNull(dao.getSaleByFolioSync("COT-00001"))
        assertEquals(17.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
    }

    // 20. Ajuste manual concurrente de inventario + rollback de compra interrumpida
    @Test
    fun `20 concurrent stock adjustments accumulate against live Room stock and interrupted purchase rolls back completely`() = runBlocking {
        val vm = createInitializedOwnerViewModel()
        val dao = getDao()

        val prodId = dao.insertItem(InventoryItem(name = "Termómetro", code = "TH-1", costPrice = 50.0, salePrice = 120.0, stock = 10.0))
        val staleUiSnapshot = dao.getInventoryItemByIdSync(prodId)!!

        // 5 concurrent +2 adjustments passing the same stale snapshot -> live stock must become 10 + 10 = 20!
        val adjusted = (1..5).map {
            async(Dispatchers.IO) { vm.adjustItemStockAsync(staleUiSnapshot, 2.0) }
        }.awaitAll()
        assertEquals(5, adjusted.count { it != null })
        assertEquals(20.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)

        // Interrupted purchase rolls back stock, cost, purchase row, and OC folio sequence
        var purchaseRolledBack = false
        try {
            vm.registerPurchaseAsync(
                supplierId = null,
                fallbackSupplierName = "Prov Rollback",
                itemsBought = listOf(Triple(dao.getInventoryItemByIdSync(prodId)!!, 8.0, 99.0)),
                isCredit = false,
                notes = "Rollback test",
                faultHookAfterPurchaseInsert = { throw IllegalStateException("Fallo en compra") }
            )
        } catch (_: IllegalStateException) {
            purchaseRolledBack = true
        }
        assertTrue(purchaseRolledBack)
        assertTrue(dao.getAllPurchasesSync().isEmpty())
        assertEquals(20.0, dao.getInventoryItemByIdSync(prodId)!!.stock, 0.001)
        assertEquals(50.0, dao.getInventoryItemByIdSync(prodId)!!.costPrice, 0.001)
        assertNull(dao.getFolioSequenceSync(CajaMaestraDatabase.KEY_COMPRA))
    }
}
