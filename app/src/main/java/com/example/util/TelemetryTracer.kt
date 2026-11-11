package com.example.util

import android.os.SystemClock
import android.util.Log

/**
 * OpenTelemetry-compatible tracing and performance monitoring utility for Android POS.
 * Tracks spans, slow queries, heavy IO tasks, and reports telemetry metrics.
 */
object TelemetryTracer {
    const val TAG = "CajaMaestraTelemetry"
    const val SLOW_THRESHOLD_MS = 250L

    data class Span(
        val name: String,
        val startTimeMs: Long,
        val attributes: MutableMap<String, String> = mutableMapOf()
    ) {
        fun setAttribute(key: String, value: String): Span {
            attributes[key] = value
            return this
        }

        fun end(thresholdMs: Long = SLOW_THRESHOLD_MS): Long {
            val durationMs = SystemClock.elapsedRealtime() - startTimeMs
            if (durationMs > thresholdMs) {
                Log.w(
                    TAG,
                    "[OpenTelemetry] SLOW OPERATION DETECTED: '$name' took ${durationMs}ms (Threshold: ${thresholdMs}ms). Attributes: $attributes"
                )
            } else {
                Log.d(
                    TAG,
                    "[OpenTelemetry] Span '$name' completed in ${durationMs}ms."
                )
            }
            return durationMs
        }
    }

    fun startSpan(name: String): Span {
        return Span(name = name, startTimeMs = SystemClock.elapsedRealtime())
    }

    /**
     * Executes a block within an OpenTelemetry span and automatically records duration.
     */
    inline fun <T> trace(name: String, thresholdMs: Long = SLOW_THRESHOLD_MS, block: () -> T): T {
        val span = startSpan(name)
        return try {
            block()
        } finally {
            span.end(thresholdMs)
        }
    }

    /**
     * Executes a suspend block within an OpenTelemetry span.
     */
    suspend fun <T> traceSuspend(
        name: String,
        thresholdMs: Long = SLOW_THRESHOLD_MS,
        block: suspend () -> T
    ): T {
        val span = startSpan(name)
        return try {
            block()
        } finally {
            span.end(thresholdMs)
        }
    }
}
