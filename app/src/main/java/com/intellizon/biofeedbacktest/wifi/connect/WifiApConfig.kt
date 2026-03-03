package com.intellizon.biofeedbacktest.wifi.connect

data class WifiApStatus(
    val enabled: Boolean,
    val ssid: String?,
    val password: String?,
    val band: Int?,
    val hidden: Boolean?,
    val ipAddress: String? = null, // ★ 新增
    val gatewayIp: String? = null  // ★ 可选
)

data class WifiApConfig(
    val ssid: String,
    val password: String,
    val band: Int = 0,
    val hidden: Boolean = false,
    val ipAddress: String? = "192.168.234.1"   // ★ 默认固定 49.1
) {
    companion object {
        val DEFAULT = WifiApConfig(
            ssid = "Intellizon_AP",
            password = "12345678",
            band = 0,
            hidden = false,
            ipAddress = "192.168.234.1"         // ★ 固定
        )
    }
}