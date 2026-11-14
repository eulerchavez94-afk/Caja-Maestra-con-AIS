package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.ui.components.DrawerContent
import com.example.ui.screens.AgendaScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.DateFilterType
import com.example.ui.viewmodel.InitialSecuritySetupResult
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SaleCheckoutResult
import com.example.ui.viewmodel.SecurityActionResult
import com.example.util.AesEncryptionUtil
import com.example.util.ReportsDataset
import com.example.util.ReportsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComprehensivePhase5Test {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUpClean() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            SettingsStorage.getInstance(app).clearAllForTest()
            clearAllTablesOnSingletonDb()
            app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
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
        val db = CajaMaestraDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
        val dao = db.dao()
        dao.clearInventoryMovements()
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

    private suspend fun initAndAuthenticateOwner(vm: MainViewModel) {
        vm.awaitInitialized()
        val setup = vm.initializeSecuritySetup(
            adminPassword = "AdminPass#55",
            jefePassword = "OwnerPass#55",
            trabajadorPassword = "WorkerPass55",
            masterPin = "8529",
            recoveryQuestion = "¿Primera tienda?",
            recoveryAnswer = "Centro"
        )
        assertTrue(setup is InitialSecuritySetupResult.Success)
        assertTrue(vm.authenticateUser("Jefe", "OwnerPass#55"))
        vm.awaitPersistence()
    }

    @Test
    fun migration_4_to_5_preserves_data_backfills_isRawMaterial_and_creates_inventory_movements() = runBlocking {
        val dbName = "phase5_mig_v4_to_v5.db"
        app.deleteDatabase(dbName)

        val callbackV4 = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE inventory_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, code TEXT NOT NULL, costPrice REAL NOT NULL, salePrice REAL NOT NULL, stock REAL NOT NULL, minStock REAL NOT NULL, category TEXT NOT NULL, unit TEXT NOT NULL, type TEXT NOT NULL, isWaste INTEGER NOT NULL, notes TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_inventory_items_name ON inventory_items(name)""")
                db.execSQL("""CREATE INDEX index_inventory_items_code ON inventory_items(code)""")
                db.execSQL("""CREATE INDEX index_inventory_items_type ON inventory_items(type)""")
                db.execSQL("""CREATE INDEX index_inventory_items_category ON inventory_items(category)""")

                db.execSQL("""CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, folio TEXT NOT NULL, type TEXT NOT NULL, status TEXT NOT NULL, customerId INTEGER, customerName TEXT NOT NULL, subtotal REAL NOT NULL, discount REAL NOT NULL, total REAL NOT NULL, paidAmount REAL NOT NULL, changeAmount REAL NOT NULL, paymentMethod TEXT NOT NULL, date INTEGER NOT NULL, sellerName TEXT NOT NULL, notes TEXT NOT NULL, itemsJson TEXT NOT NULL, shiftId INTEGER)""")
                db.execSQL("""CREATE INDEX index_sales_date ON sales(date)""")
                db.execSQL("""CREATE INDEX index_sales_folio ON sales(folio)""")
                db.execSQL("""CREATE INDEX index_sales_type ON sales(type)""")
                db.execSQL("""CREATE INDEX index_sales_status ON sales(status)""")
                db.execSQL("""CREATE INDEX index_sales_customerId ON sales(customerId)""")
                db.execSQL("""CREATE INDEX index_sales_shiftId ON sales(shiftId)""")

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

                db.execSQL("""CREATE TABLE cash_shifts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cashierName TEXT NOT NULL, cashierRole TEXT NOT NULL, openedAt INTEGER NOT NULL, closedAt INTEGER, openingFloat REAL NOT NULL, cashSalesTotal REAL NOT NULL, cardSalesTotal REAL NOT NULL, transferSalesTotal REAL NOT NULL, creditSalesTotal REAL NOT NULL, cashEntriesTotal REAL NOT NULL, cashWithdrawalsTotal REAL NOT NULL, expectedCash REAL NOT NULL, countedCash REAL NOT NULL, difference REAL NOT NULL, nextTurnFloat REAL NOT NULL, salesCount INTEGER NOT NULL, isOpen INTEGER NOT NULL, openingNotes TEXT NOT NULL, closingNotes TEXT NOT NULL)""")
                db.execSQL("""CREATE INDEX index_cash_shifts_openedAt ON cash_shifts(openedAt)""")
                db.execSQL("""CREATE INDEX index_cash_shifts_isOpen ON cash_shifts(isOpen)""")

                db.execSQL("""CREATE TABLE cash_shift_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shiftId INTEGER NOT NULL, type TEXT NOT NULL, amount REAL NOT NULL, reason TEXT NOT NULL, performedBy TEXT NOT NULL, date INTEGER NOT NULL)""")
                db.execSQL("""CREATE INDEX index_cash_shift_movements_shiftId ON cash_shift_movements(shiftId)""")
                db.execSQL("""CREATE INDEX index_cash_shift_movements_date ON cash_shift_movements(date)""")

                db.execSQL("""CREATE TABLE folio_sequences (sequenceKey TEXT NOT NULL PRIMARY KEY, lastNumber INTEGER NOT NULL)""")

                db.execSQL("INSERT INTO inventory_items VALUES (1, 'Café Grano', 'INS-1', 120.0, 220.0, 8.0, 2.0, 'Insumos', 'Kg', 'PRODUCTO', 0, '')")
                db.execSQL("INSERT INTO inventory_items VALUES (2, 'Refresco Cola', 'REF-2', 10.0, 20.0, 30.0, 5.0, 'Bebidas', 'Pza', 'PRODUCTO', 0, '')")
                db.execSQL("INSERT INTO inventory_items VALUES (3, 'Insumo Dañado', 'MER-3', 5.0, 0.0, 2.0, 0.0, 'Materia Prima', 'Kg', 'PRODUCTO', 1, 'Merma')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val helperV4 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(dbName)
                .callback(callbackV4)
                .build()
        )
        helperV4.writableDatabase.close()
        helperV4.close()

        val migratedDb = Room.databaseBuilder(app, CajaMaestraDatabase::class.java, dbName)
            .addMigrations(CajaMaestraDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val mDao = migratedDb.dao()
            val item1 = mDao.getInventoryItemByIdSync(1L)
            val item2 = mDao.getInventoryItemByIdSync(2L)
            val item3 = mDao.getInventoryItemByIdSync(3L)

            assertNotNull(item1)
            assertTrue(item1!!.isRawMaterial)
            assertNotNull(item2)
            assertFalse(item2!!.isRawMaterial)
            assertNotNull(item3)
            assertFalse(item3!!.isRawMaterial)

            val repo = BusinessOperationsRepository(migratedDb) { BusinessSettings() }
            val adjusted = repo.adjustItemStock(1L, 4.0, "Prueba migración v5")
            assertNotNull(adjusted)
            assertEquals(12.0, adjusted!!.stock, 0.001)

            val movs = mDao.getAllInventoryMovementsSync()
            assertEquals(1, movs.size)
            assertEquals(InventoryMovementType.AJUSTE_MANUAL, movs.first().movementType)
            assertEquals("Prueba migración v5", movs.first().reason)
        } finally {
            migratedDb.close()
            app.deleteDatabase(dbName)
        }
    }

    @Test
    fun inventory_movement_lifecycle_audit_and_wipe_across_operations() = runBlocking {
        val vm = MainViewModel(app)
        initAndAuthenticateOwner(vm)

        val saved = vm.saveInventoryItemAsync(
            item = InventoryItem(
                name = "Pan Artesanal",
                code = "PAN-01",
                costPrice = 15.0,
                salePrice = 30.0,
                stock = 10.0,
                minStock = 2.0,
                type = ItemType.PRODUCTO
            ),
            reason = "Inventario inicial"
        )!!
        assertEquals(10.0, saved.stock, 0.001)

        // Direct edit of stock 10 -> 15
        vm.saveInventoryItemAsync(saved.copy(stock = 15.0), reason = "Corrección conteo")

        // Manual stock adjustment +3
        vm.adjustItemStockAsync(saved, 3.0, reason = "Conteo físico bodega")

        // Purchase +4 -> stock becomes 22
        val purchaseRes = vm.registerPurchaseAsync(
            supplierId = null,
            fallbackSupplierName = "Panificadora Local",
            itemsBought = listOf(Triple(saved, 4.0, 14.0)),
            isCredit = false,
            notes = "Factura 88"
        )
        assertTrue(purchaseRes is com.example.ui.viewmodel.PurchaseRegistrationResult.Success)

        // Open register and complete sale -2 -> stock becomes 20
        assertNotNull(vm.openCashRegisterAsync(100.0, "Inicio"))
        assertTrue(vm.addToCart(saved.copy(stock = 22.0), 2.0))
        vm.setCustomerPaidInput("100")
        assertEquals(SaleCheckoutResult.Success, vm.completeSaleAsync())

        val latestSale = vm.lastCompletedSale.value!!
        assertTrue(vm.cancelSaleAsync(latestSale))

        val db = CajaMaestraDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
        val movs = db.dao().getAllInventoryMovementsSync()
        val types = movs.map { it.movementType }.toSet()
        assertTrue(types.contains(InventoryMovementType.ALTA_INVENTARIO))
        assertTrue(types.contains(InventoryMovementType.EDICION_STOCK))
        assertTrue(types.contains(InventoryMovementType.AJUSTE_MANUAL))
        assertTrue(types.contains(InventoryMovementType.ENTRADA_COMPRA))
        assertTrue(types.contains(InventoryMovementType.SALIDA_VENTA))
        assertTrue(types.contains(InventoryMovementType.RESTITUCION_CANCELACION))
        assertEquals(22.0, db.dao().getInventoryItemByIdSync(saved.id)!!.stock, 0.001)

        val modReport = ReportsEngine.buildReport(
            title = "Modificación Inventario",
            dateFilter = DateFilterType.TODOS,
            dataset = ReportsDataset(
                inventory = db.dao().getAllInventoryItemsSync(),
                inventoryMovements = movs
            ),
            settings = vm.settings.value
        )
        assertTrue(modReport.rows.size >= 6)
        assertTrue(modReport.rows.any { it.joinToString("|").contains("Conteo físico bodega") })

        // Wipe clears inventory_movements as part of the 11 operational tables
        assertTrue(vm.wipeAllDataAsync())
        assertTrue(db.dao().getAllInventoryMovementsSync().isEmpty())
    }

    @Test
    fun raw_material_flag_drives_materia_prima_reports_without_keyword_dependency() = runBlocking {
        val vm = MainViewModel(app)
        initAndAuthenticateOwner(vm)

        val rawItem = vm.saveInventoryItemAsync(
            InventoryItem(
                name = "Acero Especial X9",
                code = "AX-9",
                category = "Industrial",
                costPrice = 40.0,
                salePrice = 80.0,
                stock = 12.0,
                minStock = 3.0,
                type = ItemType.PRODUCTO,
                isRawMaterial = true
            )
        )!!
        assertTrue(rawItem.isRawMaterial)

        vm.registerPurchaseAsync(
            supplierId = null,
            fallbackSupplierName = "Aceros del Norte",
            itemsBought = listOf(Triple(rawItem, 5.0, 42.0)),
            isCredit = false,
            notes = "OC Acero"
        )

        val db = CajaMaestraDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
        val dataset = ReportsDataset(
            inventory = db.dao().getAllInventoryItemsSync(),
            purchases = db.dao().getAllPurchasesSync(),
            inventoryMovements = db.dao().getAllInventoryMovementsSync()
        )
        val comprasMp = ReportsEngine.buildReport("Materia Prima Compras", DateFilterType.TODOS, dataset, vm.settings.value)
        val analisisMp = ReportsEngine.buildReport("Materia Prima Análisis", DateFilterType.TODOS, dataset, vm.settings.value)
        val resumenMp = ReportsEngine.buildReport("Materia Prima Inventario Resumen", DateFilterType.TODOS, dataset, vm.settings.value)

        assertTrue(comprasMp.rows.any { it.joinToString("|").contains("Acero Especial X9") })
        assertTrue(analisisMp.rows.any { it.joinToString("|").contains("Acero Especial X9") })
        assertTrue(resumenMp.rows.any { it.joinToString("|").contains("Acero Especial X9") })
    }

    @Test
    fun offline_backup_v5_roundtrip_preserves_raw_material_and_movements_and_v4_compatibly() = runBlocking {
        val vm = MainViewModel(app)
        initAndAuthenticateOwner(vm)

        val item = vm.saveInventoryItemAsync(
            InventoryItem(
                name = "Resina Epóxica",
                code = "RES-5",
                category = "Químicos",
                costPrice = 90.0,
                salePrice = 150.0,
                stock = 6.0,
                minStock = 2.0,
                type = ItemType.PRODUCTO,
                isRawMaterial = true
            ),
            reason = "Alta v5"
        )!!
        vm.adjustItemStockAsync(item, 4.0, "Ingreso lote #2")

        val exportRes = vm.createFullBackupBytes("PassBackup#2026")
        assertTrue(exportRes.isSuccess)
        val (bytes, counts) = exportRes.getOrThrow()
        assertTrue(counts.inventoryMovements >= 2)

        val inspection = vm.inspectSelectedBackupBytes(bytes, "test_v5.cmbak")
        assertTrue(inspection.isValidForRestore)
        assertEquals(5, inspection.roomSchemaVersion)

        assertTrue(vm.wipeAllDataAsync())
        val db = CajaMaestraDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
        assertTrue(db.dao().getAllInventoryItemsSync().isEmpty())
        assertTrue(db.dao().getAllInventoryMovementsSync().isEmpty())

        val restoreRes = vm.executeFullRestore(bytes, "PassBackup#2026")
        assertTrue(restoreRes is BackupOperationResult.RestoreSuccess)

        val restoredItems = db.dao().getAllInventoryItemsSync()
        val restoredMovs = db.dao().getAllInventoryMovementsSync()
        assertEquals(1, restoredItems.size)
        assertTrue(restoredItems.first().isRawMaterial)
        assertEquals(2, restoredMovs.size)

        // Verify backward compatibility restoring a v4-shaped backup (without inventoryMovements or isRawMaterial)
        val v4EnvelopeBytes = buildSyntheticV4EncryptedBackup("PassCompat#4")
        val compatRestore = vm.executeFullRestore(v4EnvelopeBytes, "PassCompat#4")
        assertTrue("Expected RestoreSuccess but got: $compatRestore", compatRestore is BackupOperationResult.RestoreSuccess)
        val compatItems = db.dao().getAllInventoryItemsSync()
        assertEquals(1, compatItems.size)
        assertEquals("Producto Heredado V4", compatItems.first().name)
        assertFalse(compatItems.first().isRawMaterial)
        assertTrue(db.dao().getAllInventoryMovementsSync().isEmpty())
    }

    private suspend fun buildSyntheticV4EncryptedBackup(passphrase: String): ByteArray {
        val createdAt = 1_700_000_000_000L
        val storedState = SettingsStorage.getInstance(app).loadState()
        val baseV4Dataset = BackupDataset(
            formatVersion = BackupSerializer.CURRENT_FORMAT_VERSION,
            roomSchemaVersion = 4,
            createdAt = createdAt,
            inventory = listOf(
                InventoryItem(
                    id = 99L,
                    name = "Producto Heredado V4",
                    code = "V4-99",
                    costPrice = 12.0,
                    salePrice = 25.0,
                    stock = 8.0,
                    minStock = 2.0,
                    category = "General",
                    unit = "Pza",
                    type = ItemType.PRODUCTO,
                    isWaste = false,
                    isRawMaterial = false,
                    notes = ""
                )
            ),
            sales = emptyList(),
            purchases = emptyList(),
            extraMovements = emptyList(),
            rentalsLoans = emptyList(),
            clients = emptyList(),
            suppliers = emptyList(),
            employees = emptyList(),
            cashShifts = emptyList(),
            cashShiftMovements = emptyList(),
            businessSettings = storedState.settings,
            securityVault = storedState.vault,
            folioSequences = emptyList(),
            inventoryMovements = emptyList()
        )
        val dataJsonObj = BackupSerializer.serializeDataObject(baseV4Dataset).apply {
            val invArray = getJSONArray("inventory")
            for (i in 0 until invArray.length()) {
                invArray.getJSONObject(i).remove("isRawMaterial")
            }
            remove("inventoryMovements")
        }
        val dataJsonStr = dataJsonObj.toString()
        val digest = AesEncryptionUtil.sha256Hex(dataJsonStr.toByteArray(Charsets.UTF_8))
        val countsObj = JSONObject().apply {
            put("inventory", 1)
            put("sales", 0)
            put("purchases", 0)
            put("extraMovements", 0)
            put("rentalsLoans", 0)
            put("clients", 0)
            put("suppliers", 0)
            put("employees", 0)
            put("cashShifts", 0)
            put("cashShiftMovements", 0)
            put("hasBusinessSettings", true)
            put("hasSecurityVault", true)
        }
        val payloadRoot = JSONObject().apply {
            put("formatVersion", BackupSerializer.CURRENT_FORMAT_VERSION)
            put("roomSchemaVersion", 4)
            put("createdAt", createdAt)
            put("counts", countsObj)
            put("dataJson", dataJsonStr)
            put("integritySha256", digest)
        }
        val aad = "${BackupSerializer.MAGIC_V2}|v${BackupSerializer.CURRENT_FORMAT_VERSION}|room4|$createdAt"
            .toByteArray(Charsets.UTF_8)
        val encrypted = AesEncryptionUtil.encryptPayloadWithGzipAndAad(
            plainBytes = payloadRoot.toString().toByteArray(Charsets.UTF_8),
            passphrase = passphrase,
            iterations = AesEncryptionUtil.BACKUP_KDF_ITERATIONS,
            aad = aad
        )
        return JSONObject().apply {
            put("magic", BackupSerializer.MAGIC_V2)
            put("formatVersion", BackupSerializer.CURRENT_FORMAT_VERSION)
            put("roomSchemaVersion", 4)
            put("createdAt", createdAt)
            put("compression", "GZIP")
            put("kdf", "PBKDF2WithHmacSHA256")
            put("kdfIterations", AesEncryptionUtil.BACKUP_KDF_ITERATIONS)
            put("cipher", "AES/GCM/NoPadding")
            put("includesSecurityVault", true)
            put("payload", encrypted)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    @Test
    fun worker_edit_preserves_costPrice_and_empty_inventory_renders_empty_state_without_stuck_shimmer() {
        val vm = MainViewModel(app)
        runBlocking {
            initAndAuthenticateOwner(vm)
            val saved = vm.saveInventoryItemAsync(
                InventoryItem(
                    name = "Toner Pro",
                    code = "TON-1",
                    costPrice = 55.0,
                    salePrice = 100.0,
                    stock = 5.0
                )
            )!!
            assertTrue(vm.switchActiveRoleWithCredential(UserRole.TRABAJADOR, "WorkerPass55") is SecurityActionResult.Success)
            // Worker updates price with costPrice = 0.0 -> ViewModel + Repo must preserve original costPrice = 55.0
            val afterWorker = vm.saveInventoryItemAsync(saved.copy(salePrice = 115.0, costPrice = 0.0))!!
            assertEquals(55.0, afterWorker.costPrice, 0.001)
            assertEquals(115.0, afterWorker.salePrice, 0.001)

            // Wipe inventory so UI sees empty inventory list (reproducing endless-shimmer bug)
            assertTrue(vm.switchActiveRoleWithCredential(UserRole.DUENO, "OwnerPass#55") is SecurityActionResult.Success)
            assertTrue(vm.wipeAllDataAsync())
        }

        composeRule.setContent {
            MyApplicationTheme {
                InventoryScreen(
                    viewModel = vm,
                    onOpenDrawer = {},
                    onShowTutorial = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¡Tu Inventario está Listo!").assertIsDisplayed()
    }

    @Test
    fun agenda_displays_net_credit_balance_for_partial_credit_sales() {
        val vm = MainViewModel(app)
        runBlocking {
            initAndAuthenticateOwner(vm)
            val db = CajaMaestraDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
            db.dao().insertSale(
                Sale(
                    folio = "F-9001",
                    type = SaleType.VENTA,
                    status = SaleStatus.CREDITO,
                    customerName = "Cliente Parcial",
                    subtotal = 250.0,
                    total = 250.0,
                    paidAmount = 100.0,
                    paymentMethod = "Crédito",
                    date = System.currentTimeMillis()
                )
            )
        }

        composeRule.setContent {
            MyApplicationTheme {
                AgendaScreen(viewModel = vm, onOpenDrawer = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 5000) {
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("Cobro a Cliente: Cliente Parcial")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Cobro a Cliente: Cliente Parcial").assertIsDisplayed()
        composeRule.onNodeWithText("$150.00").assertIsDisplayed()
    }

    @Test
    fun drawer_routes_agenda_header_tile_to_agenda_screen() {
        var navigatedScreen: AppScreen? = null
        composeRule.setContent {
            MyApplicationTheme {
                DrawerContent(
                    settings = BusinessSettings(currentRole = UserRole.DUENO),
                    currentScreen = AppScreen.INVENTARIO,
                    onNavigate = { navigatedScreen = it },
                    onShowTutorial = {},
                    onLogout = {},
                    onCloseDrawer = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Agenda").performClick()
        assertEquals(AppScreen.AGENDA, navigatedScreen)
    }
}
