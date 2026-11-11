package com.example.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.example.data.model.BluetoothPrinterDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Robust Bluetooth Thermal Printer Manager for 58mm ESC/POS hardware.
 * Handles device discovery, RFCOMM SPP socket connections with multiple fallback strategies,
 * permission checks, and ESC/POS raw command streams.
 */
object BluetoothPrinterManager {

    // Universal Bluetooth Serial Port Profile (SPP) UUID
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    fun isBluetoothSupported(context: Context): Boolean {
        return getBluetoothAdapter(context) != null
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return getBluetoothAdapter(context)?.isEnabled == true
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Retrieves all Bluetooth devices paired/bonded with the Android device.
     * Prioritizes likely receipt printer names (POS, 58, MPT, Print, etc.).
     */
    fun getPairedPrinters(context: Context): List<BluetoothPrinterDevice> {
        val adapter = getBluetoothAdapter(context) ?: return emptyList()

        if (!hasBluetoothPermission(context)) {
            return emptyList()
        }

        return try {
            val bonded = adapter.bondedDevices ?: emptySet()
            val list = bonded.map { device ->
                val name = device.name ?: "Dispositivo Bluetooth"
                val address = device.address
                BluetoothPrinterDevice(
                    name = name,
                    address = address,
                    isBonded = true
                )
            }

            // Sort printers and POS devices to the top of the list
            list.sortedByDescending { dev ->
                val lower = dev.name.lowercase(Locale.ROOT)
                when {
                    lower.contains("print") || lower.contains("pos") || lower.contains("58") ||
                    lower.contains("mpt") || lower.contains("rpp") || lower.contains("thermal") ||
                    lower.contains("ticket") || lower.contains("recibo") -> 3
                    lower.contains("bt") || lower.contains("inner") -> 2
                    else -> 1
                }
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * Sends ESC/POS raw byte stream directly to the thermal printer over Bluetooth SPP.
     * Uses 3 progressive fallback connection strategies to ensure compatibility with all
     * standard and low-cost 58mm printer microcontrollers.
     */
    suspend fun printBytesDirect(
        context: Context,
        macAddress: String,
        bytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        if (macAddress.isBlank()) {
            return@withContext Result.failure(
                Exception("No has seleccionado ninguna impresora. Toca 'Nombre impresora vinculada' para elegir tu impresora de la lista.")
            )
        }

        val adapter = getBluetoothAdapter(context)
            ?: return@withContext Result.failure(Exception("Este dispositivo no cuenta con adaptador Bluetooth."))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(
                Exception("El Bluetooth está apagado en tu teléfono. Por favor enciéndelo para imprimir.")
            )
        }

        if (!hasBluetoothPermission(context)) {
            return@withContext Result.failure(
                Exception("La aplicación necesita permiso de 'Dispositivos cercanos / Bluetooth' para comunicarse con la impresora.")
            )
        }

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            return@withContext Result.failure(
                Exception("Dirección de impresora no válida ($macAddress): ${e.message}")
            )
        }

        var socket: BluetoothSocket? = null
        var lastException: Exception? = null

        // Strategy 1: Insecure RFCOMM socket (fastest, prevents encryption negotiation freezes)
        try {
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
        } catch (e1: Exception) {
            lastException = e1
            try { socket?.close() } catch (_: Exception) {}
            socket = null

            // Strategy 2: Standard Secure RFCOMM socket
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            } catch (e2: Exception) {
                lastException = e2
                try { socket?.close() } catch (_: Exception) {}
                socket = null

                // Strategy 3: Reflection on channel 1 (universal workaround for POS-58 and MPT-II printers)
                try {
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    socket = method.invoke(device, 1) as? BluetoothSocket
                    socket?.connect()
                } catch (e3: Exception) {
                    lastException = e3
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }
            }
        }

        val activeSocket = socket
        if (activeSocket == null || !activeSocket.isConnected) {
            val deviceName = try { device.name ?: macAddress } catch (_: SecurityException) { macAddress }
            val errorMsg = lastException?.localizedMessage ?: "Conexión rechazada o tiempo de espera agotado"
            return@withContext Result.failure(
                Exception("No se pudo conectar con '$deviceName'.\n\n1. Comprueba que la impresora esté encendida y con batería.\n2. Asegúrate de haberla vinculado previamente en los Ajustes de Bluetooth del teléfono.\n3. Error: $errorMsg")
            )
        }

        try {
            val out = activeSocket.outputStream
            out.write(bytes)
            out.flush()

            // Essential pause so the hardware print head buffer completely finishes printing before closing the socket
            Thread.sleep(400)

            val deviceName = try { device.name ?: macAddress } catch (_: SecurityException) { macAddress }
            Result.success("¡Ticket impreso con éxito en '$deviceName'!")
        } catch (e: Exception) {
            Result.failure(Exception("Error al enviar los datos de impresión: ${e.message}"))
        } finally {
            try {
                activeSocket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Generates and transmits a comprehensive 58mm test receipt.
     */
    suspend fun printTestReceipt(
        context: Context,
        macAddress: String,
        scale: Float = 1.0f,
        intensity: Int = 165
    ): Result<String> {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dateStr = sdf.format(Date())

        val out = ByteArrayOutputStream()

        // 1. Initialize printer (ESC @)
        out.write(byteArrayOf(0x1B, 0x40))

        // 2. Set density/intensity if requested (GS ~)
        val densityByte = ((intensity.coerceIn(100, 255) - 100) / 10).coerceIn(0, 15).toByte()
        out.write(byteArrayOf(0x1D, 0x7E, densityByte))

        // 3. Center Header (ESC a 1)
        out.write(byteArrayOf(0x1B, 0x61, 0x01))

        // Double height + double width for main title (GS ! 0x11)
        out.write(byteArrayOf(0x1D, 0x21, 0x11))
        out.write("CAJA MAESTRA\n".toByteArray(Charsets.ISO_8859_1))

        // Normal text size (GS ! 0x00)
        out.write(byteArrayOf(0x1D, 0x21, 0x00))
        out.write("SISTEMA DE GESTION POS\n".toByteArray(Charsets.ISO_8859_1))
        out.write("TEST DE IMPRESION 58MM\n".toByteArray(Charsets.ISO_8859_1))
        out.write("================================\n".toByteArray(Charsets.ISO_8859_1))

        // 4. Left Align details (ESC a 0)
        out.write(byteArrayOf(0x1B, 0x61, 0x00))
        out.write("Fecha: $dateStr\n".toByteArray(Charsets.ISO_8859_1))
        out.write("Ancho de papel: 58 mm (32 col)\n".toByteArray(Charsets.ISO_8859_1))
        out.write("Conexion Bluetooth: CORRECTA\n".toByteArray(Charsets.ISO_8859_1))
        out.write("Protocolo: ESC/POS Directo\n".toByteArray(Charsets.ISO_8859_1))
        out.write("--------------------------------\n".toByteArray(Charsets.ISO_8859_1))

        // Item test table
        out.write("CANT  DESCRIPCION          TOTAL\n".toByteArray(Charsets.ISO_8859_1))
        out.write("--------------------------------\n".toByteArray(Charsets.ISO_8859_1))
        out.write(" 1.0  Producto Prueba A   $15.00\n".toByteArray(Charsets.ISO_8859_1))
        out.write(" 2.0  Producto Prueba B   $30.00\n".toByteArray(Charsets.ISO_8859_1))
        out.write("--------------------------------\n".toByteArray(Charsets.ISO_8859_1))
        out.write("TOTAL:                    $45.00\n".toByteArray(Charsets.ISO_8859_1))
        out.write("================================\n".toByteArray(Charsets.ISO_8859_1))

        // Center Footer (ESC a 1)
        out.write(byteArrayOf(0x1B, 0x61, 0x01))
        out.write("*** IMPRESORA 58MM OK ***\n".toByteArray(Charsets.ISO_8859_1))
        out.write("Tu negocio esta listo para operar\n".toByteArray(Charsets.ISO_8859_1))

        // Feed 4 lines (ESC d 4)
        out.write(byteArrayOf(0x1B, 0x64, 0x04))

        // Paper Cut command (GS V 66 0)
        out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))

        return printBytesDirect(context, macAddress, out.toByteArray())
    }
}
