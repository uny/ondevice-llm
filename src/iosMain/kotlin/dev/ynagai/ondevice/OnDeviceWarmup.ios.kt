package dev.ynagai.ondevice

// Foundation Models prewarm is not yet exposed by foundation-models-objc; warmup
// is best-effort, so no-op for now. (Swap in prewarm() once FM-objc >= 1.1.0.)
actual suspend fun warmUpOnDevice() = Unit

// Foundation Models exposes no token-counting API, so callers fall back to their
// own estimate.
actual suspend fun countOnDeviceTokens(request: OnDeviceRequest): Int? = null
