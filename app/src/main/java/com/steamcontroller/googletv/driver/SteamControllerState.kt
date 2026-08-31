package com.steamcontroller.googletv.driver

data class SteamControllerState(
    val buttons: Int = 0,

    val leftTrigger: Int = 0,    // bytes 6-7, 16-bit, range 0-32767
    val rightTrigger: Int = 0,   // bytes 8-9, 16-bit

    val leftJoyX: Short = 0,     // bytes 10-11
    val leftJoyY: Short = 0,     // bytes 12-13
    val rightJoyX: Short = 0,    // bytes 14-15
    val rightJoyY: Short = 0,    // bytes 16-17

    val leftPadX: Short = 0,     // bytes 18-19
    val leftPadY: Short = 0,     // bytes 20-21
    val leftPadContact: Int = 0, // bytes 22-23

    val rightPadX: Short = 0,    // bytes 24-25
    val rightPadY: Short = 0,    // bytes 26-27
    val rightPadContact: Int = 0,// bytes 28-29

    val quatW: Short = 0,        // bytes 32-33  IMU quaternion
    val quatX: Short = 0,        // bytes 34-35
    val quatY: Short = 0,        // bytes 36-37
    val quatZ: Short = 0,        // bytes 38-39
) {
    fun isButtonPressed(mask: Int) = (buttons and mask) != 0
}

object Buttons {
    // byte 2: Mechanical Face Buttons
    const val A            = 0x00000001  // Physical A button (Bottom / Confirm)
    const val B            = 0x00000002  // Physical B button (Right / Cancel)
    const val X            = 0x00000004  // Physical X button (Left)
    const val Y            = 0x00000008  // Physical Y button (Top)
    const val QUICK_ACCESS = 0x00000010
    const val RS           = 0x00000020  // right stick click
    const val MENU         = 0x00000040  // Start/Menu button
    const val R4           = 0x00000080  // Mechanical back paddle (Right Upper)

    // byte 3: DPad and Shoulders
    const val R5         = 0x00000100  // Mechanical back paddle (Right Lower)
    const val RB         = 0x00000200
    const val DPAD_DOWN  = 0x00000400
    const val DPAD_RIGHT = 0x00000800
    const val DPAD_LEFT  = 0x00001000
    const val DPAD_UP    = 0x00002000
    const val VIEW       = 0x00004000  // Select/View button
    const val LS         = 0x00008000  // left stick click

    // byte 4: System and Back Paddles
    const val STEAM        = 0x00010000  // Steam guide button
    const val L4           = 0x00020000  // Mechanical back paddle (Left Upper)
    const val L5           = 0x00040000  // Mechanical back paddle (Left Lower)
    const val LB           = 0x00080000
    const val RS_TOUCH     = 0x00100000  // Capacitive touch only (do not map to click)
    const val TP_RT        = 0x00200000  // Right trackpad touch
    const val LT_FULL      = 0x00400000  // LT full digital click
    const val RT_FULL      = 0x00800000  // RT full digital click

    // byte 5: Capacitive Touch Flags (Do not map to digital buttons)
    const val LS_TOUCH     = 0x01000000  // Capacitive touch on stick
    const val TP_LT        = 0x02000000  // Capacitive touch on left pad
    const val TP_LT_CLICK  = 0x04000000  // Mechanical left pad click
    const val GRIP_RT      = 0x10000000  // Capacitive palm touch (NOT A BUTTON)
    const val GRIP_LT      = 0x20000000  // Capacitive palm touch (NOT A BUTTON)
}
