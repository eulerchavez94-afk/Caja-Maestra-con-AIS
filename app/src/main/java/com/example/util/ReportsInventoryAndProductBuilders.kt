package com.example.util

import com.example.data.model.InventoryItem
import com.example.data.model.InventoryMovementType
import com.example.data.model.ItemType
import com.example.data.model.SaleStatus
import com.example.data.model.SaleType
import java.util.Locale
import java.util.TimeZone

internal object ReportsInventoryAndProductBuilders {

    private data class SoldItemAgg(
        val key: String,
        val itemId: Long,
        var code: String,
        var name: String,
        var category: String,
        var unit: String,
        var itemType: ItemType,
        var quantity: Double = 0.0,
        var revenue: Double = 0.0,
        var costKnownQty: Double = 0.0,
        var totalCostKnown: Double = 0.0,
        var linesWithCost: Int = 0,
        var totalLines: Int = 0,
        var lastSaleDate: Long = 0L
    )

    private data class PurchasedItemAgg(
        val key: String,
        val itemId: Long,
        var code: String,
        var name: String,
        var category: String,
        var unit: String,
        var itemType: ItemType,
        var quantity: Double = 0.0,
        var totalCost: Double = 0.0,
        var lastPurchaseDate: Long = 0L
    )

    private fun aggregateSoldLines(
        lines: List<ParsedSaleLine>
    ): List<SoldItemAgg> {
        val map = linkedMapOf<String, SoldItemAgg>()
        for (line in lines) {
            val key = if (line.itemId > 0L) {
                "id:${line.itemId}"
            } else {
                "nm:${PosSearchEngine.normalize(line.name)}|${line.itemType.name}"
            }
            val agg = map.getOrPut(key) {
                SoldItemAgg(
                    key = key,
                    itemId = line.itemId,
                    code = line.code,
                    name = line.name,
                    category = line.category,
                    unit = line.unit,
                    itemType = line.itemType
                )
            }
            agg.quantity += line.quantity
            agg.revenue += line.subtotal
            agg.totalLines += 1
            if (line.historicalTotalCost != null) {
                agg.linesWithCost += 1
                agg.costKnownQty += line.quantity
                agg.totalCostKnown += line.historicalTotalCost
            }
            if (line.saleDate > agg.lastSaleDate) {
                agg.lastSaleDate = line.saleDate
            }
        }
        return map.values.sortedByDescending { it.revenue }
    }

    private fun aggregatePurchasedLines(
        lines: List<ParsedPurchaseLine>
    ): List<PurchasedItemAgg> {
        val map = linkedMapOf<String, PurchasedItemAgg>()
        for (line in lines) {
            val key = if (line.itemId > 0L) {
                "id:${line.itemId}"
            } else {
                "nm:${PosSearchEngine.normalize(line.name)}"
            }
            val agg = map.getOrPut(key) {
                PurchasedItemAgg(
                    key = key,
                    itemId = line.itemId,
                    code = line.code,
                    name = line.name,
                    category = line.category,
                    unit = line.unit,
                    itemType = line.itemType
                )
            }
            agg.quantity += line.quantity
            agg.totalCost += line.subtotal
            if (line.purchaseDate > agg.lastPurchaseDate) {
                agg.lastPurchaseDate = line.purchaseDate
            }
        }
        return map.values.sortedByDescending { it.totalCost }
    }

    private fun matchesSoldItem(item: InventoryItem, soldAggs: List<SoldItemAgg>): Boolean {
        val norm = PosSearchEngine.normalize(item.name)
        return soldAggs.any { agg ->
            (agg.itemId > 0L && agg.itemId == item.id) ||
                PosSearchEngine.normalize(agg.name) == norm
        }
    }

    private fun soldQtyForItem(item: InventoryItem, soldAggs: List<SoldItemAgg>): SoldItemAgg? {
        val norm = PosSearchEngine.normalize(item.name)
        return soldAggs.firstOrNull { agg ->
            (agg.itemId > 0L && agg.itemId == item.id) ||
                PosSearchEngine.normalize(agg.name) == norm
        }
    }

    private fun purchasedQtyForItem(item: InventoryItem, purchAggs: List<PurchasedItemAgg>): PurchasedItemAgg? {
        val norm = PosSearchEngine.normalize(item.name)
        return purchAggs.firstOrNull { agg ->
            (agg.itemId > 0L && agg.itemId == item.id) ||
                PosSearchEngine.normalize(agg.name) == norm
        }
    }

    private fun stockStatusLabel(item: InventoryItem): String = when {
        item.stock <= 0.0 -> "Agotado"
        item.stock <= item.minStock -> "Stock Bajo"
        else -> "Saludable"
    }

    private fun rotationLabel(soldQty: Double, allSoldQtys: List<Double>): String {
        if (soldQty <= 0.0) return "Sin Movimiento"
        val nonZero = allSoldQtys.filter { it > 0.0 }.sorted()
        if (nonZero.isEmpty()) return "Baja"
        val highThreshold = nonZero[(nonZero.size * 0.66).toInt().coerceAtMost(nonZero.lastIndex)]
        val midThreshold = nonZero[(nonZero.size * 0.33).toInt().coerceAtMost(nonZero.lastIndex)]
        return when {
            soldQty >= highThreshold -> "Alta"
            soldQty >= midThreshold -> "Media"
            else -> "Baja"
        }
    }

