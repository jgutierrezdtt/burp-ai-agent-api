package com.six2dez.burp.aiagent.context.openapi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiFlowAnalyzerTest {

    @Test
    fun `detects simple CRUD flow`() {
        val spec = OpenApiSpec(
            version = "3.0.0",
            title = "Flow API",
            endpoints = listOf(
                ApiEndpoint(
                    path = "/items",
                    method = "POST",
                    responses = mapOf(
                        "201" to ResponseDefinition(
                            content = mapOf(
                                "application/json" to MediaTypeDefinition(
                                    schema = SchemaDefinition(
                                        type = "object",
                                        properties = mapOf("id" to SchemaDefinition(type = "string"))
                                    )
                                )
                            )
                        )
                    )
                ),
                ApiEndpoint(path = "/items/{id}", method = "GET"),
                ApiEndpoint(path = "/items/{id}", method = "PUT")
            )
        )

        val flows = ApiFlowAnalyzer.analyze(spec)

        assertTrue(flows.any { it.name.startsWith("CRUD flow") }, "Should detect a CRUD flow")
        val crud = flows.find { it.name.startsWith("CRUD flow") }!!
        assertEquals(3, crud.steps.size)
        assertEquals("POST", crud.steps[0].endpoint.method)
        assertTrue(crud.steps.any { it.endpoint.path.contains("{id}") })
    }

    @Test
    fun `detects auth endpoints`() {
        val spec = OpenApiSpec(
            version = "3.0.0",
            title = "Auth API",
            endpoints = listOf(
                ApiEndpoint(path = "/auth/login", method = "POST", security = emptyList()),
                ApiEndpoint(path = "/secure/resource", method = "GET", security = listOf(mapOf("bearerAuth" to emptyList())))
            )
        )

        val flows = ApiFlowAnalyzer.analyze(spec)

        assertTrue(flows.any { it.name.contains("Auth flow") }, "Should detect auth flows")
    }
}
