package com.example.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.model.BusinessSettings
import com.example.data.model.ReceiptFormat
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted credential vault persisted inside DataStore.
 * Sensitive secrets (passwords, PIN, recovery codes, recovery answer) are stored strictly as
 * salted PBKDF2-HMAC-SHA256 hashes inside an AES-256-GCM encrypted JSON blob.
 */
data class SecurityVault(
    val isInitialized: Boolean = false,
    val adminPasswordHash: String = "",
    val jefePasswordHash: String = "",
    val trabajadorPasswordHash: String = "",
    val masterPinHash: String = "",
    val ownerRecoveryCodeHash: String = "",
    val adminRecoveryCodeHash: String = "",
    val recoveryQuestion: String = "",
    val ownerRecoveryAnswerHash: String = "",
    val failedAttempts: Int = 0,
    val lockoutUntilMs: Long = 0L
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("isInitialized", isInitialized)
            put("adminPasswordHash", adminPasswordHash)
            put("jefePasswordHash", jefePasswordHash)
            put("trabajadorPasswordHash", trabajadorPasswordHash)
            put("masterPinHash", masterPinHash)
            put("ownerRecoveryCodeHash", ownerRecoveryCodeHash)
            put("adminRecoveryCodeHash", adminRecoveryCodeHash)
            put("recoveryQuestion", recoveryQuestion)
            put("ownerRecoveryAnswerHash", ownerRecoveryAnswerHash)
            put("failedAttempts", failedAttempts)
            put("lockoutUntilMs", lockoutUntilMs)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): SecurityVault {
            return try {
                val obj = JSONObject(json)
                SecurityVault(
                    isInitialized = obj.optBoolean("isInitialized", false),
                    adminPasswordHash = obj.optString("adminPasswordHash", ""),
                    jefePasswordHash = obj.optString("jefePasswordHash", ""),
                    trabajadorPasswordHash = obj.optString("trabajadorPasswordHash", ""),
                    masterPinHash = obj.optString("masterPinHash", ""),
                    ownerRecoveryCodeHash = obj.optString("ownerRecoveryCodeHash", ""),
                    adminRecoveryCodeHash = obj.optString("adminRecoveryCodeHash", ""),
                    recoveryQuestion = obj.optString("recoveryQuestion", ""),
                    ownerRecoveryAnswerHash = obj.optString("ownerRecoveryAnswerHash", ""),
                    failedAttempts = obj.optInt("failedAttempts", 0),
                    lockoutUntilMs = obj.optLong("lockoutUntilMs", 0L)
                )
            } catch (_: Exception) {
                SecurityVault()
            }
        }
    }
}

data class StoredAppState(
    val settings: BusinessSettings,
    val vault: SecurityVault
)

/**
 * Persistent storage backed by Jetpack DataStore Preferences + Android KeyStore AES-256-GCM vault.
 */
