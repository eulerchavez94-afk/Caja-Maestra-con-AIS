package com.example.util

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private fun escapeCsvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun escapeHtml(text: String): String {
        return buildString(text.length) {
            for (ch in text) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }
    }

    /**
     * Builds full CSV string including all headers, all columns per row, optional totalsRow,
     * and trailing period/summary metadata so row 1 always remains the clean column header.
     */
    fun buildCsvContent(
        reportTitle: String,
        periodLabel: String = "",
        headers: List<String>,
        rows: List<List<String>>,
        totalsRow: List<String>? = null,
        summaryStats: Map<String, String> = emptyMap(),
        limitationsNote: String? = null,
        generatedAtMs: Long = System.currentTimeMillis()
    ): String {
        val sb = StringBuilder()
        sb.append(headers.joinToString(",") { escapeCsvCell(it) }).append("\n")

        for (row in rows) {
            val normalizedRow = if (headers.isNotEmpty() && row.size < headers.size) {
                row + List(headers.size - row.size) { "" }
            } else {
                row
            }
            sb.append(normalizedRow.joinToString(",") { escapeCsvCell(it) }).append("\n")
        }

        if (totalsRow != null) {
            val normalizedTotals = if (headers.isNotEmpty() && totalsRow.size < headers.size) {
                totalsRow + List(headers.size - totalsRow.size) { "" }
            } else {
                totalsRow
            }
            sb.append(normalizedTotals.joinToString(",") { escapeCsvCell(it) }).append("\n")
        }

        if (periodLabel.isNotBlank() || summaryStats.isNotEmpty() || !limitationsNote.isNullOrBlank()) {
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(generatedAtMs))
            sb.append("\n")
            sb.append(escapeCsvCell("Reporte")).append(",").append(escapeCsvCell(reportTitle)).append("\n")
            if (periodLabel.isNotBlank()) {
                sb.append(escapeCsvCell("Período")).append(",").append(escapeCsvCell(periodLabel)).append("\n")
            }
            sb.append(escapeCsvCell("Generado")).append(",").append(escapeCsvCell(dateStr)).append("\n")
            for ((k, v) in summaryStats) {
                sb.append(escapeCsvCell(k)).append(",").append(escapeCsvCell(v)).append("\n")
            }
            if (!limitationsNote.isNullOrBlank()) {
                sb.append(escapeCsvCell("Nota")).append(",").append(escapeCsvCell(limitationsNote)).append("\n")
            }
        }

        return sb.toString()
    }

    fun writeCsvFile(
        context: Context,
        reportTitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        periodLabel: String = "",
        totalsRow: List<String>? = null,
        summaryStats: Map<String, String> = emptyMap(),
        limitationsNote: String? = null,
        generatedAtMs: Long = System.currentTimeMillis()
    ): Result<File> {
        return runCatching {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(generatedAtMs))
            val sanitizedTitle = reportTitle.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val fileName = "${sanitizedTitle}_$timeStamp.csv"
            val file = File(context.cacheDir, fileName)

            val csvContent = buildCsvContent(
                reportTitle = reportTitle,
                periodLabel = periodLabel,
                headers = headers,
                rows = rows,
                totalsRow = totalsRow,
                summaryStats = summaryStats,
                limitationsNote = limitationsNote,
                generatedAtMs = generatedAtMs
            )

            FileOutputStream(file).use { fos ->
                // UTF-8 BOM so Excel opens Spanish accents cleanly
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                fos.write(csvContent.toByteArray(Charsets.UTF_8))
            }
            file
        }
    }

    /**
     * Exports tabular report data to CSV/Excel compatible file and launches Android share sheet.
     */
    fun exportToExcel(
        context: Context,
        reportTitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        periodLabel: String = "",
        totalsRow: List<String>? = null,
        summaryStats: Map<String, String> = emptyMap(),
        limitationsNote: String? = null,
        onError: ((String) -> Unit)? = null
    ): Result<File> {
        val fileResult = writeCsvFile(
            context = context,
            reportTitle = reportTitle,
            headers = headers,
            rows = rows,
            periodLabel = periodLabel,
            totalsRow = totalsRow,
            summaryStats = summaryStats,
            limitationsNote = limitationsNote
        )

        return fileResult.mapCatching { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Caja Maestra - $reportTitle")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Compartir Reporte Excel / CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            file
        }.onFailure { err ->
            onError?.invoke(err.message ?: "No se pudo exportar el archivo CSV.")
        }
    }

    /**
     * Prints or saves as PDF using Android PrintManager with clean responsive HTML styling.
     */
    fun printReportAsPdf(
        context: Context,
        reportTitle: String,
        businessName: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryStats: Map<String, String> = emptyMap(),
        periodLabel: String = "",
        totalsRow: List<String>? = null,
        limitationsNote: String? = null,
        onError: ((String) -> Unit)? = null
    ): Result<Unit> {
        return runCatching {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: throw IllegalStateException("El servicio de impresión PDF no está disponible en este dispositivo.")

            val webView = WebView(context)
            val htmlContent = buildHtmlDocument(
                title = reportTitle,
                businessName = businessName,
                headers = headers,
                rows = rows,
                summaryStats = summaryStats,
                periodLabel = periodLabel,
                totalsRow = totalsRow,
                limitationsNote = limitationsNote
            )

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    runCatching {
                        val printAdapter = webView.createPrintDocumentAdapter(reportTitle)
                        val printJobName = "Caja_Maestra_$reportTitle"
                        printManager.print(
                            printJobName,
                            printAdapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()
                        )
                    }.onFailure { err ->
                        onError?.invoke(err.message ?: "Error al iniciar la generación de PDF.")
                    }
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }.onFailure { err ->
            onError?.invoke(err.message ?: "No se pudo generar el documento PDF.")
        }
    }

    fun buildHtmlDocument(
        title: String,
        businessName: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryStats: Map<String, String> = emptyMap(),
        periodLabel: String = "",
        totalsRow: List<String>? = null,
        limitationsNote: String? = null,
        generatedAtMs: Long = System.currentTimeMillis()
    ): String {
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(generatedAtMs))
        val periodPart = if (periodLabel.isNotBlank()) " &bull; Período: ${escapeHtml(periodLabel)}" else ""

        val statsHtml = if (summaryStats.isNotEmpty()) {
            val items = summaryStats.entries.joinToString("") { (k, v) ->
                """<div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; margin:4px; display:inline-block;">
                    <span style="font-size:11px; color:#64748b; display:block;">${escapeHtml(k)}</span>
                    <strong style="font-size:14px; color:#1e293b;">${escapeHtml(v)}</strong>
                </div>"""
            }
            """<div style="margin:14px 0;">$items</div>"""
        } else ""

        val noteHtml = if (!limitationsNote.isNullOrBlank()) {
            """<div style="background:#fef3c7; border:1px solid #f59e0b; color:#92400e; padding:8px 12px; border-radius:6px; font-size:11px; margin:10px 0;">${escapeHtml(limitationsNote)}</div>"""
        } else ""

        val headersHtml = headers.joinToString("") {
            """<th style="background:#1B2A4A; color:#ffffff; padding:8px; border:1px solid #cbd5e1; text-align:left; font-size:11px;">${escapeHtml(it)}</th>"""
        }

        val rowsHtml = if (rows.isEmpty()) {
            val span = headers.size.coerceAtLeast(1)
            """<tr><td colspan="$span" style="padding:14px; text-align:center; color:#64748b; font-size:12px;">No hay registros para el período seleccionado.</td></tr>"""
        } else {
            rows.joinToString("") { row ->
                val normalized = if (headers.isNotEmpty() && row.size < headers.size) {
                    row + List(headers.size - row.size) { "" }
                } else {
                    row
                }
                val cells = normalized.joinToString("") {
                    """<td style="padding:6px 8px; border:1px solid #e2e8f0; font-size:11px; color:#334155;">${escapeHtml(it)}</td>"""
                }
                """<tr>$cells</tr>"""
            }
        }

        val totalsHtml = if (totalsRow != null) {
            val normalized = if (headers.isNotEmpty() && totalsRow.size < headers.size) {
                totalsRow + List(headers.size - totalsRow.size) { "" }
            } else {
                totalsRow
            }
            val cells = normalized.joinToString("") {
                """<td style="padding:7px 8px; border:1px solid #cbd5e1; font-size:11px; font-weight:700; background:#e2e8f0; color:#0f172a;">${escapeHtml(it)}</td>"""
            }
            """<tfoot><tr>$cells</tr></tfoot>"""
        } else ""

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>${escapeHtml(title)}</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif; margin: 20px; color: #1e293b; }
                h1 { margin: 0; font-size: 20px; color: #1B2A4A; }
                .subtitle { color: #64748b; font-size: 12px; margin-top: 4px; }
                table { width: 100%; border-collapse: collapse; margin-top: 14px; }
                tr:nth-child(even) td { background-color: #f8fafc; }
                .footer { margin-top: 24px; font-size: 10px; color: #94a3b8; text-align: center; border-top: 1px solid #e2e8f0; padding-top: 8px; }
            </style>
        </head>
        <body>
            <div>
                <h1>${escapeHtml(businessName)}</h1>
                <div class="subtitle">${escapeHtml(title)}$periodPart &bull; Generado: ${escapeHtml(dateStr)}</div>
            </div>
            $statsHtml
            $noteHtml
            <table>
                <thead><tr>$headersHtml</tr></thead>
                <tbody>$rowsHtml</tbody>
                $totalsHtml
            </table>
            <div class="footer">Documento Oficial generado por Caja Maestra POS &bull; Operación 100% Offline Segura</div>
        </body>
        </html>
        """.trimIndent()
    }
}
