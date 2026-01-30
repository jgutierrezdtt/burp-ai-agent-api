package com.six2dez.burp.aiagent.context.openapi

import com.fasterxml.jackson.annotation.JsonInclude
import com.six2dez.burp.aiagent.context.BurpContextItem

/**
 * Represents a complete OpenAPI specification with all extracted metadata.
 */
data class OpenApiSpec(
    val version: String,
    val title: String,
    val description: String? = null,
    val servers: List<String> = emptyList(),
    val endpoints: List<ApiEndpoint> = emptyList(),
    val schemas: Map<String, SchemaDefinition> = emptyMap(),
    val securitySchemes: Map<String, SecurityScheme> = emptyMap()
)

/**
 * Represents a single API endpoint (path + method).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiEndpoint(
    val path: String,
    val method: String,
    val operationId: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val parameters: List<ApiParameter> = emptyList(),
    val requestBody: RequestBodyDefinition? = null,
    val responses: Map<String, ResponseDefinition> = emptyMap(),
    val security: List<Map<String, List<String>>> = emptyList(),
    val tags: List<String> = emptyList(),
    val deprecated: Boolean = false,
    val dataSensitivity: SensitivityLevel = SensitivityLevel.LOW,
    val classification: List<DataClassification> = emptyList()
)

/**
 * Represents a parameter in an API endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiParameter(
    val name: String,
    val location: ParameterLocation,
    val required: Boolean = false,
    val description: String? = null,
    val schema: SchemaDefinition,
    val deprecated: Boolean = false,
    val classification: DataClassification? = null
)

enum class ParameterLocation {
    QUERY, PATH, HEADER, COOKIE
}

/**
 * Represents a request body definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RequestBodyDefinition(
    val description: String? = null,
    val required: Boolean = false,
    val content: Map<String, MediaTypeDefinition> = emptyMap()
)

/**
 * Represents a response definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResponseDefinition(
    val description: String? = null,
    val content: Map<String, MediaTypeDefinition> = emptyMap()
)

/**
 * Represents a media type (e.g., application/json).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MediaTypeDefinition(
    val schema: SchemaDefinition? = null,
    val examples: Map<String, Any>? = null
)

/**
 * Represents a schema definition from OpenAPI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SchemaDefinition(
    val type: String? = null,
    val format: String? = null,
    val description: String? = null,
    val properties: Map<String, SchemaDefinition>? = null,
    val required: List<String>? = null,
    val items: SchemaDefinition? = null,
    val enum: List<Any>? = null,
    val pattern: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val minimum: Number? = null,
    val maximum: Number? = null,
    val ref: String? = null,
    val nullable: Boolean? = null,
    val readOnly: Boolean? = null,
    val writeOnly: Boolean? = null
)

/**
 * Represents a security scheme from OpenAPI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SecurityScheme(
    val type: String,
    val scheme: String? = null,
    val bearerFormat: String? = null,
    val flows: OAuthFlows? = null,
    val openIdConnectUrl: String? = null,
    val name: String? = null,
    val location: String? = null
)

/**
 * Represents OAuth flows.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OAuthFlows(
    val implicit: OAuthFlow? = null,
    val password: OAuthFlow? = null,
    val clientCredentials: OAuthFlow? = null,
    val authorizationCode: OAuthFlow? = null
)

/**
 * Represents a single OAuth flow.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OAuthFlow(
    val authorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val refreshUrl: String? = null,
    val scopes: Map<String, String> = emptyMap()
)

/**
 * Classification of a data field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DataClassification(
    val fieldPath: String,
    val category: DataCategory,
    val sensitivity: SensitivityLevel,
    val reason: String
)

/**
 * Categories of data.
 */
enum class DataCategory {
    PII,         // Personal Identifiable Information (email, phone, SSN)
    AUTH,        // Authentication data (passwords, tokens, API keys)
    FINANCIAL,   // Financial data (credit cards, bank accounts)
    ADMIN,       // Admin/privileged operations
    IDENTIFIER,  // IDs, UUIDs
    PUBLIC,      // Public non-sensitive data
    UNKNOWN      // Could not classify
}

/**
 * Sensitivity levels for data classification.
 */
enum class SensitivityLevel {
    CRITICAL,    // Passwords, tokens, credit cards
    HIGH,        // PII (email, phone, SSN)
    MEDIUM,      // User IDs, session data
    LOW          // Public metadata
}

/**
 * Note: ApiSpecItem moved to ContextModels.kt to satisfy sealed interface package requirement.
 */

/**
 * Role extracted from API specification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiRole(
    val name: String,
    val description: String? = null,
    val allowedEndpoints: Set<String> = emptySet(),
    val scopes: Set<String> = emptySet()
)

/**
 * Flow representing a sequence of API calls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiFlow(
    val name: String,
    val description: String? = null,
    val steps: List<FlowStep> = emptyList(),
    val requiredRoles: Set<String> = emptySet(),
    val dataFlow: List<DataDependency> = emptyList()
)

/**
 * Single step in an API flow.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FlowStep(
    val endpoint: ApiEndpoint,
    val stepNumber: Int,
    val inputFromPreviousStep: List<String>? = null,
    val outputToNextStep: List<String>? = null
)

/**
 * Data dependency between flow steps.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DataDependency(
    val fromStep: Int,
    val toStep: Int,
    val dataField: String,
    val required: Boolean = false
)
