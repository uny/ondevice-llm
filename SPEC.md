# ondevice-llm: Specification & Build Instructions

> Framework-agnostic, Kotlin Multiplatform on-device LLM inference.
> Gemini Nano (Android, via ML Kit GenAI) & Apple Foundation Models (iOS).
> **Knows nothing about Koog.** Koog integration lives in the separate `koog-ondevice` repo, which depends on this library.

**Status**: greenfield (repo contains only `LICENSE` at time of writing)
**Author**: Yuki Nagai (@uny)
**License**: Apache 2.0
**Artifact**: `dev.ynagai.ondevice:ondevice-llm`
**Package**: `dev.ynagai.ondevice`
**Repository**: `github.com/uny/ondevice-llm`

このドキュメントは「仕様書」と「実装指示書」を兼ねる。新しいセッション/エージェントがこの 1 ファイルだけを読んで `ondevice-llm` を構築できることを目標に書いている。

---

## 0. なぜこのリポジトリが存在するのか（背景・必読）

現状、on-device 推論のコードは `koog-ondevice` リポジトリに一体化している。そこには 2 つの層が混在している：

1. **コア層**（本当に難しくて価値のある部分） — ML Kit GenAI と Apple Foundation Models を `expect`/`actual` で単一 API に束ね、可用性チェック・ストリーミング・iOS の Swift ブリッジを揃える。
2. **Koog アダプタ層** — 上記を Koog の `LLMClient`/`PromptExecutor` に翻訳し、Hybrid Executor を提供する。

この 2 層を分離する。判断の経緯と結論は以下：

- **オンデバイスの Limitations（tool calling 不可・moderation 不可・structured output 共通抽象なし）が、そのまま「多くの利用者は agent フレームワークを必要としない」ことを意味する。** オンデバイスの良いユースケース（要約・分類・書き換え・抽出）はほぼ単発 `generate()` で足り、Koog のエージェント機構一式を引く必要がない。
- よって **コアは Koog から独立した artifact として発見・利用できるべき**で、Koog 名のリポジトリに従属させるのは看板と中身が逆。
- `koog-firebase`（純 `commonMain`）が `firebase-kotlin-sdk`（プラットフォーム SDK）の上に乗っているのと**同じ構造**を採る。理由は違う（向こうはプラットフォーム SDK、こちらは「利用者の多数が framework 不要」）が、結論の形は一致する。
- v0.2.0・利用者僅少・pre-1.0 の**今が分割の最安タイミング**。

### 分割後の二リポジトリ

| リポジトリ | 役割 | パッケージ / artifact | source sets | Koog 依存 |
|:----------|:-----|:---------------------|:-----------|:---------|
| **ondevice-llm**（本リポジトリ） | コア SDK。プラットフォームネイティブ推論の KMP 統一 | `dev.ynagai.ondevice` / `dev.ynagai.ondevice:ondevice-llm` | `commonMain` + `androidMain` + `iosMain` | **なし** |
| **koog-ondevice**（既存・別作業で改修） | 薄い Koog アダプタ | `dev.ynagai.koog.ondevice` / `dev.ynagai.koog:koog-ondevice` | `commonMain` のみ | あり（このコアに依存） |

> このドキュメントは主に **ondevice-llm の構築**を指示する。§9 に下流 `koog-ondevice` がどう痩せるかを併記する（そちらは別タスク）。

---

## 1. Goals / Non-Goals

### Goals

1. **Koog 非依存のコア API**: `OnDeviceGenerator`（`suspend generate()` / `Flow` ストリーミング）を提供し、フレームワーク無しで直接呼べる。
2. **プラットフォームネイティブ API の KMP 統一**: Android = ML Kit GenAI Prompt API（Gemini Nano via AICore）、iOS = Apple Foundation Models（Swift ブリッジ経由）。
3. **可用性チェック / ダウンロード**: `checkOnDeviceStatus()`, `downloadModel()`。
4. **プラットフォーム差の吸収**: iOS の累積スナップショット→増分デルタ変換、単一セッションの直列化、Android のモデルキャッシュ/クローズ管理をコア内に隠蔽する。
5. **最小依存**: `kotlinx-coroutines` のみ（+ 各プラットフォーム SDK）。**Koog も kotlinx-serialization も依存しない。**

### Non-Goals

