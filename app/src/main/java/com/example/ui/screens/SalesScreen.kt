package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.CajaMaestraEmptyState
import com.example.ui.components.CajaMaestraTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.RoleManager
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
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }
    val canMakeSales = permissions.canCreateSale

    val cartItems by viewModel.cartItems.collectAsState()
    val currentSaleType by viewModel.currentSaleType.collectAsState()
    val currentPOSInterface by viewModel.currentPOSInterface.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val paymentMethod by viewModel.paymentMethod.collectAsState()
    val customerPaidInput by viewModel.customerPaidInput.collectAsState()
    val discountInput by viewModel.discountInput.collectAsState()

    var isInNewSaleMode by remember { mutableStateOf(false) }
    var selectedCategoryTab by remember { mutableStateOf<ItemType?>(ItemType.PRODUCTO) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var salesSearchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val subtotal = cartItems.sumOf { it.subtotal }
    val discount = discountInput.toDoubleOrNull() ?: 0.0
    val total = (subtotal - discount).coerceAtLeast(0.0)
    val paid = customerPaidInput.toDoubleOrNull() ?: total
    val change = (paid - total).coerceAtLeast(0.0)

    val displayedSales by remember(sales, currentSaleType, salesSearchQuery) {
        derivedStateOf {
            sales.filter {
                it.type == currentSaleType &&
                        (salesSearchQuery.isBlank() ||
                                it.folio.contains(salesSearchQuery, ignoreCase = true) ||
                                it.customerName.contains(salesSearchQuery, ignoreCase = true) ||
                                it.paymentMethod.contains(salesSearchQuery, ignoreCase = true))
            }
        }
    }

    if (!isInNewSaleMode) {
        // --- SCREEN 4: Sales list & Quotations History ---
        Scaffold(
            topBar = {
                CajaMaestraTopBar(
                    title = if (currentSaleType == SaleType.VENTA) "Ventas (TPV)" else "Cotizaciones",
                    subtitle = "Últimos Movimientos (${displayedSales.size})",
                    currentRole = settings.currentRole,
                    onOpenDrawer = onOpenDrawer,
                    searchQuery = salesSearchQuery,
                    isSearchVisible = isSearchActive,
                    searchPlaceholder = "Buscar folio, cliente o método...",
                    onSearchQueryChange = { salesSearchQuery = it },
                    onToggleSearch = { isSearchActive = !isSearchActive },
                    actions = {
                        IconButton(onClick = {
                            val headers = listOf("Folio", "Cliente", "Total", "Método", "Fecha")
                            val rows = displayedSales.map {
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it.date))
                                listOf(it.folio, it.customerName, "${settings.currencySymbol}${it.total}", it.paymentMethod, dateStr)
                            }
                            com.example.util.ReportExporter.exportToExcel(
                                context = context,
                                reportTitle = "Ventas_Caja_Maestra",
                                headers = headers,
                                rows = rows
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
                    FloatingActionButton(
                        onClick = {
                            viewModel.clearCart()
                            isInNewSaleMode = true
                        },
                        containerColor = BrandEmerald,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("new_sale_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nueva Venta", modifier = Modifier.size(28.dp))
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(CanvasLight)
            ) {
                var isInitialLoading by remember { mutableStateOf(sales.isEmpty()) }
                LaunchedEffect(sales) {
                    if (sales.isNotEmpty()) {
                        isInitialLoading = false
                    }
                }

                if (isInitialLoading) {
                    com.example.ui.components.SkeletonListPlaceholder(count = 5)
                } else if (displayedSales.isEmpty()) {
                    CajaMaestraEmptyState(
                        icon = Icons.Default.PointOfSale,
                        title = if (currentSaleType == SaleType.VENTA) "Sin Ventas Registradas" else "Sin Cotizaciones",
                        description = if (!canMakeSales)
                            "El Dueño ha limitado la creación de ventas para el rol Trabajador."
                        else if (salesSearchQuery.isNotBlank())
                            "No se encontraron resultados para '$salesSearchQuery'."
                        else
                            "Realiza tu primera venta en tiempo real para generar tickets y actualizar inventario automáticamente.",
                        actionButtonText = if (canMakeSales) "+ Iniciar Nueva Venta" else "",
                        onActionClick = {
                            if (canMakeSales) {
                                viewModel.clearCart()
                                isInNewSaleMode = true
                            }
                        },
                        onTutorialClick = onShowTutorial
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedSales, key = { it.id }) { sale ->
                            ModernSaleItemCard(
                                sale = sale,
                                currencySymbol = settings.currencySymbol,
                                onClick = {
                                    viewModel.openSaleReceipt(sale)
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // --- SCREEN 5: POS Terminal / Nueva Venta ---
        Scaffold(
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().background(HudGradient)) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = if (currentSaleType == SaleType.VENTA) "Punto de Venta (TPV)" else "Nueva Cotización",
                                    fontFamily = DisplayFontFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = if (cartItems.isEmpty()) "Selecciona artículos del catálogo" else "${cartItems.sumOf { it.quantity }.toInt()} artículos en carrito",
                                    fontSize = 12.sp,
                                    color = BrandEmeraldLight
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { isInNewSaleMode = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                            }
                        },
                        actions = {
                            if (cartItems.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearCart() }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Limpiar carrito", tint = Color.White.copy(alpha = 0.8f))
                                }
                            }
                            IconButton(onClick = { showBarcodeScanner = true }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear", tint = BrandAmber)
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cliente: ${selectedCustomer?.name ?: "Público General"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
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
                }
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth().imePadding()) {
                    // Floating Cart & Checkout Panel if items exist
                    if (cartItems.isNotEmpty()) {
                        Surface(
                            color = BrandNavyElevated,
                            contentColor = Color.White,
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "TOTAL A COBRAR",
                                            fontSize = 11.sp,
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

                                    // Complete Sale Button
                                    Button(
                                        onClick = {
                                            viewModel.completeSale()
                                            isInNewSaleMode = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Cobrar Venta", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Payment method chips
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
                                                .clickable { viewModel.setPaymentMethod(method) }
                                        ) {
                                            Text(
                                                text = method,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (paymentMethod == method) BrandNavy else Color.White,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                // Cash calculator if Cash is chosen
                                if (paymentMethod == "Efectivo" && settings.showCustomerPayAndChange) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customerPaidInput,
                                            onValueChange = { viewModel.setCustomerPaidInput(it) },
                                            label = { Text("Paga con ($)", fontSize = 11.sp) },
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
                                            color = Color.White.copy(alpha = 0.1f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Cambio a entregar", fontSize = 10.sp, color = TextLightSecondary)
                                                Text(
                                                    text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", change)}",
                                                    fontFamily = DisplayFontFamily,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandAmber
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Navigation Color Tabs for POS Categories
                    Surface(
                        color = BrandNavy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                        ) {
                            POSModernTab(
                                title = "Productos",
                                icon = Icons.Default.Inventory2,
                                activeColor = BrandBlue,
                                isSelected = selectedCategoryTab == ItemType.PRODUCTO,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategoryTab = ItemType.PRODUCTO }
                            )
                            POSModernTab(
                                title = "Manufactura",
                                icon = Icons.Default.PrecisionManufacturing,
                                activeColor = BrandAmber,
                                isSelected = selectedCategoryTab == ItemType.MANUFACTURA,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategoryTab = ItemType.MANUFACTURA }
                            )
                            POSModernTab(
                                title = "Servicios",
                                icon = Icons.Default.HomeRepairService,
                                activeColor = BrandEmerald,
                                isSelected = selectedCategoryTab == ItemType.SERVICIO,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategoryTab = ItemType.SERVICIO }
                            )
                            POSModernTab(
                                title = "Scanner",
                                icon = Icons.Default.QrCodeScanner,
                                activeColor = TilePurple,
                                isSelected = false,
                                modifier = Modifier.weight(1f),
                                onClick = { showBarcodeScanner = true }
                            )
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
                // Segmented Pill: Interfaz I (Cuadrícula) vs Interfaz II (Lista)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceSubtle,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (currentPOSInterface == 1) SurfaceWhite else Color.Transparent,
                            shadowElevation = if (currentPOSInterface == 1) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setPOSInterface(1) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = if (currentPOSInterface == 1) BrandNavy else TextMedium,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Interfaz I (Cuadrícula)",
                                    fontSize = 12.sp,
                                    fontWeight = if (currentPOSInterface == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (currentPOSInterface == 1) BrandNavy else TextMedium
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (currentPOSInterface == 2) SurfaceWhite else Color.Transparent,
                            shadowElevation = if (currentPOSInterface == 2) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setPOSInterface(2) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FormatListBulleted,
                                    contentDescription = null,
                                    tint = if (currentPOSInterface == 2) BrandNavy else TextMedium,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Interfaz II (Lista)",
                                    fontSize = 12.sp,
                                    fontWeight = if (currentPOSInterface == 2) FontWeight.Bold else FontWeight.Medium,
                                    color = if (currentPOSInterface == 2) BrandNavy else TextMedium
                                )
                            }
                        }
                    }
                }

                // Current Cart preview list if items in cart
                if (cartItems.isNotEmpty()) {
                    Surface(
                        color = SurfaceWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Artículos en el ticket (${cartItems.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextDark
                                )
                                Text(
                                    text = "Subtotal: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", subtotal)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BrandEmeraldDark
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(cartItems) { cartItem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cartItem.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
                                            Text(
                                                "${cartItem.quantity} ${cartItem.unit} x ${settings.currencySymbol}${cartItem.unitPrice}",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = SurfaceSubtle,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable { viewModel.updateCartItemQuantity(cartItem.itemId, cartItem.quantity - 1.0) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Remove, contentDescription = "-1", tint = TextMedium, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Text(
                                                text = cartItem.quantity.toString(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                            Surface(
                                                shape = CircleShape,
                                                color = BrandEmerald,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable { viewModel.updateCartItemQuantity(cartItem.itemId, cartItem.quantity + 1.0) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Add, contentDescription = "+1", tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
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

                // Selected Tab Items Catalog to Pick from
                val categoryItems = inventoryItems.filter { it.type == selectedCategoryTab && !it.isWaste }

                if (categoryItems.isEmpty()) {
                    CajaMaestraEmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "Sin artículos en ${selectedCategoryTab?.name?.lowercase() ?: "esta categoría"}",
                        description = "Ve a la sección Inventario o pulsa Escanear para añadir artículos a tu catálogo.",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    if (currentPOSInterface == 1) {
                        // Interfaz I: Fresh Grid POS Cards
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(categoryItems) { item ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SurfaceWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addToCart(item, 1.0)
                                            Toast.makeText(context, "Agregado: ${item.name}", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 2,
                                            color = TextDark
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Stock: ${item.stock} ${item.unit}",
                                            fontSize = 11.sp,
                                            color = if (item.stock <= item.minStock) BrandAmberDark else TextMuted
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
                                                color = BrandEmerald,
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
                        // Interfaz II: Compact List POS
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(categoryItems) { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addToCart(item, 1.0)
                                            Toast.makeText(context, "Agregado: ${item.name}", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                            Text("Cód: ${item.code} | Stock: ${item.stock} ${item.unit}", fontSize = 12.sp, color = TextMuted)
                                        }
                                        Text(
                                            "${settings.currencySymbol}${String.format(Locale.US, "%.2f", item.salePrice)}",
                                            fontFamily = DisplayFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandEmeraldDark,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = BrandBlue,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.AddShoppingCart, contentDescription = "Agregar", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Barcode Scanner Modal in POS
    if (showBarcodeScanner) {
        BarcodeScannerDialog(
            onDismiss = { showBarcodeScanner = false },
            onBarcodeScanned = { scannedCode ->
                val matched = inventoryItems.find { it.code.equals(scannedCode, ignoreCase = true) }
                if (matched != null) {
                    viewModel.addToCart(matched, 1.0)
                    Toast.makeText(context, "¡Agregado!: ${matched.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Código $scannedCode no encontrado en catálogo", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // Customer Picker Modal
    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            modifier = Modifier.imePadding(),
            title = { Text("Seleccionar Cliente", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("Público General", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.clickable {
                                viewModel.setSelectedCustomer(null)
                                showCustomerPicker = false
                            }
                        )
                    }
                    items(clients) { client ->
                        ListItem(
                            headlineContent = { Text(client.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(client.company.ifBlank { client.phone }) },
                            modifier = Modifier.clickable {
                                viewModel.setSelectedCustomer(client)
                                showCustomerPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPicker = false }) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun POSModernTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) activeColor else Color.Transparent,
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .testTag("pos_tab_${title.lowercase()}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) Color.White else TextLightSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else TextLightSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ModernSaleItemCard(
    sale: Sale,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(sale.date))

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
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
                    color = TextMedium
                )
                Text(
                    text = "$dateStr • ${sale.paymentMethod} (Vendedor: ${sale.sellerName})",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currencySymbol${String.format(Locale.US, "%.2f", sale.total)}",
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BrandEmeraldDark
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
