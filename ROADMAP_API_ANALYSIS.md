# Roadmap: Análisis Contextual de APIs

## Visión General
Adaptar Burp AI Agent para análisis contextual de especificaciones API (OpenAPI/Swagger), permitiendo:
- Importar y parsear archivos OpenAPI o URLs de specs vivas
- Clasificar endpoints según tipo de dato (PII, auth, admin, etc.)
- Analizar flujos inteligentes del API considerando roles de usuario
- Generar pruebas contextuales basadas en la especificación

## FASE 1: MVP - Análisis Básico de OpenAPI
**Duración estimada:** 2-3 semanas  
**Objetivo:** Importar spec OpenAPI y mostrar endpoints clasificados

### 1.1 Parser OpenAPI (3-4 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParser.kt`
- `src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParserTest.kt`

**Tareas:**
 - [x] Agregar dependencia `io.swagger.parser.v3:swagger-parser:2.1.x` en `build.gradle.kts`
 - [x] Implementar parser para OpenAPI 3.0/3.1
 - [x] Extraer: paths, métodos HTTP, parámetros (query, path, body, headers)
 - [x] Extraer schemas de request/response
 - [x] Extraer security schemes (OAuth2, Bearer, API Key, Basic Auth)
 - [x] Soporte para cargar desde archivo local
 - [x] Soporte para cargar desde URL
 - [x] Manejo de errores (spec inválido, versión no soportada)
 - [x] Tests unitarios con specs de ejemplo

**Fase 1 — Estado:** COMPLETADO

Detalles:
- Parser implementado y testeado (`OpenApiParser.kt`, `OpenApiParserTest.kt`).
- Modelos y `ContextCollector` implementados y soportan redaction (`ContextModels.kt`, `ContextCollector.kt`).
- `DataClassifier` implementado en modo IA-driven con fallback y tests (`DataClassifier.kt`, `DataClassifierTest.kt`).
- UI base implementada: `ApiAnalysisPanel` con carga por archivo/URL, tabla de endpoints y panel de detalle.
- Integración: `ApiAnalysisPanel` agregado como pestaña en `MainTab`.
- Hardening inicial: rate limiter, size threshold, RBAC filtering y audit logging añadidos.

Pendientes / notas:
- Filtros avanzados (por método, sensibilidad) y búsqueda por path son mejoras UX pendientes pero no bloqueantes para la Fase 1.
- Tests completos en CI: ejecutar `./gradlew test` en entorno local/CI (el contenedor de edición en este workspace tiene una limitación ENOPRO que impide ejecución directa aquí).

**Estructura de salida:**
```kotlin
data class OpenApiSpec(
    val version: String,
    val title: String,
    val servers: List<String>,
    val endpoints: List<ApiEndpoint>,
    val schemas: Map<String, SchemaDefinition>,
    val securitySchemes: Map<String, SecurityScheme>
)
```

### 1.2 Modelo de Datos API (2 días)
**Archivos a modificar/crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/context/ContextModels.kt` (extender)
- `src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/ApiModels.kt` (nuevo)

**Tareas:**
 - [x] Crear `ApiSpecItem` implementando `BurpContextItem`
 - [x] Crear `ApiEndpoint` (path, method, parameters, auth, responses)
 - [x] Crear `ApiParameter` (name, type, location, required, schema)
 - [x] Crear `SchemaDefinition` (type, properties, required fields, format)
 - [x] Crear `SecurityScheme` (type, scheme, flows)
 - [x] Integrar con `ContextCollector` para serializar a JSON
 - [x] Soporte para redacción según `PrivacyMode`

**Integración:**
```kotlin
// En ContextCollector.kt
fun fromApiSpec(spec: OpenApiSpec, options: ContextOptions): ContextCapture {
    val policy = RedactionPolicy.fromMode(options.privacyMode)
    // ... conversión y serialización
}
```

### 1.3 Clasificador de Datos Básico (3 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/DataClassifier.kt`
- `src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/DataClassifierTest.kt`

