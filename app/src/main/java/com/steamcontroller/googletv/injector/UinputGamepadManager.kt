package com.steamcontroller.googletv.injector

import android.content.Context
import android.util.Log
import com.steamcontroller.googletv.adb.AdbShellStream
import com.steamcontroller.googletv.remapper.VirtualGamepadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

class UinputGamepadManager(
    private val context: Context,
    private val shellStream: AdbShellStream
) {
    private val tag = "UinputGamepad"
    private var isDeviceRegistered = false
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastBytes = ByteArray(14)
    private val buffer = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)

    fun initializeVirtualGamepad() {
        Log.d(tag, "Initializing native uinput daemon via shell ADB...")

        // 1. Extract daemon asset to app data directory
        extractDaemonAsset()

        // 2. Kill old instances and launch daemon as shell UID 2000 via ADB stream
        shellStream.writeCommand("killall sc_uinput_daemon 2>/dev/null; chmod 755 /data/local/tmp/sc_uinput_daemon; /data/local/tmp/sc_uinput_daemon &")

        // 3. Connect to local daemon TCP socket with retry
        scope.launch {
            for (attempt in 1..25) {
                delay(200)
                try {
                    val s = Socket("127.0.0.1", 4455)
                    s.tcpNoDelay = true
                    socket = s
                    outputStream = DataOutputStream(s.getOutputStream())
                    isDeviceRegistered = true
                    Log.i(tag, "Successfully connected to native sc_uinput_daemon on 127.0.0.1:4455!")
                    break
                } catch (e: Exception) {
                    Log.v(tag, "Waiting for daemon socket on 127.0.0.1:4455... ($attempt/25)")
                }
            }
        }
    }

    private fun extractDaemonAsset() {
        try {
            val outFile = File(context.filesDir, "sc_uinput_daemon")
            context.assets.open("sc_uinput_daemon").use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile.setExecutable(true, false)

            // Copy to /data/local/tmp/ via shell if possible
            shellStream.writeCommand("cp ${outFile.absolutePath} /data/local/tmp/sc_uinput_daemon 2>/dev/null; chmod 755 /data/local/tmp/sc_uinput_daemon")
        } catch (e: Exception) {
            Log.w(tag, "Asset extraction: ${e.message}")
        }
    }

    fun dispatchState(state: VirtualGamepadState) {
        val out = outputStream ?: return
        if (!isDeviceRegistered) return

        try {
            var btnBits = 0
            if (state.btnA) btnBits = btnBits or (1 shl 0)
            if (state.btnB) btnBits = btnBits or (1 shl 1)
            if (state.btnX) btnBits = btnBits or (1 shl 2)
            if (state.btnY) btnBits = btnBits or (1 shl 3)
            if (state.btnLB) btnBits = btnBits or (1 shl 4)
            if (state.btnRB) btnBits = btnBits or (1 shl 5)
            if (state.btnSelect) btnBits = btnBits or (1 shl 6)
            if (state.btnStart) btnBits = btnBits or (1 shl 7)
            if (state.btnGuide) btnBits = btnBits or (1 shl 8)
            if (state.btnL3) btnBits = btnBits or (1 shl 9)
            if (state.btnR3) btnBits = btnBits or (1 shl 10)

            val lx = state.toLinuxAxis(state.leftStickX).toShort()
            val ly = state.toLinuxAxis(state.leftStickY).toShort()
            val rx = state.toLinuxAxis(state.rightStickX).toShort()
            val ry = state.toLinuxAxis(state.rightStickY).toShort()

            val lt = (state.leftTrigger * 255.0f).toInt().coerceIn(0, 255).toByte()
            val rt = (state.rightTrigger * 255.0f).toInt().coerceIn(0, 255).toByte()

            val dx = state.dpadX.toByte()
            val dy = state.dpadY.toByte()

            synchronized(buffer) {
                buffer.clear()
                buffer.putShort(btnBits.toShort())
                buffer.putShort(lx)
                buffer.putShort(ly)
                buffer.putShort(rx)
                buffer.putShort(ry)
                buffer.put(lt)
                buffer.put(rt)
                buffer.put(dx)
                buffer.put(dy)

                val arr = buffer.array()
                if (Arrays.equals(arr, lastBytes)) return // Skip redundant duplicate frames

                System.arraycopy(arr, 0, lastBytes, 0, 14)
                out.write(arr)
                out.flush()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to write gamepad packet: ${e.message}")
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignored
        } finally {
            socket = null
            outputStream = null
            isDeviceRegistered = false
        }
    }
}
