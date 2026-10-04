# Stage 9 — investigación de progreso real

2026-10-03 · America/Buenos_Aires · ParseForge 0.1.0.

**Decisión: NO-GO para porcentaje de conversión (global o de OCR/layout) y ETA en
los perfiles instalados. Resultado: ABORTED BY DESIGN.** Se preserva la UI existente.
La conclusión se apoya en la matriz observada y en el código exacto instalado,
no sólo en la ausencia de mensajes durante una espera.

## Versiones y método

Marker 2.0.0, Surya 0.22.1, tqdm 4.70.1; MarkItDown 0.1.8,
pdfminer.six 20260107, pdfplumber 0.11.10; CPython privado 3.12.10 x64.
CPU con llama.cpp y modelos locales, sin instalar nuevas dependencias. Se conserva
HF_HUB_OFFLINE=1 y la comunicación local con servidores del motor; no se realizó
una auditoría de tráfico de red.
Se usaron los entrypoints, argumentos y entorno de los runtimes administrados de
ParseForge. Un Windows Job Object con kill-on-close contiene cada árbol de procesos.
Es un spike directo del CLI: no certifica publicación de resultados por la aplicación.

Fixtures sintéticas de Stage 7: digital corto, escaneado corto y mixto de 2 páginas;
digital largo y escaneado largo de 240 páginas; PDF corrupto. Digital corto repetido
en Marker y 240 páginas repetidas en MarkItDown para observar consistencia.
Captura de pipes binaria, chunks con timestamps, stdout/stderr separados, snapshots
CIM de procesos (identidad, padre y comando) e inventario temporal/salidas.
Los logs sanitizados preservan CR y ruido; los originales binarios permanecen en build.
Las capturas CIM no son un censo perfecto de procesos de vida muy breve.

## Ejecuciones reales

COMPLETED significa proceso con exit 0; **no significa conversión validada por
ParseForge**. TIMEOUT_INCOMPLETE significa observación limitada a 240 segundos.
La cancelación de MarkItDown se solicita a los 2 segundos; no depende de progreso.
El intento de 240 páginas terminó antes de cancelar. Se repitió con un PDF sintético
de 15.000 páginas para comprobar una interrupción real; ambos intentos se conservan.

| Motor | Caso | Estado del proceso | Segundos con cierre/monitor | stdout B | stderr B | Archivos finales |
| --- | --- | --- | ---: | ---: | ---: | --- |
| marker | digital-short | COMPLETED | 86.297 | 0 | 268 | digital-text/digital-text.md (164 B), digital-text/digital-text_meta.json (1103 B) |
| marker | digital-long | COMPLETED | 144.0 | 0 | 267 | digital-long/digital-long.md (0 B), digital-long/digital-long_meta.json (120235 B) |
| marker | scanned-short | COMPLETED | 121.172 | 0 | 272 | scanned-image-only/scanned-image-only.md (1080 B), scanned-image-only/scanned-image-only_meta.json (887 B) |
| marker | scanned-long | TIMEOUT_INCOMPLETE | 241.422 | 0 | 0 | — |
| marker | mixed | COMPLETED | 107.125 | 0 | 251 | mixed/mixed.md (622 B), mixed/mixed_meta.json (985 B) |
| marker | digital-repeat | COMPLETED | 77.64 | 0 | 268 | digital-text/digital-text.md (164 B), digital-text/digital-text_meta.json (1103 B) |
| markitdown | digital-short | COMPLETED | 1.547 | 0 | 0 | digital-text.md (169 B) |
| markitdown | digital-long | COMPLETED | 2.875 | 0 | 0 | digital-long.md (20399 B) |
| markitdown | many-pages | COMPLETED | 1.813 | 0 | 0 | digital-long.md (20399 B) |
| markitdown | corrupt | FAILED | 1.39 | 0 | 372 | — |
| markitdown | cancel | COMPLETED | 1.844 | 0 | 0 | digital-long.md (20399 B) |
| markitdown | cancel-many-pages | CANCELLED | 2.078 | 0 | 0 | — |

El Markdown digital largo de Marker debe evaluarse por su tamaño en la tabla:
un exit 0 con Markdown vacío no es prueba de conversión exitosa. El adaptador de
producción conserva su validación de salida. Los tiempos son observaciones locales,
no pesos, ETA ni promesas de rendimiento.

## Señales y clasificación

