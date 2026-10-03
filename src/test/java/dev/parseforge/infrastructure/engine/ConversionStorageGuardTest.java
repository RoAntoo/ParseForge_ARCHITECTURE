package dev.parseforge.infrastructure.engine;

import dev.parseforge.domain.exception.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversionStorageGuardTest {
    @Test void onlyLargeInputsWithClearlyLowStorageAreBlocked() throws Exception {
        Path input = Path.of("input.pdf"), storage = Path.of("storage");
        var store = mock(FileStore.class);
        try (var files = mockStatic(Files.class)) {
            files.when(() -> Files.isRegularFile(input)).thenReturn(true);
            files.when(() -> Files.size(input)).thenReturn(1024L);
            files.when(() -> Files.getFileStore(storage)).thenReturn(store);
            when(store.getUsableSpace()).thenReturn(1L);
            ConversionStorageGuard.check(input, storage); verifyNoInteractions(store);
            files.when(() -> Files.size(input)).thenReturn(50L * 1024 * 1024);
            assertEquals(ErrorCode.DISK_SPACE_LOW, assertThrows(ConversionException.class,
                    () -> ConversionStorageGuard.check(input, storage)).code());
            when(store.getUsableSpace()).thenReturn(100L * 1024 * 1024);
            assertDoesNotThrow(() -> ConversionStorageGuard.check(input, storage));
        }
    }
}
