package com.six2dez.burp.aiagent.context.openapi

import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.supervisor.AgentClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FakeAgentClient(private val response: String) : AgentClient {
    override fun sendSync(prompt: String, contextJson: String?, privacyMode: PrivacyMode, deterministic: Boolean, timeoutMs: Long): Result<String> {
        return Result.success(response)
    }
}

class DataClassifierTest {
    @Test
    fun `ai classification parsed correctly`() {
        val resourceStream = javaClass.getResourceAsStream("/golden/data-classification.json")
            ?: throw IllegalStateException("Golden file not found")
        val fakeResponse = resourceStream.bufferedReader().use { it.readText() }

        val client = FakeAgentClient(fakeResponse)
        val classifier = DataClassifier(agentClient = client)

        val spec = OpenApiSpec(
            version = "3.0.0",
            title = "Test API",
            endpoints = listOf(
                ApiEndpoint(path = "/users", method = "POST", parameters = listOf())
            )
        )

        val result = classifier.classifySpecWithAi(spec, PrivacyMode.BALANCED, deterministic = true)
        assertEquals(3, result.size)
        assertEquals("/users.POST.body.email", result[0].fieldPath)
        assertEquals(DataCategory.PII, result[0].category)
        assertEquals(SensitivityLevel.HIGH, result[0].sensitivity)
    }
}
package com.six2dez.burp.aiagent.context.openapi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataClassifierTest {
    
    private val classifier = DataClassifier()
    
    @Test
    fun `classify authentication fields`() {
        val param = ApiParameter(
            name = "password",
            location = ParameterLocation.QUERY,
            schema = SchemaDefinition(type = "string", format = "password")
        )
        
        val classification = classifier.classifyParameter(param)
        
        assertNotNull(classification)
        assertEquals(DataCategory.AUTH, classification!!.category)
        assertEquals(SensitivityLevel.CRITICAL, classification.sensitivity)
    }
    
    @Test
    fun `classify PII email field`() {
        val param = ApiParameter(
            name = "email",
            location = ParameterLocation.QUERY,
            schema = SchemaDefinition(type = "string", format = "email")
        )
        
        val classification = classifier.classifyParameter(param)
        
        assertNotNull(classification)
        assertEquals(DataCategory.PII, classification!!.category)
        assertEquals(SensitivityLevel.HIGH, classification.sensitivity)
        assertTrue(classification.reason.contains("Email format") || classification.reason.contains("PII"))
    }
    
    @Test
    fun `classify financial fields`() {
        val param = ApiParameter(
            name = "creditCard",
            location = ParameterLocation.QUERY,
            schema = SchemaDefinition(type = "string")
        )
        
        val classification = classifier.classifyParameter(param)
        
        assertNotNull(classification)
        assertEquals(DataCategory.FINANCIAL, classification!!.category)
        assertEquals(SensitivityLevel.CRITICAL, classification.sensitivity)
    }
    
    @Test
    fun `classify admin fields`() {
        val param = ApiParameter(
            name = "role",
            location = ParameterLocation.QUERY,
            schema = SchemaDefinition(type = "string")
        )
        
        val classification = classifier.classifyParameter(param)
        
        assertNotNull(classification)
        assertEquals(DataCategory.ADMIN, classification!!.category)
        assertEquals(SensitivityLevel.HIGH, classification.sensitivity)
    }
    
    @Test
    fun `classify identifier fields`() {
        val param = ApiParameter(
            name = "userId",
            location = ParameterLocation.PATH,
            schema = SchemaDefinition(type = "string", format = "uuid")
        )
        
        val classification = classifier.classifyParameter(param)
        
        assertNotNull(classification)
        assertEquals(DataCategory.IDENTIFIER, classification!!.category)
        assertEquals(SensitivityLevel.MEDIUM, classification.sensitivity)
    }
    
    @Test
    fun `classify unknown field returns null`() {
        val param = ApiParameter(
            name = "someRandomField",
            location = ParameterLocation.QUERY,
            schema = SchemaDefinition(type = "string")
        )
        
        val classification = classifier.classifyParameter(param)
        
        assertNull(classification)
    }
    
    @Test
    fun `classify endpoint with mixed sensitivity`() {
        val endpoint = ApiEndpoint(
            path = "/users",
            method = "POST",
            parameters = listOf(
                ApiParameter(
                    name = "email",
                    location = ParameterLocation.QUERY,
                    schema = SchemaDefinition(type = "string", format = "email")
                ),
                ApiParameter(
                    name = "password",
                    location = ParameterLocation.QUERY,
                    schema = SchemaDefinition(type = "string", format = "password")
                ),
                ApiParameter(
                    name = "name",
                    location = ParameterLocation.QUERY,
                    schema = SchemaDefinition(type = "string")
                )
            )
        )
        
        val classified = classifier.classifyEndpoint(endpoint, emptyMap())
        
        // Should have CRITICAL sensitivity due to password field
        assertEquals(SensitivityLevel.CRITICAL, classified.dataSensitivity)
        
        // Should have at least 2 classifications (email + password)
        assertTrue(classified.classification.size >= 2)
        
        assertTrue(classified.classification.any { it.category == DataCategory.AUTH })
        assertTrue(classified.classification.any { it.category == DataCategory.PII })
    }
    
    @Test
    fun `classify schema with nested properties`() {
        val schema = SchemaDefinition(
            type = "object",
            properties = mapOf(
                "user" to SchemaDefinition(
                    type = "object",
                    properties = mapOf(
                        "email" to SchemaDefinition(type = "string", format = "email"),
                        "password" to SchemaDefinition(type = "string", format = "password"),
                        "profile" to SchemaDefinition(
                            type = "object",
                            properties = mapOf(
                                "phone" to SchemaDefinition(type = "string")
                            )
                        )
                    )
                )
            )
        )
        
        val classifications = classifier.classifySchema(schema, "", emptyMap())
        
        assertTrue(classifications.size >= 3)
        assertTrue(classifications.any { it.fieldPath == "user.email" && it.category == DataCategory.PII })
        assertTrue(classifications.any { it.fieldPath == "user.password" && it.category == DataCategory.AUTH })
        assertTrue(classifications.any { it.fieldPath == "user.profile.phone" && it.category == DataCategory.PII })
    }
    
    @Test
    fun `classify complete spec`() {
        val spec = OpenApiSpec(
            version = "3.0.0",
            title = "Test API",
            endpoints = listOf(
                ApiEndpoint(
                    path = "/login",
                    method = "POST",
                    parameters = listOf(
                        ApiParameter(
                            name = "username",
                            location = ParameterLocation.QUERY,
                            schema = SchemaDefinition(type = "string")
                        ),
                        ApiParameter(
                            name = "password",
                            location = ParameterLocation.QUERY,
                            schema = SchemaDefinition(type = "string", format = "password")
                        )
                    )
                ),
                ApiEndpoint(
                    path = "/users/{id}",
                    method = "GET",
                    parameters = listOf(
                        ApiParameter(
                            name = "id",
                            location = ParameterLocation.PATH,
                            schema = SchemaDefinition(type = "string")
                        )
                    )
                )
            )
        )
        
        val classifiedSpec = classifier.classifySpec(spec)
        
        assertEquals(2, classifiedSpec.endpoints.size)
        
        val loginEndpoint = classifiedSpec.endpoints.find { it.path == "/login" }
        assertNotNull(loginEndpoint)
        assertEquals(SensitivityLevel.CRITICAL, loginEndpoint!!.dataSensitivity)
        
        val getUserEndpoint = classifiedSpec.endpoints.find { it.path == "/users/{id}" }
        assertNotNull(getUserEndpoint)
        // ID is classified as IDENTIFIER with MEDIUM sensitivity
        assertEquals(SensitivityLevel.MEDIUM, getUserEndpoint!!.dataSensitivity)
    }
    
    @Test
    fun `various auth field names are detected`() {
        val authFields = listOf("password", "token", "apiKey", "api_key", "secret", "jwt", "bearer", "authorization")
        
        authFields.forEach { fieldName ->
            val param = ApiParameter(
                name = fieldName,
                location = ParameterLocation.HEADER,
                schema = SchemaDefinition(type = "string")
            )
            
            val classification = classifier.classifyParameter(param)
            
            assertNotNull(classification, "Field $fieldName should be classified")
            assertEquals(DataCategory.AUTH, classification!!.category, "Field $fieldName should be AUTH category")
        }
    }
    
    @Test
    fun `various PII field names are detected`() {
        val piiFields = listOf("email", "phone", "mobile", "address", "ssn", "dob", "birthdate")
        
        piiFields.forEach { fieldName ->
            val param = ApiParameter(
                name = fieldName,
                location = ParameterLocation.QUERY,
                schema = SchemaDefinition(type = "string")
            )
            
            val classification = classifier.classifyParameter(param)
            
            assertNotNull(classification, "Field $fieldName should be classified")
            assertEquals(DataCategory.PII, classification!!.category, "Field $fieldName should be PII category")
        }
    }
    
    // TODO: Añadir tests de lógica de negocio para flujos multi-step y validación de estados.
    // Referencia: Security.md (sección 10), vulns.md (vulnerabilidad 5)
}
