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

    /**
     * Exports tabular report data to CSV/Excel compatible file and launches Android share sheet.
     */
    fun exportToExcel(
        context: Context,
        reportTitle: String,
        headers: List<String>,
        rows: List<List<String>>
    ) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${reportTitle.replace(" ", "_")}_$timeStamp.csv"
            val file = File(context.cacheDir, fileName)

            FileOutputStream(file).use { fos ->
                // Write UTF-8 BOM so Excel opens with proper accents
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

                // Headers
                fos.write((headers.joinToString(",") { "\"$it\"" } + "\n").toByteArray(Charsets.UTF_8))

                // Rows
                for (row in rows) {
                    val line = row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\n"
                    fos.write(line.toByteArray(Charsets.UTF_8))
                }
            }

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
        } catch (e: Exception) {
            e.printStackTrace()
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
        summaryStats: Map<String, String> = emptyMap()
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return

        val webView = WebView(context)
        val htmlContent = buildHtmlDocument(reportTitle, businessName, headers, rows, summaryStats)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
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
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
    }

    private fun buildHtmlDocument(
        title: String,
        businessName: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryStats: Map<String, String>
    ): String {
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val statsHtml = if (summaryStats.isNotEmpty()) {
            val items = summaryStats.entries.joinToString("") { (k, v) ->
                """<div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; margin:4px; display:inline-block;">
                    <span style="font-size:11px; color:#64748b; display:block;">$k</span>
                    <strong style="font-size:15px; color:#1e293b;">$v</strong>
                </div>"""
            }
            """<div style="margin:16px 0;">$items</div>"""
        } else ""

        val headersHtml = headers.joinToString("") {
            """<th style="background:#1B2A4A; color:#ffffff; padding:8px; border:1px solid #cbd5e1; text-align:left; font-size:12px;">$it</th>"""
        }

        val rowsHtml = rows.joinToString("") { row ->
            val cells = row.joinToString("") {
                """<td style="padding:6px 8px; border:1px solid #e2e8f0; font-size:11px; color:#334155;">$it</td>"""
            }
            """<tr>$cells</tr>"""
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>$title</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif; margin: 20px; color: #1e293b; }
                h1 { margin: 0; font-size: 20px; color: #1B2A4A; }
                .subtitle { color: #64748b; font-size: 12px; margin-top: 4px; }
                table { width: 100%; border-collapse: collapse; margin-top: 14px; }
                tr:nth-child(even) { background-color: #f8fafc; }
                .footer { margin-top: 24px; font-size: 10px; color: #94a3b8; text-align: center; border-top: 1px solid #e2e8f0; padding-top: 8px; }
            </style>
        </head>
        <body>
            <div>
                <h1>$businessName</h1>
                <div class="subtitle">$title &bull; Generado: $dateStr</div>
            </div>
            $statsHtml
            <table>
                <thead><tr>$headersHtml</tr></thead>
                <tbody>$rowsHtml</tbody>
            </table>
            <div class="footer">Documento Oficial generado por Caja Maestra POS &bull; Sistema Seguro con Cifrado de Datos</div>
        </body>
        </html>
        """.trimIndent()
    }
}
