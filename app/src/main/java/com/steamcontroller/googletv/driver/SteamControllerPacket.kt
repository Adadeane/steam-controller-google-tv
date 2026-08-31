package com.steamcontroller.googletv.driver

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parsed state packet from the Steam Controller over BLE.
 */
data class SteamControllerPacket(
    val sequenceNumber: Long = 0,
    val buttonBitmask: Int = 0,
    val leftTrigger: Int = 0,       // 0..255
    val rightTrigger: Int = 0,      // 0..255
    val stickX: Short = 0,          // -32768..32767
    val stickY: Short = 0,          // -32768..32767
    val leftPadX: Short = 0,        // -32768..32767
    val leftPadY: Short = 0,        // -32768..32767
    val rightPadX: Short = 0,       // -32768..32767
    val rightPadY: Short = 0,       // -32768..32767
    val isLeftPadTouched: Boolean = false,
    val isRightPadTouched: Boolean = false,
    val gyroPitch: Short = 0,
    val gyroRoll: Short = 0,
    val gyroYaw: Short = 0,
    val accelX: Short = 0,
    val accelY: Short = 0,
    val accelZ: Short = 0,
    val batteryMv: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val isA: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_A) != 0
    val isB: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_B) != 0
    val isX: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_X) != 0
    val isY: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_Y) != 0
    val isL1: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_L1) != 0
    val isR1: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_R1) != 0
    val isL2Click: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_L2_CLICK) != 0
    val isR2Click: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_R2_CLICK) != 0
    val isSelect: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_SELECT) != 0
    val isStart: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_START) != 0
    val isSteam: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_STEAM) != 0
    val isLeftGrip: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_GRIP_LEFT) != 0
    val isRightGrip: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_GRIP_RIGHT) != 0
    val isStickClick: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_STICK_CLICK) != 0
    val isLeftPadClick: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_LEFT_PAD_CLICK) != 0
    val isRightPadClick: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_RIGHT_PAD_CLICK) != 0

    val isDpadUp: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_DPAD_UP) != 0
    val isDpadDown: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_DPAD_DOWN) != 0
    val isDpadLeft: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_DPAD_LEFT) != 0
    val isDpadRight: Boolean get() = (buttonBitmask and SteamControllerConstants.BTN_MASK_DPAD_RIGHT) != 0

    companion object {
        /**
         * Parse raw byte payload from Valve's Input characteristic.
         * The Steam Controller BLE reports are typically 20-byte or 60-byte structures.
         */
        fun parse(data: ByteArray): SteamControllerPacket? {
            if (data.isEmpty()) return null

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // When prefixed with report type header (0x01 or 0x04)
            val header = if (data.size > 20) buffer.get() else 0
            val status = if (data.size > 20) buffer.get() else 0

            var buttons = 0
            var lTrig = 0
            var rTrig = 0
            var stickX: Short = 0
            var stickY: Short = 0
            var lPadX: Short = 0
            var lPadY: Short = 0
            var rPadX: Short = 0
            var rPadY: Short = 0
            var lPadTouched = false
            var rPadTouched = false
            var gPitch: Short = 0
            var gRoll: Short = 0
            var gYaw: Short = 0
            var aX: Short = 0
            var aY: Short = 0
            var aZ: Short = 0

            try {
                // If standard 20-byte condensed BLE report
                if (data.size >= 19) {
                    val b0 = data[0].toInt() and 0xFF
                    val b1 = data[1].toInt() and 0xFF
                    val b2 = data[2].toInt() and 0xFF

                    buttons = b0 or (b1 shl 8) or (b2 shl 16)

                    lTrig = data[3].toInt() and 0xFF
                    rTrig = data[4].toInt() and 0xFF

                    stickX = buffer.getShort(5)
                    stickY = buffer.getShort(7)

                    rPadX = buffer.getShort(9)
                    rPadY = buffer.getShort(11)

                    lPadX = buffer.getShort(13)
                    lPadY = buffer.getShort(15)

                    lPadTouched = (buttons and SteamControllerConstants.BTN_MASK_LEFT_PAD_TOUCH) != 0
                    rPadTouched = (buttons and SteamControllerConstants.BTN_MASK_RIGHT_PAD_TOUCH) != 0

                    if (data.size >= 28) {
                        gPitch = buffer.getShort(17)
                        gRoll = buffer.getShort(19)
                        gYaw = buffer.getShort(21)
                        aX = buffer.getShort(23)
                        aY = buffer.getShort(25)
                        aZ = buffer.getShort(27)
                    }
                }
            } catch (e: Exception) {
                // Fallback / partial packet handling
                return null
            }

            return SteamControllerPacket(
                buttonBitmask = buttons,
                leftTrigger = lTrig,
                rightTrigger = rTrig,
                stickX = stickX,
                stickY = stickY,
                leftPadX = lPadX,
                leftPadY = lPadY,
                rightPadX = rPadX,
                rightPadY = rPadY,
                isLeftPadTouched = lPadTouched,
                isRightPadTouched = rPadTouched,
                gyroPitch = gPitch,
                gyroRoll = gRoll,
                gyroYaw = gYaw,
                accelX = aX,
                accelY = aY,
                accelZ = aZ
            )
        }
    }
}
