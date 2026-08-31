package com.steamcontroller.googletv.adb

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbShellStream(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    val localId: Int,
    var remoteId: Int
) : Closeable {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun writeCommand(cmd: String) {
        val payload = (cmd + "\n").toByteArray(Charsets.UTF_8)
        ioScope.launch {
            sendPacket(AdbConstants.A_WRTE, localId, remoteId, payload)
        }
    }

    fun writeRaw(bytes: ByteArray) {
        ioScope.launch {
            sendPacket(AdbConstants.A_WRTE, localId, remoteId, bytes)
        }
    }

    private fun sendPacket(cmd: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(cmd)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(data.size)
        var crc = 0
        for (b in data) {
            crc += (b.toInt() and 0xFF)
        }
        header.putInt(crc)
        header.putInt(cmd xor -1)

        synchronized(output) {
            try {
                output.write(header.array())
                if (data.isNotEmpty()) {
                    output.write(data)
                }
                output.flush()
            } catch (e: Exception) {
                Log.w("AdbShellStream", "Failed to send packet: ${e.message}")
            }
        }
    }

    override fun close() {
        ioScope.launch {
            try {
                sendPacket(AdbConstants.A_CLSE, localId, remoteId, ByteArray(0))
                socket.close()
            } catch (e: Exception) {
                // Ignored
            }
        }
    }
}

object AdbConstants {
    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257
    const val A_AUTH = 0x48545541

    const val A_AUTH_TOKEN = 1
    const val A_AUTH_SIGNATURE = 2
    const val A_AUTH_RSAPUBLICKEY = 3

    const val ADB_VERSION = 0x01000001
    const val MAX_PAYLOAD = 4096
}

class AdbConnectionManager(
    private val crypto: AdbCrypto
) {
    private val tag = "AdbConnection"
    private var socket: Socket? = null
    private var isConnected = false

    suspend fun connect(host: String = "127.0.0.1", port: Int): Result<AdbShellStream> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Connecting to ADB server at $host:$port...")
                val sock = Socket(host, port)
                sock.tcpNoDelay = true
                val input = DataInputStream(sock.getInputStream())
                val output = DataOutputStream(sock.getOutputStream())

                // 1. Send CNXN handshake
                val banner = "host::steam-controller-tv\u0000".toByteArray(Charsets.UTF_8)
                sendPacket(output, AdbConstants.A_CNXN, AdbConstants.ADB_VERSION, AdbConstants.MAX_PAYLOAD, banner)

                // 2. Read Response & Authenticate
                var authed = false
                var sentSignature = false

                while (!authed) {
                    val header = readHeader(input)
                    val body = ByteArray(header.dataLength)
                    if (header.dataLength > 0) {
                        input.readFully(body)
                    }

                    when (header.command) {
                        AdbConstants.A_CNXN -> {
                            Log.d(tag, "ADB CNXN authenticated successfully!")
                            authed = true
                        }
                        AdbConstants.A_AUTH -> {
                            if (!sentSignature) {
                                Log.d(tag, "Received AUTH token, trying signed signature...")
                                val signature = crypto.sign(body)
                                sendPacket(output, AdbConstants.A_AUTH, AdbConstants.A_AUTH_SIGNATURE, 0, signature)
                                sentSignature = true
                            } else {
                                Log.d(tag, "Signature rejected, sending RSA Public Key to trigger popup dialog...")
                                val pubKeyPayload = crypto.getAdbPublicKeyPayload()
                                sendPacket(output, AdbConstants.A_AUTH, AdbConstants.A_AUTH_RSAPUBLICKEY, 0, pubKeyPayload)
                            }
                        }
                        else -> {
                            Log.w(tag, "Unexpected command during handshake: ${Integer.toHexString(header.command)}")
                        }
                    }
                }

                // 3. Open standard interactive shell stream
                val localId = 1
                val destination = "shell:\u0000".toByteArray(Charsets.UTF_8)
                sendPacket(output, AdbConstants.A_OPEN, localId, 0, destination)

                val responseHeader = readHeader(input)
                if (responseHeader.dataLength > 0) {
                    val drop = ByteArray(responseHeader.dataLength)
                    input.readFully(drop)
                }

                val remoteId = responseHeader.arg0
                Log.d(tag, "ADB shell stream opened! localId=$localId remoteId=$remoteId")

                val stream = AdbShellStream(sock, input, output, localId, remoteId)
                isConnected = true
                socket = sock

                Result.success(stream)
            } catch (e: Exception) {
                Log.e(tag, "ADB connection failed", e)
                Result.failure(e)
            }
        }

    private data class AdbHeader(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val crc: Int,
        val magic: Int
    )

    private fun readHeader(input: DataInputStream): AdbHeader {
        val bytes = ByteArray(24)
        input.readFully(bytes)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return AdbHeader(
            command = buf.getInt(),
            arg0 = buf.getInt(),
            arg1 = buf.getInt(),
            dataLength = buf.getInt(),
            crc = buf.getInt(),
            magic = buf.getInt()
        )
    }

    private fun sendPacket(output: DataOutputStream, cmd: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(cmd)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(data.size)
        var crc = 0
        for (b in data) {
            crc += (b.toInt() and 0xFF)
        }
        header.putInt(crc)
        header.putInt(cmd xor -1)

        synchronized(output) {
            output.write(header.array())
            if (data.isNotEmpty()) {
                output.write(data)
            }
            output.flush()
        }
    }
}
