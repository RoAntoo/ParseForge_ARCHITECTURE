# ParseForge — resultado de Etapa 3C

Fecha: 2026-09-30, America/Buenos_Aires.
Resultado: **STAGE 3C: SUCCESS — implementación y validación funcional local**.
La prueba en Windows limpio y la ejecución independiente de Codex siguen siendo
validaciones previas al release; no se afirma que se hayan realizado.

## Componentes y arquitectura

Se agregaron modelos de instalación/versión/capacidades/progreso, estados
NOT_INSTALLED, DOWNLOADING, INSTALLING, VERIFYING, READY, BROKEN y REMOVING,
y errores de instalación con códigos. ConversionStatus permanece independiente.

Application contiene EngineManager, EngineInstaller, EngineVerifier,
EngineRuntimeLocator, DownloadClient, cancelación cooperativa y cinco casos
de uso: instalar, reparar, desinstalar, comprobar y cancelar. Se conserva
UserSettingsRepository como puerto de configuración, ampliando su modelo.

Infrastructure implementa ManagedEngineManager, EnginePathResolver,
EngineManifestRepository, HttpsDownloadClient, ChecksumVerifier, EngineFiles,
MarkerInstaller, MarkerEngineVerifier, ManagedMarkerRuntime y procesos cancelables.
JavaFX incorpora EngineSettingsController y usa casos de uso. La composición
es explícita en ParseForgeApplication.

## Instalación, hashes y staging

El manifiesto schema-v1 fija CPython 3.12.10, llama.cpp b11149, Marker 2.0.0,
84 wheels con URL/SHA-256 y lock, 18 archivos de modelos con revisiones fijadas,
refs offline y la fuente GoNoto. Incluye 29.065 hashes críticos portables.
No se seleccionan versiones latest ni se resuelven dependencias desde la app.

El flujo prepara un directorio hermano marker.installing-UUID, descarga por HTTPS
a .part, verifica tamaño/hash y renombra antes de usar el artefacto. Extrae los
archivos con protección contra traversal y prepara la biblioteca estándar y pip
privados. pip instala exclusivamente los wheels verificados, con no-index,
require-hashes, only-binary y no-compile. Se descargan modelos/fuente antes del
health check. No se invocan Python global, pip global, llama.cpp global o WinGet.

El health completo verifica hashes del runtime y modelos, metadatos, Python,
imports de Marker/Surya/Torch/Torchvision CPU, pip check, Marker --help y
llama-server --version. Detecta errores de carga nativa y señala el prerrequisito
Visual C++ Runtime. No se considera READY una preparación que falla.

La activación mueve el runtime previo a marker.previous y staging al destino
mediante moves atómicos del mismo volumen. Si falla el segundo move, restaura
el anterior. Al reiniciar recupera swaps/removals interrumpidos y limpia staging
abandonado, bajo bloqueo exclusivo. Son dos moves recuperables, no una transacción
única. Cancelación antes del commit detiene I/O/procesos y conserva el previo.
Una vez comenzado el commit/removal, se termina esa operación.

Se limpian descargas, parciales y caché pip. Los launchers de consola generados
por pip se eliminan porque contienen rutas de staging; la conversión llama a la
CLI con Python privado. RECORD es metadata generada por pip y se excluye del
inventario portable. Los archivos de código, módulos nativos y pesos mantienen
sus hashes revisados. Los logs de preparación se conservan.

## Rutas, localización, reparación y desinstalación

El destino normal es:

```text
%LOCALAPPDATA%/ParseForge/
  engines/marker/
    runtime/python/
    runtime/llamacpp/
    models/
    cache/
    temp/
    logs/
    engine.json
  config/config.json
  logs/
```

EnginePathResolver lee el entorno del proceso Java. No tiene rutas de Codex
LocalCache. parseforge.dataDir permite aislar pruebas. MarkerEngine recibe un
locator; adquiere un lease y construye el comando del runtime administrado.
El lease y un lock de archivo impiden reparar/eliminar mientras se convierte,
incluyendo desde otra ventana/proceso.

Reparar hace una reinstalación verificada en staging y reemplaza el runtime
solamente al completarse. Desinstalar pide confirmación en JavaFX, toma el
bloqueo, mueve el motor a un tombstone y elimina runtime/modelos/cachés privados.
No elimina documentos ni salidas fuera del directorio del motor.

El estado se reconstruye desde engine.json y archivos, sin descargar al iniciar.
La comprobación rápida revisa existencia del inventario, hashes del runtime base
y llama.cpp, tamaños de modelos y refs. La comprobación completa se realiza al
instalar/reparar y puede ejecutarse con el driver opt-in. La rápida no vuelve a
hashear todos los pesos ni todos los archivos de paquetes.

## UX y progreso

La UI muestra Configuración > Motores con instalar, reparar, desinstalar,
cancelar y actualizar estado. El selector de marker_single.exe se eliminó del
flujo normal. Se mantiene un override de desarrollo explícito.

