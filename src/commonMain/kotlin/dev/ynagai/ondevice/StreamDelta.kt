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
 * [previous]. When [cumulative] purely extends [previous] (the normal case) this
 * is the appended text; if a snapshot revises an earlier portion, only the
 * diverged tail is emitted instead of re-sending content the caller already has.
 */
internal fun incrementalDelta(previous: String, cumulative: String): String =
    cumulative.substring(previous.commonPrefixWith(cumulative).length)