    fun build(
        title: String,
        bounds: DateRangeBounds?,
        periodLabel: String,
        dataset: ReportsDataset,
        summary: FinancialSummary,
        cur: String,
        timeZone: TimeZone
    ): GeneratedReport {
        val fSales = dataset.sales.filter { ReportsEngine.inRange(it.date, bounds) }
        val confirmedSales = fSales.filter { it.type == SaleType.VENTA && it.status != SaleStatus.CANCELADA }
        val canceledSales = fSales.filter { it.status == SaleStatus.CANCELADA }
        val fPurchases = dataset.purchases.filter { ReportsEngine.inRange(it.date, bounds) }

        val allSoldLines = confirmedSales.flatMap { ReportsEngine.parseSaleLines(it, dataset.inventory) }
        val allPurchasedLines = fPurchases.flatMap { ReportsEngine.parsePurchaseLines(it, dataset.inventory) }

        val soldAggsAll = aggregateSoldLines(allSoldLines)
        val purchAggsAll = aggregatePurchasedLines(allPurchasedLines)

        return when (title) {
            "Producto Ventas" -> buildItemSalesReport(
                title = title,
                periodLabel = periodLabel,
                aggs = soldAggsAll.filter { it.itemType == ItemType.PRODUCTO },
                itemColHeader = "Producto",
                cur = cur,
                timeZone = timeZone,
                discountsInPeriod = summary.confirmedSalesDiscounts
            )

            "Manufactura Ventas" -> buildItemSalesReport(
                title = title,
                periodLabel = periodLabel,
                aggs = soldAggsAll.filter { it.itemType == ItemType.MANUFACTURA },
                itemColHeader = "Manufactura / Combo",
                cur = cur,
                timeZone = timeZone,
                discountsInPeriod = summary.confirmedSalesDiscounts
            )

            "Servicio Ventas" -> buildItemSalesReport(
                title = title,
                periodLabel = periodLabel,
                aggs = soldAggsAll.filter { it.itemType == ItemType.SERVICIO },
                itemColHeader = "Servicio",
                cur = cur,
                timeZone = timeZone,
                discountsInPeriod = summary.confirmedSalesDiscounts
            )

            "Producto Compras" -> buildItemPurchasesReport(
                title = title,
                periodLabel = periodLabel,
                aggs = purchAggsAll.filter { it.itemType != ItemType.SERVICIO },
                itemColHeader = "Producto / Artículo",
                cur = cur,
                timeZone = timeZone,
                emptyNote = null
            )

            "Materia Prima Compras" -> {
                val rawIds = dataset.inventory.filter { ReportsEngine.isMateriaPrima(it) }.map { it.id }.toHashSet()
                val rawNormNames = dataset.inventory.filter { ReportsEngine.isMateriaPrima(it) }
                    .map { PosSearchEngine.normalize(it.name) }
                    .toHashSet()
                buildItemPurchasesReport(
                    title = title,
                    periodLabel = periodLabel,
                    aggs = purchAggsAll.filter { agg ->
                        (agg.itemId > 0L && rawIds.contains(agg.itemId)) ||
                            rawNormNames.contains(PosSearchEngine.normalize(agg.name)) ||
                            ReportsEngine.isMateriaPrimaCategoryOrText(agg.category, "", agg.name)
                    },
                    itemColHeader = "Materia Prima / Insumo",
                    cur = cur,
                    timeZone = timeZone,
                    emptyNote = "Incluye artículos marcados como Materia Prima o en categorías de Insumos, Ingredientes o Empaques."
                )
            }

            "Productos Sin Ventas" -> {
                val unsold = dataset.inventory.filter {
                    !it.isWaste && it.type == ItemType.PRODUCTO && !matchesSoldItem(it, soldAggsAll)
                }
                val totalCostVal = unsold.sumOf { it.stock.coerceAtLeast(0.0) * it.costPrice }
                val rows = unsold.map { item ->
                    listOf(
                        item.code,
                        item.name,
                        item.category,
                        "${ReportsEngine.formatQuantity(item.stock)} ${item.unit}",
                        ReportsEngine.formatCurrency(item.salePrice, cur),
                        ReportsEngine.formatCurrency(item.stock.coerceAtLeast(0.0) * item.costPrice, cur),
                        stockStatusLabel(item)
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Código", "Producto", "Categoría", "Stock Actual", "Precio Venta", "Valor Costo Inv.", "Estado"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "", "", ReportsEngine.formatCurrency(totalCostVal, cur), "Sin rotación"
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Productos Sin Venta" to rows.size.toString(),
                        "Capital Inmovilizado" to ReportsEngine.formatCurrency(totalCostVal, cur)
                    )
                )
            }

            "Servicios Sin Ventas" -> {
                val unsold = dataset.inventory.filter {
                    !it.isWaste && it.type == ItemType.SERVICIO && !matchesSoldItem(it, soldAggsAll)
                }
                val rows = unsold.map { item ->
                    listOf(
                        item.code,
                        item.name,
                        item.category,
                        ReportsEngine.formatCurrency(item.salePrice, cur),
                        ReportsEngine.formatCurrency(item.costPrice, cur),
                        item.notes.ifBlank { "-" }
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Código", "Servicio", "Categoría", "Precio Venta", "Costo Base", "Notas"),
                    rows = rows,
                    summaryStats = linkedMapOf("Servicios Sin Venta" to rows.size.toString())
                )
            }

            "Manufactura Sin Ventas" -> {
                val unsold = dataset.inventory.filter {
                    !it.isWaste && it.type == ItemType.MANUFACTURA && !matchesSoldItem(it, soldAggsAll)
                }
                val rows = unsold.map { item ->
                    listOf(
                        item.code,
                        item.name,
                        item.category,
                        "${ReportsEngine.formatQuantity(item.stock)} ${item.unit}",
                        ReportsEngine.formatCurrency(item.salePrice, cur),
                        ReportsEngine.formatCurrency(item.costPrice, cur)
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Código", "Manufactura / Kit", "Categoría", "Stock Actual", "Precio Venta", "Costo Unit."),
                    rows = rows,
                    summaryStats = linkedMapOf("Manufacturas Sin Venta" to rows.size.toString())
                )
            }

            "Producto Análisis" -> buildProductAnalysisReport(
                title = title,
                periodLabel = periodLabel,
                catalogItems = dataset.inventory.filter { !it.isWaste && it.type == ItemType.PRODUCTO },
                soldAggs = soldAggsAll.filter { it.itemType == ItemType.PRODUCTO },
                purchAggs = purchAggsAll,
                itemColHeader = "Producto",
                cur = cur
            )

            "Manufactura Análisis" -> buildProductAnalysisReport(
                title = title,
                periodLabel = periodLabel,
                catalogItems = dataset.inventory.filter { !it.isWaste && it.type == ItemType.MANUFACTURA },
                soldAggs = soldAggsAll.filter { it.itemType == ItemType.MANUFACTURA },
                purchAggs = purchAggsAll,
                itemColHeader = "Manufactura / Combo",
                cur = cur
            )

            "Materia Prima Análisis" -> {
                val mpItems = dataset.inventory.filter { !it.isWaste && ReportsEngine.isMateriaPrima(it) }
                var totalValuation = 0.0
                var lowOrOutCount = 0
                val rows = mpItems.map { item ->
                    val purchased = purchasedQtyForItem(item, purchAggsAll)?.quantity ?: 0.0
                    val sold = soldQtyForItem(item, soldAggsAll)?.quantity ?: 0.0
                    val valCost = item.stock.coerceAtLeast(0.0) * item.costPrice
                    totalValuation += valCost
                    if (item.stock <= item.minStock) lowOrOutCount += 1
                    listOf(
                        item.code,
                        item.name,
                        "${ReportsEngine.formatQuantity(item.stock)} ${item.unit}",
                        ReportsEngine.formatQuantity(item.minStock),
                        ReportsEngine.formatQuantity(purchased),
                        ReportsEngine.formatQuantity(sold),
                        ReportsEngine.formatCurrency(valCost, cur),
                        stockStatusLabel(item)
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Código", "Materia Prima / Insumo", "Stock Actual", "Mínimo", "Entradas (Comp.)", "Salidas (Vtas)", "Valor Inv.", "Estado"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "", "", "", ReportsEngine.formatCurrency(totalValuation, cur), "$lowOrOutCount alerta(s)"
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Insumos Activos" to rows.size.toString(),
                        "En Alerta / Agotados" to lowOrOutCount.toString(),
                        "Valor en Costo" to ReportsEngine.formatCurrency(totalValuation, cur)
                    ),
                    limitationsNote = "Incluye artículos marcados como Materia Prima o en categorías de Insumos, Ingredientes o Empaques."
                )
            }

            "Servicio Análisis" -> {
                val serviceCatalog = dataset.inventory.filter { !it.isWaste && it.type == ItemType.SERVICIO }
                val serviceSold = soldAggsAll.filter { it.itemType == ItemType.SERVICIO }
                val soldQtys = serviceCatalog.map { item -> soldQtyForItem(item, serviceSold)?.quantity ?: 0.0 }
                var totalRev = 0.0
                val seenKeys = mutableSetOf<String>()
                val rows = mutableListOf<List<String>>()
                val chartItems = mutableListOf<ChartBarItem>()

                for (item in serviceCatalog) {
                    val agg = soldQtyForItem(item, serviceSold)
                    if (agg != null) seenKeys.add(agg.key)
                    val qty = agg?.quantity ?: 0.0
                    val rev = agg?.revenue ?: 0.0
                    totalRev += rev
                    val completeCost = agg != null && agg.totalLines > 0 && agg.linesWithCost == agg.totalLines
                    val costDisplay = if (completeCost) ReportsEngine.formatCurrency(agg!!.totalCostKnown, cur) else "Sin costo hist."
                    val marginDisplay = if (completeCost) ReportsEngine.formatCurrency(rev - agg!!.totalCostKnown, cur) else "N/D"
                    rows.add(
                        listOf(
                            item.code,
                            item.name,
                            ReportsEngine.formatQuantity(qty),
                            ReportsEngine.formatCurrency(item.salePrice, cur),
                            ReportsEngine.formatCurrency(rev, cur),
                            costDisplay,
                            marginDisplay,
                            rotationLabel(qty, soldQtys)
                        )
                    )
                    if (rev > 0.0) {
                        chartItems.add(ChartBarItem(item.name.take(14), rev, ReportsEngine.formatCurrency(rev, cur)))
                    }
                }
                for (agg in serviceSold.filter { it.key !in seenKeys }) {
                    totalRev += agg.revenue
                    val completeCost = agg.totalLines > 0 && agg.linesWithCost == agg.totalLines
                    rows.add(
                        listOf(
                            agg.code,
                            agg.name,
                            ReportsEngine.formatQuantity(agg.quantity),
                            ReportsEngine.formatCurrency(agg.revenue / agg.quantity.coerceAtLeast(1.0), cur),
                            ReportsEngine.formatCurrency(agg.revenue, cur),
                            if (completeCost) ReportsEngine.formatCurrency(agg.totalCostKnown, cur) else "Sin costo hist.",
                            if (completeCost) ReportsEngine.formatCurrency(agg.revenue - agg.totalCostKnown, cur) else "N/D",
                            "Vendido"
                        )
                    )
                    chartItems.add(ChartBarItem(agg.name.take(14), agg.revenue, ReportsEngine.formatCurrency(agg.revenue, cur)))
                }

                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Código", "Servicio", "Cant. Realizada", "Precio Lista", "Ingreso Total", "Costo Hist.", "Margen", "Demanda"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "", ReportsEngine.formatCurrency(totalRev, cur), "", "", ""
                    ) else null,
                    chartBars = chartItems.sortedByDescending { it.value }.take(5)
                )
            }

            "Categoría Producto Ventas" -> {
                val grouped = allSoldLines.groupBy { it.category.trim().ifBlank { "General" } }
                    .entries
                    .sortedByDescending { (_, lines) -> lines.sumOf { it.subtotal } }
                val totalRev = allSoldLines.sumOf { it.subtotal }
                var totalUnits = 0.0
                val rows = grouped.map { (cat, lines) ->
                    val qty = lines.sumOf { it.quantity }
                    val rev = lines.sumOf { it.subtotal }
                    totalUnits += qty
                    val distinctCount = lines.map { PosSearchEngine.normalize(it.name) }.distinct().size
                    val allHaveCost = lines.isNotEmpty() && lines.all { it.historicalTotalCost != null }
                    val costSum = lines.sumOf { it.historicalTotalCost ?: 0.0 }
                    val pct = if (totalRev > 0.0) (rev / totalRev) * 100.0 else 0.0
                    listOf(
                        cat,
                        distinctCount.toString(),
                        ReportsEngine.formatQuantity(qty),
                        ReportsEngine.formatCurrency(rev, cur),
                        if (allHaveCost) ReportsEngine.formatCurrency(costSum, cur) else "Parcial / N/D",
                        if (allHaveCost) ReportsEngine.formatCurrency(rev - costSum, cur) else "N/D",
                        String.format(Locale.US, "%.1f%%", pct)
                    )
                }
                val catDiscountNote = if (summary.confirmedSalesDiscounts > 0.0) {
                    "Importes por categoría en bruto (descuentos de ticket en período: ${ReportsEngine.formatCurrency(summary.confirmedSalesDiscounts, cur)})."
                } else null
                val combinedNote = listOfNotNull(summary.profitLimitationNote, catDiscountNote).joinToString(" ").ifBlank { null }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Categoría", "Artículos", "Unidades", "Ingresos Brutos", "Costo Hist.", "Utilidad", "% Ventas"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL",
                        "",
                        ReportsEngine.formatQuantity(totalUnits),
                        ReportsEngine.formatCurrency(totalRev, cur),
                        if (summary.costCoverage == CostCoverage.COMPLETE) ReportsEngine.formatCurrency(summary.historicalCogsTotal, cur) else "Incompleto",
                        if (summary.costCoverage == CostCoverage.COMPLETE) ReportsEngine.formatCurrency(totalRev - summary.historicalCogsTotal, cur) else "Sin costo hist.",
                        "100.0%"
                    ) else null,
                    limitationsNote = combinedNote,
                    chartBars = grouped.take(6).map { (c, l) ->
                        val v = l.sumOf { it.subtotal }
                        ChartBarItem(c.take(14), v, ReportsEngine.formatCurrency(v, cur))
                    }
                )
            }

