package com.example.smartattendance.recognition

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object FaceEmbeddingCrypto {

    private const val KEY_ALIAS = "SmartAttendanceFaceKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_SIZE_BITS = 128
    private const val FORMAT_VERSION: Byte = 1

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    fun encrypt(plainText: ByteArray): ByteArray {
        require(plainText.isNotEmpty()) { "Cannot encrypt empty data" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encrypted = cipher.doFinal(plainText)
        val iv = cipher.iv

        require(iv.size == IV_SIZE) { "Unexpected encryption IV size" }

        return ByteBuffer.allocate(1 + IV_SIZE + encrypted.size)
            .put(FORMAT_VERSION)
            .put(iv)
            .put(encrypted)
            .array()
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        require(encryptedData.size > 1 + IV_SIZE) {
            "Invalid encrypted data"
        }

        val buffer = ByteBuffer.wrap(encryptedData)

        val version = buffer.get()
        require(version == FORMAT_VERSION) {
            "Unsupported encrypted data version"
        }

        val iv = ByteArray(IV_SIZE)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_SIZE_BITS, iv)
        )

        return cipher.doFinal(ciphertext)
    }
}
