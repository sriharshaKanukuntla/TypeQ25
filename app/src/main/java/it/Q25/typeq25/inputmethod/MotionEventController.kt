package it.srik.TypeQ25.inputmethod

import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import it.srik.TypeQ25.BuildConfig

class MotionEventController(
    private val motionEventTracker: MotionEventTracker = MotionEventTracker,
    private val logTag: String = "MotionEventController",
    private val debugLogsEnabled: Boolean = BuildConfig.DEBUG
) {

    fun handle(event: MotionEvent?): Boolean? {
        if (event == null) {
            return null
        }

        val source = event.source
        val device = event.device
        val isFromTrackpad = (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                (source and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD

        val isFromKeyboard = device != null &&
                ((source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
                        device.name?.contains("keyboard", ignoreCase = true) == true ||
                        device.name?.contains("titan", ignoreCase = true) == true)

        if (isFromTrackpad || isFromKeyboard) {
            if (debugLogsEnabled) {
                Log.d(
                    logTag,
                    "Motion event intercepted - Action: ${MotionEventTracker.getActionName(event.action)}, " +
                        "Source: ${MotionEventTracker.getSourceName(source)}, " +
                        "Device: ${device?.name}, " +
                        "X: ${event.x}, Y: ${event.y}, " +
                        "ScrollX: ${event.getAxisValue(MotionEvent.AXIS_HSCROLL)}, " +
                        "ScrollY: ${event.getAxisValue(MotionEvent.AXIS_VSCROLL)}"
                )
            }

            motionEventTracker.notifyMotionEvent(event)

            if (debugLogsEnabled) {
                when (event.action) {
                    MotionEvent.ACTION_SCROLL -> {
                        val scrollX = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                        val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                        Log.d(logTag, "Trackpad scroll detected - X: $scrollX, Y: $scrollY")
                    }

                    MotionEvent.ACTION_MOVE -> {
                        Log.d(logTag, "Trackpad move detected - X: ${event.x}, Y: ${event.y}")
                    }

                    MotionEvent.ACTION_DOWN -> {
                        Log.d(logTag, "Trackpad touch down detected - X: ${event.x}, Y: ${event.y}")
                    }

                    MotionEvent.ACTION_UP -> {
                        Log.d(logTag, "Trackpad touch up detected")
                    }
                }
            }

            return false
        }

        return null
    }
}
