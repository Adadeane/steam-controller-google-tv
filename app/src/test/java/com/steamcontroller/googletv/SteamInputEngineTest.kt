package com.steamcontroller.googletv

import com.steamcontroller.googletv.driver.SteamControllerConstants
import com.steamcontroller.googletv.driver.SteamControllerPacket
import com.steamcontroller.googletv.remapper.ButtonMappingConfig
import com.steamcontroller.googletv.remapper.GamepadButton
import com.steamcontroller.googletv.remapper.InputProfile
import com.steamcontroller.googletv.remapper.SteamInputEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamInputEngineTest {

    @Test
    fun testAnalogStickMapping() {
        val engine = SteamInputEngine(InputProfile.DEFAULT)

        val packet = SteamControllerPacket(
            stickX = 32767,
            stickY = 32767,
            leftTrigger = 128,
            rightTrigger = 255
        )

        val state = engine.process(packet)

        assertEquals(1.0f, state.leftStickX, 0.01f)
        assertEquals(-1.0f, state.leftStickY, 0.01f) // Inverted for Linux evdev ABS_Y
        assertEquals(0.5f, state.leftTrigger, 0.02f)
        assertEquals(1.0f, state.rightTrigger, 0.01f)
    }

    @Test
    fun testGripPaddleRemapping() {
        val customProfile = InputProfile(
            buttonMappings = ButtonMappingConfig(
                leftGrip = GamepadButton.BTN_LB,
                rightGrip = GamepadButton.BTN_RB
            )
        )
        val engine = SteamInputEngine(customProfile)

        // Packet with Left Grip pressed
        val packet = SteamControllerPacket(
            buttonBitmask = SteamControllerConstants.BTN_MASK_GRIP_LEFT
        )

        val state = engine.process(packet)

        assertTrue("Left grip should map to LB", state.btnLB)
        assertFalse(state.btnRB)
        assertFalse(state.btnA)
    }

    @Test
    fun testRightPadTrackballMovement() {
        val engine = SteamInputEngine(InputProfile.DEFAULT)

        // 1st touch frame
        val p1 = SteamControllerPacket(
            buttonBitmask = SteamControllerConstants.BTN_MASK_RIGHT_PAD_TOUCH,
            rightPadX = 0,
            rightPadY = 0,
            isRightPadTouched = true
        )
        engine.process(p1)

        // 2nd frame moving right and up
        val p2 = SteamControllerPacket(
            buttonBitmask = SteamControllerConstants.BTN_MASK_RIGHT_PAD_TOUCH,
            rightPadX = 1500,
            rightPadY = 1500,
            isRightPadTouched = true
        )
        val state2 = engine.process(p2)

        assertTrue("Right stick X should deflect positive", state2.rightStickX > 0.0f)
        assertTrue("Right stick Y should deflect negative (inverted Y)", state2.rightStickY < 0.0f)

        // 3rd frame finger lifted (testing trackball friction decay)
        val p3 = SteamControllerPacket(
            buttonBitmask = 0,
            rightPadX = 1500,
            rightPadY = 1500,
            isRightPadTouched = false
        )
        val state3 = engine.process(p3)

        assertTrue("Trackball should retain decaying positive momentum on X", state3.rightStickX > 0.0f)
        assertTrue("Momentum should have decayed compared to touch frame", state3.rightStickX < state2.rightStickX)
    }
}
