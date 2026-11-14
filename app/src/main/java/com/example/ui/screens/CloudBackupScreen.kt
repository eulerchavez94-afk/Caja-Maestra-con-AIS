package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.backup.BackupCounts
import com.example.data.backup.BackupFormatType
import com.example.data.backup.BackupInspectionResult
import com.example.data.backup.BackupOperationResult
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.RoleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isWorking by viewModel.isCloudSyncing.collectAsState()
    val statusMessage by viewModel.cloudSyncMessage.collectAsState()
    val encryptedText by viewModel.encryptedBackupText.collectAsState()
    val selectedInspection by viewModel.selectedBackupInspection.collectAsState()
    val selectedBackupBytes by viewModel.selectedBackupBytes.collectAsState()
    val lastRestoreResult by viewModel.lastRestoreResult.collectAsState()
    val lastExportedPath by viewModel.lastExportedFilePath.collectAsState()

    var passphraseInput by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var showTextPasteSection by remember { mutableStateOf(false) }
    var restoreTextInput by remember { mutableStateOf("") }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    var pendingSafBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSafCounts by remember { mutableStateOf<BackupCounts?>(null) }

    val settings by viewModel.settings.collectAsState()
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val bytes = pendingSafBytes
        val counts = pendingSafCounts
        pendingSafBytes = null
        pendingSafCounts = null

        if (uri == null) {
            Toast.makeText(context, "Guardado cancelado por el usuario.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (bytes == null || counts == null) {
            Toast.makeText(context, "No hay datos de respaldo pendientes para escribir.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            val writeResult = withContext(Dispatchers.IO) {
                runCatching {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("No se pudo abrir la ubicación seleccionada.")
                    out.use { stream ->
                        stream.write(bytes)
                        stream.flush()
                    }
                    resolveDisplayName(context, uri)
                }
            }
            writeResult.onSuccess { savedName ->
                viewModel.recordExternalBackupExport(savedName, bytes.size, counts)
                Toast.makeText(context, "Respaldo guardado en: $savedName", Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                Toast.makeText(context, "Error al guardar archivo: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(context, "Selección de archivo cancelada.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val readRes = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = resolveDisplayName(context, uri)
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")
                    val bytes = input.use { it.readBytes() }
                    fileName to bytes
                }
            }
            readRes.onSuccess { (fileName, bytes) ->
                val inspection = viewModel.inspectSelectedBackupBytes(bytes, fileName)
                if (!inspection.isValidForRestore) {
                    Toast.makeText(context, inspection.statusMessage, Toast.LENGTH_LONG).show()
                }
            }.onFailure { err ->
                Toast.makeText(context, "Error al leer archivo: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            com.example.ui.components.CajaMaestraTopBar(
                title = "Respaldo Offline",
                subtitle = "Exportación y Restauración AES-256-GCM",
                currentRole = settings.currentRole,
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(CanvasLight),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Offline Status & Quick Local Archive Banner
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Respaldo Offline Protegido",
                                tint = GoldAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Respaldo Local Cifrado (100% Offline)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Sin servidores ni internet. Incluye todas las tablas Room, configuración y bóveda.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = statusMessage,
                            color = GoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (!lastExportedPath.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Última referencia: $lastExportedPath",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isWorking) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = GoldAccent
                            )
                        } else {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        when (val res = viewModel.saveLocalPrivateBackup(passphraseInput)) {
                                            is BackupOperationResult.ExportSuccess -> {
                                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                            }
                                            is BackupOperationResult.Error -> {
                                                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                            }
                                            is BackupOperationResult.RestoreSuccess -> Unit
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("save_local_backup_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Guardar Copia Rápida en App",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. AES-256 Encryption Passphrase Card (No default hardcoded secret)
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Contraseña de Cifrado del Archivo (PBKDF2 + AES-256)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NavyDark
                        )
                        Text(
                            "Define una frase secreta (mínimo 6 caracteres) para cifrar al exportar o escribe la contraseña original del archivo al restaurar. Nunca se almacena.",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                        OutlinedTextField(
                            value = passphraseInput,
                            onValueChange = { passphraseInput = it },
                            label = { Text("Contraseña o frase de respaldo") },
                            placeholder = { Text("Mínimo 6 caracteres") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = GoldDark) },
                            trailingIcon = {
                                IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                    Icon(
                                        imageVector = if (passphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Mostrar u ocultar contraseña de respaldo"
                                    )
                                }
                            },
                            visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_passphrase_input")
                        )
                    }
                }
            }

            // 3. Export & Share (.cmbak) Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Exportar o Compartir Respaldo Completo (.cmbak)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NavyDark
                        )
                        Text(
                            "Exporta las 10 tablas de base de datos, configuración de negocio/impresora y la bóveda reprotegida con tu contraseña.",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        Button(
                            enabled = !isWorking,
                            onClick = {
                                coroutineScope.launch {
                                    val result = viewModel.createFullBackupBytes(passphraseInput)
                                    result.onSuccess { (bytes, counts) ->
                                        pendingSafBytes = bytes
                                        pendingSafCounts = counts
                                        createDocumentLauncher.launch(viewModel.buildDefaultBackupFileName())
                                    }.onFailure { err ->
                                        Toast.makeText(
                                            context,
                                            err.message ?: "No se pudo generar el respaldo.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("export_saf_backup_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Elegir Carpeta y Guardar (.cmbak)", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            enabled = !isWorking,
                            onClick = {
                                coroutineScope.launch {
                                    val shareRes = viewModel.prepareShareableBackupFile(passphraseInput)
                                    shareRes.onSuccess { (file, _) ->
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Caja Maestra - Respaldo Cifrado (.cmbak)")
                                            clipData = ClipData.newRawUri("Respaldo Caja Maestra", uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(sendIntent, "Compartir Archivo de Respaldo")
                                        )
                                    }.onFailure { err ->
                                        Toast.makeText(
                                            context,
                                            err.message ?: "Error al preparar archivo para compartir.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("share_backup_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = NavyPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compartir Archivo (.cmbak)", color = NavyPrimary, fontWeight = FontWeight.SemiBold)
                        }

                        if (encryptedText.isNotBlank()) {
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Paquete cifrado generado (${encryptedText.length} caracteres):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = encryptedText.take(120) + "...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.DarkGray
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Caja Maestra Backup", encryptedText)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Respaldo copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copiar Código", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Restore Section (Only Jefe & Admin)
            if (permissions.canRestoreBackup) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Restaurar Respaldo (.cmbak o v1)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = NavyDark
                            )
                            Text(
                                "Selecciona un archivo desde el almacenamiento del teléfono. Se inspeccionará la firma y versión antes de aplicar cambios.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )

                            OutlinedButton(
                                enabled = !isWorking,
                                onClick = {
                                    openDocumentLauncher.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("select_restore_file_button")
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = NavyPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Seleccionar Archivo de Respaldo", color = NavyPrimary, fontWeight = FontWeight.SemiBold)
                            }

                            if (selectedInspection != null) {
                                BackupInspectionCard(
                                    inspection = selectedInspection!!,
                                    onClear = { viewModel.clearSelectedBackup() }
                                )
                            }

                            TextButton(
                                onClick = { showTextPasteSection = !showTextPasteSection },
                                modifier = Modifier.align(Alignment.Start)
                            ) {
                                Icon(
                                    imageVector = if (showTextPasteSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showTextPasteSection) {
                                        "Ocultar entrada por texto / código v1"
                                    } else {
                                        "Pegar código de respaldo (modo texto / v1)"
                                    },
                                    fontSize = 12.sp
                                )
                            }

                            if (showTextPasteSection) {
                                OutlinedTextField(
                                    value = restoreTextInput,
                                    onValueChange = { restoreTextInput = it },
                                    placeholder = { Text("Pegue aquí el paquete JSON v2 o código Base64 v1...", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .testTag("restore_paste_input")
                                )
                                OutlinedButton(
                                    enabled = restoreTextInput.isNotBlank() && !isWorking,
                                    onClick = {
                                        val bytes = restoreTextInput.trim().toByteArray(Charsets.UTF_8)
                                        val insp = viewModel.inspectSelectedBackupBytes(bytes, "Código pegado")
                                        Toast.makeText(context, insp.statusMessage, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FactCheck, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Inspeccionar Texto Pegado")
                                }
                            }

                            val hasRestorableBytes = selectedBackupBytes != null || restoreTextInput.isNotBlank()
                            Button(
                                enabled = !isWorking && hasRestorableBytes,
                                onClick = {
                                    if (selectedBackupBytes == null && restoreTextInput.isNotBlank()) {
                                        viewModel.inspectSelectedBackupBytes(
                                            restoreTextInput.trim().toByteArray(Charsets.UTF_8),
                                            "Código pegado"
                                        )
                                    }
                                    val readyBytes = viewModel.selectedBackupBytes.value
                                    val inspection = viewModel.selectedBackupInspection.value
                                    when {
                                        passphraseInput.trim().isEmpty() -> {
                                            Toast.makeText(
                                                context,
                                                "Ingresa arriba la contraseña con la que se cifró este respaldo.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        readyBytes == null -> {
                                            Toast.makeText(
                                                context,
                                                inspection?.statusMessage ?: "Selecciona un archivo o código de respaldo válido.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        else -> {
                                            showRestoreConfirmDialog = true
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TileGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("execute_restore_button")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descifrar y Restaurar Base de Datos", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = BorderStroke(1.dp, SurfaceHover),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "La restauración de copias de seguridad está reservada exclusivamente para el Dueño (Jefe) y Administrador.",
                                fontSize = 12.sp,
                                color = TextMedium
                            )
                        }
                    }
                }
            }

            // 5. Last Restore Summary Card
            if (lastRestoreResult != null) {
                item {
                    RestoreOutcomeCard(result = lastRestoreResult!!)
                }
            }
        }
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = BrandAmber) },
            title = { Text("Confirmar Restauración Segura", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se reemplazarán los datos actuales del negocio con el contenido verificado del respaldo (${selectedInspection?.fileName ?: "archivo"}).\n\nAntes de escribir cambios, Caja Maestra creará un respaldo preventivo automático con reversión segura en caso de fallo.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        val bytesToRestore = selectedBackupBytes
                            ?: restoreTextInput.trim().toByteArray(Charsets.UTF_8)
                        coroutineScope.launch {
                            val res = viewModel.executeFullRestore(bytesToRestore, passphraseInput)
                            when (res) {
                                is BackupOperationResult.RestoreSuccess -> {
                                    restoreTextInput = ""
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                                is BackupOperationResult.Error -> {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                                is BackupOperationResult.ExportSuccess -> Unit
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TileGreen)
                ) {
                    Text("Sí, Restaurar Ahora", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun BackupInspectionCard(
    inspection: BackupInspectionResult,
    onClear: () -> Unit
) {
    val borderColor = if (inspection.isValidForRestore) TileGreen else TileSalmon
    val bgColor = if (inspection.isValidForRestore) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
    val dateLabel = remember(inspection.createdAt) {
        if (inspection.createdAt > 0L) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(inspection.createdAt))
        } else {
            "Formato heredado sin sello temporal"
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (inspection.isValidForRestore) Icons.Default.Verified else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = inspection.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NavyDark,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Limpiar selección", modifier = Modifier.size(16.dp))
                }
            }

            val formatDesc = when (inspection.formatType) {
                BackupFormatType.V2_ENVELOPE -> "V2 Completo (Room v${inspection.roomSchemaVersion})"
                BackupFormatType.LEGACY_V1 -> "Heredado V1 (Inventario y Ventas)"
                BackupFormatType.UNSUPPORTED_VERSION -> "Versión incompatible (v${inspection.formatVersion})"
                BackupFormatType.EMPTY -> "Vacío"
                BackupFormatType.INVALID_FORMAT -> "Formato inválido"
            }

            Text("Formato: $formatDesc • Tamaño: ${inspection.sizeBytes} bytes", fontSize = 11.sp, color = Color.DarkGray)
            Text("Fecha: $dateLabel", fontSize = 11.sp, color = Color.DarkGray)
            if (inspection.includesSecurityVault) {
                Text("Incluye bóveda de contraseñas reprotegida.", fontSize = 11.sp, color = NavyPrimary, fontWeight = FontWeight.Medium)
            }
            Text(
                text = inspection.statusMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = borderColor
            )
        }
    }
}

@Composable
private fun RestoreOutcomeCard(result: BackupOperationResult) {
    when (result) {
        is BackupOperationResult.RestoreSuccess -> {
            val c = result.counts
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.dp, TileGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TileGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resumen de Restauración Verificada", fontWeight = FontWeight.Bold, color = NavyDark, fontSize = 14.sp)
                    }
                    Text(result.message, fontSize = 12.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Inventario: ${c.inventory} | Ventas: ${c.sales} | Compras: ${c.purchases}\n" +
                            "• Mov. Extra: ${c.extraMovements} | Rentas/Prést.: ${c.rentalsLoans}\n" +
                            "• Clientes: ${c.clients} | Proveedores: ${c.suppliers} | Empleados: ${c.employees}\n" +
                            "• Turnos Caja: ${c.cashShifts} | Mov. Turno: ${c.cashShiftMovements}\n" +
                            "• Configuración: ${if (c.hasBusinessSettings) "Restaurada" else "N/D"} | Bóveda: ${if (c.hasSecurityVault) "Reprotegida en KeyStore" else "Conservada"}",
                        fontSize = 11.sp,
                        color = NavyDark
                    )
                }
            }
        }
        is BackupOperationResult.Error -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, TileSalmon),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = TileSalmon)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restauración No Aplicada", fontWeight = FontWeight.Bold, color = TileSalmon, fontSize = 14.sp)
                    }
                    Text(result.message, fontSize = 12.sp, color = Color.DarkGray)
                }
            }
        }
        is BackupOperationResult.ExportSuccess -> Unit
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "respaldo.cmbak"
}
