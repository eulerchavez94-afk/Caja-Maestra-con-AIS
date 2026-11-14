package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.InventoryItem
import com.example.data.model.InventoryMovement
import com.example.data.model.InventoryMovementType
import com.example.data.model.ItemType
import com.example.data.model.UserRole
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.CajaMaestraEmptyState
import com.example.ui.components.CajaMaestraTopBar
import com.example.ui.components.SkeletonListPlaceholder
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.RoleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InventoryScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onShowTutorial: () -> Unit
) {
    val context = LocalContext.current
    val items by viewModel.inventoryItems.collectAsState()
    val movements by viewModel.inventoryMovements.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val debouncedQuery by viewModel.debouncedSearchQuery.collectAsState()

    var selectedBottomTab by remember { mutableStateOf(ItemType.PRODUCTO) }
    var selectedFilterChip by remember { mutableStateOf("ALMACEN") } // ALMACEN, MERMA, CANTIDAD_BAJA
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<InventoryItem?>(null) }
    var itemToDelete by remember { mutableStateOf<InventoryItem?>(null) }
    var itemForHistory by remember { mutableStateOf<InventoryItem?>(null) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Filter items with debounce & derivedStateOf to prevent unnecessary re-renders on slow devices
    val filteredItems by remember(items, selectedBottomTab, selectedFilterChip, debouncedQuery) {
        derivedStateOf {
            items.filter { item ->
                val matchesType = item.type == selectedBottomTab
                val matchesChip = when (selectedFilterChip) {
                    "MERMA" -> item.isWaste
                    "CANTIDAD_BAJA" -> item.stock <= item.minStock && !item.isWaste
                    else -> !item.isWaste
                }
                val matchesSearch = debouncedQuery.isBlank() ||
                        item.name.contains(debouncedQuery, ignoreCase = true) ||
                        item.code.contains(debouncedQuery, ignoreCase = true) ||
                        item.category.contains(debouncedQuery, ignoreCase = true)

                matchesType && matchesChip && matchesSearch
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CajaMaestraTopBar(
                    title = "Inventario",
                    subtitle = when (selectedFilterChip) {
                        "MERMA" -> "Mermas y Desperdicios (${filteredItems.size})"
                        "CANTIDAD_BAJA" -> "Existencias Bajas (${filteredItems.size})"
                        else -> "Productos Activos (${filteredItems.size})"
                    },
                    currentRole = settings.currentRole,
                    onOpenDrawer = onOpenDrawer,
                    searchQuery = searchQuery,
                    isSearchVisible = isSearchExpanded,
                    searchPlaceholder = "Buscar por nombre, código o categoría...",
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onToggleSearch = { isSearchExpanded = !isSearchExpanded },
                    actions = {
                        IconButton(onClick = { showBarcodeScanner = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Escanear",
                                tint = BrandAmber
                            )
                        }
                    }
                )

                // High-Quality Filter Chips Row
                Surface(
                    color = BrandNavyElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            ModernFilterPill(
                                icon = Icons.Default.Store,
                                label = "Almacén",
                                isSelected = selectedFilterChip == "ALMACEN",
                                badgeCount = items.count { it.type == selectedBottomTab && !it.isWaste },
                                onClick = { selectedFilterChip = "ALMACEN" }
                            )
                        }
                        item {
                            ModernFilterPill(
                                icon = Icons.Default.WarningAmber,
                                label = "Cantidad Baja",
                                isSelected = selectedFilterChip == "CANTIDAD_BAJA",
                                badgeCount = items.count { it.type == selectedBottomTab && it.stock <= it.minStock && !it.isWaste },
                                activeColor = BrandAmber,
                                onClick = { selectedFilterChip = "CANTIDAD_BAJA" }
                            )
                        }
                        item {
                            ModernFilterPill(
                                icon = Icons.Default.DeleteSweep,
                                label = "Mermas",
                                isSelected = selectedFilterChip == "MERMA",
                                badgeCount = items.count { it.type == selectedBottomTab && it.isWaste },
                                activeColor = StatusCancelled,
                                onClick = { selectedFilterChip = "MERMA" }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Fresh Bottom Navigation Tabs with clear active indicators
            NavigationBar(
                containerColor = BrandNavy,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedBottomTab == ItemType.PRODUCTO,
                    onClick = { selectedBottomTab = ItemType.PRODUCTO },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("Productos", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandNavy,
                        selectedTextColor = Color.White,
                        indicatorColor = BrandEmerald,
                        unselectedIconColor = TextLightSecondary,
                        unselectedTextColor = TextLightSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedBottomTab == ItemType.MANUFACTURA,
                    onClick = { selectedBottomTab = ItemType.MANUFACTURA },
                    icon = { Icon(Icons.Default.PrecisionManufacturing, contentDescription = null) },
                    label = { Text("Manufactura", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandNavy,
                        selectedTextColor = Color.White,
                        indicatorColor = BrandAmber,
                        unselectedIconColor = TextLightSecondary,
                        unselectedTextColor = TextLightSecondary
                    )
                )
                NavigationBarItem(
                    selected = selectedBottomTab == ItemType.SERVICIO,
                    onClick = { selectedBottomTab = ItemType.SERVICIO },
                    icon = { Icon(Icons.Default.HomeRepairService, contentDescription = null) },
                    label = { Text("Servicios", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandNavy,
                        selectedTextColor = Color.White,
                        indicatorColor = BrandBlue,
                        unselectedIconColor = TextLightSecondary,
                        unselectedTextColor = TextLightSecondary
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    itemToEdit = null
                    showAddEditDialog = true
                },
                containerColor = BrandEmerald,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_inventory_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar artículo", modifier = Modifier.size(28.dp))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CanvasLight)
        ) {
            var isInitialLoading by remember { mutableStateOf(items.isEmpty()) }
            LaunchedEffect(items) {
                isInitialLoading = false
            }

            if (isInitialLoading) {
                SkeletonListPlaceholder(count = 6)
            } else if (filteredItems.isEmpty()) {
                CajaMaestraEmptyState(
                    icon = when (selectedBottomTab) {
                        ItemType.PRODUCTO -> Icons.Default.Inventory2
                        ItemType.MANUFACTURA -> Icons.Default.PrecisionManufacturing
                        ItemType.SERVICIO -> Icons.Default.HomeRepairService
                    },
                    title = if (searchQuery.isNotBlank()) "Sin coincidencias" else "¡Tu Inventario está Listo!",
                    description = if (searchQuery.isNotBlank())
                        "No se encontraron resultados para '$searchQuery'."
                    else
                        "Aún no tienes ${selectedBottomTab.name.lowercase()}s registrados. Puedes agregar uno nuevo o escanear un código de barras.",
                    actionButtonText = if (searchQuery.isNotBlank())
                        "Limpiar Búsqueda"
                    else
                        "+ Registrar Nuevo ${selectedBottomTab.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    onActionClick = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.setSearchQuery("")
                        } else {
                            itemToEdit = null
                            showAddEditDialog = true
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
                    items(filteredItems, key = { it.id }) { item ->
                        ModernInventoryItemCard(
                            item = item,
                            currencySymbol = settings.currencySymbol,
                            canDelete = permissions.canDeleteInventoryItem,
                            canViewCost = permissions.canViewCostPrice,
                            onEdit = {
                                itemToEdit = item
                                showAddEditDialog = true
                            },
                            onDelete = { itemToDelete = item },
                            onViewHistory = { itemForHistory = item },
                            onStockAdjust = { delta -> viewModel.adjustItemStock(item, delta) }
                        )
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
                viewModel.setSearchQuery(scannedCode)
                isSearchExpanded = true
                val exists = items.any { it.code.equals(scannedCode, ignoreCase = true) }
                if (!exists) {
                    itemToEdit = InventoryItem(
                        name = "",
                        code = scannedCode,
                        costPrice = 0.0,
                        salePrice = 0.0,
                        stock = 1.0,
                        type = selectedBottomTab
                    )
                    showAddEditDialog = true
                }
            }
        )
    }

    // Add / Edit Inventory Item Dialog
    if (showAddEditDialog) {
        AddEditItemDialog(
            initialItem = itemToEdit,
            defaultType = selectedBottomTab,
            canViewCost = permissions.canViewCostPrice,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedItem ->
                viewModel.saveInventoryItem(savedItem)
                showAddEditDialog = false
            },
            onScanBarcode = {
                showBarcodeScanner = true
            }
        )
    }

    // Delete confirmation dialog
    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Eliminar Artículo", fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas eliminar '${target.name}' (${target.code}) del inventario?", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInventoryItem(target)
                        itemToDelete = null
                        Toast.makeText(context, "Artículo eliminado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                ) {
                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // Inventory Movement History & Reasoned Stock Adjustment Dialog
    itemForHistory?.let { selected ->
        val liveItem = items.firstOrNull { it.id == selected.id } ?: selected
        val itemMovements = remember(movements, liveItem.id) {
            movements.filter { it.itemId == liveItem.id }
        }
        InventoryHistoryAndAdjustDialog(
            item = liveItem,
            movements = itemMovements,
            onDismiss = { itemForHistory = null },
            onAdjustWithReason = { delta, reason ->
                viewModel.adjustItemStock(liveItem, delta, reason)
                Toast.makeText(
                    context,
                    "Existencia actualizada (${if (delta >= 0) "+" else ""}$delta ${liveItem.unit})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun ModernFilterPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    badgeCount: Int = 0,
    activeColor: Color = BrandEmerald,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) activeColor else Color.White.copy(alpha = 0.12f),
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)) else null,
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = Color.White
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernInventoryItemCard(
    item: InventoryItem,
    currencySymbol: String,
    canDelete: Boolean = true,
    canViewCost: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewHistory: () -> Unit,
    onStockAdjust: (Double) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category icon badge
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (item.type) {
                                    ItemType.PRODUCTO -> BrandBlueLight
                                    ItemType.MANUFACTURA -> BrandAmberLight
                                    ItemType.SERVICIO -> BrandEmeraldLight
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.type) {
                                ItemType.PRODUCTO -> Icons.Default.Inventory2
                                ItemType.MANUFACTURA -> Icons.Default.PrecisionManufacturing
                                ItemType.SERVICIO -> Icons.Default.HomeRepairService
                            },
                            contentDescription = null,
                            tint = when (item.type) {
                                ItemType.PRODUCTO -> BrandBlue
                                ItemType.MANUFACTURA -> BrandAmberDark
                                ItemType.SERVICIO -> BrandEmeraldDark
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextDark,
                            fontFamily = BodyFontFamily
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceSubtle,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover)
                            ) {
                                Text(
                                    text = item.code,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BrandBlueLight
                            ) {
                                Text(
                                    text = item.category,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (item.isRawMaterial) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BrandAmberLight
                                ) {
                                    Text(
                                        text = "Insumo",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandAmberDark,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // History, Edit and Delete Buttons (Delete only for Jefe / Admin)
                Row {
                    IconButton(onClick = onViewHistory, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.History, contentDescription = "Historial de Stock", tint = BrandBlue, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = BrandNavy, modifier = Modifier.size(18.dp))
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = StatusCancelled, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceHover)
            Spacer(modifier = Modifier.height(10.dp))

            // Pricing and Stock Controls (Hide confidential Cost from Trabajador)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "Precio: ",
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontFamily = BodyFontFamily
                        )
                        Text(
                            text = "$currencySymbol${String.format(java.util.Locale.US, "%.2f", item.salePrice)}",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = BrandEmeraldDark
                        )
                    }
                    if (canViewCost) {
                        Text(
                            text = "Costo: $currencySymbol${String.format(java.util.Locale.US, "%.2f", item.costPrice)}",
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontFamily = BodyFontFamily
                        )
                    }
                }

                // High-visibility Stock Status & Quick Adjustment
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isLow = item.stock <= item.minStock && item.type == ItemType.PRODUCTO
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            item.isWaste -> StatusCancelled.copy(alpha = 0.12f)
                            isLow -> BrandAmber.copy(alpha = 0.15f)
                            else -> BrandEmeraldLight
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                item.isWaste -> StatusCancelled.copy(alpha = 0.3f)
                                isLow -> BrandAmber.copy(alpha = 0.4f)
                                else -> BrandEmerald.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Text(
                            text = "${item.stock} ${item.unit}",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = when {
                                item.isWaste -> StatusCancelled
                                isLow -> BrandAmberDark
                                else -> BrandEmeraldDark
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (item.type != ItemType.SERVICIO) {
                        Surface(
                            shape = CircleShape,
                            color = SurfaceSubtle,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onStockAdjust(-1.0) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Remove, contentDescription = "-1", tint = TextMedium, modifier = Modifier.size(16.dp))
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = BrandNavy,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onStockAdjust(1.0) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "+1", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditItemDialog(
    initialItem: InventoryItem?,
    defaultType: ItemType,
    canViewCost: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (InventoryItem) -> Unit,
    onScanBarcode: () -> Unit
) {
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var code by remember { mutableStateOf(initialItem?.code ?: "") }
    var costPrice by remember { mutableStateOf(initialItem?.costPrice?.toString() ?: "") }
    var salePrice by remember { mutableStateOf(initialItem?.salePrice?.toString() ?: "") }
    var stock by remember { mutableStateOf(initialItem?.stock?.toString() ?: "10") }
    var minStock by remember { mutableStateOf(initialItem?.minStock?.toString() ?: "5") }
    var category by remember { mutableStateOf(initialItem?.category ?: "General") }
    var unit by remember { mutableStateOf(initialItem?.unit ?: "Pza") }
    var type by remember { mutableStateOf(initialItem?.type ?: defaultType) }
    var isWaste by remember { mutableStateOf(initialItem?.isWaste ?: false) }
    var isRawMaterial by remember { mutableStateOf(initialItem?.isRawMaterial ?: false) }
    var notes by remember { mutableStateOf(initialItem?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
                .testTag("add_edit_item_dialog")
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
                            text = if (initialItem == null || initialItem.id == 0L) "Nuevo Artículo" else "Editar Artículo",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = TextDark
                        )
                        Text(
                            text = "Completa los datos del producto",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable fields with keyboard adaptation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            validationError = null
                        },
                        label = { Text("Nombre del artículo *", fontWeight = FontWeight.SemiBold) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Código / Barcode") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BrandAmber,
                            modifier = Modifier
                                .size(52.dp)
                                .clickable(onClick = onScanBarcode)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear", tint = BrandNavy)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canViewCost) {
                            OutlinedTextField(
                                value = costPrice,
                                onValueChange = {
                                    costPrice = it
                                    validationError = null
                                },
                                label = { Text("Costo compra ($)") },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = salePrice,
                            onValueChange = {
                                salePrice = it
                                validationError = null
                            },
                            label = { Text("Precio venta ($) *") },
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (type != ItemType.SERVICIO) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = stock,
                                onValueChange = {
                                    stock = it
                                    validationError = null
                                },
                                label = { Text("Existencia") },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = minStock,
                                onValueChange = {
                                    minStock = it
                                    validationError = null
                                },
                                label = { Text("Mínimo Alerta") },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Categoría") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("Unidad (Pza/Kg)") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (type == ItemType.PRODUCTO && !isWaste) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceSubtle)
                                .clickable { isRawMaterial = !isRawMaterial }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isRawMaterial, onCheckedChange = { isRawMaterial = it })
                            Text("Marcar como Materia Prima / Insumo", fontSize = 13.sp, color = TextDark)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceSubtle)
                            .clickable {
                                isWaste = !isWaste
                                if (isWaste) isRawMaterial = false
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isWaste,
                            onCheckedChange = {
                                isWaste = it
                                if (it) isRawMaterial = false
                            }
                        )
                        Text("Marcar como Merma / Desperdicio", fontSize = 13.sp, color = TextDark)
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas / Ubicación (opcional)") },
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    validationError?.let { err ->
                        Text(err, color = StatusCancelled, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedCost = if (!canViewCost) {
                                initialItem?.costPrice ?: 0.0
                            } else if (costPrice.isBlank()) {
                                0.0
                            } else {
                                costPrice.toDoubleOrNull()
                            }
                            val parsedSale = if (salePrice.isBlank()) 0.0 else salePrice.toDoubleOrNull()
                            val parsedStock = if (type == ItemType.SERVICIO || stock.isBlank()) 0.0 else stock.toDoubleOrNull()
                            val parsedMin = if (type == ItemType.SERVICIO || minStock.isBlank()) 0.0 else minStock.toDoubleOrNull()

                            when {
                                name.isBlank() -> {
                                    validationError = "El nombre del artículo es obligatorio."
                                }
                                parsedCost == null || parsedCost < 0.0 ||
                                    parsedSale == null || parsedSale < 0.0 ||
                                    parsedStock == null || parsedStock < 0.0 ||
                                    parsedMin == null || parsedMin < 0.0 -> {
                                    validationError = "Costo, precio, existencia y mínimo deben ser números válidos >= 0."
                                }
                                else -> {
                                    onSave(
                                        (initialItem ?: InventoryItem(
                                            name = name,
                                            code = code,
                                            costPrice = 0.0,
                                            salePrice = 0.0,
                                            stock = 0.0
                                        )).copy(
                                            name = name.trim(),
                                            code = code.trim().ifEmpty { "ART-${System.currentTimeMillis() % 10000}" },
                                            costPrice = parsedCost,
                                            salePrice = parsedSale,
                                            stock = parsedStock,
                                            minStock = parsedMin,
                                            category = category.trim().ifEmpty { "General" },
                                            unit = unit.trim().ifEmpty { "Pza" },
                                            type = type,
                                            isWaste = isWaste,
                                            isRawMaterial = type == ItemType.PRODUCTO && !isWaste && isRawMaterial,
                                            notes = notes.trim()
                                        )
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_inventory_item_button")
                    ) {
                        Text("Guardar Artículo", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryHistoryAndAdjustDialog(
    item: InventoryItem,
    movements: List<InventoryMovement>,
    onDismiss: () -> Unit,
    onAdjustWithReason: (delta: Double, reason: String) -> Unit
) {
    var deltaInput by remember { mutableStateOf("") }
    var isIncoming by remember { mutableStateOf(true) }
    var reasonInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
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
                        Text("Kardex / Movimientos", fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                        Text("${item.name} • Stock actual: ${item.stock} ${item.unit}", fontSize = 12.sp, color = TextMuted)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                if (item.type != ItemType.SERVICIO) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CanvasLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ajuste Rápido con Motivo", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandNavy)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = isIncoming,
                                    onClick = { isIncoming = true },
                                    label = { Text("+ Entrada", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandEmerald, selectedLabelColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !isIncoming,
                                    onClick = { isIncoming = false },
                                    label = { Text("- Salida / Merma", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StatusCancelled, selectedLabelColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = deltaInput,
                                    onValueChange = {
                                        deltaInput = it
                                        errorMsg = null
                                    },
                                    label = { Text("Cantidad (${item.unit})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = reasonInput,
                                    onValueChange = {
                                        reasonInput = it
                                        errorMsg = null
                                    },
                                    label = { Text("Motivo") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f)
                                )
                            }
                            errorMsg?.let { err ->
                                Text(err, color = StatusCancelled, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    val qty = deltaInput.toDoubleOrNull()
                                    if (qty == null || !qty.isFinite() || qty <= 0.0) {
                                        errorMsg = "Ingresa una cantidad mayor a cero."
                                    } else {
                                        val signed = if (isIncoming) qty else -qty
                                        val finalReason = reasonInput.trim().ifBlank {
                                            if (isIncoming) "Ajuste entrada manual" else "Ajuste salida manual"
                                        }
                                        onAdjustWithReason(signed, finalReason)
                                        deltaInput = ""
                                        reasonInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Aplicar Ajuste al Stock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Historial Reciente (${movements.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                if (movements.isEmpty()) {
                    Text(
                        "Aún no hay movimientos registrados para este artículo.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(movements, key = { it.id }) { mov ->
                            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(mov.date))
                            val sign = if (mov.quantityDelta >= 0) "+" else ""
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CanvasLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${mov.movementType.name.replace('_', ' ')} (${mov.referenceFolio})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = BrandNavy
                                        )
                                        if (mov.reason.isNotBlank()) {
                                            Text(mov.reason, fontSize = 11.sp, color = TextDark)
                                        }
                                        Text(
                                            "$dateStr • ${mov.previousStock} -> ${mov.newStock} ${item.unit} • ${mov.performedBy}",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$sign${String.format(Locale.US, "%.2f", mov.quantityDelta)}",
                                        fontFamily = DisplayFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (mov.quantityDelta >= 0) BrandEmeraldDark else StatusCancelled
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
