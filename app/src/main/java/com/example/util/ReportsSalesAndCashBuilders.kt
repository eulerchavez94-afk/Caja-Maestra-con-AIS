package com.example.util

import com.example.data.model.CashShiftMovementType
import com.example.data.model.MovementType
import com.example.data.model.SaleStatus
import com.example.data.model.SaleType
import java.util.Locale
import java.util.TimeZone

internal object ReportsSalesAndCashBuilders {

    fun tryBuild(
        title: String,
        bounds: DateRangeBounds?,
        periodLabel: String,
        dataset: ReportsDataset,
        summary: FinancialSummary,
        cur: String,
        nowMs: Long,
        timeZone: TimeZone
    ): GeneratedReport? {
        val fSales = dataset.sales.filter { ReportsEngine.inRange(it.date, bounds) }
        val confirmedSales = fSales.filter { it.type == SaleType.VENTA && it.status != SaleStatus.CANCELADA }
        val fPurchases = dataset.purchases.filter { ReportsEngine.inRange(it.date, bounds) }
        val fExtra = dataset.extraMovements.filter { ReportsEngine.inRange(it.date, bounds) }
        val fShifts = dataset.cashShifts.filter { ReportsEngine.inRange(it.openedAt, bounds) }
        val fDrawerMoves = dataset.cashShiftMovements.filter { ReportsEngine.inRange(it.date, bounds) }
        val fRentalsLoans = dataset.rentalsLoans.filter { ReportsEngine.inRange(it.startDate, bounds) }

        return when (title) {
            "Transacciones Por Día" -> {
                data class DayAcc(
                    val displayDate: String,
                    var salesCount: Int = 0,
                    var confirmedTotal: Double = 0.0,
                    var collectedTotal: Double = 0.0,
                    var purchasesTotal: Double = 0.0,
                    var extraIncome: Double = 0.0,
                    var extraExpenses: Double = 0.0
                )
                val byDay = linkedMapOf<String, DayAcc>()
                fun accFor(ts: Long): DayAcc {
                    val key = ReportsEngine.formatDayKey(ts, timeZone)
                    return byDay.getOrPut(key) { DayAcc(ReportsEngine.formatDateOnly(ts, timeZone)) }
                }
                confirmedSales.forEach { s ->
                    val acc = accFor(s.date)
                    acc.salesCount += 1
                    acc.confirmedTotal += s.total
                    acc.collectedTotal += ReportsEngine.saleCollectedAmount(s)
                }
                fPurchases.forEach { p -> accFor(p.date).purchasesTotal += p.total }
                fExtra.forEach { m ->
                    val acc = accFor(m.date)
                    if (m.type == MovementType.INGRESO) acc.extraIncome += m.amount else acc.extraExpenses += m.amount
                }

                val sortedEntries = byDay.entries.sortedByDescending { it.key }
                val rows = sortedEntries.map { (_, d) ->
                    val netDay = d.collectedTotal + d.extraIncome - d.purchasesTotal - d.extraExpenses
                    listOf(
                        d.displayDate,
                        d.salesCount.toString(),
                        ReportsEngine.formatCurrency(d.confirmedTotal, cur),
                        ReportsEngine.formatCurrency(d.collectedTotal, cur),
                        ReportsEngine.formatCurrency(d.purchasesTotal, cur),
                        ReportsEngine.formatCurrency(d.extraIncome, cur),
                        ReportsEngine.formatCurrency(d.extraExpenses, cur),
                        ReportsEngine.formatCurrency(netDay, cur)
                    )
                }
                val netTotal = summary.collectedSalesTotal + summary.extraIncomeTotal - summary.purchasesTotal - summary.extraExpensesTotal
                val totals = if (rows.isNotEmpty()) listOf(
                    "TOTAL (${rows.size} días)",
                    summary.confirmedSalesCount.toString(),
                    ReportsEngine.formatCurrency(summary.confirmedSalesTotal, cur),
                    ReportsEngine.formatCurrency(summary.collectedSalesTotal, cur),
                    ReportsEngine.formatCurrency(summary.purchasesTotal, cur),
                    ReportsEngine.formatCurrency(summary.extraIncomeTotal, cur),
                    ReportsEngine.formatCurrency(summary.extraExpensesTotal, cur),
                    ReportsEngine.formatCurrency(netTotal, cur)
                ) else null

                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Fecha", "Ventas Conf.", "Total Facturado", "Cobrado", "Compras", "Ing. Extra", "Gastos Ext.", "Flujo Neto"),
                    rows = rows,
                    totalsRow = totals,
                    summaryStats = linkedMapOf(
                        "Ventas Conf." to ReportsEngine.formatCurrency(summary.confirmedSalesTotal, cur),
                        "Cobrado Real" to ReportsEngine.formatCurrency(summary.collectedSalesTotal, cur),
                        "Compras" to ReportsEngine.formatCurrency(summary.purchasesTotal, cur),
                        "Gastos Ext." to ReportsEngine.formatCurrency(summary.extraExpensesTotal, cur)
                    ),
                    chartBars = sortedEntries.take(6).map { (_, d) ->
                        ChartBarItem(d.displayDate.take(5), d.confirmedTotal, ReportsEngine.formatCurrency(d.confirmedTotal, cur))
                    }
                )
            }

            "Resumen Transacciones" -> {
                data class LedgerRow(val ts: Long, val cells: List<String>)
                val entries = mutableListOf<LedgerRow>()

                fSales.forEach { s ->
                    val typeLabel = when {
                        s.status == SaleStatus.CANCELADA -> "Venta Cancelada"
                        s.type == SaleType.COTIZACION -> "Cotización"
                        s.status == SaleStatus.CREDITO -> "Venta Crédito"
                        else -> "Venta Confirmada"
                    }
                    entries.add(
                        LedgerRow(
                            ts = s.date,
                            cells = listOf(
                                ReportsEngine.formatDateTime(s.date, timeZone),
                                typeLabel,
                                s.folio,
                                s.customerName,
                                s.paymentMethod,
                                s.status.name,
                                ReportsEngine.formatCurrency(s.total, cur)
                            )
                        )
                    )
                }
                fPurchases.forEach { p ->
                    entries.add(
                        LedgerRow(
                            ts = p.date,
                            cells = listOf(
                                ReportsEngine.formatDateTime(p.date, timeZone),
                                "Compra",
                                p.folio,
                                p.supplierName,
                                if (p.isCredit) "Crédito" else "Contado",
                                if (p.isCredit) "PENDIENTE" else "PAGADA",
                                ReportsEngine.formatCurrency(-p.total, cur)
                            )
                        )
                    )
                }
                fExtra.forEach { m ->
                    val signed = if (m.type == MovementType.INGRESO) m.amount else -m.amount
                    entries.add(
                        LedgerRow(
                            ts = m.date,
                            cells = listOf(
                                ReportsEngine.formatDateTime(m.date, timeZone),
                                if (m.type == MovementType.INGRESO) "Ingreso Extra" else "Gasto Extra",
                                "MOV-${m.id}",
                                m.concept,
                                m.category,
                                if (m.isCredit) "CREDITO" else "APLICADO",
                                ReportsEngine.formatCurrency(signed, cur)
                            )
                        )
                    )
                }
                fDrawerMoves.forEach { d ->
                    val label = if (d.type == CashShiftMovementType.ENTRADA) "Entrada Caja" else "Retiro Caja"
                    entries.add(
                        LedgerRow(
                            ts = d.date,
                            cells = listOf(
                                ReportsEngine.formatDateTime(d.date, timeZone),
                                label,
                                "TURNO-${d.shiftId}",
                                d.reason,
                                "Caja (${d.performedBy})",
                                "NO CONTABLE",
                                ReportsEngine.formatCurrency(d.amount, cur)
                            )
                        )
                    )
                }

                val sorted = entries.sortedByDescending { it.ts }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Fecha", "Tipo", "Folio / Ref", "Concepto / Cliente", "Método / Cat.", "Estado", "Monto"),
                    rows = sorted.map { it.cells },
                    totalsRow = if (sorted.isNotEmpty()) listOf(
                        "RESUMEN (${sorted.size} mov.)",
                        "${summary.confirmedSalesCount} Vtas",
                        "${summary.quotesCount} Cot.",
                        "${summary.canceledSalesCount} Canc.",
                        "${summary.purchasesCount} Comp.",
                        "Neto Op.:",
                        ReportsEngine.formatCurrency(summary.collectedSalesTotal + summary.extraIncomeTotal - summary.purchasesTotal - summary.extraExpensesTotal, cur)
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Ventas Conf." to "${summary.confirmedSalesCount} (${ReportsEngine.formatCurrency(summary.confirmedSalesTotal, cur)})",
                        "Cotizaciones Pend." to "${summary.quotesCount} (${ReportsEngine.formatCurrency(summary.quotesPendingTotal, cur)})",
                        "Canceladas" to "${summary.canceledSalesCount} (${ReportsEngine.formatCurrency(summary.canceledSalesTotal, cur)})",
                        "Mov. Caja (No Ing.)" to "Ent ${ReportsEngine.formatCurrency(summary.cashDrawerEntriesTotal, cur)} / Ret ${ReportsEngine.formatCurrency(summary.cashDrawerWithdrawalsTotal, cur)}"
                    )
                )
            }

