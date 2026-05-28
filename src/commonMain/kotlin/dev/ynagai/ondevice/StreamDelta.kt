package dev.ynagai.ondevice

/**
 * Recover the incremental delta from cumulative streaming snapshots.
 *
 * Some on-device backends (e.g. Apple Foundation Models) emit the full text
 * generated so far on every callback rather than just the new piece. Callers
 * concatenate [OnDeviceChunk.Delta]s, so emitting a whole snapshot would
 * duplicate the already-sent prefix.
 *
 * Returns the suffix of [cumulative] past its longest common prefix with
 * [previous]. Foundation Models snapshots grow monotonically, so [previous] is
 * normally a prefix of [cumulative] and this returns exactly the newly appended
 * text.
 *
 * A non-monotonic snapshot (a revised or shortened prefix) cannot be represented
 * in an append-only delta stream: text already emitted to the caller cannot be
 * retracted. That case is not expected from the backend; this returns the diverged
 * tail as a best effort but does not — and cannot — repair the earlier mismatch.
 */
internal fun incrementalDelta(previous: String, cumulative: String): String =
    cumulative.substring(previous.commonPrefixWith(cumulative).length)
