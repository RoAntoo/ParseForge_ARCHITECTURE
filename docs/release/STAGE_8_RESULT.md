# ParseForge — resultado de Stage 8

Versión **0.1.0** · **2026-10-03** · America/Buenos_Aires.

**Implementada y verificada en el equipo anfitrión.** `scripts/release.ps1` pasó.
Se generaron el instalador y el ZIP portátil locales. Continúa pendiente la
validación del instalador en una VM limpia; no se publicó una release.

## Interfaz entregada

- Header con ParseForge y Ajustes; sidebar reducida a tarjetas estables con nombre,
  perfil, descripción breve, estado y radio. Selección única mediante radio o
  tarjeta completa, indicada también por borde. Las acciones técnicas salieron del home.
- Instalar permanece como acceso rápido cuando falta un motor. Abre Ajustes e
  inicia la operación existente. Error ofrece Abrir ajustes; una operación activa
  ofrece Ver progreso. Cerrar el diálogo no cancela la operación.
- Ajustes es un diálogo propio de la ventana, modal, redimensionable y con scroll.
  Marker y MarkItDown tienen pestañas independientes de la selección de conversión.
  Muestra versión, tamaño aproximado del perfil, descripción y procesamiento local.
- Acciones según estado: Instalar cuando falta el motor; Verificar, Reparar y
  Desinstalar para Listo/Error; Cancelar instalación mientras corresponde.
  Se conserva la confirmación de desinstalación y la reparación existente. No se
  agrega otra operación de reinstalación: Reparar ya usa esa operación segura.
- El home ordena PDF → preflight compacto → destino → opciones → Convertir a Markdown.
  Forzar OCR conserva su semántica por conversión y aparece únicamente con Marker.
  Nombres/rutas largos conservan ellipsis y tooltip con el valor completo.
- CTA ancho con `#F63A48`; texto oscuro para contraste, hover coral leve, pressed
  más oscuro, disabled gris legible y foco azul exterior. Durante conversión se
  oculta y Cancelar conserva un estilo neutro en la sidebar, junto al motor,
  fase y tiempo, incluso con la ventana a 800×500.
- El scroll central mantiene accesible Convertir en ventanas pequeñas; Ajustes
  también se adapta y permite desplazar su contenido. Se conserva onboarding,
  drag & drop, estados, errores, progreso, cancelación y resultado final.

## Design system y accesibilidad

`src/main/resources/css/main.css` centraliza los colores como tokens JavaFX:
primary, convert, background, surface, surface-muted, border, text,
text-secondary, success, warning, error, focus y disabled. Mantiene crema/azul.
Espaciado documentado: 4/8/12/16/24/32; radios: 8/12/16;
jerarquía tipográfica: 11/13/16/24. JavaFX no admite variables CSS de dimensiones;
las escalas se aplican mediante las reglas compartidas y la composición del layout.

Las pruebas comprueban el color computado del CTA y contraste mínimo 4.5:1 en
normal, hover, pressed, disabled y focus. Las relaciones exactas están en el JSON.
Se ejercitaron Enter, Space, Tab y Shift+Tab, foco visible, selección de motores,
Ajustes, instalación rápida y conversión. Los controles nativos de archivo y
carpeta conservan sus handlers; sus diálogos del sistema no fueron automatizados.

## Verificación

| Comprobación | Resultado |
| --- | --- |
| `scripts/release.ps1` | PASS; instalador y ZIP generados |
| Suite funcional del release | 140 descubiertos, 136 aprobados, 0 fallos/errores; 4 suites UI opt-in omitidas aquí |
| Stage5UiTest, Stage6UiTest, Stage7UiTest, Stage8UiTest | PASS al 100%, 125%, 150%: 12 ejecuciones |
| Stage8UiTest tras sincronizar la captura de ventana restaurada | PASS en las tres escalas: 3 ejecuciones adicionales |
| JAR empaquetado frente al JAR construido | SHA-256 idéntico |
| Capturas Stage 8 | 48 PNG: 16 escenarios por escala |
| `git diff --check` | PASS |