            "Ventas Crédito" -> {
                val creditSales = confirmedSales.filter {
                    it.status == SaleStatus.CREDITO || PosSearchEngine.normalize(it.paymentMethod).contains("credito")
                }
                val totalAmount = creditSales.sumOf { it.total }
                val paidSum = creditSales.sumOf { ReportsEngine.saleCollectedAmount(it) }
                val pendingSum = creditSales.sumOf { ReportsEngine.salePendingCreditAmount(it) }
                val rows = creditSales.map { s ->
                    val paid = ReportsEngine.saleCollectedAmount(s)
                    val pending = ReportsEngine.salePendingCreditAmount(s)
                    listOf(
                        s.folio,
                        s.customerName,
                        ReportsEngine.formatDateTime(s.date, timeZone),
                        ReportsEngine.formatCurrency(s.total, cur),
                        ReportsEngine.formatCurrency(paid, cur),
                        ReportsEngine.formatCurrency(pending, cur),
                        s.sellerName
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Folio", "Cliente", "Fecha", "Total Venta", "Abonado", "Saldo Pend.", "Vendedor"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})",
                        "",
                        "",
                        ReportsEngine.formatCurrency(totalAmount, cur),
                        ReportsEngine.formatCurrency(paidSum, cur),
                        ReportsEngine.formatCurrency(pendingSum, cur),
                        ""
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Ventas a Crédito" to rows.size.toString(),
                        "Total Facturado" to ReportsEngine.formatCurrency(totalAmount, cur),
                        "Saldo por Cobrar" to ReportsEngine.formatCurrency(pendingSum, cur)
                    )
                )
            }

