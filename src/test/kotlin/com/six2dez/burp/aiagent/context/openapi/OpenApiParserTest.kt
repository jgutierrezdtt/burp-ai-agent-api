package com.six2dez.burp.aiagent.context.openapi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiParserTest {
    
    private val parser = OpenApiParser()
    
    @Test
    fun `parse simple OpenAPI spec with single endpoint`(@TempDir tempDir: Path) {
        val specYaml = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /users:
                get:
                  summary: Get all users
                  responses:
                    '200':
                      description: Success
        """.trimIndent()
        
        val specFile = tempDir.resolve("openapi.yaml")
        specFile.writeText(specYaml)
        
        val result = parser.parseFromFile(specFile.toString())
        
        assertTrue(result.isSuccess)
        val spec = result.getOrThrow()
        
        assertEquals("3.0.0", spec.version)
        assertEquals("Test API", spec.title)
        assertEquals(1, spec.endpoints.size)
        
        val endpoint = spec.endpoints.first()
        assertEquals("/users", endpoint.path)
        assertEquals("GET", endpoint.method)
        assertEquals("Get all users", endpoint.summary)
    }
    
    @Test
    fun `parse spec with parameters`(@TempDir tempDir: Path) {
        val specYaml = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /users/{userId}:
                get:
                  summary: Get user by ID
                  parameters:
                    - name: userId
                      in: path
                      required: true
                      schema:
                        type: integer
                    - name: includeDetails
                      in: query
                      required: false
                      schema:
                        type: boolean
                  responses:
                    '200':
                      description: Success
        """.trimIndent()
        
        val specFile = tempDir.resolve("openapi.yaml")
        specFile.writeText(specYaml)
        
        val result = parser.parseFromFile(specFile.toString())
        
        assertTrue(result.isSuccess)
        val spec = result.getOrThrow()
        
        val endpoint = spec.endpoints.first()
        assertEquals(2, endpoint.parameters.size)
        
        val pathParam = endpoint.parameters.find { it.name == "userId" }
        assertNotNull(pathParam)
        assertEquals(ParameterLocation.PATH, pathParam!!.location)
        assertTrue(pathParam.required)
        assertEquals("integer", pathParam.schema.type)
        
        val queryParam = endpoint.parameters.find { it.name == "includeDetails" }
        assertNotNull(queryParam)
        assertEquals(ParameterLocation.QUERY, queryParam!!.location)
        assertFalse(queryParam.required)
    }
    
    @Test
    fun `parse spec with request body`(@TempDir tempDir: Path) {
        val specYaml = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /users:
                post:
                  summary: Create user
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - email
                            - password
                          properties:
                            email:
                              type: string
                              format: email
                            password:
                              type: string
                              format: password
                            name:
                              type: string
                  responses:
                    '201':
                      description: Created
        """.trimIndent()
        
        val specFile = tempDir.resolve("openapi.yaml")
        specFile.writeText(specYaml)
        
        val result = parser.parseFromFile(specFile.toString())
        
        assertTrue(result.isSuccess)
        val spec = result.getOrThrow()
        
        val endpoint = spec.endpoints.first()
        assertEquals("POST", endpoint.method)
        
        assertNotNull(endpoint.requestBody)
        assertTrue(endpoint.requestBody!!.required)
        
        val jsonContent = endpoint.requestBody!!.content["application/json"]
        assertNotNull(jsonContent)
        
        val schema = jsonContent!!.schema
        assertNotNull(schema)
        assertEquals("object", schema!!.type)
        assertEquals(3, schema.properties?.size)
        
        val emailProp = schema.properties?.get("email")
        assertNotNull(emailProp)
        assertEquals("email", emailProp!!.format)
    }
    
    @Test
    fun `parse spec with security schemes`(@TempDir tempDir: Path) {
        val specYaml = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            components:
              securitySchemes:
                bearerAuth:
                  type: http
                  scheme: bearer
                  bearerFormat: JWT
                apiKey:
                  type: apiKey
                  in: header
                  name: X-API-Key
            paths:
              /secure:
                get:
                  security:
                    - bearerAuth: []
                  responses:
                    '200':
                      description: Success
        """.trimIndent()
        
        val specFile = tempDir.resolve("openapi.yaml")
        specFile.writeText(specYaml)
        
        val result = parser.parseFromFile(specFile.toString())
        
        assertTrue(result.isSuccess)
        val spec = result.getOrThrow()
        
        assertEquals(2, spec.securitySchemes.size)
        
        val bearerAuth = spec.securitySchemes["bearerAuth"]
        assertNotNull(bearerAuth)
        assertEquals("http", bearerAuth!!.type)
        assertEquals("bearer", bearerAuth.scheme)
        assertEquals("JWT", bearerAuth.bearerFormat)
        
        val apiKey = spec.securitySchemes["apiKey"]
        assertNotNull(apiKey)
        assertEquals("apiKey", apiKey!!.type)
        assertEquals("X-API-Key", apiKey.name)
    }
    
    @Test
    fun `parse spec with servers`(@TempDir tempDir: Path) {
        val specYaml = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            servers:
              - url: https://api.example.com/v1
              - url: https://staging.api.example.com/v1
            paths:
              /health:
                get:
                  responses:
                    '200':
                      description: Healthy
        """.trimIndent()
        
        val specFile = tempDir.resolve("openapi.yaml")
        specFile.writeText(specYaml)
        
        val result = parser.parseFromFile(specFile.toString())
        
        assertTrue(result.isSuccess)
        val spec = result.getOrThrow()
        
        assertEquals(2, spec.servers.size)
        assertTrue(spec.servers.contains("https://api.example.com/v1"))
        assertTrue(spec.servers.contains("https://staging.api.example.com/v1"))
    }
    
    @Test
    fun `parse fails for non-existent file`() {
        val result = parser.parseFromFile("/non/existent/file.yaml")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    
    @Test
    fun `parse fails for invalid YAML`(@TempDir tempDir: Path) {
        val invalidYaml = """
            this is not valid: yaml: structure:
            - broken
              indentation
        """.trimIndent()
        
        val specFile = tempDir.resolve("invalid.yaml")
        specFile.writeText(invalidYaml)
        
        val result = parser.parseFromFile(specFile.toString())
        
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `parse JSON format spec`(@TempDir tempDir: Path) {
        val specJson = """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "JSON API",
                "version": "1.0.0"
              },
              "paths": {
                "/test": {
                  "get": {
                    "summary": "Test endpoint",
                    "responses": {
                      "200": {
                        "description": "Success"
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        
        val specFile = tempDir.resolve("openapi.json")
        specFile.writeText(specJson)
        
        val result = parser.parseFromFile(specFile.toString())
        
        assertTrue(result.isSuccess)
        val spec = result.getOrThrow()
        
        assertEquals("JSON API", spec.title)
        assertEquals(1, spec.endpoints.size)
    }
}
