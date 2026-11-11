package com.example

import com.example.data.model.BusinessSettings
import com.example.data.model.UserRole
import com.example.ui.viewmodel.AppScreen
import com.example.util.RoleManager
import org.junit.Assert.*
import org.junit.Test

class RoleManagerTest {

    @Test
    fun adminHasUnrestrictedPermissions() {
        val settings = BusinessSettings(
            currentRole = UserRole.ADMINISTRADOR,
            moduleThermalPrinterEnabled = false, // Even if toggled off in settings, Admin retains root control
            moduleInventoryEnabled = false
        )

        val perms = RoleManager.getPermissions(settings)

        assertTrue(perms.canAccessAdminPanel)
        assertTrue(perms.canAccessJefePanel)
        assertTrue(perms.canWipeAllData)
        assertTrue(perms.canDeleteInventoryItem)
        assertTrue(perms.canViewCostPrice)
        assertTrue(perms.canConfigurePrinter)
        assertTrue(perms.canChangePasswords)
        assertTrue(RoleManager.canAccessScreen(AppScreen.CONFIGURACION, settings))
    }

    @Test
    fun jefeIsGovernedByAdminModuleSwitches() {
        val settingsWithPrinterDisabled = BusinessSettings(
            currentRole = UserRole.DUENO,
            moduleThermalPrinterEnabled = false,
            modulePurchasesEnabled = true
        )

        val perms = RoleManager.getPermissions(settingsWithPrinterDisabled)

        assertFalse("Jefe cannot access printer when Admin disables printer module", perms.canAccessThermalPrinter)
        assertFalse("Jefe cannot configure printer when Admin disables printer module", perms.canConfigurePrinter)
        assertFalse("Jefe cannot print thermal receipts when Admin disables printer module", perms.canPrintThermalReceipt)
        assertTrue("Jefe has purchases access", perms.canAccessPurchases)
        assertTrue("Jefe can view wholesale/cost price", perms.canViewCostPrice)
        assertFalse("Jefe cannot access Admin master panel", perms.canAccessAdminPanel)
        assertTrue("Jefe can access Jefe limiter panel", perms.canAccessJefePanel)
    }

    @Test
    fun trabajadorIsStrictlyRestrictedFromConfidentialAndDestructiveActions() {
        val settings = BusinessSettings(
            currentRole = UserRole.TRABAJADOR,
            moduleInventoryEnabled = true,
            jefeAllowsTrabajadorInventory = true,
            jefeAllowsTrabajadorSales = true
        )

        val perms = RoleManager.getPermissions(settings)

        // Confidentiality checks
        assertFalse("Trabajador cannot view confidential cost price", perms.canViewCostPrice)
        assertFalse("Trabajador cannot view profit KPI ribbon", perms.canViewKpiRibbon)
        assertFalse("Trabajador cannot view supplier investments", perms.canViewSupplierInvestment)

        // Destructive actions checks
        assertFalse("Trabajador cannot delete inventory items", perms.canDeleteInventoryItem)
        assertFalse("Trabajador cannot delete clients", perms.canDeleteClient)
        assertFalse("Trabajador cannot delete suppliers", perms.canDeleteSupplier)
        assertFalse("Trabajador cannot delete extra movements", perms.canDeleteExtraMovement)
        assertFalse("Trabajador cannot wipe all data", perms.canWipeAllData)
        assertFalse("Trabajador cannot restore backup", perms.canRestoreBackup)

        // Administration checks
        assertFalse("Trabajador cannot access Admin panel", perms.canAccessAdminPanel)
        assertFalse("Trabajador cannot access Jefe panel", perms.canAccessJefePanel)
        assertFalse("Trabajador cannot change passwords", perms.canChangePasswords)
        assertFalse("Trabajador cannot configure printer hardware", perms.canConfigurePrinter)
    }

    @Test
    fun fallbackScreenSelectionWorksWhenScreensAreDisabled() {
        val settings = BusinessSettings(
            currentRole = UserRole.DUENO,
            moduleSalesEnabled = false,
            moduleInventoryEnabled = true
        )

        val fallback = RoleManager.getFirstPermittedScreen(settings)
        assertEquals(AppScreen.INVENTARIO, fallback)
    }
}
