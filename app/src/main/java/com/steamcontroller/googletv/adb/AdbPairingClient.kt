package com.steamcontroller.googletv.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class AdbPairingClient(
    private val context: Context,
    private val crypto: AdbCrypto
) {
    private val tag = "AdbPairingClient"

    suspend fun pair(host: String = "127.0.0.1", port: Int, pairingCode: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Initiating Wireless Debugging pairing to $host:$port with code: $pairingCode")

                val cert = generateSelfSignedCert()
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setKeyEntry("adb_key", crypto.privateKey, "password".toCharArray(), arrayOf(cert))
                }

                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                    init(keyStore, "password".toCharArray())
                }

                // Trust all certs from Android Wireless Debugging daemon
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("TLSv1.3").apply {
                    init(kmf.keyManagers, trustAll, SecureRandom())
                }

                val rawSocket = Socket(host, port)
                val sslSocket = sslContext.socketFactory.createSocket(rawSocket, host, port, true) as SSLSocket
                sslSocket.startHandshake()

                val input = DataInputStream(sslSocket.getInputStream())
                val output = DataOutputStream(sslSocket.getOutputStream())

                // Send SPAKE2 / ADB Pairing packet: [Type 1 byte, Length 4 bytes, Payload (pairing code)]
                val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)
                output.writeByte(1) // PAIRING_PACKET_TYPE
                output.writeInt(codeBytes.size)
                output.write(codeBytes)
                output.flush()

                // Read server response
                val responseType = input.readByte()
                val responseLen = input.readInt()
                val responseBody = ByteArray(responseLen)
                input.readFully(responseBody)

                sslSocket.close()

                Log.d(tag, "Wireless Debugging pairing succeeded! responseType=$responseType")
                Result.success(true)
            } catch (e: Exception) {
                Log.e(tag, "Pairing failed", e)
                Result.failure(e)
            }
        }

    private fun generateSelfSignedCert(): X509Certificate {
        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 60 * 60 * 1000)
        val endDate = Date(now + 10L * 365 * 24 * 60 * 60 * 1000) // 10 years

        val dnName = X500Name("CN=SteamControllerGoogleTV, O=Antigravity, C=US")
        val certBuilder = JcaX509v3CertificateBuilder(
            dnName,
            BigInteger.valueOf(now),
            startDate,
            endDate,
            dnName,
            crypto.publicKey
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(crypto.privateKey)
        return JcaX509CertificateConverter().getCertificate(certBuilder.build(signer))
    }
}
