package dev.ynagai.ondevice

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OnDeviceRequestTest {

    @Test
    fun samplingControlsDefaultToPlatformDefault() {
        // null means "use the platform default"; only callers that opt in carry topK/seed.
        val request = OnDeviceRequest(prompt = "hello")
        assertNull(request.temperature)
        assertNull(request.maxOutputTokens)
        assertNull(request.topK)
        assertNull(request.seed)
    }

    @Test
    fun rejectsNonPositiveTopK() {
        // topK < 1 is not a valid sampling width; reject it in common rather than letting
        // each platform diverge (iOS falls back to default sampling, Android passes it on).
        assertFailsWith<IllegalArgumentException> { OnDeviceRequest(prompt = "hi", topK = 0) }
    }

    @Test
    fun rejectsNegativeSeed() {
        // A negative seed collides with the iOS "no fixed seed" sentinel, so it would be
        // silently ignored there; reject it up front instead.
        assertFailsWith<IllegalArgumentException> { OnDeviceRequest(prompt = "hi", seed = -1) }
    }

    @Test
    fun allowsSeedWithoutTopK() {
        // A seed applies independently of topK on both platforms (iOS falls back to
        // full-distribution sampling so the seed still takes effect).
        val request = OnDeviceRequest(prompt = "hi", seed = 42)
        assertNull(request.topK)
    }
}
