package com.example.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic password, PIN, and offline recovery hashing utility.
 * Uses PBKDF2WithHmacSHA256 with per-credential random 128-bit salts and constant-time comparison.
 */
object PasswordHasher {
    private const val PREFIX = "pbkdf2_sha256"
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65_536
    private const val SALT_BYTES = 16
    private const val KEY_LENGTH_BITS = 256
    private const val RECOVERY_CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    private val LEGACY_DISALLOWED_DEFAULTS = setOf(
        "admin",
        "jefe",
        "dueno",
        "dueño",
        "trabajador",
        "1234",
        "0000"
    )

    fun isHashed(stored: String): Boolean {
        return stored.startsWith("$PREFIX$") && stored.split("$").size == 4
    }

    fun hash(rawSecret: String): String {
        require(rawSecret.isNotEmpty()) { "Secret cannot be empty" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(rawSecret.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(derived, Base64.NO_WRAP)
        return "$PREFIX$$ITERATIONS$$saltB64$$hashB64"
    }

    fun verify(rawSecret: String, storedHash: String): Boolean {
        if (rawSecret.isEmpty() || !isHashed(storedHash)) return false
        return try {
            val parts = storedHash.split("$")
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            val actual = deriveKey(rawSecret.toCharArray(), salt, iterations, expected.size * 8)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    fun generateRecoveryCode(): String {
        val random = SecureRandom()
        val segments = (0 until 3).map {
            buildString(4) {
                repeat(4) {
                    append(RECOVERY_CHARSET[random.nextInt(RECOVERY_CHARSET.length)])
                }
            }
        }
        return "CM-${segments.joinToString("-")}"
    }

    fun normalizeRecoveryCode(code: String): String {
        return code.trim().uppercase(Locale.ROOT).replace(" ", "")
    }

    fun normalizeAnswer(answer: String): String {
        return answer.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    fun isLegacyDefaultCredential(secret: String): Boolean {
        return LEGACY_DISALLOWED_DEFAULTS.contains(secret.trim().lowercase(Locale.ROOT))
    }

    private fun deriveKey(
        chars: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int
    ): ByteArray {
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val spec = PBEKeySpec(chars, salt, iterations, keyLengthBits)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