| Señal | Clasificación para progreso de documento | Motivo |
| --- | --- | --- |
| Marker: Saved markdown / Total time | PARTIALLY_RELIABLE | Eventos terminales reales, sin avance ni total |
| OCR-error tqdm | UNRELIABLE | El servidor instalado fuerza disable_tqdm=True; no aparece en la conversión administrada |
| Loading weights current/total | UNRELIABLE | Cuenta pesos del modelo, no páginas; log compartido con historial, sin correlación con el trabajo |
| Procesos fast_layout, ocr_error, llama-server | PARTIALLY_RELIABLE | Describe servicios disponibles; su presencia no demuestra fase activa ni porcentaje |
| Tokens, slots, límites llama.cpp | UNRELIABLE | Límite máximo no es cantidad de trabajo requerida; hay reintentos/fallback |
| Tamaño de temporales, logs y salidas | UNRELIABLE | No se probó una relación determinista con progreso |
| MarkItDown silencioso | UNRELIABLE | Sin señal cuantificada en ejecuciones válidas |

No se halló una señal RELIABLE de progreso del documento o de sus fases de OCR/layout.
Los contadores de carga de modelos son reales **para carga de modelos**, pero no
justifican una barra de conversión. No se reinterpretan como páginas procesadas.

## Revisión del código instalado

- marker/scripts/convert_single.py ejecuta converter(fpath), luego save_output y
  publica los mensajes finales; no publica current/total durante converter.
- surya/common/batch_service/client.py redirige stdout/stderr de sus servidores a
  archivos append-only compartidos. No llegan al pipe principal de Marker.
- surya/ocr_error/server.py desactiva explícitamente tqdm. La barra presente en
  ocr_error/__init__.py no demuestra disponibilidad en el perfil administrado.
- surya/inference/backends/openai_client.py espera executor.map y tiene reintentos;
  no expone un callback de avance. Recognition puede regenerar páginas y recurrir
  a block-mode fallback. La cantidad de páginas no equivale a todo el trabajo.
- MarkItDown PdfConverter acumula resultados y puede repetir extracción con
  pdfminer después de pdfplumber. No emite progreso por página ni total al CLI.

Los hashes SHA-256 de estos archivos y las versiones leídas de importlib.metadata
están en [la evidencia](../release/STAGE_9_EVIDENCE.json).
Se consultaron también las fuentes primarias oficiales de
[Marker](https://github.com/datalab-to/marker) y
[MarkItDown PdfConverter](https://github.com/microsoft/markitdown/blob/main/packages/markitdown/src/markitdown/converters/_pdf_converter.py).
La decisión depende del código instalado, no de asumir que main coincide con él.

## Logs y artefactos

[Fixtures sanitizadas](fixtures/stage9/) incluyen stdout, stderr, eventos con
timestamps, snapshots de procesos e inventarios, y resumen por caso.
shared-server-logs contiene historial de invocaciones anteriores y de este spike;
se conserva como evidencia de que esos archivos no están vinculados a una conversión.
No se usa ese historial como avance de esta ejecución.

## Decisión y alcance

No se implementó una barra 1–100 porque el motor no expone una señal suficientemente
fiable. ParseForge conserva progreso indeterminado para evitar mostrar información falsa.
Se preservan tiempo transcurrido, fases propias de inicio/guardado y Cancelar.
No se agregó parser de logs, porcentaje por fase, EngineProgress ni ETA.

Se ejecutaron todos los tipos de documento de la matriz. Las observaciones largas
limitadas, si las hay, están marcadas explícitamente: no se afirma haber completado
un libro escaneado. El alcance es este perfil y estas versiones; una futura versión
con callbacks o eventos estructurados requiere un spike nuevo. No se probaron cloud,
nuevos motores, OCR externo, VM limpia ni PDFs personales.

## Verificación y reproducción

39 pruebas existentes de MarkerEngineTest, MarkItDownEngineTest,
ManagedEngineManagerTest y LocalProcessExecutorTest: PASS. Cubren que porcentajes
de etapas no se convierten en progreso global y que un listener fallido no impide
terminar el proceso. Stage7UiTest opt-in, escala 1.0: PASS; comprueba indicador
indeterminado y cancelación. No hay nuevo parser, por lo que sus tests y los de ETA
no aplican. No se ejecuta release.ps1: no hubo cambios de producción.

```powershell
python scripts/spikes/stage9-progress.py --marker-limit 240
python scripts/spikes/stage9-report.py build/stage9-progress/<run-id>
```

Requiere los dos runtimes ya instalados y los PDFs de build/stage7-host/fixtures.
El spike no instala ni descarga dependencias. Puede prolongarse hasta 24 minutos
en Marker por sus seis probes; el límite es de investigación y no modifica el timeout
de producción. Un límite más alto permite extender observaciones sin fabricar progreso.