- Koog 連携（→ `koog-ondevice` の領域）。
- Tool calling / moderation / structured output の共通抽象（プラットフォーム API が未成熟、かつコアのスコープ外）。
- GGUF 等の自前モデルバンドル実行（→ `koog-edge` の領域）。
- Desktop / Web / JVM ターゲット（iOS + Android のみ）。

---

## 2. Core API Design (Koog-free)

すべて `commonMain` の `dev.ynagai.ondevice` パッケージ。**Koog の型を一切 import しない**のが絶対条件。

### 2.1 リクエスト / 結果型

```kotlin
package dev.ynagai.ondevice

/**
 * A single-shot, framework-agnostic generation request.
 *
 * [systemInstruction] is delivered to the platform through its native channel when
 * one exists (Foundation Models `Instructions`); on platforms without a dedicated
 * channel (ML Kit) it is prepended to the prompt body by the actual implementation.
 * Callers should NOT inline system text into [prompt] themselves.
 *
 * A null [temperature] / [maxOutputTokens] means "use the platform default".
 */
data class OnDeviceRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
)

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
 *   always reports [STOP] — see §4.3).
 * - [OTHER]: backend-specific termination.
 */
enum class OnDeviceFinishReason { STOP, LENGTH, OTHER }
```

### 2.2 ジェネレータ本体（expect/actual）

```kotlin
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
 */
interface OnDeviceGenerator {
    suspend fun generate(request: OnDeviceRequest): String
    fun generateStream(request: OnDeviceRequest): Flow<OnDeviceChunk>
    fun close()
}

expect fun createOnDeviceGenerator(): OnDeviceGenerator
```

> 設計判断: `interface` + `expect fun` ファクトリにする（`expect class` ではなく）。実装クラス名がプラットフォームごとに `AndroidOnDeviceGenerator` / `IosOnDeviceGenerator` と異なってよく、テストで fake を差し込みやすいため。

### 2.3 可用性

```kotlin
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
```

### 2.4 増分デルタヘルパ（commonMain, internal）

`koog-ondevice` の `StreamDelta.kt` をそのまま移設する（Koog 非依存・現状 internal）。

```kotlin
package dev.ynagai.ondevice

/**
 * Recover the incremental delta from cumulative streaming snapshots.
 *
 * Apple Foundation Models emits the full text-so-far on every callback. Returns the
 * suffix of [cumulative] past its longest common prefix with [previous], so callers
 * that concatenate deltas don't duplicate the already-sent prefix.
 */
internal fun incrementalDelta(previous: String, cumulative: String): String =
    cumulative.substring(previous.commonPrefixWith(cumulative).length)
```

### 2.5（任意）モデルメタ情報

コア利用者がコンテキスト長などを知れるよう、Koog 非依存の軽量データを置いてもよい。**Koog の `LLModel` は置かない**（それはアダプタの責務）。

```kotlin
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
```

> これを入れるか迷ったら **入れる**。アダプタの `OnDeviceModels`（Koog `LLModel`）がこの値を単一の真実から引けて、数値の二重管理が消える。

---

## 3. Android 実装 (`androidMain`)

`koog-ondevice` の `AndroidOnDeviceLLMClient`（ML Kit 部分）から **Koog 依存を剥がした**ものを移植する。

```kotlin
package dev.ynagai.ondevice

import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

actual fun createOnDeviceGenerator(): OnDeviceGenerator = AndroidOnDeviceGenerator()

class AndroidOnDeviceGenerator : OnDeviceGenerator {

    private val closed = AtomicBoolean(false)

    @Volatile private var cached: GenerativeModel? = null
    private val lock = Any()

    private fun client(): GenerativeModel {
        check(!closed.get()) { "AndroidOnDeviceGenerator is closed" }
        cached?.let { return it }
        return synchronized(lock) { cached ?: Generation.getClient().also { cached = it } }
    }

    override suspend fun generate(request: OnDeviceRequest): String =
        try {
            val response = client().generateContent(request.toMlKitRequest())
            response.candidates.firstOrNull()?.text.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw OnDeviceException("On-device inference failed", e)
        }

    override fun generateStream(request: OnDeviceRequest): Flow<OnDeviceChunk> = flow {
        try {
            var finishReason: Int? = null
            client().generateContentStream(request.toMlKitRequest()).collect { chunk ->
                val candidate = chunk.candidates.firstOrNull()
                candidate?.finishReason?.let { finishReason = it }
                candidate?.text?.takeIf { it.isNotEmpty() }?.let { emit(OnDeviceChunk.Delta(it)) }
            }
            emit(OnDeviceChunk.Done(finishReason.toFinishReason()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw OnDeviceException("Streaming failed", e)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) { cached?.close(); cached = null }
    }

    // ML Kit has no dedicated system channel, so the system instruction is prepended.
    private fun OnDeviceRequest.toMlKitRequest() =
        generateContentRequest(TextPart(systemInstruction?.let { "$it\n\n$prompt" } ?: prompt)) {
            this@toMlKitRequest.temperature?.let { temperature = it.toFloat() }
            this@toMlKitRequest.maxOutputTokens?.let { maxOutputTokens = it }
        }

    private fun Int?.toFinishReason(): OnDeviceFinishReason = when (this) {
        Candidate.FinishReason.MAX_TOKENS -> OnDeviceFinishReason.LENGTH
        Candidate.FinishReason.OTHER -> OnDeviceFinishReason.OTHER
        else -> OnDeviceFinishReason.STOP
    }
}
```

