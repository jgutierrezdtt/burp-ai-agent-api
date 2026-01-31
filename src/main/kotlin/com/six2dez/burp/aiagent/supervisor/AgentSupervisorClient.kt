package com.six2dez.burp.aiagent.supervisor

import com.six2dez.burp.aiagent.redact.PrivacyMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentSupervisorClient(private val supervisor: AgentSupervisor) : AgentClient {
    override fun sendSync(prompt: String, contextJson: String?, privacyMode: PrivacyMode, deterministic: Boolean, timeoutMs: Long): Result<String> {
        val sb = StringBuilder()
        val latch = CountDownLatch(1)
        var err: Throwable? = null

        supervisor.send(
            text = prompt,
            contextJson = contextJson,
            privacyMode = privacyMode,
            determinismMode = deterministic,
            onChunk = { chunk -> sb.append(chunk) },
            onComplete = { throwable ->
                err = throwable
                latch.countDown()
            }
        )

        val ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return if (!ok) {
            Result.failure(TimeoutException("AI request timed out after ${timeoutMs}ms"))
        } else if (err != null) {
            Result.failure(err!!)
        } else {
            Result.success(sb.toString())
        }
    }
}

class TimeoutException(message: String) : Exception(message)
