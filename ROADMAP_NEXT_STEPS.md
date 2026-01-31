ROADMAP — API ANALYSIS (Siguientes pasos)

Resumen actual
- Fase 1: Parseo OpenAPI, modelos internos, `DataClassifier`, panel UI y `ContextCollector` — COMPLETADO.
- Fase 2: `ApiFlowAnalyzer` (detección de flujos CRUD y auth) e integración básica en la UI — COMPLETADO.

Prioridad y objetivos inmediatos (Fase 3)
- Objetivo: Mitigar riesgos operativos y de privacidad antes de enviar contextos a proveedores IA.
- Prioridades:
  1. Implementar rate-limiter en puntos de entrada de análisis (`ApiAnalysisPanel.analyzeWithAI()`).
  2. Añadir comprobaciones RBAC en `ContextCollector.fromApiSpec()` para validar permisos de subida/análisis.
  3. Añadir límites de tamaño y recuento de endpoints para rechazar/specs enormes (defensa por umbral).
- Entregables:
  - Código: `src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt` (rate-limiter + umbrales).
  - Código: `src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt` (RBAC hooks).
  - Tests: unitarios que simulen abuso (peticiones repetidas y specs grandes).
  - Docs: secciones de `Security.md` y `vulns.md` actualizadas con evidencia y TODOs.
- Estimación: 1-2 días de trabajo con tests locales.

Fase 4 — Visualización y export
- Objetivo: Mostrar diagramas de flujos detectados y permitir export (JSON/Graphviz).
- Tareas:
  - Generar resumen exportable `ApiFlow` → JSON y `graphviz`.
  - Añadir vista detallada en `ApiAnalysisPanel` con árbol/diagrama interactivo.
  - Añadir opción de exportar reporte (ZIP) con contexto redacted + flows.
- Archivo(s) relevantes: `src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/ApiFlowAnalyzer.kt`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt`.

Fase 5 — Harden IA integration & audit
- Objetivo: Asegurar trazabilidad y control de datos enviados a modelos externos.
- Tareas:
  - Forzar `PrivacyMode` mínimo (`STRICT` o `BALANCED`) configurable por política corporativa.
  - Registrar (audit log) hashes/metadata de los contextos enviados, sin exponer datos sensibles.
  - Implementar pruebas de regresión que verifiquen redaction antes de enviar.
  - Implementar límites de tokens y chunking para specs grandes.
- Entregables: hooks de auditoría, configuraciones en `gradle.properties` o `application.conf`.

Fase 6 — CI, pruebas y despliegue
- Objetivo: Integrar la suite de tests en CI y añadir análisis estático y cobertura.
- Tareas:
  - Pipeline: `./gradlew test`, `./gradlew check` en CI (GitHub Actions o similar).
  - Añadir job nocturno que analice repositorios y detecte endpoints críticos.
  - Medir cobertura y añadir pruebas faltantes para `ApiFlowAnalyzer` y `DataClassifier`.

Siguientes pasos inmediatos (para ejecutar ahora)
- Reanudar ejecución de tests localmente (el contenedor actual falla con ENOPRO). Comandos sugeridos:

```bash
./gradlew test --no-daemon
```

- ¿Deseas que implemente ahora el `rate-limiter` en `ApiAnalysisPanel` o que prepare los parches para RBAC en `ContextCollector` primero?

Referencias rápidas
- Panel UI: src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/ApiAnalysisPanel.kt
- Analizador de flujos: src/main/kotlin/com/six2dez/burp/aiagent/context/openapi/ApiFlowAnalyzer.kt
- Context collector: src/main/kotlin/com/six2dez/burp/aiagent/context/ContextCollector.kt
- Tests añadidos: src/test/kotlin/com/six2dez/burp/aiagent/context/openapi/

---
Historial: generado el 30-01-2026 por el agente. Mantener este fichero como guía de roadmap y actualizar conforme avance el trabajo.
