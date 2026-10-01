# ParseForge — Etapa 4: resultado del candidato 0.1.0

Fecha: 2026-09-30, America/Buenos_Aires.
Estado: **PACKAGE BUILT — VM VALIDATION PENDING**.
Se generaron los artefactos y se validó instalación real en el host.
La etapa no se declara RELEASE READY: falta la prueba funcional en Windows limpio.

## Packaging y artefactos

1. **jlink:** Temurin 21.0.12.1+1 Windows x64, fijado por URL/SHA-256.
   jdeps analiza los JARs de runtime; se agrega jdk.crypto.ec para HTTPS.
   Runtime con debug/headers/man pages removidos y compresión. Tamaño:
   **50.563.918 bytes (50,6 MB)**.
2. **Módulos raíz:** java.base, java.desktop, java.naming, java.net.http,
   java.sql, jdk.crypto.ec, jdk.jfr y jdk.unsupported. La imagen incluye también
   las dependencias transitivas java.datatransfer, java.xml, java.prefs,
   java.logging, java.security.sasl y java.transaction.xa: **14 módulos**.
3. **jpackage:** app-image no modular con Launcher propio, JavaFX/dependencias
   en classpath y runtime privado. Metadata: ParseForge, 0.1.0, ParseForge
   contributors, Local document conversion, copyright de los contributors.
   Icono propio aplicado a EXE, setup, accesos y ventana.
4. **App-image:** `build/app-image/ParseForge/ParseForge.exe`; contiene app/ y
   runtime/, inventario con hashes y avisos/licencias. No incluye Marker/modelos.
5. **Inno Setup:** 6.7.1 fijado/verificado. Español/inglés, carpeta, accesos de
   escritorio/Inicio, lanzamiento final, logs y desinstalador. PrivilegesRequired=lowest.
   Destino predeterminado: `%LOCALAPPDATA%\Programs\ParseForge`. Es un ajuste
   deliberado del ejemplo Program Files para instalar por usuario sin elevación;
   [ADR-015](../ADR/ADR-015-windows-packaging.md) documenta la decisión.
6. **Setup:** `build/release/ParseForge-Setup-0.1.0.exe`.
7. **Tamaño del setup:** **49.196.194 bytes (49,2 MB)**.
8. **Tamaño de la app-image:** **68.672.714 bytes (68,7 MB)**. La instalación
   agrega el desinstalador/metadatos de Inno y excluye datos mutables de Marker.
   ZIP portable: **51.311.271 bytes (51,3 MB)**.

## Runtime, UX y release

9. **Visual C++ Runtime:** detección por carga de vcruntime140.dll,
   vcruntime140_1.dll y msvcp140.dll exclusivamente desde System32. Si faltan,
   se bloquea instalar Marker antes de descargar GB con explicación y enlace
   oficial de Microsoft. La UI incluye un enlace de descarga. No se redistribuye
   ni ejecuta automáticamente un redistributable. El health check comprueba la
   compatibilidad efectiva. El escenario sin runtime sigue pendiente en VM.
10. **Job Objects:** implementados con JNA. La aplicación se asigna al job antes
    de iniciar hijos, con KILL_ON_JOB_CLOSE y sin breakaway. El handle privado
    vive hasta teardown; conserva el tracking/cancelación por operación.
    Test Windows real: matar forzosamente una JVM de fixture elimina su hijo,
    sin depender de shutdown hooks. La limpieza de Python/Surya/llama de la
    aplicación instalada debe confirmarse en VM.
11. **UI Marker:** tamaño ~3,2 GB, mínimo 7 GB libres, privacidad, uso de Internet,
    enlace de prerrequisito, acciones que se adaptan al ancho y tiempo transcurrido.
12. **Progreso/ETA:** porcentaje del archivo actual sólo con bytes totales;
    bytes descargados/total, velocidad en ventana móvil de 5 s. ETA exige al
    menos 2 s de historial, 3 muestras y velocidades razonablemente estables.
    Si falta tamaño/estabilidad aparece Calculando tiempo restante. Una pausa
    de descarga invalida la estimación. Preparación/verificación siguen
    indeterminadas; no se inventa porcentaje global.
