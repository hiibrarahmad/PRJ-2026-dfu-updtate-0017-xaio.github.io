package io.xaio.ota.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.xaio.ota.AppConfig
import io.xaio.ota.ble.BleDeviceScanner
import io.xaio.ota.ble.BleDeviceVersionReader
import io.xaio.ota.dfu.OtaDfuService
import io.xaio.ota.model.AuditEntry
import io.xaio.ota.model.DeviceVersion
import io.xaio.ota.model.OtaUiState
import io.xaio.ota.model.PendingConfirmation
import io.xaio.ota.model.PolicyResult
import io.xaio.ota.model.ReleaseCatalogSnapshot
import io.xaio.ota.model.ReleaseRecord
import io.xaio.ota.model.ScannedBleDevice
import io.xaio.ota.policy.OtaPolicyEngine
import io.xaio.ota.storage.AuditLog
import io.xaio.ota.storage.ReleaseCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.dfu.DfuServiceInitiator
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OtaViewModel(application: Application) : AndroidViewModel(application) {

    private val bleScanner = BleDeviceScanner(application)
    private val bleReader = BleDeviceVersionReader(application)
    private val catalogRepository = ReleaseCatalogRepository(application)
    private val policyEngine = OtaPolicyEngine(application)
    private val auditLog = AuditLog(application)
    private val httpClient = OkHttpClient()

    private var snapshot: ReleaseCatalogSnapshot? = null
    private var activeInstall: ActiveInstall? = null

    private val _uiState = MutableStateFlow(OtaUiState())
    val uiState: StateFlow<OtaUiState> = _uiState

    fun startDeviceScan() {
        bleScanner.stop()
        val started = bleScanner.start(
            onDevicesChanged = { devices ->
                _uiState.update {
                    it.copy(
                        scannedDevices = devices,
                        isScanning = true,
                        errorMessage = null,
                        statusMessage = if (devices.isEmpty()) {
                            "Scanning for nearby BLE devices."
                        } else {
                            "Select the correct device from the list."
                        },
                    )
                }
            },
            onError = { message ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = message,
                        statusMessage = "Scan could not start. Check Bluetooth and permissions.",
                    )
                }
            },
        )
        if (started) {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scannedDevices = emptyList(),
                    errorMessage = null,
                    statusMessage = "Scanning for nearby BLE devices.",
                )
            }
        }
    }

    fun stopDeviceScan() {
        bleScanner.stop()
        _uiState.update {
            it.copy(
                isScanning = false,
                statusMessage = if (it.scannedDevices.isEmpty()) {
                    "No BLE devices found yet. You can scan again."
                } else {
                    "Scan stopped. Choose the correct device from the list."
                },
            )
        }
    }

    fun connectToScannedDevice(device: ScannedBleDevice) {
        bleScanner.stop()
        _uiState.update {
            it.copy(
                deviceAddress = device.address,
                selectedDeviceName = device.name,
                isScanning = false,
                errorMessage = null,
                statusMessage = "Connecting to ${device.name}.",
            )
        }
        readDeviceAndRefresh()
    }

    fun selectChannel(channel: String) {
        _uiState.update { it.copy(selectedChannel = channel, errorMessage = null) }
        refreshReleasePresentation()
    }

    fun readDeviceAndRefresh() {
        val address = uiState.value.deviceAddress.trim()
        if (address.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Scan and select a device first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyMessage = "Reading BLE version information",
                    downloadProgress = null,
                    flashProgress = null,
                    errorMessage = null,
                    auditExportPath = null,
                )
            }

            val device = bleReader.read(address).getOrElse { error ->
                _uiState.update {
                    it.copy(
                        busyMessage = null,
                        errorMessage = error.message ?: "Could not read the device over BLE.",
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    deviceVersion = device,
                    selectedChannel = device.channel.takeIf { channel -> channel in AppConfig.supportedChannels } ?: it.selectedChannel,
                    busyMessage = "Fetching release metadata",
                )
            }

            snapshot = catalogRepository.fetch().getOrElse { error ->
                _uiState.update {
                    it.copy(
                        busyMessage = null,
                        errorMessage = error.message ?: "Could not fetch release metadata.",
                    )
                }
                return@launch
            }

            refreshReleasePresentation()
        }
    }

    fun requestInstall(release: ReleaseRecord) {
        val device = uiState.value.deviceVersion
        if (device == null) {
            _uiState.update { it.copy(errorMessage = "Read the device first so the app can compare versions.") }
            return
        }

        val direction = compareDirection(device, release)
        val pending = when {
            release.forcedUpdate && release.versionCode >= device.versionCode -> PendingConfirmation(
                release = release,
                title = "Required Update",
                message = "This firmware update is marked as required. Install ${release.version} now to keep the device aligned with the published catalog.",
                reason = "forced_update",
                dismissible = false,
            )
            direction == "upgrade" -> PendingConfirmation(
                release = release,
                title = "Install Update",
                message = "Update the device from ${device.firmwareRev} to ${release.version}? ${release.releaseNotesSummary}",
                reason = "user_initiated",
            )
            direction == "reinstall" -> PendingConfirmation(
                release = release,
                title = "Reinstall Firmware",
                message = "Reinstall ${release.version} on the device? This action will be logged.",
                reason = "user_initiated",
            )
            else -> PendingConfirmation(
                release = release,
                title = "Downgrade Firmware",
                message = "You are about to install the older firmware ${release.version} over ${device.firmwareRev}. This action will be logged.",
                reason = "user_initiated",
                requiresCheckbox = release.channel == "stable",
                checkboxLabel = "I understand I am downgrading to an older stable firmware.",
            )
        }

        _uiState.update { it.copy(pendingConfirmation = pending, errorMessage = null) }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(pendingConfirmation = null) }
    }

    fun confirmPendingInstall() {
        val pending = uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null) }
        startInstall(pending.release, pending.reason)
    }

    fun exportAuditCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val output = File(getApplication<Application>().cacheDir, "ota-audit-$stamp.csv")
            auditLog.exportCsv(output)
            _uiState.update { it.copy(auditExportPath = output.absolutePath) }
        }
    }

    fun onDfuStage(message: String) {
        _uiState.update { it.copy(busyMessage = message, flashProgress = it.flashProgress ?: 0) }
    }

    fun onDfuProgress(percent: Int) {
        _uiState.update {
            it.copy(
                busyMessage = "Flashing firmware",
                flashProgress = percent,
            )
        }
    }

    fun onDfuCompleted() {
        activeInstall?.let { install ->
            auditLog.record(install.auditEntry(result = "completed", reason = install.reason))
        }
        activeInstall = null
        _uiState.update {
            it.copy(
                busyMessage = null,
                flashProgress = 100,
                statusMessage = "DFU completed successfully.",
                errorMessage = null,
            )
        }
    }

    fun onDfuAborted() {
        activeInstall?.let { install ->
            auditLog.record(install.auditEntry(result = "aborted", reason = install.reason))
        }
        activeInstall = null
        _uiState.update {
            it.copy(
                busyMessage = null,
                flashProgress = null,
                errorMessage = "DFU was aborted.",
            )
        }
    }

    fun onDfuError(message: String) {
        activeInstall?.let { install ->
            auditLog.record(install.auditEntry(result = "failed", reason = "${install.reason}: $message"))
        }
        activeInstall = null
        _uiState.update {
            it.copy(
                busyMessage = null,
                flashProgress = null,
                errorMessage = message,
            )
        }
    }

    override fun onCleared() {
        bleScanner.stop()
        super.onCleared()
    }

    private fun refreshReleasePresentation() {
        val localSnapshot = snapshot ?: run {
            _uiState.update { it.copy(busyMessage = null) }
            return
        }
        val device = uiState.value.deviceVersion
        val channel = uiState.value.selectedChannel
        val latest = localSnapshot.latestForChannel(channel)
        val releases = localSnapshot.historyForChannel(channel)
        val statusMessage = when {
            device == null -> "Read the device to compare installed and published versions."
            latest == null -> "No published firmware exists yet for $channel."
            latest.versionCode > device.versionCode -> "Update available: ${device.firmwareRev} -> ${latest.version}."
            latest.versionCode == device.versionCode -> "No update. ${device.firmwareRev} is already the latest $channel firmware."
            else -> "The device is newer than the latest published $channel firmware, or you selected an older channel."
        }

        _uiState.update {
            it.copy(
                latestRelease = latest,
                releases = releases,
                statusMessage = statusMessage,
                busyMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun startInstall(release: ReleaseRecord, reason: String) {
        val address = uiState.value.deviceAddress.trim()
        val device = uiState.value.deviceVersion
        if (address.isBlank() || device == null) {
            _uiState.update { it.copy(errorMessage = "Read the selected device before starting DFU.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyMessage = "Downloading firmware package",
                    downloadProgress = 0,
                    flashProgress = null,
                    errorMessage = null,
                )
            }

            val otaDir = File(getApplication<Application>().cacheDir, "ota").apply { mkdirs() }
            val zipFile = downloadFile(release.url, File(otaDir, "${release.tag}.zip"), true) { progress ->
                _uiState.update { state -> state.copy(downloadProgress = progress) }
            } ?: run {
                _uiState.update { it.copy(busyMessage = null, errorMessage = "Firmware download failed.") }
                return@launch
            }

            val sigFile = downloadFile(release.sigUrl, File(otaDir, "${release.tag}.zip.sig"), false) { }
                ?: run {
                    _uiState.update { it.copy(busyMessage = null, errorMessage = "Signature download failed.") }
                    return@launch
                }

            _uiState.update { it.copy(busyMessage = "Verifying package", downloadProgress = null) }
            val policy = policyEngine.evaluate(device, release, zipFile, sigFile)
            when (policy) {
                is PolicyResult.HardBlock -> {
                    auditLog.record(buildAuditEntry(device, release, address, "blocked", policy.reason))
                    _uiState.update {
                        it.copy(
                            busyMessage = null,
                            errorMessage = policy.reason,
                            statusMessage = "Install blocked by policy.",
                        )
                    }
                    return@launch
                }
                is PolicyResult.Allow,
                is PolicyResult.AlreadyInstalled,
                is PolicyResult.DowngradeWarning,
                is PolicyResult.ForcedUpdate -> Unit
            }

            activeInstall = ActiveInstall(
                device = device,
                release = release,
                deviceAddress = address,
                reason = reason,
            )
            auditLog.record(activeInstall!!.auditEntry(result = "started", reason = reason))

            startNordicDfu(zipFile, address)
            _uiState.update {
                it.copy(
                    busyMessage = "DFU started. Keep the phone near the device and follow the notification.",
                    downloadProgress = null,
                    flashProgress = 0,
                    statusMessage = "DFU in progress for ${release.version}.",
                )
            }
        }
    }

    private fun startNordicDfu(zipFile: File, deviceAddress: String) {
        val context = getApplication<Application>()
        DfuServiceInitiator(deviceAddress)
            .setZip(zipFile.absolutePath)
            .setKeepBond(false)
            .setDisableNotification(false)
            .setNumberOfRetries(1)
            .setPacketsReceiptNotificationsEnabled(true)
            .setPacketsReceiptNotificationsValue(10)
            .start(context, OtaDfuService::class.java)
    }

    private suspend fun downloadFile(
        url: String,
        destination: File,
        reportProgress: Boolean,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} while downloading $url")
                }
                val body = response.body ?: error("Missing response body while downloading $url")
                val total = body.contentLength()
                destination.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (reportProgress && total > 0L) {
                                onProgress((downloaded * 100 / total).toInt())
                            }
                        }
                    }
                }
            }
            destination
        }.getOrNull()
    }

    private fun compareDirection(device: DeviceVersion, release: ReleaseRecord): String = when {
        release.versionCode > device.versionCode -> "upgrade"
        release.versionCode == device.versionCode -> "reinstall"
        else -> "downgrade"
    }

    private fun buildAuditEntry(
        device: DeviceVersion,
        release: ReleaseRecord,
        deviceAddress: String,
        result: String,
        reason: String,
    ): AuditEntry = AuditEntry(
        timestamp = System.currentTimeMillis(),
        deviceId = deviceAddress,
        fromVersion = device.firmwareRev,
        toVersion = release.version,
        fromChannel = device.channel,
        toChannel = release.channel,
        direction = compareDirection(device, release),
        epochFrom = device.securityEpoch,
        epochTo = release.securityEpoch,
        result = result,
        reason = reason,
    )

    private data class ActiveInstall(
        val device: DeviceVersion,
        val release: ReleaseRecord,
        val deviceAddress: String,
        val reason: String,
    ) {
        fun auditEntry(result: String, reason: String): AuditEntry = AuditEntry(
            timestamp = System.currentTimeMillis(),
            deviceId = deviceAddress,
            fromVersion = device.firmwareRev,
            toVersion = release.version,
            fromChannel = device.channel,
            toChannel = release.channel,
            direction = when {
                release.versionCode > device.versionCode -> "upgrade"
                release.versionCode == device.versionCode -> "reinstall"
                else -> "downgrade"
            },
            epochFrom = device.securityEpoch,
            epochTo = release.securityEpoch,
            result = result,
            reason = reason,
        )
    }
}
