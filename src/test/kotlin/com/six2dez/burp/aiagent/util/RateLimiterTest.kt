package com.six2dez.burp.aiagent.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RateLimiterTest {

    @Test
    fun `acquire tokens and refill`() {
        val limiter = RateLimiter(capacity = 2, refillTokens = 2, refillIntervalMillis = 200)
        val key = "test-user"

        // consume available tokens
        assertTrue(limiter.tryAcquire(key))
        assertTrue(limiter.tryAcquire(key))
        // now exhausted
        assertFalse(limiter.tryAcquire(key))

        // wait for refill
        Thread.sleep(250)
        // should refill tokens
        assertTrue(limiter.tryAcquire(key))
    }

    @Test
    fun `reset clears bucket`() {
        val limiter = RateLimiter(capacity = 1, refillTokens = 1, refillIntervalMillis = 1000)
        val key = "reset-user"
        assertTrue(limiter.tryAcquire(key))
        assertFalse(limiter.tryAcquire(key))
        limiter.reset(key)
        assertTrue(limiter.tryAcquire(key))
    }
}
