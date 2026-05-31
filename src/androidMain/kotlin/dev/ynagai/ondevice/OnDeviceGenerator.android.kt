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

/**
 * [clientFactory] builds the ML Kit [GenerativeModel] lazily on first use. The default
 * [Generation.getClient] resolves the application [android.content.Context] through ML
 * Kit's own startup initializer, so no Context is threaded through this API. Override
 * the factory to supply a preconfigured client (or a fake in tests).
 */
class AndroidOnDeviceGenerator(
    private val clientFactory: () -> GenerativeModel = { Generation.getClient() },
) : OnDeviceGenerator {

    private val closed = AtomicBoolean(false)

    @Volatile
    private var cached: GenerativeModel? = null
    private val lock = Any()

    private fun client(): GenerativeModel {
        cached?.let { return it }
        return synchronized(lock) { cached ?: clientFactory().also { cached = it } }
    }

    override suspend fun generate(request: OnDeviceRequest): OnDeviceResponse {
        // Reuse after close() is a caller lifecycle error, not an inference failure, so the
        // IllegalStateException propagates raw (see the OnDeviceGenerator contract) — never
        // wrapped in OnDeviceInferenceException, which is reserved for the model itself.
        check(!closed.get()) { "AndroidOnDeviceGenerator is closed" }
        return try {
            val response = client().generateContent(request.toMlKitRequest())
            val candidate = response.candidates.firstOrNull()
            OnDeviceResponse(candidate?.text.orEmpty(), candidate?.finishReason.toFinishReason())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw OnDeviceInferenceException("On-device inference failed", e)
        }
    }

    override fun generateStream(request: OnDeviceRequest): Flow<OnDeviceChunk> = flow {
        // Reuse after close() is a caller lifecycle error, not an inference failure, so the
        // IllegalStateException propagates raw (see the OnDeviceGenerator contract).
        check(!closed.get()) { "AndroidOnDeviceGenerator is closed" }
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
            throw OnDeviceInferenceException("Streaming failed", e)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            cached?.close()
            cached = null
        }
    }

    // ML Kit has no dedicated system channel, so the system instruction is prepended.
    private fun OnDeviceRequest.toMlKitRequest() =
        generateContentRequest(TextPart(toPromptText())) {
            this@toMlKitRequest.temperature?.let { temperature = it.toFloat() }
            this@toMlKitRequest.maxOutputTokens?.let { maxOutputTokens = it }
            this@toMlKitRequest.topK?.let { topK = it }
            this@toMlKitRequest.seed?.let { seed = it }
        }

    private fun Int?.toFinishReason(): OnDeviceFinishReason = when (this) {
        Candidate.FinishReason.MAX_TOKENS -> OnDeviceFinishReason.LENGTH
        Candidate.FinishReason.OTHER -> OnDeviceFinishReason.OTHER
        else -> OnDeviceFinishReason.STOP
    }
}

/**
 * The exact text ML Kit receives for [this] request. Shared by generation and token
 * counting so the count reflects what generation actually sends — ML Kit has no
 * dedicated system channel, so the system instruction is prepended to the prompt.
 */
internal fun OnDeviceRequest.toPromptText(): String =
    systemInstruction?.let { "$it\n\n$prompt" } ?: prompt