13. **Mensaje aproximado:** visible: “Los tiempos son aproximados. Las descargas
    dependen de tu conexión a Internet y las etapas de preparación dependen del
    rendimiento de tu equipo.” Etapas largas muestran tiempo transcurrido y
    “ParseForge continúa trabajando”.
14. **Scripts:** build.ps1, package.ps1, release.ps1; auxiliares release-common,
    release-tools.json, collect-notices, generate-icon, smoke-installed y
    test-installer. Un comando release.ps1 ejecuta pruebas y genera artefactos.
    Pipeline con versiones/hashes fijados; no promete identidad binaria de
    EXE/ZIP entre builds por timestamps. [BUILD.md](BUILD.md) explica comandos.
15. **Artefactos:** setup, ZIP portable, SHA256SUMS.txt y release-notes.md en
    build/release. No se agregaron a Git runtimes, engines, modelos ni instaladores.
    No se publicó GitHub Release ni se inició Etapa 5.
16. **SHA-256 final:**

```text
43a526bbfa669fe0a45c69154512b0bf14a87666450a1ca53337dfa043076712  ParseForge-Setup-0.1.0.exe
bdb43bee39d45782a322e3a27e9c396c2861b0475d807c6af466719c2cba069f  ParseForge-0.1.0-win-x64.zip
```

## Validación y límites

17. **mvn clean verify:** BUILD SUCCESS, **64 tests, 0 fallos, 0 errores,
    0 omitidos**, ejecutados con el Temurin fijado desde release.ps1. Incluye
    rutas instaladas, migración/persistencia de carpetas/idioma/motor, progreso
    y reinicio de ETA, cancelación/procesos y terminación abrupta bajo Job Object.
18. **Smoke host:** Windows 11 Pro 10.0.26200 x64. App-image y EXE realmente
    instalado abrieron con runtime privado, desde otro working directory,
    PATH sin Java/Maven/Python y JAVA_HOME vacío. Se verificaron manifest,
    config guardada/releída, rutas aisladas, Job Object y ventana; captura visual
    inspeccionada. Setup silencioso creó accesos; reinstalación abrió el EXE;
    uninstall eliminó EXE/accesos/registro y conservó un documento creado por
    el test en la carpeta de instalación. Este host contiene herramientas de
    desarrollo: estos resultados no sustituyen Windows limpio.
19. **Pendientes:** VM limpia, flujo Marker completo desde el EXE instalado,
    VC runtime ausente/presente, conversión digital/OCR/cancelación/crash con
    procesos reales, persistencia de Marker tras reinstalación y upgrade entre
    versiones distintas. No hay firma comercial, updater ni publicación.
    Licencias/avisos Java conservados; inventario de wheels y restricciones de
    modelos documentados. La revisión de términos para el destino comercial
    sigue siendo necesaria antes de distribuir los modelos. Fallos anteriores
    a la inicialización de JVM dependen del mensaje del launcher nativo, no del
    logger Java. Windows Sandbox no está disponible actualmente en este host.
20. **Validación manual:** ejecutar todos los checks y registrar hardware,
    Windows, hash, resultados y errores en [VM_VALIDATION.md](VM_VALIDATION.md).
    Abrir desde el acceso directo, instalar Marker, convertir digital/OCR,
    cancelar/matar la app, revisar procesos, persistencia, reparación y uninstall.
21. **Estado final:** **PACKAGE BUILT — VM VALIDATION PENDING**.

Logs/evidencia locales: build/final-release.log, build/final-smoke-ui.log,
build/final-test-installer.log, build/installer-test y build/smoke.
Resumen versionable: [STAGE_4_EVIDENCE.json](STAGE_4_EVIDENCE.json).
Configuración normal en `%APPDATA%\ParseForge\config.json`, migrada de 3C cuando
no existe destino; motores y logs en `%LOCALAPPDATA%\ParseForge`.
Los logs rotan a diario/5 MB con 7 días/50 MB y evitan persistir texto del PDF.
