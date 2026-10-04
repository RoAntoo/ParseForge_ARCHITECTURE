"""Archive sanitized evidence and report a finished Stage 9 matrix. No production edits."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

REPO = Path(__file__).resolve().parents[2]
BASE = Path(sys.argv[1]).resolve()
SUMMARY = json.loads((BASE / 'summary.json').read_text(encoding='utf-8'))
assert len(SUMMARY) in (11, 12), 'Wait for the entire matrix before reporting'
assert any(e['engine'] == 'markitdown' and e['status'] == 'CANCELLED' for e in SUMMARY), 'An actual cancellation is required, not a fast completion'
ARCHIVE = REPO / 'docs/spikes/fixtures/stage9'
ARCHIVE.mkdir(parents=True, exist_ok=True)
archive_outputs = set()
ROOT = Path(os.environ['LOCALAPPDATA']) / 'ParseForge/engines'


def sanitize(value):
    if isinstance(value, dict):
        return {sanitize(k): sanitize(v) for k, v in value.items()}
    if isinstance(value, list):
        return [sanitize(x) for x in value]
    if not isinstance(value, str):
        return value
    for path, token in [(str(REPO), '<REPO>'), (str(ROOT / 'marker'), '<ENGINE:marker>'), (str(ROOT / 'markitdown'), '<ENGINE:markitdown>'), (str(Path.home()), '<USER>')]:
        value = value.replace(path, token).replace(path.replace('\\', '/'), token)
    return value


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding='utf-8', newline='\n')
    if path.is_relative_to(ARCHIVE):
        archive_outputs.add(path)


versions = {}
for engine, packages in [('marker', ['marker-pdf', 'surya-ocr', 'tqdm']), ('markitdown', ['markitdown', 'pdfminer-six', 'pdfplumber'])]:
    code = 'import sys,json,importlib.metadata as m; print(json.dumps(dict(python=sys.version, packages={x:m.version(x) for x in ' + repr(packages) + '})))'
    result = subprocess.run([str(ROOT / engine / 'runtime/python/python.exe'), '-I', '-X', 'utf8', '-u', '-B', '-c', code], capture_output=True, text=True, check=True, timeout=30)
    versions[engine] = json.loads(result.stdout)

marker_packages = versions['marker']['packages']
markitdown_packages = versions['markitdown']['packages']
engine_versions = (f"Marker {marker_packages['marker-pdf']} / Surya {marker_packages['surya-ocr']} "
                   f"y MarkItDown {markitdown_packages['markitdown']}")
runtime_versions = (f"Marker {marker_packages['marker-pdf']}, Surya {marker_packages['surya-ocr']}, "
                    f"tqdm {marker_packages['tqdm']}; MarkItDown {markitdown_packages['markitdown']},\n"
                    f"pdfminer.six {markitdown_packages['pdfminer-six']}, pdfplumber {markitdown_packages['pdfplumber']}.\n"
                    f"Python privado de Marker: {versions['marker']['python']}.\n"
                    f"Python privado de MarkItDown: {versions['markitdown']['python']}.\n")

source_hashes = {}
source_files = [('marker', name) for name in ['marker/scripts/convert_single.py', 'surya/common/batch_service/client.py', 'surya/common/batch_service/server.py', 'surya/ocr_error/server.py', 'surya/ocr_error/__init__.py', 'surya/fast_layout/__init__.py', 'surya/layout/__init__.py', 'surya/recognition/__init__.py', 'surya/inference/backends/openai_client.py', 'surya/inference/backends/llamacpp.py']]
source_files += [('markitdown', 'markitdown/converters/_pdf_converter.py'), ('markitdown', 'markitdown/__main__.py')]
for engine, name in source_files:
    source_hashes[engine + '/' + name] = digest(ROOT / engine / 'runtime/python/Lib/site-packages' / name)

for entry in SUMMARY:
    source = REPO / entry['logDirectory']
    target = ARCHIVE / entry['engine'] / entry['case']
    target.mkdir(parents=True, exist_ok=True)
    events = [json.loads(line) for line in (source / 'events.jsonl').read_text(encoding='utf-8').splitlines()]
    for event in events:
        # Early harness revision serialized CIM JSON inside a string; normalize it.
        if event['kind'] == 'process_snapshot' and event['data'].get('json'):
            event['data']['processes'] = json.loads(event['data'].pop('json'))
    write(target / 'events.jsonl', ''.join(json.dumps(sanitize(event), ensure_ascii=False) + '\n' for event in events))
    for stream in ['stdout', 'stderr']:
        write(target / (stream + '.log'), sanitize((source / (stream + '.raw')).read_bytes().decode('utf-8', errors='replace')))
    entry['rawStreamHashes'] = {stream: digest(source / (stream + '.raw')) for stream in ['stdout', 'stderr']}
    entry['fixtureDirectory'] = target.relative_to(REPO).as_posix()
    entry['stdoutBytes'] = (source / 'stdout.raw').stat().st_size
    entry['stderrBytes'] = (source / 'stderr.raw').stat().st_size
    entry['processSnapshots'] = sum(e['kind'] == 'process_snapshot' for e in events)
    entry['inventoryChanges'] = sum(e['kind'] == 'inventory' for e in events)
    write(target / 'summary.json', json.dumps(entry, indent=2, ensure_ascii=False) + '\n')

# Shared append-only server files intentionally include prior invocations. They
# cannot establish per-conversion ownership or completion and are labelled so.
server_cache = ROOT / 'marker/cache/home/.cache/datalab/surya'
for path in server_cache.glob('*.log'):
    write(ARCHIVE / 'shared-server-logs' / path.name, sanitize(path.read_text(encoding='utf-8', errors='replace')))

for name in ['stage9-tests.log', 'stage9-ui.log']:
    log = REPO / 'build' / name
    content = log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESS' in content, 'Tests must pass before reporting: ' + name
    write(ARCHIVE / 'tests' / name, sanitize(content))

evidence = dict(date='2026-10-03', timezone='America/Buenos_Aires', stage=9, decision='NO-GO', result='ABORTED BY DESIGN', scope='Installed versions and CPU/llamacpp managed profile only', productionChanges=False, releaseRun=False, versions=versions, sourceHashes=source_hashes, runs=SUMMARY, longProbeLimitSeconds=240, limitations=['Synthetic PDFs; not a representative corpus of scanned books.', 'TIMEOUT_INCOMPLETE means bounded observation, not a completed conversion.', 'COMPLETED describes CLI exit 0, not ParseForge output validation.', 'Shared server logs contain prior invocations and are not job-scoped.', 'CIM snapshots every approximately 2-3 seconds can miss short-lived children.', 'No full book throughput, clean-VM or ETA validation claimed.'], tests={'functional': {'passed': 39, 'failed': 0, 'log': 'build/stage9-tests.log'}, 'ui': {'passed': 1, 'failed': 0, 'suite': 'Stage7UiTest', 'scale': '1.0', 'log': 'build/stage9-ui.log'}}, signals=[{'signal': 'Marker main stdout/stderr', 'classification': 'PARTIALLY_RELIABLE', 'use': 'Terminal saved/time messages, errors; no current/total'}, {'signal': 'OCR-error tqdm', 'classification': 'UNRELIABLE', 'use': 'Disabled by installed server; not exposed in actual managed execution'}, {'signal': 'Shared Loading weights counters', 'classification': 'UNRELIABLE', 'use': 'Real model-load counts; not document progress, not job-scoped, includes history'}, {'signal': 'Child server presence', 'classification': 'PARTIALLY_RELIABLE', 'use': 'Service readiness/lifecycle only; not active processing phase or percent'}, {'signal': 'llama tokens/slots', 'classification': 'UNRELIABLE', 'use': 'Variable generation work; max tokens is a ceiling, not total required work'}, {'signal': 'Temp/output sizes', 'classification': 'UNRELIABLE', 'use': 'No proven deterministic relation to conversion completion'}, {'signal': 'MarkItDown successful CLI', 'classification': 'UNRELIABLE', 'use': 'Silent during processing; no current/total'}])
archive_hashes = {p.relative_to(REPO).as_posix(): digest(p) for p in sorted(archive_outputs)}
evidence['fixtureHashes'] = archive_hashes
process_query = "$ErrorActionPreference='Stop'; @(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith('" + str(ROOT).replace("'", "''") + "\\', [StringComparison]::OrdinalIgnoreCase) } | Select-Object ProcessId,ParentProcessId,ExecutablePath,CreationDate) | ConvertTo-Json -Compress"
audit = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', process_query], capture_output=True, text=True, check=True, timeout=30)
evidence['runtimeProcessesAfterMatrix'] = sanitize(json.loads(audit.stdout) if audit.stdout.strip() else [])
write(REPO / 'docs/release/STAGE_9_EVIDENCE.json', json.dumps(evidence, indent=2, ensure_ascii=False) + '\n')

rows = '\n'.join('| ' + ' | '.join([e['engine'], e['case'], e['status'], str(e['seconds']), str(e['stdoutBytes']), str(e['stderrBytes']), ', '.join(k + ' (' + str(v['bytes']) + ' B)' for k, v in e['outputs'].items()) or '—']) + ' |' for e in SUMMARY)
write(REPO / 'docs/spikes/STAGE_9_PROGRESS_INVESTIGATION.md', '''# Stage 9 — investigación de progreso real

2026-10-03 · America/Buenos_Aires · ParseForge 0.1.0.

**Decisión: NO-GO para porcentaje de conversión (global o de OCR/layout) y ETA en
los perfiles instalados. Resultado: ABORTED BY DESIGN.** Se preserva la UI existente.
La conclusión se apoya en la matriz observada y en el código exacto instalado,
no sólo en la ausencia de mensajes durante una espera.

## Versiones y método

''' + runtime_versions + '''
CPU con llama.cpp y modelos locales, sin instalar nuevas dependencias. Se conserva
HF_HUB_OFFLINE=1 y la comunicación local con servidores del motor; no se realizó
una auditoría de tráfico de red.
Se usan los argumentos y entorno de los runtimes administrados de ParseForge.
El spike actual invoca directamente el CLI de MarkItDown sin la prevalidación de
PDFMiner que conserva producción. Las matrices anteriores pueden incluirla: los
comandos exactos usados en cada ejecución están en events.jsonl.
Un Windows Job Object con kill-on-close contiene cada árbol de procesos.
Es un spike directo del CLI: no certifica publicación de resultados por la aplicación.

Fixtures sintéticas de Stage 7: digital corto, escaneado corto y mixto de 2 páginas;
digital largo y escaneado largo de 240 páginas; PDF corrupto. Digital corto repetido
en Marker y 240 páginas repetidas en MarkItDown para observar consistencia.
Captura de pipes binaria, líneas sanitizadas con timestamps, stdout/stderr separados, snapshots
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
''' + rows + '''

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
''')
write(REPO / 'docs/release/STAGE_9_RESULT.md', '''# ParseForge — resultado de Stage 9

2026-10-03 · America/Buenos_Aires · versión 0.1.0.

**NO-GO — ABORTED BY DESIGN.** No continuar con porcentaje de conversión ni ETA
para ''' + engine_versions + ''' en los perfiles actuales.

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
''')
print(json.dumps({'archivedFiles': len(archive_hashes), 'report': 'docs/release/STAGE_9_RESULT.md', 'statuses': [(e['engine'], e['case'], e['status']) for e in SUMMARY]}, ensure_ascii=False))
