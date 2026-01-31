package com.six2dez.burp.aiagent.context.openapi

/**
 * Simple analyzer that identifies flows between endpoints in an OpenApiSpec.
 *
 * The implementation uses deterministic heuristics to detect common flows:
 *  - Authentication endpoints (path contains "auth" or endpoint.security present)
 *  - CRUD flows: POST /resource  -> GET/PUT/DELETE /resource/{id}
 *  - Dependency by path naming (shared base path)
 */
object ApiFlowAnalyzer {

    /**
     * Analiza la especificación `spec` y devuelve una lista de `ApiFlow` detectados.
     *
     * El analizador realiza las siguientes tareas:
     *  - Detecta endpoints de autenticación (por path, tags o security requirements).
     *  - Detecta flujos CRUD (POST que crea recursos y endpoints con path param que consumen el id).
     *  - Resuelve producción de identificadores (IDs) desde respuestas o schemas para unir productores y consumidores.
     *  - Construye flujos ordenados y recoge roles/scopes requeridos.
     */
    fun analyze(spec: OpenApiSpec): List<ApiFlow> {
        val flows = mutableListOf<ApiFlow>()

        // Índices auxiliares
        val endpoints = spec.endpoints

        // 1) Detectar endpoints de autenticación
        val authEndpoints = endpoints.filter { ep ->
            ep.path.contains("auth", ignoreCase = true)
                    || ep.tags.any { it.equals("auth", ignoreCase = true) }
                    || ep.security.isNotEmpty()
        }

        authEndpoints.forEach { ep ->
            flows.add(
                ApiFlow(
                    name = "Auth flow: ${ep.method} ${ep.path}",
                    description = "Authentication endpoint detected",
                    steps = listOf(FlowStep(endpoint = ep, stepNumber = 1)),
                    requiredRoles = ep.security.flatMap { it.keys }.toSet()
                )
            )
        }

        // 2) Indexar productores de identificadores a partir de responses/schemas
        // buscaremos propiedades típicas: id, <resource>Id, etc.
        val idProducers: MutableMap<String, MutableList<ApiEndpoint>> = mutableMapOf()

        endpoints.forEach { ep ->
            ep.responses.values.forEach { resp ->
                resp.content.values.forEach { media ->
                    val schema = media.schema ?: return@forEach
                    val props = schema.properties ?: emptyMap()
                    props.keys.forEach { propName ->
                        if (isLikelyIdentifier(propName)) {
                            idProducers.computeIfAbsent(propName) { mutableListOf() }.add(ep)
                        }
                    }
                    // también si schema tiene ref, comprobar en componentes
                    schema.ref?.let { ref ->
                        val refName = ref.substringAfterLast('/')
                        spec.schemas[refName]?.properties?.keys?.forEach { propName ->
                            if (isLikelyIdentifier(propName)) {
                                idProducers.computeIfAbsent(propName) { mutableListOf() }.add(ep)
                            }
                        }
                    }
                }
            }
        }

        // 3) Detectar CRUD flows por relación POST -> detail endpoints con path param
        val postEndpoints = endpoints.filter { it.method.equals("POST", ignoreCase = true) }

        postEndpoints.forEach { post ->
            val base = basePath(post.path)
            // buscar endpoints que comparten base y contienen path params
            val detailCandidates = endpoints.filter { ep ->
                base.isNotEmpty() && ep.path.startsWith(base) && ep.path.contains('{') && !ep.path.equals(post.path)
            }

            if (detailCandidates.isNotEmpty()) {
                val steps = mutableListOf<FlowStep>()
                steps.add(FlowStep(endpoint = post, stepNumber = 1))
                detailCandidates.forEachIndexed { idx, ep ->
                    steps.add(FlowStep(endpoint = ep, stepNumber = idx + 2))
                }

                val requiredRoles = (listOf(post) + detailCandidates).flatMap { it.security.flatMap { m -> m.keys } }.toSet()

                flows.add(
                    ApiFlow(
                        name = "CRUD flow: ${base}",
                        description = "Detected POST -> detail operations",
                        steps = steps,
                        requiredRoles = requiredRoles
                    )
                )
            }
        }

        // 4) Unir productores y consumidores por nombre de parámetro en path
        val paramPattern = Regex("\\{([^}]+)\\}")
        endpoints.forEach { consumer ->
            val params = paramPattern.findAll(consumer.path).map { it.groupValues[1] }.toList()
            params.forEach { paramName ->
                // buscar productores que generen este nombre de id o <resource>Id
                val direct = idProducers[paramName].orEmpty()
                val alt = idProducers["${paramName}Id"].orEmpty()
                val producers = (direct + alt).distinct()
                if (producers.isNotEmpty()) {
                    producers.forEach { prod ->
                        val steps = listOf(FlowStep(endpoint = prod, stepNumber = 1), FlowStep(endpoint = consumer, stepNumber = 2))
                        flows.add(
                            ApiFlow(
                                name = "Linked flow: ${prod.path} -> ${consumer.path}",
                                description = "Producer-consumer linked by param '$paramName'",
                                steps = steps,
                                requiredRoles = (prod.security + consumer.security).flatMap { it.keys }.toSet()
                            )
                        )
                    }
                }
            }
        }

        // Deduplicación simple: unir flujos con mismo nombre
        return flows.distinctBy { it.name }
    }

    private fun isLikelyIdentifier(name: String): Boolean {
        val n = name.lowercase()
        return n == "id" || n.endsWith("id") || n.contains("identifier") || n.contains("uuid")
    }

    private fun basePath(path: String): String {
        // devuelve la parte base antes del primer {param} o de la última parte variable
        val trimmed = path.trimEnd('/')
        val idx = trimmed.indexOf("/{")
        return if (idx >= 0) trimmed.substring(0, idx) else trimmed
    }
}
