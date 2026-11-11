package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

/**
 * Pantalla de Bloqueo Inicial de Caja Maestra con Acceso por Roles:
 * 1- Admin: Admin (Control total, Modo de Prueba 7+ días, Desbloqueo, Quitar/Permitir módulos)
 * 2- Jefe: Jefe (Control de la tienda/negocio, limita a roles inferiores y usa módulos de Admin)
 * 3- Trabajador: Trabajador (Rol inferior para ventas, inventario y cobro rápido)
 */
@Composable
fun InitialLockScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val settings by viewModel.settings.collectAsState()
    val loginError by viewModel.lastLoginError.collectAsState()

    var username by remember { mutableStateOf("Admin") }
    var password by remember { mutableStateOf("Admin") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedRolePreset by remember { mutableStateOf("Admin") }

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
            // App Emblem & Brand Header
            Surface(
                shape = CircleShape,
                color = BrandAmber.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(2.dp, BrandAmber),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bloqueo de Seguridad",
                        tint = BrandAmber,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CAJA MAESTRA POS",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Text(
                text = "Control de Acceso por Roles & Licencia",
                fontSize = 13.sp,
                color = TextLightSecondary,
                fontWeight = FontWeight.Medium
            )

            // License / Trial Status Pill
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isAppLocked) TileSalmon.copy(alpha = 0.2f) else if (settings.trialModeEnabled) BrandAmber.copy(alpha = 0.2f) else BrandEmerald.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
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
                            "SISTEMA ACTIVO / DESBLOQUEADO"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAppLocked) TileSalmon else if (settings.trialModeEnabled) BrandAmber else BrandEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Login Card
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
                        text = "Seleccione su Perfil o Ingrese Credenciales",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Role Selectors matching the 3 user requirements
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
                                password = "Admin"
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
                                password = "Jefe"
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
                                password = "Trabajador"
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Username Input
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            selectedRolePreset = ""
                        },
                        label = { Text("Usuario / Rol") },
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

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, TileSalmon.copy(alpha = 0.4f)),
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

                    // Submit Access Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val success = viewModel.authenticateUser(username, password)
                            if (success) {
                                Toast.makeText(context, "Acceso concedido como $username", Toast.LENGTH_SHORT).show()
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Helper Legend Card showing exact credentials
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceSubtle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Credenciales oficiales preconfiguradas:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Admin: Admin (Control total, prueba de 7 días, módulos)", fontSize = 11.sp, color = TextMedium)
                            Text("• Jefe: Jefe (Dueño de tienda, limita a trabajador)", fontSize = 11.sp, color = TextMedium)
                            Text("• Trabajador: Trabajador (Punto de venta y tareas asignadas)", fontSize = 11.sp, color = TextMedium)
                        }
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
        border = androidx.compose.foundation.BorderStroke(
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
