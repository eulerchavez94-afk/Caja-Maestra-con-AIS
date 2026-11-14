package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.ui.viewmodel.DateFilterType
import com.example.util.ChartBarItem
import com.example.util.GeneratedReport
import com.example.util.ReportExporter

@Composable
fun ReportDetailDialog(
    report: GeneratedReport,
    businessName: String,
    activeFilter: DateFilterType,
    onFilterChange: (DateFilterType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val horizontalScrollState = rememberScrollState()
    val dialogBodyScrollState = rememberScrollState()

    val columnWidths = androidx.compose.runtime.remember(report.headers, report.rows, report.totalsRow) {
        report.headers.mapIndexed { colIdx, header ->
            computeColumnWidth(colIdx, header, report.rows, report.totalsRow)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .imePadding()
                .testTag("report_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report.title,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextDark
                        )
                        Text(
                            text = "${report.periodLabel} • ${report.rows.size} registros",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_report_detail_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(dialogBodyScrollState)
                ) {
                    // Quick period switcher directly inside modal
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            DateFilterType.HOY to "Hoy",
                            DateFilterType.ULTIMA_SEMANA to "Últimos 7 días",
                            DateFilterType.ESTE_MES to "Este Mes",
                            DateFilterType.TODOS to "Todo"
                        ).forEach { (filterType, label) ->
                            val selected = activeFilter == filterType
                            item {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) BrandNavy else SurfaceSubtle,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) BrandNavy else SurfaceHover
                                    ),
                                    modifier = Modifier
                                        .clickable { onFilterChange(filterType) }
                                        .testTag("modal_filter_${filterType.name.lowercase()}")
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else TextMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (report.summaryStats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            report.summaryStats.entries.forEach { (k, v) ->
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = SurfaceSubtle,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text(k, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                                            Text(
                                                v,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandNavy,
                                                fontFamily = DisplayFontFamily
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!report.limitationsNote.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandAmber.copy(alpha = 0.14f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandAmber.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = BrandAmberDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = report.limitationsNote,
                                    fontSize = 11.sp,
                                    color = TextDark,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    if (report.chartBars.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ReportBarChartCard(bars = report.chartBars)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Full-column horizontally & vertically scrollable table preview
                    Surface(
                        color = CanvasLight,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 300.dp)
                    ) {
                        if (report.rows.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No hay registros para el filtro \"${report.periodLabel}\".",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                LazyColumn(modifier = Modifier.padding(vertical = 6.dp)) {
                                    // Header row showing ALL columns
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .background(BrandNavy)
                                                .padding(vertical = 7.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            report.headers.forEachIndexed { idx, header ->
                                                val width = columnWidths.getOrElse(idx) { 110.dp }
                                                Text(
                                                    text = header,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .width(width)
                                                        .padding(end = 8.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Data rows showing ALL columns
                                    items(report.rows) { row ->
                                        Row(
                                            modifier = Modifier
                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            report.headers.indices.forEach { idx ->
                                                val width = columnWidths.getOrElse(idx) { 110.dp }
                                                val cellText = row.getOrNull(idx) ?: ""
                                                Text(
                                                    text = cellText,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (idx == report.headers.lastIndex) BrandEmeraldDark else TextDark,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .width(width)
                                                        .padding(end = 8.dp)
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = SurfaceHover)
                                    }

                                    // Totals footer row
                                    if (report.totalsRow != null) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .background(BrandBlueLight.copy(alpha = 0.55f))
                                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                report.headers.indices.forEach { idx ->
                                                    val width = columnWidths.getOrElse(idx) { 110.dp }
                                                    val cellText = report.totalsRow.getOrNull(idx) ?: ""
                                                    Text(
                                                        text = cellText,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = BrandNavy,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier
                                                            .width(width)
                                                            .padding(end = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val result = ReportExporter.exportToExcel(
                                context = context,
                                reportTitle = report.title,
                                headers = report.headers,
                                rows = report.rows,
                                periodLabel = report.periodLabel,
                                totalsRow = report.totalsRow,
                                summaryStats = report.summaryStats,
                                limitationsNote = report.limitationsNote
                            )
                            result.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.message ?: "Error al exportar CSV.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Excel (CSV)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            val result = ReportExporter.printReportAsPdf(
                                context = context,
                                reportTitle = report.title,
                                businessName = businessName,
                                headers = report.headers,
                                rows = report.rows,
                                summaryStats = report.summaryStats,
                                periodLabel = report.periodLabel,
                                totalsRow = report.totalsRow,
                                limitationsNote = report.limitationsNote,
                                onError = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            )
                            result.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.message ?: "Error al preparar PDF.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportBarChartCard(bars: List<ChartBarItem>) {
    val maxVal = (bars.maxOfOrNull { it.value.coerceAtLeast(0.0) } ?: 0.0).coerceAtLeast(1.0)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceSubtle,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Distribución Visual",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandNavy
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            bars.forEach { bar ->
                val ratio = (bar.value.coerceAtLeast(0.0) / maxVal).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bar.label,
                        fontSize = 10.sp,
                        color = TextMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(92.dp)
                    )
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                    ) {
                        drawRoundRect(
                            color = Color(0xFFE2E8F0),
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        if (ratio > 0f) {
                            drawRoundRect(
                                color = BrandEmerald,
                                topLeft = Offset.Zero,
                                size = Size(size.width * ratio, size.height),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bar.displayValue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandNavy,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun computeColumnWidth(
    colIdx: Int,
    header: String,
    rows: List<List<String>>,
    totalsRow: List<String>?
): Dp {
    var maxChars = header.length
    for (row in rows.take(40)) {
        val len = row.getOrNull(colIdx)?.length ?: 0
        if (len > maxChars) maxChars = len
    }
    val totLen = totalsRow?.getOrNull(colIdx)?.length ?: 0
    if (totLen > maxChars) maxChars = totLen
    val estimated = (maxChars * 7 + 22).dp
    return estimated.coerceIn(88.dp, 180.dp)
}
