package com.steamcontroller.googletv.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

class AdbCrypto private constructor(
    val keyPair: KeyPair
) {
    val privateKey: PrivateKey get() = keyPair.private
    val publicKey: PublicKey get() = keyPair.public

    /**
     * Sign an ADB AUTH challenge token with the private key (SHA1withRSA or NoneWithRSA).
     */
    fun sign(token: ByteArray): ByteArray {
        val cipher = Signature.getInstance("SHA1withRSA")
        cipher.initSign(privateKey)
        cipher.update(token)
        return cipher.sign()
    }

    /**
     * Export public key in standard Android adb_keys format.
     */
    fun getAdbPublicKeyPayload(): ByteArray {
        val rsaPub = publicKey as RSAPublicKey
        val n = rsaPub.modulus
        val e = rsaPub.publicExponent

        // ADB public key format (RSA structure followed by " steamtv@localhost\0")
        val r32 = BigInteger.valueOf(2).pow(32)
        val r = BigInteger.valueOf(2).pow(2048)
        val rr = r.multiply(r).mod(n)
        val n0inv = n.modInverse(r32).negate().and(BigInteger.valueOf(0xFFFFFFFFL)).toLong()

        val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(64) // ANDROID_PUBKEY_MODULUS_SIZE_WORDS (2048 / 32)
        buffer.putInt(n0inv.toInt())

        // Modulus little endian
        val nBytes = n.toByteArray().reversedArray()
        for (i in 0 until 256) {
            buffer.put(if (i < nBytes.size) nBytes[i] else 0)
        }

        // R^2 mod N little endian
        val rrBytes = rr.toByteArray().reversedArray()
        for (i in 0 until 256) {
            buffer.put(if (i < rrBytes.size) rrBytes[i] else 0)
        }

        buffer.putInt(e.toInt())

        val b64 = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        val comment = " steamtv@google-tv\u0000"
        return (b64 + comment).toByteArray(Charsets.UTF_8)
    }

    companion object {
        private const val KEY_PREFS = "adb_crypto_keys"
        private const val PREF_PRIV = "adb_private_key"

        fun loadOrCreate(context: Context): AdbCrypto {
            val prefs = context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE)
            val privB64 = prefs.getString(PREF_PRIV, null)

            if (privB64 != null) {
                try {
                    val privBytes = Base64.decode(privB64, Base64.DEFAULT)
                    val kf = KeyFactory.getInstance("RSA")
                    val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes)) as RSAPrivateCrtKey
                    val pubSpec = RSAPublicKeySpec(privKey.modulus, privKey.publicExponent)
                    val pubKey = kf.generatePublic(pubSpec)
                    return AdbCrypto(KeyPair(pubKey, privKey))
                } catch (e: Exception) {
                    // Regenerate on failure
                }
            }

            // Generate new 2048-bit RSA Key Pair
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()

            val encodedPriv = Base64.encodeToString(kp.private.encoded, Base64.DEFAULT)
            prefs.edit().putString(PREF_PRIV, encodedPriv).apply()

            return AdbCrypto(kp)
        }
    }
}
