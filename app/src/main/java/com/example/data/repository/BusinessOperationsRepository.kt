package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.CajaMaestraDatabase
import com.example.data.model.BusinessSettings
import com.example.data.model.CashRegisterShift
import com.example.data.model.CashShiftMovement
import com.example.data.model.CashShiftMovementType
import com.example.data.model.FolioSequence
import com.example.data.model.InventoryItem
import com.example.data.model.InventoryMovement
import com.example.data.model.InventoryMovementType
import com.example.data.model.ItemType
import com.example.data.model.Purchase
import com.example.data.model.Sale
import com.example.data.model.SaleCartItem
import com.example.data.model.SaleStatus
import com.example.data.model.SaleType
import com.example.util.CashShiftCalculator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

sealed class RepoSaleResult {
    data class Committed(val sale: Sale, val items: List<SaleCartItem>) : RepoSaleResult()
    data object AlreadyProcessed : RepoSaleResult()
    data object EmptyCart : RepoSaleResult()
    data object ShiftNotOpen : RepoSaleResult()
    data object ClientRequiredForCredit : RepoSaleResult()
    data class InsufficientCash(val missing: Double) : RepoSaleResult()
    data class InsufficientStock(
        val itemName: String,
        val available: Double,
        val requested: Double
    ) : RepoSaleResult()
}

data class PurchaseLineInput(
    val itemId: Long,
    val quantity: Double,
    val unitCost: Double
)

sealed class RepoPurchaseResult {
    data class Committed(val purchase: Purchase) : RepoPurchaseResult()
    data object AlreadyProcessed : RepoPurchaseResult()
    data class Invalid(val reason: String) : RepoPurchaseResult()
}