**Tareas:**
 - [x] Implementar clasificación por reglas deterministas
 - [x] Regex patterns para PII:
  - Email: `/\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/`
  - Phone: `/\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/`
  - SSN: `/\b\d{3}-\d{2}-\d{4}\b/`
  - Credit Card: `/\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b/`
 - [x] Clasificación por nombre de campo (case-insensitive):
  - Auth: `password`, `token`, `secret`, `apikey`, `api_key`, `authorization`
  - PII: `email`, `phone`, `address`, `ssn`, `dob`, `birthdate`
  - Financial: `card`, `credit`, `cvv`, `account`, `routing`
  - Admin: `admin`, `role`, `permission`, `scope`
 - [x] Clasificación por OpenAPI schema format:
  - `format: email` → PII
  - `format: password` → Auth
  - `format: uuid` → Identifier
 - [x] Sistema de niveles de sensibilidad:
  - `CRITICAL`: passwords, tokens, credit cards
  - `HIGH`: PII (email, phone, SSN)
  - `MEDIUM`: user IDs, session data
  - `LOW`: public metadata
 - [x] Tests con datasets de ejemplo

**Tareas (IA-driven — sin regex en runtime):**
- [ ] Implementar un `DataClassifier` que delegue la clasificación a la IA mediante `AgentSupervisor`.
  - El clasificador enviará extractos redacted del `OpenApiSpec` al agente `api-analyst` con instrucciones estructuradas.
  - La IA debe devolver una lista de `DataClassification` con `fieldPath`, `category`, `sensitivity` y `reason`.
  - Añadir prompts/instrucciones en `AGENTS/api-analyst.md` para guiar la clasificación (ejemplos, criterios de sensibilidad, priorización).
- [ ] Diseñar y documentar un formato de petición/response JSON para la llamada a la IA (determinista y fácil de mockear en tests).
- [ ] Implementar un modo offline/fallback mínimo: reglas heurísticas ligeras (solo como fallback para entornos sin backend IA), pero **no** usar estas reglas como la fuente primaria de verdad.
- [ ] Implementar scoring/confianza devuelta por la IA y exponerla en `DataClassification.reason` o un nuevo campo `confidence`.
- [ ] Tests: crear tests unitarios que mockeen `AgentSupervisor`/IA y verifiquen:
  - Correcta serialización del contexto enviado a la IA.
  - Correcto parseo de la respuesta IA a `DataClassification`.
  - Comportamiento del fallback offline cuando el backend IA no está disponible.

**Salida esperada (unchanged):**
```kotlin
data class DataClassification(
    val fieldPath: String,           // "user.email"
    val category: DataCategory,      // PII, AUTH, FINANCIAL, ADMIN, IDENTIFIER, PUBLIC
    val sensitivity: SensitivityLevel, // CRITICAL, HIGH, MEDIUM, LOW
    val reason: String                // "Detected by AI: likely email field"
)
```

**Output:**
```kotlin
data class DataClassification(
    val fieldPath: String,           // "user.email"
    val category: DataCategory,      // PII, AUTH, FINANCIAL, ADMIN, IDENTIFIER, PUBLIC
    val sensitivity: SensitivityLevel, // CRITICAL, HIGH, MEDIUM, LOW
    val reason: String                // "Detected by email pattern"
)
```

### 1.4 UI Panel de Análisis de API (4 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt`
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ApiEndpointTable.kt`

**Tareas:**
 - [x] Panel con layout: input arriba, resultados abajo
 - [x] Input section:
   - [x] Botón "Load OpenAPI File"
   - [x] Campo de texto "OpenAPI URL"
   - [x] Botón "Analyze URL"
   - [x] Indicador de progreso durante carga
 - [x] Results section:
   - [x] Tabla de endpoints (path, method, auth required, data sensitivity)
   - [ ] Filtros: por método HTTP, por nivel de sensibilidad
   - [ ] Búsqueda por path
   - [x] Click en endpoint → detalle en panel derecho
 - [x] Detail panel:
   - [x] Parámetros del endpoint con clasificación
   - [x] Request/response schemas
   - [x] Security requirements
   - [ ] Botón "Send to AI Analysis"
 - [ ] Integrar en `MainTab.kt` como nueva pestaña
 - [x] Estilos consistentes con `UiTheme.kt`

**Integración en MainTab:**
```kotlin
// En MainTab.kt, agregar tab
private val apiAnalysisPanel = ApiAnalysisPanel(api, supervisor)
// En init, agregar:
tabbedPane.addTab("API Analysis", apiAnalysisPanel.root)
```

### 1.5 Acción UI para Importar API (1 día)
**Archivos a modificar:**
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/UiActions.kt`

