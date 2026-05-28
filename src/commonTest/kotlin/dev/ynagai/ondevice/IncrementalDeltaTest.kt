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
    fun returnsDivergedTailBestEffortForNonMonotonicSnapshot() {
        // A revised snapshot is not expected from Foundation Models, and an
        // append-only delta stream cannot repair an already-emitted prefix. This
        // pins the documented best-effort behavior (the diverged tail past the
        // common prefix "Hello w"), not a correctness guarantee.
        assertEquals("orld", incrementalDelta("Hello wrold", "Hello world"))
    }

    @Test
    fun handlesSnapshotShorterThanPrevious() {
        assertEquals("", incrementalDelta("Hello world", "Hello"))
    }
}
