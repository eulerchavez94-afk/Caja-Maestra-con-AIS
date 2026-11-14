package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ItemType {
    PRODUCTO,
    MANUFACTURA,
    SERVICIO
}

enum class SaleType {
    VENTA,
    COTIZACION
}

enum class SaleStatus {
    PAGADA,
    CREDITO,
    CANCELADA,
    PENDIENTE
}

enum class MovementType {
    INGRESO,
    GASTO
}

enum class UserRole {
    DUENO,
    ADMINISTRADOR,
    TRABAJADOR
}

enum class ReceiptFormat {
    TICKET_58MM,
    HOJA_CARTA
}

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["name"]),
        Index(value = ["code"]),
        Index(value = ["type"]),
        Index(value = ["category"])
    ]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    val costPrice: Double,
    val salePrice: Double,
    val stock: Double,
    val minStock: Double = 5.0,
    val category: String = "General",
    val unit: String = "Pza",
    val type: ItemType = ItemType.PRODUCTO,
    val isWaste: Boolean = false,
    val isRawMaterial: Boolean = false,
    val notes: String = ""
)

enum class InventoryMovementType {
    ENTRADA_COMPRA,
    SALIDA_VENTA,
    RESTITUCION_CANCELACION,
    AJUSTE_MANUAL,
    ALTA_INVENTARIO,
    EDICION_STOCK
}

@Entity(
    tableName = "inventory_movements",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["date"]),
        Index(value = ["movementType"])
    ]
)
data class InventoryMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val itemCode: String,
    val itemName: String,
    val movementType: InventoryMovementType,
    val quantityDelta: Double,
    val previousStock: Double,
    val newStock: Double,
    val referenceFolio: String = "",
    val reason: String = "",
    val performedBy: String = "",
    val date: Long = System.currentTimeMillis()
)

data class SaleCartItem(
    val itemId: Long,
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val costPrice: Double,
    val unit: String,
    val type: ItemType
) {
    val subtotal: Double get() = quantity * unitPrice
    val totalCost: Double get() = quantity * costPrice
}

enum class CashShiftMovementType {
    ENTRADA,
    RETIRO
}

@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["date"]),
        Index(value = ["folio"]),
        Index(value = ["type"]),
        Index(value = ["status"]),
        Index(value = ["customerId"]),
        Index(value = ["shiftId"])
    ]
)
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folio: String,
    val type: SaleType = SaleType.VENTA,
    val status: SaleStatus = SaleStatus.PAGADA,
    val customerId: Long? = null,
    val customerName: String = "Público General",
    val subtotal: Double,
    val discount: Double = 0.0,
    val total: Double,
    val paidAmount: Double = 0.0,
    val changeAmount: Double = 0.0,
    val paymentMethod: String = "Efectivo",
    val date: Long = System.currentTimeMillis(),
    val sellerName: String = "Administrador",
    val notes: String = "",
    val itemsJson: String = "",
    val shiftId: Long? = null
)

@Entity(
    tableName = "cash_shifts",
    indices = [
        Index(value = ["openedAt"]),
        Index(value = ["isOpen"])
    ]
)
data class CashRegisterShift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cashierName: String,
    val cashierRole: UserRole = UserRole.TRABAJADOR,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val openingFloat: Double = 0.0,
    val cashSalesTotal: Double = 0.0,
    val cardSalesTotal: Double = 0.0,
    val transferSalesTotal: Double = 0.0,
    val creditSalesTotal: Double = 0.0,
    val cashEntriesTotal: Double = 0.0,
    val cashWithdrawalsTotal: Double = 0.0,
    val expectedCash: Double = 0.0,
    val countedCash: Double = 0.0,
    val difference: Double = 0.0,
    val nextTurnFloat: Double = 0.0,
    val salesCount: Int = 0,
    val isOpen: Boolean = true,
    val openingNotes: String = "",
    val closingNotes: String = ""
)

@Entity(
    tableName = "cash_shift_movements",
    indices = [
        Index(value = ["shiftId"]),
        Index(value = ["date"])
    ]
)
data class CashShiftMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val type: CashShiftMovementType,
    val amount: Double,
    val reason: String,
    val performedBy: String,
    val date: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchases",
    indices = [
        Index(value = ["date"]),
        Index(value = ["folio"]),
        Index(value = ["supplierId"])
    ]
)
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folio: String,
    val supplierId: Long? = null,
    val supplierName: String = "Proveedor General",
    val total: Double,
    val date: Long = System.currentTimeMillis(),
    val isCredit: Boolean = false,
    val notes: String = "",
    val itemsJson: String = ""
)

