package dev.ynagai.ondevice

import kotlinx.cinterop.ExperimentalForeignApi
import swiftPMImport.dev.ynagai.ondevice.ondevice.llm.OnDeviceLlmBridge

@OptIn(ExperimentalForeignApi::class)
actual suspend fun checkOnDeviceStatus(): OnDeviceStatus =
    if (OnDeviceLlmBridge.isAvailable()) OnDeviceStatus.AVAILABLE else OnDeviceStatus.UNAVAILABLE

actual suspend fun downloadModel(): Boolean =
    // Apple manages model downloads transparently.
    checkOnDeviceStatus() == OnDeviceStatus.AVAILABLE
