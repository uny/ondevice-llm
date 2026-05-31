package dev.ynagai.ondevice

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class OnDeviceWarmupTest {

    @Test
    fun warmUpIsBestEffortNoOp() = runTest {
        // Foundation Models has no prewarm wiring yet; warmup must be safe to call.
        warmUpOnDevice()
    }

    @Test
    fun tokenCountingIsUnsupportedOnIos() = runTest {
        // Foundation Models exposes no token-counting API, so callers fall back to an estimate.
        assertNull(countOnDeviceTokens(OnDeviceRequest(prompt = "hello")))
    }
}
