package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.util.RoleManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val isSyncing by viewModel.isCloudSyncing.collectAsState()
    val syncMessage by viewModel.cloudSyncMessage.collectAsState()
    val encryptedText by viewModel.encryptedBackupText.collectAsState()

    var restoreInput by remember { mutableStateOf("") }
    var passphraseInput by remember { mutableStateOf("CajaMaestraSecret2026") }

    val settings by viewModel.settings.collectAsState()
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    Scaffold(
        topBar = {
            com.example.ui.components.CajaMaestraTopBar(
                title = "Salva en Nube & Backup",
                subtitle = "Cifrado Militar AES-256-GCM",
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
            // Offline-first banner
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Modo Offline con Salva Manual", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Los datos se guardan localmente y se respaldan en la nube de forma manual.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = syncMessage,
                            color = GoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isSyncing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = GoldAccent
                            )
                        } else {
                            Button(
                                onClick = { viewModel.performManualCloudSync() },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_cloud_sync_button")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ejecutar Sincronización Manual Ahora", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // AES-256 Encryption Passphrase
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Clave de Cifrado AES-256", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyDark)
                        Text(
                            "Esta contraseña deriva una llave criptográfica de 256 bits para blindar sus inventarios, clientes y finanzas.",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                        OutlinedTextField(
                            value = passphraseInput,
                            onValueChange = { passphraseInput = it },
                            label = { Text("Contraseña Maestra de Cifrado") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = GoldDark) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Encrypted Snapshot Export
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Exportar Paquete Cifrado de Salva", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyDark)
                        Text(
                            "Genere un archivo o texto cifrado con el estado completo del negocio para migrar de dispositivo o archivar.",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        Button(
                            onClick = {
                                val encrypted = viewModel.generateEncryptedBackup(passphraseInput)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, encrypted)
                                    putExtra(Intent.EXTRA_SUBJECT, "Caja Maestra - Respaldo Cifrado AES-256")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir Respaldo Cifrado"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generar y Exportar Respaldo Cifrado", color = Color.White)
                        }

                        if (encryptedText.isNotBlank()) {
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Código de Respaldo Cifrado (${encryptedText.length} caracteres):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = encryptedText.take(120) + "...",
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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

            // Restore from Encrypted Backup (Only Jefe and Admin)
            if (permissions.canRestoreBackup) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Restaurar Salva Cifrada", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyDark)
                            Text(
                                "Pegue el código de respaldo cifrado y la contraseña maestra para recuperar sus datos.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )

                            OutlinedTextField(
                                value = restoreInput,
                                onValueChange = { restoreInput = it },
                                placeholder = { Text("Pegue aquí el texto cifrado Base64...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                            )

                            Button(
                                onClick = {
                                    if (restoreInput.isNotBlank()) {
                                        val success = viewModel.restoreEncryptedBackup(restoreInput.trim(), passphraseInput)
                                        Toast.makeText(
                                            context,
                                            if (success) "¡Respaldo restaurado exitosamente!" else "Error: Clave incorrecta o formato inválido",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TileGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descifrar y Restaurar Base de Datos", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
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
        }
    }
}
