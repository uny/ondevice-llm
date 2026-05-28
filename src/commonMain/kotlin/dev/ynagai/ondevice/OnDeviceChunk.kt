package dev.ynagai.ondevice

/** A streaming chunk. The stream emits zero or more [Delta]s, then exactly one [Done]. */
sealed interface OnDeviceChunk {
    /** Incremental new text. Callers concatenate these; never a cumulative snapshot. */
    data class Delta(val text: String) : OnDeviceChunk

    /** Terminal frame carrying why generation stopped. */
    data class Done(val finishReason: OnDeviceFinishReason) : OnDeviceChunk
}

/**
 * Normalized finish reason.
 * - [STOP]: natural end.
 * - [LENGTH]: hit the output-token cap (Android can detect this; iOS cannot and
 *   always reports [STOP]).
 * - [OTHER]: backend-specific termination.
 */
enum class OnDeviceFinishReason { STOP, LENGTH, OTHER }
