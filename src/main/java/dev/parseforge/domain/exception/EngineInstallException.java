package dev.parseforge.domain.exception;
public final class EngineInstallException extends RuntimeException {
    public enum Code { DOWNLOAD_FAILED, CHECKSUM_MISMATCH, EXTRACTION_FAILED, PACKAGE_INSTALL_FAILED,
        MODEL_DOWNLOAD_FAILED, HEALTH_CHECK_FAILED, DISK_SPACE_LOW, PERMISSION_DENIED, INSTALL_CANCELLED }
    private final Code code;
    public EngineInstallException(Code code, String message) { super(message); this.code = code; }
    public EngineInstallException(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public Code code() { return code; }
}