            "Categoría Producto Compras" -> {
                val grouped = allPurchasedLines.groupBy { it.category.trim().ifBlank { "General" } }
                    .entries
                    .sortedByDescending { (_, lines) -> lines.sumOf { it.subtotal } }
                val totalPurch = allPurchasedLines.sumOf { it.subtotal }
                var totalUnits = 0.0
                val rows = grouped.map { (cat, lines) ->
                    val qty = lines.sumOf { it.quantity }
                    val cost = lines.sumOf { it.subtotal }
                    totalUnits += qty
                    val distinctCount = lines.map { PosSearchEngine.normalize(it.name) }.distinct().size
                    val pct = if (totalPurch > 0.0) (cost / totalPurch) * 100.0 else 0.0
                    listOf(
                        cat,
                        distinctCount.toString(),
                        ReportsEngine.formatQuantity(qty),
                        ReportsEngine.formatCurrency(cost, cur),
                        String.format(Locale.US, "%.1f%%", pct)
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Categoría", "Artículos", "Unidades Comp.", "Total Comprado", "% Compras"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL", "", ReportsEngine.formatQuantity(totalUnits), ReportsEngine.formatCurrency(totalPurch, cur), "100.0%"
                    ) else null,
                    chartBars = grouped.take(6).map { (c, l) ->
                        val v = l.sumOf { it.subtotal }
                        ChartBarItem(c.take(14), v, ReportsEngine.formatCurrency(v, cur))
                    }
                )
            }

