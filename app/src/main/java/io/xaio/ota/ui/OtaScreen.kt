package io.xaio.ota.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.xaio.ota.AppConfig
import io.xaio.ota.model.DeviceVersion
import io.xaio.ota.model.OtaUiState
import io.xaio.ota.model.ReleaseRecord
import io.xaio.ota.model.ScannedBleDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtaScreen(
    state: OtaUiState,
    onChannelSelected: (String) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (ScannedBleDevice) -> Unit,
    onReleaseClicked: (ReleaseRecord) -> Unit,
    onConfirmInstall: () -> Unit,
    onDismissDialog: () -> Unit,
    onExportAuditLog: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("XAIO OTA") },
                actions = {
                    TextButton(onClick = onExportAuditLog) {
                        Text("Export Log")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard(
                    title = "Release Source",
                    body = "The app compares the device version over BLE with GitHub Pages metadata from your dedicated firmware release repo.",
                )
            }

            item {
                InfoCard(
                    title = "Bluetooth Permissions",
                    body = "Grant Bluetooth and notification permissions before reading the device or starting DFU.",
                    footer = {
                        Button(onClick = { permissionLauncher.launch(requiredRuntimePermissions()) }) {
                            Text("Grant Permissions")
                        }
                    },
                )
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Nearby Devices", style = MaterialTheme.typography.titleMedium)
                        state.selectedDeviceName?.let { name ->
                            Text("Selected: $name")
                        }
                        if (state.deviceAddress.isNotBlank()) {
                            Text("Address: ${state.deviceAddress}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onStartScan) {
                                Text(if (state.isScanning) "Rescan" else "Scan Devices")
                            }
                            if (state.isScanning) {
                                TextButton(onClick = onStopScan) {
                                    Text("Stop")
                                }
                            }
                        }
                        if (state.scannedDevices.isEmpty()) {
                            Text(
                                if (state.isScanning) {
                                    "Looking for nearby BLE devices. Your XAIO board should appear within a few seconds."
                                } else {
                                    "Tap Scan Devices to discover nearby BLE boards and choose the correct XAIO device."
                                },
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.scannedDevices.forEach { device ->
                                    ScannedDeviceCard(
                                        device = device,
                                        isSelected = device.address == state.deviceAddress,
                                        onConnectClick = { onConnectDevice(device) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.deviceVersion?.let { device ->
                item {
                    DeviceCard(device)
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Status", style = MaterialTheme.typography.titleMedium)
                        Text(state.statusMessage)
                        state.busyMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        state.downloadProgress?.let {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
                                Text("Download: $it%")
                            }
                        }
                        state.flashProgress?.let {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
                                Text("Flash: $it%")
                            }
                        }
                        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        state.auditExportPath?.let { Text("Audit log exported: $it") }
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Channel", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppConfig.supportedChannels.forEach { channel ->
                                FilterChip(
                                    selected = state.selectedChannel == channel,
                                    onClick = { onChannelSelected(channel) },
                                    label = { Text(channel) },
                                )
                            }
                        }
                        state.latestRelease?.let { latest ->
                            Text("Latest ${state.selectedChannel}: ${latest.version}")
                        }
                    }
                }
            }

            item {
                Text("Published releases", style = MaterialTheme.typography.titleLarge)
            }

            if (state.releases.isEmpty()) {
                item {
                    InfoCard(
                        title = "No releases yet",
                        body = "No published firmware releases were found for the selected channel.",
                    )
                }
            } else {
                items(state.releases, key = { it.tag }) { release ->
                    ReleaseCard(
                        release = release,
                        currentDevice = state.deviceVersion,
                        onInstallClick = { onReleaseClicked(release) },
                        onNotesClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseNotesUrl))
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
    }

    state.pendingConfirmation?.let { pending ->
        var acknowledged by remember(pending.release.tag) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                if (pending.dismissible) {
                    onDismissDialog()
                }
            },
            title = { Text(pending.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(pending.message)
                    if (pending.requiresCheckbox) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = acknowledged,
                                onCheckedChange = { acknowledged = it },
                            )
                            Text(
                                pending.checkboxLabel,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmInstall,
                    enabled = !pending.requiresCheckbox || acknowledged,
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                if (pending.dismissible) {
                    TextButton(onClick = onDismissDialog) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    footer: (@Composable () -> Unit)? = null,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
            footer?.invoke()
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceVersion) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Device Version", style = MaterialTheme.typography.titleMedium)
            Text("Firmware: ${device.firmwareRev}")
            Text("Channel: ${device.channel}")
            Text("Hardware: ${device.hardwareRev}")
            Text("Version code: ${device.versionCode}")
            Text("Security epoch: ${device.securityEpoch}")
        }
    }
}

@Composable
private fun ReleaseCard(
    release: ReleaseRecord,
    currentDevice: DeviceVersion?,
    onInstallClick: () -> Unit,
    onNotesClick: () -> Unit,
) {
    val actionLabel = when {
        currentDevice == null -> "Install"
        release.versionCode > currentDevice.versionCode -> "Update"
        release.versionCode == currentDevice.versionCode -> "Reinstall"
        else -> "Downgrade"
    }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${release.version} (${release.channel})", style = MaterialTheme.typography.titleMedium)
            Text("Published: ${release.publishedAt}")
            Text(release.releaseNotesSummary.ifBlank { "No summary added yet." })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onInstallClick) {
                    Text(actionLabel)
                }
                TextButton(onClick = onNotesClick) {
                    Text("Release Notes")
                }
            }
        }
    }
}

@Composable
private fun ScannedDeviceCard(
    device: ScannedBleDevice,
    isSelected: Boolean,
    onConnectClick: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(device.name, style = MaterialTheme.typography.titleSmall)
            Text(device.address)
            Text("Signal: ${device.rssi} dBm")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConnectClick) {
                    Text(if (isSelected) "Reconnect" else "Connect")
                }
                if (isSelected) {
                    Text("Selected", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun requiredRuntimePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    else -> emptyArray()
}