可用性（`koog-ondevice` の `OnDeviceAvailability.android.kt` をそのまま移設、import 先パッケージのみ変更）：

```kotlin
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
```

共通の例外型（`commonMain`）：

```kotlin
package dev.ynagai.ondevice

/** Wraps any platform inference failure. Koog-free replacement for LLMClientException. */
class OnDeviceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

---

## 4. iOS 実装 (`iosMain` + Swift ブリッジ)

### 4.1 Swift ブリッジの移設

`koog-ondevice/swift/` をそのまま本リポジトリのルート `swift/` に移設する。**`@objc` のシグネチャは変更不要**。NSError ドメイン文字列だけ `dev.ynagai.koog.ondevice` → `dev.ynagai.ondevice` に更新する（機能差はないが一貫性のため）。

`swift/Package.swift`（変更なし）:

```swift
// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "OnDeviceLlmBridge",
    platforms: [.iOS(.v26)],
    products: [
        .library(name: "OnDeviceLlmBridge", type: .static, targets: ["OnDeviceLlmBridge"]),
    ],
    targets: [
        .target(
            name: "OnDeviceLlmBridge",
            linkerSettings: [.linkedFramework("FoundationModels")]
        ),
    ]
)
```

`swift/Sources/OnDeviceLlmBridge/LlmBridge.swift`（NSError ドメインのみ更新、他は現状維持）:

```swift
import Foundation
import FoundationModels

@objc public class OnDeviceLlmBridge: NSObject {
    private var session: LanguageModelSession?

    @objc public static func isAvailable() -> Bool {
        SystemLanguageModel.default.isAvailable
    }

    @objc public func createSession(_ instructions: String?) {
        if let instructions {
            session = LanguageModelSession { Instructions(instructions) }
        } else {
            session = LanguageModelSession()
        }
    }

    @objc public func generate(
        _ prompt: String, temperature: Double, maxTokens: Int32
    ) async throws -> String {
        guard let session else { throw Self.notInitialized }
        let response = try await session.respond(
            to: prompt, options: Self.options(temperature: temperature, maxTokens: maxTokens)
        )
        return response.content
    }

    @objc public func streamGenerate(
        _ prompt: String, temperature: Double, maxTokens: Int32,
        callback: @escaping (String) -> Void
    ) async throws {
        guard let session else { throw Self.notInitialized }
        let stream = session.streamResponse(
            to: prompt, options: Self.options(temperature: temperature, maxTokens: maxTokens)
        )
        for try await partial in stream { callback(partial.content) }
    }

    // Kotlin cannot pass optional primitives across @objc: negative temperature /
    // non-positive maxTokens encodes "unset" → use the Foundation Models default.
    private static func options(temperature: Double, maxTokens: Int32) -> GenerationOptions {
        GenerationOptions(
            temperature: temperature >= 0 ? temperature : nil,
            maximumResponseTokens: maxTokens > 0 ? Int(maxTokens) : nil
        )
    }

