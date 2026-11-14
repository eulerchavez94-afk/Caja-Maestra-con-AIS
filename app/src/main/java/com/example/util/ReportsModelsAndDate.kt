package com.example.util

import com.example.data.model.BusinessSettings
import com.example.data.model.CashRegisterShift
import com.example.data.model.CashShiftMovement
import com.example.data.model.CashShiftMovementType
import com.example.data.model.Client
import com.example.data.model.Employee
import com.example.data.model.ExtraMovement
import com.example.data.model.InventoryItem
import com.example.data.model.InventoryMovement
import com.example.data.model.ItemType
import com.example.data.model.MovementType
import com.example.data.model.Purchase
import com.example.data.model.RentalLoan
import com.example.data.model.Sale
import com.example.data.model.SaleStatus
import com.example.data.model.SaleType
import com.example.data.model.Supplier
import com.example.ui.viewmodel.DateFilterType
import org.json.JSONArray
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DateRangeBounds(
    val startMs: Long,
    val endMs: Long,
    val label: String
)

enum class CostCoverage {
    NO_SALES,
    COMPLETE,
    PARTIAL,
    UNAVAILABLE
}

data class FinancialSummary(
    val periodLabel: String,
    val confirmedSalesCount: Int,
    val confirmedSalesTotal: Double,
    val confirmedSalesDiscounts: Double,
    val collectedSalesTotal: Double,
    val creditSalesTotal: Double,
    val quotesCount: Int,
    val quotesPendingTotal: Double,
    val canceledSalesCount: Int,
    val canceledSalesTotal: Double,
    val purchasesCount: Int,
    val purchasesTotal: Double,
    val purchasesPaidTotal: Double,
    val purchasesCreditTotal: Double,
    val extraIncomeTotal: Double,
    val extraIncomeCreditTotal: Double,
    val extraExpensesTotal: Double,
    val extraExpensesCreditTotal: Double,
    val cashDrawerEntriesTotal: Double,
    val cashDrawerWithdrawalsTotal: Double,
    val costCoverage: CostCoverage,
    val linesWithHistoricalCost: Int,
    val totalSaleLines: Int,
    val historicalCogsTotal: Double,
    val partialCoveredRevenue: Double,
    val grossProfit: Double?,
    val netProfit: Double?,
    val profitLimitationNote: String?
)

data class ChartBarItem(
    val label: String,
    val value: Double,
    val displayValue: String
)

data class GeneratedReport(
    val title: String,
    val periodLabel: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val totalsRow: List<String>? = null,
    val summaryStats: Map<String, String> = emptyMap(),
    val limitationsNote: String? = null,
    val chartBars: List<ChartBarItem> = emptyList()
)

data class ReportsDataset(
    val sales: List<Sale> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val extraMovements: List<ExtraMovement> = emptyList(),
    val rentalsLoans: List<RentalLoan> = emptyList(),
    val clients: List<Client> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val employees: List<Employee> = emptyList(),
    val cashShifts: List<CashRegisterShift> = emptyList(),
    val cashShiftMovements: List<CashShiftMovement> = emptyList(),
    val inventoryMovements: List<InventoryMovement> = emptyList()
)

data class ParsedSaleLine(
    val saleId: Long,
    val saleFolio: String,
    val saleDate: Long,
    val saleStatus: SaleStatus,
    val itemId: Long,
    val code: String,
    val name: String,
    val category: String,
    val quantity: Double,
    val unitPrice: Double,
    val subtotal: Double,
    val historicalUnitCost: Double?,
    val historicalTotalCost: Double?,
    val unit: String,
    val itemType: ItemType
)

data class ParsedPurchaseLine(
    val purchaseId: Long,
    val purchaseFolio: String,
    val purchaseDate: Long,
    val supplierName: String,
    val isCredit: Boolean,
    val itemId: Long,
    val code: String,
    val name: String,
    val category: String,
    val quantity: Double,
    val unitCost: Double,
    val subtotal: Double,
    val unit: String,
    val itemType: ItemType
)

object ReportsEngine {

