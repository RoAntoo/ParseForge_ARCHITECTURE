# Etapa 3B — runtime autónomo de Marker

Fecha: 2026-09-30, America/Buenos_Aires. Repositorio de trabajo:
`C:\Users\rochi\Documents\GitHub\ParseForge_ARCHITECTURE`.

## Resultado y alcance

Java ejecutó Marker con CPython y llama.cpp privados, convirtió un PDF digital y
otro con OCR forzado, capturó ambas salidas y sus códigos, y canceló una tercera
conversión con llama-server activo. No quedaron procesos privados vivos.
`mvn clean verify`: **BUILD SUCCESS, 24 tests, cero fallos/errores/omitidos**.

La preparación se repitió en una segunda ubicación, desde archivos descargados
verificados, sin copiar un venv ni site-packages: también obtuvo READY desde Java.
No se implementaron UI de instalación, EngineManager, updater ni Etapa 3C.
Los binarios, wheels y modelos permanecen fuera de Git.

## Entorno probado

Windows 11 Pro x64, 10.0.26200; Java Oracle JDK 26, compilación para Java 21;
Maven 3.9.14. CPython 3.12.10 x64; marker-pdf 2.0.0; surya-ocr 0.22.1;
torch 2.14.0+cpu; torchvision 0.29.0+cpu. Sin CUDA/HIP.

## Estrategia de CPython

