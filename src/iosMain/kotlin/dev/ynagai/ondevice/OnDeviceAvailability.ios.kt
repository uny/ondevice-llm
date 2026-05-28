package dev.ynagai.ondevice

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import swiftPMImport.dev.ynagai.ondevice.ondevice.llm.OnDeviceLlmBridge

@OptIn(ExperimentalForeignApi::class)
actual suspend fun checkOnDeviceStatus(): OnDeviceStatus =
    if (OnDeviceLlmBridge.isAvailable()) OnDeviceStatus.AVAILABLE else OnDeviceStatus.UNAVAILABLE

// Apple provisions the model transparently, so there is no progress to report: emit a
// single terminal frame reflecting current availability.
actual fun downloadOnDeviceModel(): Flow<OnDeviceDownload> = flow {
    val status = checkOnDeviceStatus()
    emit(if (status == OnDeviceStatus.AVAILABLE) OnDeviceDownload.Completed else OnDeviceDownload.Failed(status))
}
