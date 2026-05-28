# ondevice-llm

Framework-agnostic, on-device LLM inference for Kotlin Multiplatform — Gemini Nano (Android, via ML Kit GenAI) & Apple Foundation Models (iOS).

[![Maven Central](https://img.shields.io/maven-central/v/dev.ynagai.ondevice/ondevice-llm)](https://central.sonatype.com/artifact/dev.ynagai.ondevice/ondevice-llm)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![CI](https://github.com/uny/ondevice-llm/actions/workflows/ci.yml/badge.svg)](https://github.com/uny/ondevice-llm/actions/workflows/ci.yml)

A single `OnDeviceGenerator` API for native on-device text generation. No agent framework required — most on-device use cases (summarize, classify, rewrite, extract) are single-shot `generate()` calls. To use this with [Koog](https://github.com/JetBrains/koog), see [`koog-ondevice`](https://github.com/uny/koog-ondevice).

## Supported platforms

| Platform | Backend | Min OS | Hardware |
|:---------|:--------|:-------|:---------|
| Android | Gemini Nano (ML Kit GenAI Prompt API, via AICore) | API 26+ | Pixel 9+ and other AICore-capable devices |
| iOS | Apple Foundation Models | iOS 26+ | Apple Intelligence-capable devices (iPhone 15 Pro+, etc.) |

## Installation

`gradle/libs.versions.toml`:

```toml
[libraries]
ondevice-llm = { module = "dev.ynagai.ondevice:ondevice-llm", version = "0.1.0" }
```

`build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ondevice.llm)
        }
    }
}
```

## Usage

```kotlin
val generator = createOnDeviceGenerator()
if (checkOnDeviceStatus() == OnDeviceStatus.AVAILABLE) {
    val text = generator.generate(
        OnDeviceRequest(prompt = "Summarize: ...", maxOutputTokens = 200)
    )
}

// streaming
generator.generateStream(OnDeviceRequest(prompt = "...")).collect { chunk ->
    when (chunk) {
        is OnDeviceChunk.Delta -> print(chunk.text)
        is OnDeviceChunk.Done -> println("\n[${chunk.finishReason}]")
    }
}

generator.close()
```

The `systemInstruction` field is delivered through each platform's native channel
(Foundation Models `Instructions` on iOS; prepended to the prompt on Android). Do
not inline system text into `prompt` yourself.

On platforms that support it, trigger a model download with `downloadModel()`.

## iOS toolchain requirements

The iOS backend links against Apple's `FoundationModels` framework through a small
Swift bridge resolved via Gradle's `swiftPMDependencies` (Experimental in Kotlin 2.4.0):

- Xcode 16.x with the Swift 6.2 / iOS 26 SDK
- A local SwiftPM package (`swift/`) is built as part of the Kotlin/Native compile

## Limitations

- No tool calling, moderation, or structured-output abstraction (platform APIs are
  immature; out of scope for this library).
- iOS serializes generations (single Foundation Models session, one at a time).
- Finish reason: Android can report `LENGTH` (token cap hit); iOS cannot detect a
  `maxOutputTokens` cutoff and always reports `STOP`.
- No desktop / web / JVM targets — Android and iOS only.

## Testing

The unit tests are platform-independent. Real inference requires capable hardware
(AICore/Gemini Nano on Android; an Apple Intelligence device on iOS); CI emulators
and simulators have no model and report `UNAVAILABLE`, so there are no integration
tests — verify on a real device.

## License

Apache 2.0
