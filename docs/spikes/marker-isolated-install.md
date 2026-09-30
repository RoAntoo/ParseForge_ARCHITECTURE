# Etapa 3A — instalación aislada de Marker

Fecha de ejecución: 2026-09-30 (America/Buenos_Aires).

## Alcance y repositorio real

Se validó un entorno experimental independiente. No se modificaron la UI, la
configuración manual del PoC ni la arquitectura de producción; no se implementó
EngineManager. La carpeta indicada en el contexto, `MVP-ParseForge`, estaba vacía.
El código, `pom.xml`, Git y los tests están en `ParseForge_ARCHITECTURE`; por eso
los entregables de texto se guardan en este repositorio.

## Entorno auditado

| Componente | Valor observado |
| --- | --- |
| Sistema | Windows 11 Pro, 10.0.26200, x86_64 |
| Python | CPython 3.12.10, MSC v.1943, AMD64 |
| Marker | marker-pdf 2.0.0 |
| Surya | surya-ocr 0.22.1 |
| PyTorch | distribución 2.14.0; runtime 2.14.0+cpu |
| Torchvision | 0.29.0 |
| CUDA | torch.version.cuda = None; cuda.is_available() = False |
| Backend predeterminado observado | llamacpp, mediante `surya.inference._autodetect_backend()` |
| llama.cpp existente | 0.5.0-dev, build 11149, commit d2e54583c, Clang 20.1.8, Windows x86_64 |

OLD: `C:\Users\rochi\marker\.venv\Scripts\marker_single.exe`.

Python base: `C:\Users\rochi\AppData\Local\Programs\Python\Python312\python.exe`.
El `pyvenv.cfg` original tiene `include-system-site-packages = false`.
No se encontraron variables del proceso con nombres Marker, Surya, Torch,
HF_, CUDA, Ollama, Python o XDG durante la auditoría inicial. Esto describe el
proceso auditado, no prueba qué variables tenía una sesión manual anterior.

El backend usa llama.cpp y servicios Python locales de fast layout y OCR error.
Los archivos de control previos identifican esos tres servidores, con PIDs
21184, 21144 y 18168 respectivamente. No se ejecutó el entrypoint OLD para
auditarlo ni se detuvieron esos procesos.

Las lecturas se hicieron con `python.exe -B`, `importlib.metadata`, `Get-Content`,
`Get-Command` y `Get-ChildItem`. Evidencia en `NEW\spike-info`: versiones de
85 distribuciones, inventario de 44.564 archivos (ruta, tamaño y mtime_ns), y
SHA-256 de Python, marker_single.exe y pyvenv.cfg anteriores.

## Directorio administrado y virtualización de Windows

NEW lógico: `%LOCALAPPDATA%\ParseForge\engines\marker`, que en esta sesión es
`C:\Users\rochi\AppData\Local\ParseForge\engines\marker`.

La ejecución desde la app Codex presenta redirección de AppData por Windows.
Python avisó al crear el venv que la ubicación física puede ser:
`C:\Users\rochi\AppData\Local\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\ParseForge\engines\marker`.
La línea de comandos real de llama-server resolvió los modelos bajo esta ruta
física. Sigue siendo el árbol privado del spike, fuera de Git y de OLD. No se
debe asumir que una app externa verá exactamente los mismos archivos usando
la ruta lógica: la siguiente etapa debe validar esto desde ParseForge.

El script de cierre resuelve tanto el directorio runtime como las rutas de los
ejecutables a su ubicación física antes de filtrar procesos. Se comprobó con
procesos simulados que acepta las rutas lógica y física de NEW y excluye OLD,
directorios hermanos y procesos ajenos; la validación no terminó procesos reales.

```text
marker/
  runtime/                  venv Python y paquetes
    llamacpp/               copia independiente del binario y DLLs
  models/
    huggingface/hub/         layout, reading order y GGUF
    datalab/                modelo de OCR error
  cache/
    pip/                    descargas de paquetes
    torch/
    home/.cache/datalab/surya/  locks, sentinelas y logs privados
  temp/                     PDF de prueba y Markdown
  spike-info/               auditoría, logs y comprobaciones
```

