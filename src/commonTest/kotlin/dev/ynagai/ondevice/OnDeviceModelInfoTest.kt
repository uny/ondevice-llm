package dev.ynagai.ondevice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnDeviceModelInfoTest {

    @Test
    fun geminiNanoHasExpectedFacts() {
        val info = OnDeviceModelInfo.GeminiNano
        assertEquals("gemini-nano", info.id)
        assertTrue(info.contextLength > 0)
        assertTrue(info.maxOutputTokens in 1..info.contextLength)
    }

    @Test
    fun appleFoundationHasExpectedFacts() {
        val info = OnDeviceModelInfo.AppleFoundation
        assertEquals("apple-foundation", info.id)
        assertTrue(info.contextLength > 0)
        assertTrue(info.maxOutputTokens in 1..info.contextLength)
    }

    @Test
    fun requestDefaultsLeaveTuningToPlatform() {
        val request = OnDeviceRequest(prompt = "hi")
        assertEquals(null, request.systemInstruction)
        assertEquals(null, request.temperature)
        assertEquals(null, request.maxOutputTokens)
    }
}