            "Compras Crédito" -> {
                val creditPurchases = fPurchases.filter { it.isCredit }
                val totalCredit = creditPurchases.sumOf { it.total }
                val rows = creditPurchases.map { p ->
                    listOf(
                        p.folio,
                        p.supplierName,
                        ReportsEngine.formatDateTime(p.date, timeZone),
                        "A Crédito",
                        ReportsEngine.formatCurrency(p.total, cur),
                        p.notes.ifBlank { "-" }
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Folio", "Proveedor", "Fecha", "Condición", "Total", "Notas"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "", ReportsEngine.formatCurrency(totalCredit, cur), ""
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Órdenes a Crédito" to rows.size.toString(),
                        "Total por Pagar" to ReportsEngine.formatCurrency(totalCredit, cur)
                    )
                )
            }

            "Ingreso Extra Crédito" -> {
                val items = fExtra.filter { it.type == MovementType.INGRESO && it.isCredit }
                val total = items.sumOf { it.amount }
                val rows = items.map { m ->
                    listOf(
                        ReportsEngine.formatDateTime(m.date, timeZone),
                        m.concept,
                        m.category,
                        "Crédito / Pend.",
                        ReportsEngine.formatCurrency(m.amount, cur),
                        m.notes.ifBlank { "-" }
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Fecha", "Concepto", "Categoría", "Estado", "Monto", "Notas"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf("TOTAL (${rows.size})", "", "", "", ReportsEngine.formatCurrency(total, cur), "") else null,
                    summaryStats = linkedMapOf(
                        "Registros" to rows.size.toString(),
                        "Total Ingresos Créd." to ReportsEngine.formatCurrency(total, cur)
                    )
                )
            }

            "Gasto Extra Crédito" -> {
                val items = fExtra.filter { it.type == MovementType.GASTO && it.isCredit }
                val total = items.sumOf { it.amount }
                val rows = items.map { m ->
                    listOf(
                        ReportsEngine.formatDateTime(m.date, timeZone),
                        m.concept,
                        m.category,
                        "Crédito / Pend.",
                        ReportsEngine.formatCurrency(m.amount, cur),
                        m.notes.ifBlank { "-" }
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Fecha", "Concepto", "Categoría", "Estado", "Monto", "Notas"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf("TOTAL (${rows.size})", "", "", "", ReportsEngine.formatCurrency(total, cur), "") else null,
                    summaryStats = linkedMapOf(
                        "Registros" to rows.size.toString(),
                        "Total Gastos Créd." to ReportsEngine.formatCurrency(total, cur)
                    )
                )
            }

            "Préstamos Activos" -> buildRentalsOrLoansReport(
                title = title,
                periodLabel = periodLabel,
                items = fRentalsLoans.filter { !it.isRental && !it.isReturnedOrPaid },
                isRental = false,
                cur = cur,
                nowMs = nowMs,
                timeZone = timeZone
            )

            "Rentas Activas" -> buildRentalsOrLoansReport(
                title = title,
                periodLabel = periodLabel,
                items = fRentalsLoans.filter { it.isRental && !it.isReturnedOrPaid },
                isRental = true,
                cur = cur,
                nowMs = nowMs,
                timeZone = timeZone
            )

            "Categoría Ingreso Extra" -> buildExtraCategoryReport(
                title = title,
                periodLabel = periodLabel,
                movements = fExtra.filter { it.type == MovementType.INGRESO },
                amountColHeader = "Total Ingresos",
                paidColHeader = "Contado",
                cur = cur
            )

            "Categoría Gasto Extra" -> buildExtraCategoryReport(
                title = title,
                periodLabel = periodLabel,
                movements = fExtra.filter { it.type == MovementType.GASTO },
                amountColHeader = "Total Gastos",
                paidColHeader = "Pagado",
                cur = cur
            )

            "Clientes" -> {
                val salesByClientId = confirmedSales.filter { it.customerId != null }.groupBy { it.customerId!! }
                val salesByClientName = confirmedSales.groupBy { PosSearchEngine.normalize(it.customerName) }
                val rows = dataset.clients.map { c ->
                    val matchedSales = salesByClientId[c.id]
                        ?: salesByClientName[PosSearchEngine.normalize(c.name)]
                        ?: emptyList()
                    val periodTotal = matchedSales.sumOf { it.total }
                    listOf(
                        c.name,
                        c.company.ifBlank { "-" },
                        c.phone.ifBlank { "-" },
                        matchedSales.size.toString(),
                        ReportsEngine.formatCurrency(periodTotal, cur),
                        ReportsEngine.formatCurrency(c.creditBalance, cur),
                        c.notes.ifBlank { "-" }
                    )
                }
                val totalCreditBal = dataset.clients.sumOf { it.creditBalance }
                val totalPeriodSales = rows.indices.sumOf { idx ->
                    val c = dataset.clients[idx]
                    (salesByClientId[c.id] ?: salesByClientName[PosSearchEngine.normalize(c.name)] ?: emptyList()).sumOf { it.total }
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Cliente", "Empresa", "Teléfono", "Compras Per.", "Total Período", "Saldo Crédito", "Notas"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "",
                        ReportsEngine.formatCurrency(totalPeriodSales, cur),
                        ReportsEngine.formatCurrency(totalCreditBal, cur),
                        ""
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Clientes Registrados" to dataset.clients.size.toString(),
                        "Cartera Crédito Total" to ReportsEngine.formatCurrency(totalCreditBal, cur)
                    )
                )
            }

            "Cliente Historial" -> {
                val clientCreditByName = dataset.clients.associate { PosSearchEngine.normalize(it.name) to it.creditBalance }
                val clientCreditById = dataset.clients.associate { it.id to it.creditBalance }
                val grouped = fSales.groupBy { sale ->
                    if (sale.customerId != null) "id:${sale.customerId}" else "nm:${PosSearchEngine.normalize(sale.customerName)}"
                }
                val rowsData = grouped.values.map { group ->
                    val sample = group.first()
                    val conf = group.filter { it.type == SaleType.VENTA && it.status != SaleStatus.CANCELADA }
                    val quotes = group.count { it.type == SaleType.COTIZACION && it.status != SaleStatus.CANCELADA }
                    val canceled = group.count { it.status == SaleStatus.CANCELADA }
                    val totalSpent = conf.sumOf { it.total }
                    val creditBal = sample.customerId?.let { clientCreditById[it] }
                        ?: clientCreditByName[PosSearchEngine.normalize(sample.customerName)]
                        ?: conf.sumOf { ReportsEngine.salePendingCreditAmount(it) }
                    val lastTs = group.maxOf { it.date }
                    Triple(totalSpent, sample.customerName, listOf(
                        sample.customerName,
                        conf.size.toString(),
                        ReportsEngine.formatCurrency(totalSpent, cur),
                        ReportsEngine.formatCurrency(creditBal, cur),
                        quotes.toString(),
                        canceled.toString(),
                        ReportsEngine.formatDateTime(lastTs, timeZone)
                    ))
                }.sortedByDescending { it.first }

                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Cliente", "Ventas Conf.", "Total Ventas", "Saldo Crédito", "Cotizaciones", "Canceladas", "Última Operación"),
                    rows = rowsData.map { it.third },
                    totalsRow = if (rowsData.isNotEmpty()) listOf(
                        "TOTAL (${rowsData.size} clientes)",
                        confirmedSales.size.toString(),
                        ReportsEngine.formatCurrency(summary.confirmedSalesTotal, cur),
                        "",
                        summary.quotesCount.toString(),
                        summary.canceledSalesCount.toString(),
                        ""
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Clientes con Actividad" to rowsData.size.toString(),
                        "Ventas Confirmadas" to ReportsEngine.formatCurrency(summary.confirmedSalesTotal, cur)
                    ),
                    chartBars = rowsData.take(5).map {
                        ChartBarItem(it.second.take(14), it.first, ReportsEngine.formatCurrency(it.first, cur))
                    }
                )
            }