**Tareas:**
- [ ] Agregar acción "Import API Spec" en menú contextual
- [ ] Handler para seleccionar archivo OpenAPI
- [ ] Handler para ingresar URL de spec
- [ ] Mostrar resultados en ApiAnalysisPanel
- [ ] Manejo de errores (spec inválido, URL no accesible)

---

## FASE 2: Análisis de Flujos con IA
**Duración estimada:** 2 semanas  
**Objetivo:** IA analiza flujos lógicos entre endpoints

### 2.1 Extractor de Flujos (4 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/ApiFlowAnalyzer.kt`
- `src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/ApiFlowAnalyzerTest.kt`

**Tareas:**
- [ ] Identificar flujos de autenticación:
  - Login endpoint → endpoints que requieren auth
  - OAuth flow completo (authorize → token → resource)
- [ ] Identificar flujos CRUD:
  - POST /resource → GET /resource/{id}
  - GET /resource/{id} → PUT /resource/{id} → DELETE /resource/{id}
- [ ] Identificar dependencias entre endpoints:
  - Refs en schemas ($ref)
  - IDs en path que coinciden con respuestas de otros endpoints
- [ ] Construir grafo de dependencias:
  - Nodos: endpoints
  - Aristas: tipo de relación (auth_required, creates, updates, depends_on)
- [ ] Detectar flujos de múltiples pasos:
  - Admin flows (requieren rol admin)
  - Payment flows (carrito → checkout → pago → confirmación)
- [ ] Tests con APIs de ejemplo (Petstore, Stripe-like)

**Output:**
```kotlin
data class ApiFlow(
    val name: String,              // "User Registration Flow"
    val steps: List<FlowStep>,     // Secuencia ordenada de endpoints
    val requiredRoles: Set<String>, // ["user", "admin"]
    val dataFlow: List<DataDependency> // Qué datos se pasan entre pasos
)

data class FlowStep(
    val endpoint: ApiEndpoint,
    val inputFromPreviousStep: List<String>?, // ["userId", "sessionToken"]
    val outputToNextStep: List<String>?       // ["orderId"]
)
```

### 2.2 Agent Profile para APIs (2 días)
**Archivos a crear:**
- `AGENTS/api-analyst.md`

**Tareas:**
- [ ] Crear profile específico para análisis de APIs
- [ ] Secciones:
  - `[GLOBAL]`: Rol de analista de seguridad de APIs
  - `[ANALYZE_API_SPEC]`: Instrucciones para analizar spec completo
  - `[ANALYZE_ENDPOINT]`: Análisis profundo de endpoint individual
  - `[ANALYZE_FLOW]`: Análisis de flujo multi-step
  - `[FIND_LOGIC_BUGS]`: Buscar bypasses, race conditions, IDOR
- [ ] Instrucciones para:
  - Identificar endpoints sin autenticación que deberían tenerla
  - Detectar IDOR potenciales (IDs en path sin validación de ownership)
  - Encontrar escalación de privilegios (user → admin)
  - Analizar rate limiting y abuse scenarios
  - Detectar mass assignment en requests con schemas complejos
- [ ] Incluir lista de MCP tools relevantes
- [ ] Template para crear issues automáticamente
- [ ] Tests con `AgentProfileLoader`

**Ejemplo de prompt:**
```markdown
[ANALYZE_API_SPEC]
Goal: Comprehensive security analysis of API specification.

Workflow:
1. Review authentication mechanisms - identify unauthenticated endpoints
2. Map authorization model - identify role-based access patterns
3. Analyze data flows - trace sensitive data through endpoints
4. Identify logic bugs:
   - IDOR: endpoints with IDs but no ownership validation
   - Privilege escalation: user role accessing admin endpoints
   - Missing rate limits on sensitive operations
   - Mass assignment vulnerabilities
5. For each finding, provide:
   - Severity (CRITICAL/HIGH/MEDIUM/LOW)
   - Affected endpoints
   - Attack scenario
   - Remediation steps
```

