package dev.parseforge.infrastructure.engine;

import com.fasterxml.jackson.databind.*;
import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.exception.EngineInstallException;
import dev.parseforge.domain.model.*;
import dev.parseforge.infrastructure.engine.marker.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.nio.file.*;
import java.time.Duration;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledOnOs(value = OS.WINDOWS, architectures = {"amd64", "x86_64"})
class MarkerInstallationTest {
    @TempDir Path temp;
    private EnginePathResolver paths;
    private EngineManifestRepository manifests;
    private EngineVerifier verifier;
    private ProcessExecutor executor;
    private DownloadClient downloader;
    private Map<String, byte[]> artifacts;
    private JsonNode manifest;
    private static byte[] zip(Map<String, byte[]> files) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    @BeforeEach void setup() throws Exception {
        paths = new EnginePathResolver(Map.of(), temp.toString(), temp.toString());
        byte[] executable = "test executable".getBytes();
        artifacts = Map.of("python", zip(Map.of("python.exe", executable, "python312.zip", zip(Map.of("test.pyc", executable)))),
                "llama", zip(Map.of("llama-server.exe", executable)), "pip", zip(Map.of("pip/__init__.py", executable)),
                "wheel", zip(Map.of("marker/__init__.py", executable)), "model", executable);
        var mapper = new ObjectMapper(); var json = mapper.createObjectNode();
        json.put("schemaVersion", 1); json.put("id", "marker"); json.put("displayName", "Marker");
        json.put("platform", "windows-x64"); json.put("engineVersion", "2.0.0");
        json.put("minimumFreeBytes", 1);
        for (var name : List.of("python", "llama", "pip")) {
            var item = json.putObject(name.equals("llama") ? "llamaCpp" : name);
            item.put("version", "3.12.10"); item.put("url", "https://example.test/" + name);
            item.put("sha256", EngineInfrastructureTest.hash(artifacts.get(name)));
            if (!name.equals("pip")) item.put("executable", name.equals("python") ? "runtime/python/python.exe" : "runtime/llamacpp/llama-server.exe");
        }
        var wheel = json.putObject("packages").putArray("wheels").addObject();
        wheel.put("file", "marker.whl"); wheel.put("url", "https://example.test/wheel"); wheel.put("sha256", EngineInfrastructureTest.hash(artifacts.get("wheel")));
        var model = json.putArray("models").addObject();
        model.put("path", "models/weight"); model.put("url", "https://example.test/model");
        model.put("bytes", executable.length); model.put("sha256", EngineInfrastructureTest.hash(executable));
        var critical = json.putArray("criticalFiles").addObject();
        critical.put("path", "runtime/python/python.exe"); critical.put("sha256", EngineInfrastructureTest.hash(executable));
        json.putArray("refs"); manifest = json;
        manifests = new EngineManifestRepository(mapper.writeValueAsBytes(json), "marker==2.0.0".getBytes());
        verifier = mock(EngineVerifier.class);
        when(verifier.verify(any(), any(), anyBoolean(), any(), any())).thenReturn(new EngineVerificationResult(true, "ok"));
        executor = mock(ProcessExecutor.class);
        when(executor.execute(any(), any())).thenReturn(new ProcessResult(0, false, false, Duration.ZERO));
        downloader = (request, listener, token) -> {
            try {
                token.check();
                byte[] data = artifacts.get(request.uri().getPath().substring(1));
                Files.createDirectories(request.destination().getParent()); Files.write(request.destination(), data);
                new ChecksumVerifier().verify(request.destination(), request.sha256(), token);
                listener.onProgress(data.length, data.length);
                return new DownloadResult(request.destination(), data.length);
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        };
    }
    private MarkerInstaller installer(DownloadClient client) {
        return new MarkerInstaller(paths, manifests, client, verifier, executor);
    }
    @Test void successUsesStagingAndAtomicCommitAndCleansDownloadedArtifacts() throws Exception {
        List<Path> verifiedRoots = new ArrayList<>();
        when(verifier.verify(any(), any(), eq(true), any(), any())).thenAnswer(i -> {
            Path staging = i.getArgument(1); verifiedRoots.add(staging);
            assertTrue(staging.getFileName().toString().startsWith("marker.installing-"));
            assertFalse(Files.exists(paths.engine(new EngineId("marker"))));
            assertTrue(Files.exists(staging.resolve("engine.json")));
            return new EngineVerificationResult(true, "ok");
        });
        installer(downloader).install(manifests.descriptor(), p -> {}, new OperationCancellation());
        Path engine = paths.engine(new EngineId("marker"));
        assertTrue(Files.isRegularFile(engine.resolve("runtime/python/python.exe")));
        assertTrue(Files.isRegularFile(engine.resolve("models/weight")));
        assertFalse(Files.exists(engine.resolve("downloads")));
        assertFalse(Files.exists(verifiedRoots.getFirst()));
        assertFalse(Files.exists(engine.resolveSibling("marker.previous")));
        var spec = org.mockito.ArgumentCaptor.forClass(ProcessSpec.class);
        verify(executor).execute(spec.capture(), any());
        assertFalse(spec.getValue().inheritEnvironment());
        assertTrue(spec.getValue().arguments().contains("--require-hashes"));
        assertTrue(spec.getValue().arguments().contains("--no-index"));
    }
    @Test void checksumFailurePreservesPreviousReadyAndCleansStaging() throws Exception {
        Path engine = paths.engine(new EngineId("marker")); Files.createDirectories(engine);
        Files.writeString(engine.resolve("sentinel"), "valid previous");
        DownloadClient failed = (r,l,c) -> { throw new EngineInstallException(EngineInstallException.Code.CHECKSUM_MISMATCH, "bad hash"); };
        assertThrows(EngineInstallException.class, () -> installer(failed).repair(manifests.descriptor(), p -> {}, new OperationCancellation()));
        assertEquals("valid previous", Files.readString(engine.resolve("sentinel")));
        try (var children = Files.list(engine.getParent())) { assertEquals(List.of(engine), children.toList()); }
        verifyNoInteractions(executor);
    }
    @Test void cancellingInstallPreservesPreviousInstallation() throws Exception {
        Path engine = paths.engine(new EngineId("marker")); Files.createDirectories(engine);
        Files.writeString(engine.resolve("sentinel"), "old"); var token = new OperationCancellation();
        var error = assertThrows(EngineInstallException.class, () -> installer(downloader).install(manifests.descriptor(),
                p -> { if (p.phase() == EngineInstallProgress.Phase.DOWNLOADING) token.cancel(); }, token));
        assertEquals(EngineInstallException.Code.INSTALL_CANCELLED, error.code());
        assertEquals("old", Files.readString(engine.resolve("sentinel")));
        try (var children = Files.list(engine.getParent())) { assertEquals(1, children.count()); }
    }
    @Test void failedHealthDoesNotReplacePreviousAndRepairReplacesItAfterValidation() throws Exception {
        Path engine = paths.engine(new EngineId("marker")); Files.createDirectories(engine);
        Files.writeString(engine.resolve("sentinel"), "old");
        when(verifier.verify(any(), any(), anyBoolean(), any(), any())).thenReturn(new EngineVerificationResult(false, "unhealthy"));
        assertThrows(EngineInstallException.class, () -> installer(downloader).repair(manifests.descriptor(), p -> {}, new OperationCancellation()));
        assertEquals("old", Files.readString(engine.resolve("sentinel")));
        when(verifier.verify(any(), any(), anyBoolean(), any(), any())).thenReturn(new EngineVerificationResult(true, "ok"));
        installer(downloader).repair(manifests.descriptor(), p -> {}, new OperationCancellation());
        assertFalse(Files.exists(engine.resolve("sentinel")));
        installer(downloader).uninstall(manifests.descriptor(), p -> {}, new OperationCancellation());
        assertFalse(Files.exists(engine));
        installer(downloader).install(manifests.descriptor(), p -> {}, new OperationCancellation());
        assertTrue(Files.exists(engine));
    }
    @Test void verifierRejectsMissingAndCorruptCriticalFilesAndModels() throws Exception {
        installer(downloader).install(manifests.descriptor(), p -> {}, new OperationCancellation());
        var realVerifier = new MarkerEngineVerifier(manifests, new ChecksumVerifier(), executor);
        Path engine = paths.engine(new EngineId("marker"));
        assertTrue(realVerifier.verify(manifests.descriptor(), engine, true, p -> {}, new OperationCancellation()).ready());
        Files.writeString(engine.resolve("models/weight"), "bad");
        assertFalse(realVerifier.verify(manifests.descriptor(), engine, false, p -> {}, new OperationCancellation()).ready());
        Files.write(engine.resolve("models/weight"), artifacts.get("model"));
        Files.writeString(engine.resolve("runtime/python/python.exe"), "corrupt");
        assertFalse(realVerifier.verify(manifests.descriptor(), engine, false, p -> {}, new OperationCancellation()).ready());
    }
}
