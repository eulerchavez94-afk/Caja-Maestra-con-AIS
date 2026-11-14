package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.DrawerContent
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.components.TutorialDialog
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.util.NotificationHelper
import com.example.util.RoleManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channel for critical stock & credit alerts
        NotificationHelper.createNotificationChannel(this)

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                val isSettingsLoaded by viewModel.isSettingsLoaded.collectAsState()
                val isUserAuthenticated by viewModel.isUserAuthenticated.collectAsState()
                val currentScreen by viewModel.currentScreen.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val showTutorialDialog by viewModel.showTutorialDialog.collectAsState()

                val showReceiptModal by viewModel.showReceiptModal.collectAsState()
                val lastSale by viewModel.lastCompletedSale.collectAsState()
                val lastItems by viewModel.lastSaleItems.collectAsState()

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                if (!isSettingsLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(com.example.ui.theme.HudGradient),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator(color = com.example.ui.theme.BrandAmber)
                    }
                } else if (!isUserAuthenticated) {
                    InitialLockScreen(viewModel = viewModel)
                } else {
                    // Hardware back handler
                    BackHandler(enabled = drawerState.isOpen || currentScreen != AppScreen.INVENTARIO) {
                        if (drawerState.isOpen) {
                            scope.launch { drawerState.close() }
                        } else if (currentScreen != AppScreen.INVENTARIO) {
                            viewModel.navigateTo(AppScreen.INVENTARIO)
                        }
                    }

                    var showPinLockModal by remember { mutableStateOf(false) }
                    var pendingRestrictedScreen by remember { mutableStateOf<AppScreen?>(null) }
                    var pinAttempt by remember { mutableStateOf("") }
                    var pinError by remember { mutableStateOf(false) }

                    // Automatic safeguard: If current module was deactivated by Admin, redirect immediately
                    LaunchedEffect(currentScreen, settings) {
                        if (!RoleManager.canAccessScreen(currentScreen, settings)) {
                            val fallback = RoleManager.getFirstPermittedScreen(settings)
                            viewModel.navigateTo(fallback)
                        }
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            DrawerContent(
                                settings = settings,
                                currentScreen = currentScreen,
                                isModulePermitted = { screen -> viewModel.isModulePermitted(screen) },
                                onNavigate = { screen ->
                                    val isAllowed = viewModel.isModulePermitted(screen)
                                    if (!isAllowed) {
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            "Acceso denegado: El módulo ha sido limitado para su rol.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val isRestricted = screen == AppScreen.REPORTES || screen == AppScreen.CONFIGURACION || screen == AppScreen.EMPLEADOS
                                        if (settings.restrictWithPin && isRestricted && settings.currentRole == com.example.data.model.UserRole.TRABAJADOR) {
                                            pendingRestrictedScreen = screen
                                            pinAttempt = ""
                                            pinError = false
                                            showPinLockModal = true
                                        } else {
                                            viewModel.navigateTo(screen)
                                        }
                                    }
                                },
                                onShowTutorial = {
                                    viewModel.setShowTutorialDialog(true)
                                },
                                onLogout = {
                                    viewModel.logout()
                                },
                                onCloseDrawer = {
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    ) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            when (currentScreen) {
                                AppScreen.INVENTARIO -> {
                                    InventoryScreen(
                                        viewModel = viewModel,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onShowTutorial = { viewModel.setShowTutorialDialog(true) }
                                    )
                                }
                                AppScreen.VENTAS -> {
                                    SalesScreen(
                                        viewModel = viewModel,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onShowTutorial = { viewModel.setShowTutorialDialog(true) }
                                    )
                                }
                            AppScreen.COMPRAS -> {
                                PurchasesScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.INGRESOS_GASTOS -> {
                                ExtraMovementsScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.RENTAS -> {
                                RentalsScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.REPORTES -> {
                                ReportsScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onShowTutorial = { viewModel.setShowTutorialDialog(true) }
                                )
                            }
                            AppScreen.EMPLEADOS -> {
                                EmployeesScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.CLIENTES -> {
                                ClientsScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.PROVEEDORES -> {
                                SuppliersScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.VINCULAR_WEB -> {
                                CloudBackupScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.RECIBO_TICKET -> {
                                ReceiptConfigScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.CONFIGURACION -> {
                                ConfigScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            AppScreen.AGENDA -> {
                                AgendaScreen(
                                    viewModel = viewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                        }
                    }
                }

                // 58mm Thermal Ticket Preview Modal
                if (showReceiptModal && lastSale != null) {
                    ReceiptPreviewDialog(
                        settings = settings,
                        sale = lastSale!!,
                        items = lastItems,
                        onDismiss = { viewModel.closeReceiptModal() },
                        onCancelSale = {
                            viewModel.cancelSale(lastSale!!)
                            viewModel.closeReceiptModal()
                        },
                        onPrinterSelected = { name, address ->
                            viewModel.updateSettings(
                                settings.copy(
                                    bluetoothPrinterName = name,
                                    bluetoothPrinterAddress = address
                                )
                            )
                        }
                    )
                }

                // Tutorial Help Dialog
                if (showTutorialDialog) {
                    TutorialDialog(onDismiss = { viewModel.setShowTutorialDialog(false) })
                }

                // Security PIN Lock Verification Dialog
                if (showPinLockModal) {
                    AlertDialog(
                        modifier = Modifier.imePadding(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        onDismissRequest = {
                            showPinLockModal = false
                            pendingRestrictedScreen = null
                        },
                        icon = {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = com.example.ui.theme.BrandAmber, modifier = Modifier.size(28.dp))
                        },
                        title = {
                            Text("Acceso Restringido", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        },
                        text = {
                            Column {
                                Text(
                                    "Esta sección requiere el PIN de Dueño o Administrador para continuar:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = pinAttempt,
                                    onValueChange = {
                                        pinAttempt = it
                                        pinError = false
                                    },
                                    label = { Text("PIN de seguridad (4 dígitos)") },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    isError = pinError,
                                    supportingText = if (pinError) {
                                        { Text("PIN incorrecto. Acceso denegado.", color = MaterialTheme.colorScheme.error) }
                                    } else null,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (viewModel.verifyMasterPin(pinAttempt)) {
                                        pendingRestrictedScreen?.let { viewModel.navigateTo(it) }
                                        showPinLockModal = false
                                        pendingRestrictedScreen = null
                                    } else {
                                        pinError = true
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.BrandEmerald)
                            ) {
                                Text("Verificar", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showPinLockModal = false
                                    pendingRestrictedScreen = null
                                }
                            ) {
                                Text("Cancelar", color = com.example.ui.theme.TextMedium)
                            }
                        }
                    )
                }
            }
        }
    }
}
}
