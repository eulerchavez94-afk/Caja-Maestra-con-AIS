package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BusinessSettings
import com.example.data.model.CashRegisterShift
import com.example.data.model.CashShiftMovement
import com.example.data.model.CashShiftMovementType
import com.example.ui.theme.*
import com.example.util.BluetoothPrinterManager
import com.example.util.CashShiftCalculator
import com.example.util.ShiftLiveSummary
import com.example.util.ThermalPrinterUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun OpenCashRegisterDialog(
    cashierName: String,
    currencySymbol: String,
    lastClosingFloat: Double? = null,
    onDismiss: () -> Unit,
    onConfirmOpen: (openingFloat: Double, notes: String) -> Unit
) {
    var floatInput by remember {
        mutableStateOf(
            if (lastClosingFloat != null && lastClosingFloat > 0.0) {
                String.format(Locale.US, "%.2f", lastClosingFloat)
            } else {
                "500.00"
            }
        )
    }
    var notesInput by remember { mutableStateOf("") }
    var useDenominationHelper by remember { mutableStateOf(false) }
    var denomCounts by remember { mutableStateOf<Map<Double, Int>>(emptyMap()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
                .testTag("open_cash_register_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BrandEmeraldLight,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = BrandEmeraldDark)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Abrir Caja / Iniciar Turno",
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextDark
                            )
                            Text(
                                text = "Cajero: $cashierName",
                                fontSize = 12.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Fondo Inicial Obligatorio ($currencySymbol)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = floatInput,
                    onValueChange = {
                        floatInput = it
                        errorMsg = null
                    },
                    label = { Text("Efectivo inicial en caja ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { msg -> { Text(msg, color = StatusCancelled) } },
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = BrandEmeraldDark) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_float_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick float presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.0, 200.0, 500.0, 1000.0).forEach { preset ->
                        FilterChip(
                            selected = floatInput.toDoubleOrNull() == preset,
                            onClick = {
                                floatInput = if (preset == 0.0) "0" else String.format(Locale.US, "%.0f", preset)
                                errorMsg = null
                            },
                            label = { Text("$currencySymbol${preset.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = { useDenominationHelper = !useDenominationHelper },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (useDenominationHelper) Icons.Default.ExpandLess else Icons.Default.Calculate,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (useDenominationHelper) "Ocultar contador de billetes" else "Contar por billetes y monedas",
                        color = BrandBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = useDenominationHelper) {
                    DenominationCounterGrid(
                        currencySymbol = currencySymbol,
                        counts = denomCounts,
                        onCountsChange = { newCounts ->
                            denomCounts = newCounts
                            val total = CashShiftCalculator.computeDenominationTotal(newCounts)
                            floatInput = String.format(Locale.US, "%.2f", total)
                            errorMsg = null
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Observaciones de apertura (opcional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancelar", color = TextMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsed = floatInput.trim().toDoubleOrNull()
                            if (parsed == null || parsed < 0.0) {
                                errorMsg = "Ingresa un monto válido (0 o mayor)"
                            } else {
                                onConfirmOpen(parsed, notesInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("confirm_open_cash_btn")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Abrir Caja", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CashMovementDialog(
    currencySymbol: String,
    recentMovements: List<CashShiftMovement>,
    onDismiss: () -> Unit,
    onConfirmMovement: (type: CashShiftMovementType, amount: Double, reason: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(CashShiftMovementType.RETIRO) }
    var amountInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val quickReasons = if (selectedType == CashShiftMovementType.RETIRO) {
        listOf("Retiro parcial / Seguridad", "Pago a proveedor", "Gasto menor en tienda")
    } else {
        listOf("Ingreso de morralla / cambio", "Reposición de fondo", "Ajuste de caja")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
                .testTag("cash_movement_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Movimientos de Caja",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextDark
                        )
                        Text(
                            text = "Registra entradas o retiros (sangrías) del turno",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == CashShiftMovementType.RETIRO,
                        onClick = { selectedType = CashShiftMovementType.RETIRO },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Retiro / Sangría (-)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TileSalmon,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == CashShiftMovementType.ENTRADA,
                        onClick = { selectedType = CashShiftMovementType.ENTRADA },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Entrada (+)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandEmerald,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        errorMsg = null
                    },
                    label = { Text("Monto ($currencySymbol) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMsg != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = {
                        reasonInput = it
                        errorMsg = null
                    },
                    label = { Text("Motivo / Concepto *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickReasons.forEach { reason ->
                        SuggestionChip(
                            onClick = { reasonInput = reason },
                            label = { Text(reason, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMsg!!, color = StatusCancelled, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val amt = amountInput.trim().toDoubleOrNull()
                        if (amt == null || amt <= 0.0) {
                            errorMsg = "Ingresa un monto mayor a 0"
                        } else if (reasonInput.isBlank()) {
                            errorMsg = "Especifica el motivo del movimiento"
                        } else {
                            onConfirmMovement(selectedType, amt, reasonInput.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType == CashShiftMovementType.RETIRO) TileSalmon else BrandEmerald
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (selectedType == CashShiftMovementType.RETIRO) "Registrar Retiro de Caja" else "Registrar Entrada de Efectivo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (recentMovements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SurfaceHover)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Movimientos del turno (${recentMovements.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    recentMovements.take(5).forEach { mov ->
                        val isEntry = mov.type == CashShiftMovementType.ENTRADA
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(mov.date))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mov.reason, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                                Text("$timeStr • ${mov.performedBy}", fontSize = 10.sp, color = TextMuted)
                            }
                            Text(
                                text = "${if (isEntry) "+" else "-"}$currencySymbol${String.format(Locale.US, "%.2f", mov.amount)}",
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isEntry) BrandEmeraldDark else TileSalmon
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloseCashRegisterDialog(
    shift: CashRegisterShift,
    summary: ShiftLiveSummary,
    currencySymbol: String,
    canViewExpectedBeforeClose: Boolean,
    onDismiss: () -> Unit,
    onConfirmClose: (countedCash: Double, nextTurnFloat: Double, closingNotes: String) -> Unit
) {
    var countedInput by remember { mutableStateOf("") }
    var nextTurnFloatInput by remember { mutableStateOf("") }
    var closingNotes by remember { mutableStateOf("") }
    var showExpectedPreview by remember { mutableStateOf(false) }
    var useDenominationGrid by remember { mutableStateOf(true) }
    var denomCounts by remember { mutableStateOf<Map<Double, Int>>(emptyMap()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val sdf = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
                .testTag("close_cash_register_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BrandAmberLight,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PointOfSale, contentDescription = null, tint = BrandAmberDark)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Corte y Cierre de Caja",
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextDark
                            )
                            Text(
                                text = "Turno #${shift.id} • Apertura: ${sdf.format(Date(shift.openedAt))}",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Blind count reminder banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandBlueLight.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Arqueo Ciego: Cuenta el efectivo físico real en tu cajón antes de emitir el corte (${summary.salesCount} ventas registradas).",
                            fontSize = 11.sp,
                            color = TextDark
                        )
                    }
                }

                if (canViewExpectedBeforeClose) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = { showExpectedPreview = !showExpectedPreview },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (showExpectedPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = BrandAmberDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showExpectedPreview) "Ocultar esperado (Modo Admin/Jefe)" else "Ver efectivo esperado (Admin/Jefe)",
                            color = BrandAmberDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    AnimatedVisibility(visible = showExpectedPreview) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceSubtle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                SummaryLine("Fondo Inicial:", "$currencySymbol${String.format(Locale.US, "%.2f", summary.openingFloat)}")
                                SummaryLine("+ Ventas Efectivo:", "$currencySymbol${String.format(Locale.US, "%.2f", summary.cashSalesTotal)}")
                                SummaryLine("+ Entradas Caja:", "$currencySymbol${String.format(Locale.US, "%.2f", summary.cashEntriesTotal)}")
                                SummaryLine("- Retiros / Sangrías:", "$currencySymbol${String.format(Locale.US, "%.2f", summary.cashWithdrawalsTotal)}")
                                HorizontalDivider(color = SurfaceHover, modifier = Modifier.padding(vertical = 4.dp))
                                SummaryLine(
                                    "Efectivo Esperado:",
                                    "$currencySymbol${String.format(Locale.US, "%.2f", summary.expectedCash)}",
                                    bold = true,
                                    valueColor = BrandEmeraldDark
                                )
                                if (summary.cardSalesTotal + summary.transferSalesTotal + summary.creditSalesTotal > 0) {
                                    SummaryLine(
                                        "Otros métodos (Tarj/Transf/Créd):",
                                        "$currencySymbol${String.format(Locale.US, "%.2f", summary.cardSalesTotal + summary.transferSalesTotal + summary.creditSalesTotal)}"
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = countedInput,
                    onValueChange = {
                        countedInput = it
                        errorMsg = null
                    },
                    label = { Text("Efectivo Contado en Caja ($currencySymbol) *") },
                    placeholder = { Text("Ej. 1850.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { msg -> { Text(msg, color = StatusCancelled) } },
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = BrandEmeraldDark) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("close_counted_cash_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { useDenominationGrid = !useDenominationGrid },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (useDenominationGrid) "Ocultar billetes y monedas" else "Contar billetes y monedas",
                            color = BrandBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (denomCounts.values.any { it > 0 }) {
                        TextButton(onClick = {
                            denomCounts = emptyMap()
                            countedInput = "0.00"
                        }) {
                            Text("Limpiar conteo", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }

                AnimatedVisibility(visible = useDenominationGrid) {
                    DenominationCounterGrid(
                        currencySymbol = currencySymbol,
                        counts = denomCounts,
                        onCountsChange = { newCounts ->
                            denomCounts = newCounts
                            val total = CashShiftCalculator.computeDenominationTotal(newCounts)
                            countedInput = String.format(Locale.US, "%.2f", total)
                            errorMsg = null
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = nextTurnFloatInput,
                    onValueChange = { nextTurnFloatInput = it },
                    label = { Text("Fondo dejado p/ siguiente turno (opcional)") },
                    placeholder = { Text("Ej. 500.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = closingNotes,
                    onValueChange = { closingNotes = it },
                    label = { Text("Notas de cierre (opcional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancelar", color = TextMedium)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedCounted = countedInput.trim().toDoubleOrNull()
                            if (parsedCounted == null || parsedCounted < 0.0) {
                                errorMsg = "Ingresa o cuenta el efectivo real en caja"
                            } else {
                                val nextFloat = nextTurnFloatInput.trim().toDoubleOrNull() ?: 0.0
                                onConfirmClose(parsedCounted, nextFloat, closingNotes)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("confirm_close_cash_btn")
                    ) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cerrar Caja y Emitir Corte", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftCutReceiptDialog(
    settings: BusinessSettings,
    shift: CashRegisterShift,
    canPrintThermalReceipt: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPrinting by remember { mutableStateOf(false) }

    val reportText = remember(shift, settings) {
        ThermalPrinterUtil.formatCashShiftReportText(settings, shift)
    }

    val absDiff = abs(shift.difference)
    val isExact = absDiff < 0.01
    val isSurplus = shift.difference > 0.01

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BrandNavyElevated,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
                .testTag("shift_cut_receipt_dialog")
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
                        Text(
                            text = "Corte de Caja • Turno #${shift.id}",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Responsable: ${shift.cashierName}",
                            fontSize = 12.sp,
                            color = TextLightSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextLightSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Difference Audit Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isExact -> BrandEmerald.copy(alpha = 0.18f)
                        isSurplus -> BrandBlue.copy(alpha = 0.22f)
                        else -> TileSalmon.copy(alpha = 0.22f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isExact -> BrandEmerald
                            isSurplus -> BrandBlue
                            else -> TileSalmon
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when {
                                    isExact -> "CUADRE PERFECTO"
                                    isSurplus -> "SOBRANTE EN CAJA"
                                    else -> "FALTANTE EN CAJA"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Esperado: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", shift.expectedCash)} | Contado: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", shift.countedCash)}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Text(
                            text = if (isExact) "${settings.currencySymbol}0.00" else "${if (isSurplus) "+" else "-"}${settings.currencySymbol}${String.format(Locale.US, "%.2f", absDiff)}",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = when {
                                isExact -> BrandEmeraldLight
                                isSurplus -> BrandAmber
                                else -> Color(0xFFFFB4AB)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = reportText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = TextDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canPrintThermalReceipt && settings.bluetoothPrinterAddress.isNotBlank()) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isPrinting = true
                                    val bytes = ThermalPrinterUtil.buildEscPosBytes(reportText, settings.printScale)
                                    val res = BluetoothPrinterManager.printBytesDirect(
                                        context = context,
                                        macAddress = settings.bluetoothPrinterAddress,
                                        bytes = bytes
                                    )
                                    isPrinting = false
                                    Toast.makeText(
                                        context,
                                        res.getOrElse { it.localizedMessage ?: "Error al imprimir" },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = !isPrinting,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Imprimir Ticket Corte (58mm)", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                ThermalPrinterUtil.printCashShiftReport58mm(context, settings, shift)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, reportText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir Corte de Caja"))
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandAmber),
                            border = BorderStroke(1.dp, BrandAmber.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CashShiftsHistoryDialog(
    shifts: List<CashRegisterShift>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSelectClosedShift: (CashRegisterShift) -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Historial de Cortes de Caja",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextDark
                        )
                        Text(
                            text = "${shifts.size} turnos registrados",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (shifts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aún no se han abierto turnos de caja.", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(shifts, key = { it.id }) { shift ->
                            val totalSold = shift.cashSalesTotal + shift.cardSalesTotal + shift.transferSalesTotal + shift.creditSalesTotal
                            val isExact = abs(shift.difference) < 0.01
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CanvasLight,
                                border = BorderStroke(1.dp, SurfaceHover),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !shift.isOpen) {
                                        onSelectClosedShift(shift)
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Turno #${shift.id} • ${shift.cashierName}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = TextDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (shift.isOpen) BrandEmeraldLight else SurfaceSubtle
                                            ) {
                                                Text(
                                                    text = if (shift.isOpen) "ABIERTO" else "CERRADO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (shift.isOpen) BrandEmeraldDark else TextMedium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Inicio: ${sdf.format(Date(shift.openedAt))}",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                        if (!shift.isOpen && shift.closedAt != null) {
                                            Text(
                                                text = "Cierre: ${sdf.format(Date(shift.closedAt))} • ${shift.salesCount} ventas",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (shift.isOpen) {
                                            Text(
                                                text = "Fondo: $currencySymbol${String.format(Locale.US, "%.2f", shift.openingFloat)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = BrandEmeraldDark
                                            )
                                        } else {
                                            Text(
                                                text = "$currencySymbol${String.format(Locale.US, "%.2f", totalSold)}",
                                                fontFamily = DisplayFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = BrandNavy
                                            )
                                            Text(
                                                text = when {
                                                    isExact -> "Cuadre exacto"
                                                    shift.difference > 0 -> "+$currencySymbol${String.format(Locale.US, "%.2f", shift.difference)}"
                                                    else -> "-$currencySymbol${String.format(Locale.US, "%.2f", abs(shift.difference))}"
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isExact -> BrandEmeraldDark
                                                    shift.difference > 0 -> BrandBlue
                                                    else -> TileSalmon
                                                }
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
    }
}

@Composable
private fun DenominationCounterGrid(
    currencySymbol: String,
    counts: Map<Double, Int>,
    onCountsChange: (Map<Double, Int>) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceSubtle,
        border = BorderStroke(1.dp, SurfaceHover),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val pairs = CashShiftCalculator.DENOMINATIONS.chunked(2)
            pairs.forEach { rowDenoms ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowDenoms.forEach { denom ->
                        val qty = counts[denom] ?: 0
                        val label = if (denom >= 1.0) "$currencySymbol${denom.toInt()}" else "$currencySymbol${String.format(Locale.US, "%.2f", denom)}"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, if (qty > 0) BrandEmerald else SurfaceHover),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = SurfaceSubtle,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable {
                                                val updated = counts.toMutableMap()
                                                updated[denom] = (qty - 1).coerceAtLeast(0)
                                                onCountsChange(updated)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Remove, contentDescription = "-1", modifier = Modifier.size(14.dp), tint = TextDark)
                                        }
                                    }
                                    Text(
                                        text = "$qty",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (qty > 0) BrandEmeraldDark else TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = BrandEmerald,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable {
                                                val updated = counts.toMutableMap()
                                                updated[denom] = qty + 1
                                                onCountsChange(updated)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Add, contentDescription = "+1", modifier = Modifier.size(14.dp), tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (rowDenoms.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = TextDark
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = TextMedium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = DisplayFontFamily,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
