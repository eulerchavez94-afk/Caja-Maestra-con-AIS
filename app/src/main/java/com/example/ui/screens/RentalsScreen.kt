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
import com.example.data.model.RentalLoan
import com.example.data.model.UserRole
import com.example.ui.components.CajaMaestraEmptyState
import com.example.ui.components.CajaMaestraTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.RoleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rentals Screen - Pure equipment, furniture, and item rental management
 * Completely removed loans/préstamos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalsScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val allRentals by viewModel.rentalsLoans.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    var showAddDialog by remember { mutableStateOf(false) }

    // Only active equipment/item rentals (isRental = true)
    val displayedItems = allRentals.filter { it.isRental }

    Scaffold(
        topBar = {
            CajaMaestraTopBar(
                title = "Rentas y Alquileres",
                subtitle = "Equipos, Mobiliario y Depósitos (${displayedItems.size})",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer
            )
        },
        floatingActionButton = {
            if (permissions.canCreateRental) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = BrandEmerald,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_rental_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva Renta", modifier = Modifier.size(28.dp))
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
            if (displayedItems.isEmpty()) {
                CajaMaestraEmptyState(
                    icon = Icons.Default.ShoppingCartCheckout,
                    title = "Sin Rentas Activas",
                    description = "Lleva el control de equipos y mobiliario alquilados a clientes, fechas de retorno y depósitos de garantía.",
                    actionButtonText = "+ Registrar Nueva Renta",
                    onActionClick = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val startDateStr = sdf.format(Date(item.startDate))
                        val dueDateStr = sdf.format(Date(item.dueDate))

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.itemOrReason, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BrandNavy)
                                        Text("Cliente: ${item.contactName}", fontSize = 13.sp, color = TextMedium)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (item.isReturnedOrPaid) BrandEmerald.copy(alpha = 0.15f) else BrandAmber.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (item.isReturnedOrPaid) "DEVUELTO" else "EN CURSO",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (item.isReturnedOrPaid) BrandEmeraldDark else BrandAmberDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Entrega: $startDateStr", fontSize = 11.sp, color = TextMuted)
                                    Text(
                                        "Fecha límite: $dueDateStr",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (!item.isReturnedOrPaid) StatusCancelled else TextMuted
                                    )
                                }

                                if (item.depositAmount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Depósito en garantía: ${settings.currencySymbol}${item.depositAmount}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandAmberDark
                                    )
                                }

                                if (item.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Notas: ${item.notes}",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SurfaceHover)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Tarifa Renta: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", item.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = BrandEmeraldDark
                                    )

                                    Row {
                                        Button(
                                            onClick = {
                                                viewModel.toggleRentalLoanStatus(item)
                                                Toast.makeText(context, "Estado de renta actualizado", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (item.isReturnedOrPaid) SurfaceSubtle else BrandAmber
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (item.isReturnedOrPaid) "Reabrir Renta" else "Marcar Devolución",
                                                color = if (item.isReturnedOrPaid) TextDark else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (permissions.canDeleteRental) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(onClick = { viewModel.deleteRentalLoan(item) }) {
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

    if (showAddDialog) {
        AddRentalDialog(
            onDismiss = { showAddDialog = false },
            onSave = { contact, itemRental, amount, deposit, notes ->
                viewModel.saveRentalLoan(
                    RentalLoan(
                        isRental = true,
                        contactName = contact,
                        itemOrReason = itemRental,
                        amount = amount,
                        depositAmount = deposit,
                        notes = notes
                    )
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddRentalDialog(
    onDismiss: () -> Unit,
    onSave: (contact: String, itemRental: String, amount: Double, deposit: Double, notes: String) -> Unit
) {
    var contactName by remember { mutableStateOf("") }
    var itemRental by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var depositText by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                Text(
                    text = "Registrar Nueva Renta de Equipo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = BrandNavy
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = itemRental,
                    onValueChange = { itemRental = it },
                    label = { Text("Equipo / Mobiliario / Artículo rentado") },
                    placeholder = { Text("Ej: Impresora 58mm, Sillas, Proyector...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Nombre del Cliente / Arrendatario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Tarifa Renta ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = depositText,
                        onValueChange = { depositText = it },
                        label = { Text("Depósito ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Términos de entrega") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextMedium)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            val dep = depositText.toDoubleOrNull() ?: 0.0
                            if (itemRental.isNotBlank() && contactName.isNotBlank()) {
                                onSave(contactName, itemRental, amt, dep, notes)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Text("Guardar Renta", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
