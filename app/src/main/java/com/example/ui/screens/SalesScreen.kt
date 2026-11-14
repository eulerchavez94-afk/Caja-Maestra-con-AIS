package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CashShiftCalculator
import com.example.util.ReportExporter
import com.example.util.RoleManager
import com.example.util.ShiftLiveSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onShowTutorial: () -> Unit
) {
    val context = LocalContext.current
    val sales by viewModel.sales.collectAsState()
    val cashShifts by viewModel.cashShifts.collectAsState()
    val openCashShift by viewModel.openCashShift.collectAsState()
    val activeShiftMovements by viewModel.activeShiftMovements.collectAsState()
    val lastClosedShift by viewModel.lastClosedShift.collectAsState()
    val showShiftCutModal by viewModel.showShiftCutModal.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val permissions = remember(settings) { RoleManager.getPermissions(settings) }
    val canMakeSales = permissions.canCreateSale

    val currentSaleType by viewModel.currentSaleType.collectAsState()

    var isInNewSaleMode by remember { mutableStateOf(false) }
    var salesSearchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Cash register modal states
    var showOpenCashDialog by remember { mutableStateOf(false) }
    var showCashMovementDialog by remember { mutableStateOf(false) }
    var showCloseCashDialog by remember { mutableStateOf(false) }
    var showShiftsHistoryDialog by remember { mutableStateOf(false) }
    var enterPosAfterOpen by remember { mutableStateOf(false) }

    // Intercept system back button inside POS mode so it returns cleanly to the Sales Dashboard
    BackHandler(enabled = isInNewSaleMode) {
        isInNewSaleMode = false
    }

    val displayedSales by remember(sales, currentSaleType, salesSearchQuery) {
        derivedStateOf {
            sales.filter {
                it.type == currentSaleType &&
                        (salesSearchQuery.isBlank() ||
                                it.folio.contains(salesSearchQuery, ignoreCase = true) ||
                                it.customerName.contains(salesSearchQuery, ignoreCase = true) ||
                                it.paymentMethod.contains(salesSearchQuery, ignoreCase = true) ||
                                it.sellerName.contains(salesSearchQuery, ignoreCase = true))
            }
        }
    }

    val liveShiftSummary: ShiftLiveSummary? = remember(openCashShift, sales, activeShiftMovements) {
        val active = openCashShift ?: return@remember null
        val shiftSales = sales.filter { it.shiftId == active.id }
        CashShiftCalculator.computeLiveSummary(
            shift = active,
            shiftSales = shiftSales,
            shiftMovements = activeShiftMovements
        )
    }

    val startNewSaleOrQuote: () -> Unit = {
        viewModel.clearCart()
        if (currentSaleType == SaleType.VENTA && openCashShift == null && permissions.canOpenCloseCashRegister) {
            enterPosAfterOpen = true
            showOpenCashDialog = true
            Toast.makeText(
                context,
                "Abre la caja con el fondo inicial para iniciar ventas",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            isInNewSaleMode = true
        }
    }

    if (!isInNewSaleMode) {
        // --- SCREEN 4: Sales & Shift Register Dashboard ---
        Scaffold(
            topBar = {
                CajaMaestraTopBar(
                    title = if (currentSaleType == SaleType.VENTA) "Ventas (TPV)" else "Cotizaciones",
                    subtitle = if (openCashShift != null) {
                        "Turno #${openCashShift!!.id} activo • ${displayedSales.size} registros"
                    } else {
                        "Caja cerrada • ${displayedSales.size} registros"
                    },
                    currentRole = settings.currentRole,
                    onOpenDrawer = onOpenDrawer,
                    searchQuery = salesSearchQuery,
                    isSearchVisible = isSearchActive,
                    searchPlaceholder = "Buscar folio, cliente, vendedor...",
                    onSearchQueryChange = { salesSearchQuery = it },
                    onToggleSearch = { isSearchActive = !isSearchActive },
                    actions = {
                        IconButton(onClick = { showShiftsHistoryDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Historial de Cortes",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            val headers = listOf("Folio", "Cliente", "Total", "Método", "Estado", "Fecha")
                            val rows = displayedSales.map {
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it.date))
                                listOf(
                                    it.folio,
                                    it.customerName,
                                    "${settings.currencySymbol}${String.format(Locale.US, "%.2f", it.total)}",
                                    it.paymentMethod,
                                    it.status.name,
                                    dateStr
                                )
                            }
                            ReportExporter.exportToExcel(
                                context = context,
                                reportTitle = if (currentSaleType == SaleType.VENTA) "Ventas_Caja_Maestra" else "Cotizaciones_Caja_Maestra",
                                headers = headers,
                                rows = rows,
                                onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir Excel", tint = Color.White)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = BrandNavy,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = currentSaleType == SaleType.VENTA,
                        onClick = { viewModel.setSaleType(SaleType.VENTA) },
                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
                        label = { Text("Ventas", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandNavy,
                            selectedTextColor = Color.White,
                            indicatorColor = BrandEmerald,
                            unselectedIconColor = TextLightSecondary,
                            unselectedTextColor = TextLightSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = currentSaleType == SaleType.COTIZACION,
                        onClick = { viewModel.setSaleType(SaleType.COTIZACION) },
                        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                        label = { Text("Cotizaciones", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandNavy,
                            selectedTextColor = Color.White,
                            indicatorColor = BrandAmber,
                            unselectedIconColor = TextLightSecondary,
                            unselectedTextColor = TextLightSecondary
                        )
                    )
                }
            },
            floatingActionButton = {
                if (canMakeSales) {
                    ExtendedFloatingActionButton(
                        onClick = startNewSaleOrQuote,
                        containerColor = BrandEmerald,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("new_sale_fab"),
                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
                        text = {
                            Text(
                                text = if (currentSaleType == SaleType.VENTA) "Nueva Venta" else "Cotizar",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(CanvasLight)
            ) {
                // Cash Register Turn Card (always accessible in Ventas tab)
                if (currentSaleType == SaleType.VENTA) {
                    CashRegisterDashboardCard(
                        openShift = openCashShift,
                        liveSummary = liveShiftSummary,
                        currencySymbol = settings.currencySymbol,
                        canManageRegister = permissions.canOpenCloseCashRegister,
                        onOpenRegister = {
                            enterPosAfterOpen = false
                            showOpenCashDialog = true
                        },
                        onCashMovement = { showCashMovementDialog = true },
                        onCloseRegister = { showCloseCashDialog = true },
                        onViewHistory = { showShiftsHistoryDialog = true }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    var isInitialLoading by remember { mutableStateOf(sales.isEmpty()) }
                    LaunchedEffect(sales) {
                        isInitialLoading = false
                    }

                    if (isInitialLoading) {
                        SkeletonListPlaceholder(count = 4)
                    } else if (displayedSales.isEmpty()) {
                        CajaMaestraEmptyState(
                            icon = Icons.Default.PointOfSale,
                            title = if (currentSaleType == SaleType.VENTA) "Sin Ventas Registradas" else "Sin Cotizaciones",
                            description = if (!canMakeSales)
                                "El Administrador ha limitado la creación de ventas para tu rol actual."
                            else if (salesSearchQuery.isNotBlank())
                                "No se encontraron resultados para '$salesSearchQuery'."
                            else
                                "Realiza tu primera venta en tiempo real para generar tickets y descontar inventario automáticamente.",
                            actionButtonText = when {
                                salesSearchQuery.isNotBlank() -> "Limpiar Búsqueda"
                                canMakeSales -> "+ Iniciar Nueva Venta"
                                else -> ""
                            },
                            onActionClick = {
                                if (salesSearchQuery.isNotBlank()) {
                                    salesSearchQuery = ""
                                } else if (canMakeSales) {
                                    startNewSaleOrQuote()
                                }
                            },
                            onTutorialClick = onShowTutorial
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayedSales, key = { it.id }) { sale ->
                                ModernSaleItemCard(
                                    sale = sale,
                                    currencySymbol = settings.currencySymbol,
                                    onClick = { viewModel.openSaleReceipt(sale) }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- SCREEN 5: High-Speed POS Terminal / Nueva Venta ---
        PosTerminalView(
            viewModel = viewModel,
            openShift = openCashShift,
            onOpenCashRegisterRequested = {
                enterPosAfterOpen = false
                showOpenCashDialog = true
            },
            onOpenCashMovementsRequested = {
                showCashMovementDialog = true
            },
            onExitPos = {
                isInNewSaleMode = false
            }
        )
    }

    // --- Cash Register & Shift Modals ---
    if (showOpenCashDialog) {
        val lastClosed = remember(cashShifts) { cashShifts.firstOrNull { !it.isOpen } }
        OpenCashRegisterDialog(
            cashierName = settings.currentUserName,
            currencySymbol = settings.currencySymbol,
            lastClosingFloat = lastClosed?.nextTurnFloat,
            onDismiss = {
                showOpenCashDialog = false
                enterPosAfterOpen = false
            },
            onConfirmOpen = { openingFloat, notes ->
                viewModel.openCashRegister(openingFloat, notes)
                showOpenCashDialog = false
                Toast.makeText(
                    context,
                    "Caja abierta con ${settings.currencySymbol}${String.format(Locale.US, "%.2f", openingFloat)}",
                    Toast.LENGTH_SHORT
                ).show()
                if (enterPosAfterOpen) {
                    enterPosAfterOpen = false
                    isInNewSaleMode = true
                }
            }
        )
    }

    if (showCashMovementDialog && openCashShift != null) {
        CashMovementDialog(
            currencySymbol = settings.currencySymbol,
            recentMovements = activeShiftMovements,
            onDismiss = { showCashMovementDialog = false },
            onConfirmMovement = { type, amount, reason ->
                val ok = viewModel.registerCashShiftMovement(type, amount, reason)
                if (ok) {
                    showCashMovementDialog = false
                    val label = if (type == CashShiftMovementType.ENTRADA) "Entrada registrada" else "Retiro registrado"
                    Toast.makeText(context, "$label: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", amount)}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showCloseCashDialog && openCashShift != null && liveShiftSummary != null) {
        CloseCashRegisterDialog(
            shift = openCashShift!!,
            summary = liveShiftSummary,
            currencySymbol = settings.currencySymbol,
            canViewExpectedBeforeClose = permissions.canViewCashShiftExpectedBeforeClose,
            onDismiss = { showCloseCashDialog = false },
            onConfirmClose = { countedCash, nextTurnFloat, closingNotes ->
                viewModel.closeCashRegister(
                    countedCash = countedCash,
                    nextTurnFloat = nextTurnFloat,
                    closingNotes = closingNotes
                )
                showCloseCashDialog = false
                isInNewSaleMode = false
                Toast.makeText(context, "Corte de caja completado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showShiftsHistoryDialog) {
        CashShiftsHistoryDialog(
            shifts = cashShifts,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showShiftsHistoryDialog = false },
            onSelectClosedShift = { selectedShift ->
                showShiftsHistoryDialog = false
                viewModel.openShiftCutReport(selectedShift)
            }
        )
    }

    if (showShiftCutModal && lastClosedShift != null) {
        ShiftCutReceiptDialog(
            settings = settings,
            shift = lastClosedShift!!,
            canPrintThermalReceipt = permissions.canPrintThermalReceipt,
            onDismiss = { viewModel.closeShiftCutModal() }
        )
    }
}

@Composable
private fun CashRegisterDashboardCard(
    openShift: CashRegisterShift?,
    liveSummary: ShiftLiveSummary?,
    currencySymbol: String,
    canManageRegister: Boolean,
    onOpenRegister: () -> Unit,
    onCashMovement: () -> Unit,
    onCloseRegister: () -> Unit,
    onViewHistory: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (openShift != null) BrandNavyElevated else SurfaceWhite,
        border = if (openShift != null) null else BorderStroke(1.dp, SurfaceHover),
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("cash_register_status_card")
    ) {
        if (openShift == null || liveSummary == null) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandAmberLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = BrandAmberDark)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Caja Cerrada",
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextDark
                            )
                            Text(
                                text = "Abre tu turno con fondo inicial para cobrar ventas.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canManageRegister) {
                        Button(
                            onClick = onOpenRegister,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .heightIn(min = 48.dp)
                                .testTag("open_cash_register_btn")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir Caja", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = onViewHistory,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cash_shifts_history_btn")
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = BrandNavy, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cortes", color = BrandNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            val timeStr = remember(openShift.openedAt) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(openShift.openedAt))
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandEmerald.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "CAJA ABIERTA • #$timeStr",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmeraldLight
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = openShift.cashierName,
                            fontSize = 12.sp,
                            color = TextLightSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Fondo Inicial", fontSize = 11.sp, color = TextLightSecondary)
                            Text(
                                text = "$currencySymbol${String.format(Locale.US, "%.2f", openShift.openingFloat)}",
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Ventas Turno (${liveSummary.salesCount})", fontSize = 11.sp, color = TextLightSecondary)
                            Text(
                                text = "$currencySymbol${String.format(Locale.US, "%.2f", liveSummary.totalGrossSales)}",
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BrandEmeraldLight
                            )
                        }
                    }
                }

                if (canManageRegister) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCashMovement,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("cash_movement_btn")
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Entr / Ret", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        Button(
                            onClick = onCloseRegister,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAmber),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 48.dp)
                                .testTag("close_cash_register_btn")
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = null, tint = BrandNavy, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Corte / Cerrar", color = BrandNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernSaleItemCard(
    sale: Sale,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = sdf.format(Date(sale.date))

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, SurfaceHover),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sale_card_${sale.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sale.folio,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (sale.status) {
                            SaleStatus.PAGADA -> BrandEmeraldLight
                            SaleStatus.CREDITO -> BrandAmberLight
                            SaleStatus.CANCELADA -> StatusCancelled.copy(alpha = 0.12f)
                            SaleStatus.PENDIENTE -> BrandBlueLight
                        }
                    ) {
                        Text(
                            text = sale.status.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (sale.status) {
                                SaleStatus.PAGADA -> BrandEmeraldDark
                                SaleStatus.CREDITO -> BrandAmberDark
                                SaleStatus.CANCELADA -> StatusCancelled
                                SaleStatus.PENDIENTE -> BrandBlue
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Cliente: ${sale.customerName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$dateStr • ${sale.paymentMethod} (${sale.sellerName})",
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currencySymbol${String.format(Locale.US, "%.2f", sale.total)}",
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (sale.status == SaleStatus.CANCELADA) TextMuted else BrandEmeraldDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceSubtle,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Ver Ticket", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextMedium)
                    }
                }
            }
        }
    }
}