## Instalación reproducible

Se creó un venv nuevo con el Python base de la máquina. Se instalaron las
84 distribuciones fijadas en `scripts/spikes/marker-requirements.txt` usando
exclusivamente `NEW\runtime\Scripts\python.exe -m pip install -r ...`.
La lista reproduce todas las versiones anteriores salvo pip, que se dejó
en la versión del bootstrap. No se instaló nada globalmente.

Tras la revisión, el script de preparación instala primero los pins de Torch y
Torchvision desde `https://download.pytorch.org/whl/cpu`, sin dependencias, y
después mantiene la instalación del archivo completo de requisitos. Comprueba
los sufijos `+cpu`, la ausencia de CUDA/HIP y que Torch no se compiló con CUDA.
Los nuevos logs son `pip-install-cpu.log` y `cpu-build-check.log`. Se validó la
disponibilidad de `torch==2.14.0` y `torchvision==0.29.0` con un dry-run del índice
CPU; no se reinstaló el entorno ya verificado. El metadato de versión de esos
wheels puede incluir `+cpu`, compatible con los pins de versión base.

Se copió el directorio existente de llama.cpp de WinGet a
`NEW\runtime\llamacpp`; no se enlazó ni se ejecutó la copia del sistema
para convertir. Origen leído:
`C:\Users\rochi\AppData\Local\Microsoft\WinGet\Packages\ggml.llamacpp_Microsoft.Winget.Source_8wekyb3d8bbwe`.

Los comandos ejecutados se consolidaron en scripts experimentales; desde la
raíz del repositorio, en un destino que todavía no exista:

```powershell
.\scripts\spikes\prepare-isolated-marker.ps1 `
  -BootstrapPython 'C:\Users\rochi\AppData\Local\Programs\Python\Python312\python.exe' `
  -LlamaDirectory 'C:\Users\rochi\AppData\Local\Microsoft\WinGet\Packages\ggml.llamacpp_Microsoft.Winget.Source_8wekyb3d8bbwe'

$spikeRoot = Join-Path $env:LOCALAPPDATA 'ParseForge\engines\marker'
.\scripts\spikes\invoke-isolated-marker.ps1
.\scripts\spikes\invoke-isolated-marker.ps1 -Pdf (Join-Path $spikeRoot 'temp\stage-3a.pdf')
# Prueba opcional adicional del VLM:
.\scripts\spikes\invoke-isolated-marker.ps1 -Pdf (Join-Path $spikeRoot 'temp\stage-3a.pdf') -ForceOcr
.\scripts\spikes\stop-isolated-marker-servers.ps1
```

`prepare-isolated-marker.ps1` rechaza destinos existentes y no hace borrados.
No hace falta volver a instalar para repetir la conversión actual.
El archivo de requisitos fija versiones, pero todavía no hashes de wheels.
El Python usado debe ser 3.12.10 x64 para reproducir este entorno.

## Aislamiento del proceso

El launcher ejecuta la ruta absoluta de NEW, informa `sys.executable`,
`sys.prefix`, `sys.base_prefix` y las ubicaciones de los módulos. Cambia el
directorio de trabajo a NEW para evitar un `.env` en el repositorio.
Desactiva user-site y elimina PYTHONPATH/PYTHONHOME del proceso hijo.

Configura HF_HOME/HF_HUB_CACHE, MODEL_CACHE_DIR, TORCH_HOME, XDG_CACHE_HOME,
PIP_CACHE_DIR, TEMP/TMP y USERPROFILE/HOME dentro de NEW. Lo último es necesario
porque Surya codifica `~/.cache/datalab/surya` para sentinelas, locks y logs:
configurar solamente MODEL_CACHE_DIR no evita adjuntarse a servidores viejos.
También elimina URLs y puertos de servidores externos heredados, fuerza
llamacpp y fija LLAMA_CPP_BINARY a la copia privada. Mantiene vivo el servidor
VLM para cerrarlo explícitamente con el script de limpieza. Las variables se restauran
al terminar el script; no se modifican variables persistentes del usuario.

