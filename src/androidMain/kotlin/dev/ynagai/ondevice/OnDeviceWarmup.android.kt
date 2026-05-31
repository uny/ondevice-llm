package dev.ynagai.ondevice

import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException

// Both capabilities are best-effort per their contract: safe to call when the model
// is unavailable, so any failure is swallowed (cancellation still propagates).
private suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

actual suspend fun warmUpOnDevice() {
    bestEffort { capabilityClient.warmup() }
}

actual suspend fun countOnDeviceTokens(request: OnDeviceRequest): Int? = bestEffort {
    val mlRequest = generateContentRequest(TextPart(request.toPromptText())) {}
    capabilityClient.countTokens(mlRequest).totalTokens
}
