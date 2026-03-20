package io.xaio.ota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.xaio.ota.ui.OtaScreen
import io.xaio.ota.ui.OtaViewModel
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceListenerHelper

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<OtaViewModel>()

    private val dfuProgressListener = object : DfuProgressListenerAdapter() {
        override fun onDeviceConnecting(deviceAddress: String) {
            viewModel.onDfuStage("Connecting to bootloader")
        }

        override fun onDfuProcessStarting(deviceAddress: String) {
            viewModel.onDfuStage("Starting DFU")
        }

        override fun onEnablingDfuMode(deviceAddress: String) {
            viewModel.onDfuStage("Switching device to DFU mode")
        }

        override fun onProgressChanged(
            deviceAddress: String,
            percent: Int,
            speed: Float,
            avgSpeed: Float,
            currentPart: Int,
            partsTotal: Int,
        ) {
            viewModel.onDfuProgress(percent)
        }

        override fun onFirmwareValidating(deviceAddress: String) {
            viewModel.onDfuStage("Validating firmware")
        }

        override fun onDeviceDisconnecting(deviceAddress: String) {
            viewModel.onDfuStage("Disconnecting")
        }

        override fun onDfuCompleted(deviceAddress: String) {
            viewModel.onDfuCompleted()
        }

        override fun onDfuAborted(deviceAddress: String) {
            viewModel.onDfuAborted()
        }

        override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String) {
            viewModel.onDfuError(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            MaterialTheme {
                OtaScreen(
                    state = state.value,
                    onChannelSelected = viewModel::selectChannel,
                    onStartScan = viewModel::startDeviceScan,
                    onStopScan = viewModel::stopDeviceScan,
                    onConnectDevice = viewModel::connectToScannedDevice,
                    onReleaseClicked = viewModel::requestInstall,
                    onConfirmInstall = viewModel::confirmPendingInstall,
                    onDismissDialog = viewModel::dismissConfirmation,
                    onExportAuditLog = viewModel::exportAuditCsv,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DfuServiceListenerHelper.registerProgressListener(this, dfuProgressListener)
    }

    override fun onStop() {
        DfuServiceListenerHelper.unregisterProgressListener(this, dfuProgressListener)
        super.onStop()
    }
}
