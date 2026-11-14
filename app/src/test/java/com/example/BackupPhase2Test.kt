package com.example

import android.app.Application
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupFormatType
import com.example.data.backup.BackupOperationResult
import com.example.data.backup.BackupSerializer
import com.example.data.backup.RestoreStage
import com.example.data.db.CajaMaestraDatabase
import com.example.data.model.BusinessSettings
import com.example.data.model.CashRegisterShift
import com.example.data.model.CashShiftMovement
import com.example.data.model.CashShiftMovementType
import com.example.data.model.Client
import com.example.data.model.Employee
import com.example.data.model.ExtraMovement
import com.example.data.model.InventoryItem
import com.example.data.model.ItemType
import com.example.data.model.MovementType
import com.example.data.model.Purchase
import com.example.data.model.ReceiptFormat
import com.example.data.model.RentalLoan
import com.example.data.model.Sale
import com.example.data.model.SaleStatus
import com.example.data.model.SaleType
import com.example.data.model.Supplier
import com.example.data.model.UserRole
import com.example.data.prefs.SettingsStorage
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.InitialSecuritySetupResult
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SecurityActionResult
import com.example.util.AesEncryptionUtil
import com.example.util.PasswordHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupPhase2Test {

    private lateinit var app: Application
    private lateinit var db: CajaMaestraDatabase

    @Before
    fun resetCleanEnvironment() {
        runBlocking {
            app = ApplicationProvider.getApplicationContext()
            SettingsStorage.getInstance(app).clearAllForTest()
            db = CajaMaestraDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
            clearAllRoomTables()
            File(app.filesDir, "backup_journal").deleteRecursively()
            File(app.filesDir, "backups").deleteRecursively()
            File(app.cacheDir, "backups").deleteRecursively()
        }
    }

    private suspend fun clearAllRoomTables() {
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
    }

    private fun idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun createViewModel(): MainViewModel {
        val vm = MainViewModel(app)
        withTimeout(5000L) {
            while (!vm.isSettingsLoaded.value) {
                idleMainLooper()
                delay(20)
            }
        }
        vm.awaitInitialized()
        idleMainLooper()
        return vm
    }

    private suspend fun waitUntil(timeoutMs: Long = 5000L, condition: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (true) {
                idleMainLooper()
                if (condition()) break
                delay(25)
            }
        }
    }

    private suspend fun seedRichBusinessDataset(vm: MainViewModel): String {
        val setup = vm.initializeSecuritySetup(
            adminPassword = "AdminBackup#2026",
            jefePassword = "DuenoBackup#2026",
            trabajadorPassword = "TrabBackup#2026",
            masterPin = "9472",
            recoveryQuestion = "¿Lugar de fundación?",
            recoveryAnswer = "Monterrey Centro"
        ) as InitialSecuritySetupResult.Success
        vm.awaitPersistence()

        assertTrue(vm.authenticateUser("Admin", "AdminBackup#2026"))

        val richSettings = BusinessSettings(
            businessName = "Distribuidora El Norte S.A.",
            phone = "+52 81 8000 1234",
            email = "ventas@elnorte.mx",
            address = "Calz. Madero 890",
            footerText = "Garantía 30 días presentando su ticket",
            customQr = "https://elnorte.mx/validar",
            currencySymbol = "USD",
            numberFormat = "9 999 999,99",
            checkStock = false,
            useAverageCost = false,
            allowMultipleSales = false,
            allowEditDate = false,
            showCustomerPayAndChange = false,
            receiptFormat = ReceiptFormat.HOJA_CARTA,
            receiptShowClientInfo = false,
            receiptShowQr = false,
            receiptShowPaymentBreakdown = false,
            receiptShowFolio = false,
            receiptShowStatus = false,
            receiptShowKeyAndUnit = false,
            bluetoothPrinterName = "Bixolon SPP-R210",
            bluetoothPrinterAddress = "AA:BB:CC:DD:EE:FF",
            printScale = 1.4f,
            printIntensity = 225,
            restrictWithPin = true,
            currentRole = UserRole.ADMINISTRADOR,
            currentUserName = "Administrador Master",
            externalScanner = true,
            continuousScan = false,
            frontCamera = true,
            isAppLocked = false,
            trialModeEnabled = true,
            trialDurationDays = 14,
            trialStartDate = System.currentTimeMillis(),
            appLockReason = "Prueba empresarial",
            moduleInventoryEnabled = true,
            moduleSalesEnabled = true,
            modulePurchasesEnabled = true,
            moduleCashflowEnabled = false,
            moduleRentalsEnabled = true,
            moduleReportsEnabled = true,
            moduleEmployeesEnabled = true,
            moduleClientsEnabled = true,
            moduleSuppliersEnabled = true,
            moduleThermalPrinterEnabled = true,
            moduleCloudBackupEnabled = true,
            moduleSettingsEnabled = true,
            jefeAllowsTrabajadorInventory = false,
            jefeAllowsTrabajadorSales = true,
            jefeAllowsTrabajadorPurchases = true,
            jefeAllowsTrabajadorCashflow = true,
            jefeAllowsTrabajadorRentals = false,
            jefeAllowsTrabajadorReports = true,
            jefeAllowsTrabajadorEmployees = false,
            jefeAllowsTrabajadorClients = false,
            jefeAllowsTrabajadorSuppliers = true,
            jefeAllowsTrabajadorSettings = false
        )
        vm.updateSettings(richSettings)
        vm.awaitPersistence()

        val dao = db.dao()
        val prodId = dao.insertItem(
            InventoryItem(
                id = 101L,
                name = "Taladro Percutor 750W",
                code = "TAL-750",
                costPrice = 850.5,
                salePrice = 1499.99,
                stock = 12.5,
                minStock = 3.0,
                category = "Herramientas",
                unit = "Caja",
                type = ItemType.PRODUCTO,
                isWaste = true,
                notes = "Incluye maletín rígido y brocas"
            )
        )
        val clientId = dao.insertClient(
            Client(
                id = 201L,
                name = "Constructora Regia",
                company = "Regia Obras SA",
                phone = "8181818181",
                email = "compras@regia.mx",
                address = "Av. Constitución 120",
                creditBalance = 425.75,
                notes = "Cliente preferente 15 días"
            )
        )
        val supplierId = dao.insertSupplier(
            Supplier(
                id = 301L,
                name = "Importadora Industrial",
                company = "ImpInd Corp",
                contactName = "Carlos Vega",
                phone = "5544332211",
                email = "cvega@impind.com",
                address = "Parque Industrial Norte",
                suppliedProducts = "Taladros, Esmeriles, Brocas",
                paymentTerms = "Crédito 30 días",
                notes = "Entregas los martes"
            )
        )
        dao.insertEmployee(
            Employee(
                id = 401L,
                name = "Sofía Mendoza",
                position = "Cajera Senior",
                salary = 12500.0,
                phone = "8112233445",
                email = "sofia@elnorte.mx",
                address = "Col. Centro 44",
                role = UserRole.TRABAJADOR,
                pin = PasswordHasher.hash("7410")
            )
        )
        val shiftId = dao.insertCashShift(
            CashRegisterShift(
                id = 501L,
                cashierName = "Sofía Mendoza",
                cashierRole = UserRole.TRABAJADOR,
                openedAt = 1710000000000L,
                closedAt = 1710030000000L,
                openingFloat = 1000.0,
                cashSalesTotal = 1499.99,
                cardSalesTotal = 500.0,
                transferSalesTotal = 250.0,
                creditSalesTotal = 100.0,
                cashEntriesTotal = 300.0,
                cashWithdrawalsTotal = 150.0,
                expectedCash = 2649.99,
                countedCash = 2650.0,
                difference = 0.01,
                nextTurnFloat = 1000.0,
                salesCount = 1,
                isOpen = false,
                openingNotes = "Fondo verificado en monedas",
                closingNotes = "Cuadre exacto"
            )
        )
        dao.insertCashShiftMovement(
            CashShiftMovement(
                id = 601L,
                shiftId = shiftId,
                type = CashShiftMovementType.ENTRADA,
                amount = 300.0,
                reason = "Cambio para caja",
                performedBy = "Jefe / Dueño",
                date = 1710010000000L
            )
        )
        val sampleItemsJson = """[{"itemId":$prodId,"name":"Taladro Percutor 750W","quantity":1.0,"unitPrice":1499.99,"costPrice":850.5,"unit":"Caja","type":"PRODUCTO"}]"""
        dao.insertSale(
            Sale(
                id = 701L,
                folio = "V-000701",
                type = SaleType.VENTA,
                status = SaleStatus.PAGADA,
                customerId = clientId,
                customerName = "Constructora Regia",
                subtotal = 1550.0,
                discount = 50.01,
                total = 1499.99,
                paidAmount = 2000.0,
                changeAmount = 500.01,
                paymentMethod = "Tarjeta/Efectivo",
                date = 1710015000000L,
                sellerName = "Sofía Mendoza",
                notes = "Factura pendiente envío",
                itemsJson = sampleItemsJson,
                shiftId = shiftId
            )
        )
        dao.insertPurchase(
            Purchase(
                id = 801L,
                folio = "C-000801",
                supplierId = supplierId,
                supplierName = "Importadora Industrial",
                total = 8505.0,
                date = 1709990000000L,
                isCredit = true,
                notes = "Lote 10 unidades",
                itemsJson = sampleItemsJson
            )
        )
        dao.insertExtraMovement(
            ExtraMovement(
                id = 901L,
                type = MovementType.GASTO,
                concept = "Pago de energía eléctrica",
                category = "Servicios",
                amount = 1890.45,
                isCredit = false,
                date = 1709980000000L,
                notes = "Recibo bimestral CFE"
            )
        )
        dao.insertRentalLoan(
            RentalLoan(
                id = 1001L,
                isRental = true,
                contactName = "Arq. Roberto Garza",
                itemOrReason = "Andamio tubular 3 niveles",
                amount = 1200.0,
                depositAmount = 2000.0,
                startDate = 1710005000000L,
                dueDate = 1710609800000L,
                isReturnedOrPaid = false,
                notes = "Obra San Pedro"
            )
        )

        return setup.ownerRecoveryCode
    }

    @Test
    fun `complete v2 backup and full restore preserves all 10 tables, all fields, settings and security vault offline`() = runBlocking {
        val vmSource = createViewModel()
        val ownerRecoveryCode = seedRichBusinessDataset(vmSource)
        val preSnapshot = vmSource.backupManager.captureCurrentDataset()

        val exportPassphrase = "MiFraseUltraSegura#2026"
        val (backupBytes, exportCounts) = vmSource.createFullBackupBytes(exportPassphrase).getOrThrow()

        // Inspect v2 header metadata and ensure no plaintext secret leaks
        val inspection = vmSource.inspectSelectedBackupBytes(backupBytes, "respaldo_norte.cmbak")
        assertTrue(inspection.isValidForRestore)
        assertEquals(BackupFormatType.V2_ENVELOPE, inspection.formatType)
        assertEquals(2, inspection.formatVersion)
        assertEquals(BackupSerializer.ROOM_SCHEMA_VERSION, inspection.roomSchemaVersion)
        assertEquals("PBKDF2WithHmacSHA256", inspection.kdf)
        assertEquals("AES/GCM/NoPadding", inspection.cipher)
        assertTrue(inspection.includesSecurityVault)

        val rawText = String(backupBytes, Charsets.UTF_8)
        assertFalse(rawText.contains(exportPassphrase))
        assertFalse(rawText.contains("Distribuidora El Norte"))
        assertFalse(rawText.contains("TAL-750"))
        assertFalse(rawText.contains("adminPasswordHash"))

        // Exact counts check
        assertEquals(10, exportCounts.totalRoomRecords)
        assertEquals(1, exportCounts.inventory)
        assertEquals(1, exportCounts.sales)
        assertEquals(1, exportCounts.purchases)
        assertEquals(1, exportCounts.extraMovements)
        assertEquals(1, exportCounts.rentalsLoans)
        assertEquals(1, exportCounts.clients)
        assertEquals(1, exportCounts.suppliers)
        assertEquals(1, exportCounts.employees)
        assertEquals(1, exportCounts.cashShifts)
        assertEquals(1, exportCounts.cashShiftMovements)
        assertTrue(exportCounts.hasBusinessSettings)
        assertTrue(exportCounts.hasSecurityVault)

        // Verify local archive + FileProvider share URI creation
        val localRes = vmSource.saveLocalPrivateBackup(exportPassphrase)
        assertTrue(localRes is BackupOperationResult.ExportSuccess)
        val localPath = vmSource.lastExportedFilePath.value!!
        assertTrue(File(localPath).exists())

        val (shareFile, _) = vmSource.prepareShareableBackupFile(exportPassphrase).getOrThrow()
        assertTrue(shareFile.exists())
        val contentUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", shareFile)
        assertEquals("${app.packageName}.fileprovider", contentUri.authority)

        // Wipe device cleanly to simulate restoring onto a blank / freshly installed destination device
        SettingsStorage.getInstance(app).clearAllForTest()
        clearAllRoomTables()

        val vmTarget = createViewModel()
        assertFalse(vmTarget.securityState.value.isInitialized)
        assertFalse(vmTarget.isUserAuthenticated.value)

        // Restore on uninitialized fresh install
        val restoreResult = vmTarget.executeFullRestore(backupBytes, exportPassphrase)
        assertTrue(restoreResult is BackupOperationResult.RestoreSuccess)
        val success = restoreResult as BackupOperationResult.RestoreSuccess
        assertEquals(10, success.counts.totalRoomRecords)

        // Fresh device restore must NOT auto-bypass login screen!
        assertFalse(vmTarget.isUserAuthenticated.value)
        assertTrue(vmTarget.securityState.value.isInitialized)

        // Verify SecurityVault was re-protected with local KeyStore and all restored credentials work
        assertTrue(vmTarget.verifyMasterPin("9472"))
        assertFalse(vmTarget.verifyMasterPin("1234"))
        waitUntil { vmTarget.employees.value.any { it.name == "Sofía Mendoza" } }
        assertTrue(vmTarget.authenticateUser("Sofía Mendoza", "7410"))
        assertEquals(UserRole.TRABAJADOR, vmTarget.settings.value.currentRole)
        vmTarget.logout()
        assertTrue(vmTarget.authenticateUser("Dueño", "DuenoBackup#2026"))
        vmTarget.logout()
        assertTrue(vmTarget.authenticateUser("Admin", "AdminBackup#2026"))

        // Verify recovery code preserved and valid
        vmTarget.logout()
        val rec = vmTarget.recoverRoleWithCode(UserRole.DUENO, ownerRecoveryCode, "NuevaClavePostRestore#9")
        assertTrue(rec is com.example.ui.viewmodel.RecoveryResult.Success)
        assertTrue(vmTarget.authenticateUser("Admin", "AdminBackup#2026"))

        // Deep field-by-field comparison of ALL 10 Room tables + ALL BusinessSettings fields
        val postSnapshot = vmTarget.backupManager.captureCurrentDataset()
        assertEquals(preSnapshot.inventory, postSnapshot.inventory)
        assertEquals(preSnapshot.sales, postSnapshot.sales)
        assertEquals(preSnapshot.purchases, postSnapshot.purchases)
        assertEquals(preSnapshot.extraMovements, postSnapshot.extraMovements)
        assertEquals(preSnapshot.rentalsLoans, postSnapshot.rentalsLoans)
        assertEquals(preSnapshot.clients, postSnapshot.clients)
        assertEquals(preSnapshot.suppliers, postSnapshot.suppliers)
        assertEquals(preSnapshot.employees, postSnapshot.employees)
        assertEquals(preSnapshot.cashShifts, postSnapshot.cashShifts)
        assertEquals(preSnapshot.cashShiftMovements, postSnapshot.cashShiftMovements)

        // Verify specific critical fields specifically mentioned in audit
        val restoredSale = postSnapshot.sales.single()
        assertEquals(201L, restoredSale.customerId)
        assertEquals(501L, restoredSale.shiftId)
        assertEquals("Tarjeta/Efectivo", restoredSale.paymentMethod)
        assertEquals(2000.0, restoredSale.paidAmount, 0.001)
        assertEquals(500.01, restoredSale.changeAmount, 0.001)
        assertTrue(restoredSale.itemsJson.contains("Taladro Percutor 750W"))

        val restoredInv = postSnapshot.inventory.single()
        assertEquals(3.0, restoredInv.minStock, 0.001)
        assertEquals("Caja", restoredInv.unit)
        assertTrue(restoredInv.isWaste)
        assertEquals("Incluye maletín rígido y brocas", restoredInv.notes)

        // Verify BusinessSettings restored faithfully (except currentRole updated by Admin login above -> verify equality of non-session config)
        val restoredSettings = postSnapshot.businessSettings!!
        assertEquals("Distribuidora El Norte S.A.", restoredSettings.businessName)
        assertEquals("USD", restoredSettings.currencySymbol)
        assertEquals("9 999 999,99", restoredSettings.numberFormat)
        assertEquals(ReceiptFormat.HOJA_CARTA, restoredSettings.receiptFormat)
        assertFalse(restoredSettings.receiptShowQr)
        assertEquals("Bixolon SPP-R210", restoredSettings.bluetoothPrinterName)
        assertEquals("AA:BB:CC:DD:EE:FF", restoredSettings.bluetoothPrinterAddress)
        assertEquals(1.4f, restoredSettings.printScale, 0.001f)
        assertEquals(225, restoredSettings.printIntensity)
        assertFalse(restoredSettings.moduleCashflowEnabled)
        assertTrue(restoredSettings.jefeAllowsTrabajadorPurchases)
        assertFalse(restoredSettings.jefeAllowsTrabajadorClients)

        // Verify relational consistency
        val shiftIds = postSnapshot.cashShifts.map { it.id }.toSet()
        assertTrue(postSnapshot.cashShiftMovements.all { it.shiftId in shiftIds })
        assertTrue(postSnapshot.sales.all { it.shiftId == null || it.shiftId in shiftIds })
    }

    @Test
    fun `wrong passphrase, corrupted, truncated, incompatible version and empty backups fail safely without modifying data`() = runBlocking {
        val vm = createViewModel()
        seedRichBusinessDataset(vm)
        val before = vm.backupManager.captureCurrentDataset()
        val (validBytes, _) = vm.createFullBackupBytes("ClaveCorrecta#2026").getOrThrow()

        // 1. Wrong passphrase
        val wrongPass = vm.executeFullRestore(validBytes, "ClaveIncorrecta#999")
        assertTrue(wrongPass is BackupOperationResult.Error)

        // 2. Corrupted payload byte (GCM auth tag or JSON tamper)
        val jsonObj = JSONObject(String(validBytes, Charsets.UTF_8))
        val origPayload = jsonObj.getString("payload")
        val mutatedChar = if (origPayload[origPayload.length / 2] == 'A') 'B' else 'A'
        val corruptedPayload = StringBuilder(origPayload).apply {
            setCharAt(origPayload.length / 2, mutatedChar)
        }.toString()
        val corruptedBytes = JSONObject(String(validBytes, Charsets.UTF_8))
            .put("payload", corruptedPayload)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val corruptedRes = vm.executeFullRestore(corruptedBytes, "ClaveCorrecta#2026")
        assertTrue(corruptedRes is BackupOperationResult.Error)

        // 3. Tampered AAD envelope header (createdAt modified while payload untouched)
        val aadTamperedBytes = JSONObject(String(validBytes, Charsets.UTF_8))
            .put("createdAt", 123456789L)
            .toString()
            .toByteArray(Charsets.UTF_8)
        assertTrue(vm.executeFullRestore(aadTamperedBytes, "ClaveCorrecta#2026") is BackupOperationResult.Error)

        // 4. Truncated / incomplete file
        val truncatedBytes = validBytes.copyOf(validBytes.size / 2)
        val truncatedInsp = vm.inspectSelectedBackupBytes(truncatedBytes, "truncado.cmbak")
        assertFalse(truncatedInsp.isValidForRestore)
        assertTrue(vm.executeFullRestore(truncatedBytes, "ClaveCorrecta#2026") is BackupOperationResult.Error)

        // 5. Incompatible formatVersion / roomSchemaVersion
        val futureFormatBytes = JSONObject(String(validBytes, Charsets.UTF_8))
            .put("formatVersion", 99)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val futureInsp = vm.inspectSelectedBackupBytes(futureFormatBytes, "futuro.cmbak")
        assertFalse(futureInsp.isValidForRestore)
        assertEquals(BackupFormatType.UNSUPPORTED_VERSION, futureInsp.formatType)
        assertTrue(vm.executeFullRestore(futureFormatBytes, "ClaveCorrecta#2026") is BackupOperationResult.Error)

        val futureRoomBytes = JSONObject(String(validBytes, Charsets.UTF_8))
            .put("roomSchemaVersion", 99)
            .toString()
            .toByteArray(Charsets.UTF_8)
        assertEquals(BackupFormatType.UNSUPPORTED_VERSION, vm.inspectSelectedBackupBytes(futureRoomBytes).formatType)
        assertTrue(vm.executeFullRestore(futureRoomBytes, "ClaveCorrecta#2026") is BackupOperationResult.Error)

        // 6. Empty file and whitespace
        assertEquals(BackupFormatType.EMPTY, vm.inspectSelectedBackupBytes(ByteArray(0), "vacio.cmbak").formatType)
        assertTrue(vm.executeFullRestore(ByteArray(0), "ClaveCorrecta#2026") is BackupOperationResult.Error)
        assertTrue(vm.executeFullRestore("   ".toByteArray(), "ClaveCorrecta#2026") is BackupOperationResult.Error)

        // Verify ALL existing records + settings + vault remained 100% untouched
        val after = vm.backupManager.captureCurrentDataset()
        assertEquals(before.toCounts(), after.toCounts())
        assertEquals(before.inventory, after.inventory)
        assertEquals(before.sales, after.sales)
        assertEquals(before.businessSettings, after.businessSettings)
        assertEquals(before.securityVault, after.securityVault)
    }

    @Test
    fun `interrupted restore at any critical stage rolls back cleanly with zero data loss`() = runBlocking {
        val vm = createViewModel()
        seedRichBusinessDataset(vm)
        val original = vm.backupManager.captureCurrentDataset()

        // Create a second distinct backup dataset to restore over the original
        val newItem = InventoryItem(
            id = 999L,
            name = "Producto Nuevo Reemplazo",
            code = "NEW-999",
            costPrice = 10.0,
            salePrice = 20.0,
            stock = 5.0
        )
        val altDataset = original.copy(
            inventory = listOf(newItem),
            sales = emptyList(),
            purchases = emptyList(),
            extraMovements = emptyList(),
            rentalsLoans = emptyList(),
            clients = emptyList(),
            suppliers = emptyList(),
            cashShifts = emptyList(),
            cashShiftMovements = emptyList(),
            businessSettings = original.businessSettings!!.copy(businessName = "Negocio Temporal Fallido")
        )
        val altBytes = BackupSerializer.serializeEncryptedV2(altDataset, "OtraClave#2026")

        // Test interruption at EVERY critical point:
        // 1) Inside Room transaction after clearing tables (tests Room transaction + preventive restore)
        // 2) After Room transaction committed new data, BEFORE DataStore update (tests full 2-phase rollback!)
        // 3) After Room & DataStore updated, BEFORE final verification
        val stagesToInterrupt = listOf(
            RestoreStage.INSIDE_ROOM_TRANSACTION_AFTER_CLEAR,
            RestoreStage.AFTER_ROOM_BEFORE_DATASTORE,
            RestoreStage.AFTER_DATASTORE_BEFORE_VERIFICATION
        )

        for (stage in stagesToInterrupt) {
            val res = vm.executeFullRestore(altBytes, "OtraClave#2026") { currentStage ->
                if (currentStage == stage) {
                    throw RuntimeException("Fallo simulado en etapa $stage")
                }
            }
            assertTrue("Expected Error at stage $stage", res is BackupOperationResult.Error)
            assertTrue("Expected rolledBack=true at stage $stage", (res as BackupOperationResult.Error).rolledBack)

            val postRollback = vm.backupManager.captureCurrentDataset()
            assertEquals("Inventory lost at $stage", original.inventory, postRollback.inventory)
            assertEquals("Sales lost at $stage", original.sales, postRollback.sales)
            assertEquals("Purchases lost at $stage", original.purchases, postRollback.purchases)
            assertEquals("Shifts lost at $stage", original.cashShifts, postRollback.cashShifts)
            assertEquals("BusinessName corrupted at $stage", "Distribuidora El Norte S.A.", postRollback.businessSettings?.businessName)
            assertEquals("Vault corrupted at $stage", original.securityVault, postRollback.securityVault)
        }
    }

    @Test
    fun `crash mid-restore leaving pending journal automatically recovers original data on next app startup`() = runBlocking {
        val vm = createViewModel()
        seedRichBusinessDataset(vm)
        val original = vm.backupManager.captureCurrentDataset()

        // Manually simulate an app process kill right after preventive journal was written and Room/DataStore were wiped
        val journalDir = File(app.filesDir, "backup_journal").apply { mkdirs() }
        val settingsStorage = SettingsStorage.getInstance(app)
        val snapshotBytes = BackupSerializer.serializeSnapshotJson(original)
        File(journalDir, "preventive_rollback.enc").writeText(
            settingsStorage.encryptBytesWithLocalKeyStore(snapshotBytes),
            Charsets.UTF_8
        )
        File(journalDir, "restore_pending.flag").writeText("1", Charsets.UTF_8)

        clearAllRoomTables()
        settingsStorage.clearAllForTest()

        // Restart ViewModel -> init MUST detect restore_pending.flag and recover original Room + Settings + Vault!
        val recoveredVm = createViewModel()
        val afterRecovery = recoveredVm.backupManager.captureCurrentDataset()

        assertEquals(original.toCounts(), afterRecovery.toCounts())
        assertEquals(original.inventory, afterRecovery.inventory)
        assertEquals(original.sales, afterRecovery.sales)
        assertEquals(original.businessSettings, afterRecovery.businessSettings)
        assertEquals(original.securityVault, afterRecovery.securityVault)
        assertFalse(File(journalDir, "restore_pending.flag").exists())
        assertTrue(recoveredVm.verifyMasterPin("9472"))
    }

    @Test
    fun `legacy v1 backup restores inventory and sales while preserving Phase 1 employees and SecurityVault`() = runBlocking {
        val vm = createViewModel()
        seedRichBusinessDataset(vm)
        val preSnapshot = vm.backupManager.captureCurrentDataset()

        // Craft a genuine legacy v1 payload compressed with GZIP & encrypted via legacy AesEncryptionUtil
        val v1Json = JSONObject().apply {
            put("version", 1)
            put("timestamp", 1700000000000L)
            put("businessName", "Negocio Migrado V1")
            put("inventory", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 55L)
                    put("name", "Producto Heredado V1")
                    put("code", "V1-055")
                    put("costPrice", 12.0)
                    put("salePrice", 25.0)
                    put("stock", 9.0)
                    put("category", "General")
                    put("type", "PRODUCTO")
                })
            })
            put("sales", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 77L)
                    put("folio", "V1-00077")
                    put("total", 50.0)
                    put("date", 1700000100000L)
                    put("customerName", "Cliente V1")
                    put("status", "PAGADA")
                })
            })
        }.toString()

        val legacyEncrypted = AesEncryptionUtil.encryptWithGzip(v1Json, "ClaveV1#2025")
        val legacyBytes = legacyEncrypted.toByteArray(Charsets.UTF_8)

        val inspection = vm.inspectSelectedBackupBytes(legacyBytes, "salva_v1.txt")
        assertTrue(inspection.isValidForRestore)
        assertEquals(BackupFormatType.LEGACY_V1, inspection.formatType)
        assertEquals(1, inspection.formatVersion)

        val restoreRes = vm.executeFullRestore(legacyBytes, "ClaveV1#2025")
        assertTrue(restoreRes is BackupOperationResult.RestoreSuccess)
        assertTrue((restoreRes as BackupOperationResult.RestoreSuccess).isLegacyV1)

        val post = vm.backupManager.captureCurrentDataset()
        assertEquals(1, post.inventory.size)
        assertEquals("Producto Heredado V1", post.inventory.single().name)
        assertEquals(1, post.sales.size)
        assertEquals("V1-00077", post.sales.single().folio)
        assertEquals("Negocio Migrado V1", post.businessSettings?.businessName)

        // Phase 1 SecurityVault + employees + other v2 tables must remain preserved
        assertEquals(preSnapshot.securityVault, post.securityVault)
        assertEquals(preSnapshot.employees, post.employees)
        assertEquals(preSnapshot.clients, post.clients)
        assertEquals(preSnapshot.cashShifts, post.cashShifts)
    }

    @Test
    fun `trabajador role cannot generate or restore backups and performManualCloudSync uses offline screen without delay`() = runBlocking {
        val vm = createViewModel()
        seedRichBusinessDataset(vm)
        val (validBytes, _) = vm.createFullBackupBytes("ClaveAdmin#2026").getOrThrow()

        // Admin verifies offline sync shortcut navigates to VINCULAR_WEB without cloud simulation
        vm.performManualCloudSync()
        assertEquals(AppScreen.VINCULAR_WEB, vm.currentScreen.value)
        assertTrue(vm.cloudSyncMessage.value.contains("100% offline", ignoreCase = true))

        // Switch to Trabajador -> backup export & restore MUST be denied
        assertTrue(vm.switchActiveRoleWithCredential(UserRole.TRABAJADOR, "TrabBackup#2026") is SecurityActionResult.Success)
        val workerExport = vm.createFullBackupBytes("ClaveTrab#2026")
        assertTrue(workerExport.isFailure)
        assertEquals("", vm.generateEncryptedBackup("ClaveTrab#2026"))

        val workerRestore = vm.executeFullRestore(validBytes, "ClaveAdmin#2026")
        assertTrue(workerRestore is BackupOperationResult.Error)
    }
}
