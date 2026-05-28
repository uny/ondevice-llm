package dev.ynagai.ondevice

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation

actual suspend fun checkOnDeviceStatus(): OnDeviceStatus {
    val client = Generation.getClient()
    return when (client.checkStatus()) {
        FeatureStatus.AVAILABLE -> OnDeviceStatus.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> OnDeviceStatus.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> OnDeviceStatus.DOWNLOADING
        else -> OnDeviceStatus.UNAVAILABLE
    }
}

actual suspend fun downloadModel(): Boolean {
    val client = Generation.getClient()
    client.download().collect { /* consume progress */ }
    return client.checkStatus() == FeatureStatus.AVAILABLE
}
