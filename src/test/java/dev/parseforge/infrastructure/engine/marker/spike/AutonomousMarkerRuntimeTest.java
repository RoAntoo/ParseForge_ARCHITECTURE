package dev.parseforge.infrastructure.engine.marker.spike;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.HexFormat;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class AutonomousMarkerRuntimeTest {
    @TempDir Path root;

    private AutonomousMarkerRuntime runtime() throws Exception {
        Files.createDirectories(root.resolve("runtime/python"));
        Files.createDirectories(root.resolve("runtime/llamacpp"));
        Files.writeString(root.resolve("runtime/python/python.exe"), "fixture");
        Files.writeString(root.resolve("runtime/llamacpp/llama-server.exe"), "fixture");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("fixture".getBytes()));
        Files.writeString(root.resolve("engine.json"), """
                {"id":"marker","platform":"windows-x64",
                 "python":{"executable":"runtime/python/python.exe","executableSha256":"%s"},
                 "llamaCpp":{"executable":"runtime/llamacpp/llama-server.exe","executableSha256":"%s"},
                 "criticalFiles":[{"path":"runtime/python/python.exe","sha256":"%s"}]}
                """.formatted(hash, hash, hash));
        return new AutonomousMarkerRuntime(root);
    }

    @Test void refusesTraversalAndMissingManifest() throws Exception {
        assertThrows(IOException.class, () -> new AutonomousMarkerRuntime(root));
        var runtime = runtime();
        assertThrows(IOException.class, () -> runtime.resolve("../outside"));
        assertThrows(IOException.class, () -> runtime.resolve(root.getParent().toString()));
    }

    @Test void detectsTamperedArtifacts() throws Exception {
        var runtime = runtime();
        runtime.verifyHashes();
        Files.writeString(root.resolve("runtime/llamacpp/llama-server.exe"), "tampered");
        assertThrows(IOException.class, runtime::verifyHashes);
    }

    @Test void environmentAndConversionStayPrivate() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("SystemRoot") != null);
        var runtime = runtime();
        Path pdf = root.resolve("input with spaces.pdf"); Files.writeString(pdf, "fixture");
        var spec = runtime.conversion(pdf, root.resolve("output"), true);
        assertFalse(spec.inheritEnvironment());
        assertTrue(spec.arguments().contains("--force_ocr"));
        assertTrue(spec.arguments().contains(pdf.toRealPath().toString()));
        assertFalse(spec.environment().containsKey("PYTHONPATH"));
        assertFalse(spec.environment().containsKey("SURYA_INFERENCE_URL"));
        for (String key : java.util.List.of("HOME", "USERPROFILE", "HF_HOME", "HF_HUB_CACHE", "MODEL_CACHE_DIR", "TEMP", "TMP")) {
            assertTrue(Path.of(spec.environment().get(key)).startsWith(root.toRealPath()));
        }
        assertFalse(spec.environment().get("PATH").contains("WinGet"));
    }
}