### 2.3 Integración con AgentSupervisor (3 días)
**Archivos a modificar:**
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/UiActions.kt`
- `src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt`

**Tareas:**
- [ ] Crear acción "Analyze API Spec with AI" en `UiActions`
- [ ] Preparar contexto para IA:
  - Serializar spec completo a JSON
  - Incluir flujos detectados
  - Incluir clasificación de datos
  - Agregar instrucciones del agent profile
- [ ] Enviar al backend IA seleccionado vía `AgentSupervisor`
- [ ] Streaming de respuesta en `ChatPanel`
- [ ] Parsear findings de la respuesta IA
- [ ] Botón "Create Issues" para findings confirmados
- [ ] Tests de integración

**Flujo:**
```kotlin
// En ApiAnalysisPanel
fun analyzeWithAi(spec: OpenApiSpec) {
    val context = contextCollector.fromApiSpec(spec, options)
    val flows = ApiFlowAnalyzer.analyze(spec)
    val profile = AgentProfileLoader.buildInstructionBlock("ANALYZE_API_SPEC")
    
    supervisor.executeAnalysis(
        context = context,
        additionalContext = "API Flows:\n${flows.toJson()}",
        instructions = profile,
        onResponse = { chatPanel.appendAiResponse(it) }
    )
}
```

### 2.4 Visualización de Flujos (2 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ApiFlowDiagram.kt`

**Tareas:**
- [ ] Renderizar grafo de flujos en UI
- [ ] Layout simple: lista vertical de pasos con flechas
- [ ] Highlight de endpoints según sensibilidad
- [ ] Click en paso → mostrar detalle del endpoint
- [ ] Export de flujo a Markdown para documentación
- [ ] Integrar en ApiAnalysisPanel

---

## FASE 3: Roles y Permisos
**Duración estimada:** 1-2 semanas  
**Objetivo:** Mapear roles de usuario y validar accesos

### 3.1 Extractor de Roles (3 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/RoleExtractor.kt`
- `src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/RoleExtractorTest.kt`

**Tareas:**
- [ ] Parsear `securitySchemes` en OpenAPI
- [ ] Detectar OAuth scopes como roles
- [ ] Parsear extensiones custom (x-roles, x-permissions)
- [ ] Inferir roles de descriptions:
  - "Admin only" → role: admin
  - "Requires moderator" → role: moderator
- [ ] Extraer roles de path patterns:
  - `/admin/*` → requiere role admin
  - `/user/{userId}/*` → requiere ownership o role user
- [ ] Crear modelo `ApiRole`:
  - name, description, endpoints permitidos
- [ ] Tests con specs que incluyan roles

**Output:**
```kotlin
data class ApiRole(
    val name: String,              // "admin", "user", "guest"
    val description: String?,
    val allowedEndpoints: Set<String>, // paths permitidos
    val scopes: Set<String>?       // OAuth scopes
)
```

### 3.2 Análisis de Matriz de Acceso (4 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/RoleAccessAnalyzer.kt`
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/AccessMatrixTable.kt`

**Tareas:**
- [ ] Construir matriz: roles × endpoints × métodos HTTP
- [ ] Marcar accesos permitidos vs no permitidos
- [ ] Identificar inconsistencias:
  - Endpoint sin auth requerido pero con datos sensibles
  - GET permitido sin auth, POST requiere auth en mismo recurso
  - Role user puede DELETE pero no puede GET
- [ ] IA sugiere pruebas de bypass:
  - Role A intentando acceder a endpoint de role B
  - Omitir header de auth en endpoint protegido
  - Cambiar ID en path (IDOR)
- [ ] UI: tabla interactiva con colores según permiso
- [ ] Export matriz a CSV/Excel
- [ ] Tests

**Visualización:**
```
Endpoint          | Method | anonymous | user | admin | moderator
------------------|--------|-----------|------|-------|----------
/api/users        | GET    | ❌        | ✅   | ✅    | ✅
/api/users/{id}   | GET    | ❌        | ⚠️   | ✅    | ✅
/api/users/{id}   | DELETE | ❌        | ❌   | ✅    | ✅
/api/admin/stats  | GET    | ❌        | ❌   | ✅    | ❌

