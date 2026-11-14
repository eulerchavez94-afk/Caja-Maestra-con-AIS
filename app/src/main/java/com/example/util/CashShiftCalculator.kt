package com.example.util

import com.example.data.model.CashRegisterShift
import com.example.data.model.CashShiftMovement
import com.example.data.model.CashShiftMovementType
import com.example.data.model.Sale
import com.example.data.model.SaleStatus
import com.example.data.model.SaleType

data class ShiftLiveSummary(
    val openingFloat: Double = 0.0,
    val cashSalesTotal: Double = 0.0,
    val cardSalesTotal: Double = 0.0,
    val transferSalesTotal: Double = 0.0,
    val creditSalesTotal: Double = 0.0,
    val totalDiscounts: Double = 0.0,
    val totalGrossSales: Double = 0.0,
    val salesCount: Int = 0,
    val cashEntriesTotal: Double = 0.0,
    val cashWithdrawalsTotal: Double = 0.0,
    val expectedCash: Double = 0.0
)

/**
 * Pure business logic calculator for Cash Register Shifts (Apertura, Movimientos, Arqueo y Corte).
 */
object CashShiftCalculator {

    val DENOMINATIONS = listOf(1000.0, 500.0, 200.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 1.0, 0.5)

    fun computeDenominationTotal(counts: Map<Double, Int>): Double {
        return counts.entries.sumOf { (denom, qty) ->
            denom * qty.coerceAtLeast(0)
        }
    }

    fun computeLiveSummary(
        shift: CashRegisterShift,
        shiftSales: List<Sale>,
        shiftMovements: List<CashShiftMovement>
    ): ShiftLiveSummary {
        val validSales = shiftSales.filter {
            it.type == SaleType.VENTA && it.status != SaleStatus.CANCELADA
        }

        var cashSales = 0.0
        var cardSales = 0.0
        var transferSales = 0.0
        var creditSales = 0.0
        var totalDiscounts = 0.0

        for (sale in validSales) {
            totalDiscounts += sale.discount
            val method = PosSearchEngine.normalize(sale.paymentMethod)
            when {
                sale.status == SaleStatus.CREDITO || method.contains("credito") -> {
                    creditSales += sale.total
                }
                method.contains("tarjeta") || method.contains("debito") || method.contains("tdc") -> {
                    cardSales += sale.total
                }
                method.contains("transferencia") || method.contains("spei") -> {
                    transferSales += sale.total
                }
                else -> {
                    // Cash payment: net cash retained in drawer is sale.total (since paid - change = total)
                    cashSales += sale.total
                }
            }
        }

        var entries = 0.0
        var withdrawals = 0.0
        for (mov in shiftMovements) {
            when (mov.type) {
                CashShiftMovementType.ENTRADA -> entries += mov.amount
                CashShiftMovementType.RETIRO -> withdrawals += mov.amount
            }
        }

        val expectedCash = (shift.openingFloat + cashSales + entries - withdrawals)
        val totalGross = cashSales + cardSales + transferSales + creditSales

        return ShiftLiveSummary(
            openingFloat = shift.openingFloat,
            cashSalesTotal = cashSales,
            cardSalesTotal = cardSales,
            transferSalesTotal = transferSales,
            creditSalesTotal = creditSales,
            totalDiscounts = totalDiscounts,
            totalGrossSales = totalGross,
            salesCount = validSales.size,
            cashEntriesTotal = entries,
            cashWithdrawalsTotal = withdrawals,
            expectedCash = expectedCash
        )
    }

    fun buildClosedShift(
        openShift: CashRegisterShift,
        shiftSales: List<Sale>,
        shiftMovements: List<CashShiftMovement>,
        countedCash: Double,
        nextTurnFloat: Double,
        closingNotes: String,
        closedAt: Long = System.currentTimeMillis()
    ): CashRegisterShift {
        val summary = computeLiveSummary(openShift, shiftSales, shiftMovements)
        val difference = countedCash - summary.expectedCash
        return openShift.copy(
            closedAt = closedAt,
            cashSalesTotal = summary.cashSalesTotal,
            cardSalesTotal = summary.cardSalesTotal,
            transferSalesTotal = summary.transferSalesTotal,
            creditSalesTotal = summary.creditSalesTotal,
            cashEntriesTotal = summary.cashEntriesTotal,
            cashWithdrawalsTotal = summary.cashWithdrawalsTotal,
            expectedCash = summary.expectedCash,
            countedCash = countedCash,
            difference = difference,
            nextTurnFloat = nextTurnFloat.coerceAtLeast(0.0),
            salesCount = summary.salesCount,
            isOpen = false,
            closingNotes = closingNotes.trim()
        )
    }
}