            "Proveedores" -> {
                val purchasesBySupId = fPurchases.filter { it.supplierId != null }.groupBy { it.supplierId!! }
                val purchasesBySupName = fPurchases.groupBy { PosSearchEngine.normalize(it.supplierName) }
                var sumPeriod = 0.0
                var sumOrders = 0
                val rows = dataset.suppliers.map { sup ->
                    val matched = purchasesBySupId[sup.id]
                        ?: purchasesBySupName[PosSearchEngine.normalize(sup.name)]
                        ?: emptyList()
                    val spent = matched.sumOf { it.total }
                    sumPeriod += spent
                    sumOrders += matched.size
                    listOf(
                        sup.name,
                        sup.company.ifBlank { "-" },
                        sup.phone.ifBlank { "-" },
                        sup.paymentTerms,
                        matched.size.toString(),
                        ReportsEngine.formatCurrency(spent, cur),
                        sup.suppliedProducts.ifBlank { "-" }
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Proveedor", "Empresa", "Teléfono", "Condiciones", "Órdenes Per.", "Total Comprado", "Suministros"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "", sumOrders.toString(), ReportsEngine.formatCurrency(sumPeriod, cur), ""
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Proveedores" to dataset.suppliers.size.toString(),
                        "Compras Período" to ReportsEngine.formatCurrency(sumPeriod, cur)
                    )
                )
            }