            "Merma Productos" -> buildWasteReport(
                title = title,
                periodLabel = periodLabel,
                items = dataset.inventory.filter { it.isWaste && !ReportsEngine.isMateriaPrima(it) },
                cur = cur,
                emptyNote = null
            )

            "Merma Materia Prima" -> buildWasteReport(
                title = title,
                periodLabel = periodLabel,
                items = dataset.inventory.filter { it.isWaste && ReportsEngine.isMateriaPrima(it) },
                cur = cur,
                emptyNote = "Filtra artículos marcados como merma cuya categoría o descripción corresponde a Materia Prima / Insumos."
            )

            "Producto Inventario Resumen" -> buildInventorySummaryReport(
                title = title,
                periodLabel = periodLabel,
                items = dataset.inventory.filter { !it.isWaste && it.type == ItemType.PRODUCTO },
                itemColHeader = "Producto",
                cur = cur
            )

            "Materia Prima Inventario Resumen" -> buildInventorySummaryReport(
                title = title,
                periodLabel = periodLabel,
                items = dataset.inventory.filter { !it.isWaste && ReportsEngine.isMateriaPrima(it) },
                itemColHeader = "Materia Prima / Insumo",
                cur = cur
            )

            "Manufactura Producción" -> {
                val mfgItems = dataset.inventory.filter { !it.isWaste && it.type == ItemType.MANUFACTURA }
                var sumCostVal = 0.0
                val rows = mfgItems.map { item ->
                    val inQty = purchasedQtyForItem(item, purchAggsAll)?.quantity ?: 0.0
                    val outQty = soldQtyForItem(item, soldAggsAll)?.quantity ?: 0.0
                    val valCost = item.stock.coerceAtLeast(0.0) * item.costPrice
                    sumCostVal += valCost
                    listOf(
                        item.code,
                        item.name,
                        "${ReportsEngine.formatQuantity(item.stock)} ${item.unit}",
                        ReportsEngine.formatQuantity(inQty),
                        ReportsEngine.formatQuantity(outQty),
                        ReportsEngine.formatCurrency(item.costPrice, cur),
                        ReportsEngine.formatCurrency(valCost, cur),
                        item.notes.ifBlank { "-" }
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Código", "Artículo Manufacturado", "Stock Actual", "Entradas Per.", "Salidas Per.", "Costo Unit.", "Valor Costo", "Notas / Fórmula"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "", "", "", ReportsEngine.formatCurrency(sumCostVal, cur), ""
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Manufacturas" to rows.size.toString(),
                        "Valor Stock" to ReportsEngine.formatCurrency(sumCostVal, cur)
                    )
                )
            }

