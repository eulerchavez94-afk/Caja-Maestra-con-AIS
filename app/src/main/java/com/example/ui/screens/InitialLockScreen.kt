package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.backup.BackupOperationResult
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.InitialSecuritySetupResult
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.RecoveryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de Bloqueo Inicial de Caja Maestra con:
 * - Provisión inicial segura sin contraseñas predeterminadas ni puertas traseras.
 * - Autenticación por rol o empleado registrado con PIN personal cifrado.
 * - Recuperación 100% offline mediante código de recuperación único o pregunta de seguridad.
 */
@Composable
fun InitialLockScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val securityState by viewModel.securityState.collectAsState()
    val loginError by viewModel.lastLoginError.collectAsState()
    val selectedBackupInspection by viewModel.selectedBackupInspection.collectAsState()
    val selectedBackupBytes by viewModel.selectedBackupBytes.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedRolePreset by remember { mutableStateOf("") }

    var showRecoveryDialog by remember { mutableStateOf(false) }
    var generatedCodesPair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showFreshRestoreDialog by remember { mutableStateOf(false) }
    var freshRestorePassphrase by remember { mutableStateOf("") }
    var freshRestoreError by remember { mutableStateOf<String?>(null) }
    var isRestoringFresh by remember { mutableStateOf(false) }

    val openFreshBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val readRes = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "respaldo.cmbak"
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("No se pudo abrir el archivo.")
                    fileName to input.use { it.readBytes() }
                }
            }
            readRes.onSuccess { (name, bytes) ->
                val inspection = viewModel.inspectSelectedBackupBytes(bytes, name)
                freshRestoreError = if (inspection.isValidForRestore) null else inspection.statusMessage
                freshRestorePassphrase = ""
                showFreshRestoreDialog = true
            }.onFailure { err ->
                Toast.makeText(context, "Error leyendo archivo: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val isAppLocked = settings.isAppLocked || viewModel.isTrialExpired()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudGradient)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = BrandAmber.copy(alpha = 0.15f),
                border = BorderStroke(2.dp, BrandAmber),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bloqueo de Seguridad",
                        tint = BrandAmber,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "CAJA MAESTRA POS",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Text(
                text = if (!securityState.isInitialized) {
                    "Configuración Inicial de Seguridad Offline"
                } else {
                    "Control de Acceso por Roles & Licencia"
                },
                fontSize = 13.sp,
                color = TextLightSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isAppLocked) TileSalmon.copy(alpha = 0.2f) else if (settings.trialModeEnabled) BrandAmber.copy(alpha = 0.2f) else BrandEmerald.copy(alpha = 0.2f),
                border = BorderStroke(
                    1.dp,
                    if (isAppLocked) TileSalmon else if (settings.trialModeEnabled) BrandAmber else BrandEmerald
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAppLocked) Icons.Default.LockClock else if (settings.trialModeEnabled) Icons.Default.Timer else Icons.Default.Verified,
                        contentDescription = null,
                        tint = if (isAppLocked) TileSalmon else if (settings.trialModeEnabled) BrandAmber else BrandEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAppLocked) {
                            "ESTADO: APLICACIÓN BLOQUEADA"
                        } else if (settings.trialModeEnabled) {
                            "MODO PRUEBA: ${viewModel.getTrialRemainingTime()}"
                        } else {
                            "SISTEMA ACTIVO / PROTEGIDO"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAppLocked) TileSalmon else if (settings.trialModeEnabled) BrandAmber else BrandEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (!securityState.isInitialized) {
                InitialSecuritySetupCard(
                    onSetupCompleted = { adminCode, ownerCode ->
                        generatedCodesPair = ownerCode to adminCode
                    },
                    onSubmitSetup = { adminPass, jefePass, trabPass, pin, question, answer ->
                        viewModel.initializeSecuritySetup(
                            adminPassword = adminPass,
                            jefePassword = jefePass,
                            trabajadorPassword = trabPass,
                            masterPin = pin,
                            recoveryQuestion = question,
                            recoveryAnswer = answer
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { openFreshBackupLauncher.launch(arrayOf("*/*")) },
                    border = BorderStroke(1.dp, BrandAmber.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("initial_restore_backup_button")
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = BrandAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "¿Ya tienes un respaldo? Restaurar (.cmbak)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceWhite,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Text(
                            text = "Seleccione su Rol o Ingrese Usuario / Empleado",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoleQuickButton(
                                title = "Admin",
                                subtitle = "Control Total",
                                icon = Icons.Default.Security,
                                isSelected = selectedRolePreset == "Admin",
                                accentColor = BrandAmber,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedRolePreset = "Admin"
                                    username = "Admin"
                                    password = ""
                                }
                            )

                            RoleQuickButton(
                                title = "Jefe",
                                subtitle = "Dueño Tienda",
                                icon = Icons.Default.Storefront,
                                isSelected = selectedRolePreset == "Jefe",
                                accentColor = BrandBlue,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedRolePreset = "Jefe"
                                    username = "Jefe"
                                    password = ""
                                }
                            )

                            RoleQuickButton(
                                title = "Trabajador",
                                subtitle = "Operativo",
                                icon = Icons.Default.Badge,
                                isSelected = selectedRolePreset == "Trabajador",
                                accentColor = BrandEmerald,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedRolePreset = "Trabajador"
                                    username = "Trabajador"
                                    password = ""
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                selectedRolePreset = ""
                            },
                            label = { Text("Usuario, Rol o Nombre de Empleado") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = BrandNavy)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_username_input"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña o PIN personal") },
                            leadingIcon = {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = BrandNavy)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Ver u ocultar contraseña"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                val success = viewModel.authenticateUser(username, password)
                                if (success) {
                                    Toast.makeText(context, "Bienvenido: $username", Toast.LENGTH_SHORT).show()
                                }
                            })
                        )

                        if (loginError != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TileSalmon.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, TileSalmon.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = TileSalmon, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = loginError ?: "",
                                        color = TileSalmon,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val success = viewModel.authenticateUser(username, password)
                                if (success) {
                                    Toast.makeText(context, "Acceso concedido", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (selectedRolePreset) {
                                    "Admin" -> BrandAmber
                                    "Jefe" -> BrandBlue
                                    else -> BrandEmerald
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button")
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, tint = if (selectedRolePreset == "Admin") Color.Black else Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Acceder a Caja Maestra",
                                color = if (selectedRolePreset == "Admin") Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = { showRecoveryDialog = true },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("offline_recovery_button")
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "¿Olvidaste tu contraseña? Recuperación Offline",
                                color = BrandBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (generatedCodesPair != null) {
        val (ownerCode, adminCode) = generatedCodesPair!!
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = BrandAmber) },
            title = { Text("Códigos de Recuperación Offline", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Guarda estos códigos en un lugar seguro y fuera del teléfono. Solo se muestran una vez y sirven para recuperar acceso sin internet.",
                        fontSize = 12.sp,
                        color = TextMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceSubtle,
                        border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Código Dueño / Jefe:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(ownerCode, fontSize = 15.sp, fontWeight = FontWeight.Black, color = BrandNavy)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceSubtle,
                        border = BorderStroke(1.dp, BrandAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Código Administrador:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(adminCode, fontSize = 15.sp, fontWeight = FontWeight.Black, color = BrandNavy)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { generatedCodesPair = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                ) {
                    Text("Ya los guardé, Continuar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showRecoveryDialog) {
        OfflineRecoveryDialog(
            recoveryQuestion = securityState.recoveryQuestion,
            onDismiss = { showRecoveryDialog = false },
            onRecoverWithAnswer = { answer, newPass ->
                viewModel.recoverOwnerWithSecurityAnswer(answer, newPass)
            },
            onRecoverWithCode = { role, code, newPass ->
                viewModel.recoverRoleWithCode(role, code, newPass)
            }
        )
    }

    if (showFreshRestoreDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isRestoringFresh) {
                    showFreshRestoreDialog = false
                    viewModel.clearSelectedBackup()
                }
            },
            icon = { Icon(Icons.Default.RestorePage, contentDescription = null, tint = BrandEmerald) },
            title = { Text("Restaurar en Dispositivo Nuevo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedBackupInspection?.let { insp ->
                        Text(
                            text = "Archivo: ${insp.fileName} (${insp.sizeBytes} bytes)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandNavy
                        )
                        Text(
                            text = insp.statusMessage,
                            fontSize = 12.sp,
                            color = if (insp.isValidForRestore) BrandEmeraldDark else TileSalmon
                        )
                    }
                    if (selectedBackupInspection?.isValidForRestore == true) {
                        OutlinedTextField(
                            value = freshRestorePassphrase,
                            onValueChange = { freshRestorePassphrase = it; freshRestoreError = null },
                            label = { Text("Contraseña del respaldo") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isRestoringFresh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fresh_restore_passphrase_input")
                        )
                    }
                    if (!freshRestoreError.isNullOrBlank()) {
                        Text(freshRestoreError!!, color = TileSalmon, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    if (isRestoringFresh) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (selectedBackupInspection?.isValidForRestore == true) {
                    Button(
                        enabled = !isRestoringFresh && selectedBackupBytes != null,
                        onClick = {
                            val bytes = selectedBackupBytes ?: return@Button
                            isRestoringFresh = true
                            freshRestoreError = null
                            coroutineScope.launch {
                                val res = viewModel.executeFullRestore(bytes, freshRestorePassphrase)
                                isRestoringFresh = false
                                when (res) {
                                    is BackupOperationResult.RestoreSuccess -> {
                                        showFreshRestoreDialog = false
                                        Toast.makeText(
                                            context,
                                            "${res.message} Inicia sesión con tus credenciales restauradas.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is BackupOperationResult.Error -> {
                                        freshRestoreError = res.message
                                    }
                                    is BackupOperationResult.ExportSuccess -> Unit
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Text("Restaurar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRestoringFresh,
                    onClick = {
                        showFreshRestoreDialog = false
                        viewModel.clearSelectedBackup()
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun InitialSecuritySetupCard(
    onSetupCompleted: (adminRecoveryCode: String, ownerRecoveryCode: String) -> Unit,
    onSubmitSetup: (
        adminPass: String,
        jefePass: String,
        trabPass: String,
        pin: String,
        question: String,
        answer: String
    ) -> InitialSecuritySetupResult
) {
    var adminPass by remember { mutableStateOf("") }
    var jefePass by remember { mutableStateOf("") }
    var trabPass by remember { mutableStateOf("") }
    var masterPin by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("¿Nombre de tu negocio o palabra de respaldo?") }
    var answer by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceWhite,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Protege tu negocio antes de comenzar",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = BrandNavy
            )
            Text(
                text = "Define tus contraseñas privadas y método de recuperación offline. No existen claves universales.",
                fontSize = 12.sp,
                color = TextMedium
            )

            OutlinedTextField(
                value = adminPass,
                onValueChange = { adminPass = it; setupError = null },
                label = { Text("Contraseña Administrador (mín. 6)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_admin_password")
            )

            OutlinedTextField(
                value = jefePass,
                onValueChange = { jefePass = it; setupError = null },
                label = { Text("Contraseña Dueño / Jefe (mín. 6)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_jefe_password")
            )

            OutlinedTextField(
                value = trabPass,
                onValueChange = { trabPass = it; setupError = null },
                label = { Text("Contraseña Trabajador (mín. 4)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_trabajador_password")
            )

            OutlinedTextField(
                value = masterPin,
                onValueChange = { masterPin = it.filter { ch -> ch.isDigit() }.take(8); setupError = null },
                label = { Text("PIN Maestro (4-8 dígitos, distinto de 1234)") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_master_pin")
            )

            OutlinedTextField(
                value = question,
                onValueChange = { question = it; setupError = null },
                label = { Text("Pregunta de recuperación del Dueño") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_recovery_question")
            )

            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it; setupError = null },
                label = { Text("Respuesta secreta de recuperación") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_recovery_answer")
            )

            if (setupError != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TileSalmon.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, TileSalmon.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = setupError ?: "",
                        color = TileSalmon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = {
                    when (val result = onSubmitSetup(adminPass, jefePass, trabPass, masterPin, question, answer)) {
                        is InitialSecuritySetupResult.Success -> {
                            setupError = null
                            onSetupCompleted(result.adminRecoveryCode, result.ownerRecoveryCode)
                        }
                        is InitialSecuritySetupResult.Error -> {
                            setupError = result.message
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("setup_submit_button")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Seguridad y Continuar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OfflineRecoveryDialog(
    recoveryQuestion: String,
    onDismiss: () -> Unit,
    onRecoverWithAnswer: (answer: String, newPassword: String) -> RecoveryResult,
    onRecoverWithCode: (role: UserRole, code: String, newPassword: String) -> RecoveryResult
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Pregunta Dueño, 1 = Código Offline
    var selectedRole by remember { mutableStateOf(UserRole.DUENO) }
    var answerOrCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var rotatedCode by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recuperación Segura Offline", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BrandNavy)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                if (rotatedCode != null) {
                    Text("¡Contraseña restablecida correctamente!", fontWeight = FontWeight.Bold, color = BrandEmeraldDark)
                    Text(
                        "Por seguridad se generó un nuevo código único de recuperación (el anterior quedó invalidado):",
                        fontSize = 12.sp,
                        color = TextMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceSubtle,
                        border = BorderStroke(1.dp, BrandAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = rotatedCode!!,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = BrandNavy,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Listo, Iniciar Sesión", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; errorMsg = null; answerOrCode = "" },
                            label = { Text("Pregunta Dueño", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; errorMsg = null; answerOrCode = "" },
                            label = { Text("Código Offline", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (selectedTab == 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceSubtle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Pregunta configurada: ${recoveryQuestion.ifBlank { "No configurada" }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandNavy,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        OutlinedTextField(
                            value = answerOrCode,
                            onValueChange = { answerOrCode = it; errorMsg = null },
                            label = { Text("Tu respuesta secreta") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(UserRole.DUENO to "Dueño / Jefe", UserRole.ADMINISTRADOR to "Administrador").forEach { (r, label) ->
                                FilterChip(
                                    selected = selectedRole == r,
                                    onClick = { selectedRole = r; errorMsg = null },
                                    label = { Text(label, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = answerOrCode,
                            onValueChange = { answerOrCode = it; errorMsg = null },
                            label = { Text("Código de recuperación (CM-...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMsg = null },
                        label = { Text("Nueva contraseña (mín. 6)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMsg = null },
                        label = { Text("Confirmar nueva contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMsg != null) {
                        Text(errorMsg!!, color = TileSalmon, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            if (newPassword != confirmPassword) {
                                errorMsg = "Las contraseñas no coinciden."
                                return@Button
                            }
                            val res = if (selectedTab == 0) {
                                onRecoverWithAnswer(answerOrCode, newPassword)
                            } else {
                                onRecoverWithCode(selectedRole, answerOrCode, newPassword)
                            }
                            when (res) {
                                is RecoveryResult.Success -> {
                                    if (res.newRecoveryCode != null) {
                                        rotatedCode = res.newRecoveryCode
                                    } else {
                                        onDismiss()
                                    }
                                }
                                is RecoveryResult.Error -> errorMsg = res.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Restablecer Contraseña", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleQuickButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else SurfaceSubtle,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) accentColor else SurfaceHover
        ),
        modifier = modifier
            .height(84.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) Color.White else TextDark,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accentColor else TextDark,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}
