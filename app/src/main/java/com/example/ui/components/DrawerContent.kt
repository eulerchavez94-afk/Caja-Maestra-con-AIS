package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessSettings
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.util.RoleManager

@Composable
fun DrawerContent(
    settings: BusinessSettings,
    currentScreen: AppScreen,
    isModulePermitted: (AppScreen) -> Boolean = { RoleManager.canAccessScreen(it, settings) },
    onNavigate: (AppScreen) -> Unit,
    onShowTutorial: () -> Unit,
    onLogout: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val permissions = remember(settings) { RoleManager.getPermissions(settings) }

    ModalDrawerSheet(
        drawerContainerColor = CanvasLight,
        modifier = Modifier.width(320.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Executive Fresh Header Card with Gradient & Role Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HudGradient)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Role Badge & Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when (settings.currentRole) {
                                UserRole.DUENO -> BrandAmber.copy(alpha = 0.2f)
                                UserRole.ADMINISTRADOR -> BrandBlue.copy(alpha = 0.2f)
                                UserRole.TRABAJADOR -> BrandEmerald.copy(alpha = 0.2f)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (settings.currentRole) {
                                    UserRole.DUENO -> BrandAmber
                                    UserRole.ADMINISTRADOR -> BrandBlue
                                    UserRole.TRABAJADOR -> BrandEmerald
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (settings.currentRole) {
                                                UserRole.DUENO -> BrandAmber
                                                UserRole.ADMINISTRADOR -> BrandBlue
                                                UserRole.TRABAJADOR -> BrandEmerald
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (settings.currentRole) {
                                        UserRole.DUENO -> "DUEÑO"
                                        UserRole.ADMINISTRADOR -> "ADMINISTRADOR"
                                        UserRole.TRABAJADOR -> "TRABAJADOR"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // App Version / Mode Tag
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "v2.5 PRO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Store / Business Selector Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isModulePermitted(AppScreen.CONFIGURACION)) {
                                onNavigate(AppScreen.CONFIGURACION)
                                onCloseDrawer()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BrandAmber),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = BrandNavy,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = settings.businessName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Sucursal Principal",
                                        fontSize = 12.sp,
                                        color = TextLightSecondary
                                    )
                                }
                            }
                            if (isModulePermitted(AppScreen.CONFIGURACION)) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Configurar",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Three Fresh Shortcut Tiles: Catálogo, Backup, Agenda (Filtered by role permissions)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isModulePermitted(AppScreen.INVENTARIO)) {
                            HeaderActionTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.PhotoLibrary,
                                label = "Catálogo",
                                accentColor = BrandBlue,
                                onClick = { onNavigate(AppScreen.INVENTARIO); onCloseDrawer() }
                            )
                        }
                        if (isModulePermitted(AppScreen.VINCULAR_WEB)) {
                            HeaderActionTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.CloudSync,
                                label = "Backup",
                                accentColor = BrandEmerald,
                                onClick = { onNavigate(AppScreen.VINCULAR_WEB); onCloseDrawer() }
                            )
                        }
                        if (isModulePermitted(AppScreen.RENTAS)) {
                            HeaderActionTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.CalendarMonth,
                                label = "Agenda",
                                accentColor = BrandAmber,
                                onClick = { onNavigate(AppScreen.RENTAS); onCloseDrawer() }
                            )
                        }
                    }
                }
            }

            // Menu Items List (Scrollable, Categorized & Clean)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Section: OPERACIONES
                item {
                    SectionHeader("OPERACIONES DEL NEGOCIO")
                }
                if (isModulePermitted(AppScreen.INVENTARIO)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.Inventory2,
                            label = "Inventario",
                            subtitle = "Productos, materias y combos",
                            iconColor = BrandBlue,
                            isSelected = currentScreen == AppScreen.INVENTARIO,
                            onClick = { onNavigate(AppScreen.INVENTARIO); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.VENTAS)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.PointOfSale,
                            label = "Ventas (TPV)",
                            subtitle = "Cobro rápido y cotizaciones",
                            iconColor = BrandEmerald,
                            isSelected = currentScreen == AppScreen.VENTAS,
                            onClick = { onNavigate(AppScreen.VENTAS); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.COMPRAS)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.ShoppingBag,
                            label = "Compras",
                            subtitle = "Entradas de stock e inversión",
                            iconColor = BrandAmber,
                            isSelected = currentScreen == AppScreen.COMPRAS,
                            onClick = { onNavigate(AppScreen.COMPRAS); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.INGRESOS_GASTOS)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.SwapVert,
                            label = "Ingresos y Gastos Extra",
                            subtitle = "Flujo de caja y egresos varios",
                            iconColor = TilePurple,
                            isSelected = currentScreen == AppScreen.INGRESOS_GASTOS,
                            onClick = { onNavigate(AppScreen.INGRESOS_GASTOS); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.RENTAS)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.ShoppingCartCheckout,
                            label = "Rentas y Equipos",
                            subtitle = "Alquiler temporal y depósitos",
                            iconColor = TileTeal,
                            isSelected = currentScreen == AppScreen.RENTAS,
                            onClick = { onNavigate(AppScreen.RENTAS); onCloseDrawer() }
                        )
                    }
                }

                // Section: GESTIÓN & REPORTES
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionHeader("GESTIÓN & ANÁLISIS")
                }
                if (isModulePermitted(AppScreen.REPORTES)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.BarChart,
                            label = "Reportes Financieros",
                            subtitle = "25 vistas y exportación PDF/Excel",
                            iconColor = BrandBlue,
                            isSelected = currentScreen == AppScreen.REPORTES,
                            onClick = { onNavigate(AppScreen.REPORTES); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.EMPLEADOS)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.Badge,
                            label = "Empleados y Roles",
                            subtitle = "Trabajador, Administrador, Dueño",
                            iconColor = TileLilac,
                            isSelected = currentScreen == AppScreen.EMPLEADOS,
                            onClick = { onNavigate(AppScreen.EMPLEADOS); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.CLIENTES)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.Group,
                            label = "Clientes",
                            subtitle = "Saldos a favor y cuentas por cobrar",
                            iconColor = BrandEmerald,
                            isSelected = currentScreen == AppScreen.CLIENTES,
                            onClick = { onNavigate(AppScreen.CLIENTES); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.PROVEEDORES)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.Domain,
                            label = "Proveedores",
                            subtitle = "Contactos y pedidos abastecidos",
                            iconColor = BrandAmber,
                            isSelected = currentScreen == AppScreen.PROVEEDORES,
                            onClick = { onNavigate(AppScreen.PROVEEDORES); onCloseDrawer() }
                        )
                    }
                }

                // Section: SISTEMA
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionHeader("SISTEMA & DISPOSITIVOS")
                }
                if (isModulePermitted(AppScreen.RECIBO_TICKET)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.ReceiptLong,
                            label = "Ticket e Impresora 58mm",
                            subtitle = "Configuración térmica y logo",
                            iconColor = TileOrange,
                            isSelected = currentScreen == AppScreen.RECIBO_TICKET,
                            onClick = { onNavigate(AppScreen.RECIBO_TICKET); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.VINCULAR_WEB)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.CloudSync,
                            label = "Salva en Nube (AES-256)",
                            subtitle = "Copia segura offline/online",
                            iconColor = BrandEmerald,
                            isSelected = currentScreen == AppScreen.VINCULAR_WEB,
                            onClick = { onNavigate(AppScreen.VINCULAR_WEB); onCloseDrawer() }
                        )
                    }
                }
                if (isModulePermitted(AppScreen.CONFIGURACION)) {
                    item {
                        DrawerMenuItem(
                            icon = Icons.Default.Settings,
                            label = "Configuración General",
                            subtitle = "Control de roles, módulos y licencias",
                            iconColor = TextMedium,
                            isSelected = currentScreen == AppScreen.CONFIGURACION,
                            onClick = { onNavigate(AppScreen.CONFIGURACION); onCloseDrawer() }
                        )
                    }
                }

                // Help and Tutorials
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = SurfaceHover, modifier = Modifier.padding(vertical = 4.dp))
                }
                item {
                    DrawerMenuItem(
                        icon = Icons.Default.HelpOutline,
                        label = "Tutorial y Guía",
                        subtitle = "Aprende a usar Caja Maestra",
                        iconColor = BrandBlue,
                        isSelected = false,
                        onClick = { onShowTutorial(); onCloseDrawer() }
                    )
                }

                // Cerrar Sesión / Bloquear
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    DrawerMenuItem(
                        icon = Icons.Default.Logout,
                        label = "Cerrar Sesión",
                        subtitle = "Volver a pantalla de bloqueo",
                        iconColor = TileSalmon,
                        isSelected = false,
                        onClick = {
                            onLogout()
                            onCloseDrawer()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun HeaderActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
            .height(68.dp)
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
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    subtitle: String,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) SurfaceWhite else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover) else null,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
            .testTag("drawer_item_${label.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) iconColor.copy(alpha = 0.15f) else SurfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) iconColor else TextMedium,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) BrandNavy else TextDark
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isSelected) TextMedium else TextMuted,
                    maxLines = 1
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BrandEmerald)
                )
            }
        }
    }
}
