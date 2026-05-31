package dev.ynagai.ondevice

import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

actual suspend fun checkOnDeviceStatus(): OnDeviceStatus =
    capabilityClient.checkStatus().toOnDeviceStatus()

actual fun downloadOnDeviceModel(): Flow<OnDeviceDownload> = flow {
    // ML Kit reports download progress but no total to derive a fraction from, so emit
    // indeterminate InProgress frames and let checkStatus() decide the terminal frame.
    capabilityClient.download().collect { emit(OnDeviceDownload.InProgress(fraction = null)) }
    val status = capabilityClient.checkStatus().toOnDeviceStatus()
    emit(if (status == OnDeviceStatus.AVAILABLE) OnDeviceDownload.Completed else OnDeviceDownload.Failed(status))
}

private fun Int.toOnDeviceStatus(): OnDeviceStatus = when (this) {
    FeatureStatus.AVAILABLE -> OnDeviceStatus.AVAILABLE
    FeatureStatus.DOWNLOADABLE -> OnDeviceStatus.DOWNLOADABLE
    FeatureStatus.DOWNLOADING -> OnDeviceStatus.DOWNLOADING
    else -> OnDeviceStatus.UNAVAILABLE
}
