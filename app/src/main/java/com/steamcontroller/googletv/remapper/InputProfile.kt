package com.steamcontroller.googletv.remapper

import kotlinx.serialization.Serializable

@Serializable
enum class TrackpadMode {
    RIGHT_STICK,
    MOUSE,
    CROSS_DPAD,
    DISABLED
}

@Serializable
enum class GamepadButton {
    NONE,
    BTN_A,
    BTN_B,
    BTN_X,
    BTN_Y,
    BTN_LB,
    BTN_RB,
    BTN_L3,
    BTN_R3,
    BTN_SELECT,
    BTN_START,
    BTN_GUIDE,
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT
}

@Serializable
data class TrackpadConfig(
    val mode: TrackpadMode = TrackpadMode.RIGHT_STICK,
    val sensitivity: Float = 1.0f,
    val deadzone: Float = 0.05f,
    val trackballFriction: Float = 0.85f, // 0.0 = instant stop, 0.95 = very slippery trackball
    val trackballEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
)

@Serializable
data class TriggerConfig(
    val softPullThreshold: Int = 15,
    val fullClickMapping: GamepadButton = GamepadButton.NONE
)

@Serializable
data class ButtonMappingConfig(
    val leftGrip: GamepadButton = GamepadButton.BTN_A,
    val rightGrip: GamepadButton = GamepadButton.BTN_X,
    val leftPadClick: GamepadButton = GamepadButton.BTN_L3,
    val rightPadClick: GamepadButton = GamepadButton.BTN_R3,
    val selectButton: GamepadButton = GamepadButton.BTN_SELECT,
    val startButton: GamepadButton = GamepadButton.BTN_START,
    val steamButton: GamepadButton = GamepadButton.BTN_GUIDE
)

@Serializable
data class InputProfile(
    val id: String = "default_gamepad",
    val name: String = "Standard Xbox Gamepad",
    val rightPadConfig: TrackpadConfig = TrackpadConfig(mode = TrackpadMode.RIGHT_STICK, sensitivity = 1.0f),
    val leftPadConfig: TrackpadConfig = TrackpadConfig(mode = TrackpadMode.CROSS_DPAD, sensitivity = 1.0f),
    val leftTriggerConfig: TriggerConfig = TriggerConfig(),
    val rightTriggerConfig: TriggerConfig = TriggerConfig(),
    val buttonMappings: ButtonMappingConfig = ButtonMappingConfig(),
    val gyroAimingEnabled: Boolean = false,
    val gyroSensitivity: Float = 1.0f
) {
    companion object {
        val DEFAULT = InputProfile()

        val FPS_AIMING = InputProfile(
            id = "fps_aiming",
            name = "FPS Aiming (High Precision)",
            rightPadConfig = TrackpadConfig(
                mode = TrackpadMode.RIGHT_STICK,
                sensitivity = 1.4f,
                trackballFriction = 0.92f,
                trackballEnabled = true
            ),
            buttonMappings = ButtonMappingConfig(
                leftGrip = GamepadButton.BTN_A,      // Jump
                rightGrip = GamepadButton.BTN_B      // Crouch/Slide
            ),
            gyroAimingEnabled = true,
            gyroSensitivity = 0.8f
        )

        val RETRO_DPAD = InputProfile(
            id = "retro_dpad",
            name = "Retro Arcade / D-Pad",
            leftPadConfig = TrackpadConfig(mode = TrackpadMode.CROSS_DPAD),
            buttonMappings = ButtonMappingConfig(
                leftGrip = GamepadButton.BTN_LB,
                rightGrip = GamepadButton.BTN_RB
            )
        )
    }
}