Se guardan última carpeta de entrada y de salida por separado, idioma y motor.
FileChooser abre la última entrada válida y cae a Documents/Home si hace falta.
Se migran settings anteriores y se guarda JSON de forma atómica.

Conversión muestra HH:MM:SS, estado de aplicación e indicador indeterminado,
incluso sin logs. stdout/stderr siguen visibles con sus etiquetas; eventos propios
se identifican como SYSTEM/INSTALL. El timer no llena los logs. Se agregó Forzar OCR.

Durante instalación, el porcentaje corresponde al archivo actual y sólo aparece
con Content-Length conocido. Extracción, paquetes y verificación son indeterminados.
MarkerProgressParser devuelve vacío: porcentajes por etapa no representan el
documento completo. No se muestra ETA ni se inventa progreso global.

## Tests y verificación

`mvn clean verify`: **BUILD SUCCESS, 54 tests, 0 fallos, 0 errores, 0 omitidos**.
Finalizó a las 18:50:33 (-03:00), compilando para Java 21 con Oracle JDK 26.
Los 24 tests anteriores se conservaron; se añadieron 30.

Cobertura nueva: rutas/fallback/override, hashes y corrupción, HTTPS y rechazo de
redirect HTTP, progreso por bytes, caché verificada, parciales y cancelación,
ZIP traversal, estado y reinicio, exclusión de operaciones/conversiones y dos
managers, recuperación de backup/staging, cinco casos de uso, instalación exitosa,
checksum/cancel/health fallidos conservando el previo, repair/uninstall/reinstall,
verificador, consistencia manifiesto/lock, parser, carpetas persistidas y
cancelación en la ventana anterior al registro del proceso.

Las pruebas reales se ejecutaron sobre JavaFX y procesos reales mediante drivers
opt-in en src/test; no fueron clics humanos manuales. Root aislado:
`C:/Users/rochi/AppData/Local/ParseForge-3c-validation`.

| Prueba real | Resultado |
| --- | --- |
| Instalar con el botón JavaFX desde un root nuevo | READY, archivos descargados/preparados desde la app |
| Cerrar y abrir otra instancia | READY |
| PDF digital, MarkerEngine administrado | exit 0, Markdown con las tres líneas esperadas |
| Forzar OCR desde JavaFX | completada, Markdown con las tres líneas esperadas |
| Cancelar conversión desde JavaFX | cancelada; control posterior: 0 procesos privados vivos |
| Cancelar instalación en otro root nuevo | NOT_INSTALLED |
| Reparar desde JavaFX | READY después de reinstalación verificada |
| Desinstalar desde JavaFX, aceptando el diálogo | NOT_INSTALLED; salidas conservadas |
| Reinstalar desde JavaFX | READY |
| Reabrir después de reinstalar | READY |

Las capturas de UI sin motor, READY, procesando y conversión terminada fueron
inspeccionadas. Se corrigió un recorte del texto de versión/privacidad.
La evidencia contiene ambos Markdown legibles. La medición final del runtime
es 2.950.552.940 bytes (~2,95 GB decimal); la UI conserva una estimación de ~3,2 GB
para planificación. No incluye outputs de validación.

Resumen versionable: [STAGE_3C_EVIDENCE.json](STAGE_3C_EVIDENCE.json).
Logs, capturas, PDF y salida completa de Maven están fuera de Git, en
`C:/Users/rochi/Documents/ParseForge-validation/stage3c`.
Los drivers ManagedEngineRealSmoke y ManagedEngineUiSmoke nunca se ejecutan
durante mvn test automáticamente; descargan GB y modifican únicamente el root
que se les proporciona explícitamente.

## Límites y trabajo previo al release

- Java reportó iguales la ruta lógica y toRealPath. En esta ejecución iniciada
  desde Codex, las líneas de llama-server mostraron que Python/Hugging Face
  materializan rutas físicas bajo la virtualización de LocalCache del paquete.
  El código no depende de esa ruta. Esta evidencia no prueba que un proceso
  lanzado fuera de Codex comparta la misma instalación visible; debe validarse
  el destino normal con la aplicación independiente.
- No se probó Windows limpio. Visual C++ Runtime x64 estaba disponible en este
  equipo; los imports/CLI son el detector actual. Su distribución definitiva y
  prueba sin dependencias globales pertenecen al trabajo previo al release.
- Se conserva tracking y cierre de descendientes propios; no se matan procesos
  por nombre. Los casos probados terminaron sin huérfanos. Windows Job Objects
  siguen pendientes para garantizar contención ante carreras o crash de la app.
- Resolver actualizaciones de seguridad de las versiones fijadas y distribución
  de la JVM/app antes de producción. No se implementaron updater, firma de
  manifiestos, Docling, MinerU, batch ni Etapa 4.

Dependencias restantes: Windows x64, Visual C++ Runtime x64 e Internet para
instalar/reparar. Desarrollo requiere JDK/Maven; la conversión utiliza Python
y llama.cpp privados. No se agregaron runtimes, modelos, wheels ni binarios
pesados a Git; sólo código, locks, hashes, documentación y evidencia textual.
