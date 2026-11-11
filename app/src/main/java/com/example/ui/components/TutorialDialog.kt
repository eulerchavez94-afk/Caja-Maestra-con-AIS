package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyDark

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tutorialTopics = listOf(
        "Inventario Productos" to "Aprenda a dar de alta artículos, códigos de barra, precios de compra y venta, y alertas de existencias mínimas.",
        "Inventario Servicios" to "Gestione servicios sin control de stock como instalaciones, mano de obra o asesorías técnicas.",
        "Inventario Manufactura" to "Defina combos, paquetes armados y productos elaborados a partir de materia prima.",
        "Compras" to "Registre entradas de mercancía de sus proveedores y actualice automáticamente el costo promedio y stock.",
        "Ventas" to "Realice ventas rápidas con Interfaz I (tarjetas) o Interfaz II (tabla), escanee códigos y emita tickets de 58 mm.",
        "Rentas" to "Lleve el control de alquiler de equipos, depósitos en garantía y fechas límite de devolución.",
        "Administrador - Vendedor" to "Configure permisos, oculte reportes financieros a trabajadores y restrinja con PIN.",
        "Ver Vídeo: Administrador - Vendedor" to "Guía paso a paso sobre el control de accesos y arqueo de caja."
    )

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
                .testTag("tutorial_dialog")
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
                    Text(
                        text = "Tutoriales y Ayuda",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tutorialTopics) { (title, desc) ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(context, "$title: $desc", Toast.LENGTH_LONG).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (title.startsWith("Ver")) Icons.Default.PlayCircle else Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Entendido", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