            "Modificación Inventario" -> {
                data class InvModRow(val ts: Long, val cells: List<String>)
                val mods = mutableListOf<InvModRow>()
                val loggedMovements = dataset.inventoryMovements.filter { ReportsEngine.inRange(it.date, bounds) }
                val loggedFolios = dataset.inventoryMovements.mapNotNull {
                    it.referenceFolio.trim().takeIf { ref -> ref.isNotEmpty() }
                }.toHashSet()

                for (mov in loggedMovements) {
                    val label = when (mov.movementType) {
                        InventoryMovementType.ENTRADA_COMPRA -> "Entrada Compra"
                        InventoryMovementType.SALIDA_VENTA -> "Salida Venta"
                        InventoryMovementType.RESTITUCION_CANCELACION -> "Reingreso Cancel."
                        InventoryMovementType.AJUSTE_MANUAL -> "Ajuste Manual"
                        InventoryMovementType.ALTA_INVENTARIO -> "Alta Inventario"
                        InventoryMovementType.EDICION_STOCK -> "Edición Stock"
                    }
                    val sign = if (mov.quantityDelta >= 0.0) "+" else "-"
                    val absDelta = kotlin.math.abs(mov.quantityDelta)
                    val unit = dataset.inventory.firstOrNull { it.id == mov.itemId }?.unit ?: ""
                    val unitSuffix = if (unit.isNotBlank()) " $unit" else ""
                    val originText = buildString {
                        if (mov.reason.isNotBlank()) append(mov.reason)
                        if (mov.performedBy.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(mov.performedBy)
                        }
                    }.ifBlank { "Sistema" }

                    mods.add(
                        InvModRow(
                            ts = mov.date,
                            cells = listOf(
                                ReportsEngine.formatDateTime(mov.date, timeZone),
                                label,
                                mov.referenceFolio.ifBlank { "-" },
                                mov.itemCode.ifBlank { "-" },
                                mov.itemName,
                                "$sign${ReportsEngine.formatQuantity(absDelta)}$unitSuffix",
                                originText
                            )
                        )
                    )
                }

                // Backward-compatible fallback for historical pre-v5 records without persisted movement rows
                for (sale in confirmedSales) {
                    if (loggedFolios.contains(sale.folio)) continue
                    for (line in ReportsEngine.parseSaleLines(sale, dataset.inventory)) {
                        if (line.itemType == ItemType.SERVICIO) continue
                        mods.add(
                            InvModRow(
                                ts = sale.date,
                                cells = listOf(
                                    ReportsEngine.formatDateTime(sale.date, timeZone),
                                    "Salida Venta",
                                    sale.folio,
                                    line.code,
                                    line.name,
                                    "-${ReportsEngine.formatQuantity(line.quantity)} ${line.unit}",
                                    sale.sellerName
                                )
                            )
                        )
                    }
                }
                for (sale in canceledSales.filter { it.type == SaleType.VENTA }) {
                    if (loggedFolios.contains(sale.folio)) continue
                    for (line in ReportsEngine.parseSaleLines(sale, dataset.inventory)) {
                        if (line.itemType == ItemType.SERVICIO) continue
                        mods.add(
                            InvModRow(
                                ts = sale.date,
                                cells = listOf(
                                    ReportsEngine.formatDateTime(sale.date, timeZone),
                                    "Reingreso Cancel.",
                                    sale.folio,
                                    line.code,
                                    line.name,
                                    "+${ReportsEngine.formatQuantity(line.quantity)} ${line.unit}",
                                    sale.sellerName
                                )
                            )
                        )
                    }
                }
                for (purch in fPurchases) {
                    if (loggedFolios.contains(purch.folio)) continue
                    for (line in ReportsEngine.parsePurchaseLines(purch, dataset.inventory)) {
                        mods.add(
                            InvModRow(
                                ts = purch.date,
                                cells = listOf(
                                    ReportsEngine.formatDateTime(purch.date, timeZone),
                                    "Entrada Compra",
                                    purch.folio,
                                    line.code,
                                    line.name,
                                    "+${ReportsEngine.formatQuantity(line.quantity)} ${line.unit}",
                                    purch.supplierName
                                )
                            )
                        )
                    }
                }
                for (waste in dataset.inventory.filter { it.isWaste }) {
                    mods.add(
                        InvModRow(
                            ts = 0L,
                            cells = listOf(
                                "Catálogo Actual",
                                "Merma Registrada",
                                waste.code,
                                waste.code,
                                waste.name,
                                "${ReportsEngine.formatQuantity(waste.stock)} ${waste.unit}",
                                waste.notes.ifBlank { "Merma" }
                            )
                        )
                    )
                }

                val sorted = mods.sortedByDescending { it.ts }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Fecha", "Movimiento", "Folio / Ref", "Código", "Artículo", "Variación Cant.", "Origen / Resp."),
                    rows = sorted.map { it.cells },
                    summaryStats = linkedMapOf(
                        "Movimientos Trazables" to sorted.size.toString()
                    ),
                    limitationsNote = if (loggedMovements.isNotEmpty()) {
                        "Bitácora persistente Room v5: incluye compras, ventas, cancelaciones, altas, ediciones y ajustes manuales."
                    } else {
                        "Incluye movimientos reales registrados en Room (compras, ventas, cancelaciones y mermas)."
                    }
                )
            }

            else -> GeneratedReport(
                title = title,
                periodLabel = periodLabel,
                headers = listOf("Reporte", "Estado"),
                rows = emptyList(),
                limitationsNote = "Reporte '$title' sin datos disponibles en el período."
            )
        }
    }

    private fun buildItemSalesReport(
        title: String,
        periodLabel: String,
        aggs: List<SoldItemAgg>,
        itemColHeader: String,
        cur: String,
        timeZone: TimeZone,
        discountsInPeriod: Double
    ): GeneratedReport {
        val totalQty = aggs.sumOf { it.quantity }
        val totalRev = aggs.sumOf { it.revenue }
        val allHaveCost = aggs.isNotEmpty() && aggs.all { it.totalLines > 0 && it.linesWithCost == it.totalLines }
        val partialCostCount = aggs.count { it.linesWithCost > 0 && it.linesWithCost < it.totalLines }
        val missingCostCount = aggs.count { it.linesWithCost < it.totalLines }
        val totalKnownCost = aggs.sumOf { it.totalCostKnown }

        val rows = aggs.map { agg ->
            val avgUnitPrice = if (agg.quantity > 0.0) agg.revenue / agg.quantity else 0.0
            val itemCostComplete = agg.totalLines > 0 && agg.linesWithCost == agg.totalLines
            val costCell = when {
                itemCostComplete -> ReportsEngine.formatCurrency(agg.totalCostKnown, cur)
                agg.linesWithCost > 0 -> "Parcial (${ReportsEngine.formatCurrency(agg.totalCostKnown, cur)})"
                else -> "Sin costo hist."
            }
            val profitCell = when {
                itemCostComplete -> ReportsEngine.formatCurrency(agg.revenue - agg.totalCostKnown, cur)
                else -> "No disponible"
            }
            listOf(
                agg.code,
                agg.name,
                "${ReportsEngine.formatQuantity(agg.quantity)} ${agg.unit}",
                ReportsEngine.formatCurrency(avgUnitPrice, cur),
                ReportsEngine.formatCurrency(agg.revenue, cur),
                costCell,
                profitCell,
                ReportsEngine.formatDateOnly(agg.lastSaleDate, timeZone)
            )
        }

        val noteParts = mutableListOf<String>()
        if (missingCostCount > 0 || partialCostCount > 0) {
            noteParts.add("Algunas partidas no contienen costPrice histórico en itemsJson; su utilidad no se estima con costos actuales.")
        }
        if (discountsInPeriod > 0.0) {
            noteParts.add("Importes por artículo reflejan precio de lista (descuentos de ticket del período: ${ReportsEngine.formatCurrency(discountsInPeriod, cur)}).")
        }

        return GeneratedReport(
            title = title,
            periodLabel = periodLabel,
            headers = listOf("Código", itemColHeader, "Cant. Vendida", "P. Unitario", "Total Venta", "Costo Hist.", "Utilidad", "Última Venta"),
            rows = rows,
            totalsRow = if (rows.isNotEmpty()) listOf(
                "TOTAL (${rows.size})",
                "",
                ReportsEngine.formatQuantity(totalQty),
                "",
                ReportsEngine.formatCurrency(totalRev, cur),
                if (allHaveCost) ReportsEngine.formatCurrency(totalKnownCost, cur) else "Incompleto",
                if (allHaveCost) ReportsEngine.formatCurrency(totalRev - totalKnownCost, cur) else "N/D",
                ""
            ) else null,
            summaryStats = linkedMapOf(
                "Artículos Dist." to rows.size.toString(),
                "Unidades" to ReportsEngine.formatQuantity(totalQty),
                "Total Bruto" to ReportsEngine.formatCurrency(totalRev, cur),
                "Utilidad Hist." to if (allHaveCost) ReportsEngine.formatCurrency(totalRev - totalKnownCost, cur) else "Sin costo completo"
            ),
            limitationsNote = noteParts.takeIf { it.isNotEmpty() }?.joinToString(" "),
            chartBars = aggs.take(6).map {
                ChartBarItem(it.name.take(14), it.revenue, ReportsEngine.formatCurrency(it.revenue, cur))
            }
        )
    }

    private fun buildItemPurchasesReport(
        title: String,
        periodLabel: String,
        aggs: List<PurchasedItemAgg>,
        itemColHeader: String,
        cur: String,
        timeZone: TimeZone,
        emptyNote: String?
    ): GeneratedReport {
        val totalQty = aggs.sumOf { it.quantity }
        val totalCost = aggs.sumOf { it.totalCost }

        val rows = aggs.map { agg ->
            val avgCost = if (agg.quantity > 0.0) agg.totalCost / agg.quantity else 0.0
            listOf(
                agg.code,
                agg.name,
                agg.category,
                "${ReportsEngine.formatQuantity(agg.quantity)} ${agg.unit}",
                ReportsEngine.formatCurrency(avgCost, cur),
                ReportsEngine.formatCurrency(agg.totalCost, cur),
                ReportsEngine.formatDateOnly(agg.lastPurchaseDate, timeZone)
            )
        }

        return GeneratedReport(
            title = title,
            periodLabel = periodLabel,
            headers = listOf("Código", itemColHeader, "Categoría", "Cant. Comprada", "Costo Unit.", "Total Comprado", "Última Compra"),
            rows = rows,
            totalsRow = if (rows.isNotEmpty()) listOf(
                "TOTAL (${rows.size})", "", "", ReportsEngine.formatQuantity(totalQty), "", ReportsEngine.formatCurrency(totalCost, cur), ""
            ) else null,
            summaryStats = linkedMapOf(
                "Artículos Comp." to rows.size.toString(),
                "Unidades" to ReportsEngine.formatQuantity(totalQty),
                "Total Comprado" to ReportsEngine.formatCurrency(totalCost, cur)
            ),
            limitationsNote = emptyNote,
            chartBars = aggs.take(6).map {
                ChartBarItem(it.name.take(14), it.totalCost, ReportsEngine.formatCurrency(it.totalCost, cur))
            }
        )
    }

    private fun buildProductAnalysisReport(
        title: String,
        periodLabel: String,
        catalogItems: List<InventoryItem>,
        soldAggs: List<SoldItemAgg>,
        purchAggs: List<PurchasedItemAgg>,
        itemColHeader: String,
        cur: String
    ): GeneratedReport {
        val allSoldQtys = catalogItems.map { soldQtyForItem(it, soldAggs)?.quantity ?: 0.0 } +
            soldAggs.map { it.quantity }
        val seenKeys = mutableSetOf<String>()
        var totalRev = 0.0
        var totalSoldQty = 0.0

        val sortableRows = mutableListOf<Pair<Double, List<String>>>()
        val chartItems = mutableListOf<ChartBarItem>()

        for (item in catalogItems) {
            val sAgg = soldQtyForItem(item, soldAggs)
            if (sAgg != null) seenKeys.add(sAgg.key)
            val pAgg = purchasedQtyForItem(item, purchAggs)
            val soldQty = sAgg?.quantity ?: 0.0
            val purchQty = pAgg?.quantity ?: 0.0
            val rev = sAgg?.revenue ?: 0.0
            totalRev += rev
            totalSoldQty += soldQty

            val profitStr = when {
                sAgg == null -> ReportsEngine.formatCurrency(0.0, cur)
                sAgg.totalLines > 0 && sAgg.linesWithCost == sAgg.totalLines ->
                    ReportsEngine.formatCurrency(rev - sAgg.totalCostKnown, cur)
                else -> "Sin costo hist."
            }

            sortableRows.add(
                rev to listOf(
                    item.code,
                    item.name,
                    ReportsEngine.formatQuantity(soldQty),
                    ReportsEngine.formatQuantity(purchQty),
                    "${ReportsEngine.formatQuantity(item.stock)} ${item.unit}",
                    ReportsEngine.formatCurrency(rev, cur),
                    profitStr,
                    rotationLabel(soldQty, allSoldQtys)
                )
            )
            if (rev > 0.0) {
                chartItems.add(ChartBarItem(item.name.take(14), rev, ReportsEngine.formatCurrency(rev, cur)))
            }
        }

        for (sAgg in soldAggs.filter { it.key !in seenKeys }) {
            totalRev += sAgg.revenue
            totalSoldQty += sAgg.quantity
            val complete = sAgg.totalLines > 0 && sAgg.linesWithCost == sAgg.totalLines
            sortableRows.add(
                sAgg.revenue to listOf(
                    sAgg.code,
                    sAgg.name,
                    ReportsEngine.formatQuantity(sAgg.quantity),
                    "0",
                    "No en cat.",
                    ReportsEngine.formatCurrency(sAgg.revenue, cur),
                    if (complete) ReportsEngine.formatCurrency(sAgg.revenue - sAgg.totalCostKnown, cur) else "Sin costo hist.",
                    rotationLabel(sAgg.quantity, allSoldQtys)
                )
            )
            chartItems.add(ChartBarItem(sAgg.name.take(14), sAgg.revenue, ReportsEngine.formatCurrency(sAgg.revenue, cur)))
        }

        val sorted = sortableRows.sortedByDescending { it.first }
        return GeneratedReport(
            title = title,
            periodLabel = periodLabel,
            headers = listOf("Código", itemColHeader, "Vendidas", "Compradas", "Stock Act.", "Ingresos", "Utilidad Est.", "Rotación"),
            rows = sorted.map { it.second },
            totalsRow = if (sorted.isNotEmpty()) listOf(
                "TOTAL (${sorted.size})", "", ReportsEngine.formatQuantity(totalSoldQty), "", "",
                ReportsEngine.formatCurrency(totalRev, cur), "", ""
            ) else null,
            summaryStats = linkedMapOf(
                "Artículos Evaluados" to sorted.size.toString(),
                "Ingresos Período" to ReportsEngine.formatCurrency(totalRev, cur)
            ),
            chartBars = chartItems.sortedByDescending { it.value }.take(6)
        )
    }

    private fun buildWasteReport(
        title: String,
        periodLabel: String,
        items: List<InventoryItem>,
        cur: String,
        emptyNote: String?
    ): GeneratedReport {
        val totalLoss = items.sumOf { it.stock.coerceAtLeast(0.0) * it.costPrice }
        val totalUnits = items.sumOf { it.stock.coerceAtLeast(0.0) }
        val rows = items.map { item ->
            val loss = item.stock.coerceAtLeast(0.0) * item.costPrice
            listOf(
                item.code,
                item.name,
                item.category,
                "${ReportsEngine.formatQuantity(item.stock)} ${item.unit}",
                ReportsEngine.formatCurrency(item.costPrice, cur),
                ReportsEngine.formatCurrency(loss, cur),
                item.notes.ifBlank { "-" }
            )
        }
        return GeneratedReport(
            title = title,
            periodLabel = periodLabel,
            headers = listOf("Código", "Artículo Merma", "Categoría", "Cantidad", "Costo Unit.", "Pérdida Costo", "Observaciones"),
            rows = rows,
            totalsRow = if (rows.isNotEmpty()) listOf(
                "TOTAL (${rows.size})", "", "", ReportsEngine.formatQuantity(totalUnits), "", ReportsEngine.formatCurrency(totalLoss, cur), ""
            ) else null,
            summaryStats = linkedMapOf(
                "Registros Merma" to rows.size.toString(),
                "Pérdida Valorizada" to ReportsEngine.formatCurrency(totalLoss, cur)
            ),
            limitationsNote = emptyNote
        )
    }

    private fun buildInventorySummaryReport(
        title: String,
        periodLabel: String,
        items: List<InventoryItem>,
        itemColHeader: String,
        cur: String
    ): GeneratedReport {
        var totalUnits = 0.0
        var totalCostVal = 0.0
        var totalSaleVal = 0.0
        var outCount = 0
        var lowCount = 0

        val rows = items.map { item ->
            val st = item.stock.coerceAtLeast(0.0)
            val valCost = st * item.costPrice
            val valSale = st * item.salePrice
            totalUnits += st
            totalCostVal += valCost
            totalSaleVal += valSale
            if (item.stock <= 0.0) outCount += 1 else if (item.stock <= item.minStock) lowCount += 1
            listOf(
                item.code,
                item.name,
                item.category,
                "${ReportsEngine.formatQuantity(item.stock)} ${item.unit}",
                ReportsEngine.formatQuantity(item.minStock),
                ReportsEngine.formatCurrency(item.costPrice, cur),
                ReportsEngine.formatCurrency(valCost, cur),
                ReportsEngine.formatCurrency(valSale, cur),
                stockStatusLabel(item)
            )
        }
        return GeneratedReport(
            title = title,
            periodLabel = periodLabel,
            headers = listOf("Código", itemColHeader, "Categoría", "Stock", "Mín.", "Costo Unit.", "Valor Costo", "Valor Venta", "Estado"),
            rows = rows,
            totalsRow = if (rows.isNotEmpty()) listOf(
                "TOTAL (${rows.size})", "", "", ReportsEngine.formatQuantity(totalUnits), "", "",
                ReportsEngine.formatCurrency(totalCostVal, cur),
                ReportsEngine.formatCurrency(totalSaleVal, cur),
                "$outCount ag. / $lowCount bajo"
            ) else null,
            summaryStats = linkedMapOf(
                "SKUs" to rows.size.toString(),
                "Agotados" to outCount.toString(),
                "Stock Bajo" to lowCount.toString(),
                "Valor Costo" to ReportsEngine.formatCurrency(totalCostVal, cur)
            )
        )
    }
}
