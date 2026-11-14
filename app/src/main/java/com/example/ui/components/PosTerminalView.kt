package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SaleCheckoutResult
import com.example.util.PosSearchEngine
import kotlinx.coroutines.launch
import java.util.Locale

private enum class PosCatalogFilter(val label: String) {
    TODOS("Todos"),
    MAS_VENDIDOS("Más Vendidos"),
    PRODUCTOS("Productos"),
    MANUFACTURA("Manufactura"),
    SERVICIOS("Servicios")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosTerminalView(
    viewModel: MainViewModel,
    openShift: CashRegisterShift?,
    onOpenCashRegisterRequested: () -> Unit,
    onOpenCashMovementsRequested: () -> Unit,
    onExitPos: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val sales by viewModel.sales.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val cartItems by viewModel.cartItems.collectAsState()
    val currentSaleType by viewModel.currentSaleType.collectAsState()
    val currentPOSInterface by viewModel.currentPOSInterface.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val paymentMethod by viewModel.paymentMethod.collectAsState()
    val customerPaidInput by viewModel.customerPaidInput.collectAsState()
    val discountInput by viewModel.discountInput.collectAsState()
    val posNotes by viewModel.posNotes.collectAsState()
    val isProcessingSale by viewModel.isProcessingSale.collectAsState()
    val activeCheckoutOpId by viewModel.activeCheckoutOperationId.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var posSearchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PosCatalogFilter.TODOS) }
    var isCartExpanded by remember { mutableStateOf(true) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var customerSearchQuery by remember { mutableStateOf("") }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var checkoutWarning by remember { mutableStateOf<String?>(null) }

    // Pre-build normalized search index once when inventory changes -> sub-millisecond keystroke search
    val searchIndex = remember(inventoryItems) {
        PosSearchEngine.buildIndex(inventoryItems)
    }

    val topSellingItems = remember(inventoryItems, sales) {
        PosSearchEngine.computeTopSellingItems(inventoryItems, sales, limit = 8)
    }

    val cartQtyById = remember(cartItems) {
        cartItems.associate { it.itemId to it.quantity }
    }

    val displayedCatalog by remember(searchIndex, posSearchQuery, selectedFilter, topSellingItems) {
        derivedStateOf {
            if (posSearchQuery.isNotBlank()) {
                val typeFilter = when (selectedFilter) {
                    PosCatalogFilter.PRODUCTOS -> ItemType.PRODUCTO
                    PosCatalogFilter.MANUFACTURA -> ItemType.MANUFACTURA
                    PosCatalogFilter.SERVICIOS -> ItemType.SERVICIO
                    else -> null
                }
                PosSearchEngine.search(searchIndex, posSearchQuery, typeFilter)
            } else {
                when (selectedFilter) {
                    PosCatalogFilter.TODOS -> PosSearchEngine.search(searchIndex, "", null)
                    PosCatalogFilter.MAS_VENDIDOS -> topSellingItems
                    PosCatalogFilter.PRODUCTOS -> PosSearchEngine.search(searchIndex, "", ItemType.PRODUCTO)
                    PosCatalogFilter.MANUFACTURA -> PosSearchEngine.search(searchIndex, "", ItemType.MANUFACTURA)
                    PosCatalogFilter.SERVICIOS -> PosSearchEngine.search(searchIndex, "", ItemType.SERVICIO)
                }
            }
        }
    }

    val subtotal = cartItems.sumOf { it.subtotal }
    val discount = (discountInput.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)
    val total = (subtotal - discount).coerceAtLeast(0.0)
    val paid = if (customerPaidInput.isBlank()) total else (customerPaidInput.toDoubleOrNull() ?: 0.0)
    val change = (paid - total).coerceAtLeast(0.0)
    val missingCash = if (customerPaidInput.isNotBlank() && paid < total) (total - paid) else 0.0
    val smartBillSuggestions = remember(total) { PosSearchEngine.suggestCashBills(total) }

    fun tryAddItem(item: InventoryItem) {
        val added = viewModel.addToCart(item, 1.0)
        if (added) {
            checkoutWarning = null
            Toast.makeText(context, "+1 ${item.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Stock insuficiente para ${item.name} (disp: ${item.stock} ${item.unit})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun handleSearchSubmit() {
        val clean = posSearchQuery.trim()
        if (clean.isEmpty()) return
        val exactBarcode = PosSearchEngine.findExactBarcodeMatch(inventoryItems, clean)
        if (exactBarcode != null) {
            tryAddItem(exactBarcode)
            posSearchQuery = ""
            keyboardController?.hide()
            return
        }
        if (displayedCatalog.size == 1) {
            tryAddItem(displayedCatalog.first())
            posSearchQuery = ""
            keyboardController?.hide()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HudGradient)
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (currentSaleType == SaleType.VENTA) "Punto de Venta" else "Nueva Cotización",
                                fontFamily = DisplayFontFamily,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (cartItems.isEmpty()) "Busca, escanea o toca un artículo"
                                else "${cartItems.sumOf { it.quantity }.toInt()} arts. • ${settings.currencySymbol}${String.format(Locale.US, "%.2f", total)}",
                                fontSize = 11.sp,
                                color = BrandEmeraldLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onExitPos) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                        }
                    },
                    actions = {
                        // Cash shift live badge inside POS
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (openShift != null) BrandEmerald.copy(alpha = 0.22f) else BrandAmber.copy(alpha = 0.22f),
                            border = BorderStroke(
                                1.dp,
                                if (openShift != null) BrandEmerald else BrandAmber
                            ),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable {
                                    if (openShift != null) onOpenCashMovementsRequested() else onOpenCashRegisterRequested()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(if (openShift != null) BrandEmerald else BrandAmber, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (openShift != null) "Caja #${openShift.id}" else "Abrir Caja",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (cartItems.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearCart() }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Vaciar carrito", tint = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                // Customer Selector Ribbon
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomerPicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cliente: ${selectedCustomer?.name ?: "Público General"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Cambiar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandAmber
                        )
                    }
                }

                // Always-visible Ultra-Fast Search Bar (for fast queue checkout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = posSearchQuery,
                        onValueChange = { newVal ->
                            posSearchQuery = newVal
                            // Auto-add immediately on exact barcode scan ending in newline or full scanner input
                            if (settings.externalScanner && newVal.endsWith("\n")) {
                                posSearchQuery = newVal.trim()
                                handleSearchSubmit()
                            }
                        },
                        placeholder = {
                            Text(
                                "Buscar nombre, código o escanear...",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = BrandEmerald)
                        },
                        trailingIcon = {
                            if (posSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { posSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = TextMedium)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { handleSearchSubmit() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = TextDark,
                            unfocusedTextColor = TextDark,
                            focusedBorderColor = BrandEmerald,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pos_instant_search_input")
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandAmber,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { showBarcodeScanner = true }
                            .testTag("pos_scan_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear Código", tint = BrandNavy, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                if (cartItems.isNotEmpty()) {
                    Surface(
                        color = BrandNavyElevated,
                        contentColor = Color.White,
                        shadowElevation = 10.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (checkoutWarning != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = TileSalmon.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, TileSalmon),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = checkoutWarning!!,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Payment methods (only for VENTA)
                            if (currentSaleType == SaleType.VENTA) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Efectivo", "Tarjeta", "Transferencia", "Crédito").forEach { method ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (paymentMethod == method) BrandAmber else Color.White.copy(alpha = 0.12f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    viewModel.setPaymentMethod(method)
                                                    checkoutWarning = null
                                                    if (method == "Crédito" && selectedCustomer == null) {
                                                        showCustomerPicker = true
                                                    }
                                                }
                                        ) {
                                            Text(
                                                text = method,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (paymentMethod == method) BrandNavy else Color.White,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Fast Cash Calculator + 1-tap Bill Suggestions
                                if (paymentMethod == "Efectivo" && settings.showCustomerPayAndChange) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customerPaidInput,
                                            onValueChange = {
                                                viewModel.setCustomerPaidInput(it)
                                                checkoutWarning = null
                                            },
                                            label = { Text("Paga con (${settings.currencySymbol})", fontSize = 11.sp) },
                                            placeholder = { Text(String.format(Locale.US, "%.2f", total), fontSize = 12.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = BrandEmerald,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                                focusedLabelColor = BrandEmerald,
                                                unfocusedLabelColor = TextLightSecondary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (missingCash > 0.01) TileSalmon.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (missingCash > 0.01) TileSalmon else Color.White.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = if (missingCash > 0.01) "Falta por pagar" else "Cambio a entregar",
                                                    fontSize = 10.sp,
                                                    color = if (missingCash > 0.01) Color(0xFFFFB4AB) else TextLightSecondary
                                                )
                                                Text(
                                                    text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", if (missingCash > 0.01) missingCash else change)}",
                                                    fontFamily = DisplayFontFamily,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (missingCash > 0.01) Color(0xFFFFB4AB) else BrandAmber
                                                )
                                            }
                                        }
                                    }

                                    // Smart 1-Tap Cash Bills for Fast Queue Checkout
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = BrandEmerald.copy(alpha = 0.25f),
                                                border = BorderStroke(1.dp, BrandEmerald),
                                                modifier = Modifier.clickable {
                                                    viewModel.setCustomerPaidInput(String.format(Locale.US, "%.2f", total))
                                                    checkoutWarning = null
                                                }
                                            ) {
                                                Text(
                                                    text = "Exacto",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandEmeraldLight,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                        items(smartBillSuggestions) { bill ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White.copy(alpha = 0.12f),
                                                modifier = Modifier.clickable {
                                                    viewModel.setCustomerPaidInput(String.format(Locale.US, "%.0f", bill))
                                                    checkoutWarning = null
                                                }
                                            ) {
                                                Text(
                                                    text = "${settings.currencySymbol}${bill.toInt()}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Total & Primary Charge Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (discount > 0) "TOTAL (Desc: -${settings.currencySymbol}${String.format(Locale.US, "%.2f", discount)})" else "TOTAL A COBRAR",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLightSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", total)}",
                                        fontFamily = DisplayFontFamily,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandEmerald
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (isProcessingSale) return@Button
                                        coroutineScope.launch {
                                            when (val result = viewModel.completeSaleAsync(activeCheckoutOpId)) {
                                                SaleCheckoutResult.Success -> {
                                                    checkoutWarning = null
                                                    onExitPos()
                                                }
                                                SaleCheckoutResult.AlreadyProcessing -> Unit
                                                SaleCheckoutResult.ShiftNotOpen -> {
                                                    checkoutWarning = "Abre la caja antes de cobrar una venta."
                                                    onOpenCashRegisterRequested()
                                                }
                                                SaleCheckoutResult.ClientRequiredForCredit -> {
                                                    checkoutWarning = "Selecciona un cliente registrado para venta a Crédito."
                                                    showCustomerPicker = true
                                                }
                                                is SaleCheckoutResult.InsufficientCash -> {
                                                    checkoutWarning = "Faltan ${settings.currencySymbol}${String.format(Locale.US, "%.2f", result.missing)} para cubrir el total."
                                                }
                                                is SaleCheckoutResult.InsufficientStock -> {
                                                    checkoutWarning = "Stock insuficiente para ${result.itemName} (disp: ${result.available}, solic: ${result.requested})."
                                                }
                                                SaleCheckoutResult.EmptyCart -> Unit
                                                SaleCheckoutResult.Unauthorized -> {
                                                    checkoutWarning = "Tu rol actual no tiene permisos para registrar ventas."
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessingSale && cartItems.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("complete_sale_btn")
                                ) {
                                    if (isProcessingSale) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Procesando...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (currentSaleType == SaleType.VENTA) "Cobrar Venta" else "Guardar Cotización",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CanvasLight)
        ) {
            // Queue Filter Pills + View Switcher (Grid vs List)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PosCatalogFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, SurfaceHover),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { viewModel.setPOSInterface(if (currentPOSInterface == 1) 2 else 1) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (currentPOSInterface == 1) Icons.Default.FormatListBulleted else Icons.Default.GridView,
                            contentDescription = "Cambiar vista de catálogo",
                            tint = BrandNavy,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 1-Tap Most Sold Strip (when browsing catalog without typing query)
            if (posSearchQuery.isBlank() && selectedFilter == PosCatalogFilter.TODOS && topSellingItems.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandAmberLight,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = BrandAmberDark, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Rápidos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandAmberDark)
                            }
                        }
                    }
                    items(topSellingItems.take(5), key = { "top_${it.id}" }) { fastItem ->
                        val outOfStock = settings.checkStock && fastItem.type != ItemType.SERVICIO && fastItem.stock <= 0.0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, if (outOfStock) SurfaceHover else BrandEmerald.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .alpha(if (outOfStock) 0.5f else 1f)
                                .clickable(enabled = !outOfStock) { tryAddItem(fastItem) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fastItem.name.take(18),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${settings.currencySymbol}${String.format(Locale.US, "%.0f", fastItem.salePrice)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmeraldDark
                                )
                            }
                        }
                    }
                }
            }

            // Active Cart Card (Collapsible so it never blocks searching)
            if (cartItems.isNotEmpty()) {
                Surface(
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, SurfaceHover),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isCartExpanded = !isCartExpanded }
                            ) {
                                Icon(
                                    imageVector = if (isCartExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expandir o contraer carrito",
                                    tint = BrandNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ticket (${cartItems.size} líneas)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextDark
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { showDiscountDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = BrandAmberDark, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (discount > 0) "Desc: -${settings.currencySymbol}${String.format(Locale.US, "%.2f", discount)}" else "Descuento / Nota",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandAmberDark
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isCartExpanded) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .heightIn(max = 140.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(cartItems, key = { it.itemId }) { cartItem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cartItem.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = TextDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", cartItem.unitPrice)} / ${cartItem.unit}",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = SurfaceSubtle,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clickable {
                                                        viewModel.updateCartItemQuantity(cartItem.itemId, cartItem.quantity - 1.0)
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Remove, contentDescription = "-1", tint = TextMedium, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                            Text(
                                                text = if (cartItem.quantity % 1.0 == 0.0) "${cartItem.quantity.toInt()}" else String.format(Locale.US, "%.1f", cartItem.quantity),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                            Surface(
                                                shape = CircleShape,
                                                color = BrandEmerald,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clickable {
                                                        val ok = viewModel.updateCartItemQuantity(cartItem.itemId, cartItem.quantity + 1.0)
                                                        if (!ok) {
                                                            Toast.makeText(context, "Stock máximo alcanzado para ${cartItem.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Add, contentDescription = "+1", tint = Color.White, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", cartItem.subtotal)}",
                                                fontFamily = DisplayFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = TextDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Catalog Results (Grid or Compact List)
            if (displayedCatalog.isEmpty()) {
                CajaMaestraEmptyState(
                    icon = Icons.Default.SearchOff,
                    title = if (posSearchQuery.isNotBlank()) "Sin coincidencias para \"$posSearchQuery\"" else "Sin artículos en esta vista",
                    description = "Verifica el nombre o código, o limpia el filtro para ver todo el catálogo.",
                    actionButtonText = if (posSearchQuery.isNotBlank()) "Limpiar Búsqueda" else "",
                    onActionClick = {
                        posSearchQuery = ""
                        selectedFilter = PosCatalogFilter.TODOS
                    },
                    modifier = Modifier.weight(1f)
                )
            } else if (currentPOSInterface == 1) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayedCatalog, key = { it.id }) { item ->
                        val inCartQty = cartQtyById[item.id] ?: 0.0
                        val remainingStock = if (item.type == ItemType.SERVICIO) 999.0 else (item.stock - inCartQty).coerceAtLeast(0.0)
                        val isOutOfStock = settings.checkStock && item.type != ItemType.SERVICIO && remainingStock <= 0.0

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(
                                width = if (inCartQty > 0) 1.5.dp else 1.dp,
                                color = if (inCartQty > 0) BrandEmerald else SurfaceHover
                            ),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isOutOfStock) 0.55f else 1f)
                                .clickable { tryAddItem(item) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.code.ifBlank { item.type.name },
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (inCartQty > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = BrandEmeraldLight
                                        ) {
                                            Text(
                                                text = "x${inCartQty.toInt()}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandEmeraldDark,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = TextDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when {
                                        item.type == ItemType.SERVICIO -> "Servicio"
                                        isOutOfStock -> "AGOTADO"
                                        else -> "Disp: ${if (remainingStock % 1.0 == 0.0) remainingStock.toInt() else String.format(Locale.US, "%.1f", remainingStock)} ${item.unit}"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (isOutOfStock) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isOutOfStock -> StatusCancelled
                                        item.type != ItemType.SERVICIO && remainingStock <= item.minStock -> BrandAmberDark
                                        else -> TextMuted
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", item.salePrice)}",
                                        fontFamily = DisplayFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandEmeraldDark,
                                        fontSize = 15.sp
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isOutOfStock) TextMuted else BrandEmerald,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Add, contentDescription = "Agregar", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayedCatalog, key = { it.id }) { item ->
                        val inCartQty = cartQtyById[item.id] ?: 0.0
                        val remainingStock = if (item.type == ItemType.SERVICIO) 999.0 else (item.stock - inCartQty).coerceAtLeast(0.0)
                        val isOutOfStock = settings.checkStock && item.type != ItemType.SERVICIO && remainingStock <= 0.0

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(
                                width = if (inCartQty > 0) 1.5.dp else 1.dp,
                                color = if (inCartQty > 0) BrandEmerald else SurfaceHover
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isOutOfStock) 0.55f else 1f)
                                .clickable { tryAddItem(item) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Cód: ${item.code} • ${if (item.type == ItemType.SERVICIO) "Servicio" else if (isOutOfStock) "AGOTADO" else "Stock: $remainingStock ${item.unit}"}",
                                        fontSize = 12.sp,
                                        color = if (isOutOfStock) StatusCancelled else TextMuted
                                    )
                                }
                                if (inCartQty > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BrandEmeraldLight,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = "x${inCartQty.toInt()}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandEmeraldDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", item.salePrice)}",
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmeraldDark,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isOutOfStock) TextMuted else BrandBlue,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AddShoppingCart, contentDescription = "Agregar", tint = Color.White, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Barcode Scanner Modal
    if (showBarcodeScanner) {
        BarcodeScannerDialog(
            onDismiss = { showBarcodeScanner = false },
            onBarcodeScanned = { scannedCode ->
                val matched = PosSearchEngine.findExactBarcodeMatch(inventoryItems, scannedCode)
                if (matched != null) {
                    tryAddItem(matched)
                } else {
                    posSearchQuery = scannedCode
                    Toast.makeText(context, "Código $scannedCode no exacto; mostrando resultados", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Customer Picker Modal (with instant search!)
    if (showCustomerPicker) {
        val filteredClients = remember(clients, customerSearchQuery) {
            if (customerSearchQuery.isBlank()) clients
            else clients.filter {
                PosSearchEngine.normalize(it.name).contains(PosSearchEngine.normalize(customerSearchQuery)) ||
                    it.phone.contains(customerSearchQuery) ||
                    PosSearchEngine.normalize(it.company).contains(PosSearchEngine.normalize(customerSearchQuery))
            }
        }

        Dialog(
            onDismissRequest = { showCustomerPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Seleccionar Cliente", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                        IconButton(onClick = { showCustomerPicker = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                    OutlinedTextField(
                        value = customerSearchQuery,
                        onValueChange = { customerSearchQuery = it },
                        placeholder = { Text("Buscar cliente o teléfono...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        item {
                            ListItem(
                                headlineContent = { Text("Público General", fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("Venta de mostrador sin registro") },
                                leadingContent = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    viewModel.setSelectedCustomer(null)
                                    checkoutWarning = null
                                    showCustomerPicker = false
                                }
                            )
                            HorizontalDivider(color = SurfaceHover)
                        }
                        items(filteredClients, key = { it.id }) { client ->
                            ListItem(
                                headlineContent = { Text(client.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = {
                                    val info = listOfNotNull(
                                        client.company.takeIf { it.isNotBlank() },
                                        client.phone.takeIf { it.isNotBlank() },
                                        if (client.creditBalance > 0) "Saldo: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", client.creditBalance)}" else null
                                    ).joinToString(" • ")
                                    if (info.isNotBlank()) Text(info, fontSize = 12.sp)
                                },
                                leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = BrandEmeraldDark) },
                                modifier = Modifier.clickable {
                                    viewModel.setSelectedCustomer(client)
                                    checkoutWarning = null
                                    showCustomerPicker = false
                                }
                            )
                            HorizontalDivider(color = SurfaceHover)
                        }
                    }
                }
            }
        }
    }

    // Discount & Sale Notes Modal
    if (showDiscountDialog) {
        var tempDiscount by remember(discountInput) { mutableStateOf(if (discountInput == "0") "" else discountInput) }
        var tempNotes by remember(posNotes) { mutableStateOf(posNotes) }
        var discountError by remember { mutableStateOf<String?>(null) }

        Dialog(
            onDismissRequest = { showDiscountDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Descuento y Notas del Ticket", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempDiscount,
                        onValueChange = {
                            tempDiscount = it
                            discountError = null
                        },
                        label = { Text("Descuento (${settings.currencySymbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = discountError != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    discountError?.let { err ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(err, color = StatusCancelled, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempNotes,
                        onValueChange = { tempNotes = it },
                        label = { Text("Notas en recibo (opcional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDiscountDialog = false }) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = tempDiscount.trim()
                                val parsed = if (trimmed.isEmpty()) 0.0 else trimmed.toDoubleOrNull()
                                when {
                                    parsed == null || !parsed.isFinite() -> {
                                        discountError = "Ingresa un monto numérico válido."
                                    }
                                    parsed < 0.0 -> {
                                        discountError = "El descuento no puede ser negativo."
                                    }
                                    parsed > subtotal -> {
                                        discountError = "El descuento no puede superar el subtotal (${settings.currencySymbol}${String.format(Locale.US, "%.2f", subtotal)})."
                                    }
                                    else -> {
                                        viewModel.setDiscountInput(if (parsed == 0.0) "0" else String.format(Locale.US, "%.2f", parsed))
                                        viewModel.setPosNotes(tempNotes.trim())
                                        showDiscountDialog = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Aplicar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
