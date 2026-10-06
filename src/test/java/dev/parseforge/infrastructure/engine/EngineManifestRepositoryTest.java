package dev.parseforge.infrastructure.engine;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineManifestRepositoryTest {
    @Test void shippedArtifactsMatchHashLockAndUsePinnedSecureSources() throws Exception {
        var repository = new EngineManifestRepository();
        JsonNode manifest = repository.manifest();
        Set<String> wheelHashes = new HashSet<>();
        for (JsonNode wheel : manifest.path("packages").path("wheels")) {
            wheelHashes.add(wheel.path("sha256").asText());
            assertEquals("https", URI.create(wheel.path("url").asText()).getScheme());
            assertTrue(wheel.path("file").asText().endsWith(".whl"));
        }
        Set<String> lockHashes = new HashSet<>();
        new String(repository.lock(), StandardCharsets.UTF_8).lines().forEach(line -> {
            assertTrue(line.contains("=="));
            lockHashes.add(line.split("--hash=sha256:")[1]);
        });
        assertEquals(lockHashes, wheelHashes);
        assertEquals(84, wheelHashes.size());
        for (JsonNode model : manifest.path("models")) {
            String url = model.path("url").asText();
            assertEquals("https", URI.create(url).getScheme());
            if (url.contains("huggingface.co")) assertTrue(url.matches(".*/resolve/[a-f0-9]{40}/.*"));
            assertTrue(model.path("sha256").asText().matches("[a-f0-9]{64}"));
        }
        assertFalse(manifest.path("assets").isEmpty());
    }
    @Test void missingOrUnsupportedManifestFailsClosed() {
        assertThrows(java.io.IOException.class, () -> new EngineManifestRepository("{\"schemaVersion\":99}".getBytes(), new byte[0]));
    }
    @Test void markItDownUsesPinnedOfflineDocumentPackagesAndHasNoOcrOrMarkerArtifacts() throws Exception {
        var repository = new EngineManifestRepository("markitdown");
        var manifest = repository.manifest();
        String lock = new String(repository.lock(), StandardCharsets.UTF_8);
        assertTrue(lock.contains("markitdown[pdf,docx,pptx,xlsx,xls,outlook]==0.1.8")); assertFalse(lock.contains("[all]"));
        assertFalse(lock.contains("marker-pdf")); assertFalse(lock.contains("openai=="));
        assertFalse(manifest.has("models")); assertFalse(manifest.has("llamaCpp"));
        assertFalse(repository.descriptor().capabilities().contains(dev.parseforge.domain.model.EngineCapability.OCR));
        Set<String> hashes = new HashSet<>();
        for (JsonNode wheel : manifest.path("packages").path("wheels")) {
            assertEquals("https", URI.create(wheel.path("url").asText()).getScheme());
            assertTrue(wheel.path("bytes").asLong() > 0); hashes.add(wheel.path("sha256").asText());
        }
        Set<String> locked = new HashSet<>(); lock.lines().forEach(line -> locked.add(line.split("--hash=sha256:")[1]));
        assertEquals(locked, hashes);
        for (String artifact : List.of("python", "pip")) assertTrue(manifest.path(artifact).path("sha256").asText().matches("[a-f0-9]{64}"));
        assertTrue(manifest.path("criticalFiles").size() > 0);
    }
}
