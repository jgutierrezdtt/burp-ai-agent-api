# vulns.md

## Vulnerabilidades Detectadas y Riesgos

Este documento lista vulnerabilidades, debilidades y riesgos identificados en el análisis de seguridad, tanto a nivel técnico como de lógica de negocio.

---

### 1. Falta de Rate Limiting
- **Descripción:** Actualmente, la aplicación expone endpoints de análisis y carga de especificaciones OpenAPI (por ejemplo, el panel de análisis y el backend de parsing) que pueden ser invocados repetidamente sin ningún tipo de limitación. Esto permite que un atacante automatice peticiones (por ejemplo, para cargar specs maliciosas o abusar del análisis IA) y consuma recursos del sistema, afectando la disponibilidad y generando posibles denegaciones de servicio.
- **Impacto:** Permite ataques de fuerza bruta, abuso de API y denegación de servicio.
- **Recomendación:** Implementar rate limiting y monitoreo de patrones de abuso en los puntos de entrada críticos, especialmente en los que interactúan con la IA o procesan archivos grandes.
- **Referencias de código:**
    - Métodos de carga y análisis sin limitación: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt#L1-L413)

### 2. Validación Incompleta de Roles y Permisos
- **Descripción:** En la lógica de negocio del análisis de API y la generación de contexto, no se valida de forma granular qué usuario puede acceder a qué funcionalidades. Por ejemplo, cualquier usuario con acceso al panel puede cargar y analizar cualquier especificación, sin restricciones por rol o permisos. Esto puede permitir que usuarios no autorizados accedan a información sensible o ejecuten análisis avanzados.
- **Impacto:** Riesgo de escalación de privilegios o acceso indebido a recursos.
- **Recomendación:** Revisar y reforzar validaciones de roles y scopes en cada endpoint y flujo, especialmente en la UI y en la integración con la IA.
- **Referencias de código:**
    - Acceso a funcionalidades críticas sin comprobación de permisos: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt#L1-L413)

### 3. Exposición de Datos Sensibles en Logs
- **Descripción:** Aunque existe una política de redacción, si la configuración de PrivacyMode no es estricta o se omite en algún flujo (por ejemplo, al serializar contextos para la IA o al registrar errores de parsing), datos sensibles como PII, credenciales o tokens pueden terminar en los logs de la aplicación o en los logs de la IA.
- **Impacto:** Fuga de PII, credenciales o información financiera.
- **Recomendación:** Verificar que la redacción y anonimización estén activas en todos los logs y que nunca se registre información sensible sin procesar, especialmente en logs de errores y auditoría.
- **Referencias de código:**
    - Aplicación de redacción en requests/responses: [ContextCollector.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt#L27-L33)
    - Redacción en issues: [ContextCollector.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt#L58-L68)
    - Uso de logs en la UI: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt#L1-L413)

### 4. Errores Detallados Expuestos al Usuario
- **Descripción:** En el panel de análisis y en la carga de especificaciones, los mensajes de error pueden mostrar detalles internos de la excepción (por ejemplo, stacktrace, mensajes de parsing, rutas de archivos). Esto puede ayudar a un atacante a mapear la estructura interna de la aplicación o a explotar fallos específicos del parser.
- **Impacto:** Ayuda a un atacante a mapear la aplicación o explotar fallos.
- **Recomendación:** Asegurar que los errores mostrados al usuario sean genéricos y que los detalles solo se registren en logs internos protegidos.
- **Referencias de código:**
    - Mensajes de error directos al usuario: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt#L1-L413)

### 5. Falta de Pruebas de Lógica de Negocio
- **Descripción:** No existen pruebas automatizadas para flujos críticos de negocio, como la secuencia de análisis de una API, la validación de roles en la UI, o la gestión de estados en la integración con la IA. Esto puede permitir que un usuario manipule el flujo (por ejemplo, saltarse pasos de validación o enviar specs maliciosas directamente a la IA).
- **Impacto:** Riesgo de bypass de lógica, condiciones de carrera o manipulación de estados.
- **Recomendación:** Implementar pruebas de lógica de negocio y validaciones de integridad en los flujos multi-step y en la interacción con la IA.
- **Referencias de código:**
    - Tests solo de clasificación, no de flujos completos: [DataClassifierTest.kt](src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/DataClassifierTest.kt)

### 6. Integraciones Externas sin Validación Estricta
- **Descripción:** La aplicación permite enviar datos y contexto a backends externos de IA y MCP. Actualmente, no se valida de forma estricta la estructura y el contenido de los payloads enviados y recibidos. Un atacante podría intentar inyectar datos maliciosos o manipular la respuesta de la IA para alterar el comportamiento de la aplicación.
- **Impacto:** Riesgo de ataques de inyección o manipulación de flujos externos.
- **Recomendación:** Validar estrictamente los datos recibidos de integraciones externas y sanear los datos antes de enviarlos.
- **Referencias de código:**
    - Serialización y envío de datos a la IA: [ContextCollector.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt#L104-L146)
    - Envío de contexto a la IA: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt#L1-L413)

### 7. Riesgos Específicos en el Trabajo con IA
- **Descripción:**
    - **Fuga de información sensible:** Si la redacción o anonimización no es adecuada en el método `fromApiSpec` de `ContextCollector` o en la UI, la IA puede recibir datos sensibles extraídos de las especificaciones o del tráfico HTTP, ya que el usuario puede cargar cualquier archivo y enviarlo a la IA sin revisión previa.
    - **Prompt injection:** El sistema permite que el usuario edite o genere prompts personalizados para la IA. Si no se valida ni restringe el contenido, un atacante podría inyectar instrucciones maliciosas que alteren el análisis, filtren información o manipulen la respuesta de la IA.
    - **Dependencia de la IA para decisiones críticas:** En el flujo de análisis, la respuesta de la IA puede ser utilizada para tomar decisiones de seguridad o para crear issues automáticamente. Si no se valida la respuesta, se pueden introducir vulnerabilidades lógicas o aceptar recomendaciones inseguras.
    - **Integridad de la respuesta de la IA:** No existe un mecanismo para verificar que la respuesta de la IA no ha sido manipulada en tránsito o por un backend comprometido.
- **Impacto:** Fuga de datos, manipulación de flujos, generación de recomendaciones inseguras, ataques de ingeniería social o automatización maliciosa.
- **Recomendación:**
    - Asegurar la redacción y anonimización antes de enviar datos a la IA, revisando el contexto generado y los datos serializados.
    - Validar y sanear los prompts generados dinámicamente, limitando la capacidad del usuario para modificar instrucciones críticas.
    - No confiar ciegamente en las respuestas de la IA para acciones críticas; siempre requerir validación humana o lógica adicional antes de crear issues o modificar estados.
    - Registrar y auditar todas las interacciones con la IA, incluyendo los prompts enviados y las respuestas recibidas.
    - Implementar controles para evitar prompt injection y limitar el alcance de la IA, por ejemplo, restringiendo los comandos permitidos en los prompts.
- **Referencias de código:**
    - Generación y envío de contexto a la IA: [ContextCollector.kt](src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt#L104-L146)
    - Envío de contexto y prompts a la IA: [ApiAnalysisPanel.kt](src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt#L1-L413)

---

> Este fichero debe actualizarse con cada nueva revisión de seguridad o hallazgo relevante.
