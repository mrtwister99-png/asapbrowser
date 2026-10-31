package com.example.actimelteambrowser.core.vpn

object VpnManager {
    var isVpnOn = false

    fun toggle() {
        isVpnOn = !isVpnOn
        // TODO: spustit ActimelVpnService
    }
}
