package com.example.util

import com.example.data.model.BusinessSettings
import com.example.data.model.Sale
import com.example.data.model.SaleCartItem
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ThermalPrinterUtil {
    private const val COLUMNS_58MM = 32

    /**
     * Formats receipt text tailored for 58mm thermal printers (32 columns max).
     */
    fun formatReceiptText(
        settings: BusinessSettings,
        sale: Sale,
        items: List<SaleCartItem>
    ): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dateStr = sdf.format(Date(sale.date))
        val sb = StringBuilder()

        val divider = "-".repeat(COLUMNS_58MM)
        val doubleDivider = "=".repeat(COLUMNS_58MM)

        // Header
        sb.append(centerText(settings.businessName, COLUMNS_58MM)).append("\n")
        if (settings.address.isNotBlank()) {
            sb.append(centerText(settings.address, COLUMNS_58MM)).append("\n")
        }
        if (settings.phone.isNotBlank()) {
            sb.append(centerText("Tel: ${settings.phone}", COLUMNS_58MM)).append("\n")
        }
        if (settings.email.isNotBlank()) {
            sb.append(centerText(settings.email, COLUMNS_58MM)).append("\n")
        }
        sb.append(doubleDivider).append("\n")

        // Ticket details
        if (settings.receiptShowFolio) {
            sb.append(justifyText("FOLIO:", sale.folio, COLUMNS_58MM)).append("\n")
        }
        sb.append(justifyText("FECHA:", dateStr, COLUMNS_58MM)).append("\n")
        if (settings.receiptShowStatus) {
            sb.append(justifyText("ESTADO:", sale.status.name, COLUMNS_58MM)).append("\n")
        }
        sb.append(justifyText("CAJERO:", sale.sellerName, COLUMNS_58MM)).append("\n")

        if (settings.receiptShowClientInfo && sale.customerName.isNotBlank()) {
            sb.append(justifyText("CLIENTE:", sale.customerName, COLUMNS_58MM)).append("\n")
        }

        sb.append(divider).append("\n")
        sb.append(formatHeaderRow()).append("\n")
        sb.append(divider).append("\n")

        // Items
        for (item in items) {
            val name = item.name.take(COLUMNS_58MM)
            sb.append(name).append("\n")
            val qtyStr = "${String.format(Locale.US, "%.1f", item.quantity)} ${if (settings.receiptShowKeyAndUnit) item.unit else ""}".trim()
            val priceStr = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", item.unitPrice)}"
            val subtotalStr = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", item.subtotal)}"
            sb.append(formatItemSubRow(qtyStr, priceStr, subtotalStr, COLUMNS_58MM)).append("\n")
        }

        sb.append(divider).append("\n")

        // Totals
        sb.append(justifyText("SUBTOTAL:", "${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.subtotal)}", COLUMNS_58MM)).append("\n")
        if (sale.discount > 0) {
            sb.append(justifyText("DESCUENTO:", "-${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.discount)}", COLUMNS_58MM)).append("\n")
        }
        sb.append(justifyText("TOTAL A PAGAR:", "${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.total)}", COLUMNS_58MM)).append("\n")

        if (settings.receiptShowPaymentBreakdown) {
            sb.append(divider).append("\n")
            sb.append(justifyText("METODO DE PAGO:", sale.paymentMethod, COLUMNS_58MM)).append("\n")
            if (sale.paidAmount > 0) {
                sb.append(justifyText("RECIBIDO:", "${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.paidAmount)}", COLUMNS_58MM)).append("\n")
                sb.append(justifyText("CAMBIO:", "${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.changeAmount)}", COLUMNS_58MM)).append("\n")
            }
        }

        sb.append(doubleDivider).append("\n")

        if (settings.footerText.isNotBlank()) {
            sb.append(centerText(settings.footerText, COLUMNS_58MM)).append("\n")
        }
        if (settings.receiptShowQr && settings.customQr.isNotBlank()) {
            sb.append(centerText("[ QR: ${settings.customQr} ]", COLUMNS_58MM)).append("\n")
        }
        sb.append(centerText("CAJA MAESTRA - POS OFFLINE", COLUMNS_58MM)).append("\n\n\n")

        return sb.toString()
    }

    /**
     * Builds ESC/POS binary command stream for thermal printer hardware (58mm).
     */
    fun buildEscPosBytes(receiptText: String, scale: Float = 1.0f): ByteArray {
        val out = ByteArrayOutputStream()

        // ESC @ : Initialize printer
        out.write(byteArrayOf(0x1B, 0x40))

        // Text encoding
        val lines = receiptText.split("\n")
        for (line in lines) {
            out.write(line.toByteArray(Charsets.ISO_8859_1))
            out.write(byteArrayOf(0x0A)) // Line feed
        }

        // Extra feeds and full cut
        out.write(byteArrayOf(0x1B, 0x64, 0x03)) // feed 3 lines
        out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // GS V cut paper

        return out.toByteArray()
    }

    fun printThermalReceipt58mm(
        context: android.content.Context,
        settings: BusinessSettings,
        sale: Sale,
        items: List<SaleCartItem>
    ) {
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager ?: return
        val webView = android.webkit.WebView(context)
        val receiptHtml = buildThermalHtmlReceipt(settings, sale, items)

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                val printAdapter = webView.createPrintDocumentAdapter("Ticket_${sale.folio}")
                val thermal58mmMediaSize = android.print.PrintAttributes.MediaSize(
                    "ROLL_58MM",
                    "Rollo Térmico 58mm",
                    2280, // 58 mm
                    7874  // 200 mm
                )
                printManager.print(
                    "Ticket_${sale.folio}",
                    printAdapter,
                    android.print.PrintAttributes.Builder()
                        .setMediaSize(thermal58mmMediaSize)
                        .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                        .build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, receiptHtml, "text/html", "utf-8", null)
    }

    private fun buildThermalHtmlReceipt(
        settings: BusinessSettings,
        sale: Sale,
        items: List<SaleCartItem>
    ): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(sale.date))

        val itemsHtml = items.joinToString("") { item ->
            """
            <div style="font-weight: bold; margin-top: 3px;">${item.name}</div>
            <div class="row">
               <span>${String.format(Locale.US, "%.1f", item.quantity)} ${item.unit} x ${settings.currencySymbol}${String.format(Locale.US, "%.2f", item.unitPrice)}</span>
               <span>${settings.currencySymbol}${String.format(Locale.US, "%.2f", item.subtotal)}</span>
            </div>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                @page { margin: 0; size: 58mm auto; }
                body {
                    width: 48mm;
                    margin: 0 auto;
                    padding: 4mm 1mm;
                    font-family: 'Courier New', Courier, monospace;
                    font-size: 11px;
                    line-height: 14px;
                    color: #000;
                }
                .center { text-align: center; }
                .bold { font-weight: bold; }
                .divider { border-top: 1px dashed #000; margin: 4px 0; }
                .double-divider { border-top: 2px solid #000; margin: 4px 0; }
                .row { display: flex; justify-content: space-between; font-size: 11px; }
                .title { font-size: 15px; font-weight: bold; margin-bottom: 2px; }
            </style>
        </head>
        <body>
            <div class="center title">${settings.businessName}</div>
            ${if (settings.address.isNotBlank()) "<div class=\"center\">${settings.address}</div>" else ""}
            ${if (settings.phone.isNotBlank()) "<div class=\"center\">Tel: ${settings.phone}</div>" else ""}
            <div class="double-divider"></div>
            <div class="row"><span>FOLIO:</span><span class="bold">${sale.folio}</span></div>
            <div class="row"><span>FECHA:</span><span>$dateStr</span></div>
            <div class="row"><span>ESTADO:</span><span class="bold">${sale.status.name}</span></div>
            <div class="row"><span>CAJERO:</span><span>${sale.sellerName}</span></div>
            ${if (settings.receiptShowClientInfo && sale.customerName.isNotBlank()) "<div class=\"row\"><span>CLIENTE:</span><span>${sale.customerName}</span></div>" else ""}
            <div class="divider"></div>
            <div class="row bold"><span>DESCRIPCIÓN</span><span>TOTAL</span></div>
            <div class="divider"></div>
            $itemsHtml
            <div class="divider"></div>
            <div class="row"><span>SUBTOTAL:</span><span>${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.subtotal)}</span></div>
            ${if (sale.discount > 0) "<div class=\"row\"><span>DESCUENTO:</span><span>-${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.discount)}</span></div>" else ""}
            <div class="row bold" style="font-size: 13px;"><span>TOTAL:</span><span>${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.total)}</span></div>
            <div class="divider"></div>
            <div class="row"><span>PAGO:</span><span>${sale.paymentMethod}</span></div>
            ${if (sale.paidAmount > 0) "<div class=\"row\"><span>RECIBIDO:</span><span>${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.paidAmount)}</span></div><div class=\"row bold\"><span>CAMBIO:</span><span>${settings.currencySymbol}${String.format(Locale.US, "%.2f", sale.changeAmount)}</span></div>" else ""}
            <div class="double-divider"></div>
            ${if (settings.footerText.isNotBlank()) "<div class=\"center\" style=\"margin-top:4px;\">${settings.footerText}</div>" else ""}
            ${if (settings.receiptShowQr && settings.customQr.isNotBlank()) "<div class=\"center\" style=\"font-size:9px; margin-top:4px;\">[ QR: ${settings.customQr} ]</div>" else ""}
            <div class="center" style="font-size: 9px; margin-top: 6px;">CAJA MAESTRA POS 58MM</div>
        </body>
        </html>
        """.trimIndent()
    }

    fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        val pad = (width - text.length) / 2
        return " ".repeat(pad) + text
    }

    fun justifyText(left: String, right: String, width: Int): String {
        val total = left.length + right.length
        if (total >= width) {
            val available = width - right.length - 1
            val trimmedLeft = if (available > 0) left.take(available) else left
            return "$trimmedLeft $right"
        }
        val spaces = " ".repeat(width - total)
        return left + spaces + right
    }

    private fun formatHeaderRow(): String {
        return justifyText("CANT  DESCRIPCIÓN", "TOTAL", COLUMNS_58MM)
    }

    private fun formatItemSubRow(qty: String, unitPrice: String, total: String, width: Int): String {
        val left = "  $qty x $unitPrice"
        return justifyText(left, total, width)
    }

    /**
     * Formats a 58mm thermal receipt (32 columns) for Cash Register Closing (Corte de Caja).
     */
    fun formatCashShiftReportText(
        settings: BusinessSettings,
        shift: com.example.data.model.CashRegisterShift
    ): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val openedStr = sdf.format(Date(shift.openedAt))
        val closedStr = shift.closedAt?.let { sdf.format(Date(it)) } ?: "TURNO ABIERTO"
        val cur = settings.currencySymbol
        val sb = StringBuilder()

        val divider = "-".repeat(COLUMNS_58MM)
        val doubleDivider = "=".repeat(COLUMNS_58MM)

        sb.append(centerText(settings.businessName, COLUMNS_58MM)).append("\n")
        sb.append(centerText("*** CORTE DE CAJA ***", COLUMNS_58MM)).append("\n")
        sb.append(doubleDivider).append("\n")
        sb.append(justifyText("TURNO #:", "${shift.id}", COLUMNS_58MM)).append("\n")
        sb.append(justifyText("CAJERO:", shift.cashierName, COLUMNS_58MM)).append("\n")
        sb.append(justifyText("APERTURA:", openedStr, COLUMNS_58MM)).append("\n")
        sb.append(justifyText("CIERRE:", closedStr, COLUMNS_58MM)).append("\n")
        sb.append(divider).append("\n")

        sb.append(justifyText("FONDO INICIAL:", "$cur${String.format(Locale.US, "%.2f", shift.openingFloat)}", COLUMNS_58MM)).append("\n")
        sb.append(justifyText("VENTAS (${shift.salesCount}):", "", COLUMNS_58MM)).append("\n")
        sb.append(justifyText("  Efectivo:", "$cur${String.format(Locale.US, "%.2f", shift.cashSalesTotal)}", COLUMNS_58MM)).append("\n")
        sb.append(justifyText("  Tarjeta:", "$cur${String.format(Locale.US, "%.2f", shift.cardSalesTotal)}", COLUMNS_58MM)).append("\n")
        sb.append(justifyText("  Transf.:", "$cur${String.format(Locale.US, "%.2f", shift.transferSalesTotal)}", COLUMNS_58MM)).append("\n")
        sb.append(justifyText("  Credito:", "$cur${String.format(Locale.US, "%.2f", shift.creditSalesTotal)}", COLUMNS_58MM)).append("\n")

        val totalVentas = shift.cashSalesTotal + shift.cardSalesTotal + shift.transferSalesTotal + shift.creditSalesTotal
        sb.append(justifyText("TOTAL VENDIDO:", "$cur${String.format(Locale.US, "%.2f", totalVentas)}", COLUMNS_58MM)).append("\n")
        sb.append(divider).append("\n")

        sb.append(justifyText("+ ENTRADAS CAJA:", "$cur${String.format(Locale.US, "%.2f", shift.cashEntriesTotal)}", COLUMNS_58MM)).append("\n")
        sb.append(justifyText("- RETIROS/SANG.:", "$cur${String.format(Locale.US, "%.2f", shift.cashWithdrawalsTotal)}", COLUMNS_58MM)).append("\n")
        sb.append(divider).append("\n")

        sb.append(justifyText("EFECT. ESPERADO:", "$cur${String.format(Locale.US, "%.2f", shift.expectedCash)}", COLUMNS_58MM)).append("\n")
        sb.append(justifyText("EFECT. CONTADO:", "$cur${String.format(Locale.US, "%.2f", shift.countedCash)}", COLUMNS_58MM)).append("\n")

        val diffLabel = when {
            kotlin.math.abs(shift.difference) < 0.01 -> "CUADRE:"
            shift.difference > 0 -> "SOBRANTE:"
            else -> "FALTANTE:"
        }
        val diffVal = if (kotlin.math.abs(shift.difference) < 0.01) {
            "EXACTO ($cur" + "0.00)"
        } else {
            "$cur${String.format(Locale.US, "%.2f", kotlin.math.abs(shift.difference))}"
        }
        sb.append(justifyText(diffLabel, diffVal, COLUMNS_58MM)).append("\n")

        if (shift.nextTurnFloat > 0) {
            sb.append(justifyText("FONDO SIG. TURNO:", "$cur${String.format(Locale.US, "%.2f", shift.nextTurnFloat)}", COLUMNS_58MM)).append("\n")
            val finalRetiro = (shift.countedCash - shift.nextTurnFloat).coerceAtLeast(0.0)
            sb.append(justifyText("RETIRO EN SOBRE:", "$cur${String.format(Locale.US, "%.2f", finalRetiro)}", COLUMNS_58MM)).append("\n")
        }

        if (shift.closingNotes.isNotBlank()) {
            sb.append(divider).append("\n")
            sb.append("NOTAS: ${shift.closingNotes.take(64)}\n")
        }

        sb.append(doubleDivider).append("\n")
        sb.append(centerText("FIRMA CAJERO: ____________", COLUMNS_58MM)).append("\n\n")
        sb.append(centerText("CAJA MAESTRA - CORTE 58MM", COLUMNS_58MM)).append("\n\n\n")
        return sb.toString()
    }

    fun printCashShiftReport58mm(
        context: android.content.Context,
        settings: BusinessSettings,
        shift: com.example.data.model.CashRegisterShift
    ) {
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager ?: return
        val webView = android.webkit.WebView(context)
        val reportText = formatCashShiftReportText(settings, shift)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val html = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><style>
            @page { margin: 0; size: 58mm auto; }
            body { width: 48mm; margin: 0 auto; padding: 4mm 1mm; font-family: 'Courier New', monospace; font-size: 11px; white-space: pre-wrap; color: #000; }
            </style></head><body>$reportText</body></html>
        """.trimIndent()

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                val adapter = webView.createPrintDocumentAdapter("Corte_Turno_${shift.id}")
                val mediaSize = android.print.PrintAttributes.MediaSize("ROLL_58MM", "Rollo Térmico 58mm", 2280, 7874)
                printManager.print(
                    "Corte_Turno_${shift.id}",
                    adapter,
                    android.print.PrintAttributes.Builder()
                        .setMediaSize(mediaSize)
                        .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                        .build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}