    private static var notInitialized: NSError {
        NSError(domain: "dev.ynagai.ondevice", code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Session not initialized"])
    }

    @objc public func close() { session = nil }
}
```

### 4.2 `swiftPMImport` 名前空間に関する重要な注意

iosMain から Swift ブリッジを import するパスは Kotlin ツールチェーンが**自動生成**する。`koog-ondevice` では `swiftPMImport.dev.ynagai.koog.koog.ondevice.OnDeviceLlmBridge` だった。本リポジトリは group / module / namespace が変わるため、**このパスは変わる**。

- ⚠️ **import パスをこの仕様書の値で決め打ちしないこと。** module を設定して一度 `./gradlew compileKotlinIosSimulatorArm64` を通し、生成された実際の `swiftPMImport.*` 名前空間を確認してから iosMain の import を書く。
- 期待値はおおむね `swiftPMImport.dev.ynagai.ondevice.<...>.OnDeviceLlmBridge`（`<...>` は module 名由来のセグメント）。コンパイルエラーが教えてくれる。

### 4.3 iOS ジェネレータ

`koog-ondevice` の `IosOnDeviceLLMClient` から Koog 依存を剥がし、`OnDeviceGenerator` に適合させる。**mutex 直列化・累積→デルタ変換・unlimited buffer は維持する**（これらがコアの価値）。

```kotlin
package dev.ynagai.ondevice

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSError
// ⚠️ 実際の生成パスに置き換える（§4.2）。例:
import swiftPMImport.dev.ynagai.ondevice.OnDeviceLlmBridge
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
actual fun createOnDeviceGenerator(): OnDeviceGenerator = IosOnDeviceGenerator()

@OptIn(ExperimentalForeignApi::class)
class IosOnDeviceGenerator : OnDeviceGenerator {

    private val bridge = OnDeviceLlmBridge()
    // Single Foundation Models session, one generation at a time → serialize.
    private val mutex = Mutex()

