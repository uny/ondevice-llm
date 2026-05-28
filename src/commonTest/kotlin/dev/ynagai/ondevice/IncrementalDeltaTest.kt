package dev.ynagai.ondevice

import kotlin.test.Test
import kotlin.test.assertEquals

class IncrementalDeltaTest {

    @Test
    fun returnsAppendedSuffixForCumulativeSnapshots() {
        assertEquals("", incrementalDelta("", ""))
        assertEquals("Hello", incrementalDelta("", "Hello"))
        assertEquals(" world", incrementalDelta("Hello", "Hello world"))
    }

    @Test
    fun emitsNothingWhenSnapshotUnchanged() {
        assertEquals("", incrementalDelta("Hello", "Hello"))
    }

    @Test
    fun emitsOnlyDivergedTailWhenSnapshotRevisesPrefix() {
        // Prior snapshot was "Hello wrold"; the corrected snapshot shares the
        // common prefix "Hello wr" — only the diverged tail should be emitted,
        // not the whole revised string (which would duplicate the prefix).
        assertEquals("orld", incrementalDelta("Hello wrold", "Hello world"))
    }

    @Test
    fun handlesSnapshotShorterThanPrevious() {
        assertEquals("", incrementalDelta("Hello world", "Hello"))
    }
}
