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
import com.example.data.model.Purchase
import com.example.data.model.Supplier
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.RoleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        NewPurchaseDialog(
            suppliers = suppliers,
            inventoryItems = inventoryItems,
            onDismiss = { showNewPurchaseDialog = false },
            onSave = { selectedSupplier, itemsBought, total, isCredit, notes ->
                val count = purchases.size + 1
                val folio = "OC-${String.format(Locale.US, "%05d", count)}"
                val newPurchase = Purchase(
                    folio = folio,
                    supplierId = selectedSupplier?.id,
                    supplierName = selectedSupplier?.name ?: "Proveedor General",
                    total = total,
                    isCredit = isCredit,
                    notes = notes
                )
                viewModel.registerPurchase(newPurchase, itemsBought)
                showNewPurchaseDialog = false
                Toast.makeText(context, "Compra registrada y existencias actualizadas", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun NewPurchaseDialog(
    suppliers: List<Supplier>,
    inventoryItems: List<InventoryItem>,
    onDismiss: () -> Unit,
    onSave: (Supplier?, List<Triple<InventoryItem, Double, Double>>, Double, Boolean, String) -> Unit
) {
    var selectedSupplier by remember { mutableStateOf<Supplier?>(suppliers.firstOrNull()) }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(inventoryItems.firstOrNull()) }
    var quantityText by remember { mutableStateOf("10") }
    var costPriceText by remember { mutableStateOf(selectedItem?.costPrice?.toString() ?: "0") }
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
                .widthIn(max = 480.dp)
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
                    IconButton(onClick = onDismiss) {
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
                        Text(selectedSupplier?.name ?: "Seleccione proveedor", modifier = Modifier.weight(1f))
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

                // Select Product to replenish
                Text("Artículo a ingresar:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box {
                    OutlinedButton(
                        onClick = { expandedItemDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(selectedItem?.name ?: "Seleccione artículo", modifier = Modifier.weight(1f))
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
                        onValueChange = { quantityText = it },
                        label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costPriceText,
                        onValueChange = { costPriceText = it },
                        label = { Text("Costo Unit. ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val qty = quantityText.toDoubleOrNull() ?: 0.0
                val cost = costPriceText.toDoubleOrNull() ?: 0.0
                val total = qty * cost

                Text("Total Compra: $${String.format(Locale.US, "%.2f", total)}", fontWeight = FontWeight.Bold, color = TileGreen)

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
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedItem != null && qty > 0) {
                                onSave(
                                    selectedSupplier,
                                    listOf(Triple(selectedItem!!, qty, cost)),
                                    total,
                                    isCredit,
                                    notes
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                    ) {
                        Text("Registrar e Incrementar Stock", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
