package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BluetoothPrinterDevice
import com.example.ui.theme.*
import com.example.util.BluetoothPrinterManager
import kotlinx.coroutines.launch

/**
 * Interactive Dialog to search, pair, select, and test physical 58mm Bluetooth thermal printers.
 */
@Composable
fun BluetoothPrinterDialog(
    currentPrinterName: String,
    currentPrinterAddress: String,
    printScale: Float,
    printIntensity: Int,
    onDismiss: () -> Unit,
    onPrinterSelected: (name: String, address: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isBluetoothEnabled by remember { mutableStateOf(BluetoothPrinterManager.isBluetoothEnabled(context)) }
    var hasPermission by remember { mutableStateOf(BluetoothPrinterManager.hasBluetoothPermission(context)) }
    var devices by remember { mutableStateOf<List<BluetoothPrinterDevice>>(emptyList()) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<String?>(null) }
    var selectedAddress by remember { mutableStateOf(currentPrinterAddress) }
    var selectedName by remember { mutableStateOf(currentPrinterName) }

    fun refreshDevices() {
        isBluetoothEnabled = BluetoothPrinterManager.isBluetoothEnabled(context)
        hasPermission = BluetoothPrinterManager.hasBluetoothPermission(context)
        if (hasPermission && isBluetoothEnabled) {
            devices = BluetoothPrinterManager.getPairedPrinters(context)
        }
    }

    // Permission launcher for Android 12+ (API 31+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            refreshDevices()
        } else {
            Toast.makeText(
                context,
                "Se requiere el permiso de Bluetooth para encontrar tu impresora.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            refreshDevices()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 480.dp)
                .fillMaxHeight(0.85f)
                .testTag("bluetooth_printer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BrandBlue.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = BrandBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Impresora Térmica 58mm",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = BrandNavy
                            )
                            Text(
                                text = "Conexión Bluetooth ESC/POS",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status Bar / Refresh controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBluetoothEnabled) BrandEmerald.copy(alpha = 0.12f) else StatusCancelled.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isBluetoothEnabled) BrandEmerald else StatusCancelled)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBluetoothEnabled) "Bluetooth Activo" else "Bluetooth Apagado",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBluetoothEnabled) BrandEmeraldDark else StatusCancelled
                            )
                        }
                    }

                    TextButton(onClick = { refreshDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Actualizar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bluetooth Off Banner
                if (!isBluetoothEnabled) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusCancelled.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusCancelled.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "⚠️ El Bluetooth está apagado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = StatusCancelled
                            )
                            Text(
                                text = "Enciende el Bluetooth de tu teléfono para vincular y comunicarte con tu impresora térmica.",
                                fontSize = 12.sp,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Abrir Ajustes de Bluetooth", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Paired Printers List
                Text(
                    text = "Dispositivos Vinculados:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BrandNavy,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (devices.isEmpty() && isBluetoothEnabled) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceSubtle,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                tint = BrandAmber,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No se encontraron impresoras vinculadas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = BrandNavy,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Enciende tu impresora de 58 mm.\n2. Ve a los Ajustes de Bluetooth de tu teléfono y emparéjala (PIN: 0000 ó 1234).\n3. Vuelve a esta pantalla y pulsa 'Actualizar'.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.SettingsBluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Vincular en Ajustes de Android", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices) { device ->
                            val isSelected = device.address == selectedAddress
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) BrandBlueLight.copy(alpha = 0.5f) else SurfaceWhite,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) BrandBlue else SurfaceHover
                                ),
                                shadowElevation = if (isSelected) 2.dp else 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAddress = device.address
                                        selectedName = device.name
                                        onPrinterSelected(device.name, device.address)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = null,
                                            tint = if (isSelected) BrandBlue else TextMedium,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = device.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isSelected) BrandBlue else TextDark
                                            )
                                            Text(
                                                text = device.address,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedAddress = device.address
                                            selectedName = device.name
                                            onPrinterSelected(device.name, device.address)
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = BrandBlue)
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Vincular nueva impresora en Android", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Test Result Feedback Banner
                AnimatedVisibility(visible = testResultStatus != null) {
                    testResultStatus?.let { status ->
                        val isSuccess = status.startsWith("¡")
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSuccess) BrandEmeraldLight else StatusCancelled.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSuccess) BrandEmerald else StatusCancelled
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (isSuccess) BrandEmeraldDark else StatusCancelled,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSuccess) BrandEmeraldDark else StatusCancelled
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Test Print Button
                    Button(
                        onClick = {
                            if (selectedAddress.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Selecciona primero una impresora de la lista arriba.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            isTestingConnection = true
                            testResultStatus = null
                            coroutineScope.launch {
                                val result = BluetoothPrinterManager.printTestReceipt(
                                    context = context,
                                    macAddress = selectedAddress,
                                    scale = printScale,
                                    intensity = printIntensity
                                )
                                isTestingConnection = false
                                result.fold(
                                    onSuccess = { msg ->
                                        testResultStatus = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    },
                                    onFailure = { err ->
                                        testResultStatus = err.message ?: "Error al conectar"
                                        Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        enabled = !isTestingConnection && selectedAddress.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAmber),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Imprimiendo...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Imprimir Prueba", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Done Button
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Listo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