    private val spanishMxLocale = Locale.Builder().setLanguage("es").setRegion("MX").build()
    private val currencyFormatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    private val qtyFormatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    fun formatCurrency(amount: Double, currencySymbol: String): String {
        val safe = if (amount.isFinite()) amount else 0.0
        val sign = if (safe < 0) "-" else ""
        val absVal = kotlin.math.abs(safe)
        return "$sign$currencySymbol${currencyFormatter.format(absVal)}"
    }

    fun formatQuantity(qty: Double): String {
        val safe = if (qty.isFinite()) qty else 0.0
        return qtyFormatter.format(safe)
    }

    fun formatDateTime(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        return fmt.format(Date(timestampMs))
    }

    fun formatDateOnly(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        return fmt.format(Date(timestampMs))
    }

    fun formatDayKey(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        return fmt.format(Date(timestampMs))
    }

    fun computeDateRange(
        filter: DateFilterType,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): DateRangeBounds? {
        if (filter == DateFilterType.TODOS) return null

        val cal = Calendar.getInstance(timeZone, spanishMxLocale).apply {
            timeInMillis = nowMs
        }

        val endCal = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val startCal = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (filter) {
            DateFilterType.HOY -> {
                DateRangeBounds(
                    startMs = startCal.timeInMillis,
                    endMs = endCal.timeInMillis,
                    label = "Hoy (${formatDateOnly(nowMs, timeZone)})"
                )
            }
            DateFilterType.ULTIMA_SEMANA -> {
                startCal.add(Calendar.DAY_OF_YEAR, -6)
                DateRangeBounds(
                    startMs = startCal.timeInMillis,
                    endMs = endCal.timeInMillis,
                    label = "Últimos 7 días (${formatDateOnly(startCal.timeInMillis, timeZone)} - ${formatDateOnly(nowMs, timeZone)})"
                )
            }
            DateFilterType.ESTE_MES -> {
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                val monthEndCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val monthLabel = SimpleDateFormat("MMMM yyyy", spanishMxLocale).apply {
                    this.timeZone = timeZone
                }.format(Date(nowMs)).replaceFirstChar { it.uppercase() }
                DateRangeBounds(
                    startMs = startCal.timeInMillis,
                    endMs = monthEndCal.timeInMillis,
                    label = "Este Mes ($monthLabel)"
                )
            }
            DateFilterType.TODOS -> null
        }
    }

    internal fun inRange(timestampMs: Long, bounds: DateRangeBounds?): Boolean {
        return bounds == null || (timestampMs >= bounds.startMs && timestampMs <= bounds.endMs)
    }

    fun isMateriaPrima(item: InventoryItem): Boolean {
        if (item.type != ItemType.PRODUCTO) return false
        return item.isRawMaterial || isMateriaPrimaCategoryOrText(item.category, item.notes, item.name)
    }

    internal fun isMateriaPrimaCategoryOrText(
        category: String,
        notes: String = "",
        name: String = ""
    ): Boolean {
        val cat = PosSearchEngine.normalize(category)
        val nt = PosSearchEngine.normalize(notes)
        val nm = PosSearchEngine.normalize(name)
        return cat.contains("materia prima") ||
            cat.contains("insumo") ||
            cat.contains("ingrediente") ||
            cat.contains("empaque") ||
            cat.contains("granel") ||
            nt.contains("materia prima") ||
            nm.contains("materia prima")
    }

    internal fun resolveCatalogItem(
        itemId: Long,
        name: String,
        inventoryById: Map<Long, InventoryItem>,
        inventoryByNormName: Map<String, InventoryItem>,
        inventoryList: List<InventoryItem>
    ): InventoryItem? {
        if (itemId > 0L) {
            inventoryById[itemId]?.let { return it }
        }
        val norm = PosSearchEngine.normalize(name)
        if (norm.isEmpty()) return null
        inventoryByNormName[norm]?.let { return it }
        return inventoryList.firstOrNull {
            val invNorm = PosSearchEngine.normalize(it.name)
            invNorm.startsWith(norm) || norm.startsWith(invNorm)
        }
    }

