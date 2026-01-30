# Ejemplo de TODOs para mitigaciones pendientes

// src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt

// TODO: Implementar lógica de rate limiting para evitar abuso en la carga y análisis de especificaciones OpenAPI.
// Referencia: Security.md (sección 7 y requisitos sin mitigación), vulns.md (vulnerabilidad 1)

// Ejemplo de punto de inserción:
// fun loadSpecFromFile(file: File) {
//     // TODO: Añadir comprobación de número de peticiones por usuario/IP
//     ...
// }

// fun analyzeWithAI() {
//     // TODO: Limitar la frecuencia de análisis por usuario/IP
//     ...
// }

// src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt

// TODO: Añadir validación de roles y permisos antes de procesar contextos sensibles.
// Referencia: Security.md (sección 2), vulns.md (vulnerabilidad 2)

// src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/OpenApiParser.kt

// TODO: Validar estructura y autenticidad de payloads externos antes de procesar.
// Referencia: Security.md (sección 9), vulns.md (vulnerabilidad 6)

// src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt

// TODO: Mostrar mensajes de error genéricos al usuario y registrar detalles solo en logs internos.
// Referencia: Security.md (sección 6), vulns.md (vulnerabilidad 4)

// src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt

// TODO: Revisar que la redacción y anonimización se apliquen siempre antes de loggear o enviar datos a la IA.
// Referencia: Security.md (sección 3 y 5), vulns.md (vulnerabilidad 3 y 7)

// src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/DataClassifierTest.kt

// TODO: Añadir tests de lógica de negocio para flujos multi-step y validación de estados.
// Referencia: Security.md (sección 10), vulns.md (vulnerabilidad 5)
