package io.xaio.ota.model

data class PendingConfirmation(
    val release: ReleaseRecord,
    val title: String,
    val message: String,
    val reason: String,
    val requiresCheckbox: Boolean = false,
    val checkboxLabel: String = "",
    val dismissible: Boolean = true,
)

data class OtaUiState(
    val deviceAddress: String = "",
    val selectedDeviceName: String? = null,
    val scannedDevices: List<ScannedBleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val selectedChannel: String = "stable",
    val deviceVersion: DeviceVersion? = null,
    val latestRelease: ReleaseRecord? = null,
    val releases: List<ReleaseRecord> = emptyList(),
    val busyMessage: String? = null,
    val downloadProgress: Int? = null,
    val flashProgress: Int? = null,
    val statusMessage: String = "Scan for a device, choose the correct one, and the app will read the current firmware.",
    val errorMessage: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val auditExportPath: String? = null,
)
