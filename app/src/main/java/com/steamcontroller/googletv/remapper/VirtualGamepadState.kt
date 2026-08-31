package com.steamcontroller.googletv.remapper

/**
 * Standardized Gamepad State ready for OS uinput injection.
 * Coordinates are normalized: Sticks [-1.0f .. 1.0f], Triggers [0.0f .. 1.0f], Dpad [-1, 0, 1].
 */
data class VirtualGamepadState(
    val leftStickX: Float = 0.0f,
    val leftStickY: Float = 0.0f,
    val rightStickX: Float = 0.0f,
    val rightStickY: Float = 0.0f,
    val leftTrigger: Float = 0.0f,
    val rightTrigger: Float = 0.0f,
    val dpadX: Int = 0,
    val dpadY: Int = 0,
    val btnA: Boolean = false,
    val btnB: Boolean = false,
    val btnX: Boolean = false,
    val btnY: Boolean = false,
    val btnLB: Boolean = false,
    val btnRB: Boolean = false,
    val btnL3: Boolean = false,
    val btnR3: Boolean = false,
    val btnSelect: Boolean = false,
    val btnStart: Boolean = false,
    val btnGuide: Boolean = false
) {
    /**
     * Convert float [-1.0f .. 1.0f] to Linux signed 16-bit EV_ABS axis [-32767 .. 32767].
     */
    fun toLinuxAxis(value: Float): Int = (value.coerceIn(-1.0f, 1.0f) * 32767).toInt()

    /**
     * Convert float [0.0f .. 1.0f] to Linux unsigned trigger axis [0 .. 1023].
     */
    fun toLinuxTrigger(value: Float): Int = (value.coerceIn(0.0f, 1.0f) * 1023).toInt()
}