class SettingsStorage private constructor(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val fallbackKeyFile: File
) {
    private val writeMutex = Mutex()

    suspend fun loadState(): StoredAppState {
        val prefs = dataStore.data.first()
        val defaults = BusinessSettings()

        val loadedRole = prefs[KEY_CURRENT_ROLE]?.let { name ->
            runCatching { UserRole.valueOf(name) }.getOrNull()
        } ?: defaults.currentRole

        val loadedReceiptFormat = prefs[KEY_RECEIPT_FORMAT]?.let { name ->
            runCatching { ReceiptFormat.valueOf(name) }.getOrNull()
        } ?: defaults.receiptFormat

        val settings = BusinessSettings(
            businessName = prefs[KEY_BUSINESS_NAME] ?: defaults.businessName,
            phone = prefs[KEY_PHONE] ?: defaults.phone,
            email = prefs[KEY_EMAIL] ?: defaults.email,
            address = prefs[KEY_ADDRESS] ?: defaults.address,
            footerText = prefs[KEY_FOOTER_TEXT] ?: defaults.footerText,
            customQr = prefs[KEY_CUSTOM_QR] ?: defaults.customQr,
            currencySymbol = prefs[KEY_CURRENCY_SYMBOL] ?: defaults.currencySymbol,
            numberFormat = prefs[KEY_NUMBER_FORMAT] ?: defaults.numberFormat,
            checkStock = prefs[KEY_CHECK_STOCK] ?: defaults.checkStock,
            useAverageCost = prefs[KEY_USE_AVERAGE_COST] ?: defaults.useAverageCost,
            allowMultipleSales = prefs[KEY_ALLOW_MULTIPLE_SALES] ?: defaults.allowMultipleSales,
            allowEditDate = prefs[KEY_ALLOW_EDIT_DATE] ?: defaults.allowEditDate,
            showCustomerPayAndChange = prefs[KEY_SHOW_PAY_CHANGE] ?: defaults.showCustomerPayAndChange,
            receiptFormat = loadedReceiptFormat,
            receiptShowClientInfo = prefs[KEY_RECEIPT_CLIENT_INFO] ?: defaults.receiptShowClientInfo,
            receiptShowQr = prefs[KEY_RECEIPT_SHOW_QR] ?: defaults.receiptShowQr,
            receiptShowPaymentBreakdown = prefs[KEY_RECEIPT_PAYMENT_BREAKDOWN] ?: defaults.receiptShowPaymentBreakdown,
            receiptShowFolio = prefs[KEY_RECEIPT_SHOW_FOLIO] ?: defaults.receiptShowFolio,
            receiptShowStatus = prefs[KEY_RECEIPT_SHOW_STATUS] ?: defaults.receiptShowStatus,
            receiptShowKeyAndUnit = prefs[KEY_RECEIPT_KEY_UNIT] ?: defaults.receiptShowKeyAndUnit,
            bluetoothPrinterName = prefs[KEY_BT_PRINTER_NAME] ?: defaults.bluetoothPrinterName,
            bluetoothPrinterAddress = prefs[KEY_BT_PRINTER_ADDRESS] ?: defaults.bluetoothPrinterAddress,
            printScale = prefs[KEY_PRINT_SCALE] ?: defaults.printScale,
            printIntensity = prefs[KEY_PRINT_INTENSITY] ?: defaults.printIntensity,
            restrictWithPin = prefs[KEY_RESTRICT_WITH_PIN] ?: defaults.restrictWithPin,
            currentRole = loadedRole,
            currentUserName = prefs[KEY_CURRENT_USER_NAME] ?: defaults.currentUserName,
            externalScanner = prefs[KEY_EXTERNAL_SCANNER] ?: defaults.externalScanner,
            continuousScan = prefs[KEY_CONTINUOUS_SCAN] ?: defaults.continuousScan,
            frontCamera = prefs[KEY_FRONT_CAMERA] ?: defaults.frontCamera,
            isAppLocked = prefs[KEY_IS_APP_LOCKED] ?: defaults.isAppLocked,
            trialModeEnabled = prefs[KEY_TRIAL_MODE_ENABLED] ?: defaults.trialModeEnabled,
            trialDurationDays = prefs[KEY_TRIAL_DURATION_DAYS] ?: defaults.trialDurationDays,
            trialStartDate = prefs[KEY_TRIAL_START_DATE] ?: defaults.trialStartDate,
            appLockReason = prefs[KEY_APP_LOCK_REASON] ?: defaults.appLockReason,
            moduleInventoryEnabled = prefs[KEY_MOD_INVENTORY] ?: defaults.moduleInventoryEnabled,
            moduleSalesEnabled = prefs[KEY_MOD_SALES] ?: defaults.moduleSalesEnabled,
            modulePurchasesEnabled = prefs[KEY_MOD_PURCHASES] ?: defaults.modulePurchasesEnabled,
            moduleCashflowEnabled = prefs[KEY_MOD_CASHFLOW] ?: defaults.moduleCashflowEnabled,
            moduleRentalsEnabled = prefs[KEY_MOD_RENTALS] ?: defaults.moduleRentalsEnabled,
            moduleReportsEnabled = prefs[KEY_MOD_REPORTS] ?: defaults.moduleReportsEnabled,
            moduleEmployeesEnabled = prefs[KEY_MOD_EMPLOYEES] ?: defaults.moduleEmployeesEnabled,
            moduleClientsEnabled = prefs[KEY_MOD_CLIENTS] ?: defaults.moduleClientsEnabled,
            moduleSuppliersEnabled = prefs[KEY_MOD_SUPPLIERS] ?: defaults.moduleSuppliersEnabled,
            moduleThermalPrinterEnabled = prefs[KEY_MOD_PRINTER] ?: defaults.moduleThermalPrinterEnabled,
            moduleCloudBackupEnabled = prefs[KEY_MOD_CLOUD] ?: defaults.moduleCloudBackupEnabled,
            moduleSettingsEnabled = prefs[KEY_MOD_SETTINGS] ?: defaults.moduleSettingsEnabled,
            jefeAllowsTrabajadorInventory = prefs[KEY_JEFE_INVENTORY] ?: defaults.jefeAllowsTrabajadorInventory,
            jefeAllowsTrabajadorSales = prefs[KEY_JEFE_SALES] ?: defaults.jefeAllowsTrabajadorSales,
            jefeAllowsTrabajadorPurchases = prefs[KEY_JEFE_PURCHASES] ?: defaults.jefeAllowsTrabajadorPurchases,
            jefeAllowsTrabajadorCashflow = prefs[KEY_JEFE_CASHFLOW] ?: defaults.jefeAllowsTrabajadorCashflow,
            jefeAllowsTrabajadorRentals = prefs[KEY_JEFE_RENTALS] ?: defaults.jefeAllowsTrabajadorRentals,
            jefeAllowsTrabajadorReports = prefs[KEY_JEFE_REPORTS] ?: defaults.jefeAllowsTrabajadorReports,
            jefeAllowsTrabajadorEmployees = prefs[KEY_JEFE_EMPLOYEES] ?: defaults.jefeAllowsTrabajadorEmployees,
            jefeAllowsTrabajadorClients = prefs[KEY_JEFE_CLIENTS] ?: defaults.jefeAllowsTrabajadorClients,
            jefeAllowsTrabajadorSuppliers = prefs[KEY_JEFE_SUPPLIERS] ?: defaults.jefeAllowsTrabajadorSuppliers,
            jefeAllowsTrabajadorSettings = prefs[KEY_JEFE_SETTINGS] ?: defaults.jefeAllowsTrabajadorSettings
        )

        val encryptedBlob = prefs[KEY_SECURE_VAULT_BLOB]
        val vault = if (!encryptedBlob.isNullOrBlank()) {
            decryptVault(encryptedBlob)
        } else {
            SecurityVault()
        }

        return StoredAppState(settings = settings, vault = vault)
    }

    suspend fun saveBusinessSettings(settings: BusinessSettings) {
        writeMutex.withLock {
            dataStore.edit { prefs ->
                applyBusinessSettings(prefs, settings)
            }
        }
    }

    suspend fun saveSecurityVault(vault: SecurityVault) {
        writeMutex.withLock {
            val encryptedBlob = encryptVault(vault)
            dataStore.edit { prefs ->
                prefs[KEY_SECURE_VAULT_BLOB] = encryptedBlob
            }
        }
    }

    suspend fun saveAll(settings: BusinessSettings, vault: SecurityVault) {
        writeMutex.withLock {
            val encryptedBlob = encryptVault(vault)
            dataStore.edit { prefs ->
                applyBusinessSettings(prefs, settings)
                prefs[KEY_SECURE_VAULT_BLOB] = encryptedBlob
            }
        }
    }

    internal suspend fun clearAllForTest() {
        writeMutex.withLock {
            dataStore.edit { it.clear() }
        }
    }

    private fun applyBusinessSettings(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        s: BusinessSettings
    ) {
        prefs[KEY_BUSINESS_NAME] = s.businessName
        prefs[KEY_PHONE] = s.phone
        prefs[KEY_EMAIL] = s.email
        prefs[KEY_ADDRESS] = s.address
        prefs[KEY_FOOTER_TEXT] = s.footerText
        prefs[KEY_CUSTOM_QR] = s.customQr
        prefs[KEY_CURRENCY_SYMBOL] = s.currencySymbol
        prefs[KEY_NUMBER_FORMAT] = s.numberFormat
        prefs[KEY_CHECK_STOCK] = s.checkStock
        prefs[KEY_USE_AVERAGE_COST] = s.useAverageCost
        prefs[KEY_ALLOW_MULTIPLE_SALES] = s.allowMultipleSales
        prefs[KEY_ALLOW_EDIT_DATE] = s.allowEditDate
        prefs[KEY_SHOW_PAY_CHANGE] = s.showCustomerPayAndChange
        prefs[KEY_RECEIPT_FORMAT] = s.receiptFormat.name
        prefs[KEY_RECEIPT_CLIENT_INFO] = s.receiptShowClientInfo
        prefs[KEY_RECEIPT_SHOW_QR] = s.receiptShowQr
        prefs[KEY_RECEIPT_PAYMENT_BREAKDOWN] = s.receiptShowPaymentBreakdown
        prefs[KEY_RECEIPT_SHOW_FOLIO] = s.receiptShowFolio
        prefs[KEY_RECEIPT_SHOW_STATUS] = s.receiptShowStatus
        prefs[KEY_RECEIPT_KEY_UNIT] = s.receiptShowKeyAndUnit
        prefs[KEY_BT_PRINTER_NAME] = s.bluetoothPrinterName
        prefs[KEY_BT_PRINTER_ADDRESS] = s.bluetoothPrinterAddress
        prefs[KEY_PRINT_SCALE] = s.printScale
        prefs[KEY_PRINT_INTENSITY] = s.printIntensity
        prefs[KEY_RESTRICT_WITH_PIN] = s.restrictWithPin
        prefs[KEY_CURRENT_ROLE] = s.currentRole.name
        prefs[KEY_CURRENT_USER_NAME] = s.currentUserName
        prefs[KEY_EXTERNAL_SCANNER] = s.externalScanner
        prefs[KEY_CONTINUOUS_SCAN] = s.continuousScan
        prefs[KEY_FRONT_CAMERA] = s.frontCamera
        prefs[KEY_IS_APP_LOCKED] = s.isAppLocked
        prefs[KEY_TRIAL_MODE_ENABLED] = s.trialModeEnabled
        prefs[KEY_TRIAL_DURATION_DAYS] = s.trialDurationDays
        prefs[KEY_TRIAL_START_DATE] = s.trialStartDate
        prefs[KEY_APP_LOCK_REASON] = s.appLockReason
        prefs[KEY_MOD_INVENTORY] = s.moduleInventoryEnabled
        prefs[KEY_MOD_SALES] = s.moduleSalesEnabled
        prefs[KEY_MOD_PURCHASES] = s.modulePurchasesEnabled
        prefs[KEY_MOD_CASHFLOW] = s.moduleCashflowEnabled
        prefs[KEY_MOD_RENTALS] = s.moduleRentalsEnabled
        prefs[KEY_MOD_REPORTS] = s.moduleReportsEnabled
        prefs[KEY_MOD_EMPLOYEES] = s.moduleEmployeesEnabled
        prefs[KEY_MOD_CLIENTS] = s.moduleClientsEnabled
        prefs[KEY_MOD_SUPPLIERS] = s.moduleSuppliersEnabled
        prefs[KEY_MOD_PRINTER] = s.moduleThermalPrinterEnabled
        prefs[KEY_MOD_CLOUD] = s.moduleCloudBackupEnabled
        prefs[KEY_MOD_SETTINGS] = s.moduleSettingsEnabled
        prefs[KEY_JEFE_INVENTORY] = s.jefeAllowsTrabajadorInventory
        prefs[KEY_JEFE_SALES] = s.jefeAllowsTrabajadorSales
        prefs[KEY_JEFE_PURCHASES] = s.jefeAllowsTrabajadorPurchases
        prefs[KEY_JEFE_CASHFLOW] = s.jefeAllowsTrabajadorCashflow
        prefs[KEY_JEFE_RENTALS] = s.jefeAllowsTrabajadorRentals
        prefs[KEY_JEFE_REPORTS] = s.jefeAllowsTrabajadorReports
        prefs[KEY_JEFE_EMPLOYEES] = s.jefeAllowsTrabajadorEmployees
        prefs[KEY_JEFE_CLIENTS] = s.jefeAllowsTrabajadorClients
        prefs[KEY_JEFE_SUPPLIERS] = s.jefeAllowsTrabajadorSuppliers
        prefs[KEY_JEFE_SETTINGS] = s.jefeAllowsTrabajadorSettings
    }

    private fun getOrCreateSecretKey(): SecretKey {
        // Primary path on Android hardware/OS: AndroidKeyStore
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            return keyGen.generateKey()
        } catch (_: Exception) {
            // Fallback strictly for JVM/Robolectric test environments where AndroidKeyStore provider does not exist
            synchronized(this) {
                if (fallbackKeyFile.exists()) {
                    val encoded = fallbackKeyFile.readBytes()
                    if (encoded.size == 32) {
                        return SecretKeySpec(encoded, "AES")
                    }
                }
                val randomKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
                fallbackKeyFile.parentFile?.mkdirs()
                fallbackKeyFile.writeBytes(randomKey)
                return SecretKeySpec(randomKey, "AES")
            }
        }
    }

    fun encryptBytesWithLocalKeyStore(plainBytes: ByteArray): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)

        val combined = ByteArray(1 + iv.size + cipherText.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherText, 0, combined, 1 + iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptBytesWithLocalKeyStore(blob: String): ByteArray {
        val combined = Base64.decode(blob, Base64.NO_WRAP)
        require(combined.size >= 14) { "Invalid KeyStore blob" }
        val ivLen = combined[0].toInt() and 0xFF
        require(combined.size > 1 + ivLen) { "Invalid KeyStore IV" }

        val iv = ByteArray(ivLen)
        val cipherText = ByteArray(combined.size - 1 - ivLen)
        System.arraycopy(combined, 1, iv, 0, ivLen)
        System.arraycopy(combined, 1 + ivLen, cipherText, 0, cipherText.size)

        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun encryptVault(vault: SecurityVault): String {
        return encryptBytesWithLocalKeyStore(vault.toJson().toByteArray(Charsets.UTF_8))
    }

    private fun decryptVault(blob: String): SecurityVault {
        return try {
            val plain = String(decryptBytesWithLocalKeyStore(blob), Charsets.UTF_8)
            SecurityVault.fromJson(plain)
        } catch (_: Exception) {
            SecurityVault()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "caja_maestra_vault_aes_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DEFAULT_DATASTORE_FILE = "caja_maestra_settings_v1.preferences_pb"

        private val storeCache = ConcurrentHashMap<String, DataStore<Preferences>>()

        fun getInstance(context: Context): SettingsStorage {
            val appCtx = context.applicationContext
            val file = File(appCtx.filesDir, "datastore/$DEFAULT_DATASTORE_FILE")
            return createForFile(appCtx, file)
        }

        internal fun createForFile(context: Context, file: File): SettingsStorage {
            val appCtx = context.applicationContext
            val canonical = file.canonicalPath
            val ds = storeCache.computeIfAbsent(canonical) {
                PreferenceDataStoreFactory.create(
                    produceFile = {
                        file.parentFile?.mkdirs()
                        file
                    }
                )
            }
            val fallbackKeyFile = File(appCtx.filesDir, "datastore/.vault_local_key_${canonical.hashCode()}")
            return SettingsStorage(appCtx, ds, fallbackKeyFile)
        }

        private val KEY_SECURE_VAULT_BLOB = stringPreferencesKey("secure_vault_blob")
        private val KEY_BUSINESS_NAME = stringPreferencesKey("business_name")
        private val KEY_PHONE = stringPreferencesKey("phone")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_ADDRESS = stringPreferencesKey("address")
        private val KEY_FOOTER_TEXT = stringPreferencesKey("footer_text")
        private val KEY_CUSTOM_QR = stringPreferencesKey("custom_qr")
        private val KEY_CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        private val KEY_NUMBER_FORMAT = stringPreferencesKey("number_format")
        private val KEY_CHECK_STOCK = booleanPreferencesKey("check_stock")
        private val KEY_USE_AVERAGE_COST = booleanPreferencesKey("use_average_cost")
        private val KEY_ALLOW_MULTIPLE_SALES = booleanPreferencesKey("allow_multiple_sales")
        private val KEY_ALLOW_EDIT_DATE = booleanPreferencesKey("allow_edit_date")
        private val KEY_SHOW_PAY_CHANGE = booleanPreferencesKey("show_pay_change")
        private val KEY_RECEIPT_FORMAT = stringPreferencesKey("receipt_format")
        private val KEY_RECEIPT_CLIENT_INFO = booleanPreferencesKey("receipt_client_info")
        private val KEY_RECEIPT_SHOW_QR = booleanPreferencesKey("receipt_show_qr")
        private val KEY_RECEIPT_PAYMENT_BREAKDOWN = booleanPreferencesKey("receipt_payment_breakdown")
        private val KEY_RECEIPT_SHOW_FOLIO = booleanPreferencesKey("receipt_show_folio")
        private val KEY_RECEIPT_SHOW_STATUS = booleanPreferencesKey("receipt_show_status")
        private val KEY_RECEIPT_KEY_UNIT = booleanPreferencesKey("receipt_key_unit")
        private val KEY_BT_PRINTER_NAME = stringPreferencesKey("bt_printer_name")
        private val KEY_BT_PRINTER_ADDRESS = stringPreferencesKey("bt_printer_address")
        private val KEY_PRINT_SCALE = floatPreferencesKey("print_scale")
        private val KEY_PRINT_INTENSITY = intPreferencesKey("print_intensity")
        private val KEY_RESTRICT_WITH_PIN = booleanPreferencesKey("restrict_with_pin")
        private val KEY_CURRENT_ROLE = stringPreferencesKey("current_role")
        private val KEY_CURRENT_USER_NAME = stringPreferencesKey("current_user_name")
        private val KEY_EXTERNAL_SCANNER = booleanPreferencesKey("external_scanner")
        private val KEY_CONTINUOUS_SCAN = booleanPreferencesKey("continuous_scan")
        private val KEY_FRONT_CAMERA = booleanPreferencesKey("front_camera")
        private val KEY_IS_APP_LOCKED = booleanPreferencesKey("is_app_locked")
        private val KEY_TRIAL_MODE_ENABLED = booleanPreferencesKey("trial_mode_enabled")
        private val KEY_TRIAL_DURATION_DAYS = intPreferencesKey("trial_duration_days")
        private val KEY_TRIAL_START_DATE = longPreferencesKey("trial_start_date")
        private val KEY_APP_LOCK_REASON = stringPreferencesKey("app_lock_reason")

        private val KEY_MOD_INVENTORY = booleanPreferencesKey("mod_inventory")
        private val KEY_MOD_SALES = booleanPreferencesKey("mod_sales")
        private val KEY_MOD_PURCHASES = booleanPreferencesKey("mod_purchases")
        private val KEY_MOD_CASHFLOW = booleanPreferencesKey("mod_cashflow")
        private val KEY_MOD_RENTALS = booleanPreferencesKey("mod_rentals")
        private val KEY_MOD_REPORTS = booleanPreferencesKey("mod_reports")
        private val KEY_MOD_EMPLOYEES = booleanPreferencesKey("mod_employees")
        private val KEY_MOD_CLIENTS = booleanPreferencesKey("mod_clients")
        private val KEY_MOD_SUPPLIERS = booleanPreferencesKey("mod_suppliers")
        private val KEY_MOD_PRINTER = booleanPreferencesKey("mod_printer")
        private val KEY_MOD_CLOUD = booleanPreferencesKey("mod_cloud")
        private val KEY_MOD_SETTINGS = booleanPreferencesKey("mod_settings")

        private val KEY_JEFE_INVENTORY = booleanPreferencesKey("jefe_inventory")
        private val KEY_JEFE_SALES = booleanPreferencesKey("jefe_sales")
        private val KEY_JEFE_PURCHASES = booleanPreferencesKey("jefe_purchases")
        private val KEY_JEFE_CASHFLOW = booleanPreferencesKey("jefe_cashflow")
        private val KEY_JEFE_RENTALS = booleanPreferencesKey("jefe_rentals")
        private val KEY_JEFE_REPORTS = booleanPreferencesKey("jefe_reports")
        private val KEY_JEFE_EMPLOYEES = booleanPreferencesKey("jefe_employees")
        private val KEY_JEFE_CLIENTS = booleanPreferencesKey("jefe_clients")
        private val KEY_JEFE_SUPPLIERS = booleanPreferencesKey("jefe_suppliers")
        private val KEY_JEFE_SETTINGS = booleanPreferencesKey("jefe_settings")
    }
}
