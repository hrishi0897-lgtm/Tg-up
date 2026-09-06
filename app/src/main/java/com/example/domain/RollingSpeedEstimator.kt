package com.example.domain

import java.util.ArrayDeque

/**
 * Calculates real, live transfer speed over a rolling time window (default 1800ms).
 * Smooths out TCP burstiness and avoids jumpy single-packet spikes or false 0 drops,
 * ensuring accurate B/s, KB/s, or MB/s rates.
 */
class RollingSpeedEstimator(
    private val windowDurationMs: Long = 1800L
) {
    private data class Sample(val timestamp: Long, val cumulativeBytes: Long)
    private val samples = ArrayDeque<Sample>()

    /**
     * Records a new cumulative progress sample and returns the rolling speed in bytes per second.
     */
    @Synchronized
    fun addSample(timestamp: Long, cumulativeBytes: Long): Long {
        samples.addLast(Sample(timestamp, cumulativeBytes))
        val cutoff = timestamp - windowDurationMs
        while (samples.size > 2 && samples.first().timestamp < cutoff) {
            samples.removeFirst()
        }
        if (samples.size < 2) return 0L

        val oldest = samples.first()
        val newest = samples.last()
        val dt = newest.timestamp - oldest.timestamp
        if (dt <= 0) return 0L
        val dBytes = newest.cumulativeBytes - oldest.cumulativeBytes
        if (dBytes <= 0) return 0L

        return ((dBytes * 1000L) / dt).coerceAtLeast(0L)
    }

    /**
     * Returns the current speed estimate without adding a new sample,
     * decaying to 0 if no progress has been reported within the window.
     */
    @Synchronized
    fun getCurrentSpeed(now: Long = System.currentTimeMillis()): Long {
        if (samples.isEmpty()) return 0L
        val newest = samples.last()
        if (now - newest.timestamp > windowDurationMs) {
            return 0L
        }
        if (samples.size < 2) return 0L
        val oldest = samples.first()
        val dt = newest.timestamp - oldest.timestamp
        if (dt <= 0) return 0L
        val dBytes = newest.cumulativeBytes - oldest.cumulativeBytes
        if (dBytes <= 0) return 0L
        return ((dBytes * 1000L) / dt).coerceAtLeast(0L)
    }

    @Synchronized
    fun reset() {
        samples.clear()
    }
}