Las pruebas existentes se adaptaron al nuevo diálogo y al CTA desplazable;
no se eliminaron suites ni pruebas funcionales. Stage7UiTest permanece intacta.
También se actualizaron los selectores de ManagedEngineUiSmoke y su espera del
preflight; el driver compiló, pero no se ejecutó contra motores reales.
Los únicos cambios de producción están en los dos controladores JavaFX y CSS.
No se modificaron motores, EngineManager, preflight, heurísticas, casos de uso,
persistencia, Job Objects, runtimes, instaladores ni hashes de dependencias.

## Capturas y artefactos

Capturas locales: `build/stage8-ui/100%`, `125%` y `150%`.
Cada carpeta contiene home vacío, PDF digital con MarkItDown, escaneado con Marker,
escaneado incompatible con MarkItDown/CTA disabled, motor no instalado,
Ajustes de ambos motores, Error/No instalado en Ajustes, hover/pressed/focus,
ventana pequeña, Ajustes pequeño, conversión activa y conversión completada.

- [Home con PDF digital](../../build/stage8-ui/100%/home-digital-markitdown.png)
- [Home con PDF escaneado](../../build/stage8-ui/100%/home-scanned-marker.png)
- [Ajustes de Marker](../../build/stage8-ui/100%/settings-marker.png)
- [Ventana pequeña al 150%](../../build/stage8-ui/150%/small-window.png)
- [Evidencia JSON con rutas, dimensiones y SHA-256](STAGE_8_EVIDENCE.json)

Paquetes: `build/release/ParseForge-Setup-0.1.0.exe` y
`build/release/ParseForge-0.1.0-win-x64.zip`; hashes en `SHA256SUMS.txt`.
Logs: `build/stage8-release.log`, `build/stage8-matrix-<escala>.log` y
`build/stage8-final-<escala>.log`. Los archivos de build están ignorados por Git.

## Alcance de la evidencia

Son capturas de escenas JavaFX en píxeles lógicos con outputScale 1.0/1.25/1.5,
no capturas completas del escritorio Windows. Preflight usa PDFs reales de
fixture y PDFBox. Las operaciones de motores y conversiones en las pruebas UI
usan mocks controlados; una captura de resultado no certifica una conversión
real. Para esta etapa visual no se repitieron descargas, reinstalaciones reales
ni benchmarks de OCR. Sigue pendiente la aceptación del instalador en VM limpia
y de los selectores nativos de archivos/carpetas. El paquete es local y sin firma.

## Reproducir

Usar PowerShell en la raíz del repositorio:

```powershell
. ./scripts/release-common.ps1
$env:JAVA_HOME = Get-ReleaseJdk
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
foreach ($scale in @('100%', '125%', '150%')) {
    mvn.cmd -B '-Dtest=Stage5UiTest,Stage6UiTest,Stage7UiTest,Stage8UiTest' `
        '-Dparseforge.uiTests=true' "-Dglass.win.uiScale=$scale" '-DreuseForks=false' test
    if ($LASTEXITCODE -ne 0) { throw "UI falló en $scale" }
}
./scripts/release.ps1
```


## Ajuste posterior solicitado — texto blanco

El CTA activo ahora usa texto blanco (`#ffffff`) en normal, hover, pressed y
focus. Disabled conserva texto oscuro sobre gris para distinguir su estado.
La prueba de UI comprueba el blanco solicitado en los estados activos y mantiene
la comprobación de contraste para disabled. La medición ≥4.5:1 y la evidencia de
release anteriores corresponden a la versión original con texto oscuro; no se
atribuyen al texto blanco. Los paquetes anteriores no incluyen este ajuste.

Verificación del ajuste: Stage8UiTest PASS al 100%; log
`build/stage8-white-text.log`, capturas en `build/stage8-ui/1.0/`.
