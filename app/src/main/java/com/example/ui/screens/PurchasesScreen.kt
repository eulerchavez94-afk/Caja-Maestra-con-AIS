package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.InventoryItem
import com.example.data.model.ItemType
import com.example.data.model.Purchase
import com.example.data.model.Supplier
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PurchaseRegistrationResult
import com.example.util.RoleManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val purchases by viewModel.purchases.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isProcessingPurchase by viewModel.isProcessingPurchase.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val permissions = remember(settings) { RoleManager.getPermissions(settings) }
    var showNewPurchaseDialog by remember { mutableStateOf(false) }
    val canAddPurchase = permissions.canCreatePurchase

    Scaffold(
        topBar = {
            com.example.ui.components.CajaMaestraTopBar(
                title = "Compras",
                subtitle = "Reabastecimiento de Inventario (${purchases.size})",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer
            )
        },
        floatingActionButton = {
            if (canAddPurchase) {
                FloatingActionButton(
                    onClick = { showNewPurchaseDialog = true },
                    containerColor = BrandEmerald,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_purchase_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva Compra", modifier = Modifier.size(28.dp))
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
            if (purchases.isEmpty()) {
                com.example.ui.components.CajaMaestraEmptyState(
                    icon = Icons.Default.ShoppingBag,
                    title = "Sin Compras Registradas",
                    description = if (canAddPurchase)
                        "Registra compras a tus proveedores para ingresar nueva mercancía e incrementar el stock automáticamente."
                    else
                        "El registro de compras a proveedores está reservado para el Dueño o Administrador.",
                    actionButtonText = if (canAddPurchase) "+ Registrar Nueva Compra" else "",
                    onActionClick = { if (canAddPurchase) showNewPurchaseDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(purchases, key = { it.id }) { purchase ->
                        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(purchase.date))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceWhite,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(purchase.folio, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                                        Text(purchase.supplierName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMedium)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (purchase.isCredit) BrandAmberLight else BrandEmeraldLight
                                    ) {
                                        Text(
                                            text = if (purchase.isCredit) "A Crédito" else "Pagada",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (purchase.isCredit) BrandAmberDark else BrandEmeraldDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (purchase.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(purchase.notes, fontSize = 12.sp, color = TextMuted)
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = SurfaceHover)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(dateStr, fontSize = 11.sp, color = TextMuted)
                                    if (!permissions.canViewCostPrice) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SurfaceSubtle
                                        ) {
                                            Text(
                                                "Costo Reservado",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMuted,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            "Total: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", purchase.total)}",
                                            fontFamily = DisplayFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = BrandEmeraldDark
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

    if (showNewPurchaseDialog) {
        val purchasableItems = remember(inventoryItems) {
            inventoryItems.filter { !it.isWaste && it.type != ItemType.SERVICIO }
        }
        val purchaseOpId = remember { UUID.randomUUID().toString() }
        NewPurchaseDialog(
            suppliers = suppliers,
            inventoryItems = purchasableItems,
            currencySymbol = settings.currencySymbol,
            isProcessing = isProcessingPurchase,
            onDismiss = { if (!isProcessingPurchase) showNewPurchaseDialog = false },
            onSave = { selectedSupplier, itemsBought, isCredit, notes ->
                coroutineScope.launch {
                    when (val res = viewModel.registerPurchaseAsync(
                        supplierId = selectedSupplier?.id,
                        fallbackSupplierName = selectedSupplier?.name ?: "Proveedor General",
                        itemsBought = itemsBought,
                        isCredit = isCredit,
                        notes = notes,
                        operationId = purchaseOpId
                    )) {
                        is PurchaseRegistrationResult.Success -> {
                            showNewPurchaseDialog = false
                            Toast.makeText(
                                context,
                                "Compra ${res.purchase.folio} registrada y existencias actualizadas",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        PurchaseRegistrationResult.AlreadyProcessing -> Unit
                        PurchaseRegistrationResult.Unauthorized -> {
                            Toast.makeText(context, "Sin permisos para registrar compras.", Toast.LENGTH_SHORT).show()
                        }
                        is PurchaseRegistrationResult.Error -> {
                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun NewPurchaseDialog(
    suppliers: List<Supplier>,
    inventoryItems: List<InventoryItem>,
    currencySymbol: String = "$",
    isProcessing: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Supplier?, List<Triple<InventoryItem, Double, Double>>, Boolean, String) -> Unit
) {
    var selectedSupplier by remember { mutableStateOf<Supplier?>(suppliers.firstOrNull()) }
    var selectedItem by remember(inventoryItems) { mutableStateOf<InventoryItem?>(inventoryItems.firstOrNull()) }
    var quantityText by remember { mutableStateOf("10") }
    var costPriceText by remember(selectedItem) { mutableStateOf(selectedItem?.costPrice?.toString() ?: "0") }
    var orderLines by remember { mutableStateOf<List<Triple<InventoryItem, Double, Double>>>(emptyList()) }
    var lineError by remember { mutableStateOf<String?>(null) }
    var isCredit by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var expandedSupplierDropdown by remember { mutableStateOf(false) }
    var expandedItemDropdown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nueva Orden de Compra", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NavyDark)
                    IconButton(onClick = onDismiss, enabled = !isProcessing) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Select Supplier
                Text("Proveedor:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { expandedSupplierDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(selectedSupplier?.name ?: "Proveedor General", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expandedSupplierDropdown,
                        onDismissRequest = { expandedSupplierDropdown = false }
                    ) {
                        suppliers.forEach { sup ->
                            DropdownMenuItem(
                                text = { Text(sup.name) },
                                onClick = {
                                    selectedSupplier = sup
                                    expandedSupplierDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Select Product to replenish (Servicios & Mermas excluded)
                Text("Artículo a ingresar (sin servicios ni mermas):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { expandedItemDropdown = true },
                        enabled = inventoryItems.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            selectedItem?.name ?: "No hay artículos comprables en inventario",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expandedItemDropdown,
                        onDismissRequest = { expandedItemDropdown = false }
                    ) {
                        inventoryItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} (${item.code})") },
                                onClick = {
                                    selectedItem = item
                                    costPriceText = item.costPrice.toString()
                                    lineError = null
                                    expandedItemDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = {
                            quantityText = it
                            lineError = null
                        },
                        label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costPriceText,
                        onValueChange = {
                            costPriceText = it
                            lineError = null
                        },
                        label = { Text("Costo Unit. ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val currentQty = quantityText.toDoubleOrNull() ?: 0.0
                val currentCost = costPriceText.toDoubleOrNull() ?: 0.0

                OutlinedButton(
                    onClick = {
                        val item = selectedItem
                        if (item == null) {
                            lineError = "Selecciona un producto válido."
                        } else if (currentQty <= 0.0 || currentCost < 0.0) {
                            lineError = "Cantidad > 0 y costo >= 0 requeridos."
                        } else {
                            orderLines = orderLines + Triple(item, currentQty, currentCost)
                            quantityText = "1"
                            lineError = null
                        }
                    },
                    enabled = selectedItem != null && !isProcessing,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_purchase_line_btn")
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar artículo a la orden (${orderLines.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (orderLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceSubtle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Productos en esta orden:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                            orderLines.forEachIndexed { idx, (lineItem, lQty, lCost) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${lQty}x ${lineItem.name} (@ $currencySymbol${String.format(Locale.US, "%.2f", lCost)})",
                                        fontSize = 12.sp,
                                        color = TextDark,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$currencySymbol${String.format(Locale.US, "%.2f", lQty * lCost)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TileGreen
                                    )
                                    IconButton(
                                        onClick = {
                                            orderLines = orderLines.toMutableList().apply { removeAt(idx) }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Quitar línea", tint = TileSalmon, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                lineError?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(err, color = TileSalmon, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                val effectiveLines = if (orderLines.isNotEmpty()) {
                    orderLines
                } else if (selectedItem != null && currentQty > 0.0 && currentCost >= 0.0) {
                    listOf(Triple(selectedItem!!, currentQty, currentCost))
                } else {
                    emptyList()
                }
                val total = effectiveLines.sumOf { (_, q, c) -> q * c }

                Text(
                    "Total Compra: $currencySymbol${String.format(Locale.US, "%.2f", total)}",
                    fontWeight = FontWeight.Bold,
                    color = TileGreen
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isCredit, onCheckedChange = { isCredit = it })
                    Text("Compra a Crédito", fontSize = 13.sp)
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Folio Factura") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (effectiveLines.isEmpty()) {
                                lineError = "Agrega al menos un producto válido con cantidad mayor a cero."
                            } else {
                                onSave(
                                    selectedSupplier,
                                    effectiveLines,
                                    isCredit,
                                    notes
                                )
                            }
                        },
                        enabled = !isProcessing && effectiveLines.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                        modifier = Modifier.testTag("submit_purchase_btn")
                    ) {
                        Text(
                            if (isProcessing) "Registrando..." else "Registrar e Incrementar Stock",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
