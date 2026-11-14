package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CajaMaestraDatabase
import com.example.data.model.*
import com.example.data.prefs.SettingsStorage
import com.example.data.repository.BusinessOperationsRepository
import com.example.data.repository.PurchaseLineInput
import com.example.data.repository.RepoPurchaseResult
import com.example.data.repository.RepoSaleResult
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DateFilterType
import com.example.ui.viewmodel.InitialSecuritySetupResult
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CostCoverage
import com.example.util.ReportExporter
import com.example.util.ReportsDataset
import com.example.util.ReportsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportsPhase4Test {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private val mexTz: TimeZone = TimeZone.getTimeZone("America/Mexico_City")

    @Before
    fun setUpClean() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            SettingsStorage.getInstance(app).clearAllForTest()
            clearAllTablesOnSingletonDb()
            app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            SettingsStorage.getInstance(app).clearAllForTest()
            clearAllTablesOnSingletonDb()
        }
    }

    private suspend fun clearAllTablesOnSingletonDb() {
        val db = CajaMaestraDatabase.getDatabase(app, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
        val dao = db.dao()
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
    }

    private fun localTimestamp(
        year: Int,
        monthZeroBased: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        ms: Int = 0,
        tz: TimeZone = mexTz
    ): Long {
        return Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis
    }

    private val all41ReportTitles = listOf(
        "Transacciones Por Día",
        "Resumen Transacciones",
        "Ventas Crédito",
        "Compras Crédito",
        "Ingreso Extra Crédito",
        "Gasto Extra Crédito",
        "Préstamos Activos",
        "Rentas Activas",
        "Producto Ventas",
        "Producto Compras",
        "Materia Prima Compras",
        "Manufactura Ventas",
        "Servicio Ventas",
        "Productos Sin Ventas",
        "Servicios Sin Ventas",
        "Manufactura Sin Ventas",
        "Categoría Ingreso Extra",
        "Categoría Gasto Extra",
        "Producto Análisis",
        "Materia Prima Análisis",
        "Servicio Análisis",
        "Manufactura Análisis",
        "Categoría Producto Ventas",
        "Categoría Producto Compras",
        "Clientes",
        "Cliente Historial",
        "Proveedores",
        "Proveedor Historial",
        "Empleados",
        "Empleado Historial",
        "Merma Productos",
        "Merma Materia Prima",
        "Forma Pago",
        "Etiqueta Venta",
        "Etiqueta Compra",
        "Cotización Reporte",
        "Orden Compra Reporte",
        "Producto Inventario Resumen",
        "Materia Prima Inventario Resumen",
        "Manufactura Producción",
        "Modificación Inventario"
    )

    @Test
    fun test01_dateFilterHoyExactLocalBoundaries() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 14, 30, 0)
        val todayStart = localTimestamp(2026, Calendar.MAY, 15, 0, 0, 0, 0)
        val todayEnd = localTimestamp(2026, Calendar.MAY, 15, 23, 59, 59, 999)
        val yesterdayLate = localTimestamp(2026, Calendar.MAY, 14, 23, 59, 59, 999)
        val tomorrowEarly = localTimestamp(2026, Calendar.MAY, 16, 0, 0, 0, 0)

        val dataset = ReportsDataset(
            sales = listOf(
                Sale(id = 1, folio = "F-1", total = 100.0, subtotal = 100.0, date = todayStart),
                Sale(id = 2, folio = "F-2", total = 250.0, subtotal = 250.0, date = todayEnd),
                Sale(id = 3, folio = "F-3", total = 999.0, subtotal = 999.0, date = yesterdayLate),
                Sale(id = 4, folio = "F-4", total = 888.0, subtotal = 888.0, date = tomorrowEarly)
            )
        )

        val summary = ReportsEngine.computeFinancialSummary(dataset, DateFilterType.HOY, nowMs, mexTz)
        assertEquals(2, summary.confirmedSalesCount)
        assertEquals(350.0, summary.confirmedSalesTotal, 0.001)
    }

    @Test
    fun test02_dateFilterUltimaSemanaRollingSevenDays() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 12, 0, 0)
        val dayMinus6Start = localTimestamp(2026, Calendar.MAY, 9, 0, 0, 0, 0)
        val dayMinus7Late = localTimestamp(2026, Calendar.MAY, 8, 23, 59, 59, 999)

        val dataset = ReportsDataset(
            purchases = listOf(
                Purchase(id = 1, folio = "OC-1", total = 500.0, date = dayMinus6Start),
                Purchase(id = 2, folio = "OC-2", total = 300.0, date = nowMs),
                Purchase(id = 3, folio = "OC-3", total = 999.0, date = dayMinus7Late)
            )
        )

        val summary = ReportsEngine.computeFinancialSummary(dataset, DateFilterType.ULTIMA_SEMANA, nowMs, mexTz)
        assertEquals(2, summary.purchasesCount)
        assertEquals(800.0, summary.purchasesTotal, 0.001)
    }

    @Test
    fun test03_dateFilterEsteMesCalendarMonthBounds() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 10, 0, 0)
        val mayFirst = localTimestamp(2026, Calendar.MAY, 1, 0, 0, 0, 0)
        val aprilLast = localTimestamp(2026, Calendar.APRIL, 30, 23, 59, 59, 999)

        val dataset = ReportsDataset(
            extraMovements = listOf(
                ExtraMovement(id = 1, type = MovementType.GASTO, concept = "Luz Mayo", category = "Servicios", amount = 450.0, date = mayFirst),
                ExtraMovement(id = 2, type = MovementType.GASTO, concept = "Luz Abril", category = "Servicios", amount = 600.0, date = aprilLast)
            )
        )

        val summary = ReportsEngine.computeFinancialSummary(dataset, DateFilterType.ESTE_MES, nowMs, mexTz)
        assertEquals(450.0, summary.extraExpensesTotal, 0.001)
    }

    @Test
    fun test04_dateFilterTodosIncludesFullHistory() {
        val y2024 = localTimestamp(2024, Calendar.JANUARY, 10, 9, 0, 0)
        val y2026 = localTimestamp(2026, Calendar.MAY, 15, 9, 0, 0)

        val dataset = ReportsDataset(
            sales = listOf(
                Sale(id = 1, folio = "F-1", total = 120.0, subtotal = 120.0, date = y2024),
                Sale(id = 2, folio = "F-2", total = 280.0, subtotal = 280.0, date = y2026)
            )
        )

        val summary = ReportsEngine.computeFinancialSummary(dataset, DateFilterType.TODOS, y2026, mexTz)
        assertEquals(2, summary.confirmedSalesCount)
        assertEquals(400.0, summary.confirmedSalesTotal, 0.001)
    }

    @Test
    fun test05_confirmedSalesVsQuotesVsCanceledAndCredit() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 15, 0, 0)
        val dataset = ReportsDataset(
            sales = listOf(
                Sale(id = 1, folio = "F-101", type = SaleType.VENTA, status = SaleStatus.PAGADA, subtotal = 500.0, total = 500.0, paidAmount = 500.0, paymentMethod = "Efectivo", date = nowMs),
                Sale(id = 2, folio = "F-102", type = SaleType.VENTA, status = SaleStatus.CREDITO, subtotal = 300.0, total = 300.0, paidAmount = 0.0, paymentMethod = "Crédito", date = nowMs),
                Sale(id = 3, folio = "F-103", type = SaleType.VENTA, status = SaleStatus.CANCELADA, subtotal = 900.0, total = 900.0, date = nowMs),
                Sale(id = 4, folio = "COT-1", type = SaleType.COTIZACION, status = SaleStatus.PENDIENTE, subtotal = 1200.0, total = 1200.0, date = nowMs)
            )
        )

        val summary = ReportsEngine.computeFinancialSummary(dataset, DateFilterType.HOY, nowMs, mexTz)
        assertEquals(2, summary.confirmedSalesCount)
        assertEquals(800.0, summary.confirmedSalesTotal, 0.001)
        assertEquals(500.0, summary.collectedSalesTotal, 0.001)
        assertEquals(300.0, summary.creditSalesTotal, 0.001)
        assertEquals(1, summary.quotesCount)
        assertEquals(1200.0, summary.quotesPendingTotal, 0.001)
        assertEquals(1, summary.canceledSalesCount)
        assertEquals(900.0, summary.canceledSalesTotal, 0.001)
    }

    @Test
    fun test06_cashDrawerMovementsSeparatedFromAccountingIncome() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 16, 0, 0)
        val dataset = ReportsDataset(
            sales = listOf(
                Sale(id = 1, folio = "F-200", type = SaleType.VENTA, status = SaleStatus.PAGADA, subtotal = 400.0, total = 400.0, paidAmount = 400.0, date = nowMs, itemsJson = """[{"itemId":1,"name":"Café","quantity":2,"unitPrice":200.0,"costPrice":100.0,"unit":"Pza","type":"PRODUCTO"}]""")
            ),
            cashShiftMovements = listOf(
                CashShiftMovement(id = 1, shiftId = 1, type = CashShiftMovementType.ENTRADA, amount = 1000.0, reason = "Cambio en efectivo", performedBy = "Cajero", date = nowMs),
                CashShiftMovement(id = 2, shiftId = 1, type = CashShiftMovementType.RETIRO, amount = 500.0, reason = "Corte parcial", performedBy = "Cajero", date = nowMs)
            )
        )

        val summary = ReportsEngine.computeFinancialSummary(dataset, DateFilterType.HOY, nowMs, mexTz)
        assertEquals(400.0, summary.confirmedSalesTotal, 0.001)
        assertEquals(0.0, summary.extraIncomeTotal, 0.001)
        assertEquals(1000.0, summary.cashDrawerEntriesTotal, 0.001)
        assertEquals(500.0, summary.cashDrawerWithdrawalsTotal, 0.001)
        assertEquals(200.0, summary.grossProfit!!, 0.001)
        assertEquals(200.0, summary.netProfit!!, 0.001)
    }

    @Test
    fun test07_purchasesBySupplierAndSupplierHistoryReport() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 10, 0, 0)
        val oldMs = localTimestamp(2026, Calendar.MARCH, 1, 10, 0, 0)
        val s1 = Supplier(id = 10, name = "Café Sur", company = "CS SA", phone = "555-111", paymentTerms = "Crédito 15 días", suppliedProducts = "Café")
        val s2 = Supplier(id = 20, name = "Empaques Norte", company = "EN SA", phone = "555-222", paymentTerms = "Contado", suppliedProducts = "Cajas")
        val dataset = ReportsDataset(
            suppliers = listOf(s1, s2),
            purchases = listOf(
                Purchase(id = 1, folio = "OC-01", supplierId = 10, supplierName = "Café Sur", total = 1500.0, isCredit = true, date = nowMs),
                Purchase(id = 2, folio = "OC-02", supplierId = 20, supplierName = "Empaques Norte", total = 700.0, isCredit = false, date = oldMs)
            )
        )

        val repToday = ReportsEngine.buildReport("Proveedor Historial", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, repToday.rows.size)
        assertEquals("OC-01", repToday.rows[0][0])
        assertEquals("Café Sur", repToday.rows[0][2])
        assertTrue(repToday.totalsRow!!.any { it.contains("1,500.00") })

        val supDir = ReportsEngine.buildReport("Proveedores", DateFilterType.ESTE_MES, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(2, supDir.rows.size)
        assertEquals("1", supDir.rows[0][4]) // 1 order in May for s1
        assertEquals("0", supDir.rows[1][4]) // 0 orders in May for s2
    }

    @Test
    fun test08_salesByProductFromItemsJsonNeverUsesFolioAsProductName() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 11, 0, 0)
        val inv = listOf(
            InventoryItem(id = 1, name = "Café Espresso 500g", code = "SKU-001", costPrice = 80.0, salePrice = 150.0, stock = 20.0, category = "Bebidas", type = ItemType.PRODUCTO),
            InventoryItem(id = 2, name = "Kit Barista", code = "KIT-01", costPrice = 300.0, salePrice = 600.0, stock = 5.0, category = "Combos", type = ItemType.MANUFACTURA),
            InventoryItem(id = 3, name = "Calibración Molino", code = "SRV-01", costPrice = 50.0, salePrice = 250.0, stock = 99.0, category = "Servicios", type = ItemType.SERVICIO)
        )
        val saleJson = """[
            {"itemId":1,"name":"Café Espresso 500g","quantity":3,"unitPrice":150.0,"costPrice":80.0,"unit":"Pza","type":"PRODUCTO"},
            {"itemId":2,"name":"Kit Barista","quantity":1,"unitPrice":600.0,"costPrice":300.0,"unit":"Kit","type":"MANUFACTURA"},
            {"itemId":3,"name":"Calibración Molino","quantity":2,"unitPrice":250.0,"costPrice":50.0,"unit":"Srv","type":"SERVICIO"}
        ]"""
        val dataset = ReportsDataset(
            inventory = inv,
            sales = listOf(
                Sale(id = 1, folio = "F-99001", type = SaleType.VENTA, status = SaleStatus.PAGADA, subtotal = 1550.0, total = 1550.0, date = nowMs, itemsJson = saleJson)
            )
        )

        val prodRep = ReportsEngine.buildReport("Producto Ventas", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, prodRep.rows.size)
        assertEquals("SKU-001", prodRep.rows[0][0])
        assertEquals("Café Espresso 500g", prodRep.rows[0][1])
        assertFalse(prodRep.rows[0].any { it.contains("F-99001") })
        assertTrue(prodRep.rows[0][4].contains("450.00"))
        assertTrue(prodRep.rows[0][5].contains("240.00"))
        assertTrue(prodRep.rows[0][6].contains("210.00"))

        val mfgRep = ReportsEngine.buildReport("Manufactura Ventas", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, mfgRep.rows.size)
        assertEquals("Kit Barista", mfgRep.rows[0][1])

        val srvRep = ReportsEngine.buildReport("Servicio Ventas", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, srvRep.rows.size)
        assertEquals("Calibración Molino", srvRep.rows[0][1])
        assertTrue(srvRep.rows[0][6].contains("400.00")) // 500 rev - 100 cost
    }

    @Test
    fun test09_extraExpensesAndIncomeByCategory() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 11, 0, 0)
        val dataset = ReportsDataset(
            extraMovements = listOf(
                ExtraMovement(id = 1, type = MovementType.GASTO, concept = "Luz", category = "Servicios", amount = 300.0, isCredit = false, date = nowMs),
                ExtraMovement(id = 2, type = MovementType.GASTO, concept = "Internet", category = "Servicios", amount = 500.0, isCredit = true, date = nowMs),
                ExtraMovement(id = 3, type = MovementType.GASTO, concept = "Renta Local", category = "Alquiler", amount = 1200.0, isCredit = false, date = nowMs),
                ExtraMovement(id = 4, type = MovementType.INGRESO, concept = "Recargas", category = "Comisiones", amount = 250.0, isCredit = false, date = nowMs)
            )
        )

        val expCat = ReportsEngine.buildReport("Categoría Gasto Extra", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(2, expCat.rows.size)
        assertEquals("Alquiler", expCat.rows[0][0]) // sorted desc by amount
        assertEquals("Servicios", expCat.rows[1][0])
        assertEquals("2", expCat.rows[1][1])
        assertTrue(expCat.rows[1][2].contains("500.00")) // credit
        assertTrue(expCat.rows[1][4].contains("800.00")) // total

        val incCredit = ReportsEngine.buildReport("Gasto Extra Crédito", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, incCredit.rows.size)
        assertEquals("Internet", incCredit.rows[0][1])
    }

    @Test
    fun test10_lowStockAndOutOfStockInventoryReports() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 11, 0, 0)
        val dataset = ReportsDataset(
            inventory = listOf(
                InventoryItem(id = 1, name = "Agotado A", code = "A1", costPrice = 10.0, salePrice = 20.0, stock = 0.0, minStock = 5.0, category = "General", type = ItemType.PRODUCTO),
                InventoryItem(id = 2, name = "Bajo B", code = "B1", costPrice = 20.0, salePrice = 40.0, stock = 3.0, minStock = 5.0, category = "General", type = ItemType.PRODUCTO),
                InventoryItem(id = 3, name = "Sano C", code = "C1", costPrice = 50.0, salePrice = 90.0, stock = 10.0, minStock = 5.0, category = "General", type = ItemType.PRODUCTO),
                InventoryItem(id = 4, name = "Insumo Azúcar", code = "MP1", costPrice = 15.0, salePrice = 25.0, stock = 2.0, minStock = 5.0, category = "Materia Prima", type = ItemType.PRODUCTO)
            )
        )

        val invSummary = ReportsEngine.buildReport("Producto Inventario Resumen", DateFilterType.TODOS, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(4, invSummary.rows.size)
        assertEquals("Agotado", invSummary.rows[0][8])
        assertEquals("Stock Bajo", invSummary.rows[1][8])
        assertEquals("Saludable", invSummary.rows[2][8])
        assertEquals("1", invSummary.summaryStats["Agotados"])
        assertEquals("2", invSummary.summaryStats["Stock Bajo"])
        // Valuation = 0*10 + 3*20 + 10*50 + 2*15 = 590.00
        assertEquals("$590.00", invSummary.summaryStats["Valor Costo"])
    }

    @Test
    fun test11_topSellingAndZeroSalesItems() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 11, 0, 0)
        val inv = listOf(
            InventoryItem(id = 1, name = "Estrella", code = "P1", costPrice = 10.0, salePrice = 30.0, stock = 50.0, type = ItemType.PRODUCTO),
            InventoryItem(id = 2, name = "Dormido", code = "P2", costPrice = 15.0, salePrice = 40.0, stock = 8.0, type = ItemType.PRODUCTO),
            InventoryItem(id = 3, name = "Servicio Inactivo", code = "S1", costPrice = 0.0, salePrice = 100.0, stock = 99.0, type = ItemType.SERVICIO),
            InventoryItem(id = 4, name = "Combo Inactivo", code = "M1", costPrice = 50.0, salePrice = 120.0, stock = 4.0, type = ItemType.MANUFACTURA)
        )
        val dataset = ReportsDataset(
            inventory = inv,
            sales = listOf(
                Sale(id = 1, folio = "F-1", total = 300.0, subtotal = 300.0, date = nowMs, itemsJson = """[{"itemId":1,"name":"Estrella","quantity":10,"unitPrice":30.0,"costPrice":10.0,"type":"PRODUCTO"}]""")
            )
        )

        val noSalesProd = ReportsEngine.buildReport("Productos Sin Ventas", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, noSalesProd.rows.size)
        assertEquals("Dormido", noSalesProd.rows[0][1])

        val noSalesSrv = ReportsEngine.buildReport("Servicios Sin Ventas", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, noSalesSrv.rows.size)
        assertEquals("Servicio Inactivo", noSalesSrv.rows[0][1])

        val noSalesMfg = ReportsEngine.buildReport("Manufactura Sin Ventas", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, noSalesMfg.rows.size)
        assertEquals("Combo Inactivo", noSalesMfg.rows[0][1])

        val analysis = ReportsEngine.buildReport("Producto Análisis", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals("Estrella", analysis.rows[0][1])
        assertEquals("Alta", analysis.rows[0][7])
        assertEquals("Sin Movimiento", analysis.rows[1][7])
    }

    @Test
    fun test12_activeLoansAndRentalsWithOverdueStatus() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 12, 0, 0)
        val overdueDue = localTimestamp(2026, Calendar.MAY, 14, 12, 0, 0)
        val futureDue = localTimestamp(2026, Calendar.MAY, 25, 12, 0, 0)
        val dataset = ReportsDataset(
            rentalsLoans = listOf(
                RentalLoan(id = 1, isRental = false, contactName = "Luis", itemOrReason = "Préstamo Caja", amount = 500.0, startDate = nowMs - 3600000L, dueDate = overdueDue, isReturnedOrPaid = false),
                RentalLoan(id = 2, isRental = false, contactName = "Ana", itemOrReason = "Préstamo Pagado", amount = 200.0, startDate = nowMs, dueDate = futureDue, isReturnedOrPaid = true),
                RentalLoan(id = 3, isRental = true, contactName = "Cafe X", itemOrReason = "Impresora 58mm", amount = 350.0, depositAmount = 500.0, startDate = nowMs, dueDate = futureDue, isReturnedOrPaid = false)
            )
        )

        val loans = ReportsEngine.buildReport("Préstamos Activos", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, loans.rows.size)
        assertEquals("Luis", loans.rows[0][0])
        assertEquals("Vencido", loans.rows[0][6])

        val rentals = ReportsEngine.buildReport("Rentas Activas", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, rentals.rows.size)
        assertEquals("Cafe X", rentals.rows[0][0])
        assertEquals("Vigente", rentals.rows[0][6])
    }

    @Test
    fun test13_profitCalculationWithCompleteHistoricalCostVsMissingCost() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 12, 0, 0)
        val catalogItem = InventoryItem(id = 1, name = "Producto X", code = "X1", costPrice = 40.0, salePrice = 100.0, stock = 10.0)

        // Case A: Complete historical cost -> exact gross & net profit (even with purchases of $5,000 unrelated to COGS!)
        val completeDataset = ReportsDataset(
            inventory = listOf(catalogItem),
            purchases = listOf(Purchase(id = 1, folio = "OC-1", total = 5000.0, date = nowMs)),
            extraMovements = listOf(
                ExtraMovement(id = 1, type = MovementType.INGRESO, concept = "Comisión", category = "Varios", amount = 50.0, date = nowMs),
                ExtraMovement(id = 2, type = MovementType.GASTO, concept = "Limpieza", category = "Insumos", amount = 120.0, date = nowMs)
            ),
            sales = listOf(
                Sale(
                    id = 1, folio = "F-1", subtotal = 300.0, discount = 20.0, total = 280.0, paidAmount = 280.0, date = nowMs,
                    itemsJson = """[{"itemId":1,"name":"Producto X","quantity":3,"unitPrice":100.0,"costPrice":40.0,"type":"PRODUCTO"}]"""
                )
            )
        )
        val completeSummary = ReportsEngine.computeFinancialSummary(completeDataset, DateFilterType.HOY, nowMs, mexTz)
        assertEquals(CostCoverage.COMPLETE, completeSummary.costCoverage)
        assertEquals(120.0, completeSummary.historicalCogsTotal, 0.001)
        assertEquals(160.0, completeSummary.grossProfit!!, 0.001) // 280 net sales - 120 historical COGS
        assertEquals(90.0, completeSummary.netProfit!!, 0.001) // 160 + 50 - 120

        // Case B: Missing historical cost (like legacy seed JSON without costPrice) -> MUST NOT fake profit using catalog costPrice=40 or purchases=5000!
        val missingCostDataset = ReportsDataset(
            inventory = listOf(catalogItem),
            purchases = listOf(Purchase(id = 1, folio = "OC-1", total = 5000.0, date = nowMs)),
            sales = listOf(
                Sale(
                    id = 1, folio = "F-LEGACY", subtotal = 300.0, total = 300.0, paidAmount = 300.0, date = nowMs,
                    itemsJson = """[{"name":"Producto X","quantity":3,"unitPrice":100.0}]"""
                )
            )
        )
        val missingSummary = ReportsEngine.computeFinancialSummary(missingCostDataset, DateFilterType.HOY, nowMs, mexTz)
        assertEquals(CostCoverage.UNAVAILABLE, missingSummary.costCoverage)
        assertNull(missingSummary.grossProfit)
        assertNull(missingSummary.netProfit)
        assertNotNull(missingSummary.profitLimitationNote)
    }

    @Test
    fun test14_all41ReportTilesExecuteWithoutFallbackOrCrash() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 13, 0, 0)
        val dataset = ReportsDataset(
            inventory = listOf(
                InventoryItem(id = 1, name = "Café Molido", code = "C-1", costPrice = 60.0, salePrice = 120.0, stock = 10.0, minStock = 5.0, category = "Bebidas", type = ItemType.PRODUCTO),
                InventoryItem(id = 2, name = "Bolsa Kraft", code = "MP-1", costPrice = 5.0, salePrice = 10.0, stock = 20.0, minStock = 10.0, category = "Materia Prima", type = ItemType.PRODUCTO),
                InventoryItem(id = 3, name = "Combo Desayuno", code = "MAN-1", costPrice = 80.0, salePrice = 180.0, stock = 4.0, minStock = 2.0, category = "Combos", type = ItemType.MANUFACTURA),
                InventoryItem(id = 4, name = "Instalación", code = "SRV-1", costPrice = 20.0, salePrice = 300.0, stock = 99.0, minStock = 0.0, category = "Soporte", type = ItemType.SERVICIO),
                InventoryItem(id = 5, name = "Bolsa Rota", code = "W-MP", costPrice = 5.0, salePrice = 0.0, stock = 3.0, minStock = 0.0, category = "Materia Prima", type = ItemType.PRODUCTO, isWaste = true),
                InventoryItem(id = 6, name = "Taza Rota", code = "W-P", costPrice = 30.0, salePrice = 0.0, stock = 2.0, minStock = 0.0, category = "Mermas", type = ItemType.PRODUCTO, isWaste = true)
            ),
            clients = listOf(Client(id = 1, name = "Cliente A", creditBalance = 120.0)),
            suppliers = listOf(Supplier(id = 1, name = "Proveedor A", suppliedProducts = "Bolsas")),
            employees = listOf(Employee(id = 1, name = "Carlos", role = UserRole.DUENO, salary = 15000.0)),
            purchases = listOf(
                Purchase(id = 1, folio = "OC-1", supplierId = 1, supplierName = "Proveedor A", total = 100.0, isCredit = true, notes = "Urgente", date = nowMs,
                    itemsJson = """[{"itemId":2,"code":"MP-1","name":"Bolsa Kraft","quantity":20,"unitPrice":5.0,"subtotal":100.0,"unit":"Pza","type":"PRODUCTO"}]""")
            ),
            sales = listOf(
                Sale(id = 1, folio = "F-1", type = SaleType.VENTA, status = SaleStatus.PAGADA, customerId = 1, customerName = "Cliente A", subtotal = 420.0, total = 420.0, paidAmount = 420.0, paymentMethod = "Efectivo", sellerName = "Carlos", notes = "Mayoreo", date = nowMs,
                    itemsJson = """[{"itemId":1,"name":"Café Molido","quantity":1,"unitPrice":120.0,"costPrice":60.0,"type":"PRODUCTO"},{"itemId":4,"name":"Instalación","quantity":1,"unitPrice":300.0,"costPrice":20.0,"type":"SERVICIO"}]"""),
                Sale(id = 2, folio = "COT-1", type = SaleType.COTIZACION, status = SaleStatus.PENDIENTE, customerId = 1, customerName = "Cliente A", subtotal = 180.0, total = 180.0, date = nowMs,
                    itemsJson = """[{"itemId":3,"name":"Combo Desayuno","quantity":1,"unitPrice":180.0,"costPrice":80.0,"type":"MANUFACTURA"}]""")
            ),
            extraMovements = listOf(
                ExtraMovement(id = 1, type = MovementType.INGRESO, concept = "Comisión", category = "Servicios", amount = 50.0, isCredit = true, date = nowMs),
                ExtraMovement(id = 2, type = MovementType.GASTO, concept = "Flete", category = "Envíos", amount = 40.0, isCredit = true, date = nowMs)
            ),
            rentalsLoans = listOf(
                RentalLoan(id = 1, isRental = true, contactName = "Cliente A", itemOrReason = "Impresora", amount = 200.0, startDate = nowMs),
                RentalLoan(id = 2, isRental = false, contactName = "Pedro", itemOrReason = "Adelanto", amount = 300.0, startDate = nowMs)
            ),
            cashShifts = listOf(CashRegisterShift(id = 1, cashierName = "Carlos", openedAt = nowMs)),
            cashShiftMovements = listOf(CashShiftMovement(id = 1, shiftId = 1, type = CashShiftMovementType.ENTRADA, amount = 500.0, reason = "Fondo", performedBy = "Carlos", date = nowMs))
        )

        assertEquals(41, all41ReportTitles.size)
        for (title in all41ReportTitles) {
            val report = ReportsEngine.buildReport(title, DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
            assertEquals("Report title mismatch", title, report.title)
            assertTrue("Report $title should have >= 4 columns", report.headers.size >= 4)
            assertFalse("Report $title must never produce fallback message", report.limitationsNote?.contains("sin datos disponibles") == true)
            for (row in report.rows) {
                assertEquals("Row column count must match headers for $title", report.headers.size, row.size)
            }
            if (report.totalsRow != null) {
                assertEquals("Totals row column count must match headers for $title", report.headers.size, report.totalsRow!!.size)
            }
        }
    }

    @Test
    fun test15_reportsWithMoreThan3ColumnsRenderAndExportAllColumns() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 10, 0, 0)
        val dataset = ReportsDataset(
            sales = listOf(
                Sale(id = 1, folio = "F-55", subtotal = 200.0, total = 200.0, paidAmount = 200.0, date = nowMs,
                    itemsJson = """[{"itemId":1,"name":"Termo","quantity":2,"unitPrice":100.0,"costPrice":60.0,"unit":"Pza","type":"PRODUCTO"}]""")
            ),
            inventory = listOf(
                InventoryItem(id = 1, name = "Termo", code = "TR-55", costPrice = 60.0, salePrice = 100.0, stock = 12.0)
            )
        )

        val report = ReportsEngine.buildReport("Producto Ventas", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(8, report.headers.size)
        // Verify columns 4, 5, 6, 7 exist and contain real amounts/dates
        val row = report.rows.first()
        assertTrue(row[4].contains("200.00"))
        assertTrue(row[5].contains("120.00"))
        assertTrue(row[6].contains("80.00"))
        assertEquals("15/05/2026", row[7])
    }

    @Test
    fun test16_csvExportUtf8BomEscapingTotalsAndPeriod() {
        val headers = listOf("Código", "Producto \"Especial\"", "Cant.", "Total")
        val rows = listOf(listOf("P-1", "Café, \"Altura\"", "2", "$290.00"))
        val totals = listOf("TOTAL", "", "2", "$290.00")

        val fileRes = ReportExporter.writeCsvFile(
            context = app,
            reportTitle = "Producto Ventas",
            headers = headers,
            rows = rows,
            periodLabel = "Hoy",
            totalsRow = totals,
            summaryStats = mapOf("Unidades" to "2")
        )
        assertTrue(fileRes.isSuccess)
        val file = fileRes.getOrThrow()
        assertTrue(file.exists())

        val rawBytes = file.readBytes()
        assertEquals(0xEF.toByte(), rawBytes[0])
        assertEquals(0xBB.toByte(), rawBytes[1])
        assertEquals(0xBF.toByte(), rawBytes[2])

        val text = String(rawBytes.copyOfRange(3, rawBytes.size), Charsets.UTF_8)
        assertTrue(text.contains("\"Producto \"\"Especial\"\"\""))
        assertTrue(text.contains("\"Café, \"\"Altura\"\"\""))
        assertTrue(text.contains("\"TOTAL\",\"\",\"2\",\"$290.00\""))
        assertTrue(text.contains("\"Período\",\"Hoy\""))
    }

    @Test
    fun test17_pdfHtmlGenerationEscapingStatsAndTotals() {
        val html = ReportExporter.buildHtmlDocument(
            title = "Resumen <Financiero> & Ventas",
            businessName = "Tienda A & B \"Centro\"",
            headers = listOf("Col1", "Col2", "Col3", "Col4"),
            rows = listOf(listOf("A < B", "Valor & 1", "3", "$100.00")),
            summaryStats = mapOf("Ventas" to "$100.00"),
            periodLabel = "Hoy",
            totalsRow = listOf("TOTAL", "", "3", "$100.00"),
            limitationsNote = "Nota <importante>"
        )

        assertFalse(html.contains("<Financiero>"))
        assertTrue(html.contains("Resumen &lt;Financiero&gt; &amp; Ventas"))
        assertTrue(html.contains("Tienda A &amp; B &quot;Centro&quot;"))
        assertTrue(html.contains("Nota &lt;importante&gt;"))
        assertTrue(html.contains("<tfoot>"))
        assertTrue(html.contains("Col4"))
        assertTrue(html.contains("Período: Hoy"))
    }

    @Test
    fun test18_combinedTransactionFlowUpdatesReportsFromRoomEndToEnd() {
        val db = CajaMaestraDatabase.getDatabase(app, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
        val dao = db.dao()
        var currentSettings = BusinessSettings(currentRole = UserRole.DUENO, currentUserName = "Jefe")
        val repo = BusinessOperationsRepository(db) { currentSettings }

        runBlocking {
            val itemId = dao.insertItem(
                InventoryItem(name = "Grano Oro 1kg", code = "GO-1", costPrice = 100.0, salePrice = 220.0, stock = 10.0, minStock = 3.0, category = "Alimentos", type = ItemType.PRODUCTO)
            )
            val clientId = dao.insertClient(Client(name = "Cliente Leal"))
            val supId = dao.insertSupplier(Supplier(name = "Finca Chiapas"))

            val shift = repo.openCashRegister(500.0, "Inicio")!!
            repo.registerCashShiftMovement(CashShiftMovementType.ENTRADA, 200.0, "Sencillo")

            val purRes = repo.registerPurchase(
                supplierId = supId,
                fallbackSupplierName = "Finca Chiapas",
                lines = listOf(PurchaseLineInput(itemId = itemId, quantity = 10.0, unitCost = 100.0)),
                isCredit = false,
                notes = "Lote Mayo",
                operationId = "op-pur-1"
            )
            assertTrue(purRes is RepoPurchaseResult.Committed)

            // Cash sale of 3 units
            val s1 = repo.completeSale(
                items = listOf(SaleCartItem(itemId, "Grano Oro 1kg", 3.0, 220.0, 100.0, "Kg", ItemType.PRODUCTO)),
                saleType = SaleType.VENTA,
                selectedCustomerId = clientId,
                paymentMethod = "Efectivo",
                customerPaidInput = "700",
                discountInput = "0",
                notes = "Mostrador",
                operationId = "op-sale-1"
            ) as RepoSaleResult.Committed

            // Sale later canceled -> must NOT appear in confirmed revenue!
            val s2 = repo.completeSale(
                items = listOf(SaleCartItem(itemId, "Grano Oro 1kg", 2.0, 220.0, 100.0, "Kg", ItemType.PRODUCTO)),
                saleType = SaleType.VENTA,
                selectedCustomerId = clientId,
                paymentMethod = "Tarjeta",
                customerPaidInput = "440",
                discountInput = "0",
                notes = "Error",
                operationId = "op-sale-2"
            ) as RepoSaleResult.Committed
            repo.cancelSale(s2.sale)

            repo.closeCashRegister(countedCash = 1360.0, nextTurnFloat = 300.0, closingNotes = "Cierre OK")

            val dataset = ReportsDataset(
                sales = dao.getAllSalesSync(),
                purchases = dao.getAllPurchasesSync(),
                inventory = dao.getAllInventoryItemsSync(),
                extraMovements = dao.getAllExtraMovementsSync(),
                rentalsLoans = dao.getAllRentalsLoansSync(),
                clients = dao.getAllClientsSync(),
                suppliers = dao.getAllSuppliersSync(),
                employees = dao.getAllEmployeesSync(),
                cashShifts = dao.getAllCashShiftsSync(),
                cashShiftMovements = dao.getAllShiftMovementsSync()
            )

            val summary = ReportsEngine.computeFinancialSummary(dataset, DateFilterType.HOY)
            assertEquals(1, summary.confirmedSalesCount)
            assertEquals(660.0, summary.confirmedSalesTotal, 0.001)
            assertEquals(1, summary.canceledSalesCount)
            assertEquals(440.0, summary.canceledSalesTotal, 0.001)
            assertEquals(1000.0, summary.purchasesTotal, 0.001)
            assertEquals(200.0, summary.cashDrawerEntriesTotal, 0.001)
            assertEquals(300.0, summary.historicalCogsTotal, 0.001) // 3 * 100.0
            assertEquals(360.0, summary.grossProfit!!, 0.001) // 660 - 300

            val invMod = ReportsEngine.buildReport("Modificación Inventario", DateFilterType.HOY, dataset, currentSettings)
            assertTrue(invMod.rows.any { it[1] == "Entrada Compra" && it[5].contains("+10") })
            assertTrue(invMod.rows.any { it[1] == "Salida Venta" && it[2] == s1.sale.folio })
            assertTrue(invMod.rows.any { it[1] == "Reingreso Cancel." && it[2] == s2.sale.folio })
            assertTrue(shift.id > 0)
        }
    }

    @Test
    fun test19_wasteAndRawMaterialsReportsSeparation() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 10, 0, 0)
        val dataset = ReportsDataset(
            inventory = listOf(
                InventoryItem(id = 1, name = "Harina Húmeda", code = "W-MP", costPrice = 20.0, salePrice = 0.0, stock = 5.0, category = "Materia Prima", type = ItemType.PRODUCTO, isWaste = true),
                InventoryItem(id = 2, name = "Vaso Quebrado", code = "W-PROD", costPrice = 15.0, salePrice = 0.0, stock = 4.0, category = "Accesorios", type = ItemType.PRODUCTO, isWaste = true),
                InventoryItem(id = 3, name = "Harina Buena", code = "MP-OK", costPrice = 20.0, salePrice = 40.0, stock = 10.0, minStock = 5.0, category = "Materia Prima", type = ItemType.PRODUCTO, isWaste = false)
            )
        )

        val mermaMp = ReportsEngine.buildReport("Merma Materia Prima", DateFilterType.TODOS, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, mermaMp.rows.size)
        assertEquals("Harina Húmeda", mermaMp.rows[0][1])
        assertEquals("$100.00", mermaMp.summaryStats["Pérdida Valorizada"])

        val mermaProd = ReportsEngine.buildReport("Merma Productos", DateFilterType.TODOS, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, mermaProd.rows.size)
        assertEquals("Vaso Quebrado", mermaProd.rows[0][1])
        assertEquals("$60.00", mermaProd.summaryStats["Pérdida Valorizada"])
    }

    @Test
    fun test20_paymentMethodsTagsStaffHistoryAndInventoryModifications() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 10, 0, 0)
        val dataset = ReportsDataset(
            sales = listOf(
                Sale(id = 1, folio = "F-1", total = 300.0, subtotal = 300.0, paidAmount = 300.0, paymentMethod = "Transferencia", sellerName = "Ana", notes = "Evento", date = nowMs),
                Sale(id = 2, folio = "F-2", total = 200.0, subtotal = 200.0, paidAmount = 200.0, paymentMethod = "Efectivo", sellerName = "Ana", notes = "Evento", date = nowMs)
            ),
            cashShifts = listOf(CashRegisterShift(id = 1, cashierName = "Ana", openedAt = nowMs, isOpen = false, difference = -10.0))
        )

        val payMethod = ReportsEngine.buildReport("Forma Pago", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(2, payMethod.rows.size)
        assertEquals("Transferencia", payMethod.rows[0][0])
        assertEquals("60.0%", payMethod.rows[0][5])

        val tagRep = ReportsEngine.buildReport("Etiqueta Venta", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, tagRep.rows.size)
        assertEquals("Evento", tagRep.rows[0][0])

        val staffHist = ReportsEngine.buildReport("Empleado Historial", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertEquals(1, staffHist.rows.size)
        assertEquals("Ana", staffHist.rows[0][0])
        assertEquals("2", staffHist.rows[0][2])
        assertEquals("-$10.00", staffHist.rows[0][6])
    }

    @Test
    fun test21_composeReportsScreenRendersKpisFiltersAndFullColumnModal() {
        val db = CajaMaestraDatabase.getDatabase(app, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
        runBlocking {
            val dao = db.dao()
            dao.insertItem(InventoryItem(id = 1, name = "Botella Térmica", code = "BT-77", costPrice = 90.0, salePrice = 200.0, stock = 10.0))
            dao.insertSale(
                Sale(
                    folio = "F-UI-1",
                    type = SaleType.VENTA,
                    status = SaleStatus.PAGADA,
                    customerName = "Sofía",
                    subtotal = 400.0,
                    total = 400.0,
                    paidAmount = 400.0,
                    paymentMethod = "Efectivo",
                    date = System.currentTimeMillis(),
                    itemsJson = """[{"itemId":1,"name":"Botella Térmica","quantity":2,"unitPrice":200.0,"costPrice":90.0,"unit":"Pza","type":"PRODUCTO"}]"""
                )
            )
        }

        val vm = MainViewModel(app)
        runBlocking {
            vm.awaitInitialized()
            assertTrue(
                vm.initializeSecuritySetup(
                    adminPassword = "AdminPass99",
                    jefePassword = "JefePass99",
                    trabajadorPassword = "Trab99",
                    masterPin = "5678",
                    recoveryQuestion = "Ciudad de origen?",
                    recoveryAnswer = "Monterrey"
                ) is InitialSecuritySetupResult.Success
            )
            assertTrue(vm.authenticateUser("Jefe", "JefePass99"))
            vm.awaitPersistence()
        }

        composeRule.setContent {
            MyApplicationTheme {
                ReportsScreen(
                    viewModel = vm,
                    onOpenDrawer = {},
                    onShowTutorial = {}
                )
            }
        }

        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("$400.00").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kpi_confirmed_sales").assertIsDisplayed()
        composeRule.onNodeWithTag("kpi_gross_profit").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("$400.00").fetchSemanticsNodes().size >= 2)
        composeRule.onNodeWithText("$220.00").assertIsDisplayed() // 400 - (2*90)

        // Scroll grid and open 8-column report modal and confirm columns beyond index 2 exist & render!
        composeRule.onNodeWithTag("reports_tiles_grid")
            .performScrollToNode(hasTestTag("report_tile_producto_ventas"))
        composeRule.onNodeWithTag("report_tile_producto_ventas").performClick()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("report_detail_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Botella Térmica").assertExists()
        composeRule.onNodeWithText("Costo Hist.").assertExists()
        composeRule.onNodeWithText("Utilidad").assertExists()
        composeRule.onNodeWithText("Última Venta").assertExists()
        composeRule.onNodeWithTag("export_csv_button").assertIsDisplayed()
        composeRule.onNodeWithTag("export_pdf_button").assertIsDisplayed()

        // Close modal and verify restricted Trabajador role blocks financial reports
        composeRule.onNodeWithTag("close_report_detail_button").performClick()
        runBlocking {
            vm.switchActiveRoleWithCredential(UserRole.TRABAJADOR, "Trab99")
            vm.awaitPersistence()
        }
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Acceso Restringido").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun test22_emptyPeriodHandlesGracefullyInEngineCsvHtmlAndUi() {
        val nowMs = localTimestamp(2026, Calendar.MAY, 15, 10, 0, 0)
        val oldMs = localTimestamp(2020, Calendar.JANUARY, 1, 10, 0, 0)
        val dataset = ReportsDataset(
            sales = listOf(Sale(id = 1, folio = "F-OLD", total = 100.0, subtotal = 100.0, date = oldMs))
        )

        val summary = ReportsEngine.computeFinancialSummary(dataset, DateFilterType.HOY, nowMs, mexTz)
        assertEquals(0, summary.confirmedSalesCount)
        assertEquals(0.0, summary.confirmedSalesTotal, 0.001)
        assertEquals(CostCoverage.NO_SALES, summary.costCoverage)
        assertEquals(0.0, summary.grossProfit!!, 0.001)
        assertEquals(0.0, summary.netProfit!!, 0.001)

        val emptyRep = ReportsEngine.buildReport("Transacciones Por Día", DateFilterType.HOY, dataset, BusinessSettings(), nowMs, mexTz)
        assertTrue(emptyRep.rows.isEmpty())
        assertNull(emptyRep.totalsRow)

        val html = ReportExporter.buildHtmlDocument(
            title = emptyRep.title,
            businessName = "Caja Test",
            headers = emptyRep.headers,
            rows = emptyRep.rows,
            periodLabel = emptyRep.periodLabel
        )
        assertTrue(html.contains("No hay registros para el período seleccionado"))
    }
}
