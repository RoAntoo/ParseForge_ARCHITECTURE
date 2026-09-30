package dev.parseforge.infrastructure.engine;

import dev.parseforge.application.port.out.*;
import dev.parseforge.domain.exception.EngineInstallException;
import dev.parseforge.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EngineInfrastructureTest {
    @TempDir Path temp;
    private static final byte[] DATA = "reviewed artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    @Test void pathResolverUsesHostLocalAppDataAndIndependentOverride() {
        var paths = new EnginePathResolver(Map.of("LOCALAPPDATA", temp.toString(), "APPDATA", "ignored"), "ignored", null);
        assertEquals(temp.resolve("ParseForge/engines/marker"), paths.engine(new EngineId("marker")));
        assertEquals(temp.resolve("other"), new EnginePathResolver(Map.of(), temp.toString(), temp.resolve("other").toString()).dataRoot());
        assertEquals(temp.resolve("AppData/Local/ParseForge"), new EnginePathResolver(Map.of(), temp.toString(), null).dataRoot());
    }
    @Test void checksumRejectsCorruptionAndRespondsToCancellation() throws Exception {
        Path file = temp.resolve("file"); Files.write(file, DATA);
        var verifier = new ChecksumVerifier(); var cancel = new OperationCancellation();
        verifier.verify(file, hash(DATA), cancel);
        var error = assertThrows(EngineInstallException.class, () -> verifier.verify(file, "0".repeat(64), cancel));
        assertEquals(EngineInstallException.Code.CHECKSUM_MISMATCH, error.code());
        cancel.cancel();
        assertThrows(EngineInstallException.class, () -> verifier.verify(file, hash(DATA), cancel));
    }
    @Test void extractorRejectsZipTraversalAndDeletionCannotEscapeWorkspace() throws Exception {
        Path zip = temp.resolve("bad.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../outside")); out.write(DATA); out.closeEntry();
        }
        assertThrows(IOException.class, () -> EngineFiles.extract(zip, temp.resolve("stage"), new OperationCancellation()));
        assertFalse(Files.exists(temp.resolve("outside")));
        assertThrows(IOException.class, () -> EngineFiles.deleteTree(temp.resolve("stage"), zip));
        assertTrue(Files.exists(zip));
    }
    private HttpsDownloadClient client(int status, byte[] data, String redirect) {
        return new HttpsDownloadClient(request -> CompletableFuture.completedFuture(new HttpResponse<InputStream>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return request; }
            public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(redirect == null
                    ? Map.of("Content-Length", List.of("" + data.length)) : Map.of("location", List.of(redirect)), (a,b) -> true); }
            public InputStream body() { return new ByteArrayInputStream(data); }
            public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return request.uri(); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        }), new ChecksumVerifier());
    }
    @Test void downloadCommitsOnlyVerifiedContentAndReportsActualBytes() throws Exception {
        Path target = temp.resolve("archive"); List<Long> bytes = new ArrayList<>();
        var result = client(200, DATA, null).download(new DownloadRequest(URI.create("https://example.test/file"), target, hash(DATA), DATA.length),
                (done,total) -> bytes.add(done), new OperationCancellation());
        assertArrayEquals(DATA, Files.readAllBytes(result.file()));
        assertEquals((long) DATA.length, bytes.getLast());
        assertFalse(Files.exists(temp.resolve("archive.part")));
    }
    @Test void mismatchAndHttpFailuresNeverCommitAndCleanPartials() throws Exception {
        Path target = temp.resolve("archive");
        var error = assertThrows(EngineInstallException.class, () -> client(200, DATA, null).download(
                new DownloadRequest(URI.create("https://example.test/file"), target, "0".repeat(64), -1), (a,b) -> {}, new OperationCancellation()));
        assertEquals(EngineInstallException.Code.CHECKSUM_MISMATCH, error.code());
        assertFalse(Files.exists(target)); assertFalse(Files.exists(temp.resolve("archive.part")));
        assertThrows(EngineInstallException.class, () -> client(503, DATA, null).download(
                new DownloadRequest(URI.create("https://example.test/file"), target, hash(DATA), -1), (a,b) -> {}, new OperationCancellation()));
    }
    @Test void downloaderRejectsHttpIncludingDowngradeRedirects() throws Exception {
        Path target = temp.resolve("archive");
        for (String url : List.of("http://example.test/file", "https://example.test/file")) {
            assertThrows(EngineInstallException.class, () -> client(302, DATA, "http://example.test/file").download(
                    new DownloadRequest(URI.create(url), target, hash(DATA), -1), (a,b) -> {}, new OperationCancellation()));
        }
    }
    @Test void cancellationDuringTransferClosesAndRemovesPartial() throws Exception {
        Path target = temp.resolve("archive"); var token = new OperationCancellation();
        assertThrows(EngineInstallException.class, () -> client(200, DATA, null).download(
                new DownloadRequest(URI.create("https://example.test/file"), target, hash(DATA), -1),
                (done,total) -> token.cancel(), token));
        assertFalse(Files.exists(target)); assertFalse(Files.exists(temp.resolve("archive.part")));
    }
    @Test void checkedCacheAvoidsNetworkButCorruptCacheIsReplaced() throws Exception {
        Path target = temp.resolve("archive"); Files.writeString(target, "corrupt");
        client(200, DATA, null).download(new DownloadRequest(URI.create("https://example.test/file"), target, hash(DATA), -1),
                (a,b) -> {}, new OperationCancellation());
        assertArrayEquals(DATA, Files.readAllBytes(target));
        client(503, new byte[0], null).download(new DownloadRequest(URI.create("https://example.test/file"), target, hash(DATA), -1),
                (a,b) -> fail("Cache unexpectedly downloaded"), new OperationCancellation());
    }
}
