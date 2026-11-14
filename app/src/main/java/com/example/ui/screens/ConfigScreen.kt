package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.UserRole
import com.example.ui.components.BluetoothPrinterDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.RecoveryResult
import com.example.ui.viewmodel.SecurityActionResult
import com.example.util.BluetoothPrinterManager
import com.example.util.RoleManager
import com.example.util.ThermalPrinterUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val securityState by viewModel.securityState.collectAsState()
    val lastSecurityError by viewModel.lastSecurityError.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var isTestingPrinter by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var targetRoleForPassword by remember { mutableStateOf(UserRole.TRABAJADOR) }
    var showRecoveryConfigDialog by remember { mutableStateOf(false) }
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    LaunchedEffect(lastSecurityError) {
        lastSecurityError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.clearSecurityError()
        }
    }

    Scaffold(
        topBar = {
            com.example.ui.components.CajaMaestraTopBar(
                title = "Configuración",
                subtitle = "Preferencias & Sistema",
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ADMIN MASTER CONTROL PANEL (Exclusively visible to Rol: Admin)
            if (permissions.canAccessAdminPanel) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BrandNavyElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandAmber.copy(alpha = 0.2f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("PANEL DE CONTROL ADMIN", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
                                    Text("Control Maestro de Licencia y Módulos", fontSize = 11.sp, color = TextLightSecondary)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // App Lock Status & Actions
                            Text(
                                text = "Estado de la Aplicación: ${if (settings.isAppLocked) "BLOQUEADA" else if (settings.trialModeEnabled) "MODO DE PRUEBA ACTIVO" else "DESBLOQUEADA / ACTIVA"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (settings.isAppLocked) TileSalmon else if (settings.trialModeEnabled) BrandAmber else BrandEmerald
                            )

                            if (settings.trialModeEnabled) {
                                Text(
                                    text = "Tiempo restante: ${viewModel.getTrialRemainingTime()}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons: Modo de Prueba (7 días o más) & Desbloqueo
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.setTrialMode(true, 7)
                                        Toast.makeText(context, "Modo de Prueba de 7 Días Activado", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandAmber),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Prueba 7 Días", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.setTrialMode(true, 15)
                                        Toast.makeText(context, "Modo de Prueba de 15 Días Activado", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Prueba 15 Días", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.unlockApp()
                                        Toast.makeText(context, "¡Aplicación Desbloqueada Ilimitadamente!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Desbloquear App", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.lockApp("Aplicación bloqueada por decisión del Administrador.")
                                        Toast.makeText(context, "Aplicación Bloqueada para usuarios", Toast.LENGTH_SHORT).show()
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, TileSalmon),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TileSalmon),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = TileSalmon, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bloquear App", color = TileSalmon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Text("Módulos Habilitados en la App (Admin):", color = BrandAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            ConfigSwitchRowDark(
                                title = "Módulo Inventario",
                                subtitle = "Productos, costos y combos",
                                checked = settings.moduleInventoryEnabled,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(moduleInventoryEnabled = it)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ConfigSwitchRowDark(
                                title = "Módulo Ventas (TPV)",
                                subtitle = "Facturación, cobro y tickets",
                                checked = settings.moduleSalesEnabled,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(moduleSalesEnabled = it)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ConfigSwitchRowDark(
                                title = "Módulo Compras",
                                subtitle = "Reabastecimiento y gastos",
                                checked = settings.modulePurchasesEnabled,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(modulePurchasesEnabled = it)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ConfigSwitchRowDark(
                                title = "Módulo Reportes Financieros",
                                subtitle = "Ganancias, gráficas y PDF",
                                checked = settings.moduleReportsEnabled,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(moduleReportsEnabled = it)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ConfigSwitchRowDark(
                                title = "Módulo Rentas y Agenda",
                                subtitle = "Alquiler temporal y depósitos",
                                checked = settings.moduleRentalsEnabled,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(moduleRentalsEnabled = it)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ConfigSwitchRowDark(
                                title = "Módulo Empleados y Roles",
                                subtitle = "Gestión de personal",
                                checked = settings.moduleEmployeesEnabled,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(moduleEmployeesEnabled = it)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ConfigSwitchRowDark(
                                title = "Módulo Impresora Térmica 58mm",
                                subtitle = "Impresión de recibos ESC/POS",
                                checked = settings.moduleThermalPrinterEnabled,
                                onCheckedChange = { viewModel.updateSettings(settings.copy(moduleThermalPrinterEnabled = it)) }
                            )
                        }
                    }
                }
            }

            // JEFE / DUEÑO CONTROL PANEL (Visible to Jefe and Admin)
            if (permissions.canAccessJefePanel) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlue.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandBlue.copy(alpha = 0.12f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Storefront, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("CONTROL DEL JEFE / DUEÑO", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BrandNavy)
                                    Text("Permitir o limitar funciones al rol Trabajador", fontSize = 11.sp, color = TextMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (settings.moduleInventoryEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede ver Inventario",
                                    subtitle = "Permite al cajero consultar catálogo de productos",
                                    checked = settings.jefeAllowsTrabajadorInventory,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorInventory = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.moduleSalesEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Realizar Ventas",
                                    subtitle = "Permite cobrar y emitir tickets térmicos",
                                    checked = settings.jefeAllowsTrabajadorSales,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorSales = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.modulePurchasesEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Realizar Compras",
                                    subtitle = "Registrar compras de mercancía a proveedores",
                                    checked = settings.jefeAllowsTrabajadorPurchases,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorPurchases = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.moduleCashflowEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Registrar Gastos Extra",
                                    subtitle = "Movimientos de egresos o caja chica",
                                    checked = settings.jefeAllowsTrabajadorCashflow,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorCashflow = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.moduleReportsEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Ver Reportes",
                                    subtitle = "Consultar utilidades y balances financieros",
                                    checked = settings.jefeAllowsTrabajadorReports,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorReports = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.moduleRentalsEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Rentas y Equipos",
                                    subtitle = "Control de préstamos y devoluciones",
                                    checked = settings.jefeAllowsTrabajadorRentals,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorRentals = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.moduleClientsEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Ver Clientes",
                                    subtitle = "Consultar directorio y saldos a favor",
                                    checked = settings.jefeAllowsTrabajadorClients,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorClients = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.moduleSuppliersEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Ver Proveedores",
                                    subtitle = "Consultar datos de contacto de proveedores",
                                    checked = settings.jefeAllowsTrabajadorSuppliers,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorSuppliers = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.moduleEmployeesEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Ver Empleados",
                                    subtitle = "Ver lista del equipo y roles",
                                    checked = settings.jefeAllowsTrabajadorEmployees,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorEmployees = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (settings.moduleSettingsEnabled) {
                                ConfigSwitchRow(
                                    title = "Trabajador puede Entrar a Configuración",
                                    subtitle = "Acceso a ajustes generales de la app",
                                    checked = settings.jefeAllowsTrabajadorSettings,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(jefeAllowsTrabajadorSettings = it)) }
                                )
                            }
                        }
                    }
                }
            }

            // GESTIÓN DE CONTRASEÑAS POR ROL (Admin y Jefe)
            if (permissions.canChangePasswords) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandAmber.copy(alpha = 0.15f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Password, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("GESTIÓN DE CONTRASEÑAS", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BrandNavy)
                                    Text("Actualice las claves maestras de cada rol", fontSize = 11.sp, color = TextMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (settings.currentRole == UserRole.ADMINISTRADOR) {
                                OutlinedButton(
                                    onClick = {
                                        targetRoleForPassword = UserRole.ADMINISTRADOR
                                        showPasswordDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = BrandAmber)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cambiar Clave: Admin", fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            OutlinedButton(
                                onClick = {
                                    targetRoleForPassword = UserRole.DUENO
                                    showPasswordDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = BrandBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cambiar Clave: Jefe (Dueño)", fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    targetRoleForPassword = UserRole.TRABAJADOR
                                    showPasswordDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = BrandEmerald)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cambiar Clave: Trabajador", fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { showRecoveryConfigDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manage_offline_recovery_button")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = BrandNavy)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Recuperación Offline (Código / Pregunta)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // General Section (Matches Screenshot 20)
            item {
                Text("General", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GoldDark)
            }

            item {
                ConfigNavRow(
                    title = "Símbolo de la moneda",
                    subtitle = "Símbolo Actual: ${settings.currencySymbol}",
                    onClick = { showCurrencyDialog = true }
                )
            }

            item {
                ConfigNavRow(
                    title = "Formato Numérico",
                    subtitle = "Formato Actual: ${settings.numberFormat}",
                    onClick = {
                        val nextFormat = if (settings.numberFormat.contains(",")) "9999999.99" else "9,999,999.99"
                        viewModel.updateSettings(settings.copy(numberFormat = nextFormat))
                    }
                )
            }

            item {
                ConfigNavRow(
                    title = "Tamaño Imagen",
                    subtitle = "300 Píxeles (óptimo para balance de almacenamiento en la app)",
                    onClick = {
                        Toast.makeText(context, "Optimización automática fija a 300 px para ticket y catálogo.", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                ConfigNavRow(
                    title = "Carpeta de descarga",
                    subtitle = ".../Download",
                    onClick = {
                        Toast.makeText(context, "Los reportes PDF/CSV y respaldos exportados se guardan en Descargas.", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (permissions.canAccessInventory) {
                item {
                    ConfigSwitchRow(
                        title = "Verificar existencias inventario",
                        subtitle = "Verificar la existencia en inventario de los elementos en las ventas",
                        checked = settings.checkStock,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(checkStock = it)) }
                    )
                }

                item {
                    ConfigSwitchRow(
                        title = "Usar costo promedio",
                        subtitle = "El costo promedio se utiliza para calcular las ganancias a partir del ingreso de nueva mercancía",
                        checked = settings.useAverageCost,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(useAverageCost = it)) }
                    )
                }
            }

            if (permissions.canAccessSales) {
                // Administrador - Vendedor Section (Matches Screenshot 19)
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Administrador - Vendedor", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GoldDark)
                }

                item {
                    ConfigSwitchRow(
                        title = "Ventas Múltiples",
                        subtitle = "Realizar varias ventas en paralelo agregando alias a cada venta para identificar",
                        checked = settings.allowMultipleSales,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(allowMultipleSales = it)) }
                    )
                }

                item {
                    ConfigSwitchRow(
                        title = "Editar fecha",
                        subtitle = "Modificar fecha en transacciones: ventas, compras, rentas, ingresos y gastos extras",
                        checked = settings.allowEditDate,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(allowEditDate = it)) }
                    )
                }

                item {
                    ConfigSwitchRow(
                        title = "Habilitar cambio y pago cliente",
                        subtitle = "Mostrar el pago del cliente y el cambio en el recibo de venta y en el punto de venta",
                        checked = settings.showCustomerPayAndChange,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(showCustomerPayAndChange = it)) }
                    )
                }
            }

            // Escáner Section (Matches Screenshot 18)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Escáner", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GoldDark)
            }

            item {
                ConfigSwitchRow(
                    title = "Usar escáner externo",
                    subtitle = "Habilitar soporte para lectores USB o Bluetooth de código de barras",
                    checked = settings.externalScanner,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(externalScanner = it)) }
                )
            }

            item {
                ConfigSwitchRow(
                    title = "Escáner Continuo",
                    subtitle = "Usar escáner continuo en ventas y compras",
                    checked = settings.continuousScan,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(continuousScan = it)) }
                )
            }

            item {
                ConfigSwitchRow(
                    title = "Usar cámara frontal (scanner)",
                    subtitle = "Por default se utiliza la cámara trasera para el escaneo",
                    checked = settings.frontCamera,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(frontCamera = it)) }
                )
            }

            // Restringir Acceso Section (Solo Dueño y Administrador)
            if (permissions.canAccessJefePanel || permissions.canAccessAdminPanel) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Restringir Acceso", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GoldDark)
                }

                item {
                    ConfigSwitchRow(
                        title = "Restringir Acceso",
                        subtitle = "Restringir acceso por PIN maestro a Reportes y Configuración",
                        checked = settings.restrictWithPin,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPinDialog = true
                            } else {
                                when (val res = viewModel.disablePinRestriction()) {
                                    is SecurityActionResult.Success -> Toast.makeText(
                                        context,
                                        "Restricción por PIN desactivada",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    is SecurityActionResult.Error -> Toast.makeText(
                                        context,
                                        res.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }

            // Impresión Bluetooth 58mm Section (Matches Screenshot 16 - Solo si el Admin la tiene habilitada)
            if (permissions.canConfigurePrinter) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Impresión Bluetooth (funcionamiento 58mm)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GoldDark)
                }

                item {
                    ConfigNavRow(
                        title = "Nombre impresora vinculada",
                        subtitle = if (settings.bluetoothPrinterAddress.isNotBlank()) {
                            "${settings.bluetoothPrinterName} (${settings.bluetoothPrinterAddress})"
                        } else {
                            "Toca para vincular y seleccionar tu impresora 58mm"
                        },
                        onClick = {
                            showPrinterDialog = true
                        }
                    )
                }

                item {
                    ConfigNavRow(
                        title = "Tipo de impresión",
                        subtitle = "Raster Bit Image (GS v 0) / ESC-POS 58mm (SPP)",
                        onClick = {
                            showPrinterDialog = true
                        }
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Escalamiento Impresión", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NavyDark)
                            Text("${String.format(java.util.Locale.US, "%.1f", settings.printScale)}", fontWeight = FontWeight.Bold, color = GoldDark)
                        }
                        Slider(
                            value = settings.printScale,
                            onValueChange = { viewModel.updateSettings(settings.copy(printScale = it)) },
                            valueRange = 0.8f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = GoldAccent, activeTrackColor = GoldAccent)
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Intensidad Color / Contraste", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NavyDark)
                            Text("${settings.printIntensity}", fontWeight = FontWeight.Bold, color = GoldDark)
                        }
                        Slider(
                            value = settings.printIntensity.toFloat(),
                            onValueChange = { viewModel.updateSettings(settings.copy(printIntensity = it.toInt())) },
                            valueRange = 100f..255f,
                            colors = SliderDefaults.colors(thumbColor = GoldAccent, activeTrackColor = GoldAccent)
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (settings.bluetoothPrinterAddress.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Por favor selecciona o vincula tu impresora 58mm primero",
                                    Toast.LENGTH_SHORT
                                ).show()
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
                                    val message = result.getOrElse { it.localizedMessage ?: "Error de conexión" }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isTestingPrinter,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingPrinter) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enviando a cabezal térmico...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Impresión 58mm", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Seguridad & Respaldo Offline (Solo si el Admin lo tiene habilitado)
            if (permissions.canAccessCloudBackup) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Seguridad y Respaldo Offline", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GoldDark)
                }

                item {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = TileGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cifrado AES-256-GCM Offline Activo", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyDark)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "La bóveda local y los archivos de respaldo (.cmbak) están protegidos con AES-256-GCM y derivación PBKDF2-HMAC-SHA256 sin dependencia de internet.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.performManualCloudSync() },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abrir Respaldo Local y Restauración (.cmbak)")
                            }
                        }
                    }
                }
            }

            // Eliminar Información Section (Matches Screenshot 16 - Exclusivo para Admin y Jefe)
            if (permissions.canWipeAllData) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Eliminar Información", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TileSalmon)
                }

                item {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Elimina irreversiblemente los registros operativos: inventario, movimientos de inventario, ventas, cotizaciones, compras, movimientos extra, rentas, turnos de caja, clientes, proveedores y empleados. Conserva configuración, contraseñas y folios históricos.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showWipeConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = TileSalmon),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Borrar Toda la Información", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Seleccionar Símbolo de Moneda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("$", "€", "Q", "L", "C$", "₡", "Bs.", "S/.").forEach { sym ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSettings(settings.copy(currencySymbol = sym))
                                    showCurrencyDialog = false
                                }
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = settings.currencySymbol == sym,
                                onClick = {
                                    viewModel.updateSettings(settings.copy(currencySymbol = sym))
                                    showCurrencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sym, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Cerrar") }
            }
        )
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var pinConfirmInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            modifier = Modifier.imePadding(),
            title = { Text("Definir PIN Maestro de Acceso") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter { ch -> ch.isDigit() }
                            pinError = null
                        },
                        label = { Text("Nuevo PIN (4 a 8 dígitos)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = {
                            pinConfirmInput = it.filter { ch -> ch.isDigit() }
                            pinError = null
                        },
                        label = { Text("Confirmar PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinError?.let { err ->
                        Text(err, color = TileSalmon, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput != pinConfirmInput) {
                            pinError = "Los PIN ingresados no coinciden."
                        } else {
                            when (val res = viewModel.enablePinRestriction(pinInput)) {
                                is SecurityActionResult.Success -> {
                                    showPinDialog = false
                                    Toast.makeText(context, "PIN maestro activado", Toast.LENGTH_SHORT).show()
                                }
                                is SecurityActionResult.Error -> {
                                    pinError = res.message
                                }
                            }
                        }
                    }
                ) {
                    Text("Activar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showWipeConfirmDialog) {
        var confirmIrreversible by remember { mutableStateOf(false) }
        var isWiping by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isWiping) showWipeConfirmDialog = false },
            title = { Text("¿Eliminar todos los datos?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Esta acción borrará de forma irreversible las 11 tablas operativas: inventario, historial de movimientos de inventario, ventas, cotizaciones, compras, movimientos extra, rentas/préstamos, turnos y movimientos de caja, clientes, proveedores y empleados.",
                        fontSize = 13.sp
                    )
                    Text(
                        "Se conservarán tu configuración del negocio, contraseñas/bóveda de seguridad y contadores históricos de folios.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isWiping) { confirmIrreversible = !confirmIrreversible }
                    ) {
                        Checkbox(
                            checked = confirmIrreversible,
                            onCheckedChange = { if (!isWiping) confirmIrreversible = it },
                            modifier = Modifier.testTag("confirm_wipe_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Confirmo que esta eliminación es irreversible",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isWiping) return@Button
                        isWiping = true
                        coroutineScope.launch {
                            val ok = viewModel.wipeAllDataAsync()
                            isWiping = false
                            if (ok) {
                                showWipeConfirmDialog = false
                                Toast.makeText(context, "Base de datos operativa restablecida", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No autorizado para borrar la información", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = confirmIrreversible && !isWiping,
                    colors = ButtonDefaults.buttonColors(containerColor = TileSalmon),
                    modifier = Modifier.testTag("confirm_wipe_btn")
                ) {
                    Text(if (isWiping) "Borrando..." else "Sí, Borrar Todo", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWipeConfirmDialog = false },
                    enabled = !isWiping
                ) { Text("Cancelar") }
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
                viewModel.updateSettings(
                    settings.copy(
                        bluetoothPrinterName = name,
                        bluetoothPrinterAddress = address
                    )
                )
                showPrinterDialog = false
                Toast.makeText(context, "Impresora seleccionada: $name", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showPasswordDialog) {
        var currentActorPassword by remember { mutableStateOf("") }
        var newPasswordInput by remember { mutableStateOf("") }
        var confirmPasswordInput by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            modifier = Modifier.imePadding(),
            title = {
                Text(
                    text = "Cambiar Contraseña - ${targetRoleForPassword.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BrandNavy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Por seguridad, confirma tu contraseña actual (${settings.currentRole.name}) antes de asignar la nueva clave.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    OutlinedTextField(
                        value = currentActorPassword,
                        onValueChange = {
                            currentActorPassword = it
                            passwordError = null
                        },
                        label = { Text("Tu Contraseña Actual") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = {
                            newPasswordInput = it
                            passwordError = null
                        },
                        label = { Text("Nueva Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = {
                            confirmPasswordInput = it
                            passwordError = null
                        },
                        label = { Text("Confirmar Nueva Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    passwordError?.let { err ->
                        Text(err, color = TileSalmon, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasswordInput != confirmPasswordInput) {
                            passwordError = "Las contraseñas nuevas no coinciden."
                        } else {
                            when (
                                val res = viewModel.updateRolePassword(
                                    targetRole = targetRoleForPassword,
                                    currentActorPassword = currentActorPassword,
                                    newPassword = newPasswordInput
                                )
                            ) {
                                is SecurityActionResult.Success -> {
                                    Toast.makeText(context, "Contraseña actualizada exitosamente", Toast.LENGTH_SHORT).show()
                                    showPasswordDialog = false
                                }
                                is SecurityActionResult.Error -> {
                                    passwordError = res.message
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                ) {
                    Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showRecoveryConfigDialog) {
        var currentActorPassword by remember { mutableStateOf("") }
        var newQuestion by remember { mutableStateOf(securityState.recoveryQuestion) }
        var newAnswer by remember { mutableStateOf("") }
        var recoveryError by remember { mutableStateOf<String?>(null) }
        var generatedCode by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRecoveryConfigDialog = false },
            modifier = Modifier.imePadding(),
            title = {
                Text(
                    text = "Recuperación Offline (${settings.currentRole.name})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BrandNavy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (generatedCode != null) {
                        Text(
                            text = "Guarda este nuevo código de recuperación de un solo uso en un lugar seguro:",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandNavyElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedCode!!,
                                color = BrandAmber,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Text(
                            text = "El código anterior ha sido invalidado.",
                            fontSize = 11.sp,
                            color = TextMedium
                        )
                    } else {
                        Text(
                            text = "Regenera tu código offline de respaldo y, si eres Dueño, opcionalmente actualiza la pregunta de seguridad.",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                        OutlinedTextField(
                            value = currentActorPassword,
                            onValueChange = {
                                currentActorPassword = it
                                recoveryError = null
                            },
                            label = { Text("Tu Contraseña Actual") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (settings.currentRole == UserRole.DUENO) {
                            OutlinedTextField(
                                value = newQuestion,
                                onValueChange = {
                                    newQuestion = it
                                    recoveryError = null
                                },
                                label = { Text("Pregunta de Seguridad (opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newAnswer,
                                onValueChange = {
                                    newAnswer = it
                                    recoveryError = null
                                },
                                label = { Text("Nueva Respuesta (opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        recoveryError?.let { err ->
                            Text(err, color = TileSalmon, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                if (generatedCode != null) {
                    Button(
                        onClick = { showRecoveryConfigDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Text("Ya lo guardé", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val qArg = if (settings.currentRole == UserRole.DUENO && newAnswer.isNotBlank()) newQuestion else null
                            val aArg = if (settings.currentRole == UserRole.DUENO && newAnswer.isNotBlank()) newAnswer else null
                            when (val res = viewModel.regenerateRecoveryMaterial(currentActorPassword, qArg, aArg)) {
                                is RecoveryResult.Success -> {
                                    generatedCode = res.newRecoveryCode
                                }
                                is RecoveryResult.Error -> {
                                    recoveryError = res.message
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Text("Generar Código", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (generatedCode == null) {
                    TextButton(onClick = { showRecoveryConfigDialog = false }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}

@Composable
private fun ConfigNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
            Spacer(modifier = Modifier.height(3.dp))
            Text(subtitle, fontSize = 13.sp, color = TextMedium)
        }
    }
}

@Composable
private fun ConfigSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                Spacer(modifier = Modifier.height(3.dp))
                Text(subtitle, fontSize = 13.sp, color = TextMedium)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandEmerald,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceSubtle
                )
            )
        }
    }
}

@Composable
private fun ConfigSwitchRowDark(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = TextLightSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BrandAmber,
                    checkedTrackColor = BrandAmber.copy(alpha = 0.4f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}