La información de procesos de Windows confirma que el launcher y los servicios
Python usan argumentos de NEW. Un venv Windows también inicia el Python base
del sistema: esto sigue siendo una dependencia de ejecución, no sólo una
herramienta que se pueda quitar después del bootstrap. Los paquetes permanecen
en el venv, pero todavía no hay un runtime portable autónomo.

## Verificaciones y conversión

`pip install` finalizó con 0. `pip check`: `No broken requirements found`.
Todas las versiones de las 84 distribuciones fijadas coinciden con OLD.
El `marker_single.exe --help` de NEW funcionó, código 0.

Se generó un PDF digital de una página con contenido conocido mediante
`new-marker-fixture.py`, sin instalar paquetes extra. No se usaron documentos
personales ni servicios remotos para procesar su contenido.

La conversión predeterminada terminó con código 0 en 46,19 segundos reportados
por Marker. Resultado: `NEW\temp\output\stage-3a\stage-3a.md`:

```markdown
## ParseForge Stage 3A

Isolated Marker conversion smoke test.

The expected result is readable Markdown.
```

Fast layout y OCR error se iniciaron desde NEW en los puertos 49715 y 49778.
Este PDF digital no necesita iniciar el VLM; por eso se hizo una segunda
prueba con `--force_ocr` y un archivo separado, `stage-3a-ocr.pdf`.

## Modelos y descargas

Los paquetes se descargaron de PyPI mediante pip; no se copió site-packages
del entorno viejo. Incluyen Torch CPU y bibliotecas nativas de PDFium/OpenCV.
El binario llama.cpp y sus DLLs se copiaron de WinGet: no hubo descarga de
llama.cpp en este spike.

Los modelos se descargaron con cachés nuevas y vacías, bajo NEW. No se copiaron
las cachés viejas. Orígenes y pesos principales:

| Modelo | Origen | Peso principal |
| --- | --- | --- |
| Fast layout | huggingface.co/datalab-to/surya_layout2 | rfdetr_layout.pth: 134.915.547 bytes |
| Reading order | mismo repositorio, subcarpeta order | order_ar.pt: 7.072.639 bytes |
| OCR error | models.datalab.to, ocr_error_detection/2025_02_18 | model.safetensors: 270.664.948 bytes |
| VLM OCR | huggingface.co/datalab-to/surya-ocr-2-gguf | surya-2.gguf: 1.266.400.864 bytes; mmproj: 204.986.688 bytes |

También se descargan configuraciones, tokenizadores y manifiestos. Los modelos
se obtienen al primer uso del servicio, no al ejecutar `--help` ni al instalar
el paquete. HF conserva snapshots por revisión; en este equipo: layout
`0aee81d5fd9275c0582e545bf3a56944b1e75679` y GGUF
`6a3a4c30e5e74446d4f8b6afd05b2f2da970f470`.
Las peticiones pueden comprobar metadatos en red aunque los pesos estén en
caché; este spike no acredita funcionamiento completamente offline.

Ocupación observada: runtime 1,36 GB, modelos 1,89 GB, caché 290 MB (unidades
decimales). La caché pip aproxima el volumen de wheels descargados; la medida
incluye metadatos y no representa bytes exactos transferidos por la red.
La ocupación de modelos incluye archivos auxiliares y posibles copias de la
caché HF en Windows. No se descargaron pesos nuevos durante `--help`.

Las cachés originales auditadas, que no se usaron como destino, eran:
`C:\Users\rochi\.cache\huggingface\hub` y
`C:\Users\rochi\AppData\Local\datalab\datalab\Cache\models`.

