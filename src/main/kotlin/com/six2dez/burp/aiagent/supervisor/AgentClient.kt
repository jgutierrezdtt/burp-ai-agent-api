package com.six2dez.burp.aiagent.supervisor

import com.six2dez.burp.aiagent.redact.PrivacyMode

/**
 * Thin interface to abstract sending prompts to an AI agent.
 */
interface AgentClient {
    /**
     * Send a prompt and return the full response text or an error.
     */
    fun sendSync(prompt: String, contextJson: String?, privacyMode: PrivacyMode, deterministic: Boolean, timeoutMs: Long = 10_000L): Result<String>
}
