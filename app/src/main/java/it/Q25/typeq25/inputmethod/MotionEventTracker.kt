package it.srik.TypeQ25.inputmethod

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Global tracker for motion events (trackpad/touch-sensitive keyboard).
 * Allows input method service to communicate motion events to MainActivity.
 */
object MotionEventTracker {
    private var _motionEventState: MutableState<MotionEventInfo?>? = null
    val motionEventState: MutableState<MotionEventInfo?>?
        get() = _motionEventState
    
    data class MotionEventInfo(
        val action: Int,
        val actionName: String,
        val source: Int,
        val sourceName: String,
        val x: Float,
        val y: Float,
        val scrollX: Float,
        val scrollY: Float,
        val buttonState: Int,
        val pressure: Float,
        val size: Float,
        val deviceId: Int,
        val deviceName: String?,
        val timestamp: Long
    )
    
    fun registerState(state: MutableState<MotionEventInfo?>) {
        _motionEventState = state
    }
    
    fun unregisterState() {
        _motionEventState = null
    }
    
    fun notifyMotionEvent(event: MotionEvent) {
        val device = event.device
        val deviceName = device?.name
        
        val motionEventInfo = MotionEventInfo(
            action = event.action,
            actionName = getActionName(event.action),
            source = event.source,
            sourceName = getSourceName(event.source),
            x = event.x,
            y = event.y,
            scrollX = event.getAxisValue(MotionEvent.AXIS_HSCROLL),
            scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL),
            buttonState = event.buttonState,
            pressure = event.pressure,
            size = event.size,
            deviceId = event.deviceId,
            deviceName = deviceName,
            timestamp = event.eventTime
        )
        _motionEventState?.value = motionEventInfo
    }
    
    fun getActionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "ACTION_DOWN"
            MotionEvent.ACTION_UP -> "ACTION_UP"
            MotionEvent.ACTION_MOVE -> "ACTION_MOVE"
            MotionEvent.ACTION_CANCEL -> "ACTION_CANCEL"
            MotionEvent.ACTION_SCROLL -> "ACTION_SCROLL"
            MotionEvent.ACTION_HOVER_ENTER -> "ACTION_HOVER_ENTER"
            MotionEvent.ACTION_HOVER_EXIT -> "ACTION_HOVER_EXIT"
            MotionEvent.ACTION_HOVER_MOVE -> "ACTION_HOVER_MOVE"
            MotionEvent.ACTION_BUTTON_PRESS -> "ACTION_BUTTON_PRESS"
            MotionEvent.ACTION_BUTTON_RELEASE -> "ACTION_BUTTON_RELEASE"
            else -> "ACTION_$action"
        }
    }
    
    fun getSourceName(source: Int): String {
        val sources = mutableListOf<String>()
        if (source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
            sources.add("MOUSE")
        }
        if (source and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD) {
            sources.add("TOUCHPAD")
        }
        if (source and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) {
            sources.add("KEYBOARD")
        }
        if (source and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS) {
            sources.add("STYLUS")
        }
        if (source and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN) {
            sources.add("TOUCHSCREEN")
        }
        if (sources.isEmpty()) {
            return "UNKNOWN($source)"
        }
        return sources.joinToString("|")
    }
}

