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
 * [seed] fixes the RNG for reproducible output. A seed applies independently of
 * [topK] on both platforms: where the platform only seeds within an explicit
 * sampling mode (iOS Foundation Models), a seed-only request falls back to
 * full-distribution sampling so the seed still takes effect.
 *
 * [topK] must be positive and [seed] non-negative when set; a non-positive [topK]
 * is not a valid sampling width and a negative [seed] collides with the iOS
 * "unset" sentinel, so both are rejected here rather than diverging per platform.
 */
data class OnDeviceRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val topK: Int? = null,
    val seed: Int? = null,
) {
    init {
        require(topK == null || topK > 0) { "topK must be positive when set, was $topK" }
        require(seed == null || seed >= 0) { "seed must be non-negative when set, was $seed" }
    }
}
