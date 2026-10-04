# ParseForge — resultado de Stage 9

2026-10-03 · America/Buenos_Aires · versión 0.1.0.

**NO-GO — ABORTED BY DESIGN.** No continuar con porcentaje de conversión ni ETA
para Marker 2.0.0 / Surya 0.22.1 y MarkItDown 0.1.8 en los perfiles actuales.

No se implementó una barra 1–100 porque el motor no expone una señal suficientemente
fiable. ParseForge conserva progreso indeterminado para evitar mostrar información falsa.

Se instrumentaron ambos CLIs con PDFs sintéticos, stdout/stderr binarios, timestamps,
procesos hijos e inventarios temporales. Marker sólo ofrece mensajes terminales en
el pipe principal; sus servidores no publican contadores fiables de OCR/layout.
Los contadores de carga de pesos en logs compartidos no representan conversión.
MarkItDown no expone progreso cuantificado durante el procesamiento.

Se conservan indicador indeterminado, tiempo transcurrido, fases propias y Cancelar.
No hubo cambios de producción ni release. 39 pruebas funcionales y Stage7UiTest
al 100%: PASS. No se atribuyen pruebas de un parser o ETA que no se implementaron.

La investigación cubre la matriz indicada; las ejecuciones limitadas se identifican
como TIMEOUT_INCOMPLETE y no se presentan como conversiones completas.
No se certifica throughput de libros reales ni compatibilidad de VM limpia.

- [Investigación, matriz, clasificación y límites](../spikes/STAGE_9_PROGRESS_INVESTIGATION.md)
- [Evidencia con versiones, hashes, resultados y tests](STAGE_9_EVIDENCE.json)
- [Logs reales sanitizados](../spikes/fixtures/stage9/)

Stage 9 se cierra por el camino NO-GO para el alcance probado. Reabrir sólo ante una
señal estructurada o un contador de fase observable, correlacionado con el trabajo y
estable en múltiples PDFs. La falta de señal fiable no se compensa con porcentajes
por tiempo, pesos de fases ni tamaños de archivos.
