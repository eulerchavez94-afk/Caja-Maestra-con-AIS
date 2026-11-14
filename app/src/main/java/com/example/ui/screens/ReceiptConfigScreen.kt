package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ReceiptFormat
import com.example.data.model.UserRole
import com.example.ui.components.BluetoothPrinterDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.BluetoothPrinterManager
import com.example.util.RoleManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptConfigScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()

    var showPrinterDialog by remember { mutableStateOf(false) }
    var isTestingPrinter by remember { mutableStateOf(false) }
    var editingFieldTitle by remember { mutableStateOf<String?>(null) }
    var editingFieldValue by remember { mutableStateOf("") }
    var onSaveField by remember { mutableStateOf<(String) -> Unit>({}) }

    val permissions = remember(settings) { RoleManager.getPermissions(settings) }
    val canEdit = permissions.canEditReceiptHeaders

    Scaffold(
        topBar = {
            com.example.ui.components.CajaMaestraTopBar(
                title = "Recibo & Ticket 58mm",
                subtitle = "Impresora Térmica & Formato",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CanvasLight),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Business Details Section (Matches Screenshot 15)
            item {
                ReceiptConfigItem(
                    label = "Nombre del Negocio",
                    value = settings.businessName,
                    canEdit = canEdit,
                    onEdit = {
                        editingFieldTitle = "Nombre del Negocio"
                        editingFieldValue = settings.businessName
                        onSaveField = { viewModel.updateSettings(settings.copy(businessName = it)) }
                    }
                )
            }

            // Logo Box matching Screenshot 15
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Emblema de Ticket", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavyDark)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandEmeraldLight
                            ) {
                                Text(
                                    text = "Estándar 58mm",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmeraldDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Logo Graphic Box matching screenshot
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp, 70.dp)
                                    .border(4.dp, Color.Black, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .border(4.dp, Color.Black, RoundedCornerShape(18.dp))
                                )
                            }
                        }
                    }
                }
            }

            item {
                ReceiptConfigItem(
                    label = "Contacto",
                    value = settings.address,
                    canEdit = canEdit,
                    onEdit = {
                        editingFieldTitle = "Contacto / Dirección"
                        editingFieldValue = settings.address
                        onSaveField = { viewModel.updateSettings(settings.copy(address = it)) }
                    }
                )
            }

            item {
                ReceiptConfigItem(
                    label = "Teléfono",
                    value = settings.phone,
                    canEdit = canEdit,
                    onEdit = {
                        editingFieldTitle = "Teléfono"
                        editingFieldValue = settings.phone
                        onSaveField = { viewModel.updateSettings(settings.copy(phone = it)) }
                    }
                )
            }

            item {
                ReceiptConfigItem(
                    label = "Correo",
                    value = settings.email,
                    canEdit = canEdit,
                    onEdit = {
                        editingFieldTitle = "Correo"
                        editingFieldValue = settings.email
                        onSaveField = { viewModel.updateSettings(settings.copy(email = it)) }
                    }
                )
            }

            item {
                ReceiptConfigItem(
                    label = "Pie de Página",
                    value = settings.footerText,
                    canEdit = canEdit,
                    onEdit = {
                        editingFieldTitle = "Pie de Página"
                        editingFieldValue = settings.footerText
                        onSaveField = { viewModel.updateSettings(settings.copy(footerText = it)) }
                    }
                )
            }

            item {
                ReceiptConfigItem(
                    label = "QR Personalizado",
                    value = settings.customQr,
                    canEdit = canEdit,
                    onEdit = {
                        editingFieldTitle = "QR Personalizado"
                        editingFieldValue = settings.customQr
                        onSaveField = { viewModel.updateSettings(settings.copy(customQr = it)) }
                    }
                )
            }

            // Receipt Format & Options Card (Matches Screenshot 17)
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Formato del recibo en ventas",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NavyDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Radio Buttons: Hoja Carta vs Ticket (58mm)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.updateSettings(settings.copy(receiptFormat = ReceiptFormat.HOJA_CARTA)) }
                            ) {
                                RadioButton(
                                    selected = settings.receiptFormat == ReceiptFormat.HOJA_CARTA,
                                    onClick = { viewModel.updateSettings(settings.copy(receiptFormat = ReceiptFormat.HOJA_CARTA)) },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldAccent)
                                )
                                Text("Hoja Carta", fontSize = 14.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.updateSettings(settings.copy(receiptFormat = ReceiptFormat.TICKET_58MM)) }
                            ) {
                                RadioButton(
                                    selected = settings.receiptFormat == ReceiptFormat.TICKET_58MM,
                                    onClick = { viewModel.updateSettings(settings.copy(receiptFormat = ReceiptFormat.TICKET_58MM)) },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldAccent)
                                )
                                Text("Ticket (58 mm)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Checkboxes (Matches Screenshot 17)
                        ReceiptCheckboxItem(
                            label = "Agregar información del cliente",
                            checked = settings.receiptShowClientInfo,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(receiptShowClientInfo = it)) }
                        )

                        ReceiptCheckboxItem(
                            label = "Agregar Identificador QR (Recibo / Ticket)",
                            checked = settings.receiptShowQr,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(receiptShowQr = it)) }
                        )

                        ReceiptCheckboxItem(
                            label = "Desglose Pagos",
                            checked = settings.receiptShowPaymentBreakdown,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(receiptShowPaymentBreakdown = it)) }
                        )

                        ReceiptCheckboxItem(
                            label = "Mostrar Clave y Unidad",
                            checked = settings.receiptShowKeyAndUnit,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(receiptShowKeyAndUnit = it)) }
                        )

                        ReceiptCheckboxItem(
                            label = "Mostrar Folio",
                            checked = settings.receiptShowFolio,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(receiptShowFolio = it)) }
                        )

                        ReceiptCheckboxItem(
                            label = "Mostrar Status",
                            checked = settings.receiptShowStatus,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(receiptShowStatus = it)) }
                        )
                    }
                }
            }

            // Impresora Bluetooth 58mm Card (Solo si el Admin la tiene habilitada)
            if (permissions.canConfigurePrinter) {
                item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = BrandEmerald)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Impresora Bluetooth (58mm)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyDark)
                            }
                            TextButton(onClick = { showPrinterDialog = true }) {
                                Text("Cambiar", color = GoldAccent, fontWeight = FontWeight.Bold)
                            }
                        }

                        val printerStatus = if (settings.bluetoothPrinterAddress.isNotBlank()) {
                            "${settings.bluetoothPrinterName}\nMAC: ${settings.bluetoothPrinterAddress}"
                        } else {
                            "Ninguna impresora 58mm vinculada todavía"
                        }

                        Text(
                            text = printerStatus,
                            fontSize = 13.sp,
                            color = if (settings.bluetoothPrinterAddress.isNotBlank()) BrandEmerald else Color.Gray,
                            fontWeight = if (settings.bluetoothPrinterAddress.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showPrinterDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Vincular / Buscar", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (settings.bluetoothPrinterAddress.isBlank()) {
                                        Toast.makeText(context, "Vincula tu impresora 58mm primero", Toast.LENGTH_SHORT).show()
                                        showPrinterDialog = true
                                    } else {
                                        coroutineScope.launch {
                                            isTestingPrinter = true
                                            Toast.makeText(context, "Conectando a ${settings.bluetoothPrinterName}...", Toast.LENGTH_SHORT).show()
                                            val result = BluetoothPrinterManager.printTestReceipt(
                                                context = context,
                                                macAddress = settings.bluetoothPrinterAddress,
                                                scale = settings.printScale,
                                                intensity = settings.printIntensity
                                            )
                                            isTestingPrinter = false
                                            val message = result.getOrElse { it.localizedMessage ?: "Error al imprimir" }
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isTestingPrinter,
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTestingPrinter) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test 58mm", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (editingFieldTitle != null) {
        Dialog(
            onDismissRequest = { editingFieldTitle = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
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
                    Text("Editar $editingFieldTitle", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = NavyDark)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editingFieldValue,
                        onValueChange = { editingFieldValue = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingFieldTitle = null }) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSaveField(editingFieldValue.trim())
                                editingFieldTitle = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                        ) {
                            Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showPrinterDialog) {
        BluetoothPrinterDialog(
            currentPrinterName = settings.bluetoothPrinterName,
            currentPrinterAddress = settings.bluetoothPrinterAddress,
            printScale = settings.printScale,
            printIntensity = settings.printIntensity,
            onDismiss = { showPrinterDialog = false },
            onPrinterSelected = { name, address ->
                viewModel.updateSettings(
                    settings.copy(
                        bluetoothPrinterName = name,
                        bluetoothPrinterAddress = address
                    )
                )
                showPrinterDialog = false
                Toast.makeText(context, "Impresora vinculada: $name", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun ReceiptConfigItem(
    label: String,
    value: String,
    canEdit: Boolean = true,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value.ifBlank { "Sin Información" },
                    fontSize = 15.sp,
                    color = if (value.isBlank()) Color.Gray else NavyDark,
                    fontWeight = FontWeight.Medium
                )
            }
            if (canEdit) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ReceiptCheckboxItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 14.sp, color = Color.DarkGray)
    }
}
