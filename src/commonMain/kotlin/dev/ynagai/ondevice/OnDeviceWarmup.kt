package dev.ynagai.ondevice

/**
 * Pre-load the on-device model into memory so the next generation avoids the
 * cold-start cost. Best-effort: safe to call when the model is unavailable, and
 * safe to call repeatedly.
 *
 * - Android: ML Kit `GenerativeModel.warmup()`.
 * - iOS: currently a no-op — Foundation Models `prewarm()` is not yet exposed by
 *   `foundation-models-objc`. Calling it stays safe and will warm the
 *   process-shared model weights once that lands.
 */
expect suspend fun warmUpOnDevice()

/**
 * Exact input token count [request] would consume on the on-device model, or
 * `null` when the platform exposes no token-counting API (iOS Foundation Models)
 * or the model is currently unavailable. Callers should fall back to their own
 * estimate when this returns `null`.
 */
expect suspend fun countOnDeviceTokens(request: OnDeviceRequest): Int?
