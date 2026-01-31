package com.six2dez.burp.aiagent.context.openapi

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.six2dez.burp.aiagent.supervisor.AgentClient
import com.six2dez.burp.aiagent.redact.PrivacyMode
import java.time.Instant

/**
 * DataClassifier delegates classification to an AI agent via AgentClient.
 * It builds a deterministic, redacted context and parses a JSON response with classifications.
 */
class DataClassifier(private val agentClient: AgentClient? = null) {
    private val mapper = JsonMapper.builder()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .build()
        .registerKotlinModule()

    data class ClassificationRequest(
        val requestId: String,
        val specSummary: OpenApiSpecSummary
    )

    data class OpenApiSpecSummary(
        val title: String,
        val version: String,
        val endpoints: List<EndpointSummary>
    )

    data class EndpointSummary(val path: String, val method: String, val parameters: List<String>)

    /**
     * Ask the agent to classify fields in the spec. Returns an empty list on error or if no agentClient is provided.
     */
    fun classifySpecWithAi(spec: OpenApiSpec, privacyMode: PrivacyMode, deterministic: Boolean, timeoutMs: Long = 10_000L): List<DataClassification> {
        if (agentClient == null) return emptyList()

        val summary = OpenApiSpecSummary(
            title = spec.title,
            version = spec.version,
            endpoints = spec.endpoints.map { ep -> EndpointSummary(ep.path, ep.method, ep.parameters.map { it.name }) }
        )

        val req = ClassificationRequest(requestId = "req-${Instant.now().toEpochMilli()}", specSummary = summary)
        val contextJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(req)

        val prompt = buildString {
            appendLine("ANALYZE_SPEC_FOR_DATA_CLASSIFICATION")
            appendLine("Respond with JSON array of objects: {fieldPath, category, sensitivity, reason, confidence}")
            appendLine("Only output valid JSON.")
        }

        val res = agentClient.sendSync(prompt = prompt, contextJson = contextJson, privacyMode = privacyMode, deterministic = deterministic, timeoutMs = timeoutMs)
        if (res.isFailure) return emptyList()

        val text = res.getOrNull().orEmpty().trim()
        return try {
            // Expecting a JSON array
            mapper.readValue(text, mapper.typeFactory.constructCollectionType(List::class.java, DataClassification::class.java))
        } catch (e: Exception) {
            emptyList()
        }
    }
}
package com.six2dez.burp.aiagent.context.openapi

/**
 * Classifier for API data fields and endpoints.
 * Uses deterministic rules to identify sensitive data categories.
 */
class DataClassifier {
    
    /**
     * Classify an entire API specification.
     */
    fun classifySpec(spec: OpenApiSpec): OpenApiSpec {
        val classifiedEndpoints = spec.endpoints.map { endpoint ->
            classifyEndpoint(endpoint, spec.schemas)
        }
        
        return spec.copy(endpoints = classifiedEndpoints)
    }
    
    /**
     * Classify a single endpoint.
     */
    fun classifyEndpoint(endpoint: ApiEndpoint, schemas: Map<String, SchemaDefinition>): ApiEndpoint {
        val parameterClassifications = endpoint.parameters.map { param ->
            param.copy(classification = classifyParameter(param))
        }
        
        // Classify request body fields
        val requestBodyClassifications = endpoint.requestBody?.let { body ->
            body.content.values.flatMap { mediaType ->
                mediaType.schema?.let { schema ->
                    classifySchema(schema, "", schemas)
                } ?: emptyList()
            }
        } ?: emptyList()
        
        // Combine all classifications
        val allClassifications = parameterClassifications.mapNotNull { it.classification } + requestBodyClassifications
        
        // Determine overall endpoint sensitivity
        val maxSensitivity = allClassifications.maxOfOrNull { it.sensitivity } ?: SensitivityLevel.LOW
        
        return endpoint.copy(
            parameters = parameterClassifications,
            dataSensitivity = maxSensitivity,
            classification = allClassifications
        )
    }
    
    /**
     * Classify a parameter.
     */
    fun classifyParameter(param: ApiParameter): DataClassification? {
        val fieldPath = param.name
        val category = determineCategory(param.name, param.schema)
        val sensitivity = determineSensitivity(category)
        val reason = buildReason(param.name, param.schema, category)
        
        return if (category != DataCategory.UNKNOWN) {
            DataClassification(fieldPath, category, sensitivity, reason)
        } else {
            null
        }
    }
    
    /**
     * Classify fields in a schema recursively.
     */
    fun classifySchema(
        schema: SchemaDefinition,
        basePath: String,
        schemas: Map<String, SchemaDefinition>,
        depth: Int = 0
    ): List<DataClassification> {
        if (depth > 5) return emptyList() // Prevent infinite recursion
        
        val classifications = mutableListOf<DataClassification>()
        
        // Handle object with properties
        schema.properties?.forEach { (propName, propSchema) ->
            val fieldPath = if (basePath.isEmpty()) propName else "$basePath.$propName"
            val category = determineCategory(propName, propSchema)
            val sensitivity = determineSensitivity(category)
            
            if (category != DataCategory.UNKNOWN) {
                val reason = buildReason(propName, propSchema, category)
                classifications.add(DataClassification(fieldPath, category, sensitivity, reason))
            }
            
            // Recurse into nested objects
            if (propSchema.type == "object" && propSchema.properties != null) {
                classifications.addAll(classifySchema(propSchema, fieldPath, schemas, depth + 1))
            }
        }
        
        return classifications
    }
    
