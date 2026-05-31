package dev.ynagai.ondevice

import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException

actual suspend fun warmUpOnDevice() {
    try {
        capabilityClient.warmup()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Best-effort per the contract: safe to call when the model is unavailable.
    }
}

actual suspend fun countOnDeviceTokens(request: OnDeviceRequest): Int? = try {
    val mlRequest = generateContentRequest(TextPart(request.toPromptText())) {}
    capabilityClient.countTokens(mlRequest).totalTokens
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Token counting needs the model present; treat any failure as "unknown".
    null
}
