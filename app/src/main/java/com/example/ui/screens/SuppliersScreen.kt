package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Purchase
import com.example.data.model.Supplier
import com.example.data.model.UserRole
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
fun SuppliersScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val suppliers by viewModel.suppliers.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var supplierToEdit by remember { mutableStateOf<Supplier?>(null) }
    var selectedSupplierDetail by remember { mutableStateOf<Supplier?>(null) }

    val filteredSuppliers = suppliers.filter {
        searchQuery.isBlank() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.company.contains(searchQuery, ignoreCase = true) ||
                it.suppliedProducts.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            CajaMaestraTopBar(
                title = "Proveedores",
                subtitle = "Directorio y Abastecimiento (${suppliers.size})",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer,
                searchQuery = searchQuery,
                isSearchVisible = isSearchExpanded,
                searchPlaceholder = "Buscar proveedor o producto...",
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = { isSearchExpanded = !isSearchExpanded },
                actions = {
                    if (permissions.canCreateSupplier) {
                        IconButton(onClick = {
                            supplierToEdit = null
                            showAddEditDialog = true
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Agregar Proveedor", tint = BrandAmber)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (permissions.canCreateSupplier) {
                FloatingActionButton(
                    onClick = {
                        supplierToEdit = null
                        showAddEditDialog = true
                    },
                    containerColor = BrandEmerald,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_supplier_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Proveedor", modifier = Modifier.size(28.dp))
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
            if (filteredSuppliers.isEmpty()) {
                CajaMaestraEmptyState(
                    icon = Icons.Default.Domain,
                    title = "Sin Proveedores Registrados",
                    description = if (searchQuery.isNotBlank())
                        "No se encontraron resultados para '$searchQuery'."
                    else
                        "Registra tus proveedores para gestionar órdenes de compra, términos de pago e historial de compras.",
                    actionButtonText = "+ Registrar Nuevo Proveedor",
                    onActionClick = {
                        supplierToEdit = null
                        showAddEditDialog = true
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSuppliers, key = { it.id }) { supplier ->
                        val supplierPurchases = purchases.filter { it.supplierId == supplier.id || it.supplierName == supplier.name }
                        val totalPurchased = supplierPurchases.sumOf { it.total }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceWhite,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSupplierDetail = supplier }
                                .testTag("supplier_card_${supplier.id}")
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
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(BrandAmberLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Domain,
                                                contentDescription = null,
                                                tint = BrandAmberDark,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = supplier.name,
                                                fontFamily = DisplayFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = TextDark
                                            )
                                            if (supplier.company.isNotBlank()) {
                                                Text(
                                                    text = supplier.company,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextMedium
                                                )
                                            }
                                        }
                                    }

                                    // Payment Terms Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BrandAmberLight
                                    ) {
                                        Text(
                                            text = supplier.paymentTerms,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandAmberDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (supplier.suppliedProducts.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Surte: ${supplier.suppliedProducts}",
                                            fontSize = 12.sp,
                                            color = BrandBlue,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = SurfaceHover)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${supplierPurchases.size} compras registradas",
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                        if (!permissions.canViewSupplierInvestment) {
                                            Text(
                                                text = "Inversión Reservada",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMuted
                                            )
                                        } else {
                                            Text(
                                                text = "Invertido: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", totalPurchased)}",
                                                fontFamily = DisplayFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = BrandEmeraldDark
                                            )
                                        }
                                    }

                                    Row {
                                        if (supplier.phone.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${supplier.phone}"))
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = BrandEmerald)
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                supplierToEdit = supplier
                                                showAddEditDialog = true
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = BrandNavy)
                                        }
                                        if (permissions.canDeleteSupplier) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteSupplier(supplier)
                                                    Toast.makeText(context, "Proveedor eliminado", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = StatusCancelled)
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

    // Detailed Supplier Dialog with Purchase History
    if (selectedSupplierDetail != null) {
        val s = selectedSupplierDetail!!
        val supplierPurchases = purchases.filter { it.supplierId == s.id || it.supplierName == s.name }

        Dialog(
            onDismissRequest = { selectedSupplierDetail = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
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
                        Column {
                            Text(s.name, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                            if (s.company.isNotBlank()) Text(s.company, fontSize = 13.sp, color = TextMuted)
                        }
                        IconButton(onClick = { selectedSupplierDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = CanvasLight,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (s.contactName.isNotBlank()) Text("Contacto: ${s.contactName}", fontSize = 12.sp, color = TextDark)
                            if (s.phone.isNotBlank()) Text("Teléfono: ${s.phone}", fontSize = 12.sp, color = TextDark)
                            if (s.email.isNotBlank()) Text("Correo: ${s.email}", fontSize = 12.sp, color = TextDark)
                            if (s.address.isNotBlank()) Text("Dirección: ${s.address}", fontSize = 12.sp, color = TextDark)
                            Text("Términos de Pago: ${s.paymentTerms}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandAmberDark)
                            if (s.suppliedProducts.isNotBlank()) Text("Suministra: ${s.suppliedProducts}", fontSize = 12.sp, color = BrandBlue)
                            if (s.notes.isNotBlank()) Text("Notas: ${s.notes}", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Historial de Compras (${supplierPurchases.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (supplierPurchases.isEmpty()) {
                        Text("No se han registrado órdenes de compra con este proveedor.", fontSize = 12.sp, color = TextMuted)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(supplierPurchases) { p ->
                                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(p.date))
                                Surface(
                                    color = CanvasLight,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(p.folio, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                            Text("$dateStr • ${if (p.isCredit) "A Crédito" else "Pagada"}", fontSize = 11.sp, color = TextMuted)
                                        }
                                        Text(
                                            "${settings.currencySymbol}${String.format(Locale.US, "%.2f", p.total)}",
                                            fontFamily = DisplayFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = BrandEmeraldDark
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { selectedSupplierDetail = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cerrar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Add / Edit Supplier Dialog
    if (showAddEditDialog) {
        AddEditSupplierDialog(
            initialSupplier = supplierToEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedSupplier ->
                viewModel.saveSupplier(savedSupplier)
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun AddEditSupplierDialog(
    initialSupplier: Supplier?,
    onDismiss: () -> Unit,
    onSave: (Supplier) -> Unit
) {
    var name by remember { mutableStateOf(initialSupplier?.name ?: "") }
    var company by remember { mutableStateOf(initialSupplier?.company ?: "") }
    var contactName by remember { mutableStateOf(initialSupplier?.contactName ?: "") }
    var phone by remember { mutableStateOf(initialSupplier?.phone ?: "") }
    var email by remember { mutableStateOf(initialSupplier?.email ?: "") }
    var address by remember { mutableStateOf(initialSupplier?.address ?: "") }
    var suppliedProducts by remember { mutableStateOf(initialSupplier?.suppliedProducts ?: "") }
    var paymentTerms by remember { mutableStateOf(initialSupplier?.paymentTerms ?: "Contado") }
    var notes by remember { mutableStateOf(initialSupplier?.notes ?: "") }

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
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
                .testTag("add_supplier_dialog")
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
                    Column {
                        Text(
                            text = if (initialSupplier == null || initialSupplier.id == 0L) "Nuevo Proveedor" else "Editar Proveedor",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextDark
                        )
                        Text(
                            text = "Información del contacto y suministro",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre / Razón Social *") },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BrandBlue) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Nombre Comercial / Empresa") },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Domain, contentDescription = null, tint = BrandBlue) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Persona de Contacto") },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.ContactMail, contentDescription = null, tint = BrandBlue) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Teléfono") },
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = BrandEmerald) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = paymentTerms,
                            onValueChange = { paymentTerms = it },
                            label = { Text("Términos") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo Electrónico") },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = suppliedProducts,
                        onValueChange = { suppliedProducts = it },
                        label = { Text("Productos que suministra") },
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Ej. Granos, Harinas, Vasos 58mm") },
                        leadingIcon = { Icon(Icons.Default.Inventory, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                            if (name.isNotBlank()) {
                                onSave(
                                    (initialSupplier ?: Supplier(name = name)).copy(
                                        name = name.trim(),
                                        company = company.trim(),
                                        contactName = contactName.trim(),
                                        phone = phone.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        suppliedProducts = suppliedProducts.trim(),
                                        paymentTerms = paymentTerms.trim(),
                                        notes = notes.trim()
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_supplier_button")
                    ) {
                        Text("Guardar Proveedor", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
