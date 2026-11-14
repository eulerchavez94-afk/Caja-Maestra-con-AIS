package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LabelImportant
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CajaMaestraTopBar
import com.example.ui.components.ReportDetailDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DateFilterType
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CostCoverage
import com.example.util.FinancialSummary
import com.example.util.ReportsDataset
import com.example.util.ReportsEngine
import com.example.util.RoleManager

data class ReportTileItem(
    val title: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onShowTutorial: () -> Unit
) {
    val sales by viewModel.sales.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val inventory by viewModel.inventoryItems.collectAsState()
    val movements by viewModel.extraMovements.collectAsState()
    val rentalsLoans by viewModel.rentalsLoans.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val employees by viewModel.employees.collectAsState()
    val cashShifts by viewModel.cashShifts.collectAsState()
    val cashShiftMovements by viewModel.allShiftMovements.collectAsState()
    val inventoryMovements by viewModel.inventoryMovements.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val selectedReportTitle by viewModel.selectedReportTitle.collectAsState()
    val dateFilter by viewModel.reportDateFilter.collectAsState()

    val dataset = remember(
        sales,
        purchases,
        inventory,
        movements,
        rentalsLoans,
        clients,
        suppliers,
        employees,
        cashShifts,
        cashShiftMovements,
        inventoryMovements
    ) {
        ReportsDataset(
            sales = sales,
            purchases = purchases,
            inventory = inventory,
            extraMovements = movements,
            rentalsLoans = rentalsLoans,
            clients = clients,
            suppliers = suppliers,
            employees = employees,
            cashShifts = cashShifts,
            cashShiftMovements = cashShiftMovements,
            inventoryMovements = inventoryMovements
        )
    }

    val financialSummary = remember(dataset, dateFilter) {
        ReportsEngine.computeFinancialSummary(dataset = dataset, dateFilter = dateFilter)
    }

    // All 41 Report Tiles preserved with exact names, icons, and palettes
    val reportTiles = remember {
        listOf(
            ReportTileItem("Transacciones Por Día", TileGreen, Icons.Default.CalendarToday),
            ReportTileItem("Resumen Transacciones", TileGreen, Icons.Default.Receipt),
            ReportTileItem("Ventas Crédito", TileYellow, Icons.Default.CreditScore),
            ReportTileItem("Compras Crédito", TileYellow, Icons.Default.ShoppingBag),
            ReportTileItem("Ingreso Extra Crédito", TileYellow, Icons.AutoMirrored.Filled.TrendingUp),
            ReportTileItem("Gasto Extra Crédito", TileYellow, Icons.AutoMirrored.Filled.TrendingDown),
            ReportTileItem("Préstamos Activos", TileYellow, Icons.Default.Handshake),
            ReportTileItem("Rentas Activas", TileYellow, Icons.Default.ShoppingCartCheckout),
            ReportTileItem("Producto Ventas", TilePurple, Icons.Default.PointOfSale),
            ReportTileItem("Producto Compras", TilePurple, Icons.Default.LocalShipping),
            ReportTileItem("Materia Prima Compras", TilePurple, Icons.Default.Category),
            ReportTileItem("Manufactura Ventas", TilePurple, Icons.Default.PrecisionManufacturing),
            ReportTileItem("Servicio Ventas", TilePurple, Icons.Default.HomeRepairService),
            ReportTileItem("Productos Sin Ventas", TileSalmon, Icons.Default.HourglassEmpty),
            ReportTileItem("Servicios Sin Ventas", TileSalmon, Icons.Default.EventBusy),
            ReportTileItem("Manufactura Sin Ventas", TileSalmon, Icons.Default.DoNotDisturb),
            ReportTileItem("Categoría Ingreso Extra", TileTeal, Icons.Default.AccountBalanceWallet),
            ReportTileItem("Categoría Gasto Extra", TileTeal, Icons.Default.MoneyOff),
            ReportTileItem("Producto Análisis", TileLilac, Icons.Default.Analytics),
            ReportTileItem("Materia Prima Análisis", TileLilac, Icons.Default.Insights),
            ReportTileItem("Servicio Análisis", TileLilac, Icons.Default.QueryStats),
            ReportTileItem("Manufactura Análisis", TileLilac, Icons.Default.Assessment),
            ReportTileItem("Categoría Producto Ventas", TilePink, Icons.Default.PieChart),
            ReportTileItem("Categoría Producto Compras", TilePink, Icons.Default.DonutSmall),
            ReportTileItem("Clientes", TileTeal, Icons.Default.Group),
            ReportTileItem("Cliente Historial", TileTeal, Icons.Default.History),
            ReportTileItem("Proveedores", TileTeal, Icons.Default.Domain),
            ReportTileItem("Proveedor Historial", TileTeal, Icons.Default.ManageAccounts),
            ReportTileItem("Empleados", TileTeal, Icons.Default.Badge),
            ReportTileItem("Empleado Historial", TileTeal, Icons.Default.WorkHistory),
            ReportTileItem("Merma Productos", TileOlive, Icons.Default.DeleteSweep),
            ReportTileItem("Merma Materia Prima", TileOlive, Icons.Default.AutoDelete),
            ReportTileItem("Forma Pago", TileTeal, Icons.Default.Payments),
            ReportTileItem("Etiqueta Venta", TileTeal, Icons.AutoMirrored.Filled.Label),
            ReportTileItem("Etiqueta Compra", TileTeal, Icons.AutoMirrored.Filled.LabelImportant),
            ReportTileItem("Cotización Reporte", TileBlue, Icons.Default.Description),
            ReportTileItem("Orden Compra Reporte", TileBlue, Icons.Default.RequestQuote),
            ReportTileItem("Producto Inventario Resumen", TileBrown, Icons.Default.Inventory),
            ReportTileItem("Materia Prima Inventario Resumen", TileBrown, Icons.Default.Warehouse),
            ReportTileItem("Manufactura Producción", TileBrown, Icons.Default.Factory),
            ReportTileItem("Modificación Inventario", TileBrown, Icons.Default.EditNote)
        )
    }

    val permissions = remember(settings) { RoleManager.getPermissions(settings) }
    val isRestricted = !permissions.canViewFinancialReports
    val cur = settings.currencySymbol

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CajaMaestraTopBar(
                    title = "Reportes Financieros",
                    subtitle = if (isRestricted) {
                        "Acceso Restringido por el Dueño"
                    } else {
                        "${reportTiles.size} Reportes • ${financialSummary.periodLabel}"
                    },
                    currentRole = settings.currentRole,
                    onOpenDrawer = onOpenDrawer,
                    actions = {
                        if (!isRestricted) {
                            IconButton(onClick = onShowTutorial) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Ayuda", tint = Color.White)
                            }
                        }
                    }
                )

                // Live Date-Filtered KPI Dashboard Ribbon
                if (permissions.canViewKpiRibbon) {
                    val grossDisplay = when (financialSummary.costCoverage) {
                        CostCoverage.COMPLETE, CostCoverage.NO_SALES ->
                            ReportsEngine.formatCurrency(financialSummary.grossProfit ?: 0.0, cur)
                        CostCoverage.PARTIAL -> "Parcial"
                        CostCoverage.UNAVAILABLE -> "Sin costo"
                    }
                    val grossSubDisplay = when (financialSummary.costCoverage) {
                        CostCoverage.COMPLETE, CostCoverage.NO_SALES ->
                            "Neta: ${ReportsEngine.formatCurrency(financialSummary.netProfit ?: 0.0, cur)}"
                        CostCoverage.PARTIAL, CostCoverage.UNAVAILABLE -> "Falta costo hist."
                    }

                    Surface(
                        color = BrandNavyElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportKpiCard(
                                title = "Ventas Conf.",
                                amount = ReportsEngine.formatCurrency(financialSummary.confirmedSalesTotal, cur),
                                subText = "Cobrado: ${ReportsEngine.formatCurrency(financialSummary.collectedSalesTotal, cur)}",
                                color = BrandEmerald,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("kpi_confirmed_sales")
                            )
                            ReportKpiCard(
                                title = "Compras / Gastos",
                                amount = ReportsEngine.formatCurrency(financialSummary.purchasesTotal, cur),
                                subText = "Gastos Ext: ${ReportsEngine.formatCurrency(financialSummary.extraExpensesTotal, cur)}",
                                color = BrandAmber,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("kpi_purchases_expenses")
                            )
                            ReportKpiCard(
                                title = "Utilidad Bruta",
                                amount = grossDisplay,
                                subText = grossSubDisplay,
                                color = when {
                                    (financialSummary.grossProfit ?: 0.0) < 0.0 -> StatusCancelled
                                    else -> BrandBlue
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("kpi_gross_profit")
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isRestricted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(CanvasLight)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceWhite,
                    shadowElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TileSalmon.copy(alpha = 0.15f),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = TileSalmon, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Acceso Restringido",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "El Dueño o Administrador ha limitado la visualización de los Reportes Financieros y balances de utilidades para el rol de Trabajador.",
                            fontSize = 13.sp,
                            color = TextMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenDrawer,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir Menú de Módulos", color = Color.White)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(CanvasLight)
            ) {
                // Period Filter Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        DateFilterType.HOY to "Hoy",
                        DateFilterType.ULTIMA_SEMANA to "Última Semana",
                        DateFilterType.ESTE_MES to "Este Mes",
                        DateFilterType.TODOS to "Todo el Historial"
                    ).forEach { (filterType, label) ->
                        val isSelected = dateFilter == filterType
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) BrandNavy else SurfaceWhite,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BrandNavy else SurfaceHover),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable { viewModel.setReportDateFilter(filterType) }
                                    .testTag("date_filter_${filterType.name.lowercase()}")
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else TextMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // Grid of all 41 Report Tiles + Financial Summary Snapshot Header
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("reports_tiles_grid")
                ) {
                    if (permissions.canViewKpiRibbon) {
                        item(span = { GridItemSpan(2) }) {
                            FinancialSnapshotCard(summary = financialSummary, cur = cur)
                        }
                    }

                    items(reportTiles) { tile ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = tile.color,
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clickable { viewModel.openReportDetail(tile.title) }
                                .testTag("report_tile_${tile.title.lowercase().replace(" ", "_")}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.22f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = tile.icon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = tile.title,
                                    color = Color.White,
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Detailed Report Modal with full-column preview, visual bar chart & CSV/PDF export
    if (selectedReportTitle != null) {
        val generatedReport = remember(selectedReportTitle, dateFilter, dataset, settings.currencySymbol) {
            ReportsEngine.buildReport(
                title = selectedReportTitle!!,
                dateFilter = dateFilter,
                dataset = dataset,
                settings = settings
            )
        }

        ReportDetailDialog(
            report = generatedReport,
            businessName = settings.businessName,
            activeFilter = dateFilter,
            onFilterChange = { viewModel.setReportDateFilter(it) },
            onDismiss = { viewModel.closeReportDetail() }
        )
    }
}

@Composable
private fun FinancialSnapshotCard(
    summary: FinancialSummary,
    cur: String
) {
    val maxVal = maxOf(
        summary.collectedSalesTotal,
        summary.creditSalesTotal,
        summary.purchasesTotal,
        summary.extraExpensesTotal,
        1.0
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("financial_snapshot_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Balance del Período",
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BrandNavy
                )
                Text(
                    text = "${summary.confirmedSalesCount} vtas • ${summary.quotesCount} cot. • ${summary.canceledSalesCount} canc.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4 proportional indicators: Cobrado, Crédito, Compras, Gastos
            val bars = listOf(
                Triple("Cobrado", summary.collectedSalesTotal, BrandEmerald),
                Triple("Crédito", summary.creditSalesTotal, BrandAmber),
                Triple("Compras", summary.purchasesTotal, BrandBlue),
                Triple("Gastos Ext.", summary.extraExpensesTotal, TileSalmon)
            )
            bars.forEach { (label, amount, barColor) ->
                val ratio = (amount / maxVal).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = TextMedium,
                        modifier = Modifier.width(68.dp)
                    )
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                    ) {
                        drawRoundRect(
                            color = Color(0xFFF1F5F9),
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(5f, 5f)
                        )
                        if (ratio > 0f) {
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width * ratio, size.height),
                                cornerRadius = CornerRadius(5f, 5f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ReportsEngine.formatCurrency(amount, cur),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SurfaceHover)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ing. Ext: ${ReportsEngine.formatCurrency(summary.extraIncomeTotal, cur)}",
                    fontSize = 10.sp,
                    color = BrandEmeraldDark,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Cotiz: ${ReportsEngine.formatCurrency(summary.quotesPendingTotal, cur)}",
                    fontSize = 10.sp,
                    color = BrandBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Caja Ent/Ret: ${ReportsEngine.formatCurrency(summary.cashDrawerEntriesTotal, cur)} / ${ReportsEngine.formatCurrency(summary.cashDrawerWithdrawalsTotal, cur)}",
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (summary.profitLimitationNote != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.profitLimitationNote,
                    fontSize = 10.sp,
                    color = BrandAmberDark,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ReportKpiCard(
    title: String,
    amount: String,
    subText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = TextLightSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = amount,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subText,
                fontSize = 9.sp,
                color = TextLightSecondary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
