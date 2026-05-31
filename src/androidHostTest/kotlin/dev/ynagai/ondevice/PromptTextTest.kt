package dev.ynagai.ondevice

import kotlin.test.Test
import kotlin.test.assertEquals

class PromptTextTest {

    @Test
    fun prependsSystemInstructionAcrossABlankLine() {
        // The token counter and the generator share this builder, so the exact text
        // generation sends is the text that gets counted.
        val request = OnDeviceRequest(prompt = "Summarize this.", systemInstruction = "You are terse.")
        assertEquals("You are terse.\n\nSummarize this.", request.toPromptText())
    }

    @Test
    fun usesPromptVerbatimWhenNoSystemInstruction() {
        assertEquals("Summarize this.", OnDeviceRequest(prompt = "Summarize this.").toPromptText())
    }
}