    override suspend fun generate(request: OnDeviceRequest): String = mutex.withLock {
        try {
            bridge.createSession(request.systemInstruction)
            suspendCancellableCoroutine { cont ->
                bridge.generate(
                    request.prompt,
                    request.temperature ?: UNSET_TEMPERATURE,
                    request.maxOutputTokens ?: UNSET_MAX_TOKENS,
                ) { result, error ->
                    if (error != null) cont.resumeWithException(error.toException())
                    else cont.resume(result.orEmpty())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw OnDeviceException("On-device inference failed", e)
        }
    }

    override fun generateStream(request: OnDeviceRequest): Flow<OnDeviceChunk> = channelFlow {
        mutex.withLock {
            try {
                bridge.createSession(request.systemInstruction)
                var previous = ""
                suspendCancellableCoroutine { cont ->
                    bridge.streamGenerate(
                        request.prompt,
                        request.temperature ?: UNSET_TEMPERATURE,
                        request.maxOutputTokens ?: UNSET_MAX_TOKENS,
                        { cumulative ->
                            val text = cumulative.orEmpty()
                            val delta = incrementalDelta(previous, text)
                            previous = text
                            if (delta.isNotEmpty()) trySend(OnDeviceChunk.Delta(delta))
                        },
                    ) { error ->
                        if (error != null) cont.resumeWithException(error.toException())
                        else cont.resume(Unit)
                    }
                }
                // Foundation Models exposes no finish-reason signal (a maxTokens cutoff
                // is a silent truncation), so we always report STOP — unlike Android.
                send(OnDeviceChunk.Done(OnDeviceFinishReason.STOP))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw OnDeviceException("Streaming failed", e)
            }
        }
    }.buffer(Channel.UNLIMITED)

    override fun close() = bridge.close()
}

// Sentinels for the @objc bridge (cannot carry optional primitives).
private const val UNSET_TEMPERATURE: Double = -1.0
private const val UNSET_MAX_TOKENS: Int = 0

private fun NSError.toException(): Exception =
    RuntimeException("$domain (code $code): $localizedDescription")
```

可用性（`OnDeviceAvailability.ios.kt` を移設、import のみ更新）：

```kotlin
package dev.ynagai.ondevice

import kotlinx.cinterop.ExperimentalForeignApi
import swiftPMImport.dev.ynagai.ondevice.OnDeviceLlmBridge // ⚠️ §4.2

@OptIn(ExperimentalForeignApi::class)
actual suspend fun checkOnDeviceStatus(): OnDeviceStatus =
    if (OnDeviceLlmBridge.isAvailable()) OnDeviceStatus.AVAILABLE else OnDeviceStatus.UNAVAILABLE

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadModel(): Boolean =
    // Apple manages model downloads transparently.
    checkOnDeviceStatus() == OnDeviceStatus.AVAILABLE
```

---

## 5. Build Configuration

### 5.1 `gradle/libs.versions.toml`

`koog-ondevice` の版から **koog / serialization を削除**し、Kotlin を安定版 `2.4.0` に上げる。

```toml
[versions]
agp = "8.13.2"
android-compileSdk = "36"
android-minSdk = "26"
kotlin = "2.4.0"
kotlinx-coroutines = "1.10.2"
publish = "0.36.0"
mlkit-genai = "1.0.0-beta2"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
mlkit-genai-prompt = { module = "com.google.mlkit:genai-prompt", version.ref = "mlkit-genai" }

[plugins]
android-kotlin-multiplatform-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
publish = { id = "com.vanniktech.maven.publish", version.ref = "publish" }
```

> **kotlinx-serialization プラグイン/依存は不要。** コアは何もシリアライズしない。`OnDeviceLLMProvider`（`@Serializable`）はアダプタ側に残る。

### 5.2 `build.gradle.kts`

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.publish)
}

group = "dev.ynagai.ondevice"

kotlin {
    androidLibrary {
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
        namespace = "dev.ynagai.ondevice"
    }
    iosArm64()
    iosSimulatorArm64()
    withSourcesJar(publish = true)

    swiftPMDependencies {
        iosMinimumDeploymentTarget.set("26.0")
        localSwiftPackage(
            directory = project.layout.projectDirectory.dir("swift"),
            products = listOf("OnDeviceLlmBridge"),
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.genai.prompt)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name.set("On-Device LLM")
        description.set("Framework-agnostic on-device LLM inference for Kotlin Multiplatform — Gemini Nano (Android) & Apple Foundation Models (iOS)")
        url.set("https://github.com/uny/ondevice-llm")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer { id.set("uny"); name.set("Yuki Nagai"); url.set("https://github.com/uny") }
        }
        scm {
            url.set("https://github.com/uny/ondevice-llm")
            connection.set("scm:git:https://github.com/uny/ondevice-llm.git")
            developerConnection.set("scm:git:https://github.com/uny/ondevice-llm.git")
        }
    }
}
```

### 5.3 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); mavenLocal() }
}
rootProject.name = "ondevice-llm"
```

### 5.4 `gradle.properties`

```properties
version=0.1.0

org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
org.gradle.caching=true

kotlin.mpp.enableCInteropCommonization=true
kotlin.native.ignoreDisabledTargets=true

android.useAndroidX=true
android.nonTransitiveRClass=true

kotlin.jvm.target=21
```

### 5.5 Gradle wrapper

`koog-ondevice` の `gradlew`, `gradlew.bat`, `gradle/wrapper/*` をそのままコピーする（Gradle 9.x）。

---

## 6. CI / CD

`koog-ondevice` の `.github/workflows/ci.yml` と `cd.yml` を**ほぼそのままコピー**する（単一モジュールなので `--no-parallel` は不要）。重要点：

- `runs-on: macos-latest`（FoundationModels SDK のため Xcode が必要）。
- `maxim-lobanov/setup-xcode@... latest-stable`（Swift 6.2 / iOS 26 SDK）。
- JDK 21 / Temurin。
- `~/.konan` キャッシュ（key は `gradle/libs.versions.toml` のハッシュ）。
- **CD のみ**: publish の前に `./gradlew fetchSyntheticImportProjectPackages`（SwiftPM 解決）を実行。
- CI は `./gradlew assemble check --build-cache`。
- 必要 Secrets: `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` / `GPG_KEY_ID` / `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE`。新リポジトリの GitHub Environment `release` にも同じ secrets を設定すること。

`.gitignore` には少なくとも `build/`, `.gradle/`, `.kotlin/`, `local.properties`, `swift/.build/`, `.swiftpm-locks/`（必要なら）を含める。`koog-ondevice` の `.gitignore` を流用しつつ Swift ビルド生成物を確実に除外する。

---

## 7. Tests (`commonTest`)

コアは Koog 非依存なので、Koog を使わない純粋なユニットテストだけ書ける。

- `incrementalDelta` の境界（純粋な追記 / 途中改訂 / 同一文字列 / 空）。`koog-ondevice` の `IncrementalDeltaTest` を移植。
- `OnDeviceRequest` / `OnDeviceModelInfo` の素朴な検証。
- ⚠️ 実推論はハード依存（Android: AICore/Gemini Nano 実機、iOS: Apple Intelligence 対応端末）で、CI のエミュレータ/シミュレータには対応モデルが無く `checkStatus()` が `UNAVAILABLE` を返すだけ。**統合テストは設けない。** 実機確認は手動（README に手順）。

> 注: `koog-ondevice` 側に現存する `DefaultPromptRouterTest` / `EstimateTokenCountTest` / `ToPromptStringTest` / `ExtractSystemInstructionTest` は **Koog 依存ロジックのテストなのでアダプタ側に残す**（§9）。コアには移さない。

---

## 8. README（新規作成）

コアを「KMP の on-device LLM ライブラリ」として単体で発見・利用できるよう書く。最低限：

- 一文要約 + バッジ（Maven Central / License / CI）。
- 対応プラットフォーム（Android API 26+ / iOS 26+）と必要ハード（Pixel 9+ 等 / iPhone 15 Pro+ 等）。
- インストール（version catalog & DSL、`dev.ynagai.ondevice:ondevice-llm`）。
- 最小例：

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
  ```

- iOS ツールチェーン要件（Xcode 26.x / Swift 6.2 / iOS 26 SDK、`swiftPMDependencies` は Experimental）。
- Limitations（tool calling / moderation / structured output 非対応、iOS は逐次実行）。
- 「Koog で使うなら `koog-ondevice` を見よ」へのリンク。

---

## 9. 下流 `koog-ondevice` の改修（別タスク・参考）

このコアが公開されたら、`koog-ondevice` は**純 `commonMain` のアダプタ**に痩せる（`androidMain`/`iosMain` を削除）。要点のみ：

- 依存に `implementation("dev.ynagai.ondevice:ondevice-llm:<version>")` を追加。`koog-agents` と `kotlinx-serialization` は維持。
- `OnDeviceLLMProvider`（`@Serializable data object`）はそのまま残す。
- `OnDeviceModels`（Koog `LLModel`）は残すが、数値はコアの `OnDeviceModelInfo` から引く。`Default` の expect/actual は不要になる（→ コアに `createOnDeviceGenerator()` があり、モデル選択は `OnDeviceModelInfo` で表現できる。`OnDeviceModels.Default` を Koog `LLModel` で出したいなら commonMain で `OnDeviceModelInfo` を見て分岐する純関数にできる）。
- `OnDeviceLLMClient` を **commonMain の単一クラス**に作り直す。`expect`/`actual` を廃し、コアの `OnDeviceGenerator` を保持して Koog `LLMClient` に翻訳する：

  ```kotlin
  class OnDeviceLLMClient(
      private val generator: OnDeviceGenerator = createOnDeviceGenerator(),
  ) : LLMClient() {
      override fun llmProvider() = OnDeviceLLMProvider
      override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
          require(tools.isEmpty()) { "On-device inference does not support tool calling" }
          val text = generator.generate(prompt.toOnDeviceRequest())
          return Message.Assistant(listOf(MessagePart.Text(text)), ResponseMetaInfo(Clock.System.now()))
      }
      override fun executeStreaming(...): Flow<StreamFrame> = flow {
          require(tools.isEmpty()) { ... }
          generator.generateStream(prompt.toOnDeviceRequest()).collect { chunk ->
              when (chunk) {
                  is OnDeviceChunk.Delta -> emit(StreamFrame.TextDelta(chunk.text))
                  is OnDeviceChunk.Done -> emit(StreamFrame.End(chunk.finishReason.toKoog(), ResponseMetaInfo(...)))
              }
          }
      }
      override suspend fun moderate(...) = throw UnsupportedOperationException(...)
      override suspend fun models() = OnDeviceModels.models
      override fun close() = generator.close()
  }
  ```

- `prompt.toOnDeviceRequest()` は `MessageMappers` を使い、**常に** `systemInstruction = extractSystemInstruction()` と `prompt = toPromptString(includeSystem = false)` を渡す（プラットフォーム差はコアが吸収するので、アダプタは Android/iOS を区別しない）。
- `HybridPromptExecutor` / `PromptRouter` / `DefaultPromptRouter` / `estimateTokenCount` はそのまま `koog-ondevice` に残す（すべて Koog `Prompt` 依存）。`DefaultPromptRouter` の `statusProvider` は `dev.ynagai.ondevice.checkOnDeviceStatus` を参照するよう import を変更。
- `MessageMappers` / 既存テスト群（`DefaultPromptRouterTest` 等）は残す。
- `StreamDelta.kt`（`incrementalDelta`）と `OnDeviceAvailability` の expect/actual、Swift ブリッジは**コアへ移動したので削除**。
- 破壊的変更（コアシンボルが `dev.ynagai.ondevice` へ移動）に伴い `koog-ondevice` をマイナーバンプ。互換 typealias は入れない。

---

## 10. 実装手順（チェックリスト）

ondevice-llm（本リポジトリ）を 0 から立ち上げる順序：

1. [ ] `koog-ondevice` から wrapper / `gradlew` / `gradle/wrapper` / `.gitignore` をコピー。
2. [ ] `settings.gradle.kts`・`gradle.properties`・`gradle/libs.versions.toml`・`build.gradle.kts` を §5 の内容で作成。
3. [ ] `swift/` を移設し NSError ドメインを更新（§4.1）。
4. [ ] `commonMain` にコア API を作成（§2: `OnDeviceRequest`, `OnDeviceChunk`, `OnDeviceFinishReason`, `OnDeviceGenerator`+`createOnDeviceGenerator` expect, `OnDeviceStatus`+`checkOnDeviceStatus`/`downloadModel` expect, `incrementalDelta`, `OnDeviceException`, 任意で `OnDeviceModelInfo`)。
5. [ ] `androidMain` を作成（§3）。`./gradlew compileAndroidMain` で通す。
6. [ ] `iosMain` を作成（§4.3）。まず仮 import で `./gradlew compileKotlinIosSimulatorArm64` を走らせ、**生成された実際の `swiftPMImport.*` 名前空間**を確認して import を確定（§4.2）。再コンパイルで通す。
7. [ ] `commonTest` を作成（§7、`IncrementalDeltaTest` 移植）。`./gradlew check` を通す。
8. [ ] README 作成（§8）。
9. [ ] CI/CD ワークフロー作成（§6）。GitHub `release` Environment に secrets 設定。
10. [ ] `./gradlew assemble check` がローカルで緑であることを確認。
11. [ ] v0.1.0 タグで Maven Central 公開を確認。
12. [ ] （別タスク）`koog-ondevice` を §9 に従い改修し、このコアに依存させる。

### 検証コマンド

```bash
./gradlew compileAndroidMain                # Android コア
./gradlew compileKotlinIosSimulatorArm64    # iOS コア（swiftPM 解決を含む）
./gradlew check                             # commonTest
./gradlew assemble check                    # CI 相当
./gradlew fetchSyntheticImportProjectPackages   # CD の SwiftPM 解決ステップ
```

---

## 11. 既知の落とし穴 / Open Questions

- **`swiftPMImport.*` パスは生成依存**（§4.2）。仕様書の値を信用せず、必ずコンパイルで確認。
- **`swiftPMDependencies` は Experimental**（Kotlin 2.4.0）。DSL が将来変わりうる。Maven Central 公開を阻んでいた `swiftpm-metadata` 不備（KT-85476）は 2.4.0-Beta2 で修正済みのため回避策不要。
- **finish reason**: iOS は MAX_TOKENS を検知できず常に `STOP`。Android のみ `LENGTH` を返す。これは仕様（アダプタ・利用者に明示済み）。
- **`OnDeviceModelInfo` を置くか**: 置く方を推奨（数値の単一真実化）。不要と判断したら省略可で、その場合アダプタの `OnDeviceModels` が数値を直接持つ。
- **min deployment target 26.0 / minSdk 26**: 対応端末が限られる前提。CI のシミュレータ/エミュレータでは実推論不可。

---

## 12. Related Resources

| Resource | URL |
|:---------|:----|
| koog-ondevice（分割前・移植元） | `github.com/uny/koog-ondevice` |
| koog-firebase（対称アダプタの参考） | `github.com/uny/koog-firebase` |
| firebase-kotlin-sdk（コア SDK 構成の参考） | `github.com/uny/firebase-kotlin-sdk` |
| Koog | `github.com/JetBrains/koog` / `docs.koog.ai` |
| ML Kit GenAI Prompt API | `developers.google.com/ml-kit/genai/prompt` |
| Apple Foundation Models | `developer.apple.com/documentation/FoundationModels` |
