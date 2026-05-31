package dev.ynagai.ondevice

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class OnDeviceWarmupTest {

    @Test
    fun warmUpIsBestEffort() = runTest {
        // Foundation Models prewarm() is a non-throwing hint; warmup must be safe to
        // call even when the model is unavailable (e.g. on the simulator).
        warmUpOnDevice()
    }

    @Test
    fun tokenCountingIsUnsupportedOnIos() = runTest {
        // Foundation Models exposes no token-counting API, so callers fall back to an estimate.
        assertNull(countOnDeviceTokens(OnDeviceRequest(prompt = "hello")))
    }
}
