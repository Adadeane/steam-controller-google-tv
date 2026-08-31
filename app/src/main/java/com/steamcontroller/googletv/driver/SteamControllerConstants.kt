package com.steamcontroller.googletv.driver

import java.util.UUID

object SteamControllerConstants {

    // Valve Steam Controller BLE GATT Service & Characteristics
    val VALVE_SERVICE_UUID: UUID = UUID.fromString("100F6C32-1735-4313-B402-38567131E5F3")
    val INPUT_REPORT_CHAR_UUID: UUID = UUID.fromString("100F6C33-1735-4313-B402-38567131E5F3")
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("100F6C34-1735-4313-B402-38567131E5F3")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Standard HID Service (Fallbacks)
    val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")

    // Valve Custom Protocol Report Types
    const val REPORT_TYPE_INPUT: Byte = 0x01
    const val REPORT_TYPE_EXTENDED_INPUT: Byte = 0x04
    const val REPORT_TYPE_BATTERY: Byte = 0x0B

    // Command Header & Payloads
    const val CMD_HEADER_PREFIX: Byte = 0xC0.toByte()

    // Disable Lizard Mode & Enable Raw Gamepad / IMU Events
    // 0x87 is the SET_SETTINGS command in Valve Steam Controller protocol
    val CMD_DISABLE_LIZARD_MODE = byteArrayOf(
        0xC0.toByte(), 0x87.toByte(), 0x03.toByte(), 0x08.toByte(), 0x07.toByte(), 0x00.toByte()
    )

    // Enable Controller Raw Input Stream (100Hz+)
    val CMD_ENABLE_RAW_INPUT = byteArrayOf(
        0xC0.toByte(), 0x87.toByte(), 0x15.toByte(), 0x32.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
    )

    // Request Controller Info & Firmware Version
    val CMD_GET_INFO = byteArrayOf(0xC0.toByte(), 0x83.toByte())

    // Button Bitmasks (32-bit integer or byte offsets in Valve report)
    // Byte 8, 9, 10 in standard 60-byte BLE input report
    const val BTN_MASK_R2_CLICK       = 1 shl 0
    const val BTN_MASK_L2_CLICK       = 1 shl 1
    const val BTN_MASK_R1             = 1 shl 2
    const val BTN_MASK_L1             = 1 shl 3
    const val BTN_MASK_Y              = 1 shl 4
    const val BTN_MASK_B              = 1 shl 5
    const val BTN_MASK_X              = 1 shl 6
    const val BTN_MASK_A              = 1 shl 7

    const val BTN_MASK_DPAD_UP        = 1 shl 8
    const val BTN_MASK_DPAD_RIGHT     = 1 shl 9
    const val BTN_MASK_DPAD_LEFT      = 1 shl 10
    const val BTN_MASK_DPAD_DOWN      = 1 shl 11
    const val BTN_MASK_SELECT         = 1 shl 12 // Back (<)
    const val BTN_MASK_STEAM          = 1 shl 13 // Guide Logo
    const val BTN_MASK_START          = 1 shl 14 // Forward (>)
    const val BTN_MASK_GRIP_LEFT      = 1 shl 15 // LG paddle

    const val BTN_MASK_GRIP_RIGHT     = 1 shl 16 // RG paddle
    const val BTN_MASK_LEFT_PAD_TOUCH = 1 shl 17
    const val BTN_MASK_LEFT_PAD_CLICK = 1 shl 18
    const val BTN_MASK_RIGHT_PAD_TOUCH= 1 shl 19
    const val BTN_MASK_RIGHT_PAD_CLICK= 1 shl 20
    const val BTN_MASK_STICK_CLICK    = 1 shl 21 // L3
}
