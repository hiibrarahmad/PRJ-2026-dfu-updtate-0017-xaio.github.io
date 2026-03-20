package io.xaio.ota.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import io.xaio.ota.AppConfig
import io.xaio.ota.model.ScannedBleDevice

class BleDeviceScanner(private val context: Context) {

    private val discovered = linkedMapOf<String, ScannedBleDevice>()
    private var activeCallback: ScanCallback? = null

    fun start(
        onDevicesChanged: (List<ScannedBleDevice>) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        if (activeCallback != null) {
            onDevicesChanged(sortedDevices())
            return true
        }

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            onError("Bluetooth adapter unavailable.")
            return false
        }
        if (!adapter.isEnabled) {
            onError("Bluetooth is turned off.")
            return false
        }
        if (!hasScanPermission()) {
            onError("Bluetooth scan permission is not granted.")
            return false
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onError("Bluetooth LE scanner unavailable.")
            return false
        }

        discovered.clear()
        onDevicesChanged(emptyList())

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                upsert(result)
                onDevicesChanged(sortedDevices())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::upsert)
                onDevicesChanged(sortedDevices())
            }

            override fun onScanFailed(errorCode: Int) {
                activeCallback = null
                onError("BLE scan failed with error code $errorCode.")
            }
        }

        activeCallback = callback
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, callback)
        return true
    }

    fun stop() {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        val callback = activeCallback ?: return
        if (hasScanPermission()) {
            scanner?.stopScan(callback)
        }
        activeCallback = null
    }

    private fun upsert(result: ScanResult) {
        val address = result.device.address ?: return
        val scanRecord = result.scanRecord
        val advertisesVersionService = scanRecord?.serviceUuids
            ?.contains(ParcelUuid(AppConfig.versionServiceUuid)) == true
        val advertisedName = scanRecord?.deviceName
            ?: runCatching { result.device.name }.getOrNull()

        if (advertisedName == null && !advertisesVersionService) {
            return
        }

        val deviceName = advertisedName
            ?: if (advertisesVersionService) "Unnamed XAIO device" else "Unnamed BLE device"
        discovered[address] = ScannedBleDevice(
            address = address,
            name = deviceName,
            rssi = result.rssi,
        )
    }

    private fun sortedDevices(): List<ScannedBleDevice> = discovered.values
        .sortedWith(
            compareByDescending<ScannedBleDevice> { scanPriority(it) }
                .thenByDescending { it.rssi },
        )

    private fun scanPriority(device: ScannedBleDevice): Int {
        val name = device.name.uppercase()
        return when {
            name.contains("XAIO") -> 3
            name.contains("XIAO") -> 2
            !name.startsWith("UNNAMED") -> 1
            else -> 0
        }
    }

    private fun hasScanPermission(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }
        else -> true
    }
}
