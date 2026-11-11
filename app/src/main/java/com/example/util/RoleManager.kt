package com.example.util

import com.example.data.model.BusinessSettings
import com.example.data.model.UserRole
import com.example.ui.viewmodel.AppScreen

/**
 * Data class representing the full immutable snapshot of permissions
 * for the currently authenticated user in Caja Maestra.
 */
data class UserPermissions(
    val role: UserRole,

    // Module / Screen Level Access
    val canAccessInventory: Boolean,
    val canAccessSales: Boolean,
    val canAccessPurchases: Boolean,
    val canAccessCashflow: Boolean,
    val canAccessRentals: Boolean,
    val canAccessReports: Boolean,
    val canAccessEmployees: Boolean,
    val canAccessClients: Boolean,
    val canAccessSuppliers: Boolean,
    val canAccessThermalPrinter: Boolean,
    val canAccessCloudBackup: Boolean,
    val canAccessSettings: Boolean,

    // Creation / Operational Action Buttons
    val canCreateSale: Boolean,
    val canCreatePurchase: Boolean,
    val canCreateMovement: Boolean,
    val canCreateRental: Boolean,
    val canCreateClient: Boolean,
    val canCreateSupplier: Boolean,
    val canCreateEmployee: Boolean,

    // Deletion & Dangerous Actions
    val canDeleteInventoryItem: Boolean,
    val canDeleteClient: Boolean,
    val canDeleteSupplier: Boolean,
    val canDeleteExtraMovement: Boolean,
    val canDeleteRental: Boolean,
    val canDeleteEmployee: Boolean,
    val canWipeAllData: Boolean,
    val canRestoreBackup: Boolean,

    // Financial & Confidential Information
    val canViewCostPrice: Boolean,
    val canViewSupplierInvestment: Boolean,
    val canViewFinancialReports: Boolean,
    val canViewKpiRibbon: Boolean,

    // Hardware & Printer Controls
    val canPrintThermalReceipt: Boolean,
    val canConfigurePrinter: Boolean,
    val canEditReceiptHeaders: Boolean,

    // Administrative Control Panels
    val canAccessAdminPanel: Boolean,
    val canAccessJefePanel: Boolean,
    val canChangePasswords: Boolean
)

/**
 * Centralized Role and Privilege Manager for Caja Maestra.
 *
 * Enforces the strict three-tier privilege hierarchy:
 * 1. Admin (ADMINISTRADOR): Universal root control over all modules, license mode, and master switches.
 * 2. Jefe (DUENO): Store owner, operates within modules allowed by Admin, and configures
 *    what the subordinate Trabajador role can see and execute.
 * 3. Trabajador (TRABAJADOR): Operational frontline staff (cashier), limited by Jefe's policy,
 *    strictly restricted from seeing confidential cost prices, deleting financial or inventory records,
 *    and accessing unassigned modules.
 */
object RoleManager {

