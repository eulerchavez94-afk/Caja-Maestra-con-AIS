package com.example.data.backup

import com.example.data.model.BusinessSettings
import com.example.data.model.CashRegisterShift
import com.example.data.model.CashShiftMovement
import com.example.data.model.Client
import com.example.data.model.Employee
import com.example.data.model.ExtraMovement
import com.example.data.model.FolioSequence
import com.example.data.model.InventoryItem
import com.example.data.model.InventoryMovement
import com.example.data.model.Purchase
import com.example.data.model.RentalLoan
import com.example.data.model.Sale
import com.example.data.model.Supplier
import com.example.data.prefs.SecurityVault

enum class BackupFormatType {
    V2_ENVELOPE,
    LEGACY_V1,
    EMPTY,
    INVALID_FORMAT,
    UNSUPPORTED_VERSION
}

enum class RestoreStage {
    BEFORE_PREVENTIVE_SNAPSHOT,
    BEFORE_ROOM_TRANSACTION,
    INSIDE_ROOM_TRANSACTION_AFTER_CLEAR,
    AFTER_ROOM_BEFORE_DATASTORE,
    AFTER_DATASTORE_BEFORE_VERIFICATION
}

data class BackupCounts(
    val inventory: Int = 0,
    val sales: Int = 0,
    val purchases: Int = 0,
    val extraMovements: Int = 0,
    val rentalsLoans: Int = 0,
    val clients: Int = 0,
    val suppliers: Int = 0,
    val employees: Int = 0,
    val cashShifts: Int = 0,
    val cashShiftMovements: Int = 0,
    val inventoryMovements: Int = 0,
    val hasBusinessSettings: Boolean = true,
    val hasSecurityVault: Boolean = false
) {
    val totalRoomRecords: Int
        get() = inventory + sales + purchases + extraMovements + rentalsLoans +
            clients + suppliers + employees + cashShifts + cashShiftMovements + inventoryMovements
}

data class BackupDataset(
    val formatVersion: Int,
    val roomSchemaVersion: Int,
    val createdAt: Long,
    val inventory: List<InventoryItem>,
    val sales: List<Sale>,
    val purchases: List<Purchase>,
    val extraMovements: List<ExtraMovement>,
    val rentalsLoans: List<RentalLoan>,
    val clients: List<Client>,
    val suppliers: List<Supplier>,
    val employees: List<Employee>,
    val cashShifts: List<CashRegisterShift>,
    val cashShiftMovements: List<CashShiftMovement>,
    val businessSettings: BusinessSettings?,
    val securityVault: SecurityVault?,
    val isLegacyV1: Boolean = false,
    val legacyBusinessName: String? = null,
    val folioSequences: List<FolioSequence> = emptyList(),
    val inventoryMovements: List<InventoryMovement> = emptyList()
) {
    fun toCounts(): BackupCounts = BackupCounts(
        inventory = inventory.size,
        sales = sales.size,
        purchases = purchases.size,
        extraMovements = extraMovements.size,
        rentalsLoans = rentalsLoans.size,
        clients = clients.size,
        suppliers = suppliers.size,
        employees = employees.size,
        cashShifts = cashShifts.size,
        cashShiftMovements = cashShiftMovements.size,
        inventoryMovements = inventoryMovements.size,
        hasBusinessSettings = businessSettings != null,
        hasSecurityVault = securityVault != null
    )
}

data class BackupInspectionResult(
    val fileName: String,
    val sizeBytes: Long,
    val formatType: BackupFormatType,
    val formatVersion: Int,
    val roomSchemaVersion: Int,
    val createdAt: Long,
    val kdf: String,
    val cipher: String,
    val includesSecurityVault: Boolean,
    val isValidForRestore: Boolean,
    val statusMessage: String
)

sealed class BackupOperationResult {
    data class ExportSuccess(
        val fileName: String,
        val sizeBytes: Int,
        val counts: BackupCounts,
        val message: String
    ) : BackupOperationResult()

    data class RestoreSuccess(
        val formatVersion: Int,
        val isLegacyV1: Boolean,
        val counts: BackupCounts,
        val message: String
    ) : BackupOperationResult()

    data class Error(
        val message: String,
        val rolledBack: Boolean = false
    ) : BackupOperationResult()
}
