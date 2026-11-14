package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.model.ExtraMovement
import com.example.data.model.MovementType
import com.example.data.model.UserRole
import com.example.ui.components.CajaMaestraEmptyState
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.RoleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraMovementsScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val movements by viewModel.extraMovements.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var movementToDelete by remember { mutableStateOf<ExtraMovement?>(null) }
    var selectedFilter by remember { mutableStateOf<MovementType?>(null) } // null = Todos

    val totalIngresos = movements.filter { it.type == MovementType.INGRESO }.sumOf { it.amount }
    val totalGastos = movements.filter { it.type == MovementType.GASTO }.sumOf { it.amount }
    val balance = totalIngresos - totalGastos

    val displayedMovements = if (selectedFilter == null) movements else movements.filter { it.type == selectedFilter }

    val permissions = remember(settings) { RoleManager.getPermissions(settings) }
    val canAddMovement = permissions.canCreateMovement

    Scaffold(
        topBar = {
            com.example.ui.components.CajaMaestraTopBar(
                title = "Ingresos & Gastos Extra",
                subtitle = "Flujo de Caja (${displayedMovements.size} movimientos)",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer,
                actions = {
                    IconButton(onClick = {
                        selectedFilter = when (selectedFilter) {
                            null -> MovementType.INGRESO
                            MovementType.INGRESO -> MovementType.GASTO
                            MovementType.GASTO -> null
                        }
                    }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filtro",
                            tint = if (selectedFilter != null) BrandAmber else Color.White
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (canAddMovement) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = BrandEmerald,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_movement_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Movimiento", modifier = Modifier.size(28.dp))
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
            // Balance Summary Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ingresos", color = Color.LightGray, fontSize = 11.sp)
                        Text(
                            "+${settings.currencySymbol}${String.format(Locale.US, "%.2f", totalIngresos)}",
                            color = TileGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gastos", color = Color.LightGray, fontSize = 11.sp)
                        Text(
                            "-${settings.currencySymbol}${String.format(Locale.US, "%.2f", totalGastos)}",
                            color = TileSalmon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Balance Neto", color = Color.LightGray, fontSize = 11.sp)
                        Text(
                            "${settings.currencySymbol}${String.format(Locale.US, "%.2f", balance)}",
                            color = if (balance >= 0) GoldAccent else TileSalmon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            if (displayedMovements.isEmpty()) {
                CajaMaestraEmptyState(
                    icon = Icons.Default.SwapVert,
                    title = if (selectedFilter == null) "Sin Movimientos Extra" else "Sin Movimientos en Este Filtro",
                    description = "Registra ingresos adicionales o gastos operativos (servicios, mantenimiento, nómina) para mantener actualizado tu flujo neto.",
                    actionButtonText = when {
                        selectedFilter != null -> "Ver Todos"
                        canAddMovement -> "+ Registrar Movimiento"
                        else -> ""
                    },
                    onActionClick = {
                        if (selectedFilter != null) {
                            selectedFilter = null
                        } else if (canAddMovement) {
                            showAddDialog = true
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedMovements, key = { it.id }) { mov ->
                        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(mov.date))
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (mov.type == MovementType.INGRESO) TileGreen.copy(alpha = 0.15f) else TileSalmon.copy(alpha = 0.15f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (mov.type == MovementType.INGRESO) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = if (mov.type == MovementType.INGRESO) TileGreen else TileSalmon,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(mov.concept, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavyDark)
                                        Text("${mov.category} • $dateStr", fontSize = 11.sp, color = Color.Gray)
                                        if (mov.notes.isNotBlank()) {
                                            Text(mov.notes, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${if (mov.type == MovementType.INGRESO) "+" else "-"}${settings.currencySymbol}${String.format(Locale.US, "%.2f", mov.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (mov.type == MovementType.INGRESO) TileGreen else TileSalmon
                                    )
                                    if (permissions.canDeleteExtraMovement) {
                                        IconButton(onClick = { movementToDelete = mov }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = Color.LightGray, modifier = Modifier.size(16.dp))
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
        AddMovementDialog(
            onDismiss = { showAddDialog = false },
            onSave = { type, concept, category, amount, isCredit, notes ->
                viewModel.saveExtraMovement(
                    ExtraMovement(
                        type = type,
                        concept = concept,
                        category = category,
                        amount = amount,
                        isCredit = isCredit,
                        notes = notes
                    )
                )
                showAddDialog = false
            }
        )
    }

    movementToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { movementToDelete = null },
            title = { Text("Eliminar Movimiento", fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas eliminar '${target.concept}' (${settings.currencySymbol}${String.format(Locale.US, "%.2f", target.amount)})?", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExtraMovement(target)
                        movementToDelete = null
                        Toast.makeText(context, "Movimiento eliminado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TileSalmon)
                ) {
                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { movementToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun AddMovementDialog(
    onDismiss: () -> Unit,
    onSave: (MovementType, String, String, Double, Boolean, String) -> Unit
) {
    var type by remember { mutableStateOf(MovementType.GASTO) }
    var concept by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Servicios Básicos") }
    var amountText by remember { mutableStateOf("") }
    var isCredit by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Servicios Básicos", "Mantenimiento", "Publicidad", "Comisiones", "Transporte", "Nómina extra", "Otros")

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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Registrar Movimiento Extra", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NavyDark)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == MovementType.INGRESO,
                        onClick = { type = MovementType.INGRESO },
                        label = { Text("Ingreso (+)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TileGreen, selectedLabelColor = Color.White),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == MovementType.GASTO,
                        onClick = { type = MovementType.GASTO },
                        label = { Text("Gasto (-)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TileSalmon, selectedLabelColor = Color.White),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = concept,
                    onValueChange = {
                        concept = it
                        validationError = null
                    },
                    label = { Text("Concepto *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        validationError = null
                    },
                    label = { Text("Monto ($) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoría") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Referencia (opcional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isCredit, onCheckedChange = { isCredit = it })
                    Text("Movimiento a Crédito", fontSize = 13.sp)
                }

                validationError?.let { err ->
                    Text(err, color = TileSalmon, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            when {
                                concept.isBlank() -> {
                                    validationError = "El concepto es obligatorio."
                                }
                                amt == null || !amt.isFinite() || amt <= 0.0 -> {
                                    validationError = "Ingresa un monto válido mayor a cero."
                                }
                                else -> {
                                    onSave(type, concept.trim(), category.trim().ifEmpty { "Otros" }, amt, isCredit, notes.trim())
                                }
                            }
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
