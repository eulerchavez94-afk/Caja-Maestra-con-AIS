package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.SaleStatus
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AgendaTask(
    val title: String,
    val subtitle: String,
    val dueDate: Long,
    val amount: Double,
    val category: String, // CLIENTE_CREDITO, RENTA, PROVEEDOR
    val isUrgent: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val sales by viewModel.sales.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val rentalsLoans by viewModel.rentalsLoans.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedCategory by remember { mutableStateOf("TODAS") }

    val tasks = remember(sales, purchases, rentalsLoans) {
        val list = mutableListOf<AgendaTask>()
        val now = System.currentTimeMillis()

        // Sales on credit pending collection
        sales.filter { it.status == SaleStatus.CREDITO }.forEach { s ->
            val due = s.date + 15L * 24 * 3600 * 1000 // 15 days credit
            val pendingAmount = (s.total - s.paidAmount.coerceAtLeast(0.0)).coerceAtLeast(0.0)
            list.add(
                AgendaTask(
                    title = "Cobro a Cliente: ${s.customerName}",
                    subtitle = "Venta Folio ${s.folio} a Crédito",
                    dueDate = due,
                    amount = pendingAmount,
                    category = "CLIENTE_CREDITO",
                    isUrgent = due - now < 3L * 24 * 3600 * 1000
                )
            )
        }

        // Rentals of equipment/items to return
        rentalsLoans.filter { !it.isReturnedOrPaid && it.isRental }.forEach { r ->
            list.add(
                AgendaTask(
                    title = "Devolución de Renta: ${r.itemOrReason}",
                    subtitle = "Cliente: ${r.contactName}",
                    dueDate = r.dueDate,
                    amount = r.amount,
                    category = "RENTA",
                    isUrgent = r.dueDate - now < 2L * 24 * 3600 * 1000
                )
            )
        }

        // Purchases on credit pending payment to suppliers
        purchases.filter { it.isCredit }.forEach { p ->
            val due = p.date + 30L * 24 * 3600 * 1000
            list.add(
                AgendaTask(
                    title = "Pago a Proveedor: ${p.supplierName}",
                    subtitle = "Orden de Compra ${p.folio}",
                    dueDate = due,
                    amount = p.total,
                    category = "PROVEEDOR",
                    isUrgent = due - now < 5L * 24 * 3600 * 1000
                )
            )
        }

        list.sortedBy { it.dueDate }
    }

    val filteredTasks = tasks.filter {
        selectedCategory == "TODAS" || it.category == selectedCategory
    }

    Scaffold(
        topBar = {
            com.example.ui.components.CajaMaestraTopBar(
                title = "Agenda & Vencimientos",
                subtitle = "${filteredTasks.size} compromisos y retornos pendientes",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CanvasLight)
        ) {
            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "TODAS" to "Todas",
                    "CLIENTE_CREDITO" to "Cobros",
                    "RENTA" to "Rentas",
                    "PROVEEDOR" to "Proveedores"
                ).forEach { (cat, label) ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldAccent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            if (filteredTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay compromisos o vencimientos próximos.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTasks) { task ->
                        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(task.dueDate))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(task.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyDark)
                                        Text(task.subtitle, fontSize = 12.sp, color = Color.Gray)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (task.isUrgent) TileSalmon.copy(alpha = 0.15f) else GoldAccent.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (task.isUrgent) "PRÓXIMO A VENCER" else "PROGRAMADO",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (task.isUrgent) TileSalmon else GoldDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Event, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fecha límite: $dateStr", fontSize = 12.sp, color = Color.DarkGray)
                                    }

                                    Text(
                                        text = "${settings.currencySymbol}${String.format(Locale.US, "%.2f", task.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (task.category == "PROVEEDOR") TileSalmon else TileGreen
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
