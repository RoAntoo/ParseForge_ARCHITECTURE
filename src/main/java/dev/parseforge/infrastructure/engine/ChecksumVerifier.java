package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.OperationCancellation;
import dev.parseforge.domain.exception.EngineInstallException;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import static dev.parseforge.domain.exception.EngineInstallException.Code.CHECKSUM_MISMATCH;

public final class ChecksumVerifier {
    public void verify(Path file, String expected, OperationCancellation cancellation) throws IOException {
        if (expected == null || !expected.matches("[a-fA-F0-9]{64}"))
            throw new EngineInstallException(CHECKSUM_MISMATCH, "SHA-256 inválido: " + file.getFileName());
        try (var input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) { cancellation.check(); digest.update(buffer, 0, count); }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected))
                throw new EngineInstallException(CHECKSUM_MISMATCH, "El archivo descargado no coincide con SHA-256: " + file.getFileName());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
