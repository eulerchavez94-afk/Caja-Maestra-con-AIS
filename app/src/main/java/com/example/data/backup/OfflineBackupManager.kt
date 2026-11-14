package com.example.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.data.db.CajaMaestraDatabase
import com.example.data.model.BusinessSettings
import com.example.data.prefs.SecurityVault
import com.example.data.prefs.SettingsStorage
import com.example.util.PasswordHasher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class OfflineBackupManager(
    private val context: Context,
    private val db: CajaMaestraDatabase,
    private val settingsStorage: SettingsStorage
) {
    private val dao = db.dao()
    private val restoreMutex = Mutex()
    private val journalDir = File(context.applicationContext.filesDir, "backup_journal")
    private val journalDataFile = File(journalDir, "preventive_rollback.enc")
    private val pendingFlagFile = File(journalDir, "restore_pending.flag")

    fun validateExportPassphrase(passphrase: String): String? {
        val clean = passphrase.trim()
        if (clean.length < 6) {
            return "La contraseña del respaldo debe tener al menos 6 caracteres."
        }
        if (clean == "CajaMaestraSecret2026" || PasswordHasher.isLegacyDefaultCredential(clean)) {
            return "Por seguridad, elige una contraseña personal distinta a las claves predeterminadas."
        }
        return null
    }

    suspend fun captureCurrentDataset(): BackupDataset {
        val storedState = settingsStorage.loadState()
        return db.withTransaction {
            BackupDataset(
                formatVersion = BackupSerializer.CURRENT_FORMAT_VERSION,
                roomSchemaVersion = BackupSerializer.ROOM_SCHEMA_VERSION,
                createdAt = System.currentTimeMillis(),
                inventory = dao.getAllInventoryItemsSync(),
                sales = dao.getAllSalesSync(),
                purchases = dao.getAllPurchasesSync(),
                extraMovements = dao.getAllExtraMovementsSync(),
                rentalsLoans = dao.getAllRentalsLoansSync(),
                clients = dao.getAllClientsSync(),
                suppliers = dao.getAllSuppliersSync(),
                employees = dao.getAllEmployeesSync(),
                cashShifts = dao.getAllCashShiftsSync(),
                cashShiftMovements = dao.getAllShiftMovementsSync(),
                businessSettings = storedState.settings,
                securityVault = storedState.vault,
                isLegacyV1 = false,
                folioSequences = dao.getAllFolioSequencesSync(),
                inventoryMovements = dao.getAllInventoryMovementsSync()
            )
        }
    }

    suspend fun createEncryptedBackupBytes(passphrase: String): Result<Pair<ByteArray, BackupCounts>> {
        val validationError = validateExportPassphrase(passphrase)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        return try {
            val dataset = captureCurrentDataset()
            val bytes = BackupSerializer.serializeEncryptedV2(dataset, passphrase.trim())
            Result.success(bytes to dataset.toCounts())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun inspectBackupBytes(bytes: ByteArray, fileName: String = ""): BackupInspectionResult {
        return BackupSerializer.inspectBytes(bytes, fileName)
    }

    fun writeShareableBackupFile(fileName: String, bytes: ByteArray): Result<File> {
        return try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val backupsCacheDir = File(context.applicationContext.cacheDir, "backups")
            backupsCacheDir.mkdirs()
            val outFile = File(backupsCacheDir, safeName)
            outFile.outputStream().use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeLocalArchiveFile(fileName: String, bytes: ByteArray): Result<File> {
        return try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val localDir = File(context.applicationContext.filesDir, "backups")
            localDir.mkdirs()
            val outFile = File(localDir, safeName)
            outFile.outputStream().use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackupBytes(
        bytes: ByteArray,
        passphrase: String,
        faultInjector: ((RestoreStage) -> Unit)? = null
    ): BackupOperationResult {
        if (bytes.isEmpty()) {
            return BackupOperationResult.Error("El archivo de respaldo está vacío.", rolledBack = false)
        }
        val cleanPass = passphrase.trim()
        if (cleanPass.isEmpty()) {
            return BackupOperationResult.Error("Ingresa la contraseña del respaldo.", rolledBack = false)
        }

        val inspection = BackupSerializer.inspectBytes(bytes)
        if (!inspection.isValidForRestore) {
            return BackupOperationResult.Error(inspection.statusMessage, rolledBack = false)
        }

        val dataset = BackupSerializer.decryptAndParse(bytes, cleanPass).getOrElse { err ->
            return BackupOperationResult.Error(
                err.message ?: "No se pudo descifrar el respaldo. Verifica la contraseña.",
                rolledBack = false
            )
        }

        val semanticError = validateDataset(dataset)
        if (semanticError != null) {
            return BackupOperationResult.Error(semanticError, rolledBack = false)
        }

        return restoreMutex.withLock {
            faultInjector?.invoke(RestoreStage.BEFORE_PREVENTIVE_SNAPSHOT)
            val preventiveSnapshot = captureCurrentDataset()

            try {
                writePreventiveJournal(preventiveSnapshot)
            } catch (e: Exception) {
                clearPreventiveJournal()
                return@withLock BackupOperationResult.Error(
                    "No se pudo crear el respaldo preventivo antes de restaurar: ${e.message}",
                    rolledBack = false
                )
            }

            try {
                faultInjector?.invoke(RestoreStage.BEFORE_ROOM_TRANSACTION)

                if (dataset.isLegacyV1) {
                    applyLegacyV1Restore(dataset, preventiveSnapshot, faultInjector)
                } else {
                    applyFullV2Restore(dataset, faultInjector)
                }

                // Post-restore integrity verification across Room + DataStore
                verifyPostRestoreIntegrity(dataset, preventiveSnapshot)

                clearPreventiveJournal()

                val counts = if (dataset.isLegacyV1) {
                    BackupCounts(
                        inventory = dataset.inventory.size,
                        sales = dataset.sales.size,
                        purchases = preventiveSnapshot.purchases.size,
                        extraMovements = preventiveSnapshot.extraMovements.size,
                        rentalsLoans = preventiveSnapshot.rentalsLoans.size,
                        clients = preventiveSnapshot.clients.size,
                        suppliers = preventiveSnapshot.suppliers.size,
                        employees = preventiveSnapshot.employees.size,
                        cashShifts = preventiveSnapshot.cashShifts.size,
                        cashShiftMovements = preventiveSnapshot.cashShiftMovements.size,
                        hasBusinessSettings = true,
                        hasSecurityVault = preventiveSnapshot.securityVault != null
                    )
                } else {
                    dataset.toCounts()
                }

                BackupOperationResult.RestoreSuccess(
                    formatVersion = dataset.formatVersion,
                    isLegacyV1 = dataset.isLegacyV1,
                    counts = counts,
                    message = if (dataset.isLegacyV1) {
                        "Respaldo heredado v1 restaurado e integrado (${counts.inventory} productos, ${counts.sales} ventas)."
                    } else {
                        "Restauración completa verificada (${counts.totalRoomRecords} registros Room, configuración y bóveda)."
                    }
                )
            } catch (e: Exception) {
                val rolledBackCleanly = rollbackToPreventiveSnapshot(preventiveSnapshot)
                if (rolledBackCleanly) {
                    clearPreventiveJournal()
                }
                val suffix = if (rolledBackCleanly) {
                    " Se restauró automáticamente la información original sin pérdidas."
                } else {
                    " Se conservó el respaldo preventivo local para recuperación automática al reiniciar."
                }
                BackupOperationResult.Error(
                    message = "Error durante la restauración: ${e.message ?: "fallo inesperado"}.$suffix",
                    rolledBack = rolledBackCleanly
                )
            }
        }
    }

    suspend fun recoverPendingInterruptedRestoreIfNeeded(): Boolean {
        if (!pendingFlagFile.exists() || !journalDataFile.exists()) return false
        return restoreMutex.withLock {
            try {
                val blob = journalDataFile.readText(Charsets.UTF_8)
                val plainBytes = settingsStorage.decryptBytesWithLocalKeyStore(blob)
                val snapshot = BackupSerializer.parseSnapshotJson(plainBytes)
                val recovered = rollbackToPreventiveSnapshot(snapshot)
                if (recovered) {
                    clearPreventiveJournal()
                }
                recovered
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun applyFullV2Restore(
        dataset: BackupDataset,
        faultInjector: ((RestoreStage) -> Unit)?
    ) {
        val targetSettings = dataset.businessSettings
            ?: throw IllegalStateException("El respaldo v2 no contiene configuración del negocio.")
        val targetVault = dataset.securityVault
            ?: throw IllegalStateException("El respaldo v2 no contiene información de seguridad.")

        // 1. Atomic Room transaction
        db.withTransaction {
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

            faultInjector?.invoke(RestoreStage.INSIDE_ROOM_TRANSACTION_AFTER_CLEAR)

            if (dataset.inventory.isNotEmpty()) dao.insertAllInventoryItems(dataset.inventory)
            if (dataset.clients.isNotEmpty()) dao.insertAllClients(dataset.clients)
            if (dataset.suppliers.isNotEmpty()) dao.insertAllSuppliers(dataset.suppliers)
            if (dataset.employees.isNotEmpty()) dao.insertAllEmployees(dataset.employees)
            if (dataset.cashShifts.isNotEmpty()) dao.insertAllCashShifts(dataset.cashShifts)
            if (dataset.cashShiftMovements.isNotEmpty()) dao.insertAllCashShiftMovements(dataset.cashShiftMovements)
            if (dataset.sales.isNotEmpty()) dao.insertAllSales(dataset.sales)
            if (dataset.purchases.isNotEmpty()) dao.insertAllPurchases(dataset.purchases)
            if (dataset.extraMovements.isNotEmpty()) dao.insertAllExtraMovements(dataset.extraMovements)
            if (dataset.rentalsLoans.isNotEmpty()) dao.insertAllRentalsLoans(dataset.rentalsLoans)
            if (dataset.inventoryMovements.isNotEmpty()) dao.insertAllInventoryMovements(dataset.inventoryMovements)

            val sequencesToRestore = CajaMaestraDatabase.deriveFolioSequences(
                sales = dataset.sales,
                purchases = dataset.purchases,
                existing = dataset.folioSequences
            )
            dao.insertAllFolioSequences(sequencesToRestore)
        }

        faultInjector?.invoke(RestoreStage.AFTER_ROOM_BEFORE_DATASTORE)

        // 2. Persist BusinessSettings + re-encrypt SecurityVault with destination device's AndroidKeyStore
        settingsStorage.saveAll(targetSettings, targetVault)

        faultInjector?.invoke(RestoreStage.AFTER_DATASTORE_BEFORE_VERIFICATION)
    }

    private suspend fun applyLegacyV1Restore(
        dataset: BackupDataset,
        preventiveSnapshot: BackupDataset,
        faultInjector: ((RestoreStage) -> Unit)?
    ) {
        db.withTransaction {
            dao.clearInventory()
            dao.clearSales()
            faultInjector?.invoke(RestoreStage.INSIDE_ROOM_TRANSACTION_AFTER_CLEAR)
            if (dataset.inventory.isNotEmpty()) dao.insertAllInventoryItems(dataset.inventory)
            if (dataset.sales.isNotEmpty()) dao.insertAllSales(dataset.sales)
            val existingSeq = dao.getAllFolioSequencesSync()
            val updatedSeq = CajaMaestraDatabase.deriveFolioSequences(
                sales = dataset.sales,
                purchases = preventiveSnapshot.purchases,
                existing = existingSeq
            )
            dao.insertAllFolioSequences(updatedSeq)
        }

        faultInjector?.invoke(RestoreStage.AFTER_ROOM_BEFORE_DATASTORE)

        val currentSettings = preventiveSnapshot.businessSettings ?: BusinessSettings()
        val updatedSettings = dataset.legacyBusinessName?.let { name ->
            currentSettings.copy(businessName = name)
        } ?: currentSettings
        val currentVault = preventiveSnapshot.securityVault ?: SecurityVault()
        settingsStorage.saveAll(updatedSettings, currentVault)

        faultInjector?.invoke(RestoreStage.AFTER_DATASTORE_BEFORE_VERIFICATION)
    }

    private suspend fun verifyPostRestoreIntegrity(
        expected: BackupDataset,
        preventive: BackupDataset
    ) {
        val currentAfter = captureCurrentDataset()

        if (expected.isLegacyV1) {
            check(currentAfter.inventory.size == expected.inventory.size) {
                "Conteo de inventario restaurado no coincide (${currentAfter.inventory.size} vs ${expected.inventory.size})."
            }
            check(currentAfter.sales.size == expected.sales.size) {
                "Conteo de ventas restaurado no coincide (${currentAfter.sales.size} vs ${expected.sales.size})."
            }
            check(currentAfter.employees.size == preventive.employees.size) {
                "Los empleados existentes sufrieron alteraciones durante restauración v1."
            }
            return
        }

        check(currentAfter.inventory.size == expected.inventory.size) { "Discrepancia en inventario tras restaurar." }
        check(currentAfter.sales.size == expected.sales.size) { "Discrepancia en ventas tras restaurar." }
        check(currentAfter.purchases.size == expected.purchases.size) { "Discrepancia en compras tras restaurar." }
        check(currentAfter.extraMovements.size == expected.extraMovements.size) { "Discrepancia en movimientos extra tras restaurar." }
        check(currentAfter.rentalsLoans.size == expected.rentalsLoans.size) { "Discrepancia en rentas tras restaurar." }
        check(currentAfter.clients.size == expected.clients.size) { "Discrepancia en clientes tras restaurar." }
        check(currentAfter.suppliers.size == expected.suppliers.size) { "Discrepancia en proveedores tras restaurar." }
        check(currentAfter.employees.size == expected.employees.size) { "Discrepancia en empleados tras restaurar." }
        check(currentAfter.cashShifts.size == expected.cashShifts.size) { "Discrepancia en turnos de caja tras restaurar." }
        check(currentAfter.cashShiftMovements.size == expected.cashShiftMovements.size) { "Discrepancia en movimientos de caja tras restaurar." }
        check(currentAfter.inventoryMovements.size == expected.inventoryMovements.size) { "Discrepancia en movimientos de inventario tras restaurar." }

        val shiftIds = currentAfter.cashShifts.map { it.id }.toHashSet()
        check(currentAfter.cashShiftMovements.all { shiftIds.contains(it.shiftId) }) {
            "Integridad referencial fallida entre movimientos de caja y turnos."
        }
        check(currentAfter.sales.all { it.shiftId == null || shiftIds.contains(it.shiftId) }) {
            "Integridad referencial fallida entre ventas y turnos de caja."
        }

        val expectedVault = expected.securityVault
        val actualVault = currentAfter.securityVault
        if (expectedVault != null) {
            check(actualVault != null &&
                actualVault.isInitialized == expectedVault.isInitialized &&
                actualVault.adminPasswordHash == expectedVault.adminPasswordHash &&
                actualVault.jefePasswordHash == expectedVault.jefePasswordHash &&
                actualVault.trabajadorPasswordHash == expectedVault.trabajadorPasswordHash &&
                actualVault.masterPinHash == expectedVault.masterPinHash
            ) {
                "La verificación de SecurityVault reprotegida en AndroidKeyStore no coincide."
            }
        }
    }

    private suspend fun rollbackToPreventiveSnapshot(snapshot: BackupDataset): Boolean {
        return try {
            db.withTransaction {
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

                if (snapshot.inventory.isNotEmpty()) dao.insertAllInventoryItems(snapshot.inventory)
                if (snapshot.clients.isNotEmpty()) dao.insertAllClients(snapshot.clients)
                if (snapshot.suppliers.isNotEmpty()) dao.insertAllSuppliers(snapshot.suppliers)
                if (snapshot.employees.isNotEmpty()) dao.insertAllEmployees(snapshot.employees)
                if (snapshot.cashShifts.isNotEmpty()) dao.insertAllCashShifts(snapshot.cashShifts)
                if (snapshot.cashShiftMovements.isNotEmpty()) dao.insertAllCashShiftMovements(snapshot.cashShiftMovements)
                if (snapshot.sales.isNotEmpty()) dao.insertAllSales(snapshot.sales)
                if (snapshot.purchases.isNotEmpty()) dao.insertAllPurchases(snapshot.purchases)
                if (snapshot.extraMovements.isNotEmpty()) dao.insertAllExtraMovements(snapshot.extraMovements)
                if (snapshot.rentalsLoans.isNotEmpty()) dao.insertAllRentalsLoans(snapshot.rentalsLoans)
                if (snapshot.inventoryMovements.isNotEmpty()) dao.insertAllInventoryMovements(snapshot.inventoryMovements)
                val seqs = CajaMaestraDatabase.deriveFolioSequences(
                    sales = snapshot.sales,
                    purchases = snapshot.purchases,
                    existing = snapshot.folioSequences
                )
                dao.insertAllFolioSequences(seqs)
            }
            val origSettings = snapshot.businessSettings ?: BusinessSettings()
            val origVault = snapshot.securityVault ?: SecurityVault()
            settingsStorage.saveAll(origSettings, origVault)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writePreventiveJournal(snapshot: BackupDataset) {
        journalDir.mkdirs()
        val snapshotBytes = BackupSerializer.serializeSnapshotJson(snapshot)
        val encryptedBlob = settingsStorage.encryptBytesWithLocalKeyStore(snapshotBytes)
        journalDataFile.writeText(encryptedBlob, Charsets.UTF_8)
        pendingFlagFile.writeText(System.currentTimeMillis().toString(), Charsets.UTF_8)
    }

    private fun clearPreventiveJournal() {
        runCatching { pendingFlagFile.delete() }
        runCatching { journalDataFile.delete() }
    }

    fun validateDataset(dataset: BackupDataset): String? {
        if (hasDuplicateIds(dataset.inventory.map { it.id })) return "IDs duplicados en inventario."
        if (hasDuplicateIds(dataset.sales.map { it.id })) return "IDs duplicados en ventas."
        if (hasDuplicateIds(dataset.purchases.map { it.id })) return "IDs duplicados en compras."
        if (hasDuplicateIds(dataset.extraMovements.map { it.id })) return "IDs duplicados en movimientos extra."
        if (hasDuplicateIds(dataset.rentalsLoans.map { it.id })) return "IDs duplicados en rentas/préstamos."
        if (hasDuplicateIds(dataset.clients.map { it.id })) return "IDs duplicados en clientes."
        if (hasDuplicateIds(dataset.suppliers.map { it.id })) return "IDs duplicados en proveedores."
        if (hasDuplicateIds(dataset.employees.map { it.id })) return "IDs duplicados en empleados."
        if (hasDuplicateIds(dataset.cashShifts.map { it.id })) return "IDs duplicados en turnos de caja."
        if (hasDuplicateIds(dataset.cashShiftMovements.map { it.id })) return "IDs duplicados en movimientos de turno."
        if (hasDuplicateIds(dataset.inventoryMovements.map { it.id })) return "IDs duplicados en historial de inventario."

        if (dataset.inventory.any { it.name.isBlank() || it.costPrice < 0.0 || it.salePrice < 0.0 }) {
            return "Existen productos con nombre vacío o precios negativos."
        }
        if (dataset.sales.any { it.folio.isBlank() || it.total < 0.0 || it.paidAmount < 0.0 }) {
            return "Existen ventas con folio vacío o importes inválidos."
        }
        if (dataset.purchases.any { it.folio.isBlank() || it.total < 0.0 }) {
            return "Existen compras con folio vacío o total negativo."
        }
        if (dataset.extraMovements.any { it.concept.isBlank() || it.amount < 0.0 }) {
            return "Existen movimientos extra inválidos."
        }
        if (dataset.rentalsLoans.any { it.contactName.isBlank() || it.itemOrReason.isBlank() || it.amount < 0.0 }) {
            return "Existen rentas o préstamos inválidos."
        }
        if (dataset.clients.any { it.name.isBlank() }) return "Existen clientes sin nombre."
        if (dataset.suppliers.any { it.name.isBlank() }) return "Existen proveedores sin nombre."
        if (dataset.employees.any { it.name.isBlank() }) return "Existen empleados sin nombre."
        if (dataset.cashShifts.any { it.cashierName.isBlank() }) return "Existen turnos de caja sin cajero."
        if (dataset.cashShiftMovements.any { it.amount < 0.0 || it.reason.isBlank() }) {
            return "Existen movimientos de turno de caja inválidos."
        }

        if (!dataset.isLegacyV1) {
            val shiftIds = dataset.cashShifts.map { it.id }.toHashSet()
            if (dataset.cashShiftMovements.any { !shiftIds.contains(it.shiftId) }) {
                return "Relación inválida: movimiento de caja apunta a un turno inexistente."
            }
            if (dataset.sales.any { it.shiftId != null && !shiftIds.contains(it.shiftId) }) {
                return "Relación inválida: venta apunta a un turno de caja inexistente."
            }

            if (dataset.businessSettings == null) {
                return "El respaldo v2 no contiene configuración del negocio."
            }
            val vault = dataset.securityVault
                ?: return "El respaldo v2 no contiene bóveda de seguridad."
            if (vault.isInitialized) {
                if (!PasswordHasher.isHashed(vault.adminPasswordHash) ||
                    !PasswordHasher.isHashed(vault.jefePasswordHash) ||
                    !PasswordHasher.isHashed(vault.trabajadorPasswordHash)
                ) {
                    return "Los hashes de seguridad dentro del respaldo están corruptos."
                }
            }
        }
        return null
    }

    private fun hasDuplicateIds(ids: List<Long>): Boolean {
        val positive = ids.filter { it > 0L }
        return positive.size != positive.toHashSet().size
    }
}
