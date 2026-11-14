package com.example

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.example.data.db.CajaMaestraDatabase
import com.example.data.model.BusinessSettings
import com.example.data.model.Employee
import com.example.data.model.InventoryItem
import com.example.data.model.ReceiptFormat
import com.example.data.model.UserRole
import com.example.data.prefs.SettingsStorage
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.InitialSecuritySetupResult
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.RecoveryResult
import com.example.ui.viewmodel.SecurityActionResult
import com.example.util.PasswordHasher
import com.example.util.RoleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityPhase1Test {

    private lateinit var app: Application

    @Before
    fun resetPersistentState() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        SettingsStorage.getInstance(app).clearAllForTest()
    }

    private fun idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun createInitializedViewModel(): MainViewModel {
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

    @Test
    fun `setup rejects universal backdoors and strong setup authenticates every role`() = runBlocking {
        val vm = createInitializedViewModel()
        assertFalse(vm.securityState.value.isInitialized)

        // Reject universal legacy passwords & PIN 1234 during first-time setup
        val badAdmin = vm.initializeSecuritySetup("Admin", "DuenoClave99", "TrabClave99", "8520", "Ciudad natal?", "Oaxaca")
        assertTrue(badAdmin is InitialSecuritySetupResult.Error)

        val badPin = vm.initializeSecuritySetup("AdminClave99", "DuenoClave99", "TrabClave99", "1234", "Ciudad natal?", "Oaxaca")
        assertTrue(badPin is InitialSecuritySetupResult.Error)

        // Valid strong setup
        val okSetup = vm.initializeSecuritySetup(
            adminPassword = "AdminClave#2026",
            jefePassword = "DuenoClave#2026",
            trabajadorPassword = "Caja#2026",
            masterPin = "8520",
            recoveryQuestion = "¿Cuál fue tu primera mascota?",
            recoveryAnswer = "Firulais"
        )
        assertTrue(okSetup is InitialSecuritySetupResult.Success)
        vm.awaitPersistence()

        // Universal passwords must NEVER work
        assertFalse(vm.authenticateUser("Admin", "Admin"))
        assertFalse(vm.authenticateUser("Jefe", "Jefe"))
        assertFalse(vm.authenticateUser("Trabajador", "Trabajador"))
        assertFalse(vm.authenticateUser("Admin", "1234"))

        // Universal PIN 1234 must NEVER bypass security
        assertFalse(vm.verifyMasterPin("1234"))
        assertTrue(vm.verifyMasterPin("8520"))

        // Authenticate with each role using custom credentials
        assertTrue(vm.authenticateUser("Administrador", "AdminClave#2026"))
        assertEquals(UserRole.ADMINISTRADOR, vm.settings.value.currentRole)
        vm.logout()
        assertFalse(vm.isUserAuthenticated.value)

        assertTrue(vm.authenticateUser("Dueño", "DuenoClave#2026"))
        assertEquals(UserRole.DUENO, vm.settings.value.currentRole)
        vm.logout()

        assertTrue(vm.authenticateUser("Trabajador", "Caja#2026"))
        assertEquals(UserRole.TRABAJADOR, vm.settings.value.currentRole)
    }

    @Test
    fun `unauthorized role switching is blocked while credential-verified switching succeeds`() = runBlocking {
        val vm = createInitializedViewModel()
        vm.initializeSecuritySetup(
            adminPassword = "AdminClave#2026",
            jefePassword = "DuenoClave#2026",
            trabajadorPassword = "Caja#2026",
            masterPin = "7391",
            recoveryQuestion = "Color favorito?",
            recoveryAnswer = "Azul Marino"
        )
        vm.awaitPersistence()

        assertTrue(vm.authenticateUser("Trabajador", "Caja#2026"))
        assertEquals(UserRole.TRABAJADOR, vm.settings.value.currentRole)

        // Deprecated 1-tap switchActiveRole must NOT elevate privileges
        @Suppress("DEPRECATION")
        vm.switchActiveRole(UserRole.ADMINISTRADOR, "Atacante")
        assertEquals(UserRole.TRABAJADOR, vm.settings.value.currentRole)

        // Wrong credentials must be rejected
        val failedSwitch = vm.switchActiveRoleWithCredential(UserRole.DUENO, "Jefe")
        assertTrue(failedSwitch is SecurityActionResult.Error)
        assertEquals(UserRole.TRABAJADOR, vm.settings.value.currentRole)

        // Valid Owner credential authorizes role change
        val okDueno = vm.switchActiveRoleWithCredential(UserRole.DUENO, "DuenoClave#2026")
        assertTrue(okDueno is SecurityActionResult.Success)
        assertEquals(UserRole.DUENO, vm.settings.value.currentRole)
    }

    @Test
    fun `business logic enforces role permissions beyond UI hiding`() = runBlocking {
        val vm = createInitializedViewModel()
        vm.initializeSecuritySetup(
            adminPassword = "AdminClave#2026",
            jefePassword = "DuenoClave#2026",
            trabajadorPassword = "Caja#2026",
            masterPin = "6419",
            recoveryQuestion = "Escuela primaria?",
            recoveryAnswer = "Benito Juarez"
        )
        vm.awaitPersistence()

        // Admin disables reports module and enables worker settings so worker tries escalating settings
        assertTrue(vm.authenticateUser("Admin", "AdminClave#2026"))
        vm.updateSettings(
            vm.settings.value.copy(
                moduleReportsEnabled = false,
                jefeAllowsTrabajadorSettings = true,
                jefeAllowsTrabajadorReports = false
            )
        )
        val seededItem = InventoryItem(name = "Producto Test Guard", code = "TST-GUARD-1", salePrice = 50.0, costPrice = 20.0, stock = 5.0)
        vm.saveInventoryItem(seededItem)
        waitUntil {
            val res = vm.inventoryItems.value
            res.any { it.code == "TST-GUARD-1" }
        }
        val savedItem = vm.inventoryItems.value.first { it.code == "TST-GUARD-1" }

        // Switch to TRABAJADOR
        assertTrue(vm.switchActiveRoleWithCredential(UserRole.TRABAJADOR, "Caja#2026") is SecurityActionResult.Success)

        // Trabajador tries to delete inventory item, enable PIN, change password, or wipe DB
        vm.deleteInventoryItem(savedItem)
        vm.wipeAllData()
        assertTrue(vm.enablePinRestriction("9512") is SecurityActionResult.Error)
        assertTrue(vm.updateRolePassword(UserRole.DUENO, "Caja#2026", "HackClave99") is SecurityActionResult.Error)
        vm.setTrialMode(true, 99)

        // Trabajador tries to escalate privileges directly through updateSettings
        vm.updateSettings(
            vm.settings.value.copy(
                moduleReportsEnabled = true,
                jefeAllowsTrabajadorReports = true,
                currentRole = UserRole.ADMINISTRADOR,
                isAppLocked = false
            )
        )
        idleMainLooper()
        delay(200)
        idleMainLooper()

        // Confirm none of the escalations took effect
        assertEquals(UserRole.TRABAJADOR, vm.settings.value.currentRole)
        assertFalse(vm.settings.value.moduleReportsEnabled)
        assertFalse(vm.settings.value.jefeAllowsTrabajadorReports)
        assertFalse(vm.settings.value.trialModeEnabled)
        assertTrue(vm.inventoryItems.value.any { it.code == "TST-GUARD-1" })

        // Trabajador cannot navigate to a disabled screen
        val initialScreen = vm.currentScreen.value
        vm.navigateTo(AppScreen.REPORTES)
        assertEquals(initialScreen, vm.currentScreen.value)

        // Switch to DUENO: Dueño cannot create/modify an ADMINISTRADOR employee or change Admin password
        assertTrue(vm.switchActiveRoleWithCredential(UserRole.DUENO, "DuenoClave#2026") is SecurityActionResult.Success)
        assertTrue(vm.updateRolePassword(UserRole.ADMINISTRADOR, "DuenoClave#2026", "AdminHack99") is SecurityActionResult.Error)
        vm.saveEmployee(Employee(name = "FakeAdmin", role = UserRole.ADMINISTRADOR, pin = "9182"))
        idleMainLooper()
        delay(200)
        idleMainLooper()
        assertFalse(vm.employees.value.any { it.name == "FakeAdmin" })

        // Cleanup inserted test item as Admin
        assertTrue(vm.switchActiveRoleWithCredential(UserRole.ADMINISTRADOR, "AdminClave#2026") is SecurityActionResult.Success)
        vm.deleteInventoryItem(savedItem)
        waitUntil { vm.inventoryItems.value.none { it.code == "TST-GUARD-1" } }
    }

    @Test
    fun `settings, role permissions, printer, currency, passwords and room employees persist across app restart`() = runBlocking {
        // 1. First session: initialize security, customize all BusinessSettings, add Room employee
        val vmSession1 = createInitializedViewModel()
        vmSession1.initializeSecuritySetup(
            adminPassword = "AdminClave#2026",
            jefePassword = "DuenoClave#2026",
            trabajadorPassword = "Caja#2026",
            masterPin = "4826",
            recoveryQuestion = "Nombre del primer negocio?",
            recoveryAnswer = "Abarrotes Lupita"
        )
        vmSession1.awaitPersistence()

        assertTrue(vmSession1.authenticateUser("Admin", "AdminClave#2026"))
        val customSettings = vmSession1.settings.value.copy(
            businessName = "Ferretería El Roble",
            phone = "555-9876",
            address = "Av. Reforma 450",
            footerText = "¡Gracias por su compra en El Roble!",
            currencySymbol = "Q",
            numberFormat = "9999999.99",
            receiptFormat = ReceiptFormat.HOJA_CARTA,
            receiptShowQr = false,
            bluetoothPrinterName = "PTP-II 58mm Thermal",
            bluetoothPrinterAddress = "00:11:22:33:44:55",
            printScale = 1.25f,
            printIntensity = 210,
            externalScanner = true,
            continuousScan = true,
            moduleRentalsEnabled = false,
            jefeAllowsTrabajadorInventory = false,
            jefeAllowsTrabajadorPurchases = true
        )
        vmSession1.updateSettings(customSettings)
        assertTrue(vmSession1.enablePinRestriction("7744") is SecurityActionResult.Success)
        assertTrue(vmSession1.updateRolePassword(UserRole.TRABAJADOR, "AdminClave#2026", "NuevaTrab#2026") is SecurityActionResult.Success)

        // Add a Room employee with personal PIN (stored hashed in DB, not plaintext)
        val empName = "Lucia Cajera Persist"
        vmSession1.saveEmployee(Employee(name = empName, position = "Cajera", role = UserRole.TRABAJADOR, pin = "9630"))
        waitUntil { vmSession1.employees.value.any { it.name == empName } }
        vmSession1.awaitPersistence()

        // Verify PIN stored in Room is PBKDF2-hashed, never plaintext
        val storedEmp = vmSession1.employees.value.first { it.name == empName }
        assertNotEquals("9630", storedEmp.pin)
        assertTrue(PasswordHasher.isHashed(storedEmp.pin))
        assertTrue(PasswordHasher.verify("9630", storedEmp.pin))

        // 2. Simulate full app close & restart by creating a new MainViewModel instance
        val vmSession2 = createInitializedViewModel()

        // Restarted app must require authentication again
        assertFalse(vmSession2.isUserAuthenticated.value)
        assertTrue(vmSession2.securityState.value.isInitialized)

        // Verify ALL BusinessSettings survived process restart
        val reloaded = vmSession2.settings.value
        assertEquals("Ferretería El Roble", reloaded.businessName)
        assertEquals("555-9876", reloaded.phone)
        assertEquals("Av. Reforma 450", reloaded.address)
        assertEquals("¡Gracias por su compra en El Roble!", reloaded.footerText)
        assertEquals("Q", reloaded.currencySymbol)
        assertEquals("9999999.99", reloaded.numberFormat)
        assertEquals(ReceiptFormat.HOJA_CARTA, reloaded.receiptFormat)
        assertFalse(reloaded.receiptShowQr)
        assertEquals("PTP-II 58mm Thermal", reloaded.bluetoothPrinterName)
        assertEquals("00:11:22:33:44:55", reloaded.bluetoothPrinterAddress)
        assertEquals(1.25f, reloaded.printScale, 0.001f)
        assertEquals(210, reloaded.printIntensity)
        assertTrue(reloaded.externalScanner)
        assertTrue(reloaded.continuousScan)
        assertFalse(reloaded.moduleRentalsEnabled)
        assertFalse(reloaded.jefeAllowsTrabajadorInventory)
        assertTrue(reloaded.jefeAllowsTrabajadorPurchases)
        assertTrue(reloaded.restrictWithPin)

        // Verify updated Master PIN and updated passwords survived restart
        assertTrue(vmSession2.verifyMasterPin("7744"))
        assertFalse(vmSession2.verifyMasterPin("4826"))
        assertFalse(vmSession2.verifyMasterPin("1234"))

        // Old worker password must fail; updated password and Room employee personal PIN must work
        assertFalse(vmSession2.authenticateUser("Trabajador", "Caja#2026"))
        assertTrue(vmSession2.authenticateUser("Trabajador", "NuevaTrab#2026"))
        vmSession2.logout()

        waitUntil { vmSession2.employees.value.any { it.name == empName } }
        assertTrue(vmSession2.authenticateUser(empName, "9630"))
        assertEquals(UserRole.TRABAJADOR, vmSession2.settings.value.currentRole)
        assertEquals(empName, vmSession2.settings.value.currentUserName)

        // Clean up Room test employee
        assertTrue(vmSession2.switchActiveRoleWithCredential(UserRole.ADMINISTRADOR, "AdminClave#2026") is SecurityActionResult.Success)
        vmSession2.deleteEmployee(vmSession2.employees.value.first { it.name == empName })
        waitUntil { vmSession2.employees.value.none { it.name == empName } }
    }

    @Test
    fun `offline owner recovery via security answer and single-use recovery code works without backdoors`() = runBlocking {
        val vm = createInitializedViewModel()
        val setup = vm.initializeSecuritySetup(
            adminPassword = "AdminClave#2026",
            jefePassword = "DuenoOriginal#2026",
            trabajadorPassword = "Caja#2026",
            masterPin = "5192",
            recoveryQuestion = "Apodo de infancia?",
            recoveryAnswer = "  El Tigre  "
        ) as InitialSecuritySetupResult.Success
        vm.awaitPersistence()

        // 1. Wrong answer must fail
        val badAnswer = vm.recoverOwnerWithSecurityAnswer("El Leon", "DuenoNuevo#2026")
        assertTrue(badAnswer is RecoveryResult.Error)

        // 2. Normalized correct answer resets Owner password offline
        val okAnswer = vm.recoverOwnerWithSecurityAnswer("el tigre", "DuenoNuevo#2026")
        assertTrue(okAnswer is RecoveryResult.Success)
        vm.awaitPersistence()

        assertFalse(vm.authenticateUser("Dueño", "DuenoOriginal#2026"))
        assertTrue(vm.authenticateUser("Dueño", "DuenoNuevo#2026"))
        vm.logout()

        // 3. Recovery via one-time Owner recovery code rotates code immediately
        val okCodeRecovery = vm.recoverRoleWithCode(UserRole.DUENO, setup.ownerRecoveryCode, "DuenoRotado#2026")
        assertTrue(okCodeRecovery is RecoveryResult.Success)
        val rotatedCode = (okCodeRecovery as RecoveryResult.Success).newRecoveryCode!!
        assertNotEquals(setup.ownerRecoveryCode, rotatedCode)
        vm.awaitPersistence()

        // Reusing old code must fail
        val replay = vm.recoverRoleWithCode(UserRole.DUENO, setup.ownerRecoveryCode, "OtraClave#2026")
        assertTrue(replay is RecoveryResult.Error)

        // New rotated code works
        val rotatedUse = vm.recoverRoleWithCode(UserRole.DUENO, rotatedCode, "DuenoFinal#2026")
        assertTrue(rotatedUse is RecoveryResult.Success)
        assertTrue(vm.authenticateUser("Dueño", "DuenoFinal#2026"))
    }

    @Test
    fun `legacy plaintext employee pins migrate non-destructively clearing 1234 and hashing custom pins`() = runBlocking {
        val dao = CajaMaestraDatabase.getDatabase(app, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)).dao()
        val legacyDefaultEmpId = dao.insertEmployee(
            Employee(name = "Legacy Default 1234", role = UserRole.TRABAJADOR, pin = "1234")
        )
        val legacyCustomEmpId = dao.insertEmployee(
            Employee(name = "Legacy Custom 6789", role = UserRole.DUENO, pin = "6789")
        )

        val vm = createInitializedViewModel()
        vm.initializeSecuritySetup(
            adminPassword = "AdminClave#2026",
            jefePassword = "DuenoClave#2026",
            trabajadorPassword = "Caja#2026",
            masterPin = "4913",
            recoveryQuestion = "Mes de apertura?",
            recoveryAnswer = "Octubre"
        )
        vm.awaitPersistence()

        val allEmps = dao.getAllEmployeesSync()
        val migratedDefault = allEmps.first { it.id == legacyDefaultEmpId }
        val migratedCustom = allEmps.first { it.id == legacyCustomEmpId }

        // "1234" seed PIN must be cleared without deleting the employee record
        assertEquals("", migratedDefault.pin)
        assertFalse(vm.authenticateUser("Legacy Default 1234", "1234"))

        // Custom plaintext PIN must be converted to salted PBKDF2 hash and still authenticate
        assertTrue(PasswordHasher.isHashed(migratedCustom.pin))
        assertTrue(PasswordHasher.verify("6789", migratedCustom.pin))
        waitUntil { vm.employees.value.any { it.id == legacyCustomEmpId } }
        assertTrue(vm.authenticateUser("Legacy Custom 6789", "6789"))

        // Clean up
        dao.deleteEmployee(migratedDefault)
        dao.deleteEmployee(migratedCustom)
    }

    @Test
    fun `brute force failed attempts trigger lockout that persists across app restart`() = runBlocking {
        val vm1 = createInitializedViewModel()
        vm1.initializeSecuritySetup(
            adminPassword = "AdminClave#2026",
            jefePassword = "DuenoClave#2026",
            trabajadorPassword = "Caja#2026",
            masterPin = "8246",
            recoveryQuestion = "Nombre de calle?",
            recoveryAnswer = "Insurgentes"
        )
        vm1.awaitPersistence()

        repeat(5) {
            assertFalse(vm1.authenticateUser("Admin", "WrongPass$it"))
        }
        vm1.awaitPersistence()
        assertTrue(vm1.securityState.value.lockoutUntilMs > System.currentTimeMillis())

        // Even with the real password, login must be blocked during lockout
        assertFalse(vm1.authenticateUser("Admin", "AdminClave#2026"))

        // Even after restarting app, lockout persists
        val vm2 = createInitializedViewModel()
        assertFalse(vm2.authenticateUser("Admin", "AdminClave#2026"))
        assertTrue(vm2.securityState.value.lockoutUntilMs > System.currentTimeMillis())
    }
}