    fun parseSaleLines(
        sale: Sale,
        inventory: List<InventoryItem>
    ): List<ParsedSaleLine> {
        if (sale.itemsJson.isBlank()) return emptyList()
        val byId = inventory.associateBy { it.id }
        val byNormName = inventory.associateBy { PosSearchEngine.normalize(it.name) }
        val result = mutableListOf<ParsedSaleLine>()

        runCatching {
            val arr = JSONArray(sale.itemsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val itemId = obj.optLong("itemId", 0L)
                val name = obj.optString("name", "").trim().ifEmpty { "Artículo sin nombre" }
                val qty = obj.optDouble("quantity", 0.0)
                if (!qty.isFinite() || qty <= 0.0) continue

                val unitPrice = obj.optDouble("unitPrice", 0.0).let {
                    if (it.isFinite() && it >= 0.0) it else 0.0
                }
                val rawSubtotal = obj.optDouble("subtotal", Double.NaN)
                val subtotal = if (rawSubtotal.isFinite() && rawSubtotal >= 0.0) {
                    rawSubtotal
                } else {
                    qty * unitPrice
                }

                val catalog = resolveCatalogItem(itemId, name, byId, byNormName, inventory)
                val parsedType = runCatching {
                    val typeRaw = obj.optString("type", "")
                    if (typeRaw.isNotBlank()) ItemType.valueOf(typeRaw) else null
                }.getOrNull() ?: catalog?.type ?: ItemType.PRODUCTO

                // Strict accounting rule: NEVER reconstruct missing historical costPrice from current catalog cost
                val hasCostKey = obj.has("costPrice")
                val rawCost = if (hasCostKey) obj.optDouble("costPrice", Double.NaN) else Double.NaN
                val validHistoricalUnitCost: Double? = when {
                    !hasCostKey || !rawCost.isFinite() || rawCost < 0.0 -> null
                    parsedType != ItemType.SERVICIO && rawCost <= 0.0 -> null
                    else -> rawCost
                }

                result.add(
                    ParsedSaleLine(
                        saleId = sale.id,
                        saleFolio = sale.folio,
                        saleDate = sale.date,
                        saleStatus = sale.status,
                        itemId = if (itemId > 0L) itemId else (catalog?.id ?: 0L),
                        code = obj.optString("code", "").ifBlank { catalog?.code ?: "S/C" },
                        name = name,
                        category = obj.optString("category", "").ifBlank { catalog?.category ?: "General" },
                        quantity = qty,
                        unitPrice = unitPrice,
                        subtotal = subtotal,
                        historicalUnitCost = validHistoricalUnitCost,
                        historicalTotalCost = validHistoricalUnitCost?.let { it * qty },
                        unit = obj.optString("unit", "").ifBlank { catalog?.unit ?: "Pza" },
                        itemType = parsedType
                    )
                )
            }
        }
        return result
    }

    fun parsePurchaseLines(
        purchase: Purchase,
        inventory: List<InventoryItem>
    ): List<ParsedPurchaseLine> {
        if (purchase.itemsJson.isBlank()) return emptyList()
        val byId = inventory.associateBy { it.id }
        val byNormName = inventory.associateBy { PosSearchEngine.normalize(it.name) }
        val result = mutableListOf<ParsedPurchaseLine>()

        runCatching {
            val arr = JSONArray(purchase.itemsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val itemId = obj.optLong("itemId", 0L)
                val name = obj.optString("name", "").trim().ifEmpty { "Artículo comprado" }
                val qty = obj.optDouble("quantity", 0.0)
                if (!qty.isFinite() || qty <= 0.0) continue

                val unitCost = obj.optDouble("unitPrice", Double.NaN).let {
                    if (it.isFinite() && it >= 0.0) it
                    else obj.optDouble("costPrice", 0.0).let { c -> if (c.isFinite() && c >= 0.0) c else 0.0 }
                }
                val rawSubtotal = obj.optDouble("subtotal", Double.NaN)
                val subtotal = if (rawSubtotal.isFinite() && rawSubtotal >= 0.0) rawSubtotal else qty * unitCost

                val catalog = resolveCatalogItem(itemId, name, byId, byNormName, inventory)
                val parsedType = runCatching {
                    val typeRaw = obj.optString("type", "")
                    if (typeRaw.isNotBlank()) ItemType.valueOf(typeRaw) else null
                }.getOrNull() ?: catalog?.type ?: ItemType.PRODUCTO

                result.add(
                    ParsedPurchaseLine(
                        purchaseId = purchase.id,
                        purchaseFolio = purchase.folio,
                        purchaseDate = purchase.date,
                        supplierName = purchase.supplierName,
                        isCredit = purchase.isCredit,
                        itemId = if (itemId > 0L) itemId else (catalog?.id ?: 0L),
                        code = obj.optString("code", "").ifBlank { catalog?.code ?: "S/C" },
                        name = name,
                        category = obj.optString("category", "").ifBlank { catalog?.category ?: "General" },
                        quantity = qty,
                        unitCost = unitCost,
                        subtotal = subtotal,
                        unit = obj.optString("unit", "").ifBlank { catalog?.unit ?: "Pza" },
                        itemType = parsedType
                    )
                )
            }
        }
        return result
    }

