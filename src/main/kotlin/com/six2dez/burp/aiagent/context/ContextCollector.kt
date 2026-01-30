package com.six2dez.burp.aiagent.context

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.scanner.audit.issues.AuditIssue
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.six2dez.burp.aiagent.context.openapi.OpenApiSpec
import com.six2dez.burp.aiagent.context.openapi.DataClassifier
import com.six2dez.burp.aiagent.redact.Redaction
import com.six2dez.burp.aiagent.redact.RedactionPolicy
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ContextCollector(private val api: MontoyaApi) {
    private val mapper = JsonMapper.builder()
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .build()
        .registerKotlinModule()
    
    private val dataClassifier = DataClassifier()

    fun fromRequestResponses(rr: List<HttpRequestResponse>, options: ContextOptions): ContextCapture {
        // TODO: Revisar que la redacción y anonimización se apliquen siempre antes de loggear o enviar datos a la IA.
        // Referencia: Security.md (sección 3 y 5), vulns.md (vulnerabilidad 3 y 7)
        val policy = RedactionPolicy.fromMode(options.privacyMode)
        val items = rr.map { item ->
            val req = item.request().toString()
            val resp = item.response()?.toString()

            val redactedReq = Redaction.apply(req, policy, stableHostSalt = options.hostSalt)
            val redactedResp = resp?.let { Redaction.apply(it, policy, stableHostSalt = options.hostSalt) }

            HttpItem(
                tool = null,
                url = item.request().url(),
                method = item.request().method(),
                request = redactedReq,
                response = redactedResp
            )
        }.let { list ->
            if (options.deterministic) list.sortedBy { stableKey(it) } else list
        }

        val env = BurpContextEnvelope(
            capturedAtEpochMs = System.currentTimeMillis(),
            items = items
        )

        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(env)
        val preview = buildPreview(items.size, "HTTP selection", policy, options.deterministic)

        return ContextCapture(contextJson = json, previewText = preview)
    }

    fun fromAuditIssues(issues: List<AuditIssue>, options: ContextOptions): ContextCapture {
        // TODO: Añadir validación de roles y permisos antes de procesar contextos sensibles.
        // Referencia: Security.md (sección 2), vulns.md (vulnerabilidad 2)
        val policy = RedactionPolicy.fromMode(options.privacyMode)
        val items = issues.map { i ->
            val host = i.httpService()?.host()
            AuditIssueItem(
                name = i.name(),
                severity = i.severity()?.name,
                confidence = i.confidence()?.name,
                detail = i.detail(),
                remediation = i.remediation(),
                affectedHost = host?.let {
                    if (policy.anonymizeHosts) Redaction.anonymizeHost(it, options.hostSalt) else it
                }
            )
        }.let { list ->
            if (options.deterministic) list.sortedBy { stableKey(it) } else list
        }

        val env = BurpContextEnvelope(
            capturedAtEpochMs = System.currentTimeMillis(),
            items = items
        )

        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(env)
        val preview = buildPreview(items.size, "Scanner findings", policy, options.deterministic)

        return ContextCapture(contextJson = json, previewText = preview)
    }

    private fun buildPreview(count: Int, kind: String, policy: RedactionPolicy, deterministic: Boolean): String {
        return """
            Kind: $kind
            Items: $count
            Redaction:
              - Cookie stripping: ${policy.stripCookies}
              - Token redaction: ${policy.redactTokens}
              - Host anonymization: ${policy.anonymizeHosts}
            Deterministic: $deterministic
        """.trimIndent()
    }

    private fun stableKey(item: HttpItem): String {
        val base = listOf(item.url, item.method, hashOf(item.request)).joinToString("|")
        return base
    }

    private fun stableKey(item: AuditIssueItem): String {
        val base = listOf(item.name, item.severity, item.affectedHost, hashOf(item.detail ?: "")).joinToString("|")
        return base
    }
    
    /**
     * Create context capture from OpenAPI specification.
     */
    fun fromApiSpec(spec: OpenApiSpec, options: ContextOptions): ContextCapture {
        // Apply data classification
        val classifiedSpec = dataClassifier.classifySpec(spec)
        
        // Wrap in ApiSpecItem
        val item = ApiSpecItem(
            spec = classifiedSpec,
            analysisTimestamp = System.currentTimeMillis()
        )
        
        val env = BurpContextEnvelope(
            capturedAtEpochMs = System.currentTimeMillis(),
            items = listOf(item)
        )
        
        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(env)
        val preview = buildApiSpecPreview(classifiedSpec, options.deterministic)
        
        return ContextCapture(contextJson = json, previewText = preview)
    }
    
    private fun buildApiSpecPreview(spec: OpenApiSpec, deterministic: Boolean): String {
        val endpointCount = spec.endpoints.size
        val sensitiveCount = spec.endpoints.count { it.dataSensitivity.ordinal >= com.six2dez.burp.aiagent.context.openapi.SensitivityLevel.HIGH.ordinal }
        val authRequired = spec.endpoints.count { it.security.isNotEmpty() }
        
        return """
            Kind: OpenAPI Specification
            Title: ${spec.title}
            Version: ${spec.version}
            Endpoints: $endpointCount
            Sensitive endpoints: $sensitiveCount
            Auth required: $authRequired
            Deterministic: $deterministic
        """.trimIndent()
    }

    private fun hashOf(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