Se eligió el [paquete embeddable oficial de CPython 3.12.10](https://www.python.org/downloads/release/python-31210/),
Windows AMD64, preparado como runtime de aplicación. Archivo de aproximadamente
10,6 MB. No se crea un venv, no se usa `py.exe` y no se invoca Python global,
ni siquiera para preparar el entorno.

Origen exacto y SHA-256 del ZIP:

```text
https://www.python.org/ftp/python/3.12.10/python-3.12.10-embed-amd64.zip
4acbed6dd1c744b0376e3b1cf57ce906f9dc9e95e68824584c8099a63025a3c3
```

SHA-256 de python.exe instalado:
`4d6f5f81a4bca11191c4c7c6b43632694d0a4ce74e068619d8fdc161d469859a`.

Se extrae la biblioteca estándar compilada a `Lib`, se configura
`python312._pth` con `Lib`, `.`, `Lib/site-packages` e `import site`, y se extrae
el wheel verificado de pip 25.0.1. El propio Python privado descarga wheels
binarios; Torch/Torchvision provienen del índice CPU oficial. Se comparan todos
los hashes con `marker-autonomous-requirements.lock` antes de instalar, y se
instala con `--no-index --require-hashes`. `pip check` pasó.

El uso de pip es una herramienta experimental de preparación: [CPython documenta
que el embeddable no admite la gestión ordinaria de dependencias con pip](https://docs.python.org/3.12/using/windows.html#the-embeddable-package).
Aquí ParseForge prepara y fija un conjunto de wheels, sin dejar su selección al
usuario. No admite sdists, compilación de extensiones, Tcl/Tk ni un entorno de
desarrollo completo. Marker y PyTorch CPU se importaron y funcionaron en las
conversiones reales. Python 3.12.10 se conserva para reproducir 3A; la estrategia
de actualizaciones de seguridad debe resolverse antes de distribuir producción.

## Estrategia de llama.cpp

[Release upstream b11149](https://github.com/ggml-org/llama.cpp/releases/tag/b11149),
paquete `llama-b11149-bin-win-cpu-x64.zip`, backend CPU x64.
Versión observada: 0.5.0-dev, build 11149, commit d2e54583c, Clang 20.1.8.

```text
https://github.com/ggml-org/llama.cpp/releases/download/b11149/llama-b11149-bin-win-cpu-x64.zip
SHA256 ZIP: d1cb5f9ef7bbb7068954b4c9767d5b5309e20bcefeb61d4aafc47f9581f38752
SHA256 llama-server.exe: 36e2803d3bc1c87ff21180dc1f1be1e53c6f04c515e91b46a480c1e1285471b4
```

Se mantiene el paquete upstream completo, aproximadamente 47,19 MB instalado.
Incluye llama-server.exe, llama-server-impl.dll, llama-common.dll, llama.dll,
mtmd.dll, ggml.dll, ggml-base.dll, variantes ggml-cpu-*.dll y libomp.dll, además
de utilidades y licencia OpenMP. No se intenta minimizar DLLs en este spike.
Todos los archivos de ese paquete tienen hashes en engine.json.
`LLAMA_CPP_BINARY` recibe la ruta absoluta privada; no se busca el backend en
PATH. **WinGet no es necesario**.

## Estructura y rutas reales

Se eligió una ubicación explícitamente administrada por ParseForge, fuera de
AppData para evitar la redirección observada en 3A:

```text
C:\Users\rochi\Documents\ParseForge-runtimes\marker-3b\
  runtime/python/       CPython, Lib y Lib/site-packages
  runtime/llamacpp/     paquete upstream CPU completo
  models/huggingface/   caché Hub, snapshots y caché Xet
  models/datalab/       OCR error y sus configuraciones
  cache/home/          HOME, sentinelas y logs de servidores Surya
  cache/torch/
  cache/pip/
  downloads/wheels/    artefactos descargados; no necesarios en runtime
  temp/                fixtures y resultados
  logs/                evidencia Java, CIM, auditoría y Maven
  engine.json
```

Ruta lógica pasada al launcher, `Path.toRealPath()` de Java y ruta observada por
Python coincidieron con ese directorio. Las líneas de comandos CIM de
llama-server apuntaron al mismo árbol para el ejecutable y ambos GGUF.
No se usa la ruta física de LocalCache de Codex como valor de producción.
Esto acredita la ubicación explícita; todavía no demuestra que JavaFX ejecutado
fuera de Codex vea idéntico contenido en `%LOCALAPPDATA%`. 3C debe fijar esa
resolución desde la aplicación instalada.

La segunda preparación usó
`C:\Users\rochi\Documents\ParseForge-runtimes\marker-3b-replay` y reportó sus
propias rutas privadas; no dependió del primer runtime para importar paquetes.
Sólo se reutilizaron ZIPs y wheels descargados, validados por hash.

Valores observados desde Java → Python en el primer runtime:

```text
sys.executable = ...\marker-3b\runtime\python\python.exe
sys.prefix = ...\marker-3b\runtime\python
sys.base_prefix = ...\marker-3b\runtime\python
sys.path = [ ...\runtime\python\Lib,
             ...\runtime\python,
             ...\runtime\python\Lib\site-packages ]
```

Las rutas completas están en la evidencia. Ninguna apunta a Python global.

## Manifiesto y hashes

[marker-autonomous-engine.json](marker-autonomous-engine.json) es una copia real
del engine.json instalado: fuentes, versiones, arquitectura, rutas relativas,
hashes del ZIP y ejecutable de Python, ZIP y ejecutable de llama.cpp, módulos
nativos del embeddable, DLLs de llama.cpp, `_pth` y archivos de lock/inventario.
Java rechaza archivos faltantes, hashes distintos y rutas que escapen del root.
El manifiesto sigue siendo experimental, sin formato definitivo ni firma.

Los 84 wheels tienen versiones exactas y SHA-256 en
`scripts/spikes/marker-autonomous-requirements.lock`; los nombres y tamaños
están en `marker-autonomous-wheels.json`. La instalación reproduce ese lock y
no calcula hashes nuevos para aceptar silenciosamente cambios upstream.

Se registraron también hashes de 23 archivos de modelos, incluyendo manifiesto
Datalab, configs, tokenizadores, refs y pesos, y de los artefactos descargados.
El inventario queda en [marker-autonomous-evidence.json](marker-autonomous-evidence.json).
Los hashes de wheels se validan antes/durante instalación; los hashes críticos
del runtime se validan antes de cada lanzamiento Java. Los modelos se auditan
después de descargar; aún no existe instalador que fuerce sus revisiones.
El manifiesto regenerado tras revisión incluye también la biblioteca estándar
extraída y los archivos instalados en site-packages: 29.201 archivos críticos,
de los cuales 28.476 pertenecen a paquetes y 598 a la biblioteca estándar.
Se excluyen los directorios `__pycache__` generados; los `.pyc` de la biblioteca
estándar embeddable sí se verifican, porque son necesarios para ejecutar Python.

## Entorno aislado y health check

`ProcessSpec` permite no heredar el entorno, manteniendo compatible su
constructor anterior. El launcher sólo hereda SystemRoot, WINDIR, COMSPEC y
NUMBER_OF_PROCESSORS. Define todas las siguientes variables dentro del root:

| Variable | Ruta relativa/valor |
| --- | --- |
| HF_HOME | models/huggingface |
| HF_HUB_CACHE | models/huggingface/hub |
| MODEL_CACHE_DIR | models/datalab |
| TORCH_HOME | cache/torch |
| XDG_CACHE_HOME | cache |
| HOME, USERPROFILE | cache/home |
| APPDATA, LOCALAPPDATA | cache/home/AppData/Roaming, cache/home/AppData/Local |
| TEMP, TMP | temp |
| PIP_CACHE_DIR | cache/pip |
| LLAMA_CPP_BINARY | runtime/llamacpp/llama-server.exe |
| SURYA_INFERENCE_BACKEND | llamacpp |
| SURYA_INFERENCE_KEEP_ALIVE | true |
| TORCH_DEVICE | cpu |

PATH contiene únicamente Python privado, llama.cpp privado y Windows System32.
No se heredan PYTHONHOME/PYTHONPATH, credenciales de LLMs, rutas GGUF externas ni
URLs/puertos de servicios Surya. Python se ejecuta con `-I -X utf8 -u -B` y `_pth`
aislado. No se modifica permanentemente el PATH del usuario.

Health valida manifest/hashes, `python --version`, imports/rutas/builds CPU,
el entrypoint de Marker con `--help` y `llama-server --version`.
El módulo `marker.scripts.convert_single` no ejecuta la CLI al usar `-m` en esta
versión: se invoca explícitamente `convert_single_cli()` con el Python privado.
Resultado READY tanto para el runtime inicial como para la preparación repetida;
faltantes/corrupción generan BROKEN y salida distinta de cero.

## Modelos privados

Las cachés comenzaron vacías. **No se copiaron pesos de OLD ni de 3A**.

| Uso | Origen/revisión observada | Peso |
| --- | --- | --- |
| Layout | datalab-to/surya_layout2, 0aee81d5fd9275c0582e545bf3a56944b1e75679 | rfdetr_layout.pth, 134.915.547 bytes |
| Reading order | mismo snapshot, subcarpeta order | order_ar.pt, 7.072.639 bytes |
| OCR error | models.datalab.to, ocr_error_detection/2025_02_18 | model.safetensors, 270.664.948 bytes |
| VLM OCR | datalab-to/surya-ocr-2-gguf, 6a3a4c30e5e74446d4f8b6afd05b2f2da970f470 | surya-2.gguf, 1.266.400.864 bytes |
| Proyección visual | mismo snapshot GGUF | surya-2-mmproj.gguf, 204.986.688 bytes |

Los servidores Python se iniciaron con `sys.executable` privado. Sus sentinelas
y logs viven bajo HOME privado. La línea de comandos de llama-server registra
ambos modelos de ese snapshot privado. La conversión digital no necesitó VLM;
OCR forzado sí lo ejecutó y generó 143 tokens, `truncated = 0`.

## Conversiones, cancelación y procesos

Se reutilizó el generador determinista stdlib de 3A; los archivos de prueba
contienen el título «ParseForge Stage 3A». Ese texto no indica uso del runtime
3A: ambos PDFs se generaron con Python 3B y se convirtieron desde Java 3B.

| Prueba | Resultado | Tiempo observado | Procesos vivos después |
| --- | --- | --- | --- |
| Digital | exit 0, Markdown legible | Marker 41,66 s; ejecutor 52,54 s | 0 |
| --force_ocr | exit 0, Markdown legible | Marker 169,42 s, con primera descarga | 0 |
| Cancelación | cancelled=true, exit 1, sin timeout | ejecutor 43,84 s | 0 |
| Health segunda preparación | READY | cachés de modelos aún vacías | 0 |

Ambas salidas contienen las tres líneas esperadas. La digital produjo un título
`##` y OCR un título `#`, sin pérdida del contenido.
Resultados locales: `temp/output-digital/digital/digital.md` y
`temp/output-ocr/ocr/ocr.md`.

Se reutilizan `ProcessSpec`, `ProcessExecutor` y `LocalProcessExecutor`.
El ejecutor registra descendientes durante la vida del padre y cierra los
servicios persistentes antes de esperar EOF de los streams. Cancelación y
timeout cierran ese mismo conjunto; no se terminan procesos por nombre ni se
escanea OLD para matarlo. Tests cubren finalización normal, cancelación,
timeout, entorno limpio y conservación de un proceso ajeno.

Java observó Python principal y servicios privados; en OCR también llama-server.
En este JDK Windows `ProcessHandle.Info` devuelve líneas/argumentos vacíos;
el wrapper PowerShell agrega snapshots CIM de sólo lectura. La primera prueba
OCR tiene snapshots tomados durante la ejecución; cancelación usa el monitor
reproducible del wrapper. Los comandos identifican fast layout, OCR error y
los dos GGUF privados. Cancelación espera la presencia de llama-server y deja
2 segundos para registrar esa evidencia antes de invocar `executor.cancel()`.

El cierre usa handles de descendientes propios, sin matar por coincidencia de
rutas. El muestreo es suficiente para estos servicios observados, pero no es
equivalente a una garantía de Windows Job Objects contra procesos que se
desvinculen antes de ser observados; esa robustez queda para producción.
La auditoría final también escaneó el root privado: cero procesos restantes.

OLD se comparó en modo de sólo lectura con su baseline 3A: inventario de 44.564
archivos por tamaño/mtime y tres hashes críticos. Resultado registrado en la
evidencia. No se ejecutó Marker OLD ni se modificó su configuración.

## Tamaños

Medidas aproximadas por suma de tamaños de archivos, GB/MB decimales;
no equivalen al espacio asignado por NTFS ni al tráfico de red exacto.

| Componente | Tamaño |
| --- | --- |
| Python y stdlib, sin site-packages | 35,42 MB |
| site-packages | 1.261,04 MB |
| llama.cpp completo | 47,19 MB |
| Modelos y caché HF/Xet | 1.888,31 MB |
| Caché auxiliar del primer runtime | aproximadamente 22 KB |
| Total instalado primer runtime | aproximadamente **3,23 GB** |
| ZIPs y wheels retenidos para reproducción | 310,31 MB adicionales |
| Total con artefactos de descarga | aproximadamente **3,54 GB** |

La segunda preparación es un artefacto separado de validación; su tamaño no se
suma al requisito de una instalación. La versión final del script mantiene
también la caché pip dentro del engine; la primera ejecución usó el caché
predeterminado de pip durante preparación. Esto no afecta las rutas de runtime
ni los modelos privados. El tamaño final con caché de preparación variará.

## Red, sistema restante y riesgos

La preparación desde cero requiere Internet para Python, pip, wheels y
llama.cpp. La primera conversión descarga modelos HF y Datalab. Con pesos
locales, la cancelación posterior no volvió a descargar pesos; HF aún consultó
metadatos y emitió aviso de peticiones anónimas. No se afirma operación offline
completa: requerirá revisiones/modelos fijados, precarga de archivos auxiliares,
configuración offline del Hub y validación del resolver Datalab.

No se necesitan Python global, Marker global, WinGet ni CUDA en runtime.
Siguen siendo necesarios Windows x64 y un runtime Java para ParseForge.
La inspección de módulos nativos tras importar Torch mostró dependencias de
Windows/UCRT y `C:\Windows\System32\vcruntime140_threads.dll` (Visual C++ runtime).
El embeddable incluye vcruntime140.dll y vcruntime140_1.dll; no se ha demostrado
que por sí solos cubran todos los wheels en una instalación limpia de Windows.
También se observó el módulo del antivirus Microsoft Defender, que es propio
del sistema auditado y no un artefacto del engine.

No se probó una VM limpia ni se hizo un instalador; la simulación permitida fue
un entorno sin PATH global y comprobación explícita de los ejecutables privados.
Antes de producción hay que definir distribución/instalación del runtime VC++,
licencias de paquetes/modelos, autenticación de manifiestos, manejo de descargas
parciales y revisiones inmutables de modelos. El manifiesto no tiene firma y el
script rechaza runtime existente; no ofrece repair ni actualización.

## Reproducción

Desde la raíz de ParseForge_ARCHITECTURE, elegir un destino fuera del repositorio
que aún no tenga runtime. PowerShell descarga/extracta; no necesita Python:

```powershell
.\scripts\spikes\prepare-autonomous-marker.ps1 -EngineRoot 'C:\ParseForge-data\marker'
mvn -B -ntp clean verify
mvn -B -ntp compile dependency:build-classpath '-Dmdep.outputFile=target/spike-classpath.txt'
.\scripts\spikes\verify-marker-runtime.ps1 -EngineRoot 'C:\ParseForge-data\marker' -Conversions
```

El último comando ejecuta health, digital, OCR, cancelación y auditoría final.
Las conversiones descargan modelos en ese nuevo destino. Para sólo health,
omitir `-Conversions`. `clean` elimina el archivo de classpath de target:
recrearlo con el comando documentado antes de usar el launcher.

Scripts son experimentales. No se elimina ni sobrescribe un runtime existente.
El engine.json generado usa rutas relativas; el launcher recibe explícitamente
la raíz administrada. La verificación opcional de OLD requiere la baseline de
3A y se limita a leer archivos, sin instalar ni ejecutar OLD.

## Validación de correcciones de revisión

Los tres hallazgos se confirmaron contra el código y se corrigieron:

- El sellado incluye módulos y bibliotecas instalados. Una prueba Java confirma
  que modificar un módulo de Marker instalado hace fallar `verifyHashes()`;
  una prueba Python valida la selección de stdlib, paquetes, DLLs y cachés.
- Si falta un archivo de los hashes de OLD, se registra la diferencia y se
  continúa: el informe se escribe y la salida es distinta de cero. Una prueba
  también confirma que se verifican los hashes restantes después del faltante.
- Cada conversión limpia y recrea su directorio de salida antes de ejecutar
  Marker, y usa ese mismo directorio para validar el Markdown actual. Se
  verifican las rutas resueltas antes de borrar; una prueba conserva las salidas
  de otros modos y comprueba que desaparece el Markdown anterior.

Revalidación: `mvn -B -ntp clean verify`, **27 tests aprobados**; pruebas Python
de scripts: **2 aprobadas**; health check Java con el manifiesto regenerado:
**READY**. La evidencia original conserva los resultados y
tamaños de la ejecución inicial; el manifiesto capturado fue regenerado con la
selección ampliada de archivos.

## Evidencia y recomendación para 3C

La evidencia consolidada de texto y el manifiesto real quedan junto a este
documento. Logs completos, stdout/stderr separados, sentinelas/logs Surya,
inventarios, resultados y `maven-clean-verify.log` quedan en `marker-3b/logs`
y `marker-3b/cache/home/.cache/datalab/surya`. La preparación repetida conserva
su propia evidencia de health en `marker-3b-replay/logs`.

3C debería convertir este spike en un paquete verificable: distribución de
CPython/dependencias, modelos por revisión fija, prerrequisitos nativos de
Windows y resolución del directorio desde JavaFX externo a Codex. Validar
primero en una VM sin Python/WinGet y fortalecer cierre con Job Objects antes
de desarrollar instalación/repair desde UI. No se inició 3C.

SPIKE RESULT: SUCCESS
