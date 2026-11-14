package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Employee
import com.example.data.model.UserRole
import com.example.ui.components.CajaMaestraEmptyState
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SecurityActionResult
import com.example.util.PasswordHasher
import com.example.util.RoleManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val employees by viewModel.employees.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val lastSecurityError by viewModel.lastSecurityError.collectAsState()
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var employeeToEdit by remember { mutableStateOf<Employee?>(null) }
    var employeeToDelete by remember { mutableStateOf<Employee?>(null) }

    var pendingRoleSwitch by remember { mutableStateOf<UserRole?>(null) }
    var roleSwitchCredential by remember { mutableStateOf("") }
    var roleSwitchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lastSecurityError) {
        lastSecurityError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.clearSecurityError()
        }
    }

    Scaffold(
        topBar = {
            com.example.ui.components.CajaMaestraTopBar(
                title = "Empleados & Roles",
                subtitle = "Usuario Activo: ${settings.currentUserName} (${settings.currentRole.name})",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer,
                actions = {
                    if (permissions.canCreateEmployee) {
                        IconButton(onClick = {
                            employeeToEdit = null
                            showAddEditDialog = true
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Agregar", tint = BrandAmber)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (permissions.canCreateEmployee) {
                FloatingActionButton(
                    onClick = {
                        employeeToEdit = null
                        showAddEditDialog = true
                    },
                    containerColor = BrandEmerald,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_employee_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Empleado", modifier = Modifier.size(28.dp))
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
            // Active Session Switcher Card (Requires credential authorization)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BrandNavyElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Cambiar Rol Activo en Esta Sesión (Requiere Autorización):",
                        color = TextLightSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserRole.values().forEach { role ->
                            val isSelected = settings.currentRole == role
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    when (role) {
                                        UserRole.DUENO -> BrandAmber
                                        UserRole.ADMINISTRADOR -> BrandBlue
                                        UserRole.TRABAJADOR -> BrandEmerald
                                    }
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("switch_role_${role.name}")
                                    .clickable {
                                        if (role != settings.currentRole) {
                                            pendingRoleSwitch = role
                                            roleSwitchCredential = ""
                                            roleSwitchError = null
                                        }
                                    }
                            ) {
                                Text(
                                    text = role.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) {
                                        if (role == UserRole.DUENO) BrandNavy else Color.White
                                    } else Color.White,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (employees.isEmpty()) {
                CajaMaestraEmptyState(
                    icon = Icons.Default.Badge,
                    title = "Sin Empleados Registrados",
                    description = "Registra a tus empleados para asignar roles, salario y PIN personal de acceso rápido.",
                    actionButtonText = if (permissions.canCreateEmployee) "+ Registrar Empleado" else "",
                    onActionClick = {
                        if (permissions.canCreateEmployee) {
                            employeeToEdit = null
                            showAddEditDialog = true
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(employees, key = { it.id }) { emp ->
                    val canManageTarget = RoleManager.canManageEmployeeRole(settings.currentRole, emp.role)
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = when (emp.role) {
                                            UserRole.DUENO -> GoldAccent
                                            UserRole.ADMINISTRADOR -> NavyPrimary
                                            UserRole.TRABAJADOR -> TileTeal
                                        },
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = emp.name.take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(emp.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyDark)
                                        Text("${emp.position} • Rol: ${emp.role.name}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (emp.role) {
                                        UserRole.DUENO -> GoldAccent.copy(alpha = 0.2f)
                                        UserRole.ADMINISTRADOR -> NavyPrimary.copy(alpha = 0.15f)
                                        UserRole.TRABAJADOR -> TileTeal.copy(alpha = 0.15f)
                                    }
                                ) {
                                    Text(
                                        text = emp.role.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (emp.role) {
                                            UserRole.DUENO -> GoldDark
                                            UserRole.ADMINISTRADOR -> NavyPrimary
                                            UserRole.TRABAJADOR -> TileTeal
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    if (emp.salary > 0) {
                                        Text(
                                            "Salario: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", emp.salary)}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = TileGreen
                                        )
                                    }
                                    if (emp.phone.isNotBlank()) Text("Tel: ${emp.phone}", fontSize = 12.sp, color = Color.DarkGray)
                                    Text(
                                        text = if (emp.pin.isNotBlank()) "PIN Personal: Configurado" else "Sin PIN de acceso directo",
                                        fontSize = 11.sp,
                                        color = if (emp.pin.isNotBlank()) BrandEmerald else Color.Gray
                                    )
                                }

                                if (canManageTarget && (permissions.canCreateEmployee || permissions.canDeleteEmployee)) {
                                    Row {
                                        if (permissions.canCreateEmployee) {
                                            IconButton(
                                                onClick = {
                                                    employeeToEdit = emp
                                                    showAddEditDialog = true
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.Gray)
                                            }
                                        }
                                        if (permissions.canDeleteEmployee) {
                                            IconButton(
                                                onClick = { employeeToDelete = emp }
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = TileSalmon)
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
    }

    // Role Switch Authorization Dialog
    pendingRoleSwitch?.let { targetRole ->
        AlertDialog(
            onDismissRequest = { pendingRoleSwitch = null },
            modifier = Modifier
                .imePadding()
                .testTag("role_switch_dialog"),
            title = {
                Text(
                    text = "Autorizar Cambio a ${targetRole.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = BrandNavy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ingresa la contraseña maestra del rol ${targetRole.name} o el PIN personal de un empleado con ese rol.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    OutlinedTextField(
                        value = roleSwitchCredential,
                        onValueChange = {
                            roleSwitchCredential = it
                            roleSwitchError = null
                        },
                        label = { Text("Contraseña o PIN de ${targetRole.name}") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("role_switch_password_input")
                    )
                    roleSwitchError?.let { err ->
                        Text(
                            text = err,
                            color = TileSalmon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (val res = viewModel.switchActiveRoleWithCredential(targetRole, roleSwitchCredential)) {
                            is SecurityActionResult.Success -> {
                                pendingRoleSwitch = null
                                Toast.makeText(
                                    context,
                                    "Sesión cambiada a ${targetRole.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is SecurityActionResult.Error -> {
                                roleSwitchError = res.message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                    modifier = Modifier.testTag("role_switch_confirm_button")
                ) {
                    Text("Autorizar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRoleSwitch = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Add / Edit Employee Dialog
    if (showAddEditDialog) {
        AddEditEmployeeDialog(
            actorRole = settings.currentRole,
            initialEmployee = employeeToEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedEmp ->
                viewModel.saveEmployee(savedEmp)
                showAddEditDialog = false
            }
        )
    }

    employeeToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { employeeToDelete = null },
            title = { Text("Eliminar Empleado", fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas eliminar a '${target.name}' (${target.role.name})?", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEmployee(target)
                        employeeToDelete = null
                        Toast.makeText(context, "Empleado eliminado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TileSalmon)
                ) {
                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { employeeToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun AddEditEmployeeDialog(
    actorRole: UserRole = UserRole.DUENO,
    initialEmployee: Employee?,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit
) {
    val allowedRoles = remember(actorRole) {
        UserRole.values().filter { RoleManager.canManageEmployeeRole(actorRole, it) }
    }
    var name by remember { mutableStateOf(initialEmployee?.name ?: "") }
    var position by remember { mutableStateOf(initialEmployee?.position ?: "") }
    var salaryText by remember { mutableStateOf(initialEmployee?.salary?.toString() ?: "") }
    var phone by remember { mutableStateOf(initialEmployee?.phone ?: "") }
    var email by remember { mutableStateOf(initialEmployee?.email ?: "") }
    var address by remember { mutableStateOf(initialEmployee?.address ?: "") }
    var role by remember {
        mutableStateOf(
            initialEmployee?.role?.takeIf { RoleManager.canManageEmployeeRole(actorRole, it) }
                ?: UserRole.TRABAJADOR
        )
    }
    var pin by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun validateAndSave() {
        val cleanName = name.trim()
        val cleanPin = pin.trim()
        val parsedSalary = if (salaryText.isBlank()) 0.0 else salaryText.toDoubleOrNull()
        if (cleanName.isEmpty()) {
            validationError = "El nombre es obligatorio."
            return
        }
        if (parsedSalary == null || !parsedSalary.isFinite() || parsedSalary < 0.0) {
            validationError = "El salario debe ser un número válido >= 0."
            return
        }
        if (cleanPin.isNotEmpty()) {
            if (cleanPin.length < 4 || !cleanPin.all { it.isDigit() }) {
                validationError = "El PIN personal debe tener al menos 4 dígitos numéricos."
                return
            }
            if (PasswordHasher.isLegacyDefaultCredential(cleanPin)) {
                validationError = "Por seguridad, no uses el PIN predeterminado $cleanPin."
                return
            }
        }
        onSave(
            (initialEmployee ?: Employee(name = cleanName)).copy(
                name = cleanName,
                position = position.trim(),
                salary = parsedSalary,
                phone = phone.trim(),
                email = email.trim(),
                address = address.trim(),
                role = role,
                pin = cleanPin
            )
        )
    }

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
                .testTag("add_employee_dialog")
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
                    Text(
                        text = if (initialEmployee == null || initialEmployee.id == 0L) "Empleado" else "Editar Empleado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = NavyDark
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFD4AF37),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { validateAndSave() }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Guardar", tint = Color.White, modifier = Modifier.padding(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            validationError = null
                        },
                        label = { Text("Nombre *") },
                        placeholder = { Text("Obligatorio", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = position,
                        onValueChange = { position = it },
                        label = { Text("Puesto") },
                        placeholder = { Text("Ej. Cajero, Encargado", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = salaryText,
                        onValueChange = { salaryText = it },
                        label = { Text("Salario ($)") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Role Picker filtered by actor authority
                    Text("Rol de Permisos:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allowedRoles.forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = { role = r },
                                label = { Text(r.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldAccent,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it.filter { ch -> ch.isDigit() }
                            validationError = null
                        },
                        label = {
                            Text(
                                if (initialEmployee?.pin?.isNotBlank() == true) {
                                    "Nuevo PIN (dejar vacío para conservar actual)"
                                } else {
                                    "PIN de Acceso Personal (4+ dígitos, opcional)"
                                }
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo") },
                        leadingIcon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Dirección") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    validationError?.let { err ->
                        Text(
                            text = err,
                            color = TileSalmon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { validateAndSave() },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                    ) {
                        Text("Guardar Empleado", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
