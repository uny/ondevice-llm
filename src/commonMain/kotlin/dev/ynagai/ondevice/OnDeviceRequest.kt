package dev.ynagai.ondevice

/**
 * A single-shot, framework-agnostic generation request.
 *
 * [systemInstruction] is delivered to the platform through its native channel when
 * one exists (Foundation Models `Instructions`); on platforms without a dedicated
 * channel (ML Kit) it is prepended to the prompt body by the actual implementation.
 * Callers should NOT inline system text into [prompt] themselves.
 *
 * A null [temperature] / [maxOutputTokens] / [topK] / [seed] means "use the
 * platform default". [topK] caps sampling to the K highest-probability tokens;
 * [seed] fixes the RNG for reproducible output where the platform supports it.
 */
data class OnDeviceRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val topK: Int? = null,
    val seed: Int? = null,
)
