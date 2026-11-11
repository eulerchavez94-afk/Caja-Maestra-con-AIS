package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.CajaMaestraTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.DateFilterType
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ReportExporter
import com.example.util.RoleManager
import java.util.Locale

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
    val context = LocalContext.current
    val sales by viewModel.sales.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val inventory by viewModel.inventoryItems.collectAsState()
    val movements by viewModel.extraMovements.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val selectedReportTitle by viewModel.selectedReportTitle.collectAsState()
    val dateFilter by viewModel.reportDateFilter.collectAsState()

    val totalSales = sales.sumOf { it.total }
    val totalPurchases = purchases.sumOf { it.total }
    val estimatedProfit = (totalSales - totalPurchases).coerceAtLeast(0.0)

    // 25+ Report Tiles with rich, modern visual palettes
    val reportTiles = remember {
        listOf(
            ReportTileItem("Transacciones Por Día", TileGreen, Icons.Default.CalendarToday),
            ReportTileItem("Resumen Transacciones", TileGreen, Icons.Default.Receipt),
            ReportTileItem("Ventas Crédito", TileYellow, Icons.Default.CreditScore),
            ReportTileItem("Compras Crédito", TileYellow, Icons.Default.ShoppingBag),
            ReportTileItem("Ingreso Extra Crédito", TileYellow, Icons.Default.TrendingUp),
            ReportTileItem("Gasto Extra Crédito", TileYellow, Icons.Default.TrendingDown),
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
            ReportTileItem("Etiqueta Venta", TileTeal, Icons.Default.Label),
            ReportTileItem("Etiqueta Compra", TileTeal, Icons.Default.LabelImportant),
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

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CajaMaestraTopBar(
                    title = "Reportes Financieros",
                    subtitle = if (isRestricted) "Acceso Restringido por el Dueño" else "25+ Vistas y Análisis del Negocio",
                    currentRole = settings.currentRole,
                    onOpenDrawer = onOpenDrawer,
                    actions = {
                        if (!isRestricted) {
                            IconButton(onClick = onShowTutorial) {
                                Icon(Icons.Default.HelpOutline, contentDescription = "Ayuda", tint = Color.White)
                            }
                        }
                    }
                )

                // KPI Dashboard Ribbon (Hidden when restricted or when KPIs are confidential)
                if (permissions.canViewKpiRibbon) {
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
                                title = "Ventas Totales",
                                amount = "${settings.currencySymbol}${String.format(Locale.US, "%.0f", totalSales)}",
                                color = BrandEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            ReportKpiCard(
                                title = "Compras / Stock",
                                amount = "${settings.currencySymbol}${String.format(Locale.US, "%.0f", totalPurchases)}",
                                color = BrandAmber,
                                modifier = Modifier.weight(1f)
                            )
                            ReportKpiCard(
                                title = "Ganancia Bruta",
                                amount = "${settings.currencySymbol}${String.format(Locale.US, "%.0f", estimatedProfit)}",
                                color = BrandBlue,
                                modifier = Modifier.weight(1f)
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

            // Grid of 25+ Report Tiles
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
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

    // Detailed Report Dialog with Export to PDF & Excel
    if (selectedReportTitle != null) {
        val title = selectedReportTitle!!

        val rows = remember(title, dateFilter, sales, purchases, inventory, movements) {
            when {
                title.contains("Ventas", ignoreCase = true) -> {
                    sales.map {
                        listOf(it.folio, it.customerName, "${settings.currencySymbol}${it.total}", it.paymentMethod, it.status.name)
                    }
                }
                title.contains("Compras", ignoreCase = true) || title.contains("Orden Compra", ignoreCase = true) -> {
                    purchases.map {
                        listOf(it.folio, it.supplierName, "${settings.currencySymbol}${it.total}", if (it.isCredit) "Crédito" else "Pagada")
                    }
                }
                title.contains("Proveedor", ignoreCase = true) -> {
                    suppliers.map {
                        listOf(it.name, it.company, it.phone, it.paymentTerms, it.suppliedProducts)
                    }
                }
                title.contains("Cliente", ignoreCase = true) -> {
                    clients.map {
                        listOf(it.name, it.phone, "${settings.currencySymbol}${it.creditBalance}", it.notes)
                    }
                }
                title.contains("Inventario", ignoreCase = true) || title.contains("Producto", ignoreCase = true) -> {
                    inventory.map {
                        listOf(it.code, it.name, "${it.stock} ${it.unit}", "${settings.currencySymbol}${it.salePrice}", it.category)
                    }
                }
                else -> {
                    sales.map {
                        listOf(it.folio, it.customerName, "${settings.currencySymbol}${it.total}", it.sellerName)
                    }
                }
            }
        }

        val headers = remember(title) {
            when {
                title.contains("Ventas", ignoreCase = true) -> listOf("Folio", "Cliente", "Total", "Método", "Estado")
                title.contains("Compras", ignoreCase = true) -> listOf("Folio", "Proveedor", "Total", "Tipo")
                title.contains("Proveedor", ignoreCase = true) -> listOf("Nombre", "Empresa", "Teléfono", "Condiciones", "Productos")
                title.contains("Cliente", ignoreCase = true) -> listOf("Nombre", "Teléfono", "Saldo Crédito", "Notas")
                title.contains("Inventario", ignoreCase = true) || title.contains("Producto", ignoreCase = true) -> listOf("Código", "Nombre", "Existencia", "Precio Venta", "Categoría")
                else -> listOf("Folio", "Cliente", "Monto", "Responsable")
            }
        }

        Dialog(
            onDismissRequest = { viewModel.closeReportDetail() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextDark
                            )
                            Text(
                                text = "${rows.size} registros encontrados",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        IconButton(onClick = { viewModel.closeReportDetail() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Records table preview
                    Surface(
                        color = CanvasLight,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp)
                    ) {
                        if (rows.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("No hay datos en este período.", color = TextMuted, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                items(rows) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(row.getOrNull(0) ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                        Text(row.getOrNull(1) ?: "", fontSize = 12.sp, color = TextMedium, maxLines = 1)
                                        Text(row.getOrNull(2) ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandEmeraldDark)
                                    }
                                    HorizontalDivider(color = SurfaceHover)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Export Buttons: PDF and Excel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                ReportExporter.exportToExcel(
                                    context = context,
                                    reportTitle = title.replace(" ", "_"),
                                    headers = headers,
                                    rows = rows
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Excel (CSV)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = {
                                ReportExporter.printReportAsPdf(
                                    context = context,
                                    reportTitle = title,
                                    businessName = settings.businessName,
                                    headers = headers,
                                    rows = rows
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
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
}

@Composable
private fun ReportKpiCard(
    title: String,
    amount: String,
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
                fontSize = 15.sp,
                color = color
            )
        }
    }
}