@Entity(
    tableName = "extra_movements",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["category"])
    ]
)
data class ExtraMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MovementType,
    val concept: String,
    val category: String,
    val amount: Double,
    val isCredit: Boolean = false,
    val date: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "rentals_loans",
    indices = [
        Index(value = ["startDate"]),
        Index(value = ["isRental"]),
        Index(value = ["isReturnedOrPaid"])
    ]
)
data class RentalLoan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isRental: Boolean, // true = Renta, false = Préstamo
    val contactName: String,
    val itemOrReason: String,
    val amount: Double,
    val depositAmount: Double = 0.0,
    val startDate: Long = System.currentTimeMillis(),
    val dueDate: Long = System.currentTimeMillis() + 7L * 24 * 3600 * 1000,
    val isReturnedOrPaid: Boolean = false,
    val notes: String = ""
)

@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"])
    ]
)
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val company: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val creditBalance: Double = 0.0,
    val notes: String = ""
)

@Entity(
    tableName = "suppliers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["company"])
    ]
)
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val company: String = "",
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val suppliedProducts: String = "",
    val paymentTerms: String = "Contado",
    val notes: String = ""
)

@Entity(
    tableName = "employees",
    indices = [
        Index(value = ["name"]),
        Index(value = ["role"])
    ]
)
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: String = "Vendedor",
    val salary: Double = 0.0,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val role: UserRole = UserRole.TRABAJADOR,
    val pin: String = ""
)

@Entity(tableName = "folio_sequences")
data class FolioSequence(
    @PrimaryKey val sequenceKey: String,
    val lastNumber: Long
)

data class BusinessSettings(
    val businessName: String = "Mi Negocio Principal",
    val phone: String = "+52 55 1234 5678",
    val email: String = "contacto@cajamaestra.pos",
    val address: String = "Av. Principal #100, Centro",
    val footerText: String = "¡Gracias por su compra! Vuelva pronto.",
    val customQr: String = "https://cajamaestra.app/recibo",
    val currencySymbol: String = "$",
    val numberFormat: String = "9,999,999.99",
    val checkStock: Boolean = true,
    val useAverageCost: Boolean = true,
    val allowMultipleSales: Boolean = true,
    val allowEditDate: Boolean = true,
    val showCustomerPayAndChange: Boolean = true,
    val receiptFormat: ReceiptFormat = ReceiptFormat.TICKET_58MM,
    val receiptShowClientInfo: Boolean = true,
    val receiptShowQr: Boolean = true,
    val receiptShowPaymentBreakdown: Boolean = true,
    val receiptShowFolio: Boolean = true,
    val receiptShowStatus: Boolean = true,
    val receiptShowKeyAndUnit: Boolean = true,
    val bluetoothPrinterName: String = "Sin vincular",
    val bluetoothPrinterAddress: String = "",
    val printScale: Float = 1.0f,
    val printIntensity: Int = 165,
    val restrictWithPin: Boolean = false,
    val currentRole: UserRole = UserRole.DUENO,
    val currentUserName: String = "Jefe / Dueño",
    val externalScanner: Boolean = false,
    val continuousScan: Boolean = true,
    val frontCamera: Boolean = false,
    // Admin App Control & License/Trial Mode
    val isAppLocked: Boolean = false,
    val trialModeEnabled: Boolean = false,
    val trialDurationDays: Int = 7,
    val trialStartDate: Long = 0L,
    val appLockReason: String = "Periodo de prueba finalizado. Contacte al Administrador.",
    // Admin Module Permissions (Toggles controlled by Admin)
    val moduleInventoryEnabled: Boolean = true,
    val moduleSalesEnabled: Boolean = true,
    val modulePurchasesEnabled: Boolean = true,
    val moduleCashflowEnabled: Boolean = true,
    val moduleRentalsEnabled: Boolean = true,
    val moduleReportsEnabled: Boolean = true,
    val moduleEmployeesEnabled: Boolean = true,
    val moduleClientsEnabled: Boolean = true,
    val moduleSuppliersEnabled: Boolean = true,
    val moduleThermalPrinterEnabled: Boolean = true,
    val moduleCloudBackupEnabled: Boolean = true,
    val moduleSettingsEnabled: Boolean = true,
    // Jefe (Store Owner) Permissions for Trabajador (Role Limiter)
    val jefeAllowsTrabajadorInventory: Boolean = true,
    val jefeAllowsTrabajadorSales: Boolean = true,
    val jefeAllowsTrabajadorPurchases: Boolean = false,
    val jefeAllowsTrabajadorCashflow: Boolean = false,
    val jefeAllowsTrabajadorRentals: Boolean = true,
    val jefeAllowsTrabajadorReports: Boolean = false,
    val jefeAllowsTrabajadorEmployees: Boolean = false,
    val jefeAllowsTrabajadorClients: Boolean = true,
    val jefeAllowsTrabajadorSuppliers: Boolean = false,
    val jefeAllowsTrabajadorSettings: Boolean = false
)

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean = true
)