class BusinessOperationsRepository(
    private val db: CajaMaestraDatabase,
    private val settingsProvider: () -> BusinessSettings
) {
    private val dao = db.dao()

    private val saleMutex = Mutex()
    private val purchaseMutex = Mutex()
    private val cashShiftMutex = Mutex()
    private val inventoryMutex = Mutex()
    private val wipeMutex = Mutex()

    private val processedSaleOperationIds = LinkedHashSet<String>()
    private val processedPurchaseOperationIds = LinkedHashSet<String>()

    private data class PendingStockMovement(
        val item: InventoryItem,
        val delta: Double,
        val previousStock: Double,
        val newStock: Double
    )

    private class AbortSaleException(val failure: RepoSaleResult) : RuntimeException()

    private fun recordOperationId(set: LinkedHashSet<String>, operationId: String?) {
        val clean = operationId?.trim().orEmpty()
        if (clean.isEmpty()) return
        set.add(clean)
        while (set.size > 500) {
            val oldest = set.iterator().next()
            set.remove(oldest)
        }
    }

    suspend fun completeSale(
        items: List<SaleCartItem>,
        saleType: SaleType,
        selectedCustomerId: Long?,
        paymentMethod: String,
        customerPaidInput: String,
        discountInput: String,
        notes: String,
        operationId: String? = null,
        faultHookAfterSaleInsert: (() -> Unit)? = null
    ): RepoSaleResult {
        if (items.isEmpty()) return RepoSaleResult.EmptyCart
        val cleanOpId = operationId?.trim()?.takeIf { it.isNotEmpty() }

        return saleMutex.withLock {
            if (cleanOpId != null && processedSaleOperationIds.contains(cleanOpId)) {
                return@withLock RepoSaleResult.AlreadyProcessed
            }

            try {
                val committed = db.withTransaction {
                    val currentSettings = settingsProvider()
                    val isQuote = saleType == SaleType.COTIZACION
                    val activeShift = if (!isQuote) dao.getOpenCashShiftSync() else null

                    if (!isQuote && activeShift == null) {
                        throw AbortSaleException(RepoSaleResult.ShiftNotOpen)
                    }

                    val isCreditSale = !isQuote && paymentMethod.contains("Crédito", ignoreCase = true)
                    val liveClient = selectedCustomerId?.let { dao.getClientByIdSync(it) }
                    if (isCreditSale && liveClient == null) {
                        throw AbortSaleException(RepoSaleResult.ClientRequiredForCredit)
                    }

                    // Aggregate requested quantity per inventory item so multi-line carts verify total stock cleanly
                    val qtyByItemId = linkedMapOf<Long, Double>()
                    for (line in items) {
                        if (line.quantity <= 0.0 || !line.quantity.isFinite() || line.unitPrice < 0.0 || !line.unitPrice.isFinite()) {
                            throw AbortSaleException(
                                RepoSaleResult.InsufficientStock(line.name, 0.0, line.quantity)
                            )
                        }
                        qtyByItemId[line.itemId] = (qtyByItemId[line.itemId] ?: 0.0) + line.quantity
                    }

                    val pendingMovements = mutableListOf<PendingStockMovement>()
                    for ((itemId, requestedQty) in qtyByItemId) {
                        val liveItem = dao.getInventoryItemByIdSync(itemId)
                            ?: throw AbortSaleException(
                                RepoSaleResult.InsufficientStock(
                                    items.firstOrNull { it.itemId == itemId }?.name ?: "Artículo",
                                    0.0,
                                    requestedQty
                                )
                            )
                        if (liveItem.isWaste) {
                            throw AbortSaleException(
                                RepoSaleResult.InsufficientStock(liveItem.name, 0.0, requestedQty)
                            )
                        }
                        if (!isQuote && currentSettings.checkStock && liveItem.type != ItemType.SERVICIO) {
                            if (liveItem.stock + 0.0001 < requestedQty) {
                                throw AbortSaleException(
                                    RepoSaleResult.InsufficientStock(liveItem.name, liveItem.stock, requestedQty)
                                )
                            }
                            val updatedStock = (liveItem.stock - requestedQty).coerceAtLeast(0.0)
                            dao.updateItem(liveItem.copy(stock = updatedStock))
                            pendingMovements.add(
                                PendingStockMovement(
                                    item = liveItem,
                                    delta = -requestedQty,
                                    previousStock = liveItem.stock,
                                    newStock = updatedStock
                                )
                            )
                        }
                    }

                    val subtotal = items.sumOf { it.subtotal }
                    val discount = (discountInput.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)
                    val total = (subtotal - discount).coerceAtLeast(0.0)

                    val isCashSale = !isQuote && !isCreditSale && paymentMethod.equals("Efectivo", ignoreCase = true)
                    val paid = when {
                        isCreditSale || isQuote -> 0.0
                        customerPaidInput.isBlank() -> total
                        else -> customerPaidInput.toDoubleOrNull() ?: 0.0
                    }

                    if (isCashSale && customerPaidInput.isNotBlank() && paid + 0.001 < total) {
                        throw AbortSaleException(
                            RepoSaleResult.InsufficientCash((total - paid).coerceAtLeast(0.0))
                        )
                    }

                    val change = if (isCashSale) (paid - total).coerceAtLeast(0.0) else 0.0
                    val folio = allocateNextSaleFolioSync(saleType)

                    val status = when {
                        isQuote -> SaleStatus.PENDIENTE
                        isCreditSale -> SaleStatus.CREDITO
                        else -> SaleStatus.PAGADA
                    }

                    val jsonArray = JSONArray()
                    for (it in items) {
                        jsonArray.put(JSONObject().apply {
                            put("itemId", it.itemId)
                            put("name", it.name)
                            put("quantity", it.quantity)
                            put("unitPrice", it.unitPrice)
                            put("costPrice", it.costPrice)
                            put("unit", it.unit)
                            put("type", it.type.name)
                            put("subtotal", it.subtotal)
                        })
                    }

                    val sale = Sale(
                        folio = folio,
                        type = saleType,
                        status = status,
                        customerId = liveClient?.id,
                        customerName = liveClient?.name ?: "Público General",
                        subtotal = subtotal,
                        discount = discount,
                        total = total,
                        paidAmount = paid,
                        changeAmount = change,
                        paymentMethod = if (isQuote) "Cotización" else paymentMethod,
                        date = System.currentTimeMillis(),
                        sellerName = currentSettings.currentUserName,
                        notes = notes.trim(),
                        itemsJson = jsonArray.toString(),
                        shiftId = if (!isQuote) activeShift?.id else null
                    )

                    val generatedId = dao.insertSale(sale)
                    faultHookAfterSaleInsert?.invoke()

                    for (pm in pendingMovements) {
                        dao.insertInventoryMovement(
                            InventoryMovement(
                                itemId = pm.item.id,
                                itemCode = pm.item.code,
                                itemName = pm.item.name,
                                movementType = InventoryMovementType.SALIDA_VENTA,
                                quantityDelta = pm.delta,
                                previousStock = pm.previousStock,
                                newStock = pm.newStock,
                                referenceFolio = folio,
                                reason = "Venta $folio",
                                performedBy = currentSettings.currentUserName,
                                date = sale.date
                            )
                        )
                    }

                    if (status == SaleStatus.CREDITO && liveClient != null) {
                        dao.updateClient(
                            liveClient.copy(creditBalance = liveClient.creditBalance + total)
                        )
                    }

                    RepoSaleResult.Committed(sale = sale.copy(id = generatedId), items = items)
                }

                recordOperationId(processedSaleOperationIds, cleanOpId)
                committed
            } catch (abort: AbortSaleException) {
                abort.failure
            }
        }
    }

    suspend fun cancelSale(sale: Sale): Sale? {
        return saleMutex.withLock {
            db.withTransaction {
                val liveSale = when {
                    sale.id > 0L -> dao.getSaleByIdSync(sale.id)
                    sale.folio.isNotBlank() -> dao.getSaleByFolioSync(sale.folio)
                    else -> null
                } ?: return@withTransaction null

                if (liveSale.status == SaleStatus.CANCELADA) {
                    return@withTransaction null
                }

                val wasCredit = liveSale.status == SaleStatus.CREDITO
                val updatedSale = liveSale.copy(status = SaleStatus.CANCELADA)
                dao.updateSale(updatedSale)

                val currentSettings = settingsProvider()
                if (liveSale.type == SaleType.VENTA && currentSettings.checkStock && liveSale.itemsJson.isNotBlank()) {
                    val restoreByItemId = linkedMapOf<Long, Double>()
                    runCatching {
                        val arr = JSONArray(liveSale.itemsJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val itemId = obj.optLong("itemId", 0L)
                            val qty = obj.optDouble("quantity", 0.0)
                            val typeStr = obj.optString("type", ItemType.PRODUCTO.name)
                            if (itemId > 0L && qty > 0.0 && typeStr != ItemType.SERVICIO.name) {
                                restoreByItemId[itemId] = (restoreByItemId[itemId] ?: 0.0) + qty
                            }
                        }
                    }
                    for ((itemId, qtyToRestore) in restoreByItemId) {
                        val liveItem = dao.getInventoryItemByIdSync(itemId)
                        if (liveItem != null && liveItem.type != ItemType.SERVICIO) {
                            val newStock = liveItem.stock + qtyToRestore
                            dao.updateItem(liveItem.copy(stock = newStock))
                            dao.insertInventoryMovement(
                                InventoryMovement(
                                    itemId = liveItem.id,
                                    itemCode = liveItem.code,
                                    itemName = liveItem.name,
                                    movementType = InventoryMovementType.RESTITUCION_CANCELACION,
                                    quantityDelta = qtyToRestore,
                                    previousStock = liveItem.stock,
                                    newStock = newStock,
                                    referenceFolio = liveSale.folio,
                                    reason = "Cancelación de venta ${liveSale.folio}",
                                    performedBy = currentSettings.currentUserName,
                                    date = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                if (wasCredit && liveSale.customerId != null) {
                    val liveClient = dao.getClientByIdSync(liveSale.customerId)
                    if (liveClient != null) {
                        dao.updateClient(
                            liveClient.copy(
                                creditBalance = (liveClient.creditBalance - liveSale.total).coerceAtLeast(0.0)
                            )
                        )
                    }
                }

                // If the voided sale belonged to an already closed shift, recalculate its audit totals
                if (liveSale.shiftId != null) {
                    val shift = dao.getCashShiftByIdSync(liveSale.shiftId)
                    if (shift != null && !shift.isOpen) {
                        val shiftSales = dao.getSalesForShiftSync(shift.id)
                        val shiftMovements = dao.getMovementsForShiftSync(shift.id)
                        val recalculated = CashShiftCalculator.buildClosedShift(
                            openShift = shift,
                            shiftSales = shiftSales,
                            shiftMovements = shiftMovements,
                            countedCash = shift.countedCash,
                            nextTurnFloat = shift.nextTurnFloat,
                            closingNotes = shift.closingNotes,
                            closedAt = shift.closedAt ?: System.currentTimeMillis()
                        )
                        dao.updateCashShift(recalculated)
                    }
                }

                updatedSale
            }
        }
    }

    suspend fun registerPurchase(
        supplierId: Long?,
        fallbackSupplierName: String,
        lines: List<PurchaseLineInput>,
        isCredit: Boolean,
        notes: String,
        operationId: String? = null,
        faultHookAfterPurchaseInsert: (() -> Unit)? = null
    ): RepoPurchaseResult {
        if (lines.isEmpty()) {
            return RepoPurchaseResult.Invalid("Agrega al menos un producto válido a la orden de compra.")
        }
        for (line in lines) {
            if (line.quantity <= 0.0 || !line.quantity.isFinite()) {
                return RepoPurchaseResult.Invalid("Todas las cantidades deben ser mayores a cero.")
            }
            if (line.unitCost < 0.0 || !line.unitCost.isFinite()) {
                return RepoPurchaseResult.Invalid("El costo unitario no puede ser negativo.")
            }
        }

        val cleanOpId = operationId?.trim()?.takeIf { it.isNotEmpty() }
        return purchaseMutex.withLock {
            if (cleanOpId != null && processedPurchaseOperationIds.contains(cleanOpId)) {
                return@withLock RepoPurchaseResult.AlreadyProcessed
            }

            try {
                val result = db.withTransaction {
                    val currentSettings = settingsProvider()
                    val jsonArray = JSONArray()
                    var authoritativeTotal = 0.0
                    val pendingMovements = mutableListOf<PendingStockMovement>()

                    for (line in lines) {
                        val liveItem = dao.getInventoryItemByIdSync(line.itemId)
                            ?: throw IllegalArgumentException("El artículo seleccionado ya no existe en el inventario.")
                        if (liveItem.isWaste) {
                            throw IllegalArgumentException("No se pueden registrar compras de artículos marcados como merma (${liveItem.name}).")
                        }
                        if (liveItem.type == ItemType.SERVICIO) {
                            throw IllegalArgumentException("Los servicios (${liveItem.name}) no generan entradas de compra en inventario.")
                        }

                        val oldStock = liveItem.stock.coerceAtLeast(0.0)
                        val oldCost = liveItem.costPrice.coerceAtLeast(0.0)
                        val newStock = oldStock + line.quantity
                        val newCost = when {
                            line.unitCost <= 0.0 -> oldCost
                            currentSettings.useAverageCost && oldStock > 0.0 && oldCost > 0.0 -> {
                                ((oldStock * oldCost) + (line.quantity * line.unitCost)) / newStock
                            }
                            else -> line.unitCost
                        }

                        dao.updateItem(
                            liveItem.copy(
                                stock = newStock,
                                costPrice = newCost
                            )
                        )
                        pendingMovements.add(
                            PendingStockMovement(
                                item = liveItem,
                                delta = line.quantity,
                                previousStock = oldStock,
                                newStock = newStock
                            )
                        )

                        val lineSubtotal = line.quantity * line.unitCost
                        authoritativeTotal += lineSubtotal

                        jsonArray.put(JSONObject().apply {
                            put("itemId", liveItem.id)
                            put("code", liveItem.code)
                            put("name", liveItem.name)
                            put("quantity", line.quantity)
                            put("unitPrice", line.unitCost)
                            put("subtotal", lineSubtotal)
                            put("unit", liveItem.unit)
                            put("type", liveItem.type.name)
                        })
                    }

                    val resolvedSupplier = supplierId?.let { dao.getSupplierByIdSync(it) }
                    val folio = allocateNextPurchaseFolioSync()
                    val resolvedSupplierName = resolvedSupplier?.name ?: fallbackSupplierName.ifBlank { "Proveedor General" }
                    val purchase = Purchase(
                        folio = folio,
                        supplierId = resolvedSupplier?.id,
                        supplierName = resolvedSupplierName,
                        total = authoritativeTotal,
                        date = System.currentTimeMillis(),
                        isCredit = isCredit,
                        notes = notes.trim(),
                        itemsJson = jsonArray.toString()
                    )

                    val insertedId = dao.insertPurchase(purchase)
                    faultHookAfterPurchaseInsert?.invoke()

                    for (pm in pendingMovements) {
                        dao.insertInventoryMovement(
                            InventoryMovement(
                                itemId = pm.item.id,
                                itemCode = pm.item.code,
                                itemName = pm.item.name,
                                movementType = InventoryMovementType.ENTRADA_COMPRA,
                                quantityDelta = pm.delta,
                                previousStock = pm.previousStock,
                                newStock = pm.newStock,
                                referenceFolio = folio,
                                reason = "Compra a $resolvedSupplierName",
                                performedBy = currentSettings.currentUserName,
                                date = purchase.date
                            )
                        )
                    }

                    RepoPurchaseResult.Committed(purchase.copy(id = insertedId))
                }

                recordOperationId(processedPurchaseOperationIds, cleanOpId)
                result
            } catch (e: IllegalArgumentException) {
                RepoPurchaseResult.Invalid(e.message ?: "Datos inválidos para registrar la compra.")
            }
        }
    }

    suspend fun openCashRegister(
        openingFloat: Double,
        notes: String
    ): CashRegisterShift? {
        val safeFloat = openingFloat.coerceAtLeast(0.0)
        return cashShiftMutex.withLock {
            db.withTransaction {
                if (dao.getOpenCashShiftSync() != null) {
                    return@withTransaction null
                }
                val currentSettings = settingsProvider()
                val shift = CashRegisterShift(
                    cashierName = currentSettings.currentUserName,
                    cashierRole = currentSettings.currentRole,
                    openedAt = System.currentTimeMillis(),
                    openingFloat = safeFloat,
                    isOpen = true,
                    openingNotes = notes.trim()
                )
                val id = dao.insertCashShift(shift)
                shift.copy(id = id)
            }
        }
    }

    suspend fun registerCashShiftMovement(
        type: CashShiftMovementType,
        amount: Double,
        reason: String
    ): CashShiftMovement? {
        if (amount <= 0.0 || !amount.isFinite() || reason.isBlank()) return null
        return cashShiftMutex.withLock {
            db.withTransaction {
                val activeShift = dao.getOpenCashShiftSync() ?: return@withTransaction null
                val movement = CashShiftMovement(
                    shiftId = activeShift.id,
                    type = type,
                    amount = amount,
                    reason = reason.trim(),
                    performedBy = settingsProvider().currentUserName,
                    date = System.currentTimeMillis()
                )
                val id = dao.insertCashShiftMovement(movement)
                movement.copy(id = id)
            }
        }
    }

    suspend fun closeCashRegister(
        countedCash: Double,
        nextTurnFloat: Double,
        closingNotes: String
    ): CashRegisterShift? {
        return cashShiftMutex.withLock {
            db.withTransaction {
                val activeShift = dao.getOpenCashShiftSync() ?: return@withTransaction null
                val shiftSales = dao.getSalesForShiftSync(activeShift.id)
                val shiftMovements = dao.getMovementsForShiftSync(activeShift.id)
                val closedShift = CashShiftCalculator.buildClosedShift(
                    openShift = activeShift,
                    shiftSales = shiftSales,
                    shiftMovements = shiftMovements,
                    countedCash = countedCash.coerceAtLeast(0.0),
                    nextTurnFloat = nextTurnFloat.coerceAtLeast(0.0),
                    closingNotes = closingNotes
                )
                dao.updateCashShift(closedShift)
                closedShift
            }
        }
    }

    suspend fun adjustItemStock(
        itemId: Long,
        delta: Double,
        reason: String = "Ajuste manual en inventario"
    ): InventoryItem? {
        if (!delta.isFinite() || delta == 0.0) return null
        return inventoryMutex.withLock {
            db.withTransaction {
                val liveItem = dao.getInventoryItemByIdSync(itemId) ?: return@withTransaction null
                val newStock = (liveItem.stock + delta).coerceAtLeast(0.0)
                val effectiveDelta = newStock - liveItem.stock
                val updated = liveItem.copy(stock = newStock)
                dao.updateItem(updated)
                if (kotlin.math.abs(effectiveDelta) > 0.0001) {
                    dao.insertInventoryMovement(
                        InventoryMovement(
                            itemId = liveItem.id,
                            itemCode = liveItem.code,
                            itemName = liveItem.name,
                            movementType = InventoryMovementType.AJUSTE_MANUAL,
                            quantityDelta = effectiveDelta,
                            previousStock = liveItem.stock,
                            newStock = newStock,
                            referenceFolio = "AJUSTE",
                            reason = reason.trim().ifBlank { "Ajuste manual en inventario" },
                            performedBy = settingsProvider().currentUserName,
                            date = System.currentTimeMillis()
                        )
                    )
                }
                updated
            }
        }
    }

    suspend fun saveInventoryItem(
        item: InventoryItem,
        canEditCostPrice: Boolean = true,
        reason: String = ""
    ): InventoryItem {
        return inventoryMutex.withLock {
            db.withTransaction {
                val performer = settingsProvider().currentUserName
                val safeCost = item.costPrice.let { if (it.isFinite() && it >= 0.0) it else 0.0 }
                val safeSale = item.salePrice.let { if (it.isFinite() && it >= 0.0) it else 0.0 }
                val safeStock = item.stock.let { if (it.isFinite() && it >= 0.0) it else 0.0 }
                val safeMinStock = item.minStock.let { if (it.isFinite() && it >= 0.0) it else 0.0 }
                val cleanRawMaterial = item.type == ItemType.PRODUCTO && !item.isWaste && item.isRawMaterial

                val sanitizedBase = item.copy(
                    name = item.name.trim(),
                    code = item.code.trim().ifEmpty { "ART-${System.currentTimeMillis() % 10000}" },
                    costPrice = safeCost,
                    salePrice = safeSale,
                    stock = safeStock,
                    minStock = safeMinStock,
                    category = item.category.trim().ifEmpty { "General" },
                    unit = item.unit.trim().ifEmpty { "Pza" },
                    isRawMaterial = cleanRawMaterial,
                    notes = item.notes.trim()
                )

                if (sanitizedBase.id == 0L) {
                    val insertedId = dao.insertItem(sanitizedBase)
                    val saved = sanitizedBase.copy(id = insertedId)
                    if (saved.type != ItemType.SERVICIO && saved.stock > 0.0) {
                        dao.insertInventoryMovement(
                            InventoryMovement(
                                itemId = saved.id,
                                itemCode = saved.code,
                                itemName = saved.name,
                                movementType = InventoryMovementType.ALTA_INVENTARIO,
                                quantityDelta = saved.stock,
                                previousStock = 0.0,
                                newStock = saved.stock,
                                referenceFolio = "ALTA",
                                reason = reason.trim().ifBlank { "Alta inicial de inventario" },
                                performedBy = performer,
                                date = System.currentTimeMillis()
                            )
                        )
                    }
                    saved
                } else {
                    val existing = dao.getInventoryItemByIdSync(sanitizedBase.id)
                    val finalItem = if (existing != null && !canEditCostPrice) {
                        sanitizedBase.copy(costPrice = existing.costPrice)
                    } else {
                        sanitizedBase
                    }
                    dao.updateItem(finalItem)
                    if (existing != null && finalItem.type != ItemType.SERVICIO) {
                        val delta = finalItem.stock - existing.stock
                        if (kotlin.math.abs(delta) > 0.0001) {
                            dao.insertInventoryMovement(
                                InventoryMovement(
                                    itemId = finalItem.id,
                                    itemCode = finalItem.code,
                                    itemName = finalItem.name,
                                    movementType = InventoryMovementType.EDICION_STOCK,
                                    quantityDelta = delta,
                                    previousStock = existing.stock,
                                    newStock = finalItem.stock,
                                    referenceFolio = "EDICION",
                                    reason = reason.trim().ifBlank { "Edición directa de existencia" },
                                    performedBy = performer,
                                    date = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    finalItem
                }
            }
        }
    }

    suspend fun wipeAllBusinessData() {
        wipeMutex.withLock {
            saleMutex.withLock {
                purchaseMutex.withLock {
                    cashShiftMutex.withLock {
                        inventoryMutex.withLock {
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
                                // Preserve folio_sequences so deleted folios are never reused
                            }
                            processedSaleOperationIds.clear()
                            processedPurchaseOperationIds.clear()
                        }
                    }
                }
            }
        }
    }

    private suspend fun allocateNextSaleFolioSync(saleType: SaleType): String {
        val isQuote = saleType == SaleType.COTIZACION
        val key = if (isQuote) CajaMaestraDatabase.KEY_COTIZACION else CajaMaestraDatabase.KEY_VENTA
        val prefix = if (isQuote) CajaMaestraDatabase.PREFIX_COTIZACION else CajaMaestraDatabase.PREFIX_VENTA

        val existingSeq = dao.getFolioSequenceSync(key)?.lastNumber ?: 0L
        val maxExisting = dao.getSaleFoliosByTypeSync(saleType)
            .maxOfOrNull { CajaMaestraDatabase.extractNumericSuffix(it, prefix) } ?: 0L

        var candidate = maxOf(existingSeq, maxExisting) + 1L
        var formatted = formatFolio(prefix, candidate)
        while (dao.countSalesWithFolio(formatted) > 0) {
            candidate++
            formatted = formatFolio(prefix, candidate)
        }
        dao.upsertFolioSequence(FolioSequence(sequenceKey = key, lastNumber = candidate))
        return formatted
    }

    private suspend fun allocateNextPurchaseFolioSync(): String {
        val key = CajaMaestraDatabase.KEY_COMPRA
        val prefix = CajaMaestraDatabase.PREFIX_COMPRA

        val existingSeq = dao.getFolioSequenceSync(key)?.lastNumber ?: 0L
        val maxExisting = dao.getPurchaseFoliosSync()
            .maxOfOrNull { CajaMaestraDatabase.extractNumericSuffix(it, prefix) } ?: 0L

        var candidate = maxOf(existingSeq, maxExisting) + 1L
        var formatted = formatFolio(prefix, candidate)
        while (dao.countPurchasesWithFolio(formatted) > 0) {
            candidate++
            formatted = formatFolio(prefix, candidate)
        }
        dao.upsertFolioSequence(FolioSequence(sequenceKey = key, lastNumber = candidate))
        return formatted
    }

    private fun formatFolio(prefix: String, number: Long): String {
        return "$prefix${String.format(Locale.US, "%05d", number)}"
    }
}