    internal fun saleCollectedAmount(sale: Sale): Double {
        if (sale.type != SaleType.VENTA || sale.status == SaleStatus.CANCELADA) return 0.0
        return when (sale.status) {
            SaleStatus.PAGADA -> sale.total.coerceAtLeast(0.0)
            SaleStatus.CREDITO -> sale.paidAmount.coerceIn(0.0, sale.total.coerceAtLeast(0.0))
            SaleStatus.PENDIENTE, SaleStatus.CANCELADA -> 0.0
        }
    }

    internal fun salePendingCreditAmount(sale: Sale): Double {
        if (sale.type != SaleType.VENTA || sale.status != SaleStatus.CREDITO) return 0.0
        return (sale.total - sale.paidAmount.coerceAtLeast(0.0)).coerceAtLeast(0.0)
    }

    fun computeFinancialSummary(
        dataset: ReportsDataset,
        dateFilter: DateFilterType,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): FinancialSummary {
        val bounds = computeDateRange(dateFilter, nowMs, timeZone)
        val periodLabel = bounds?.label ?: "Todo el Historial"

        val filteredSales = dataset.sales.filter { inRange(it.date, bounds) }
        val confirmedSales = filteredSales.filter { it.type == SaleType.VENTA && it.status != SaleStatus.CANCELADA }
        val quotes = filteredSales.filter { it.type == SaleType.COTIZACION && it.status != SaleStatus.CANCELADA }
        val canceled = filteredSales.filter { it.status == SaleStatus.CANCELADA }

        val confirmedTotal = confirmedSales.sumOf { it.total }
        val confirmedDiscounts = confirmedSales.sumOf { it.discount }
        val collectedTotal = confirmedSales.sumOf { saleCollectedAmount(it) }
        val creditTotal = confirmedSales.sumOf { salePendingCreditAmount(it) }

        val filteredPurchases = dataset.purchases.filter { inRange(it.date, bounds) }
        val purchasesTotal = filteredPurchases.sumOf { it.total }
        val purchasesCredit = filteredPurchases.filter { it.isCredit }.sumOf { it.total }
        val purchasesPaid = filteredPurchases.filter { !it.isCredit }.sumOf { it.total }

        val filteredExtra = dataset.extraMovements.filter { inRange(it.date, bounds) }
        val extraIncome = filteredExtra.filter { it.type == MovementType.INGRESO }.sumOf { it.amount }
        val extraIncomeCredit = filteredExtra.filter { it.type == MovementType.INGRESO && it.isCredit }.sumOf { it.amount }
        val extraExpenses = filteredExtra.filter { it.type == MovementType.GASTO }.sumOf { it.amount }
        val extraExpensesCredit = filteredExtra.filter { it.type == MovementType.GASTO && it.isCredit }.sumOf { it.amount }

        val filteredDrawer = dataset.cashShiftMovements.filter { inRange(it.date, bounds) }
        val drawerEntries = filteredDrawer.filter { it.type == CashShiftMovementType.ENTRADA }.sumOf { it.amount }
        val drawerWithdrawals = filteredDrawer.filter { it.type == CashShiftMovementType.RETIRO }.sumOf { it.amount }

        val allSaleLines = confirmedSales.flatMap { parseSaleLines(it, dataset.inventory) }
        val salesWithoutParsedLines = confirmedSales.count { parseSaleLines(it, dataset.inventory).isEmpty() }
        val linesWithCost = allSaleLines.filter { it.historicalTotalCost != null }
        val historicalCogs = linesWithCost.sumOf { it.historicalTotalCost ?: 0.0 }
        val partialCoveredRevenue = linesWithCost.sumOf { it.subtotal }

        val coverage = when {
            confirmedSales.isEmpty() -> CostCoverage.NO_SALES
            salesWithoutParsedLines == 0 && allSaleLines.isNotEmpty() && linesWithCost.size == allSaleLines.size ->
                CostCoverage.COMPLETE
            linesWithCost.isNotEmpty() -> CostCoverage.PARTIAL
            else -> CostCoverage.UNAVAILABLE
        }

        val grossProfit: Double? = when (coverage) {
            CostCoverage.NO_SALES -> 0.0
            CostCoverage.COMPLETE -> confirmedTotal - historicalCogs
            CostCoverage.PARTIAL, CostCoverage.UNAVAILABLE -> null
        }

        val netProfit: Double? = grossProfit?.let { gp ->
            gp + extraIncome - extraExpenses
        }

        val limitationNote: String? = when (coverage) {
            CostCoverage.NO_SALES, CostCoverage.COMPLETE -> null
            CostCoverage.PARTIAL -> {
                val missing = (allSaleLines.size - linesWithCost.size) + salesWithoutParsedLines
                "Cobertura parcial de costo histórico: $missing partida(s)/venta(s) no incluyen costPrice histórico. No se calcula utilidad completa para evitar cifras ficticias."
            }
            CostCoverage.UNAVAILABLE -> {
                "Sin costo histórico registrado en las partidas vendidas (itemsJson). No se sustituye con compras del período ni costos actuales."
            }
        }

        return FinancialSummary(
            periodLabel = periodLabel,
            confirmedSalesCount = confirmedSales.size,
            confirmedSalesTotal = confirmedTotal,
            confirmedSalesDiscounts = confirmedDiscounts,
            collectedSalesTotal = collectedTotal,
            creditSalesTotal = creditTotal,
            quotesCount = quotes.size,
            quotesPendingTotal = quotes.sumOf { it.total },
            canceledSalesCount = canceled.size,
            canceledSalesTotal = canceled.sumOf { it.total },
            purchasesCount = filteredPurchases.size,
            purchasesTotal = purchasesTotal,
            purchasesPaidTotal = purchasesPaid,
            purchasesCreditTotal = purchasesCredit,
            extraIncomeTotal = extraIncome,
            extraIncomeCreditTotal = extraIncomeCredit,
            extraExpensesTotal = extraExpenses,
            extraExpensesCreditTotal = extraExpensesCredit,
            cashDrawerEntriesTotal = drawerEntries,
            cashDrawerWithdrawalsTotal = drawerWithdrawals,
            costCoverage = coverage,
            linesWithHistoricalCost = linesWithCost.size,
            totalSaleLines = allSaleLines.size,
            historicalCogsTotal = historicalCogs,
            partialCoveredRevenue = partialCoveredRevenue,
            grossProfit = grossProfit,
            netProfit = netProfit,
            profitLimitationNote = limitationNote
        )
    }

    fun buildReport(
        title: String,
        dateFilter: DateFilterType,
        dataset: ReportsDataset,
        settings: BusinessSettings,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): GeneratedReport {
        val bounds = computeDateRange(dateFilter, nowMs, timeZone)
        val periodLabel = bounds?.label ?: "Todo el Historial"
        val cur = settings.currencySymbol
        val summary = computeFinancialSummary(dataset, dateFilter, nowMs, timeZone)

        return ReportsSalesAndCashBuilders.tryBuild(
            title = title,
            bounds = bounds,
            periodLabel = periodLabel,
            dataset = dataset,
            summary = summary,
            cur = cur,
            nowMs = nowMs,
            timeZone = timeZone
        ) ?: ReportsInventoryAndProductBuilders.build(
            title = title,
            bounds = bounds,
            periodLabel = periodLabel,
            dataset = dataset,
            summary = summary,
            cur = cur,
            timeZone = timeZone
        )
    }
}
