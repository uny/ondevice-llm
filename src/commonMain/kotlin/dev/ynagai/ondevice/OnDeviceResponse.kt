package dev.ynagai.ondevice

/**
 * The result of a single-shot [OnDeviceGenerator.generate] call.
 *
 * Mirrors the streaming path's terminal [OnDeviceChunk.Done]: callers get both the
 * [text] and why generation stopped. [finishReason] lets Android callers detect a
 * [OnDeviceFinishReason.LENGTH] truncation at the output-token cap; iOS cannot detect
 * a cutoff and always reports [OnDeviceFinishReason.STOP] (see [OnDeviceChunk]).
 */
data class OnDeviceResponse(
    val text: String,
    val finishReason: OnDeviceFinishReason,
)
