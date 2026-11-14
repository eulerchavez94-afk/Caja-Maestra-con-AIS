package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BusinessSettings
import com.example.data.model.Sale
import com.example.data.model.SaleCartItem
import com.example.data.model.SaleStatus
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.util.ReportExporter
import com.example.util.RoleManager
import com.example.util.ThermalPrinterUtil
import kotlinx.coroutines.launch

@Composable
fun ReceiptPreviewDialog(
    settings: BusinessSettings,
    sale: Sale,
    items: List<SaleCartItem>,
    onDismiss: () -> Unit,
    onCancelSale: (() -> Unit)? = null,
    onPrinterSelected: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showPrinterDialog by remember { mutableStateOf(false) }
    var isPrintingDirect by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    val receiptText = remember(sale, settings, items) {
        ThermalPrinterUtil.formatReceiptText(settings, sale, items)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BrandNavyElevated,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
                .testTag("receipt_preview_dialog")
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandAmber.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = "Ticket",
                                    tint = BrandAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ticket Térmico (58 mm)",
                                fontFamily = DisplayFontFamily,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Folio: ${sale.folio}",
                                fontSize = 12.sp,
                                color = TextLightSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextLightSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Realistic Paper Receipt View with crisp dark text
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp)
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = receiptText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Bluetooth Thermal Print, Print PDF, Share
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (permissions.canPrintThermalReceipt) {
                        Button(
                            onClick = {
                                if (settings.bluetoothPrinterAddress.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Por favor selecciona o vincula tu impresora 58mm",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showPrinterDialog = true
                                } else {
                                    coroutineScope.launch {
                                        isPrintingDirect = true
                                        Toast.makeText(context, "Conectando e imprimiendo en ${settings.bluetoothPrinterName}...", Toast.LENGTH_SHORT).show()
                                        val escPosBytes = ThermalPrinterUtil.buildEscPosBytes(receiptText, settings.printScale)
                                        val result = com.example.util.BluetoothPrinterManager.printBytesDirect(
                                            context = context,
                                            macAddress = settings.bluetoothPrinterAddress,
                                            bytes = escPosBytes
                                        )
                                        isPrintingDirect = false
                                        val message = result.getOrElse { it.localizedMessage ?: "Error desconocido" }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isPrintingDirect,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("thermal_print_button")
                        ) {
                            if (isPrintingDirect) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Imprimiendo en 58mm...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            } else {
                                Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                val btnLabel = if (settings.bluetoothPrinterAddress.isNotBlank()) {
                                    "Imprimir en ${settings.bluetoothPrinterName.take(15)} (58mm)"
                                } else {
                                    "Vincular / Imprimir en Térmica 58mm"
                                }
                                Text(btnLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val headers = listOf("Cant", "Descripción", "P. Unit", "Subtotal")
                                val rows = items.map {
                                    listOf(it.quantity.toString(), it.name, "${settings.currencySymbol}${it.unitPrice}", "${settings.currencySymbol}${it.subtotal}")
                                }
                                ReportExporter.printReportAsPdf(
                                    context = context,
                                    reportTitle = "Ticket_${sale.folio}",
                                    businessName = settings.businessName,
                                    headers = headers,
                                    rows = rows,
                                    summaryStats = mapOf(
                                        "Folio" to sale.folio,
                                        "Total" to "${settings.currencySymbol}${sale.total}",
                                        "Método" to sale.paymentMethod,
                                        "Cajero" to sale.sellerName
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generar PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, receiptText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir Recibo").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (permissions.canCancelSales && sale.status != SaleStatus.CANCELADA && onCancelSale != null) {
                        OutlinedButton(
                            onClick = { showCancelConfirm = true },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TileSalmon.copy(alpha = 0.65f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB4AB)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("cancel_sale_button")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Anular Venta / Restituir Stock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showCancelConfirm && onCancelSale != null) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Anular ${sale.folio}", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Deseas cancelar esta venta por ${settings.currencySymbol}${String.format(java.util.Locale.US, "%.2f", sale.total)}? Se restituirá el inventario automáticamente y no contará en el corte de caja.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        onCancelSale()
                        Toast.makeText(context, "Venta ${sale.folio} anulada", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TileSalmon)
                ) {
                    Text("Sí, Anular", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Volver")
                }
            }
        )
    }

    if (showPrinterDialog) {
        BluetoothPrinterDialog(
            currentPrinterName = settings.bluetoothPrinterName,
            currentPrinterAddress = settings.bluetoothPrinterAddress,
            printScale = settings.printScale,
            printIntensity = settings.printIntensity,
            onDismiss = { showPrinterDialog = false },
            onPrinterSelected = { name, address ->
                onPrinterSelected?.invoke(name, address)
                showPrinterDialog = false
                Toast.makeText(context, "Impresora vinculada: $name. Toca Imprimir de nuevo.", Toast.LENGTH_LONG).show()
            }
        )
    }
}
