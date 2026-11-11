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
import com.example.data.model.Client
import com.example.data.model.UserRole
import com.example.ui.components.CajaMaestraEmptyState
import com.example.ui.components.CajaMaestraTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.RoleManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val clients by viewModel.clients.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var clientToEdit by remember { mutableStateOf<Client?>(null) }

    val filteredClients = clients.filter {
        searchQuery.isBlank() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.company.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            CajaMaestraTopBar(
                title = "Clientes",
                subtitle = "Directorio y Saldos a Favor (${clients.size})",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer,
                searchQuery = searchQuery,
                isSearchVisible = isSearchExpanded,
                searchPlaceholder = "Buscar por nombre, empresa o teléfono...",
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = { isSearchExpanded = !isSearchExpanded },
                actions = {
                    if (permissions.canCreateClient) {
                        IconButton(onClick = {
                            clientToEdit = null
                            showAddDialog = true
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Agregar Cliente", tint = BrandAmber)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (permissions.canCreateClient) {
                FloatingActionButton(
                    onClick = {
                        clientToEdit = null
                        showAddDialog = true
                    },
                    containerColor = BrandEmerald,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_client_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Cliente", modifier = Modifier.size(28.dp))
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
            if (filteredClients.isEmpty()) {
                CajaMaestraEmptyState(
                    icon = Icons.Default.Group,
                    title = "Sin Clientes Registrados",
                    description = if (searchQuery.isNotBlank())
                        "No se encontraron resultados para '$searchQuery'."
                    else
                        "Registra a tus clientes frecuentes para llevar su historial de compras, créditos pendientes y facturación.",
                    actionButtonText = "+ Registrar Nuevo Cliente",
                    onActionClick = {
                        clientToEdit = null
                        showAddDialog = true
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredClients, key = { it.id }) { client ->
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
                                                .background(BrandEmeraldLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = BrandEmeraldDark,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = client.name,
                                                fontFamily = DisplayFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = TextDark
                                            )
                                            if (client.company.isNotBlank()) {
                                                Text(
                                                    text = client.company,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextMedium
                                                )
                                            }
                                        }
                                    }

                                    if (client.creditBalance > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = BrandAmberLight,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandAmber.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = "Debe: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", client.creditBalance)}",
                                                fontFamily = DisplayFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = BrandAmberDark,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = BrandEmeraldLight
                                        ) {
                                            Text(
                                                text = "Al Día",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandEmeraldDark,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                if (client.phone.isNotBlank() || client.address.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (client.phone.isNotBlank()) {
                                            Icon(Icons.Default.Phone, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(client.phone, fontSize = 12.sp, color = TextMedium)
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        if (client.address.isNotBlank()) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(client.address, fontSize = 12.sp, color = TextMuted, maxLines = 1)
                                        }
                                    }
                                }

                                if (client.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Nota: ${client.notes}", fontSize = 11.sp, color = TextMuted)
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = SurfaceHover)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (client.phone.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phone}"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = BrandEmerald)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            clientToEdit = client
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = BrandNavy)
                                    }
                                    if (permissions.canDeleteClient) {
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteClient(client)
                                                Toast.makeText(context, "Cliente eliminado", Toast.LENGTH_SHORT).show()
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

    if (showAddDialog) {
        AddEditClientDialog(
            initialClient = clientToEdit,
            onDismiss = { showAddDialog = false },
            onSave = { savedClient ->
                viewModel.saveClient(savedClient)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddEditClientDialog(
    initialClient: Client?,
    onDismiss: () -> Unit,
    onSave: (Client) -> Unit
) {
    var name by remember { mutableStateOf(initialClient?.name ?: "") }
    var company by remember { mutableStateOf(initialClient?.company ?: "") }
    var phone by remember { mutableStateOf(initialClient?.phone ?: "") }
    var email by remember { mutableStateOf(initialClient?.email ?: "") }
    var address by remember { mutableStateOf(initialClient?.address ?: "") }
    var creditBalance by remember { mutableStateOf(initialClient?.creditBalance?.toString() ?: "0.0") }
    var notes by remember { mutableStateOf(initialClient?.notes ?: "") }

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
                            text = if (initialClient == null || initialClient.id == 0L) "Nuevo Cliente" else "Editar Cliente",
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextDark
                        )
                        Text(
                            text = "Datos de contacto y cuenta",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre completo *") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Empresa o Negocio") },
                        shape = RoundedCornerShape(12.dp),
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
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = creditBalance,
                            onValueChange = { creditBalance = it },
                            label = { Text("Saldo Crédito ($)") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Dirección") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                                    (initialClient ?: Client(name = name)).copy(
                                        name = name.trim(),
                                        company = company.trim(),
                                        phone = phone.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        creditBalance = creditBalance.toDoubleOrNull() ?: 0.0,
                                        notes = notes.trim()
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar Cliente", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
