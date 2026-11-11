package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary

@Composable
fun BarcodeScannerDialog(
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    var manualBarcodeInput by remember { mutableStateOf("") }

    // Laser scanning animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding()
                .testTag("barcode_scanner_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Escaner",
                            tint = GoldAccent,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Escáner de Código",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Simulated Viewfinder Frame
                Box(
                    modifier = Modifier
                        .size(240.dp, 200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(2.dp, GoldAccent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Subtle grid lines & laser
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("CAM: ACTIVA", color = Color.Green, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("58MM READY", color = GoldAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        // Barcode placeholder graphic
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewWeek,
                                contentDescription = null,
                                tint = Color.LightGray.copy(alpha = 0.5f),
                                modifier = Modifier.size(72.dp)
                            )
                            Text(
                                "Apunte la cámara al código de barras",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Red Animated Laser Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .offset(y = (laserOffsetY - 90).dp)
                            .background(Color.Red)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Manual or External USB/Bluetooth Barcode Scanner Input
                OutlinedTextField(
                    value = manualBarcodeInput,
                    onValueChange = { manualBarcodeInput = it },
                    label = { Text("Código de barras / Escáner externo", color = Color.LightGray) },
                    placeholder = { Text("Ej. 7501001", color = Color.DarkGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = Color.Gray
                    ),
                    trailingIcon = {
                        if (manualBarcodeInput.isNotBlank()) {
                            IconButton(onClick = {
                                onBarcodeScanned(manualBarcodeInput.trim())
                                onDismiss()
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Aceptar", tint = GoldAccent)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("barcode_input_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Barcode Test Chips (great for emulator testing)
                Text(
                    text = "Códigos rápidos de prueba:",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sampleCodes = listOf("7501001", "7501002", "7501003", "MAN-001")
                    sampleCodes.forEach { code ->
                        SuggestionChip(
                            onClick = {
                                onBarcodeScanned(code)
                                onDismiss()
                            },
                            label = { Text(code, fontSize = 11.sp, color = Color.White) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = NavyPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (manualBarcodeInput.isNotBlank()) {
                            onBarcodeScanned(manualBarcodeInput.trim())
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_scan_button")
                ) {
                    Text("Confirmar Lectura", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
