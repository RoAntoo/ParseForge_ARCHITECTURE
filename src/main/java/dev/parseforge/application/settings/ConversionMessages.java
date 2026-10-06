package dev.parseforge.application.settings;

import dev.parseforge.domain.exception.*;

/** Only typed failures determine user instructions; technical text stays in logs. */
public final class ConversionMessages {
    private ConversionMessages() { }
    public static String forError(Throwable error) {
        if (error instanceof ConversionException conversion) return forCode(conversion.code());
        if (error instanceof EngineInstallException engine) return switch (engine.code()) {
            case INSTALL_CANCELLED -> "Conversión cancelada";
            case DISK_SPACE_LOW -> forCode(ErrorCode.DISK_SPACE_LOW);
            case PERMISSION_DENIED -> forCode(ErrorCode.PERMISSION_DENIED);
            default -> forCode(ErrorCode.ENGINE_CORRUPTED);
        };
        return forCode(ErrorCode.PROCESS_CRASHED);
    }
    public static String forCode(ErrorCode code) {
        if (code == null) code = ErrorCode.PROCESS_CRASHED;
        return switch (code) {
            case FORMAT_UNSUPPORTED -> "El motor seleccionado no admite este formato. Elegí un motor compatible o un archivo de los formatos disponibles.";
            case PDF_INVALID -> "No se pudo leer el PDF. El archivo podría estar dañado o no ser un PDF válido.";
            case FILE_INACCESSIBLE -> "ParseForge no puede acceder al archivo seleccionado. Verificá que siga existiendo y que no esté bloqueado por otra aplicación.";
            case PERMISSION_DENIED -> "No se puede escribir en la carpeta de destino. Elegí otra ubicación o revisá los permisos.";
            case DISK_SPACE_LOW -> "No hay suficiente espacio libre en el destino o en la unidad del motor. Liberá espacio o elegí otra carpeta.";
            case ENGINE_NOT_INSTALLED -> "El motor no está instalado. Instalalo desde el panel de motores.";
            case ENGINE_CORRUPTED, PROCESS_START_FAILED -> "El motor seleccionado no está listo para usarse. Verificá su estado o usá Reparar motor.";
            case PROCESS_TIMEOUT -> "La conversión superó el tiempo máximo del motor. Probá con un documento más corto.";
            case USER_CANCELLED -> "Conversión cancelada";
            default -> "No se pudo completar la conversión. Revisá los detalles técnicos; podés intentar con otro motor o documento.";
        };
    }
}
