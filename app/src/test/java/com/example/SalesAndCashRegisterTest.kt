package com.example

import com.example.data.model.BusinessSettings
import com.example.data.model.CashRegisterShift
import com.example.data.model.CashShiftMovement
import com.example.data.model.CashShiftMovementType
import com.example.data.model.InventoryItem
import com.example.data.model.ItemType
import com.example.data.model.Sale
import com.example.data.model.SaleStatus
import com.example.data.model.SaleType
import com.example.data.model.UserRole
import com.example.util.CashShiftCalculator
import com.example.util.PosSearchEngine
import com.example.util.RoleManager
import com.example.util.ThermalPrinterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesAndCashRegisterTest {

    private val sampleCatalog = listOf(
        InventoryItem(
            id = 1L,
            code = "7501001",
            name = "Café Gourmet Molido 500g",
            category = "Bebidas Calientes",
            type = ItemType.PRODUCTO,
            stock = 12.0,
            costPrice = 90.0,
            salePrice = 145.0
        ),
        InventoryItem(
            id = 2L,
            code = "7501002",
            name = "Azúcar Morena 1kg",
            category = "Abarrotes",
            type = ItemType.PRODUCTO,
            stock = 0.0,
            costPrice = 22.0,
            salePrice = 34.0
        ),
        InventoryItem(
            id = 3L,
            code = "7501",
            name = "Agua Purificada 1L",
            category = "Bebidas",
            type = ItemType.PRODUCTO,
            stock = 40.0,
            costPrice = 9.0,
            salePrice = 18.0
        ),
        InventoryItem(
            id = 4L,
            code = "SRV-01",
            name = "Instalación Express",
            category = "Servicios",
            type = ItemType.SERVICIO,
            stock = 0.0,
            costPrice = 0.0,
            salePrice = 250.0
        ),
        InventoryItem(
            id = 5L,
            code = "MERMA-99",
            name = "Café Dañado",
            category = "Merma",
            type = ItemType.PRODUCTO,
            stock = 2.0,
            costPrice = 50.0,
            salePrice = 0.0,
            isWaste = true
        )
    )

    @Test
    fun `search ignores accents and supports multi-token matching while excluding waste`() {
        val index = PosSearchEngine.buildIndex(sampleCatalog)
        // Waste item must be excluded from index
        assertEquals(4, index.size)

        // Multi-token accent-insensitive search ("cafe 500" -> Café Gourmet Molido 500g)
        val results = PosSearchEngine.search(index, "cafe 500")
        assertEquals(1, results.size)
        assertEquals(1L, results.first().id)

        // Accent-insensitive single token ("azucar")
        val sugarResults = PosSearchEngine.search(index, "azucar")
        assertEquals(1, sugarResults.size)
        assertEquals("Azúcar Morena 1kg", sugarResults.first().name)
    }

    @Test
    fun `search ranks exact barcode before prefix matches and findExactBarcodeMatch works`() {
        val index = PosSearchEngine.buildIndex(sampleCatalog)
        val ranked = PosSearchEngine.search(index, "7501")
        assertTrue(ranked.size >= 3)
        // Exact code "7501" (Agua Purificada 1L) MUST rank first
        assertEquals("7501", ranked.first().code)

        val exact = PosSearchEngine.findExactBarcodeMatch(sampleCatalog, "  7501001 ")
        assertNotNull(exact)
        assertEquals("Café Gourmet Molido 500g", exact?.name)

        // Waste item barcode should not match in POS
        assertNull(PosSearchEngine.findExactBarcodeMatch(sampleCatalog, "MERMA-99"))
    }

    @Test
    fun `computeTopSellingItems aggregates non-cancelled sales and suggestCashBills proposes smart tenders`() {
        val sales = listOf(
            Sale(
                id = 1L,
                folio = "F-00001",
                type = SaleType.VENTA,
                status = SaleStatus.PAGADA,
                subtotal = 54.0,
                total = 54.0,
                paymentMethod = "Efectivo",
                itemsJson = """[{"name":"Agua Purificada 1L","quantity":3}]"""
            ),
            Sale(
                id = 2L,
                folio = "F-00002",
                type = SaleType.VENTA,
                status = SaleStatus.CANCELADA,
                subtotal = 1450.0,
                total = 1450.0,
                paymentMethod = "Efectivo",
                itemsJson = """[{"name":"Café Gourmet Molido 500g","quantity":10}]"""
            )
        )

        val top = PosSearchEngine.computeTopSellingItems(sampleCatalog, sales, limit = 4)
        // Cancelled sale should not propel Café above Agua Purificada
        assertEquals("Agua Purificada 1L", top.first().name)

        val bills = PosSearchEngine.suggestCashBills(135.0)
        assertTrue(bills.contains(140.0) || bills.contains(150.0) || bills.contains(200.0))
        assertTrue(bills.all { it > 135.0 })
    }

    @Test
    fun `cash shift calculator computes expected cash, movements, and surplus or shortage accurately`() {
        val openShift = CashRegisterShift(
            id = 10L,
            cashierName = "Carlos",
            cashierRole = UserRole.TRABAJADOR,
            openingFloat = 500.0,
            isOpen = true
        )

        val shiftSales = listOf(
            Sale(folio = "F-1", type = SaleType.VENTA, status = SaleStatus.PAGADA, subtotal = 200.0, total = 200.0, paymentMethod = "Efectivo", shiftId = 10L),
            Sale(folio = "F-2", type = SaleType.VENTA, status = SaleStatus.PAGADA, subtotal = 350.0, total = 350.0, paymentMethod = "Tarjeta", shiftId = 10L),
            Sale(folio = "F-3", type = SaleType.VENTA, status = SaleStatus.PAGADA, subtotal = 150.0, total = 150.0, paymentMethod = "Transferencia", shiftId = 10L),
            Sale(folio = "F-4", type = SaleType.VENTA, status = SaleStatus.CREDITO, subtotal = 100.0, total = 100.0, paymentMethod = "Crédito", shiftId = 10L),
            // Cancelled sale MUST be ignored
            Sale(folio = "F-5", type = SaleType.VENTA, status = SaleStatus.CANCELADA, subtotal = 999.0, total = 999.0, paymentMethod = "Efectivo", shiftId = 10L)
        )

        val movements = listOf(
            CashShiftMovement(shiftId = 10L, type = CashShiftMovementType.ENTRADA, amount = 100.0, reason = "Cambio", performedBy = "Carlos"),
            CashShiftMovement(shiftId = 10L, type = CashShiftMovementType.RETIRO, amount = 150.0, reason = "Proveedor", performedBy = "Carlos")
        )

        // Expected cash = 500 (float) + 200 (cash sales) + 100 (entry) - 150 (withdrawal) = 650.0
        val summary = CashShiftCalculator.computeLiveSummary(openShift, shiftSales, movements)
        assertEquals(4, summary.salesCount)
        assertEquals(200.0, summary.cashSalesTotal, 0.001)
        assertEquals(350.0, summary.cardSalesTotal, 0.001)
        assertEquals(150.0, summary.transferSalesTotal, 0.001)
        assertEquals(100.0, summary.creditSalesTotal, 0.001)
        assertEquals(100.0, summary.cashEntriesTotal, 0.001)
        assertEquals(150.0, summary.cashWithdrawalsTotal, 0.001)
        assertEquals(650.0, summary.expectedCash, 0.001)

        // Shortage of 20.0 when counted is 630.0
        val closedShortage = CashShiftCalculator.buildClosedShift(
            openShift = openShift,
            shiftSales = shiftSales,
            shiftMovements = movements,
            countedCash = 630.0,
            nextTurnFloat = 500.0,
            closingNotes = "Faltó moneda de $20"
        )
        assertFalse(closedShortage.isOpen)
        assertEquals(650.0, closedShortage.expectedCash, 0.001)
        assertEquals(630.0, closedShortage.countedCash, 0.001)
        assertEquals(-20.0, closedShortage.difference, 0.001)

        // Denomination counter check
        val denomTotal = CashShiftCalculator.computeDenominationTotal(
            mapOf(500.0 to 1, 100.0 to 1, 20.0 to 2, 0.5 to 4)
        )
        assertEquals(642.0, denomTotal, 0.001)
    }

    @Test
    fun `role manager enforces blind count for trabajador while allowing cash register operations`() {
        val workerSettings = BusinessSettings(
            currentRole = UserRole.TRABAJADOR,
            moduleSalesEnabled = true,
            jefeAllowsTrabajadorSales = true
        )
        val workerPerms = RoleManager.getPermissions(workerSettings)
        assertTrue(workerPerms.canOpenCloseCashRegister)
        assertFalse(workerPerms.canViewCashShiftExpectedBeforeClose) // Arqueo ciego
        assertFalse(workerPerms.canCancelSales)

        val ownerSettings = BusinessSettings(
            currentRole = UserRole.DUENO,
            moduleSalesEnabled = true
        )
        val ownerPerms = RoleManager.getPermissions(ownerSettings)
        assertTrue(ownerPerms.canOpenCloseCashRegister)
        assertTrue(ownerPerms.canViewCashShiftExpectedBeforeClose)
        assertTrue(ownerPerms.canCancelSales)
    }

    @Test
    fun `thermal printer formats 58mm cash shift report text with key audit lines`() {
        val settings = BusinessSettings(businessName = "Tienda La Palma", currencySymbol = "$")
        val shift = CashRegisterShift(
            id = 5L,
            cashierName = "Ana",
            openingFloat = 500.0,
            cashSalesTotal = 300.0,
            expectedCash = 800.0,
            countedCash = 800.0,
            difference = 0.0,
            salesCount = 3,
            isOpen = false
        )
        val ticket = ThermalPrinterUtil.formatCashShiftReportText(settings, shift)
        assertTrue(ticket.contains("CORTE DE CAJA"))
        assertTrue(ticket.contains("TURNO #:"))
        assertTrue(ticket.contains("5"))
        assertTrue(ticket.contains("CUADRE:"))
        assertTrue(ticket.contains("EXACTO"))
    }
}