            "Proveedor Historial" -> {
                val supTermsById = dataset.suppliers.associate { it.id to it.paymentTerms }
                val rows = fPurchases.sortedByDescending { it.date }.map { p ->
                    val terms = p.supplierId?.let { supTermsById[it] } ?: (if (p.isCredit) "Crédito" else "Contado")
                    listOf(
                        p.folio,
                        ReportsEngine.formatDateTime(p.date, timeZone),
                        p.supplierName,
                        terms,
                        if (p.isCredit) "A Crédito" else "Pagada",
                        ReportsEngine.formatCurrency(p.total, cur),
                        p.notes.ifBlank { "-" }
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Folio", "Fecha", "Proveedor", "Condiciones", "Estado", "Total", "Notas"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "", "", ReportsEngine.formatCurrency(summary.purchasesTotal, cur), ""
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Órdenes" to rows.size.toString(),
                        "Total Comprado" to ReportsEngine.formatCurrency(summary.purchasesTotal, cur),
                        "A Crédito" to ReportsEngine.formatCurrency(summary.purchasesCreditTotal, cur)
                    )
                )
            }

            "Empleados" -> {
                val salesBySeller = confirmedSales.groupBy { PosSearchEngine.normalize(it.sellerName) }
                var sumSalary = 0.0
                var sumSold = 0.0
                val rows = dataset.employees.map { emp ->
                    val empSales = salesBySeller[PosSearchEngine.normalize(emp.name)] ?: emptyList()
                    val soldTotal = empSales.sumOf { it.total }
                    sumSalary += emp.salary
                    sumSold += soldTotal
                    listOf(
                        emp.name,
                        emp.position,
                        emp.role.name,
                        ReportsEngine.formatCurrency(emp.salary, cur),
                        emp.phone.ifBlank { "-" },
                        empSales.size.toString(),
                        ReportsEngine.formatCurrency(soldTotal, cur)
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Nombre", "Puesto", "Rol", "Sueldo Base", "Teléfono", "Ventas Per.", "Total Vendido"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "",
                        ReportsEngine.formatCurrency(sumSalary, cur),
                        "",
                        "",
                        ReportsEngine.formatCurrency(sumSold, cur)
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Empleados" to dataset.employees.size.toString(),
                        "Nómina Base" to ReportsEngine.formatCurrency(sumSalary, cur)
                    )
                )
            }

