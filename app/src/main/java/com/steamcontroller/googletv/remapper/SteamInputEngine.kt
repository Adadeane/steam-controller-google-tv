package com.steamcontroller.googletv.remapper

import com.steamcontroller.googletv.driver.Buttons
import com.steamcontroller.googletv.driver.SteamControllerState
import kotlin.math.abs
import kotlin.math.sqrt

class SteamInputEngine(
    var profile: InputProfile = InputProfile.DEFAULT
) {

    // Trackball state for right pad
    private var lastRightPadX: Short = 0
    private var lastRightPadY: Short = 0
    private var wasRightPadTouched: Boolean = false
    private var rightStickVelX: Float = 0.0f
    private var rightStickVelY: Float = 0.0f
    private var currentRightStickX: Float = 0.0f
    private var currentRightStickY: Float = 0.0f

    // Deadzone application helper (radial deadzone)
    private fun applyDeadzone(rawX: Float, rawY: Float, deadzone: Float = 0.08f): Pair<Float, Float> {
        val magnitude = sqrt((rawX * rawX + rawY * rawY).toDouble()).toFloat()
        if (magnitude < deadzone) {
            return Pair(0.0f, 0.0f)
        }
        val scale = (magnitude - deadzone) / (1.0f - deadzone)
        val normX = (rawX / magnitude) * scale
        val normY = (rawY / magnitude) * scale
        return Pair(normX.coerceIn(-1.0f, 1.0f), normY.coerceIn(-1.0f, 1.0f))
    }

    // Process SC2026 SteamControllerState into a VirtualGamepadState
    fun process(state: SteamControllerState): VirtualGamepadState {
        // 1. Left Stick with radial deadzone
        val rawLeftX = (state.leftJoyX / 32767.0f).coerceIn(-1.0f, 1.0f)
        val rawLeftY = -(state.leftJoyY / 32767.0f).coerceIn(-1.0f, 1.0f) // Invert Y for standard Linux evdev
        val (leftStickX, leftStickY) = applyDeadzone(rawLeftX, rawLeftY, 0.08f)

        // 2. Right Stick / Trackpad
        val rawRightX = (state.rightJoyX / 32767.0f).coerceIn(-1.0f, 1.0f)
        val rawRightY = -(state.rightJoyY / 32767.0f).coerceIn(-1.0f, 1.0f)
        var (rightX, rightY) = applyDeadzone(rawRightX, rawRightY, 0.08f)

        // If physical right joystick is centered, use Right Trackpad stick emulation / trackball
        if (abs(rawRightX) < 0.1f && abs(rawRightY) < 0.1f) {
            updateRightPadStick(state)
            rightX = currentRightStickX
            rightY = currentRightStickY
        }

        // 3. D-Pad
        var dpadX = 0
        var dpadY = 0
        if (state.isButtonPressed(Buttons.DPAD_UP)) dpadY = -1
        if (state.isButtonPressed(Buttons.DPAD_DOWN)) dpadY = 1
        if (state.isButtonPressed(Buttons.DPAD_LEFT)) dpadX = -1
        if (state.isButtonPressed(Buttons.DPAD_RIGHT)) dpadX = 1

        // 4. Analog Triggers (0..32767 -> 0.0f..1.0f)
        val leftTrigNorm = (state.leftTrigger / 32767.0f).coerceIn(0.0f, 1.0f)
        val rightTrigNorm = (state.rightTrigger / 32767.0f).coerceIn(0.0f, 1.0f)

        // 5. Mechanical Buttons (Exclude capacitive grip touch flags)
        val activeButtons = mutableSetOf<GamepadButton>()
        if (state.isButtonPressed(Buttons.A)) activeButtons.add(GamepadButton.BTN_A)
        if (state.isButtonPressed(Buttons.B)) activeButtons.add(GamepadButton.BTN_B)
        if (state.isButtonPressed(Buttons.X)) activeButtons.add(GamepadButton.BTN_X)
        if (state.isButtonPressed(Buttons.Y)) activeButtons.add(GamepadButton.BTN_Y)
        if (state.isButtonPressed(Buttons.LB)) activeButtons.add(GamepadButton.BTN_LB)
        if (state.isButtonPressed(Buttons.RB)) activeButtons.add(GamepadButton.BTN_RB)
        if (state.isButtonPressed(Buttons.LS)) activeButtons.add(GamepadButton.BTN_L3)
        if (state.isButtonPressed(Buttons.RS)) activeButtons.add(GamepadButton.BTN_R3)
        if (state.isButtonPressed(Buttons.VIEW)) activeButtons.add(profile.buttonMappings.selectButton)
        if (state.isButtonPressed(Buttons.MENU)) activeButtons.add(profile.buttonMappings.startButton)
        if (state.isButtonPressed(Buttons.STEAM)) activeButtons.add(profile.buttonMappings.steamButton)

        // Back Grip Switches (L4/L5 -> Left Paddle, R4/R5 -> Right Paddle) — Mechanical only
        if (state.isButtonPressed(Buttons.L4) || state.isButtonPressed(Buttons.L5)) {
            activeButtons.add(profile.buttonMappings.leftGrip)
        }
        if (state.isButtonPressed(Buttons.R4) || state.isButtonPressed(Buttons.R5)) {
            activeButtons.add(profile.buttonMappings.rightGrip)
        }

        // Left Pad Mechanical Click
        if (state.isButtonPressed(Buttons.TP_LT_CLICK)) {
            activeButtons.add(profile.buttonMappings.leftPadClick)
        }

        return VirtualGamepadState(
            leftStickX = leftStickX,
            leftStickY = leftStickY,
            rightStickX = rightX,
            rightStickY = rightY,
            leftTrigger = leftTrigNorm,
            rightTrigger = rightTrigNorm,
            dpadX = dpadX,
            dpadY = dpadY,
            btnA = activeButtons.contains(GamepadButton.BTN_A),
            btnB = activeButtons.contains(GamepadButton.BTN_B),
            btnX = activeButtons.contains(GamepadButton.BTN_X),
            btnY = activeButtons.contains(GamepadButton.BTN_Y),
            btnLB = activeButtons.contains(GamepadButton.BTN_LB),
            btnRB = activeButtons.contains(GamepadButton.BTN_RB),
            btnL3 = activeButtons.contains(GamepadButton.BTN_L3),
            btnR3 = activeButtons.contains(GamepadButton.BTN_R3),
            btnSelect = activeButtons.contains(GamepadButton.BTN_SELECT),
            btnStart = activeButtons.contains(GamepadButton.BTN_START),
            btnGuide = activeButtons.contains(GamepadButton.BTN_GUIDE)
        )
    }

    private fun updateRightPadStick(state: SteamControllerState) {
        val config = profile.rightPadConfig
        val isTouched = state.isButtonPressed(Buttons.TP_RT) || state.rightPadContact > 0

        if (isTouched) {
            if (wasRightPadTouched) {
                val dx = (state.rightPadX - lastRightPadX).toFloat()
                val dy = (state.rightPadY - lastRightPadY).toFloat()

                rightStickVelX = (dx / 1200.0f) * config.sensitivity
                rightStickVelY = -(dy / 1200.0f) * config.sensitivity

                currentRightStickX = (currentRightStickX * 0.25f + rightStickVelX * 0.75f).coerceIn(-1.0f, 1.0f)
                currentRightStickY = (currentRightStickY * 0.25f + rightStickVelY * 0.75f).coerceIn(-1.0f, 1.0f)
            } else {
                rightStickVelX = 0.0f
                rightStickVelY = 0.0f
                currentRightStickX = 0.0f
                currentRightStickY = 0.0f
            }

            lastRightPadX = state.rightPadX
            lastRightPadY = state.rightPadY
            wasRightPadTouched = true
        } else {
            if (config.trackballEnabled && wasRightPadTouched) {
                currentRightStickX *= config.trackballFriction
                currentRightStickY *= config.trackballFriction

                if (abs(currentRightStickX) < 0.015f) currentRightStickX = 0.0f
                if (abs(currentRightStickY) < 0.015f) currentRightStickY = 0.0f
            } else {
                currentRightStickX = 0.0f
                currentRightStickY = 0.0f
            }
            wasRightPadTouched = false
        }
    }
}
