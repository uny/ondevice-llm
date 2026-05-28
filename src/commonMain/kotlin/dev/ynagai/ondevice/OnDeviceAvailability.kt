package dev.ynagai.ondevice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.lastOrNull

enum class OnDeviceStatus {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
}

expect suspend fun checkOnDeviceStatus(): OnDeviceStatus

/** Progress of a [downloadOnDeviceModel] request. */
sealed interface OnDeviceDownload {
    /**
     * The download is running. [fraction] is in `0.0..1.0` when the backend reports it,
     * or `null` when it only signals progress without a total (the caller can still show
     * an indeterminate indicator).
     */
    data class InProgress(val fraction: Float?) : OnDeviceDownload

    /** The model is now [OnDeviceStatus.AVAILABLE]. */
    data object Completed : OnDeviceDownload

    /** The download ended without reaching availability; [status] is the resulting state. */
    data class Failed(val status: OnDeviceStatus) : OnDeviceDownload
}

/**
 * Triggers a model download where the platform supports it, emitting progress until a
 * terminal [OnDeviceDownload.Completed] or [OnDeviceDownload.Failed].
 *
 * Android (ML Kit) reports real download progress. iOS manages model provisioning
 * transparently, so it emits only a single terminal frame reflecting current status.
 */
expect fun downloadOnDeviceModel(): Flow<OnDeviceDownload>

/** Collects [downloadOnDeviceModel] to completion; returns true once the model is available. */
suspend fun Flow<OnDeviceDownload>.awaitAvailable(): Boolean =
    lastOrNull() is OnDeviceDownload.Completed
