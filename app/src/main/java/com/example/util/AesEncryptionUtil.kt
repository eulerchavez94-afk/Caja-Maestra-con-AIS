package com.example.util

import android.util.Base64
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
    private const val KEY_LENGTH_BIT = 256

    /**
     * Encrypts plaintext data with AES-256-GCM using PBKDF2 derived key from passphrase.
     * Output format: Base64(salt + iv + ciphertext)
     */
    fun encrypt(plaintext: String, passphrase: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTE).also { random.nextBytes(it) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combine salt + iv + cipherText
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
            if (combined.size < SALT_LENGTH_BYTE + IV_LENGTH_BYTE) {
                return Result.failure(IllegalArgumentException("Formato de respaldo cifrado inválido"))
            }

            val salt = ByteArray(SALT_LENGTH_BYTE)
            val iv = ByteArray(IV_LENGTH_BYTE)
            val cipherText = ByteArray(combined.size - SALT_LENGTH_BYTE - IV_LENGTH_BYTE)

            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH_BYTE)
            System.arraycopy(combined, SALT_LENGTH_BYTE, iv, 0, IV_LENGTH_BYTE)
            System.arraycopy(combined, SALT_LENGTH_BYTE + IV_LENGTH_BYTE, cipherText, 0, cipherText.size)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
            val plainTextBytes = cipher.doFinal(cipherText)

            // Try decompressing GZIP if payload was compressed
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

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val cipherText = cipher.doFinal(compressed)

        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
}
