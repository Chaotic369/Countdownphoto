package com.countdown.widgets

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(WidgetBridgePlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
