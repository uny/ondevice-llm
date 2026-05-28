package dev.ynagai.ondevice

enum class OnDeviceStatus {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
}

expect suspend fun checkOnDeviceStatus(): OnDeviceStatus

/** Triggers a model download where the platform supports it; returns true once AVAILABLE. */
expect suspend fun downloadModel(): Boolean
