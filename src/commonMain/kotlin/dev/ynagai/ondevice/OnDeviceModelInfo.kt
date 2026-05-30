package dev.ynagai.ondevice

/** Static, platform-native model facts. Koog `LLModel` is built from this in the adapter. */
data class OnDeviceModelInfo(
    val id: String,
    val contextLength: Int,
    val maxOutputTokens: Int,
) {
    companion object {
        // Gemini Nano via ML Kit: ~4k input; ML Kit recommends keeping output ≤ 256.
        val GeminiNano = OnDeviceModelInfo("gemini-nano", contextLength = 4_096, maxOutputTokens = 256)

        // Apple Foundation Models (iOS 26+): 4,096 combined input+output.
        val AppleFoundation = OnDeviceModelInfo("apple-foundation", contextLength = 4_096, maxOutputTokens = 2_048)
    }
}

/**
 * The on-device model native to the current platform — Gemini Nano on Android,
 * Apple Foundation Models on iOS. Lets common code select the right model without
 * branching on platform.
 */
expect val OnDeviceModelInfo.Companion.platformDefault: OnDeviceModelInfo