            "Empleado Historial" -> {
                val names = linkedSetOf<String>()
                dataset.employees.forEach { names.add(it.name) }
                fSales.forEach { if (it.sellerName.isNotBlank()) names.add(it.sellerName) }
                fShifts.forEach { if (it.cashierName.isNotBlank()) names.add(it.cashierName) }

                val byNorm = linkedMapOf<String, String>()
                names.forEach { n ->
                    val k = PosSearchEngine.normalize(n)
                    if (!byNorm.containsKey(k)) byNorm[k] = n
                }

                val rowsData = byNorm.entries.mapNotNull { (normName, displayName) ->
                    val empConf = confirmedSales.filter { PosSearchEngine.normalize(it.sellerName) == normName }
                    val empCanc = fSales.count { PosSearchEngine.normalize(it.sellerName) == normName && it.status == SaleStatus.CANCELADA }
                    val empShifts = fShifts.filter { PosSearchEngine.normalize(it.cashierName) == normName }
                    if (empConf.isEmpty() && empCanc == 0 && empShifts.isEmpty()) return@mapNotNull null

                    val totalSold = empConf.sumOf { it.total }
                    val avgTicket = if (empConf.isNotEmpty()) totalSold / empConf.size else 0.0
                    val shiftDiff = empShifts.filter { !it.isOpen }.sumOf { it.difference }
                    Triple(
                        totalSold,
                        displayName,
                        listOf(
                            displayName,
                            empShifts.size.toString(),
                            empConf.size.toString(),
                            empCanc.toString(),
                            ReportsEngine.formatCurrency(totalSold, cur),
                            ReportsEngine.formatCurrency(avgTicket, cur),
                            ReportsEngine.formatCurrency(shiftDiff, cur)
                        )
                    )
                }.sortedByDescending { it.first }

                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Responsable", "Turnos", "Ventas Conf.", "Canceladas", "Total Vendido", "Ticket Prom.", "Dif. Cortes"),
                    rows = rowsData.map { it.third },
                    totalsRow = if (rowsData.isNotEmpty()) listOf(
                        "TOTAL (${rowsData.size})",
                        fShifts.size.toString(),
                        summary.confirmedSalesCount.toString(),
                        summary.canceledSalesCount.toString(),
                        ReportsEngine.formatCurrency(summary.confirmedSalesTotal, cur),
                        "",
                        ReportsEngine.formatCurrency(fShifts.filter { !it.isOpen }.sumOf { it.difference }, cur)
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Vendedores Activos" to rowsData.size.toString(),
                        "Turnos en Período" to fShifts.size.toString()
                    ),
                    chartBars = rowsData.take(5).map {
                        ChartBarItem(it.second.take(14), it.first, ReportsEngine.formatCurrency(it.first, cur))
                    }
                )
            }

            "Forma Pago" -> {
                val grouped = confirmedSales.groupBy { it.paymentMethod.trim().ifBlank { "Efectivo" } }
                val grandTotal = summary.confirmedSalesTotal
                val sorted = grouped.entries.sortedByDescending { (_, list) -> list.sumOf { it.total } }
                val rows = sorted.map { (method, list) ->
                    val amt = list.sumOf { it.total }
                    val collected = list.sumOf { ReportsEngine.saleCollectedAmount(it) }
                    val credit = list.sumOf { ReportsEngine.salePendingCreditAmount(it) }
                    val pct = if (grandTotal > 0.0) (amt / grandTotal) * 100.0 else 0.0
                    listOf(
                        method,
                        list.size.toString(),
                        ReportsEngine.formatCurrency(amt, cur),
                        ReportsEngine.formatCurrency(collected, cur),
                        ReportsEngine.formatCurrency(credit, cur),
                        String.format(Locale.US, "%.1f%%", pct)
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Forma de Pago", "Ventas", "Total Ventas", "Cobrado Real", "Pend. Crédito", "% Part."),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL",
                        summary.confirmedSalesCount.toString(),
                        ReportsEngine.formatCurrency(grandTotal, cur),
                        ReportsEngine.formatCurrency(summary.collectedSalesTotal, cur),
                        ReportsEngine.formatCurrency(summary.creditSalesTotal, cur),
                        "100.0%"
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Cobrado Real" to ReportsEngine.formatCurrency(summary.collectedSalesTotal, cur),
                        "Crédito Pend." to ReportsEngine.formatCurrency(summary.creditSalesTotal, cur)
                    ),
                    chartBars = sorted.map { (m, l) ->
                        val v = l.sumOf { it.total }
                        ChartBarItem(m.take(14), v, ReportsEngine.formatCurrency(v, cur))
                    }
                )
            }

            "Etiqueta Venta" -> {
                val grouped = confirmedSales.groupBy { it.notes.trim().ifBlank { "Sin etiqueta / General" } }
                val grandTotal = summary.confirmedSalesTotal
                val sorted = grouped.entries.sortedByDescending { (_, list) -> list.sumOf { it.total } }
                val rows = sorted.map { (tag, list) ->
                    val tot = list.sumOf { it.total }
                    val disc = list.sumOf { it.discount }
                    val pct = if (grandTotal > 0.0) (tot / grandTotal) * 100.0 else 0.0
                    listOf(
                        tag,
                        list.size.toString(),
                        ReportsEngine.formatCurrency(disc, cur),
                        ReportsEngine.formatCurrency(tot, cur),
                        String.format(Locale.US, "%.1f%%", pct)
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Etiqueta / Referencia", "Ventas", "Descuentos", "Total Vendido", "% Part."),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL",
                        summary.confirmedSalesCount.toString(),
                        ReportsEngine.formatCurrency(summary.confirmedSalesDiscounts, cur),
                        ReportsEngine.formatCurrency(grandTotal, cur),
                        "100.0%"
                    ) else null
                )
            }

            "Etiqueta Compra" -> {
                val grouped = fPurchases.groupBy { it.notes.trim().ifBlank { "Sin etiqueta / General" } }
                val grandTotal = summary.purchasesTotal
                val sorted = grouped.entries.sortedByDescending { (_, list) -> list.sumOf { it.total } }
                val rows = sorted.map { (tag, list) ->
                    val creditSum = list.filter { it.isCredit }.sumOf { it.total }
                    val paidSum = list.filter { !it.isCredit }.sumOf { it.total }
                    val tot = list.sumOf { it.total }
                    val pct = if (grandTotal > 0.0) (tot / grandTotal) * 100.0 else 0.0
                    listOf(
                        tag,
                        list.size.toString(),
                        ReportsEngine.formatCurrency(creditSum, cur),
                        ReportsEngine.formatCurrency(paidSum, cur),
                        ReportsEngine.formatCurrency(tot, cur),
                        String.format(Locale.US, "%.1f%%", pct)
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Etiqueta / Nota", "Órdenes", "A Crédito", "Pagadas", "Total Comprado", "% Part."),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL",
                        summary.purchasesCount.toString(),
                        ReportsEngine.formatCurrency(summary.purchasesCreditTotal, cur),
                        ReportsEngine.formatCurrency(summary.purchasesPaidTotal, cur),
                        ReportsEngine.formatCurrency(grandTotal, cur),
                        "100.0%"
                    ) else null
                )
            }

            "Cotización Reporte" -> {
                val quotes = fSales.filter { it.type == SaleType.COTIZACION }.sortedByDescending { it.date }
                val activeQuotes = quotes.filter { it.status != SaleStatus.CANCELADA }
                val rows = quotes.map { q ->
                    val linesCount = ReportsEngine.parseSaleLines(q, dataset.inventory).size
                    listOf(
                        q.folio,
                        ReportsEngine.formatDateTime(q.date, timeZone),
                        q.customerName,
                        linesCount.toString(),
                        ReportsEngine.formatCurrency(q.discount, cur),
                        ReportsEngine.formatCurrency(q.total, cur),
                        q.status.name
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Folio", "Fecha", "Cliente", "Partidas", "Descuento", "Total Cotizado", "Estado"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL ACTIVAS (${activeQuotes.size})",
                        "",
                        "",
                        "",
                        ReportsEngine.formatCurrency(activeQuotes.sumOf { it.discount }, cur),
                        ReportsEngine.formatCurrency(activeQuotes.sumOf { it.total }, cur),
                        "NO FACTURADO"
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Cotizaciones Pend." to activeQuotes.size.toString(),
                        "Monto Cotizado" to ReportsEngine.formatCurrency(activeQuotes.sumOf { it.total }, cur)
                    ),
                    limitationsNote = "Las cotizaciones no forman parte de los ingresos por ventas confirmadas."
                )
            }

            "Orden Compra Reporte" -> {
                val orders = fPurchases.sortedByDescending { it.date }
                val rows = orders.map { p ->
                    val parsedLines = ReportsEngine.parsePurchaseLines(p, dataset.inventory)
                    val partSummary = if (parsedLines.isNotEmpty()) {
                        "${parsedLines.size} (${parsedLines.joinToString(", ") { it.name }.take(28)})"
                    } else "Sin detalle JSON"
                    listOf(
                        p.folio,
                        ReportsEngine.formatDateTime(p.date, timeZone),
                        p.supplierName,
                        partSummary,
                        if (p.isCredit) "Crédito" else "Pagada",
                        ReportsEngine.formatCurrency(p.total, cur),
                        p.notes.ifBlank { "-" }
                    )
                }
                GeneratedReport(
                    title = title,
                    periodLabel = periodLabel,
                    headers = listOf("Folio", "Fecha", "Proveedor", "Partidas", "Condición", "Total", "Notas"),
                    rows = rows,
                    totalsRow = if (rows.isNotEmpty()) listOf(
                        "TOTAL (${rows.size})", "", "", "", "", ReportsEngine.formatCurrency(summary.purchasesTotal, cur), ""
                    ) else null,
                    summaryStats = linkedMapOf(
                        "Órdenes Totales" to rows.size.toString(),
                        "Contado" to ReportsEngine.formatCurrency(summary.purchasesPaidTotal, cur),
                        "Crédito" to ReportsEngine.formatCurrency(summary.purchasesCreditTotal, cur)
                    )
                )
            }

            else -> null
        }
    }

    private fun buildRentalsOrLoansReport(
        title: String,
        periodLabel: String,
        items: List<com.example.data.model.RentalLoan>,
        isRental: Boolean,
        cur: String,
        nowMs: Long,
        timeZone: TimeZone
    ): GeneratedReport {
        val sorted = items.sortedBy { it.dueDate }
        val totalAmount = sorted.sumOf { it.amount }
        val totalDeposit = sorted.sumOf { it.depositAmount }
        val overdueCount = sorted.count { it.dueDate < nowMs }

        val rows = sorted.map { rl ->
            val statusStr = when {
                rl.dueDate < nowMs -> "Vencido"
                rl.dueDate - nowMs <= 2L * 24 * 3600 * 1000 -> "Por vencer"
                else -> "Vigente"
            }
            listOf(
                rl.contactName,
                rl.itemOrReason,
                ReportsEngine.formatDateOnly(rl.startDate, timeZone),
                ReportsEngine.formatDateOnly(rl.dueDate, timeZone),
                ReportsEngine.formatCurrency(rl.depositAmount, cur),
                ReportsEngine.formatCurrency(rl.amount, cur),
                statusStr
            )
        }

        val headers = if (isRental) {
            listOf("Cliente", "Equipo / Renta", "Inicio", "Entrega", "Depósito", "Monto Renta", "Situación")
        } else {
            listOf("Contacto", "Concepto", "Inicio", "Vencimiento", "Garantía", "Monto Préstamo", "Situación")
        }

        return GeneratedReport(
            title = title,
            periodLabel = periodLabel,
            headers = headers,
            rows = rows,
            totalsRow = if (rows.isNotEmpty()) listOf(
                "TOTAL (${rows.size})",
                "",
                "",
                "",
                ReportsEngine.formatCurrency(totalDeposit, cur),
                ReportsEngine.formatCurrency(totalAmount, cur),
                "$overdueCount vencido(s)"
            ) else null,
            summaryStats = linkedMapOf(
                "Activos" to rows.size.toString(),
                "Vencidos" to overdueCount.toString(),
                "Monto Total" to ReportsEngine.formatCurrency(totalAmount, cur),
                "Depósitos" to ReportsEngine.formatCurrency(totalDeposit, cur)
            )
        )
    }

    private fun buildExtraCategoryReport(
        title: String,
        periodLabel: String,
        movements: List<com.example.data.model.ExtraMovement>,
        amountColHeader: String,
        paidColHeader: String,
        cur: String
    ): GeneratedReport {
        val grandTotal = movements.sumOf { it.amount }
        val totalCredit = movements.filter { it.isCredit }.sumOf { it.amount }
        val totalPaid = grandTotal - totalCredit

        val grouped = movements.groupBy { it.category.trim().ifBlank { "General" } }
            .entries
            .sortedByDescending { (_, list) -> list.sumOf { it.amount } }

        val rows = grouped.map { (cat, list) ->
            val catTotal = list.sumOf { it.amount }
            val catCredit = list.filter { it.isCredit }.sumOf { it.amount }
            val catPaid = catTotal - catCredit
            val pct = if (grandTotal > 0.0) (catTotal / grandTotal) * 100.0 else 0.0
            listOf(
                cat,
                list.size.toString(),
                ReportsEngine.formatCurrency(catCredit, cur),
                ReportsEngine.formatCurrency(catPaid, cur),
                ReportsEngine.formatCurrency(catTotal, cur),
                String.format(Locale.US, "%.1f%%", pct)
            )
        }

        return GeneratedReport(
            title = title,
            periodLabel = periodLabel,
            headers = listOf("Categoría", "Movimientos", "Crédito / Pend.", paidColHeader, amountColHeader, "% Total"),
            rows = rows,
            totalsRow = if (rows.isNotEmpty()) listOf(
                "TOTAL",
                movements.size.toString(),
                ReportsEngine.formatCurrency(totalCredit, cur),
                ReportsEngine.formatCurrency(totalPaid, cur),
                ReportsEngine.formatCurrency(grandTotal, cur),
                "100.0%"
            ) else null,
            summaryStats = linkedMapOf(
                "Categorías" to rows.size.toString(),
                amountColHeader to ReportsEngine.formatCurrency(grandTotal, cur)
            ),
            chartBars = grouped.take(6).map { (cat, list) ->
                val v = list.sumOf { it.amount }
                ChartBarItem(cat.take(14), v, ReportsEngine.formatCurrency(v, cur))
            }
        )
    }
}