    /**
     * Determine the category of a field based on name and schema.
     */
    private fun determineCategory(fieldName: String, schema: SchemaDefinition): DataCategory {
        val name = fieldName.lowercase()
        
        // Auth patterns
        if (authPatterns.any { name.contains(it) }) {
            return DataCategory.AUTH
        }
        
        // Schema format-based classification
        schema.format?.let { format ->
            when (format.lowercase()) {
                "password" -> return DataCategory.AUTH
                "email" -> return DataCategory.PII
                "uuid", "uri" -> return DataCategory.IDENTIFIER
            }
        }
        
        // PII patterns
        if (piiPatterns.any { name.contains(it) }) {
            return DataCategory.PII
        }
        
        // Financial patterns
        if (financialPatterns.any { name.contains(it) }) {
            return DataCategory.FINANCIAL
        }
        
        // Admin patterns
        if (adminPatterns.any { name.contains(it) }) {
            return DataCategory.ADMIN
        }
        
        // Identifier patterns
        if (identifierPatterns.any { name.contains(it) }) {
            return DataCategory.IDENTIFIER
        }
        
        // Content validation patterns
        if (schema.pattern != null) {
            emailRegex.find(schema.pattern)?.let { return DataCategory.PII }
            phoneRegex.find(schema.pattern)?.let { return DataCategory.PII }
            ssnRegex.find(schema.pattern)?.let { return DataCategory.PII }
            creditCardRegex.find(schema.pattern)?.let { return DataCategory.FINANCIAL }
        }
        
        return DataCategory.UNKNOWN
    }
    
    /**
     * Determine sensitivity level based on category.
     */
    private fun determineSensitivity(category: DataCategory): SensitivityLevel {
        return when (category) {
            DataCategory.AUTH -> SensitivityLevel.CRITICAL
            DataCategory.FINANCIAL -> SensitivityLevel.CRITICAL
            DataCategory.PII -> SensitivityLevel.HIGH
            DataCategory.ADMIN -> SensitivityLevel.HIGH
            DataCategory.IDENTIFIER -> SensitivityLevel.MEDIUM
            DataCategory.PUBLIC -> SensitivityLevel.LOW
            DataCategory.UNKNOWN -> SensitivityLevel.LOW
        }
    }
    
    /**
     * Build a human-readable reason for the classification.
     */
    private fun buildReason(fieldName: String, schema: SchemaDefinition, category: DataCategory): String {
        val reasons = mutableListOf<String>()
        
        when (category) {
            DataCategory.AUTH -> reasons.add("Authentication field pattern")
            DataCategory.PII -> {
                if (schema.format == "email") {
                    reasons.add("Email format")
                } else {
                    reasons.add("PII field pattern")
                }
            }
            DataCategory.FINANCIAL -> reasons.add("Financial data pattern")
            DataCategory.ADMIN -> reasons.add("Administrative field pattern")
            DataCategory.IDENTIFIER -> reasons.add("Identifier pattern")
            else -> {}
        }
        
        if (schema.format != null) {
            reasons.add("format: ${schema.format}")
        }
        
        return if (reasons.isEmpty()) {
            "Matched field name pattern: $fieldName"
        } else {
            reasons.joinToString(", ")
        }
    }
    
    companion object {
        // Authentication patterns
        private val authPatterns = setOf(
            "password", "passwd", "pwd",
            "token", "jwt", "bearer",
            "secret", "api_key", "apikey", "api-key",
            "authorization", "auth",
            "credential", "session", "cookie"
        )
        
        // PII patterns
        private val piiPatterns = setOf(
            "email", "mail",
            "phone", "mobile", "tel", "telephone",
            "address", "street", "city", "zip", "postal",
            "ssn", "social_security",
            "dob", "birthdate", "birth_date", "dateofbirth",
            "name", "firstname", "lastname", "fullname",
            "passport", "license", "driver"
        )
        
        // Financial patterns
        private val financialPatterns = setOf(
            "card", "credit", "debit",
            "cvv", "cvc", "security_code",
            "account", "iban", "routing",
            "payment", "billing",
            "price", "amount", "balance"
        )
        
        // Admin patterns
        private val adminPatterns = setOf(
            "admin", "administrator",
            "role", "permission", "scope",
            "privilege", "access_level",
            "sudo", "root"
        )
        
        // Identifier patterns
        private val identifierPatterns = setOf(
            "id", "uuid", "guid",
            "identifier", "key",
            "reference", "ref"
        )
        
        // Regex patterns for content validation
        private val emailRegex = Regex(
            """[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}""",
            RegexOption.IGNORE_CASE
        )
        
        private val phoneRegex = Regex(
            """\d{3}[-.]?\d{3}[-.]?\d{4}""",
            RegexOption.IGNORE_CASE
        )
        
        private val ssnRegex = Regex(
            """\d{3}-\d{2}-\d{4}""",
            RegexOption.IGNORE_CASE
        )
        
        private val creditCardRegex = Regex(
            """\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}""",
            RegexOption.IGNORE_CASE
        )
    }
}
