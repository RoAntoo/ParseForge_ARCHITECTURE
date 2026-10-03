package dev.parseforge.infrastructure.engine;

import dev.parseforge.domain.exception.*;
import java.io.IOException;
import java.nio.file.*;

/** Conservative floor, not a prediction of OCR storage requirements. */
public final class ConversionStorageGuard {
    private ConversionStorageGuard() { }
    public static void check(Path input, Path storage) throws IOException {
        if (Files.isRegularFile(input) && Files.size(input) >= 50L * 1024 * 1024
                && Files.getFileStore(storage).getUsableSpace() < 100L * 1024 * 1024)
            throw new ConversionException(ErrorCode.DISK_SPACE_LOW,
                    "Less than 100 MiB available for a large PDF in " + storage);
    }
}