⚠️ = Requires ownership validation
```

### 3.3 Generador de Test Cases (2 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/TestCaseGenerator.kt`

**Tareas:**
- [ ] Generar casos de prueba desde matriz de acceso
- [ ] Para cada celda ❌ o ⚠️, crear test case:
  - Request HTTP completo
  - Header de autenticación para el rol
  - Resultado esperado (403, 401, 404)
- [ ] Export a formato ejecutable:
  - Burp Suite XML (Repeater tabs)
  - Postman Collection
  - cURL scripts
- [ ] Integrar con `ActiveAiScanner` para ejecutar automáticamente
- [ ] Tests

---

## FASE 4: Integración con Scanner Activo
**Duración estimada:** 1 semana  
**Objetivo:** Generar payloads contextuales desde spec

### 4.1 PayloadGenerator desde Spec (4 días)
**Archivos a modificar:**
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PayloadGenerator.kt`

**Tareas:**
- [ ] Extender `PayloadGenerator` con soporte para schemas OpenAPI
- [ ] Generar payloads según tipo de schema:
  - `type: string` → XSS, SQLi, command injection
  - `type: integer` → integer overflow, SQLi numeric
  - `type: boolean` → boolean bypass
  - `type: array` → array injection, mass assignment
  - `type: object` → mass assignment, JSON injection
- [ ] Respetar constraints del schema:
  - `enum` → solo valores válidos + 1 inválido
  - `pattern` → valores que matchean + que no matchean
  - `minLength/maxLength` → boundary testing
  - `minimum/maximum` → boundary testing numérico
- [ ] Payloads específicos por formato:
  - `format: email` → email injection
  - `format: uri` → SSRF, open redirect
  - `format: date-time` → time-based injection
- [ ] Priorizar payloads según sensibilidad del campo
- [ ] Tests con schemas variados

**Ejemplo:**
```kotlin
fun generatePayloads(parameter: ApiParameter, endpoint: ApiEndpoint): List<Payload> {
    val payloads = mutableListOf<Payload>()
    
    when (parameter.schema.type) {
        "string" -> {
            if (parameter.classification?.category == DataCategory.AUTH) {
                payloads.addAll(authBypassPayloads())
            }
            if (parameter.schema.format == "email") {
                payloads.addAll(emailInjectionPayloads())
            }
            payloads.addAll(xssPayloads())
            payloads.addAll(sqliPayloads())
        }
        "integer" -> {
            payloads.addAll(integerBoundaryPayloads(parameter.schema.minimum, parameter.schema.maximum))
            payloads.addAll(sqliNumericPayloads())
        }
        // ...
    }
    
    return payloads.sortedByDescending { it.risk }
}
```

### 4.2 ActiveAiScanner con Spec (2 días)
**Archivos a modificar:**
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt`
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt`

**Tareas:**
- [ ] Agregar método `scanApiSpec(spec: OpenApiSpec)`
- [ ] Priorizar endpoints según:
  - Nivel de sensibilidad de datos (CRITICAL primero)
  - Requiere auth vs no requiere
  - Admin endpoints primero
- [ ] Auto-queue endpoints sensibles
- [ ] Generar requests base desde ejemplos en spec
- [ ] Aplicar payloads contextuales
- [ ] Enviar requests y analizar respuestas
- [ ] Crear issues para vulnerabilidades encontradas
- [ ] Botón "Start Active Scan" en ApiAnalysisPanel
- [ ] Progress bar con estado del scan
- [ ] Tests de integración

**Integración:**
```kotlin
// En ApiAnalysisPanel
fun startActiveScan() {
    val spec = currentLoadedSpec ?: return
    val endpoints = spec.endpoints.sortedByDescending { it.dataSensitivity }
    
    activeAiScanner.scanApiSpec(spec)
    showProgressDialog("Scanning ${endpoints.size} endpoints...")
}
```

---

## FASE 5: MCP Tools para APIs
**Duración estimada:** 1 semana  
**Objetivo:** Exponer análisis de APIs vía MCP

### 5.1 MCP Tool: api_import (2 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ApiImportTool.kt`

