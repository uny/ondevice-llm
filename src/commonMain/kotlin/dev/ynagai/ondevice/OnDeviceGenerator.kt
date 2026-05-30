package dev.ynagai.ondevice

import kotlinx.coroutines.flow.Flow

/**
 * Platform-native on-device text generation. Backed by Gemini Nano (Android) or
 * Apple Foundation Models (iOS). Obtain an instance via [createOnDeviceGenerator].
 *
 * Implementations are responsible for hiding all platform quirks:
 * - iOS serializes calls (single Foundation Models session, one generation at a time).
 * - iOS converts cumulative snapshots into incremental [OnDeviceChunk.Delta]s.
 * - Android maps ML Kit finish reasons; iOS always reports [OnDeviceFinishReason.STOP].
 *
 * Not for tool calling, moderation, or structured output — those are out of scope.
 *
 * Error contract (uniform across platforms):
 * - An on-device inference failure is reported as [OnDeviceInferenceException] (single-shot:
 *   thrown from [generate]; streaming: thrown from the [generateStream] flow).
 * - [close] is terminal. Calling [generate]/[generateStream] after [close] is a caller
 *   lifecycle error and throws [IllegalStateException] — it is never wrapped in
 *   [OnDeviceInferenceException], which is reserved for genuine model/inference failures.
 */
interface OnDeviceGenerator {
    suspend fun generate(request: OnDeviceRequest): OnDeviceResponse
    fun generateStream(request: OnDeviceRequest): Flow<OnDeviceChunk>
    fun close()
}

expect fun createOnDeviceGenerator(): OnDeviceGenerator
