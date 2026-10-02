# Resumen de sesión — ParseForge, Etapa 5

**Fecha:** 01/10/2026 · **Versión:** 0.1.0  
**Resultado:** UI implementada y paquetes reconstruidos; validación en VM limpia pendiente.

## Cambios realizados

- Rediseñé la pantalla con la referencia adjunta: paleta crema y azul oscuro, sidebar de motores, zona amplia para PDF, tarjeta de carpeta de destino y botón Convertir estable.
- Mostré únicamente **Marker**, con selección mediante tarjeta completa, radio, teclado y estado explícito. Reutilicé los casos de uso existentes para instalar, reparar, cancelar y administrar el motor; conservé desinstalación con confirmación y pruebas opcionales de funcionamiento.
- Agregué una bienvenida que explica el procesamiento local y la instalación del motor. La opción **No volver a mostrar al iniciar** persiste mediante `welcomeDialogVersion`, usando la configuración JSON existente y manteniendo compatibilidad con archivos anteriores.
- Incorporé scroll vertical independiente para el área de trabajo y la sidebar. Definí una ventana mínima de **800×500 píxeles lógicos** y ajusté el tamaño inicial al espacio disponible en pantalla.
- Mejoré textos, espaciado, contraste, iconos, foco, hover y estados deshabilitados. Los nombres y rutas largas se truncan y muestran su valor completo mediante tooltip.
- Ajusté Convertir para reflejar motor listo y seleccionado, PDF accesible, destino utilizable y ausencia de operaciones incompatibles. Durante la conversión se bloquean los cambios de archivo, destino y OCR.
- Conservé feedback de progreso, cancelación, resultados y errores; dejé los logs técnicos en una sección desplegable. El progreso de instalación sigue siendo real por archivo o indeterminado según los datos disponibles.
- Centralicé la versión visible en el `pom.xml` mediante un recurso generado: **ParseForge 0.1.0**.

## Problemas solucionados

- Contenido inaccesible al reducir la altura de la ventana.
- Desborde de nombres de archivo y rutas extensas.
- Disponibilidad incorrecta del botón Convertir ante requisitos faltantes u operaciones activas.
- Falta de orientación inicial y persistencia de la preferencia de bienvenida.
- Durante la revisión visual detecté y corregí una altura excesiva de la tarjeta de Marker que ocultaba su texto; agregué una comprobación de regresión.

## Validaciones y entregables

- Ejecuté el build completo mediante `scripts/release.ps1`, incluyendo `mvn clean verify`: **80 tests aprobados**, sin errores ni fallos. El test de escritorio quedó omitido en esa corrida y se ejecutó aparte.
- Agregué tests de persistencia y `Stage5UiTest`. Este último pasó con escalas JavaFX **100%, 125% y 150%**, comprobando estados del motor, bienvenida, progreso, reparación, cancelación, rutas largas, ventana mínima y conversión con éxito/error mediante motores de prueba.
- Generé **51 capturas** de UI y actualicé la captura del ejecutable empaquetado.
- Reconstruí `ParseForge-Setup-0.1.0.exe`, el ZIP portable y `SHA256SUMS.txt` en `build/release`.
- Verifiqué el arranque y la apertura del ejecutable con Java privado y datos aislados. Confirmé que el JAR empaquetado coincide con el build validado.
- Actualicé README, notas de release, checklist de VM y los registros `STAGE_5_RESULT.md` y `STAGE_5_EVIDENCE.json`.

## Alcance y pendientes

Se mantuvo la versión 0.1.0 y la arquitectura existente. No se agregaron motores, formatos ni nuevas capacidades de procesamiento.

Falta validar en una **VM limpia**: instalación y primer inicio, escalado real de Windows, drag & drop desde Explorer, instalación de Marker, conversión real digital/OCR y controles de reinstalación/desinstalación. Las pruebas de UI con motores de prueba no sustituyen esa validación. No se publicó un release público.
