package com.steamcontroller.googletv

import com.steamcontroller.googletv.driver.SteamControllerConstants
import com.steamcontroller.googletv.driver.SteamControllerPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SteamControllerPacketTest {

    @Test
    fun testParseButtonsAndTriggers() {
        val bytes = ByteArray(28)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Set A button (bit 7 = 0x80) and Left Grip (bit 15 = 0x8000)
        val buttons = SteamControllerConstants.BTN_MASK_A or SteamControllerConstants.BTN_MASK_GRIP_LEFT
        bytes[0] = (buttons and 0xFF).toByte()
        bytes[1] = ((buttons shr 8) and 0xFF).toByte()
        bytes[2] = ((buttons shr 16) and 0xFF).toByte()

        // Left trigger = 200, Right trigger = 255
        bytes[3] = 200.toByte()
        bytes[4] = 255.toByte()

        // Stick X = 16000, Stick Y = -16000
        buffer.putShort(5, 16000.toShort())
        buffer.putShort(7, (-16000).toShort())

        val packet = SteamControllerPacket.parse(bytes)
        assertNotNull(packet)

        assertTrue(packet!!.isA)
        assertFalse(packet.isB)
        assertTrue(packet.isLeftGrip)
        assertFalse(packet.isRightGrip)

        assertEquals(200, packet.leftTrigger)
        assertEquals(255, packet.rightTrigger)
        assertEquals(16000.toShort(), packet.stickX)
        assertEquals((-16000).toShort(), packet.stickY)
    }

    @Test
    fun testParseTrackpadTouchAndCoordinates() {
        val bytes = ByteArray(28)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Set Right Pad Touch bit
        val buttons = SteamControllerConstants.BTN_MASK_RIGHT_PAD_TOUCH
        bytes[0] = (buttons and 0xFF).toByte()
        bytes[1] = ((buttons shr 8) and 0xFF).toByte()
        bytes[2] = ((buttons shr 16) and 0xFF).toByte()

        // Right Pad X = 12500, Y = -8000
        buffer.putShort(9, 12500.toShort())
        buffer.putShort(11, (-8000).toShort())

        val packet = SteamControllerPacket.parse(bytes)
        assertNotNull(packet)

        assertTrue(packet!!.isRightPadTouched)
        assertFalse(packet.isLeftPadTouched)
        assertEquals(12500.toShort(), packet.rightPadX)
        assertEquals((-8000).toShort(), packet.rightPadY)
    }
}
