package dev.ynagai.ondevice

import kotlin.test.Test
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
}