## Problemas y necesidades de automatización

- El contexto apuntaba a un repositorio vacío; los entregables se ubican donde
  realmente está el PoC.
- La redirección AppData de Codex debe contemplarse al transferir el resultado
  a una aplicación Windows externa.
- Un primer launcher falló por pasar variables opcionales con cadenas vacías;
  Pydantic rechazó cuatro puertos. Se corrigió eliminando las variables con
  `Remove-Item Env:...`, y la prueba posterior funcionó.
- Marker es un paquete namespace: `marker.__file__` devuelve None. La evidencia
  correcta de su ubicación es `marker.__path__`.
- Surya mantiene vivos los servicios de fast layout/OCR error incluso con
  SURYA_INFERENCE_KEEP_ALIVE=false. Hay que gestionar su árbol de procesos.
  La primera prueba OCR generó correctamente su salida, pero la sesión de
  ejecución terminó con 1 antes de registrar el código del launcher. No se
  considera una finalización normal. Se repitió con KEEP_ALIVE=true y cierre
  explícito posterior, evitando el camino de limpieza atexit de Surya.
- La descarga anónima de HF mostró un aviso de rate limit; no se necesitó token.
- La instalación fija versiones de paquetes, pero la automatización final
  necesitará hashes, revisiones de modelos, descargas verificables, tratamiento
  de fallos y un manifiesto de runtime/backend.

## Tests de ParseForge

`mvn test`: BUILD SUCCESS, 16 tests, 0 fallos, 0 errores, 0 omitidos.
Se conservaron los avisos existentes de Mockito/Byte Buddy sobre agentes
dinámicos. No se cambió código de producción para hacer pasar los tests.

## Recomendación para 3B

Validar un runtime CPython privado x64 y el paquete completo de llama.cpp desde
el proceso real de ParseForge. Definir primero un manifiesto con versiones,
hashes, rutas de modelos y variables por proceso, más el cierre de los
servidores persistentes. El venv de 3A valida paquetes aislados pero sigue
dependiendo del Python base del usuario. No se inició 3B.

## Resultado

La conversión digital está verificada. La primera conversión con OCR forzado
produjo el mismo contenido esperado en 87,14 segundos, con la anomalía de cierre
descrita arriba. La repetición con KEEP_ALIVE=true terminó normalmente con
código 0 en 61,12 segundos reportados por Marker y conservó el texto esperado.
Se observó llama-server privado (PID 9732), puerto 56980, leyendo los dos GGUF
de NEW; la petición generó 143 tokens y reportó `truncated = 0`.

La auditoría posterior mantiene los 44.564 archivos de OLD con los mismos
tamaños y mtime_ns, y los tres hashes críticos coinciden. No hay diferencias
entre las versiones de las dependencias fijadas de OLD y NEW. Esto es evidencia
de integridad por inventario y hashes críticos, no un hash de cada archivo.

Evidencia completa local bajo `NEW\spike-info`: `old-files-before.json`,
`old-hashes-before.json`, `old-packages.json`, `new-packages.json`,
`verification.json`, `pip-install.log`, `entrypoint-help.log`, `conversion.log`,
`conversion-ocr.log`, `process-evidence.json`, `llama-process-evidence.json` y
`sizes.json`, `exit-codes.json`, `conversion-ocr-repeat.log`,
`process-evidence-repeat.json`, `server-cleanup-repeat.log` y
`parseforge-tests.log`. Se cerraron los árboles de servidores experimentales
con el script de limpieza, comprobando cero procesos restantes de NEW. Los
artefactos pesados permanecen fuera de Git.

Los criterios del spike están satisfechos: instalación y dependencias
separadas, entrypoint nuevo, conversión real, versiones fijadas, instalación
vieja conservada, modelos/backend identificados y los 16 tests existentes
aprobados. La autonomía del runtime y el acceso desde una app externa quedan
como trabajo explícito de 3B.

SPIKE RESULT: SUCCESS
