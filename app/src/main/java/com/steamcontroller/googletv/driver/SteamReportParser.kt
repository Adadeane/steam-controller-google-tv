package com.steamcontroller.googletv.driver

import android.util.Log

data class BatteryStatus(val percent: Int, val chargeState: Int)

object SteamReportParser {

    private const val TAG = "SteamReportParser"
    private const val EXPECTED_REPORT_ID = 0x45
    private const val MIN_REPORT_LEN = 6
    private const val REPORT_BATTERY_STATUS = 0x43

    fun parseBatteryStatus(report: ByteArray): BatteryStatus? {
        if (report.size < 2) return null
        if ((report[0].toInt() and 0xFF) != REPORT_BATTERY_STATUS) return null
        return BatteryStatus(percent = report[1].toInt() and 0xFF, chargeState = 0)
    }

    fun parse(report: ByteArray): SteamControllerState? {
        if (report.size < MIN_REPORT_LEN) return null
        return try {
            doParse(report)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing report: ${e.message}")
            null
        }
    }

    fun parseRaw(report: ByteArray): SteamControllerState {
        if (report.size < MIN_REPORT_LEN) return SteamControllerState()
        return try {
            doParse(report)
        } catch (e: Exception) {
            SteamControllerState()
        }
    }

    private fun doParse(report: ByteArray): SteamControllerState {
        val b0 = if (2 < report.size) report[2].toInt() and 0xFF else 0
        val b1 = if (3 < report.size) report[3].toInt() and 0xFF else 0
        val b2 = if (4 < report.size) report[4].toInt() and 0xFF else 0
        val b3 = if (5 < report.size) report[5].toInt() and 0xFF else 0
        val buttons = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)

        return SteamControllerState(
            buttons         = buttons,
            leftTrigger     = readUInt16LE(report, 6),
            rightTrigger    = readUInt16LE(report, 8),
            leftJoyX        = readInt16LE(report, 10),
            leftJoyY        = readInt16LE(report, 12),
            rightJoyX       = readInt16LE(report, 14),
            rightJoyY       = readInt16LE(report, 16),
            leftPadX        = readInt16LE(report, 18),
            leftPadY        = readInt16LE(report, 20),
            leftPadContact  = readUInt16LE(report, 22),
            rightPadX       = readInt16LE(report, 24),
            rightPadY       = readInt16LE(report, 26),
            rightPadContact = readUInt16LE(report, 28),
            quatW           = readInt16LE(report, 32),
            quatX           = readInt16LE(report, 34),
            quatY           = readInt16LE(report, 36),
            quatZ           = readInt16LE(report, 38),
        )
    }

    private fun readInt16LE(buf: ByteArray, offset: Int): Short {
        if (offset + 1 >= buf.size) return 0
        val lo = buf[offset].toInt() and 0xFF
        val hi = buf[offset + 1].toInt() and 0xFF
        return ((hi shl 8) or lo).toShort()
    }

    private fun readUInt16LE(buf: ByteArray, offset: Int): Int {
        if (offset + 1 >= buf.size) return 0
        val lo = buf[offset].toInt() and 0xFF
        val hi = buf[offset + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }
}