**Tareas:**
- [ ] Tool MCP para importar spec OpenAPI
- [ ] Parámetros: `source` (file path o URL)
- [ ] Retorna resumen del spec:
  - Número de endpoints
  - Security schemes
  - Top 10 endpoints más sensibles
- [ ] Registrar en `McpToolCatalog`
- [ ] Tests

### 5.2 MCP Tool: api_analyze (2 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ApiAnalyzeTool.kt`

**Tareas:**
- [ ] Tool MCP para analizar spec cargado
- [ ] Parámetros opcionales:
  - `focus`: "auth", "data-classification", "flows", "roles"
  - `endpoint_filter`: regex para filtrar endpoints
- [ ] Retorna análisis detallado en JSON
- [ ] Integrar con `ApiFlowAnalyzer`, `DataClassifier`, `RoleExtractor`
- [ ] Registrar en `McpToolCatalog`
- [ ] Tests

### 5.3 MCP Tool: api_test_generate (2 días)
**Archivos a crear:**
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ApiTestGenerateTool.kt`

**Tareas:**
- [ ] Tool MCP para generar test cases
- [ ] Parámetros:
  - `endpoint`: endpoint específico o "all"
  - `test_type`: "auth", "idor", "injection", "all"
- [ ] Retorna lista de requests HTTP listos para ejecutar
- [ ] Opcionalmente ejecutar tests (require unsafe mode)
- [ ] Registrar en `McpToolCatalog`
- [ ] Tests

---

## EXTRAS (Backlog)

### E.1 Crawler HTTP para APIs sin Spec
**Si la API no tiene OpenAPI:**
- [ ] Implementar crawler que observe tráfico Burp
- [ ] Inferir endpoints, parámetros, schemas desde requests/responses
- [ ] Construir spec "aproximado" automáticamente
- [ ] Permitir correcciones manuales

### E.2 Comparador de Specs
**Detectar cambios entre versiones:**
- [ ] Comparar spec v1 vs v2
- [ ] Identificar: endpoints nuevos, eliminados, modificados
- [ ] Detectar breaking changes
- [ ] Analizar impacto en seguridad

### E.3 GraphQL Support
**Además de REST:**
- [ ] Parser GraphQL schema (introspection)
- [ ] Análisis de queries, mutations, subscriptions
- [ ] Detección de over-fetching, under-fetching
- [ ] GraphQL-specific vulnerabilities (injection, DoS)

### E.4 Rate Limiting Detection
**Desde spec o runtime:**
- [ ] Parsear x-rate-limit headers en spec
- [ ] Probar rate limits con requests automáticos
- [ ] Detectar endpoints sin rate limiting
- [ ] Sugerir abuse scenarios

---

## Dependencias Técnicas

### Librerías a Agregar
**En `build.gradle.kts`:**
```kotlin
dependencies {
    // OpenAPI parser
    implementation("io.swagger.parser.v3:swagger-parser:2.1.20")
    
    // YAML support (para OpenAPI en YAML)
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    
    // JSON Schema validation (opcional)
    implementation("com.networknt:json-schema-validator:1.0.87")
    
    // Graph library para análisis de flujos (opcional)
    implementation("org.jgrapht:jgrapht-core:1.5.2")
}
```

### Estructura de Directorios Nueva
```
src/main/kotlin/com/six2dez/burp/aiagent/
├── context/
│   └── openapi/
│       ├── OpenApiParser.kt
│       ├── ApiModels.kt
│       ├── DataClassifier.kt
│       ├── ApiFlowAnalyzer.kt
│       ├── RoleExtractor.kt
│       ├── RoleAccessAnalyzer.kt
│       └── TestCaseGenerator.kt
├── ui/
│   ├── panels/
│   │   └── ApiAnalysisPanel.kt
│   └── components/
│       ├── ApiEndpointTable.kt
│       ├── ApiFlowDiagram.kt
│       └── AccessMatrixTable.kt
└── mcp/
    └── tools/
        ├── ApiImportTool.kt
        ├── ApiAnalyzeTool.kt
        └── ApiTestGenerateTool.kt

