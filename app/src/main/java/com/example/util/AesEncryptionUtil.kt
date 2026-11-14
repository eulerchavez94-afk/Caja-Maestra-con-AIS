package com.example.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AesEncryptionUtil {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val ITERATION_COUNT = 65536
    const val BACKUP_KDF_ITERATIONS = 120_000
    private const val KEY_LENGTH_BIT = 256

    private fun deriveAesKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH_BIT)
        return try {
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Encrypts plaintext data with AES-256-GCM using PBKDF2 derived key from passphrase.
     * Output format: Base64(salt + iv + ciphertext)
     */
    fun encrypt(plaintext: String, passphrase: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTE).also { random.nextBytes(it) }

        val secretKey = deriveAesKey(passphrase, salt, ITERATION_COUNT)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64 encrypted string with passphrase using AES-256-GCM.
     */
    fun decrypt(encryptedBase64: String, passphrase: String): Result<String> {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < SALT_LENGTH_BYTE + IV_LENGTH_BYTE + 16) {
                return Result.failure(IllegalArgumentException("Formato de respaldo cifrado inválido"))
            }

            val salt = ByteArray(SALT_LENGTH_BYTE)
            val iv = ByteArray(IV_LENGTH_BYTE)
            val cipherText = ByteArray(combined.size - SALT_LENGTH_BYTE - IV_LENGTH_BYTE)

            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH_BYTE)
            System.arraycopy(combined, SALT_LENGTH_BYTE, iv, 0, IV_LENGTH_BYTE)
            System.arraycopy(combined, SALT_LENGTH_BYTE + IV_LENGTH_BYTE, cipherText, 0, cipherText.size)

            val secretKey = deriveAesKey(passphrase, salt, ITERATION_COUNT)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val plainTextBytes = cipher.doFinal(cipherText)

            val decompressedBytes = try {
                decompressGzip(plainTextBytes)
            } catch (_: Exception) {
                plainTextBytes
            }

            Result.success(String(decompressedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compresses byte array using standard GZIP compression for high efficiency.
     */
    fun compressGzip(data: ByteArray): ByteArray {
        val byteOut = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(byteOut).use { gzip ->
            gzip.write(data)
        }
        return byteOut.toByteArray()
    }

    /**
     * Decompresses GZIP-compressed byte array.
     */
    fun decompressGzip(compressed: ByteArray): ByteArray {
        val byteIn = java.io.ByteArrayInputStream(compressed)
        java.util.zip.GZIPInputStream(byteIn).use { gzip ->
            return gzip.readBytes()
        }
    }

    /**
     * Encrypts plaintext with GZIP compression + AES-256-GCM.
     */
    fun encryptWithGzip(plaintext: String, passphrase: String): String {
        val compressed = compressGzip(plaintext.toByteArray(Charsets.UTF_8))
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTE).also { random.nextBytes(it) }

        val secretKey = deriveAesKey(passphrase, salt, ITERATION_COUNT)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val cipherText = cipher.doFinal(compressed)

        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Encrypts raw UTF-8 bytes using GZIP compression + PBKDF2-HMAC-SHA256 + AES-256-GCM with AAD binding.
     * Output is Base64(salt[16] + iv[12] + ciphertextWithGcmTag).
     */
    fun encryptPayloadWithGzipAndAad(
        plainBytes: ByteArray,
        passphrase: String,
        iterations: Int = BACKUP_KDF_ITERATIONS,
        aad: ByteArray
    ): String {
        val compressed = compressGzip(plainBytes)
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTE).also { random.nextBytes(it) }

        val secretKey = deriveAesKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        cipher.updateAAD(aad)
        val cipherText = cipher.doFinal(compressed)

        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64(salt + iv + ciphertextWithGcmTag) verifying AAD and mandatory GZIP decompression.
     */
    fun decryptPayloadWithGzipAndAad(
        payloadBase64: String,
        passphrase: String,
        iterations: Int = BACKUP_KDF_ITERATIONS,
        aad: ByteArray
    ): Result<ByteArray> {
        return try {
            val combined = Base64.decode(payloadBase64, Base64.NO_WRAP)
            if (combined.size < SALT_LENGTH_BYTE + IV_LENGTH_BYTE + 16) {
                return Result.failure(IllegalArgumentException("Datos cifrados incompletos o dañados"))
            }

            val salt = ByteArray(SALT_LENGTH_BYTE)
            val iv = ByteArray(IV_LENGTH_BYTE)
            val cipherText = ByteArray(combined.size - SALT_LENGTH_BYTE - IV_LENGTH_BYTE)

            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH_BYTE)
            System.arraycopy(combined, SALT_LENGTH_BYTE, iv, 0, IV_LENGTH_BYTE)
            System.arraycopy(combined, SALT_LENGTH_BYTE + IV_LENGTH_BYTE, cipherText, 0, cipherText.size)

            val secretKey = deriveAesKey(passphrase, salt, iterations)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            cipher.updateAAD(aad)
            val compressedBytes = cipher.doFinal(cipherText)
            val plainBytes = decompressGzip(compressedBytes)
            Result.success(plainBytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