    /**
     * Resolves the complete UserPermissions set for the given settings and active role.
     */
    fun getPermissions(settings: BusinessSettings): UserPermissions {
        val role = settings.currentRole

        // 1. Admin has unrestricted root access
        if (role == UserRole.ADMINISTRADOR) {
            return UserPermissions(
                role = role,
                canAccessInventory = true,
                canAccessSales = true,
                canAccessPurchases = true,
                canAccessCashflow = true,
                canAccessRentals = true,
                canAccessReports = true,
                canAccessEmployees = true,
                canAccessClients = true,
                canAccessSuppliers = true,
                canAccessThermalPrinter = true,
                canAccessCloudBackup = true,
                canAccessSettings = true,
                canCreateSale = true,
                canCreatePurchase = true,
                canCreateMovement = true,
                canCreateRental = true,
                canCreateClient = true,
                canCreateSupplier = true,
                canCreateEmployee = true,
                canDeleteInventoryItem = true,
                canDeleteClient = true,
                canDeleteSupplier = true,
                canDeleteExtraMovement = true,
                canDeleteRental = true,
                canDeleteEmployee = true,
                canWipeAllData = true,
                canRestoreBackup = true,
                canViewCostPrice = true,
                canViewSupplierInvestment = true,
                canViewFinancialReports = true,
                canViewKpiRibbon = true,
                canPrintThermalReceipt = true,
                canConfigurePrinter = true,
                canEditReceiptHeaders = true,
                canAccessAdminPanel = true,
                canAccessJefePanel = true,
                canChangePasswords = true
            )
        }

        // 2. Global module switches enabled by Admin
        val adminInventory = settings.moduleInventoryEnabled
        val adminSales = settings.moduleSalesEnabled
        val adminPurchases = settings.modulePurchasesEnabled
        val adminCashflow = settings.moduleCashflowEnabled
        val adminRentals = settings.moduleRentalsEnabled
        val adminReports = settings.moduleReportsEnabled
        val adminEmployees = settings.moduleEmployeesEnabled
        val adminClients = settings.moduleClientsEnabled
        val adminSuppliers = settings.moduleSuppliersEnabled
        val adminPrinter = settings.moduleThermalPrinterEnabled
        val adminCloudBackup = settings.moduleCloudBackupEnabled
        val adminSettings = settings.moduleSettingsEnabled

        // 3. Jefe (Store Owner / Dueño) permissions
        if (role == UserRole.DUENO) {
            return UserPermissions(
                role = role,
                canAccessInventory = adminInventory,
                canAccessSales = adminSales,
                canAccessPurchases = adminPurchases,
                canAccessCashflow = adminCashflow,
                canAccessRentals = adminRentals,
                canAccessReports = adminReports,
                canAccessEmployees = adminEmployees,
                canAccessClients = adminClients,
                canAccessSuppliers = adminSuppliers,
                canAccessThermalPrinter = adminPrinter,
                canAccessCloudBackup = adminCloudBackup,
                canAccessSettings = adminSettings,
                canCreateSale = adminSales,
                canCreatePurchase = adminPurchases,
                canCreateMovement = adminCashflow,
                canCreateRental = adminRentals,
                canCreateClient = adminClients,
                canCreateSupplier = adminSuppliers,
                canCreateEmployee = adminEmployees,
                canDeleteInventoryItem = true,
                canDeleteClient = true,
                canDeleteSupplier = true,
                canDeleteExtraMovement = true,
                canDeleteRental = true,
                canDeleteEmployee = true,
                canWipeAllData = true,
                canRestoreBackup = adminCloudBackup,
                canViewCostPrice = true,
                canViewSupplierInvestment = true,
                canViewFinancialReports = adminReports,
                canViewKpiRibbon = adminReports,
                canPrintThermalReceipt = adminPrinter,
                canConfigurePrinter = adminPrinter,
                canEditReceiptHeaders = true,
                canAccessAdminPanel = false,
                canAccessJefePanel = true,
                canChangePasswords = true
            )
        }

        // 4. Trabajador (Operational / Frontline staff) permissions
        val trabajadorInventory = adminInventory && settings.jefeAllowsTrabajadorInventory
        val trabajadorSales = adminSales && settings.jefeAllowsTrabajadorSales
        val trabajadorPurchases = adminPurchases && settings.jefeAllowsTrabajadorPurchases
        val trabajadorCashflow = adminCashflow && settings.jefeAllowsTrabajadorCashflow
        val trabajadorRentals = adminRentals && settings.jefeAllowsTrabajadorRentals
        val trabajadorReports = adminReports && settings.jefeAllowsTrabajadorReports
        val trabajadorEmployees = adminEmployees && settings.jefeAllowsTrabajadorEmployees
        val trabajadorClients = adminClients && settings.jefeAllowsTrabajadorClients
        val trabajadorSuppliers = adminSuppliers && settings.jefeAllowsTrabajadorSuppliers
        val trabajadorSettings = adminSettings && settings.jefeAllowsTrabajadorSettings

        return UserPermissions(
            role = role,
            canAccessInventory = trabajadorInventory,
            canAccessSales = trabajadorSales,
            canAccessPurchases = trabajadorPurchases,
            canAccessCashflow = trabajadorCashflow,
            canAccessRentals = trabajadorRentals,
            canAccessReports = trabajadorReports,
            canAccessEmployees = trabajadorEmployees,
            canAccessClients = trabajadorClients,
            canAccessSuppliers = trabajadorSuppliers,
            canAccessThermalPrinter = false, // Printer settings reserved for Jefe/Admin
            canAccessCloudBackup = false, // Cloud backup settings reserved for Jefe/Admin
            canAccessSettings = trabajadorSettings,
            canCreateSale = trabajadorSales,
            canCreatePurchase = trabajadorPurchases,
            canCreateMovement = trabajadorCashflow,
            canCreateRental = trabajadorRentals,
            canCreateClient = trabajadorClients,
            canCreateSupplier = false, // Supplier registration reserved for Jefe/Admin
            canCreateEmployee = false, // Staff hiring reserved for Jefe/Admin
            canDeleteInventoryItem = false, // Deletion strictly forbidden for Trabajador
            canDeleteClient = false,
            canDeleteSupplier = false,
            canDeleteExtraMovement = false,
            canDeleteRental = false,
            canDeleteEmployee = false,
            canWipeAllData = false, // Database wipe strictly forbidden for Trabajador
            canRestoreBackup = false,
            canViewCostPrice = false, // Purchase/wholesale prices confidential
            canViewSupplierInvestment = false,
            canViewFinancialReports = trabajadorReports,
            canViewKpiRibbon = false, // Profit KPI cards confidential
            canPrintThermalReceipt = adminPrinter, // Can print receipt on sale if printer enabled
            canConfigurePrinter = false,
            canEditReceiptHeaders = false,
            canAccessAdminPanel = false,
            canAccessJefePanel = false,
            canChangePasswords = false
        )
    }

    /**
     * Determines if a screen is permitted for the active session.
     */
    fun canAccessScreen(screen: AppScreen, settings: BusinessSettings): Boolean {
        val perms = getPermissions(settings)
        return when (screen) {
            AppScreen.INVENTARIO -> perms.canAccessInventory
            AppScreen.VENTAS -> perms.canAccessSales
            AppScreen.COMPRAS -> perms.canAccessPurchases
            AppScreen.INGRESOS_GASTOS -> perms.canAccessCashflow
            AppScreen.RENTAS, AppScreen.AGENDA -> perms.canAccessRentals
            AppScreen.REPORTES -> perms.canAccessReports
            AppScreen.EMPLEADOS -> perms.canAccessEmployees
            AppScreen.CLIENTES -> perms.canAccessClients
            AppScreen.PROVEEDORES -> perms.canAccessSuppliers
            AppScreen.RECIBO_TICKET -> perms.canAccessThermalPrinter
            AppScreen.VINCULAR_WEB -> perms.canAccessCloudBackup
            AppScreen.CONFIGURACION -> perms.canAccessSettings
        }
    }

    /**
     * Finds the fallback permitted screen when a user navigates to a disabled module.
     */
    fun getFirstPermittedScreen(settings: BusinessSettings): AppScreen {
        val preferred = listOf(
            AppScreen.VENTAS,
            AppScreen.INVENTARIO,
            AppScreen.CLIENTES,
            AppScreen.CONFIGURACION
        )
        return preferred.firstOrNull { canAccessScreen(it, settings) } ?: AppScreen.VENTAS
    }
}
