package com.six2dez.burp.aiagent.util

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Simple in-memory token-bucket rate limiter.
 * Keyed by arbitrary string (e.g., "global" or user id).
 * Not distributed — intended for UI/process-level protection.
 */
class RateLimiter(
    private val capacity: Int = 5,
    private val refillTokens: Int = 5,
    private val refillIntervalMillis: Long = 60_000L
) {
    private data class Bucket(var tokens: Int, var lastRefill: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryAcquire(key: String, permits: Int = 1): Boolean {
        val now = System.currentTimeMillis()
        val bucket = buckets.computeIfAbsent(key) { Bucket(capacity, now) }

        synchronized(bucket) {
            // Refill logic
            val elapsed = now - bucket.lastRefill
            if (elapsed >= refillIntervalMillis) {
                val periods = elapsed / refillIntervalMillis
                val refill = (periods * refillTokens).toInt()
                bucket.tokens = min(capacity, bucket.tokens + refill)
                bucket.lastRefill = now
            }

            return if (bucket.tokens >= permits) {
                bucket.tokens -= permits
                true
            } else {
                false
            }
        }
    }

    fun reset(key: String) {
        buckets.remove(key)
    }
}
