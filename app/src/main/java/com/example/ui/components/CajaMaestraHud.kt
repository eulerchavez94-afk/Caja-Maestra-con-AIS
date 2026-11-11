package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserRole
import com.example.ui.theme.*

/**
 * Premium, Fresh HUD Top Bar for Caja Maestra
 * Provides crystal-clear typography, consistent styling, and quick role/status indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CajaMaestraTopBar(
    title: String,
    subtitle: String? = null,
    currentRole: UserRole? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    searchQuery: String = "",
    isSearchVisible: Boolean = false,
    searchPlaceholder: String = "Buscar...",
    onSearchQueryChange: ((String) -> Unit)? = null,
    onToggleSearch: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudGradient)
    ) {
        TopAppBar(
            title = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (currentRole != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when (currentRole) {
                                    UserRole.DUENO -> BrandAmber.copy(alpha = 0.25f)
                                    UserRole.ADMINISTRADOR -> BrandBlue.copy(alpha = 0.25f)
                                    UserRole.TRABAJADOR -> BrandEmerald.copy(alpha = 0.25f)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (currentRole) {
                                        UserRole.DUENO -> BrandAmber
                                        UserRole.ADMINISTRADOR -> BrandBlue
                                        UserRole.TRABAJADOR -> BrandEmerald
                                    }
                                )
                            ) {
                                Text(
                                    text = when (currentRole) {
                                        UserRole.DUENO -> "Dueño"
                                        UserRole.ADMINISTRADOR -> "Admin"
                                        UserRole.TRABAJADOR -> "Cajero"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontFamily = BodyFontFamily,
                            fontSize = 12.sp,
                            color = TextLightSecondary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Color.White
                        )
                    }
                } else if (onOpenDrawer != null) {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menú",
                            tint = Color.White
                        )
                    }
                }
            },
            actions = {
                if (onToggleSearch != null) {
                    IconButton(onClick = onToggleSearch, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = if (isSearchVisible) BrandAmber else Color.White
                        )
                    }
                }
                actions()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )

        // Expandable Smooth Search Input Pill
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { onSearchQueryChange?.invoke(it) },
                    placeholder = {
                        Text(
                            text = searchPlaceholder,
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontFamily = BodyFontFamily
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = BrandBlue)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange?.invoke("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextMedium)
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Beautiful, high-quality empty state component
 */
@Composable
fun CajaMaestraEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    tutorialButtonText: String = "Ver Tutorial Rápido",
    onTutorialClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(BrandBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = title,
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    fontFamily = BodyFontFamily,
                    fontSize = 14.sp,
                    color = TextMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (actionButtonText != null && onActionClick != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionButtonText,
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }

                if (onTutorialClick != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onTutorialClick,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceHover),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tutorialButtonText,
                            fontFamily = BodyFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = BrandNavy
                        )
                    }
                }
            }
        }
    }
}
