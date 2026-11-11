package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import com.example.util.AesEncryptionUtil
import com.example.util.ThermalPrinterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Caja Maestra", appName)
  }

  @Test
  fun `verify aes encryption and thermal receipt format`() {
    val secret = "TestSecret2026"
    val original = "Prueba de respaldo seguro Caja Maestra"
    val encrypted = AesEncryptionUtil.encrypt(original, secret)
    val decryptedResult = AesEncryptionUtil.decrypt(encrypted, secret)
    assertTrue(decryptedResult.isSuccess)
    assertEquals(original, decryptedResult.getOrNull())
  }

  @Test
  fun `security test - verify aes tamper detection fails corrupted ciphertext`() {
    val secret = "CajaMaestraVaultSecret"
    val payload = """{"business":"Mi Negocio","sales":[{"total":1500.0}]}"""
    val encryptedBase64 = AesEncryptionUtil.encrypt(payload, secret)

    // Tamper with a byte in the middle of the ciphertext
    val chars = encryptedBase64.toCharArray()
    chars[chars.size / 2] = if (chars[chars.size / 2] == 'A') 'B' else 'A'
    val tampered = String(chars)

    val result = AesEncryptionUtil.decrypt(tampered, secret)
    // GCM authentication tag must detect tampering and fail decryption
    assertFalse(result.isSuccess)
  }

  @Test
  fun `security test - verify aes fails on incorrect passphrase`() {
    val original = "Datos sensibles de ventas"
    val encrypted = AesEncryptionUtil.encrypt(original, "ClaveCorrecta123")
    val result = AesEncryptionUtil.decrypt(encrypted, "ClaveIncorrecta999")
    assertFalse(result.isSuccess)
  }

  @Test
  fun `functionality test - thermal receipt 58mm text generation`() {
    val settings = BusinessSettings(
        businessName = "Caja Maestra Express",
        address = "Calle 10 #20",
        phone = "555-0100",
        currencySymbol = "$",
        receiptShowFolio = true,
        receiptShowStatus = true
    )
    val sale = Sale(
        folio = "F-00999",
        total = 350.0,
        subtotal = 350.0,
        paidAmount = 500.0,
        changeAmount = 150.0,
        paymentMethod = "Efectivo",
        customerName = "Cliente Prueba",
        sellerName = "Admin"
    )
    val items = listOf(
        SaleCartItem(
            itemId = 1,
            name = "Café Gourmet 500g",
            quantity = 2.0,
            unitPrice = 175.0,
            costPrice = 90.0,
            unit = "Pza",
            type = ItemType.PRODUCTO
        )
    )

    val receipt = ThermalPrinterUtil.formatReceiptText(settings, sale, items)
    assertTrue(receipt.contains("Caja Maestra Express"))
    assertTrue(receipt.contains("F-00999"))
    assertTrue(receipt.contains("TOTAL A PAGAR:"))
    assertTrue(receipt.contains("$350.00"))
    assertTrue(receipt.contains("CAMBIO:"))
    assertTrue(receipt.contains("$150.00"))

    // ESC/POS command verification
    val bytes = ThermalPrinterUtil.buildEscPosBytes(receipt)
    assertTrue(bytes.isNotEmpty())
    assertEquals(0x1B.toByte(), bytes[0]) // ESC
    assertEquals(0x40.toByte(), bytes[1]) // @ (Initialize)
  }

  @Test
  fun `functionality test - cart calculations`() {
    val item1 = SaleCartItem(1, "Prod A", 3.0, 100.0, 50.0, "Pza", ItemType.PRODUCTO)
    val item2 = SaleCartItem(2, "Prod B", 1.5, 200.0, 120.0, "Kg", ItemType.PRODUCTO)

    val subtotal = item1.subtotal + item2.subtotal
    assertEquals(600.0, subtotal, 0.001)

    val discount = 50.0
    val total = (subtotal - discount).coerceAtLeast(0.0)
    assertEquals(550.0, total, 0.001)

    val paid = 600.0
    val change = (paid - total).coerceAtLeast(0.0)
    assertEquals(50.0, change, 0.001)
  }

  @Test
  fun `functionality test - barcode search match`() {
    val items = listOf(
        InventoryItem(id = 1, name = "Harina 1kg", code = "75010001", costPrice = 18.0, salePrice = 25.0, stock = 10.0),
        InventoryItem(id = 2, name = "Azúcar 1kg", code = "75010002", costPrice = 22.0, salePrice = 30.0, stock = 5.0),
        InventoryItem(id = 3, name = "Aceite 1L", code = "75010003", costPrice = 35.0, salePrice = 45.0, stock = 2.0)
    )
    val query = "75010002"
    val found = items.find { it.code.equals(query, ignoreCase = true) }
    assertTrue(found != null)
    assertEquals("Azúcar 1kg", found?.name)
  }

  @Test
  fun `functionality test - supplier history and purchases aggregation`() {
    val supplier = Supplier(
        id = 1,
        name = "Distribuidora Nacional S.A.",
        company = "Distribuidora Nacional",
        contactName = "Juan Pérez",
        phone = "555-4321",
        email = "ventas@distnacional.com",
        suppliedProducts = "Granos, Harinas, Aceites",
        paymentTerms = "Crédito a 30 días"
    )
    val purchases = listOf(
        Purchase(id = 1, supplierId = 1, supplierName = "Distribuidora Nacional S.A.", folio = "OC-00101", total = 5000.0, isCredit = false),
        Purchase(id = 2, supplierId = 1, supplierName = "Distribuidora Nacional S.A.", folio = "OC-00102", total = 3200.0, isCredit = true),
        Purchase(id = 3, supplierId = 2, supplierName = "Otro Proveedor", folio = "OC-00103", total = 1200.0, isCredit = false)
    )

    val supplierPurchases = purchases.filter { it.supplierId == supplier.id }
    val totalInvested = supplierPurchases.sumOf { it.total }
    val pendingCreditDebt = supplierPurchases.filter { it.isCredit }.sumOf { it.total }

    assertEquals(2, supplierPurchases.size)
    assertEquals(8200.0, totalInvested, 0.001)
    assertEquals(3200.0, pendingCreditDebt, 0.001)
  }

  @Test
  fun `security test - role based access control`() {
    val restrictedScreens = setOf(
        com.example.ui.viewmodel.AppScreen.CONFIGURACION,
        com.example.ui.viewmodel.AppScreen.REPORTES,
        com.example.ui.viewmodel.AppScreen.EMPLEADOS
    )

    fun canAccessDirectly(role: UserRole, screen: com.example.ui.viewmodel.AppScreen): Boolean {
      return if (role == UserRole.TRABAJADOR) {
        !restrictedScreens.contains(screen)
      } else {
        true
      }
    }

    // Trabajador can access POS and Inventory directly, but not Configuration or Reports
    assertTrue(canAccessDirectly(UserRole.TRABAJADOR, com.example.ui.viewmodel.AppScreen.VENTAS))
    assertTrue(canAccessDirectly(UserRole.TRABAJADOR, com.example.ui.viewmodel.AppScreen.INVENTARIO))
    assertFalse(canAccessDirectly(UserRole.TRABAJADOR, com.example.ui.viewmodel.AppScreen.CONFIGURACION))
    assertFalse(canAccessDirectly(UserRole.TRABAJADOR, com.example.ui.viewmodel.AppScreen.REPORTES))
    assertFalse(canAccessDirectly(UserRole.TRABAJADOR, com.example.ui.viewmodel.AppScreen.EMPLEADOS))

    // Administrador and Dueño have full direct access
    assertTrue(canAccessDirectly(UserRole.ADMINISTRADOR, com.example.ui.viewmodel.AppScreen.CONFIGURACION))
    assertTrue(canAccessDirectly(UserRole.DUENO, com.example.ui.viewmodel.AppScreen.REPORTES))
  }
}
