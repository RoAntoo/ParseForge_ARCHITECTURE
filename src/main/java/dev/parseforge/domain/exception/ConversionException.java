package dev.parseforge.domain.exception;

public class ConversionException extends RuntimeException {
    private final ErrorCode code;

    public ConversionException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ConversionException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
