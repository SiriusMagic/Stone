package com.zirakusa.stone.nativeapp.data.persistence

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts API secrets (agent tokens, the relay key) before they are stored, with an AES-GCM key
 * that lives in the Android Keystore and never leaves it. A copied database, a backup or another
 * app reading Stone's files only sees "ks1:…" ciphertext.
 *
 * Where the Keystore is unavailable (unit tests, a broken device) values are kept as they are,
 * so Stone keeps working; plain values written by older versions are read and re-sealed.
 */
object SecretBox {
    private const val ALIAS = "stone.api.secrets"
    private const val PREFIX = "ks1:"
    private const val TAG_BITS = 128

    fun isSealed(value: String) = value.startsWith(PREFIX)

    fun seal(plain: String): String = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoder = Base64.getEncoder()
        PREFIX + encoder.encodeToString(cipher.iv) + ":" + encoder.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }.getOrDefault(plain)

    /** The plain value; "" when it was sealed with a key this phone no longer has. */
    fun open(stored: String): String {
        if (!isSealed(stored)) return stored
        return runCatching {
            val (iv, data) = stored.removePrefix(PREFIX).split(':', limit = 2).map { Base64.getDecoder().decode(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
}
