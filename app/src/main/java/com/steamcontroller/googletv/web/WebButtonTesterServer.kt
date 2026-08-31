package com.steamcontroller.googletv.web

import android.util.Log
import com.steamcontroller.googletv.driver.SteamControllerState
import com.steamcontroller.googletv.remapper.VirtualGamepadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class WebButtonTesterServer(
    private val port: Int = 8080
) {
    private val tag = "WebButtonTester"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val activeClients = CopyOnWriteArrayList<OutputStream>()

    fun start(
        gamepadFlow: StateFlow<VirtualGamepadState>,
        rawStateFlow: StateFlow<SteamControllerState?>
    ) {
        stop()
        serverJob = scope.launch {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                Log.i(tag, "Web Button Tester running at http://0.0.0.0:$port")

                // Launch throttled SSE broadcast loop (max 30fps)
                launch {
                    while (isActive) {
                        delay(33) // ~30fps max
                        if (activeClients.isNotEmpty()) {
                            broadcastState(gamepadFlow.value)
                        }
                    }
                }

                while (isActive) {
                    val socket = ss.accept()
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                if (isActive) Log.w(tag, "Server error: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val line = reader.readLine() ?: return
            val parts = line.split(" ")
            val path = if (parts.size > 1) parts[1] else "/"

            val out = socket.getOutputStream()

            if (path == "/events") {
                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/event-stream\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Connection: keep-alive\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n"
                out.write(header.toByteArray(Charsets.UTF_8))
                out.flush()

                activeClients.add(out)
                while (socket.isConnected && !socket.isClosed) {
                    Thread.sleep(1000)
                }
            } else {
                val html = getTesterHtml()
                val body = html.toByteArray(Charsets.UTF_8)
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                out.write(response.toByteArray(Charsets.UTF_8))
                out.write(body)
                out.flush()
                socket.close()
            }
        } catch (e: Exception) {
            // Client disconnected
        }
    }

    private fun broadcastState(state: VirtualGamepadState) {
        if (activeClients.isEmpty()) return

        val json = """{"btnA":${state.btnA},"btnB":${state.btnB},"btnX":${state.btnX},"btnY":${state.btnY},"btnLB":${state.btnLB},"btnRB":${state.btnRB},"btnL3":${state.btnL3},"btnR3":${state.btnR3},"btnSelect":${state.btnSelect},"btnStart":${state.btnStart},"btnGuide":${state.btnGuide},"leftStickX":${state.leftStickX},"leftStickY":${state.leftStickY},"rightStickX":${state.rightStickX},"rightStickY":${state.rightStickY},"leftTrigger":${state.leftTrigger},"rightTrigger":${state.rightTrigger},"dpadX":${state.dpadX},"dpadY":${state.dpadY}}"""

        val msg = "data: $json\n\n".toByteArray(Charsets.UTF_8)

        for (client in activeClients) {
            try {
                synchronized(client) {
                    client.write(msg)
                    client.flush()
                }
            } catch (e: Exception) {
                activeClients.remove(client)
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignored
        }
        serverSocket = null
        activeClients.clear()
    }

    private fun getTesterHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Steam Controller TV Live Tester</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: #0b1118;
            color: #ffffff;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            padding: 24px;
            display: flex;
            flex-direction: column;
            align-items: center;
        }
        h1 { color: #66c0f4; margin-bottom: 8px; font-size: 28px; }
        .subtitle { color: #8f98a0; margin-bottom: 24px; font-size: 14px; }
        .container {
            display: flex;
            gap: 24px;
            max-width: 1000px;
            width: 100%;
            flex-wrap: wrap;
            justify-content: center;
        }
        .card {
            background: #1b2838;
            border-radius: 12px;
            padding: 20px;
            border: 1px solid #2a475e;
            flex: 1;
            min-width: 320px;
        }
        .card-title {
            color: #66c0f4;
            font-size: 18px;
            font-weight: 600;
            margin-bottom: 16px;
            border-bottom: 1px solid #2a475e;
            padding-bottom: 8px;
        }
        .buttons-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 10px;
            margin-bottom: 20px;
        }
        .btn-badge {
            background: #101822;
            border: 1px solid #2a475e;
            border-radius: 8px;
            padding: 12px 6px;
            text-align: center;
            font-weight: 700;
            font-size: 14px;
            color: #8f98a0;
            transition: all 0.05s ease;
        }
        .btn-badge.active {
            background: #00d2ff;
            color: #000000;
            border-color: #00d2ff;
            box-shadow: 0 0 12px #00d2ff88;
            transform: scale(1.05);
        }
        .axis-row {
            display: flex;
            align-items: center;
            margin-bottom: 12px;
            gap: 12px;
        }
        .axis-label { width: 90px; font-size: 13px; color: #c6d4df; }
        .axis-bar-bg {
            flex: 1;
            height: 14px;
            background: #101822;
            border-radius: 7px;
            overflow: hidden;
            border: 1px solid #2a475e;
            position: relative;
        }
        .axis-bar-fill {
            height: 100%;
            width: 50%;
            background: #66c0f4;
            transition: width 0.05s linear;
        }
        .axis-val { width: 45px; font-size: 12px; text-align: right; color: #8f98a0; }
        .gamepad-indicator {
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 30px;
            margin-top: 10px;
        }
        .stick-box {
            width: 120px;
            height: 120px;
            background: #101822;
            border: 2px solid #2a475e;
            border-radius: 60px;
            position: relative;
        }
        .stick-dot {
            width: 24px;
            height: 24px;
            background: #00d2ff;
            border-radius: 12px;
            position: absolute;
            top: 48px;
            left: 48px;
            box-shadow: 0 0 8px #00d2ff;
        }
        .status-badge {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: 700;
            margin-bottom: 12px;
        }
        .status-live { background: #4cff0033; color: #4cff00; border: 1px solid #4cff00; }
    </style>
</head>
<body>
    <h1>Steam Controller Live TV Tester</h1>
    <div class="subtitle">Real-Time State Inspector & HTML5 Gamepad Visualizer</div>

    <div class="container">
        <div class="card">
            <div class="card-title">Face Buttons & Triggers</div>
            <div class="buttons-grid">
                <div id="btnA" class="btn-badge">A</div>
                <div id="btnB" class="btn-badge">B</div>
                <div id="btnX" class="btn-badge">X</div>
                <div id="btnY" class="btn-badge">Y</div>
                <div id="btnLB" class="btn-badge">LB</div>
                <div id="btnRB" class="btn-badge">RB</div>
                <div id="btnL3" class="btn-badge">L3</div>
                <div id="btnR3" class="btn-badge">R3</div>
                <div id="btnSelect" class="btn-badge">SELECT</div>
                <div id="btnStart" class="btn-badge">START</div>
                <div id="btnGuide" class="btn-badge">STEAM</div>
                <div id="btnDpad" class="btn-badge">D-PAD</div>
            </div>

            <div class="axis-row">
                <div class="axis-label">Left Trigger</div>
                <div class="axis-bar-bg"><div id="barLT" class="axis-bar-fill" style="width: 0%;"></div></div>
                <div id="valLT" class="axis-val">0%</div>
            </div>
            <div class="axis-row">
                <div class="axis-label">Right Trigger</div>
                <div class="axis-bar-bg"><div id="barRT" class="axis-bar-fill" style="width: 0%;"></div></div>
                <div id="valRT" class="axis-val">0%</div>
            </div>
        </div>

        <div class="card">
            <div class="card-title">Analog Sticks & Trackpads</div>
            <div class="gamepad-indicator">
                <div style="text-align:center;">
                    <div style="font-size:12px; color:#8f98a0; margin-bottom:6px;">Left Stick</div>
                    <div class="stick-box"><div id="dotLS" class="stick-dot"></div></div>
                </div>
                <div style="text-align:center;">
                    <div style="font-size:12px; color:#8f98a0; margin-bottom:6px;">Right Pad / Stick</div>
                    <div class="stick-box"><div id="dotRS" class="stick-dot"></div></div>
                </div>
            </div>
            <div style="margin-top:20px;">
                <span class="status-badge status-live">STREAM ACTIVE (SSE)</span>
                <div id="rawInfo" style="font-family:monospace; font-size:11px; color:#8f98a0;">Connecting...</div>
            </div>
        </div>
    </div>

    <script>
        const evtSource = new EventSource('/events');
        evtSource.onmessage = function(e) {
            const data = JSON.parse(e.data);
            
            // Buttons
            document.getElementById('btnA').className = data.btnA ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnB').className = data.btnB ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnX').className = data.btnX ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnY').className = data.btnY ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnLB').className = data.btnLB ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnRB').className = data.btnRB ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnL3').className = data.btnL3 ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnR3').className = data.btnR3 ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnSelect').className = data.btnSelect ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnStart').className = data.btnStart ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnGuide').className = data.btnGuide ? 'btn-badge active' : 'btn-badge';
            document.getElementById('btnDpad').className = (data.dpadX !== 0 || data.dpadY !== 0) ? 'btn-badge active' : 'btn-badge';

            // Triggers
            const ltPct = Math.round(data.leftTrigger * 100);
            const rtPct = Math.round(data.rightTrigger * 100);
            document.getElementById('barLT').style.width = ltPct + '%';
            document.getElementById('valLT').innerText = ltPct + '%';
            document.getElementById('barRT').style.width = rtPct + '%';
            document.getElementById('valRT').innerText = rtPct + '%';

            // Sticks
            const lsX = 48 + (data.leftStickX * 40);
            const lsY = 48 + (data.leftStickY * 40);
            document.getElementById('dotLS').style.left = lsX + 'px';
            document.getElementById('dotLS').style.top = lsY + 'px';

            const rsX = 48 + (data.rightStickX * 40);
            const rsY = 48 + (data.rightStickY * 40);
            document.getElementById('dotRS').style.left = rsX + 'px';
            document.getElementById('dotRS').style.top = rsY + 'px';

            document.getElementById('rawInfo').innerText = 'LX: ' + data.leftStickX.toFixed(2) + ' LY: ' + data.leftStickY.toFixed(2) + ' | RX: ' + data.rightStickX.toFixed(2) + ' RY: ' + data.rightStickY.toFixed(2);
        };
    </script>
</body>
</html>
    """.trimIndent()
}
