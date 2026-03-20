package io.xaio.ota.model

data class ScannedBleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)

