package com.example.sondenit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens to screen on/off broadcasts and forwards them via the supplied
 * callback. Registered programmatically by the recording service so that we
 * receive events even with the screen locked.
 */
class ScreenStateReceiver(private val onChange: (on: Boolean) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> onChange(true)
            Intent.ACTION_SCREEN_OFF -> onChange(false)
        }
    }
}
