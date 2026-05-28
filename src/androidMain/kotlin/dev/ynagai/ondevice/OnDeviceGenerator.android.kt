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

    @Volatile
    private var cached: GenerativeModel? = null
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
        synchronized(lock) {
            cached?.close()
            cached = null
        }
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
