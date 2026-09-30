package dev.parseforge.infrastructure.engine;

import com.fasterxml.jackson.databind.*;
import dev.parseforge.domain.model.*;
import java.io.*;
import java.util.*;

public final class EngineManifestRepository {
    private final JsonNode manifest;
    private final byte[] lock;
    public EngineManifestRepository() throws IOException {
        this(read("/engines/marker-windows-x64.json"), read("/engines/marker-requirements.lock"));
    }
    public EngineManifestRepository(byte[] json, byte[] lock) throws IOException {
        this.manifest = new ObjectMapper().readTree(json);
        this.lock = lock.clone();
        if (manifest.path("schemaVersion").asInt() != 1 ||
                !"marker".equals(manifest.path("id").asText()) ||
                !"windows-x64".equals(manifest.path("platform").asText()) ||
                manifest.path("criticalFiles").isEmpty() || manifest.path("packages").path("wheels").isEmpty() ||
                manifest.path("models").isEmpty())
            throw new IOException("Manifiesto de motor no compatible o incompleto");
    }
    private static byte[] read(String resource) throws IOException {
        try (var input = EngineManifestRepository.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Falta recurso: " + resource);
            return input.readAllBytes();
        }
    }
    public JsonNode manifest() { return manifest.deepCopy(); }
    public byte[] lock() { return lock.clone(); }
    public EngineDescriptor descriptor() {
        return new EngineDescriptor(new EngineId(manifest.path("id").asText()),
                manifest.path("displayName").asText(), manifest.path("engineVersion").asText(),
                Set.of(EngineCapability.PDF_TO_MARKDOWN, EngineCapability.OCR, EngineCapability.LOCAL_PROCESSING));
    }
}
