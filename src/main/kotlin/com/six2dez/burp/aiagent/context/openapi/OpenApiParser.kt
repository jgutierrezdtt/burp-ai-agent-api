package com.six2dez.burp.aiagent.context.openapi

import com.six2dez.burp.aiagent.backends.BackendDiagnostics
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.SecurityScheme as SwaggerSecurityScheme
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import java.io.File
import java.net.URL

/**
 * Parser for OpenAPI specifications (v3.0 and v3.1).
 * Supports loading from file paths or URLs.
 */
class OpenApiParser {
    
    /**
     * Parse OpenAPI spec from a local file path.
     */
    fun parseFromFile(filePath: String): Result<OpenApiSpec> {
        // TODO: Validar estructura y autenticidad de payloads externos antes de procesar.
        // Referencia: Security.md (sección 9), vulns.md (vulnerabilidad 6)
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("File not found: $filePath"))
            }
            parse(file.absolutePath)
        } catch (e: Exception) {
            BackendDiagnostics.logError("Failed to parse OpenAPI from file: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Parse OpenAPI spec from a URL.
     */
    fun parseFromUrl(url: String): Result<OpenApiSpec> {
        return try {
            // Validate URL format
            URL(url)
            parse(url)
        } catch (e: Exception) {
            BackendDiagnostics.logError("Failed to parse OpenAPI from URL: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Internal parse method that handles both file and URL sources.
     */
    private fun parse(location: String): Result<OpenApiSpec> {
        return try {
            val parseOptions = ParseOptions().apply {
                isResolve = true
                isResolveFully = true
            }
            
            val parseResult = OpenAPIV3Parser().readLocation(location, null, parseOptions)
            
            if (parseResult.messages.isNotEmpty()) {
                val errors = parseResult.messages.filter { it.contains("error", ignoreCase = true) }
                if (errors.isNotEmpty()) {
                    BackendDiagnostics.logError("OpenAPI parse errors: ${errors.joinToString(", ")}")
                }
            }
            
            val openApi = parseResult.openAPI
                ?: return Result.failure(IllegalArgumentException("Failed to parse OpenAPI spec: ${parseResult.messages.joinToString(", ")}"))
            
            val spec = convertToApiSpec(openApi)
            Result.success(spec)
        } catch (e: Exception) {
            BackendDiagnostics.logError("OpenAPI parsing exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Convert Swagger OpenAPI model to our internal model.
     */
    private fun convertToApiSpec(openApi: OpenAPI): OpenApiSpec {
        val info = openApi.info
        val servers = openApi.servers?.mapNotNull { it.url } ?: emptyList()
        val endpoints = extractEndpoints(openApi)
        val schemas = extractSchemas(openApi)
        val securitySchemes = extractSecuritySchemes(openApi)
        
        return OpenApiSpec(
            version = openApi.openapi ?: "3.0.0",
            title = info?.title ?: "Untitled API",
            description = info?.description,
            servers = servers,
            endpoints = endpoints,
            schemas = schemas,
            securitySchemes = securitySchemes
        )
    }
    
    /**
     * Extract all endpoints from paths.
     */
    private fun extractEndpoints(openApi: OpenAPI): List<ApiEndpoint> {
        val endpoints = mutableListOf<ApiEndpoint>()
        val paths = openApi.paths ?: return endpoints
        
        for ((path, pathItem) in paths) {
            endpoints.addAll(extractOperations(path, pathItem))
        }
        
        return endpoints
    }
    
    /**
     * Extract operations (GET, POST, etc.) from a path item.
     */
    private fun extractOperations(path: String, pathItem: PathItem): List<ApiEndpoint> {
        val operations = mutableListOf<ApiEndpoint>()
        
        // Map of HTTP method to operation
        val methodOps = mapOf(
            "GET" to pathItem.get,
            "POST" to pathItem.post,
            "PUT" to pathItem.put,
            "DELETE" to pathItem.delete,
            "PATCH" to pathItem.patch,
            "HEAD" to pathItem.head,
            "OPTIONS" to pathItem.options,
            "TRACE" to pathItem.trace
        )
        
        for ((method, operation) in methodOps) {
            if (operation != null) {
                operations.add(convertOperation(path, method, operation, pathItem.parameters))
            }
        }
        
        return operations
    }
    
    /**
     * Convert a Swagger Operation to ApiEndpoint.
     */
    private fun convertOperation(
        path: String,
        method: String,
        operation: Operation,
        pathParameters: List<Parameter>?
    ): ApiEndpoint {
        val allParameters = mutableListOf<Parameter>()
        pathParameters?.let { allParameters.addAll(it) }
        operation.parameters?.let { allParameters.addAll(it) }
        
        val parameters = allParameters.map { convertParameter(it) }
        val requestBody = operation.requestBody?.let { convertRequestBody(it) }
        val responses = operation.responses?.mapNotNull { (code, response) ->
            code to ResponseDefinition(
                description = response.description,
                content = response.content?.mapValues { (_, mediaType) ->
                    MediaTypeDefinition(
                        schema = mediaType.schema?.let { convertSchema(it) }
                    )
                } ?: emptyMap()
            )
        }?.toMap() ?: emptyMap()
        
        val security = operation.security?.map { secReq ->
            secReq.mapValues { (_, scopes) -> scopes }
        } ?: emptyList()
        
        return ApiEndpoint(
            path = path,
            method = method,
            operationId = operation.operationId,
            summary = operation.summary,
            description = operation.description,
            parameters = parameters,
            requestBody = requestBody,
            responses = responses,
            security = security,
            tags = operation.tags ?: emptyList(),
            deprecated = operation.deprecated ?: false
        )
    }
    
    /**
     * Convert a Swagger Parameter to ApiParameter.
     */
    private fun convertParameter(param: Parameter): ApiParameter {
        val location = when (param.`in`) {
            "query" -> ParameterLocation.QUERY
            "path" -> ParameterLocation.PATH
            "header" -> ParameterLocation.HEADER
            "cookie" -> ParameterLocation.COOKIE
            else -> ParameterLocation.QUERY
        }
        
        return ApiParameter(
            name = param.name,
            location = location,
            required = param.required ?: false,
            description = param.description,
            schema = param.schema?.let { convertSchema(it) } ?: SchemaDefinition(type = "string"),
            deprecated = param.deprecated ?: false
        )
    }
    
    /**
     * Convert request body.
     */
    private fun convertRequestBody(requestBody: io.swagger.v3.oas.models.parameters.RequestBody): RequestBodyDefinition {
        return RequestBodyDefinition(
            description = requestBody.description,
            required = requestBody.required ?: false,
            content = requestBody.content?.mapValues { (_, mediaType) ->
                MediaTypeDefinition(
                    schema = mediaType.schema?.let { convertSchema(it) }
                )
            } ?: emptyMap()
        )
    }
    
    /**
     * Convert a Swagger Schema to SchemaDefinition.
     */
    private fun convertSchema(schema: Schema<*>): SchemaDefinition {
        return SchemaDefinition(
            type = schema.type,
            format = schema.format,
            description = schema.description,
            properties = schema.properties?.mapValues { (_, propSchema) ->
                convertSchema(propSchema)
            },
            required = schema.required,
            items = schema.items?.let { convertSchema(it) },
            enum = schema.enum,
            pattern = schema.pattern,
            minLength = schema.minLength,
            maxLength = schema.maxLength,
            minimum = schema.minimum,
            maximum = schema.maximum,
            ref = schema.`$ref`,
            nullable = schema.nullable,
            readOnly = schema.readOnly,
            writeOnly = schema.writeOnly
        )
    }
    
    /**
     * Extract schemas from components.
     */
    private fun extractSchemas(openApi: OpenAPI): Map<String, SchemaDefinition> {
        val schemas = openApi.components?.schemas ?: return emptyMap()
        return schemas.mapValues { (_, schema) -> convertSchema(schema) }
    }
    
    /**
     * Extract security schemes from components.
     */
    private fun extractSecuritySchemes(openApi: OpenAPI): Map<String, SecurityScheme> {
        val schemes = openApi.components?.securitySchemes ?: return emptyMap()
        return schemes.mapValues { (_, scheme) -> convertSecurityScheme(scheme) }
    }
    
    /**
     * Convert Swagger SecurityScheme to our model.
     */
    private fun convertSecurityScheme(scheme: SwaggerSecurityScheme): SecurityScheme {
        val flows = scheme.flows?.let { oauthFlows ->
            OAuthFlows(
                implicit = oauthFlows.implicit?.let {
                    OAuthFlow(
                        authorizationUrl = it.authorizationUrl,
                        refreshUrl = it.refreshUrl,
                        scopes = it.scopes ?: emptyMap()
                    )
                },
                password = oauthFlows.password?.let {
                    OAuthFlow(
                        tokenUrl = it.tokenUrl,
                        refreshUrl = it.refreshUrl,
                        scopes = it.scopes ?: emptyMap()
                    )
                },
                clientCredentials = oauthFlows.clientCredentials?.let {
                    OAuthFlow(
                        tokenUrl = it.tokenUrl,
                        refreshUrl = it.refreshUrl,
                        scopes = it.scopes ?: emptyMap()
                    )
                },
                authorizationCode = oauthFlows.authorizationCode?.let {
                    OAuthFlow(
                        authorizationUrl = it.authorizationUrl,
                        tokenUrl = it.tokenUrl,
                        refreshUrl = it.refreshUrl,
                        scopes = it.scopes ?: emptyMap()
                    )
                }
            )
        }
        
        return SecurityScheme(
            type = scheme.type?.toString() ?: "unknown",
            scheme = scheme.scheme,
            bearerFormat = scheme.bearerFormat,
            flows = flows,
            openIdConnectUrl = scheme.openIdConnectUrl,
            name = scheme.name,
            location = scheme.`in`?.toString()
        )
    }
}
