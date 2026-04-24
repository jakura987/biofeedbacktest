package com.intellizon.biofeedbacktest.wifi.connect

// base module
interface TcpServerController {
    fun ensureStarted(port: Int = 8883)
    fun ensureStopped()
}