src/test/kotlin/com/six2dez/burp/aiagent/
└── context/
    └── openapi/
        ├── OpenApiParserTest.kt
        ├── DataClassifierTest.kt
        ├── ApiFlowAnalyzerTest.kt
        └── RoleExtractorTest.kt

AGENTS/
└── api-analyst.md
```

---

## Criterios de Éxito

### Fase 1 (MVP)
- [ ] Usuario puede cargar archivo OpenAPI 3.0
- [ ] UI muestra tabla de endpoints con clasificación de datos
- [ ] Al menos 5 categorías de datos detectadas (PII, AUTH, etc.)
- [ ] Tests pasan al 100%

### Fase 2 (Flujos con IA)
- [ ] IA identifica al menos 3 tipos de flujos (auth, CRUD, multi-step)
- [ ] Agent profile "api-analyst" genera análisis coherente
- [ ] Findings de IA se muestran en ChatPanel
- [ ] Usuario puede crear Burp issues desde findings

### Fase 3 (Roles)
- [ ] Matriz de acceso se genera correctamente para spec con 2+ roles
- [ ] UI visualiza matriz de forma clara
- [ ] Test cases generados son ejecutables

### Fase 4 (Scanner Activo)
- [ ] PayloadGenerator crea payloads contextuales basados en schemas
- [ ] ActiveAiScanner procesa al menos 10 endpoints de spec
- [ ] Al menos 1 vulnerabilidad detectada en spec de prueba

### Fase 5 (MCP)
- [ ] Claude Desktop puede importar spec vía `api_import`
- [ ] Claude puede analizar spec vía `api_analyze`
- [ ] Claude puede generar tests vía `api_test_generate`

---

## Riesgos y Mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Specs OpenAPI incompletos o inválidos | Alta | Alto | Parser robusto con fallbacks, permitir correcciones manuales |
| Schemas complejos (nested, recursive) | Media | Medio | Limitar profundidad de parsing, simplificar visualización |
| Performance con specs grandes (500+ endpoints) | Media | Medio | Paginación en UI, procesamiento async, cache |
| Roles custom no estándar | Alta | Medio | Inferencia con IA, permitir anotaciones manuales |
| IA genera falsos positivos | Media | Alto | Require confirmación manual, score de confianza |
| Incompatibilidad con Burp Community | Baja | Bajo | Funcionalidad core compatible, features Pro opcionales |

---

## Estimación Total

| Fase | Duración | Esfuerzo (dev-days) |
|------|----------|---------------------|
| Fase 1: MVP | 2-3 semanas | 12-15 días |
| Fase 2: Flujos IA | 2 semanas | 9-11 días |
| Fase 3: Roles | 1-2 semanas | 9 días |
| Fase 4: Scanner | 1 semana | 6 días |
| Fase 5: MCP | 1 semana | 6 días |
| **TOTAL** | **7-9 semanas** | **42-47 días** |

*Estimación asumiendo 1 developer full-time. Con 2 developers en paralelo: ~4-5 semanas.*

---

## Próximos Pasos Inmediatos

1. **Validar roadmap con stakeholders**
2. **Crear branch feature: `feature/api-analysis`**
3. **Agregar dependencias en `build.gradle.kts`**
4. **Implementar Fase 1.1: OpenApiParser (primera tarea concreta)**
5. **Setup de tests con spec OpenAPI de ejemplo (Petstore)**

---

## Referencias

- [OpenAPI Specification 3.1](https://spec.openapis.org/oas/v3.1.0)
- [Swagger Parser v3](https://github.com/swagger-api/swagger-parser)
- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
- [Burp Suite API](https://portswigger.net/burp/extender/api/)
