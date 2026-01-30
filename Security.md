# Security.md

## Estructura de Seguridad del Proyecto

### 1. Autenticación y Autorización
- **Requisito:** Toda interacción sensible debe requerir autenticación.
- **Mitigación:** Uso de esquemas de seguridad OpenAPI (Bearer, API Key, OAuth2). Validación de tokens y claves en cada endpoint.
- **Evidencia:**
    - Definición de esquemas en la spec: [OpenApiParser.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParser.kt#L1-L60)
    - Uso de `securitySchemes` en modelos: [ApiModels.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/ApiModels.kt)
- **Ejemplo:**
    - Un endpoint protegido en la spec OpenAPI debe incluir:
      ```yaml
      security:
        - bearerAuth: []
      ```

### 2. Control de Acceso Basado en Roles (RBAC)
- **Requisito:** Los endpoints deben validar el rol y los permisos del usuario.
- **Mitigación:** Definición de roles y scopes en la especificación OpenAPI. Validación de roles en lógica de negocio.
- **Evidencia:**
    - Modelado de roles y scopes: [ApiModels.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/ApiModels.kt)
    - Uso de `security` en endpoints: [OpenApiParser.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParser.kt)
- **Ejemplo:**
    - Definir roles en la spec y comprobarlos en la lógica de negocio antes de ejecutar acciones sensibles.

### 3. Protección de Datos Sensibles
- **Requisito:** Los datos clasificados como PII, AUTH o FINANCIAL deben ser protegidos en tránsito y en almacenamiento.
- **Mitigación:** Uso de HTTPS obligatorio. Redacción y anonimización configurable (PrivacyMode). No se almacena información sensible sin redacción.
- **Evidencia:**
    - Redacción aplicada en: [ContextCollector.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt#L27-L33)
    - Política de redacción: [Redaction.kt](src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt#L1-L60)
    - Clasificación de datos: [DataClassifier.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/DataClassifier.kt#L1-L60)
- **Ejemplo:**
    - Antes de registrar o enviar datos a la IA, aplicar siempre `Redaction.apply(...)`.

### 4. Validación de Entradas y Salidas
- **Requisito:** Todas las entradas de usuario y datos externos deben ser validadas y saneadas.
- **Mitigación:** Validación de parámetros y cuerpos según schemas OpenAPI. Uso de tipos y constraints (minLength, pattern, enum, etc.).
- **Evidencia:**
    - Extracción y validación de parámetros: [OpenApiParser.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParser.kt#L1-L60)
    - Modelos de parámetros y schemas: [ApiModels.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/ApiModels.kt)
- **Ejemplo:**
    - Definir constraints en la spec:
      ```yaml
      parameters:
        - name: email
          in: query
          schema:
            type: string
            format: email
            minLength: 5
      ```

### 5. Registro y Auditoría
- **Requisito:** Todas las acciones relevantes deben ser registradas para auditoría.
- **Mitigación:** Logging estructurado en puntos críticos. No se registran datos sensibles (redacción activa en logs).
- **Evidencia:**
    - Uso de logging en la UI y backend: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt)
    - Redacción previa al log: [ContextCollector.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt#L27-L33)
- **Ejemplo:**
    - Antes de loggear, aplicar redacción:
      ```kotlin
      val safe = Redaction.apply(data, policy)
      logger.info(safe)
      ```

### 6. Gestión de Errores y Excepciones
- **Requisito:** Los errores no deben filtrar información sensible ni detalles internos.
- **Mitigación:** Mensajes de error genéricos para el usuario. Detalles solo en logs internos.
- **Evidencia:**
    - Manejo de errores en la UI: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt)
    - Logging de errores internos: [OpenApiParser.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParser.kt)
- **Ejemplo:**
    - Mostrar solo "Error al procesar la especificación" al usuario y registrar detalles en logs.

### 7. Protección contra Abuso y Rate Limiting
- **Requisito:** Los endpoints críticos deben tener protección contra abuso y limitación de peticiones.
- **Mitigación:** (Pendiente de implementar) Rate limiting y detección de patrones de abuso.
- **Evidencia:**
    - No existe lógica de rate limiting en: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt)
- **TODO:**
    - Añadir lógica de rate limiting en los métodos de carga y análisis de specs. Ver vulnerabilidad 1 en [vulns.md](vulns.md).

### 8. Integridad de la Especificación y Código
- **Requisito:** La especificación OpenAPI y el código deben estar alineados y versionados.
- **Mitigación:** Control de versiones en Git. Validación automática de la spec en CI/CD.
- **Evidencia:**
    - Control de versiones: [build.gradle.kts](build.gradle.kts)
    - Validación de spec en tests: [OpenApiParserTest.kt](src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParserTest.kt)
- **Ejemplo:**
    - Añadir test de validación de la spec en el pipeline CI.

### 9. Seguridad en Integraciones Externas
- **Requisito:** Las integraciones con servicios externos (IA, MCP, webhooks) deben ser autenticadas y validadas.
- **Mitigación:** Validación de certificados, autenticación mutua y validación de payloads.
- **Evidencia:**
    - Validación de payloads y errores: [OpenApiParser.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParser.kt)
    - Uso de MCP SDK: [build.gradle.kts](build.gradle.kts)
- **Ejemplo:**
    - Validar la estructura y autenticidad de los datos antes de procesar respuestas externas.

### 10. Protección de la Lógica de Negocio
- **Requisito:** No debe ser posible el bypass de flujos críticos ni la manipulación de estados de negocio.
- **Mitigación:** Validación de estados y transiciones en lógica de negocio. Pruebas de flujos multi-step.
- **Evidencia:**
    - Validación de flujos y estados: [ContextCollector.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt)
    - Pruebas de lógica: [DataClassifierTest.kt](src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/DataClassifierTest.kt)
- **Ejemplo:**
    - Implementar tests que simulen flujos multi-step y validen la consistencia de estados.

---

## Requisitos sin mitigación actual
- **Rate limiting y protección contra abuso:** No implementado aún, pendiente de desarrollo.
- **Evidencia:**
    - No hay lógica de limitación en los puntos de entrada: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt)
- **TODO:**
    - Implementar rate limiting y actualizar la documentación de mitigación cuando esté disponible. Ver [vulns.md](vulns.md).

---

## Evaluación de Seguridad Lógica y de Negocio
- Validación de flujos de autenticación y autorización.
- Control de acceso granular por endpoint y operación.
- Protección contra manipulación de roles y privilegios.
- Validación de integridad de datos y consistencia de estados.
- Pruebas de lógica de negocio para evitar bypass y condiciones de carrera.

> Para vulnerabilidades detectadas, consulta el fichero `vulns.md`.
