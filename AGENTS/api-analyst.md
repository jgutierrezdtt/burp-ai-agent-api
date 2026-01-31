# Agent Profile: api-analyst

Propósito: provisto como guía y prompts 'golden' para el agente que ayudará en el análisis de especificaciones OpenAPI, clasificación de datos, extracción de roles y enriquecimiento de flujos. Este fichero contiene plantillas de prompt, el formato JSON esperado y ejemplos "golden" de respuesta que se usarán en tests y mocks.

IMPORTANTE:
- Antes de enviar cualquier contexto al agente, el plugin debe aplicar `RedactionPolicy` (ya implementado).
- Las llamadas deben incluir un `ctxHash` para caching/auditoría y un `privacyMode` (STRICT/BALANCED/OFF).
- Las respuestas del agente deben ser JSON puro (sin texto adicional) para facilitar el parseo en el cliente.

----

## 1) Esquema general para peticiones

Request (contextJson):

```json
{
  "requestId": "req-<timestamp>",
  "ctxHash": "<short-hash>",
  "spec": {
    "title": "...",
    "version": "...",
    "endpoints": [ { "path": "/users", "method": "POST", "parameters": ["email","password"] }, ... ]
  },
  "notes": "Optional short instructions / policy reminders"
}
```

El cliente debe pasar `privacyMode` y `deterministic` por la capa que llama al agente.

----

## 2) Prompt: Data Classification (ANALYZE_SPEC_FOR_DATA_CLASSIFICATION)

Instrucciones para el agente:

- Input: la `spec` resumida en JSON (ver esquema). Usa solo esa información.
- Objetivo: identificar campos (parámetros y propiedades de body) que probablemente contengan datos sensibles.
- Salida: JSON array con objetos: `{ "fieldPath", "category", "sensitivity", "reason", "confidence" }`.
- Categorías permitidas: `PII`, `AUTH`, `FINANCIAL`, `ADMIN`, `IDENTIFIER`, `PUBLIC`, `UNKNOWN`.
- Sensitivity: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`.
- `confidence`: número 0..1.
- `reason`: breve texto justificando la clasificación (1-2 frases).
- Requisitos: SOLO salida JSON; en caso de duda, usar `UNKNOWN` con baja `confidence`.

Prompt template (enviar en el campo `text` del agente):

```
ANALYZE_SPEC_FOR_DATA_CLASSIFICATION

You will receive a JSON `spec` (title, version, endpoints with parameter names) as context.
Return a JSON array of objects with fields: fieldPath, category, sensitivity, reason, confidence.

Rules:
- Use semantic reasoning; do not rely on regex only.
- If parameter name is ambiguous, use lower confidence.
- Output must be valid JSON only.
```

Golden response example (JSON):

```json
[
  {"fieldPath":"/users.POST.body.email","category":"PII","sensitivity":"HIGH","reason":"User email address field","confidence":0.95},
  {"fieldPath":"/users.POST.body.password","category":"AUTH","sensitivity":"CRITICAL","reason":"Password field used for authentication","confidence":0.99}
]
```

----

## 3) Prompt: Flow Enrichment (ENRICH_API_FLOWS)

Objetivo: recibir grafos/heurísticas locales y enriquecer con nombres semánticos, pasos de datos, y riesgos lógicos.

Input: resumen de flujos heurísticos (lista de pasos con endpoints), `spec` summary.

Salida esperada: JSON array de `ApiFlow` enriquecidos:

```json
[
  {
    "name":"User registration flow",
    "steps":[{"path":"/register","method":"POST","stepNumber":1},{"path":"/verify","method":"POST","stepNumber":2}],
    "requiredRoles":[],
    "dataFlow":[{"fromStep":1,"toStep":2,"dataField":"userId","required":true}],
    "risks":[{"severity":"HIGH","description":"Email enumeration via registration endpoint"}]
  }
]
```

Directivas:
- Priorizar claridad y escenarios de abuso concretos.
- Añadir una lista breve de pruebas útiles (p. ej. mutate POST -> GET with id change to test IDOR).

Golden example: incluir 1-2 flows con `dataFlow` y `risks`.

----

## 4) Prompt: Role Inference (INFER_ROLES_AND_MATRIX)

Objetivo: inferir roles y mapear endpoints a roles desde `securitySchemes`, `x-roles` o descripciones.

Salida:

```json
{
  "roles":[{"name":"admin","description":"Administrator","scopes":["admin:*"],"allowedEndpoints":["/admin/*"]}],
  "accessMatrix": [ {"endpoint":"/users/{id}","method":"DELETE","allowedRoles":["admin"]} ]
}
```

Directivas: explicar incertidumbres (ej. inferredFrom: description) y asignar confidence 0..1 por cada mapping.

----

## 5) Testing & Golden files

- Guardar respuestas golden en `src/test/resources/golden/` para cada prompt (`data-classification.json`, `flow-enrichment.json`, `role-inference.json`).
- Tests unitarios deben mockear `AgentClient` y devolver las respuestas golden; validar parseo y que el código reacciona correctamente en fallback y caching.
- Incluir tests que verifiquen que cuando `privacyMode=STRICT` se siguen enviando solo campos redacted y que `ctxHash` se genera y se usa para cacheo.

----

## 6) Buenas prácticas y seguridad

- Nunca enviar tokens, secrets o valores literales en el prompt (solo nombres de campos y estructuras). Si un schema contiene ejemplos con datos sensibles, deben ser redacted antes de la llamada.
- Forzar `PrivacyMode` por defecto a `BALANCED` o `STRICT` en análisis automatizados.
- Registrar (audit) `ctxHash`, requester id y counts; no registrar el `contextJson` completo sin permiso.
- Validar JSON de salida del agente estrictamente y fallar con un fallback seguro (p. ej. vacío) si la salida no es JSON válido.

----

## 7) Integración sugerida en tests

- `FakeAgentClient` (ya creado en tests) devolverá el contenido del fichero golden correspondiente.
- Tests a incluir:
  - `DataClassifier` parsea correctamente golden file.
  - `ApiFlowAnalyzer` enrichment acepta respuesta AI y la incorpora.
  - `ContextCollector` no envía datos sensibles en `privacyMode=STRICT` (mock verify).

----

Mantén este fichero actualizado cuando se mejoren prompts o cambien los esquemas JSON.
