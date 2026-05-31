package dev.ynagai.ondevice

import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException

actual suspend fun warmUpOnDevice() {
    Generation.getClient().warmup()
}

actual suspend fun countOnDeviceTokens(request: OnDeviceRequest): Int? = try {
    val mlRequest = generateContentRequest(TextPart(request.toPromptText())) {}
    Generation.getClient().countTokens(mlRequest).totalTokens
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Token counting needs the model present; treat any failure as "unknown".
    null
}
